package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 Sable 的 ServerChunkCache.blockChanged 在 plot holder 不存在时崩溃的问题。
 * <p>
 * 问题分析：
 * Sable 的 ServerChunkCacheMixin.blockChanged 在检测到方块位置在 plot 范围内时，
 * 会尝试获取 PlotChunkHolder。但如果 holder 不存在（例如物理化结构已卸载但区块仍在 plot 范围内），
 * 直接抛出 UnsupportedOperationException，导致服务器崩溃。
 * <p>
 * 典型触发场景：竹子在物理化结构上方的全局区块中生长，该区块恰好落在 plot 范围内但 holder 不存在。
 * <p>
 * 修复方式：
 * 在 Sable 的 blockChanged 注入之前拦截，当 holder 不存在时安全跳过而非崩溃。
 */
@Mixin(ServerChunkCache.class)
public class ServerChunkCacheBlockChangedMixin {

    @Inject(method = "blockChanged", at = @At("HEAD"), cancellable = true)
    private void fucksable$safeBlockChanged(BlockPos blockPos, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("plot-holder-guard")) return;

        SubLevelContainer container = SubLevelContainer.getContainer(((ServerChunkCache) (Object) this).level);
        if (container == null) return;

        ChunkPos pos = new ChunkPos(blockPos);
        if (container.inBounds(pos)) {
            PlotChunkHolder holder = container.getChunkHolder(pos);
            if (holder == null) {
                // holder 不存在，安全跳过而非崩溃
                FuckSable.LOGGER.debug("Plot holder not found for block change at {}, skipping", blockPos);
                ci.cancel();
            }
        }
    }
}
