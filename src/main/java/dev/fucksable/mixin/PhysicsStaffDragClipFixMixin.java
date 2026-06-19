package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.border.WorldBorder;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 修复物理实体超出世界边界导致无法恢复的问题。
 * <p>
 * 问题分析：
 * Sable 的物理系统可能将 SubLevel 推出世界边界（XZ 平面和 Y 轴），
 * 一旦超出边界，物理实体无法被正常交互或恢复，导致永久丢失。
 * 此外，持续超出边界会导致每帧重复拉回和日志刷屏。
 * <p>
 * 修复方式：
 * 在 SubLevelPhysicsSystem.updatePose 之后检查 SubLevel 的位置是否超出世界边界，
 * 如果超出则将位置钳制到边界内，清零速度，并记录已超出边界的 SubLevel。
 * 已记录的 SubLevel 不再重复输出日志，防止刷屏。
 * 当 SubLevel 回到边界内时，自动从记录中移除。
 */
@Mixin(SubLevelPhysicsSystem.class)
public class PhysicsStaffDragClipFixMixin {

    @Shadow(remap = false)
    private ServerLevel level;

    private static final Set<UUID> CLAMPED_SUBLEVELS = ConcurrentHashMap.newKeySet();

    /**
     * 在 updatePose 完成后检查 SubLevel 位置是否超出世界边界。
     * 如果超出则钳制位置到边界内，只输出一次日志警告防止刷屏。
     */
    @Inject(method = "updatePose", at = @At("TAIL"), remap = false)
    private void fucksable$clampSubLevelToWorldBounds(ServerSubLevel serverSubLevel, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("physics-staff-drag-clipfix")) return;

        Vector3d position = serverSubLevel.logicalPose().position();
        WorldBorder border = this.level.getWorldBorder();

        double minX = border.getMinX() + 1.0;
        double maxX = border.getMaxX() - 1.0;
        double minZ = border.getMinZ() + 1.0;
        double maxZ = border.getMaxZ() - 1.0;

        // Y 轴边界：原版高度 + 1000 格富裕空间
        double minY = this.level.getMinBuildHeight() + 1.0;
        double maxY = this.level.getMaxBuildHeight() + 1000.0;

        boolean outOfBounds = false;
        double clampedX = position.x;
        double clampedY = position.y;
        double clampedZ = position.z;

        if (clampedX < minX) { clampedX = minX; outOfBounds = true; }
        if (clampedX > maxX) { clampedX = maxX; outOfBounds = true; }
        if (clampedZ < minZ) { clampedZ = minZ; outOfBounds = true; }
        if (clampedZ > maxZ) { clampedZ = maxZ; outOfBounds = true; }
        if (clampedY < minY) { clampedY = minY; outOfBounds = true; }
        if (clampedY > maxY) { clampedY = maxY; outOfBounds = true; }

        UUID subLevelId = serverSubLevel.getUniqueId();

        if (outOfBounds) {
            position.set(clampedX, clampedY, clampedZ);

            // 清零速度，防止下一帧继续飞出
            serverSubLevel.latestLinearVelocity.zero();
            serverSubLevel.latestAngularVelocity.zero();

            // 只在首次超出边界时输出日志，防止刷屏
            if (CLAMPED_SUBLEVELS.add(subLevelId)) {
                FuckSable.LOGGER.warn("SubLevel {} is out of world bounds, clamping position. Subsequent clamps will be silent.",
                    subLevelId);
            }
        } else {
            // 回到边界内，移除记录
            CLAMPED_SUBLEVELS.remove(subLevelId);
        }
    }
}
