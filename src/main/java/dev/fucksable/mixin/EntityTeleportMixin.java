package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.plot.SubLevelContainerHolder;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 修复 CarryOn 放置玩家到物理化 sub-level 上导致 AABB 异常和崩溃的问题。
 * <p>
 * 问题分析：
 * Sable 的射线检测系统返回 sub-level 内的局部坐标。CarryOn 使用这个局部坐标
 * 调用 Entity.teleportTo，将玩家传送到局部坐标位置。Sable 的碰撞系统
 * (SubLevelInclusiveLevelEntityGetter) 检测到玩家在 sub-level 的全局 chunk 位置范围内，
 * 将玩家的 AABB 从"局部坐标"变换到全局坐标，但 AABB 已经是全局坐标（因为玩家
 * 在主世界中），变换后 AABB 被放大到异常大的值，导致崩溃。
 * <p>
 * 修复方式：
 * 在 teleportTo 头部注入，检查目标坐标是否在 sub-level 范围内。
 * 如果是，使用 sub-level 的 pose 将局部坐标投影到全局坐标。
 * <p>
 * 首先尝试 Sable 的 projectOutOfSubLevel（通过 getContaining 查找 sub-level），
 * 如果失败则遍历所有 sub-level 的 LevelPlot，检查目标坐标是否在 plot 的局部坐标范围内。
 * <p>
 * 使用方法描述符 (DDD)V 而非方法名来定位目标方法，
 * 以兼容无 refmap 的 SRG 映射环境（如 Youer 服务器）。
 */
@Mixin(Entity.class)
public abstract class EntityTeleportMixin {

    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void fucksable$projectSubLevelPosition(double x, double y, double z, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("carryon-compat")) return;

        Entity self = (Entity) (Object) this;
        if (!(self instanceof ServerPlayer player)) return;

        Level level = self.level();
        if (level == null) return;

        Vec3 targetPos = new Vec3(x, y, z);

        // 方法1：使用 Sable 的 projectOutOfSubLevel（通过 getContaining 查找 sub-level）
        Vec3 projected = Sable.HELPER.projectOutOfSubLevel(level, targetPos);
        if (!projected.equals(targetPos)) {
            FuckSable.LOGGER.debug("CarryOn compat: projected teleport via getContaining from ({}, {}, {}) to ({}, {}, {})",
                x, y, z, projected.x, projected.y, projected.z);
            self.setPos(projected.x, projected.y, projected.z);
            ci.cancel();
            return;
        }

        // 方法2：遍历所有 sub-level，检查目标坐标是否在 plot 的局部坐标范围内
        if (!(level instanceof SubLevelContainerHolder holder)) return;

        SubLevelContainer container = holder.sable$getPlotContainer();
        if (container == null) return;

        List<? extends SubLevel> subLevels = container.getAllSubLevels();
        for (SubLevel subLevel : subLevels) {
            LevelPlot plot = subLevel.getPlot();
            var plotBounds = plot.getBoundingBox();

            // 检查目标坐标是否在 plot 的局部坐标范围内
            if (x >= plotBounds.minX() - 1 && x <= plotBounds.maxX() + 2 &&
                y >= plotBounds.minY() - 1 && y <= plotBounds.maxY() + 2 &&
                z >= plotBounds.minZ() - 1 && z <= plotBounds.maxZ() + 2) {

                // 使用 sub-level 的 pose 将局部坐标投影到全局坐标
                Vec3 globalPos = JOMLConversion.toMojang(subLevel.logicalPose().transformPosition(JOMLConversion.toJOML(targetPos)));
                FuckSable.LOGGER.debug("CarryOn compat: projected teleport via plot scan from ({}, {}, {}) to ({}, {}, {})",
                    x, y, z, globalPos.x, globalPos.y, globalPos.z);
                self.setPos(globalPos.x, globalPos.y, globalPos.z);
                ci.cancel();
                return;
            }
        }
    }
}
