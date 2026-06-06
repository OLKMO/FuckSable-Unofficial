package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.CorruptedPointerCache;
import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 直接在 SubLevelStorage.attemptLoadSubLevel() 上拦截，
 * 已知损坏的指针直接返回 null，不进入方法内部（不触发错误日志）。
 */
@Mixin(SubLevelStorage.class)
public abstract class SubLevelStorageMixin {

    @Inject(method = "attemptLoadSubLevel", at = @At("HEAD"), cancellable = true, remap = false)
    private void fucksable$skipCorruptedPointer(ChunkPos chunkPos, SavedSubLevelPointer pointer, CallbackInfoReturnable<SubLevelData> cir) {
        if (!FixRegistry.isEnabled("corrupted-cleanup")) return;

        long chunkKey = chunkPos.toLong();
        if (CorruptedPointerCache.isCorrupted(chunkKey, pointer)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "attemptLoadSubLevel", at = @At("RETURN"), remap = false)
    private void fucksable$cacheFailedPointer(ChunkPos chunkPos, SavedSubLevelPointer pointer, CallbackInfoReturnable<SubLevelData> cir) {
        if (!FixRegistry.isEnabled("corrupted-cleanup")) return;

        if (cir.getReturnValue() == null) {
            long chunkKey = chunkPos.toLong();
            if (!CorruptedPointerCache.isCorrupted(chunkKey, pointer)) {
                CorruptedPointerCache.markCorrupted(chunkKey, pointer);
                FuckSable.LOGGER.warn("Corrupted sub-level pointer cached: {} in chunk {}", pointer, chunkPos);
            }
        }
    }
}
