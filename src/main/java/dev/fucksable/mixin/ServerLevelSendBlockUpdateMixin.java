package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 Sable 的 ServerChunkCache.blockChanged 在 plot holder 不存在时崩溃的问题。
 * <p>
 * 问题分析：
 * Sable 的 ServerChunkCacheMixin 用 @Inject 拦截 blockChanged，当 plot holder 不存在时
 * 直接 throw UnsupportedOperationException("Cannot change blocks in nonexistent plot holder")，
 * 导致服务器崩溃（如紫水晶生长、竹林扩散等随机刻触发的方块更新）。
 * <p>
 * 之前的 ServerChunkCacheBlockChangedMixin 用 @Overwrite 替换 blockChanged，
 * 但在 Sable 2.0.x 上 @Overwrite 与 Sable 的 @Inject 冲突，未能生效——
 * 错误堆栈仍显示 handler$cml000$sable$blockChanged 被调用并抛异常。
 * <p>
 * 修复方式：
 * 从调用方 ServerLevel.sendBlockUpdated 拦截，在 plot 范围内但 holder 不存在时 cancel，
 * 避免进入 blockChanged 触发 Sable 的异常。
 * sendBlockUpdated 是原版方法，Sable 未拦截，不与之冲突；
 * 且本方案在 Sable 1.x 和 2.x 上都有效（API 跨版本一致）。
 * <p>
 * 安全性：
 * plot holder 不存在说明该 plot 未加载，区块内无玩家，跳过 sendBlockUpdated（通知方块更新）
 * 不会影响玩家视角；plot 重新加载后会重新同步方块状态。
 */
@Mixin(ServerLevel.class)
public class ServerLevelSendBlockUpdateMixin {

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"), cancellable = true)
    private void fucksable$skipIfPlotHolderMissing(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("plot-holder-guard")) return;

        ServerLevel level = (ServerLevel) (Object) this;
        SubLevelContainer container = SubLevelContainer.getContainer(level);
        if (container == null) return;

        ChunkPos chunkPos = new ChunkPos(pos);
        if (!container.inBounds(chunkPos)) return;

        PlotChunkHolder holder = container.getChunkHolder(chunkPos);
        if (holder == null) {
            FuckSable.LOGGER.debug("Skipping sendBlockUpdated at {}: plot holder missing", pos);
            ci.cancel();
        }
    }
}
