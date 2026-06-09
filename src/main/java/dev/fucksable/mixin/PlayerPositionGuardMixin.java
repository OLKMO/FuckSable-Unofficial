package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复玩家坐标超出世界边界导致服务器崩溃的问题。
 * <p>
 * 问题分析：
 * Sable 的 SubLevel 物理系统可能将玩家推出世界边界，
 * 导致后续的坐标计算和区块加载出现异常，最终引发服务器崩溃。
 * <p>
 * 修复方式：
 * 在 ServerPlayer.tick 中检查玩家坐标是否超出世界边界，
 * 如果超出则立刻将其拉回最近的边界点。
 */
@Mixin(ServerPlayer.class)
public class PlayerPositionGuardMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void fucksable$clampToWorldBorder(CallbackInfo ci) {
        if (!FixRegistry.isEnabled("player-position-guard")) return;

        ServerPlayer self = (ServerPlayer) (Object) this;
        Vec3 pos = self.position();

        // 世界边界检查
        WorldBorder border = self.level().getWorldBorder();
        double minX = border.getMinX() + 1.0;
        double maxX = border.getMaxX() - 1.0;
        double minZ = border.getMinZ() + 1.0;
        double maxZ = border.getMaxZ() - 1.0;

        // Y 轴边界（原版高度 + 1000 格富裕空间）
        double minY = self.level().getMinBuildHeight() + 1.0;
        double maxY = self.level().getMaxBuildHeight() + 1000.0;

        boolean outOfBounds = false;
        double clampedX = pos.x;
        double clampedY = pos.y;
        double clampedZ = pos.z;

        if (clampedX < minX) { clampedX = minX; outOfBounds = true; }
        if (clampedX > maxX) { clampedX = maxX; outOfBounds = true; }
        if (clampedZ < minZ) { clampedZ = minZ; outOfBounds = true; }
        if (clampedZ > maxZ) { clampedZ = maxZ; outOfBounds = true; }
        if (clampedY < minY) { clampedY = minY; outOfBounds = true; }
        if (clampedY > maxY) { clampedY = maxY; outOfBounds = true; }

        if (outOfBounds) {
            FuckSable.LOGGER.warn("Player {} was out of world bounds at ({}, {}, {}), clamping to ({}, {}, {})",
                self.getName().getString(), pos.x, pos.y, pos.z, clampedX, clampedY, clampedZ);
            self.setPos(clampedX, clampedY, clampedZ);
            self.setDeltaMovement(Vec3.ZERO);
        }
    }
}
