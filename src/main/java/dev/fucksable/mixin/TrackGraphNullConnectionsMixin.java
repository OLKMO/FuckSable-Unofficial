package dev.fucksable.mixin;

import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackEdge;
import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collections;
import java.util.Map;

/**
 * 修复 Create 6.0.10 的 TrackGraph.getConnectionsFrom 在 node 为 null 时返回 null
 * 导致 Navigation.search 抛出 NullPointerException 崩溃服务器的问题。
 * <p>
 * 问题分析：
 * TrackGraph.getConnectionsFrom(TrackNode node) 在 node == null 时直接返回 null
 * （com/simibubi/create/content/trains/graph/TrackGraph.java:376）。
 * 而 Navigation.search(double, double, ...) 在第 606-607 行直接对返回值调用
 * .get(otherNode)，未做 null 检查，导致 NPE。
 * <p>
 * 触发场景：玩家驾驶火车时（ControlsServerHandler.tick -> CarriageContraptionEntity.control
 * -> Navigation.findNearestApproachable -> Navigation.search），火车的 TravellingPoint.node1
 * 或 node2 为 null（火车状态损坏，常见于 CTT 并发问题之后）。
 * <p>
 * 修复方式：
 * 在 getConnectionsFrom 入口处检查 node 是否为 null，如果是则返回空 Map 而非 null。
 * 这与方法签名承诺的返回类型 Map 一致，且不影响其他已做 null 检查的调用点
 * （如 TrackGraph 自身代码在调用 getConnectionsFrom 后已有 ifnull 检查）。
 */
@Mixin(value = TrackGraph.class, remap = false)
public class TrackGraphNullConnectionsMixin {

    /**
     * 在 getConnectionsFrom 入口处拦截 null node，返回空 Map 避免下游 NPE。
     */
    @Inject(method = "getConnectionsFrom", at = @At("HEAD"), cancellable = true, remap = false)
    private void fucksable$returnEmptyForNullNode(TrackNode node,
                                                  CallbackInfoReturnable<Map<TrackNode, TrackEdge>> cir) {
        if (!FixRegistry.isEnabled("create-trackgraph-null-guard")) {
            return;
        }
        if (node == null) {
            FuckSable.LOGGER.debug("TrackGraph.getConnectionsFrom called with null node, returning empty map");
            cir.setReturnValue(Collections.emptyMap());
        }
    }
}
