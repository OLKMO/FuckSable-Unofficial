package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 修复 Sable 的 ServerChunkCache.blockChanged 在 plot holder 不存在时崩溃的问题。
 * <p>
 * 问题分析：
 * Sable 的 ServerChunkCacheMixin.blockChanged 在 plot holder 不存在时
 * 直接 throw UnsupportedOperationException，导致服务器崩溃。
 * <p>
 * 之前的 @Inject 高优先级方案无效，因为 Sable 的 mixin handler 仍然注册在目标方法上，
 * 两个 @Inject at HEAD 都会执行，Sable 的低优先级注入仍会抛异常。
 * <p>
 * 之前的 @Overwrite 方案导致幽灵方块，因为不在 plot 范围内的区块
 * 直接 return 跳过了原版的方块更新通知。
 * <p>
 * 修复方式：
 * 使用 @Overwrite 完全替换 blockChanged 方法：
 * - 在 plot 范围内且 holder 存在 → 调用 holder.blockChanged()
 * - 在 plot 范围内且 holder 不存在 → 安全跳过（记录日志）
 * - 不在 plot 范围内 → 调用原版逻辑（通过 chunkMap 获取 ChunkHolder 并通知方块变化）
 */
@Mixin(ServerChunkCache.class)
public class ServerChunkCacheBlockChangedMixin {

    @Shadow
    @Final
    ServerLevel level;

    @Shadow
    private ChunkHolder getVisibleChunkIfPresent(long pos) {
        throw new AssertionError();
    }

    /**
     * @author FuckSable
     * @reason Fix crash when plot holder doesn't exist, and fix ghost blocks from previous @Overwrite
     */
    @Overwrite
    public void blockChanged(BlockPos blockPos) {
        SubLevelContainer container = SubLevelContainer.getContainer(this.level);

        if (container != null) {
            ChunkPos pos = new ChunkPos(blockPos);
            if (container.inBounds(pos)) {
                PlotChunkHolder holder = container.getChunkHolder(pos);

                if (holder == null) {
                    // holder 不存在，安全跳过而非崩溃
                    FuckSable.LOGGER.debug("Plot holder not found for block change at {}, skipping", blockPos);
                    return;
                }

                holder.blockChanged(blockPos);
                return;
            }
        }

        // 不在 plot 范围内，执行原版逻辑
        int cx = SectionPos.blockToSectionCoord(blockPos.getX());
        int cz = SectionPos.blockToSectionCoord(blockPos.getZ());
        ChunkHolder holder = this.getVisibleChunkIfPresent(ChunkPos.asLong(cx, cz));
        if (holder != null) {
            holder.blockChanged(blockPos);
        }
    }
}
