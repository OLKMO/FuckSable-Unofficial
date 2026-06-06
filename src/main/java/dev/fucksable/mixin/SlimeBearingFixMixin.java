package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.lang.reflect.Method;

/**
 * 修复粘液球粘连旋转轴承导致结构分离和穿模的问题。
 * <p>
 * 当粘液球紧邻旋转轴承时，canStickTo 检查允许粘液球将轴承面方向的方块
 * 拉入同一个结构。这导致旋转轴承的子结构被粘液球"粘连"进主结构，
 * 旋转时产生人为的分离和穿模效应。
 * <p>
 * 修复方式：拦截 SimAssemblyService.canStickTo，当其中一方是旋转轴承时返回 false。
 * <p>
 * 默认关闭，因为很多玩法基于这个 bug。
 */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.util.assembly.SimAssemblyContraption", remap = false)
public abstract class SlimeBearingFixMixin {

    /**
     * 拦截第一个 canStickTo 调用，当其中一方是旋转轴承时禁止粘连。
     */
    @Redirect(method = "moveBlock", at = @At(value = "INVOKE", target = "Ldev/simulated_team/simulated/service/SimAssemblyService;canStickTo(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)Z", ordinal = 0), remap = false)
    private static boolean fucksable$preventSlimeStickToBearing0(Object instance, BlockState stateA, BlockState stateB) {
        boolean original = invokeCanStickTo(instance, stateA, stateB);

        if (!FixRegistry.isEnabled("aeronautics-slime-bearfix")) {
            return original;
        }

        if (!original) {
            return false;
        }

        if (isSwivelBearing(stateA) || isSwivelBearing(stateB)) {
            FuckSable.LOGGER.debug("Slime-bearing fix: preventing stick between {} and {}", stateA, stateB);
            return false;
        }

        return true;
    }

    /**
     * 拦截第二个 canStickTo 调用（反向检查）。
     */
    @Redirect(method = "moveBlock", at = @At(value = "INVOKE", target = "Ldev/simulated_team/simulated/service/SimAssemblyService;canStickTo(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;)Z", ordinal = 1), remap = false)
    private static boolean fucksable$preventSlimeStickToBearing1(Object instance, BlockState stateA, BlockState stateB) {
        boolean original = invokeCanStickTo(instance, stateA, stateB);

        if (!FixRegistry.isEnabled("aeronautics-slime-bearfix")) {
            return original;
        }

        if (!original) {
            return false;
        }

        if (isSwivelBearing(stateA) || isSwivelBearing(stateB)) {
            return false;
        }

        return true;
    }

    /**
     * 通过反射调用原始的 canStickTo 方法，避免编译时依赖 Simulated。
     */
    private static boolean invokeCanStickTo(Object instance, BlockState stateA, BlockState stateB) {
        try {
            Method method = instance.getClass().getMethod("canStickTo", BlockState.class, BlockState.class);
            return (boolean) method.invoke(instance, stateA, stateB);
        } catch (Exception e) {
            FuckSable.LOGGER.error("Slime-bearing fix: failed to invoke canStickTo via reflection", e);
            return false;
        }
    }

    /**
     * 通过类名检查方块是否为旋转轴承，避免直接引用 SimBlocks。
     */
    private static boolean isSwivelBearing(BlockState state) {
        String className = state.getBlock().getClass().getName();
        return className.contains("SwivelBearingBlock");
    }
}
