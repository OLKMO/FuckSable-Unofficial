package dev.fucksable.mixin;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 Create 6.0.10 的 Train.detachFromTracks 在火车状态损坏时崩溃服务器的问题。
 * <p>
 * 问题分析：
 * 当铁轨被重新放置（TrackBlock.tick → TrackPropagator.onRailAdded → TrackGraph.removeNode）
 * 时，会触发 Train.detachFromTracks，对每个 TravellingPoint 创建 TrainMigration。
 * 但 TrainMigration 构造函数（TrainMigration.java:29）直接调用 point.edge.getLength()，
 * 未做 null 检查。当火车状态因 CTT 并发问题等原因损坏导致 point.edge 为 null 时，
 * 抛出 NullPointerException 导致服务器崩溃。
 * <p>
 * 修复方式：
 * 在 Train.lambda$detachFromTracks$22 入口处检查 point.edge 是否为 null，
 * 如果是则跳过该 point（不创建 TrainMigration）。
 * 这样损坏的 point 不会迁移到新轨道，但其他正常的 point 仍能正常迁移，
 * 避免 single point of failure 导致整个铁轨重布事件崩溃。
 */
@Mixin(value = Train.class, remap = false)
public class TrainDetachNullEdgeMixin {

    /**
     * 在 detachFromTracks 的 lambda 入口处拦截 edge 为 null 的 TravellingPoint。
     * <p>
     * 该 lambda（lambda$detachFromTracks$22）只做一件事：new TrainMigration(point) 并加到
     * migratingPoints 列表。如果 point.edge 为 null，跳过即可，不会影响其他 point 的迁移。
     */
    @Inject(method = "lambda$detachFromTracks$22", at = @At("HEAD"), cancellable = true, remap = false)
    private void fucksable$skipNullEdgePoint(TravellingPoint tp, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("create-train-detach-nulledge-guard")) {
            return;
        }
        if (tp == null || tp.edge == null) {
            FuckSable.LOGGER.warn("Train.detachFromTracks: skipping TravellingPoint with null edge (corrupted train state), migration skipped for this point");
            ci.cancel();
        }
    }
}
