package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复 PhysicsChunkTicketManager 在更新 ticket 时导致 DistanceManager
 * 内部数据结构损坏而崩溃的问题。
 * <p>
 * 问题分析：
 * Sable 的 PhysicsChunkTicketManager.update 在调用 distanceManager.addTicket 时，
 * 可能触发 DistanceManager 内部的 LeveledPriorityQueue 数据结构损坏，
 * 导致 ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 33。
 * <p>
 * 这通常发生在 SubLevel 物理结构移动到新的区块时，ticket 的添加/移除操作
 * 导致 DistanceManager 的内部队列状态不一致。
 * <p>
 * 修复方式：
 * 拦截 SubLevelPhysicsSystem.tick 中的 ticketManager.update 调用，
 * 捕获 ArrayIndexOutOfBoundsException 并记录日志而非崩溃。
 */
@Mixin(SubLevelPhysicsSystem.class)
public class PhysicsTicketGuardMixin {

    /**
     * 拦截 ticketManager.update 调用，捕获 DistanceManager 内部异常。
     * <p>
     * 原始调用：this.ticketManager.update(this.level, container, this, this.pipeline, 1.0 / 20.0)
     * @Redirect handler 必须包含所有调用参数，包括调用者实例和调用者对象(this)
     */
    @Redirect(
        method = "tick",
        at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/system/ticket/PhysicsChunkTicketManager;update(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;D)V"),
        remap = false
    )
    private void fucksable$safeTicketUpdate(PhysicsChunkTicketManager instance, net.minecraft.server.level.ServerLevel level,
                                            dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container,
                                            SubLevelPhysicsSystem system,
                                            dev.ryanhcode.sable.api.physics.PhysicsPipeline pipeline, double timeStep) {
        if (!FixRegistry.isEnabled("physics-ticket-guard")) {
            instance.update(level, container, system, pipeline, timeStep);
            return;
        }

        try {
            instance.update(level, container, system, pipeline, timeStep);
        } catch (ArrayIndexOutOfBoundsException e) {
            FuckSable.LOGGER.error("PhysicsChunkTicketManager update failed due to DistanceManager internal state corruption, skipping this tick", e);
        } catch (IllegalStateException e) {
            // 也捕获可能的并发修改异常
            if (e.getMessage() != null && (e.getMessage().contains("Full") || e.getMessage().contains("ticket") || e.getMessage().contains("chunk"))) {
                FuckSable.LOGGER.error("PhysicsChunkTicketManager update failed with state error, skipping this tick", e);
            } else {
                throw e;
            }
        }
    }
}
