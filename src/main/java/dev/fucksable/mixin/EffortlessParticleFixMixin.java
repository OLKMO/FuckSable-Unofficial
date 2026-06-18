package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 Effortless 等模组在 Sable 物理结构上操作时客户端崩溃的问题。
 * <p>
 * Sable 的射线检测 (BlockGetterMixin.clip) 返回的 BlockHitResult 包含
 * Plot 存储区域的全局坐标（远端区块），而非玩家可见的世界坐标。
 * 当 Effortless 客户端使用该坐标调用 ParticleEngine.destroy() 或 crack() 时，
 * 客户端未加载该远端区块，导致崩溃。
 * <p>
 * 修复方式：在 ParticleEngine.destroy() 和 crack() 入口处检查 BlockPos
 * 对应的区块是否已在客户端加载。如果未加载，跳过粒子生成。
 * <p>
 * 这是安全的，因为未加载区块的粒子本来就不应该被渲染。
 */
@Mixin(ParticleEngine.class)
public abstract class EffortlessParticleFixMixin {

    @Shadow
    private ClientLevel level;

    /**
     * 拦截 destroy 方法，检查区块是否已加载。
     */
    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true)
    private void fucksable$checkChunkLoadedDestroy(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("effortless-particle-fix")) {
            return;
        }

        if (this.level == null) {
            return;
        }

        if (!this.level.hasChunkAt(pos)) {
            FuckSable.LOGGER.debug("Effortless particle fix: skipping destroy particle at {} (chunk not loaded)", pos);
            ci.cancel();
        }
    }

    /**
     * 拦截 crack 方法，检查区块是否已加载。
     * 新版 MC 中 crack 签名为 (BlockPos, Direction)，旧版为 (BlockHitResult)。
     * 使用宽松参数匹配以兼容不同版本。
     */
    @Inject(method = "crack", at = @At("HEAD"), cancellable = true)
    private void fucksable$checkChunkLoadedCrack(BlockPos pos, net.minecraft.core.Direction direction, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("effortless-particle-fix")) {
            return;
        }

        if (this.level == null) {
            return;
        }

        if (!this.level.hasChunkAt(pos)) {
            FuckSable.LOGGER.debug("Effortless particle fix: skipping crack particle at {} (chunk not loaded)", pos);
            ci.cancel();
        }
    }
}
