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
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * 修复 CarryOn 放置玩家到物理化 sub-level 上导致 AABB 异常和崩溃的问题。
 * <p>
 * 问题分析：
 * Sable 的射线检测系统返回 sub-level 内的局部坐标。CarryOn 使用这个局部坐标
 * 计算放置位置，然后调用 Entity.teleportTo 将玩家传送到局部坐标位置。
 * Sable 的碰撞系统 (SubLevelInclusiveLevelEntityGetter) 检测到玩家在 sub-level 内，
 * 将 AABB 从局部坐标变换到全局坐标，产生异常大的 AABB，导致崩溃。
 * <p>
 * 修复方式：
 * 在 CarryOn 的 PlacementHandler.tryPlaceEntity 方法中，修改 placementPos 变量。
 * 如果目标位置在 sub-level 的局部坐标范围内，将局部坐标投影到全局坐标。
 */
@Mixin(targets = "tschipp.carryon.common.carry.PlacementHandler", remap = false)
public class PlacementHandlerMixin {

    /**
     * 修改 placementPos 变量，将 sub-level 局部坐标投影到全局坐标。
     * <p>
     * 拦截 Vec3 placementPos = Vec3.atBottomCenterOf(pos) 之后的赋值，
     * 检查 placementPos 是否在 sub-level 的局部坐标范围内，
     * 如果是，使用 sub-level 的 pose 将局部坐标投影到全局坐标。
     */
    @ModifyVariable(
        method = "tryPlaceEntity",
        at = @At(value = "STORE", ordinal = 0),
        ordinal = 0,
        remap = false
    )
    private static Vec3 fucksable$projectPlacementPos(Vec3 placementPos, ServerPlayer player) {
        if (!FixRegistry.isEnabled("carryon-compat")) return placementPos;

        try {
            var level = player.serverLevel();

            // 方法1：使用 Sable 的 projectOutOfSubLevel
            Vec3 projected = Sable.HELPER.projectOutOfSubLevel(level, placementPos);
            if (!projected.equals(placementPos)) {
                FuckSable.LOGGER.info("CarryOn compat: projected placement pos via getContaining from ({}, {}, {}) to ({}, {}, {})",
                    placementPos.x, placementPos.y, placementPos.z, projected.x, projected.y, projected.z);
                return projected;
            }

            // 方法2：遍历所有 sub-level，检查目标坐标是否在 plot 的局部坐标范围内
            if (!(level instanceof SubLevelContainerHolder holder)) return placementPos;

            SubLevelContainer container = holder.sable$getPlotContainer();
            if (container == null) return placementPos;

            List<? extends SubLevel> subLevels = container.getAllSubLevels();
            for (SubLevel subLevel : subLevels) {
                LevelPlot plot = subLevel.getPlot();
                var plotBounds = plot.getBoundingBox();

                double x = placementPos.x;
                double y = placementPos.y;
                double z = placementPos.z;

                if (x >= plotBounds.minX() - 1 && x <= plotBounds.maxX() + 2 &&
                    y >= plotBounds.minY() - 1 && y <= plotBounds.maxY() + 2 &&
                    z >= plotBounds.minZ() - 1 && z <= plotBounds.maxZ() + 2) {

                    Vec3 globalPos = JOMLConversion.toMojang(subLevel.logicalPose().transformPosition(JOMLConversion.toJOML(placementPos)));
                    FuckSable.LOGGER.info("CarryOn compat: projected placement pos via plot scan from ({}, {}, {}) to ({}, {}, {})",
                        x, y, z, globalPos.x, globalPos.y, globalPos.z);
                    return globalPos;
                }
            }
        } catch (Exception e) {
            FuckSable.LOGGER.error("CarryOn compat: error projecting placement position, using original", e);
        }

        return placementPos;
    }
}
