package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.CorruptedPointerCache;
import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SavedSubLevelPointer;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunkMap;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData;
import dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mixin(SubLevelHoldingChunkMap.class)
public abstract class SubLevelHoldingChunkMapMixin {

    @Unique
    private ExecutorService fucksable$ioExecutor;

    @Unique
    private CompletableFuture<Void> fucksable$pendingSave;

    @Unique
    private final ThreadLocal<Boolean> fucksable$onIoThread = ThreadLocal.withInitial(() -> false);

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void fucksable$init(CallbackInfo ci) {
        this.fucksable$ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "fuckSable Sub-Level I/O");
            t.setDaemon(true);
            return t;
        });
        this.fucksable$pendingSave = CompletableFuture.completedFuture(null);
    }

    // --- async-save 修复项 ---

    @Inject(method = "saveAll", at = @At("HEAD"), cancellable = true, remap = false)
    private void fucksable$redirectSaveAllToAsync(CallbackInfo ci) {
        if (!FixRegistry.isEnabled("async-save")) return;

        if (this.fucksable$onIoThread.get()) {
            return;
        }

        SubLevelHoldingChunkMap self = (SubLevelHoldingChunkMap) (Object) this;
        this.fucksable$pendingSave = this.fucksable$pendingSave
            .handle((result, ex) -> {
                if (ex != null) {
                    FuckSable.LOGGER.error("Previous sub-level save failed", ex);
                }
                return null;
            })
            .thenRunAsync(() -> {
                this.fucksable$onIoThread.set(true);
                try {
                    self.saveAll();
                } catch (Exception e) {
                    FuckSable.LOGGER.error("Async sub-level save failed", e);
                } finally {
                    this.fucksable$onIoThread.set(false);
                }
            }, this.fucksable$ioExecutor);
        ci.cancel();
    }

    @Redirect(
        method = "saveAll",
        at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelStorage;attemptSaveSubLevel(Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;)V", remap = false),
        remap = false
    )
    private void fucksable$wrapSaveSubLevel(SubLevelStorage storage, GlobalSavedSubLevelPointer pointer, SubLevelData data) {
        try {
            storage.attemptSaveSubLevel(pointer, data);
        } catch (Exception e) {
            FuckSable.LOGGER.error("Failed to save sub-level for pointer {}, skipping", pointer, e);
        }
    }

    @Redirect(
        method = "saveAll",
        at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelStorage;attemptSaveHoldingChunk(Lnet/minecraft/world/level/ChunkPos;Ldev/ryanhcode/sable/sublevel/storage/holding/SubLevelHoldingChunk;)V", remap = false),
        remap = false
    )
    private void fucksable$wrapSaveHoldingChunk(SubLevelStorage storage, ChunkPos chunkPos, SubLevelHoldingChunk holdingChunk) {
        try {
            storage.attemptSaveHoldingChunk(chunkPos, holdingChunk);
        } catch (Exception e) {
            FuckSable.LOGGER.error("Failed to save holding chunk at {}, skipping", chunkPos, e);
        }
    }

    // --- corrupted-cleanup 修复项 ---

    /**
     * 在 getOrLoadHoldingChunk 返回前，移除所有已知损坏的指针。
     * 损坏检测和缓存已在 SubLevelStorageMixin 中完成。
     */
    @Inject(method = "getOrLoadHoldingChunk", at = @At("RETURN"), remap = false)
    private void fucksable$cleanupCorruptedPointers(ChunkPos chunkPos, boolean create, CallbackInfoReturnable<SubLevelHoldingChunk> cir) {
        if (!FixRegistry.isEnabled("corrupted-cleanup")) return;

        SubLevelHoldingChunk loadedChunk = cir.getReturnValue();
        if (loadedChunk == null) return;

        Set<SavedSubLevelPointer> knownCorrupted = CorruptedPointerCache.getCorrupted(chunkPos.toLong());
        if (knownCorrupted != null && !knownCorrupted.isEmpty()) {
            List<SavedSubLevelPointer> pointers = loadedChunk.getSubLevelPointers();
            int before = pointers.size();
            pointers.removeAll(knownCorrupted);
            int removed = before - pointers.size();
            if (removed > 0) {
                FuckSable.LOGGER.info("Removed {} corrupted pointer(s) from holding chunk at {}", removed, chunkPos);
            }
        }
    }

    @Inject(method = "close", at = @At("HEAD"), remap = false)
    private void fucksable$awaitBeforeClose(CallbackInfo ci) {
        if (this.fucksable$pendingSave != null) {
            this.fucksable$pendingSave.join();
        }
        if (this.fucksable$ioExecutor != null) {
            this.fucksable$ioExecutor.shutdown();
        }
    }
}
