package dev.fucksable.mixin;

import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accessor interface for LevelAccelerator's private grabChunkFast method.
 */
@Mixin(LevelAccelerator.class)
public interface LevelAcceleratorAccessor {

    @Invoker(value = "grabChunkFast", remap = false)
    LevelChunk invokeGrabChunkFast(int chunkX, int chunkZ, long pos);
}
