package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.api.block.BlockSubLevelLiftProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复 Copycats mod 的 CopycatStackedHalfLayerBlock 在 Sable 的
 * onBlockChange 中崩溃的问题。
 * <p>
 * 问题分析：
 * CopycatStackedHalfLayerBlock 实现了 BlockSubLevelLiftProvider 接口，
 * 但其 sable$getNormal() 方法尝试获取 facing 属性，而
 * copycat_stacked_half_layer 方块没有该属性，导致 IllegalArgumentException。
 * <p>
 * 修复方式：
 * 在 onBlockChange 中 @Redirect 拦截 sable$getNormal 调用，
 * 捕获 IllegalArgumentException 并返回默认方向（UP）。
 */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot", remap = false)
public class ServerLevelPlotBlockChangeMixin {

    /**
     * 拦截 onBlockChange 中的 sable$getNormal 调用，
     * 当抛出 IllegalArgumentException 时返回 Direction.UP 作为默认值。
     */
    @Redirect(
        method = "onBlockChange",
        at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/block/BlockSubLevelLiftProvider;sable$getNormal(Lnet/minecraft/world/level/block/state/BlockState;)Lnet/minecraft/core/Direction;"),
        remap = false
    )
    private Direction fucksable$safeGetNormal(BlockSubLevelLiftProvider instance, BlockState state) {
        if (!FixRegistry.isEnabled("copycats-lift-compat")) {
            return instance.sable$getNormal(state);
        }
        try {
            return instance.sable$getNormal(state);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().contains("does not exist in")) {
                FuckSable.LOGGER.debug("Copycats compatibility: sable$getNormal failed for {} - returning UP as fallback", state, e);
                return Direction.UP;
            }
            throw e;
        }
    }
}
