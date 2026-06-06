package dev.fucksable.mixin;

import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Optimization: Enables LevelAccelerator's Long2ObjectMap chunk cache.
 * <p>
 * The original code has USE_CACHE_MAP hardcoded to false, using only a single
 * chunk cache entry. This mixin overwrites getChunk to always use the cache map
 * when the optimization is enabled.
 * <p>
 * This improves chunk lookup hit rate during collision detection where many
 * different chunks are accessed in rapid succession.
 */
@Mixin(LevelAccelerator.class)
public class LevelAcceleratorCacheMixin {

    @Shadow(remap = false)
    private long cachedChunkPos;

    @Shadow(remap = false)
    private LevelChunk cachedChunkObj;

    @Shadow(remap = false)
    private it.unimi.dsi.fastutil.longs.Long2ObjectMap<LevelChunk> cachedLevelChunks;

    /**
     * Overwrite getChunk to always use the cache map when optimization is enabled.
     * This is equivalent to setting USE_CACHE_MAP = true but done via mixin.
     */
    @Overwrite(remap = false)
    public LevelChunk getChunk(final int chunkX, final int chunkZ) {
        final long pos = net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ);

        // Single-entry cache check (always)
        if (pos == this.cachedChunkPos && this.cachedChunkObj != null) {
            return this.cachedChunkObj;
        }

        final LevelChunk chunk;

        if (FixRegistry.isEnabled("level-accelerator-cache")) {
            // Use the full cache map
            chunk = this.cachedLevelChunks.computeIfAbsent(pos, x -> ((LevelAcceleratorAccessor)(Object)this).invokeGrabChunkFast(chunkX, chunkZ, pos));
        } else {
            // Original behavior: no cache map
            chunk = ((LevelAcceleratorAccessor)(Object)this).invokeGrabChunkFast(chunkX, chunkZ, pos);
        }

        this.cachedChunkObj = chunk;
        this.cachedChunkPos = pos;

        return chunk;
    }
}
