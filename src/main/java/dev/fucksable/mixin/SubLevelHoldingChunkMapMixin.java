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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mixin(SubLevelHoldingChunkMap.class)
public abstract class SubLevelHoldingChunkMapMixin {

    @Unique
    private ExecutorService fucksable$ioExecutor;

    // 收集 saveAll 中的异步磁盘 IO future
    // Collect async disk IO futures from saveAll
    @Unique
    private List<CompletableFuture<Void>> fucksable$pendingIOFutures;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void fucksable$init(CallbackInfo ci) {
        this.fucksable$ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "fuckSable Sub-Level I/O");
            t.setDaemon(true);
            return t;
        });
        this.fucksable$pendingIOFutures = new ArrayList<>();
    }

    // --- async-save 修复项 ---
    // 注意：PalettedContainer 不是线程安全的（有 ThreadingDetector），不能在异步线程调用 ServerLevelPlot.save
    // 因此 saveAll 的序列化部分（含 PalettedContainer.pack）在主线程执行，只把磁盘 IO 放异步线程
    // Note: PalettedContainer is not thread-safe (has ThreadingDetector), ServerLevelPlot.save cannot run on async thread
    // So serialization (including PalettedContainer.pack) runs on main thread, only disk IO goes async

    @Redirect(
        method = "saveAll",
        at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelStorage;attemptSaveSubLevel(Ldev/ryanhcode/sable/sublevel/storage/holding/GlobalSavedSubLevelPointer;Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelData;)V", remap = false),
        remap = false
    )
    private void fucksable$wrapSaveSubLevel(SubLevelStorage storage, GlobalSavedSubLevelPointer pointer, SubLevelData data) {
        if (!FixRegistry.isEnabled("async-save")) {
            try {
                storage.attemptSaveSubLevel(pointer, data);
            } catch (Exception e) {
                FuckSable.LOGGER.error("Failed to save sub-level for pointer {}, skipping", pointer, e);
            }
            return;
        }
        // 把磁盘 IO 提交到异步线程，避免阻塞主线程
        // Submit disk IO to async thread to avoid blocking main thread
        this.fucksable$pendingIOFutures.add(CompletableFuture.runAsync(() -> {
            try {
                storage.attemptSaveSubLevel(pointer, data);
            } catch (Exception e) {
                FuckSable.LOGGER.error("Failed to save sub-level for pointer {}, skipping", pointer, e);
            }
        }, this.fucksable$ioExecutor));
    }

    @Redirect(
        method = "saveAll",
        at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/storage/serialization/SubLevelStorage;attemptSaveHoldingChunk(Lnet/minecraft/world/level/ChunkPos;Ldev/ryanhcode/sable/sublevel/storage/holding/SubLevelHoldingChunk;)V", remap = false),
        remap = false
    )
    private void fucksable$wrapSaveHoldingChunk(SubLevelStorage storage, ChunkPos chunkPos, SubLevelHoldingChunk holdingChunk) {
        if (!FixRegistry.isEnabled("async-save")) {
            try {
                storage.attemptSaveHoldingChunk(chunkPos, holdingChunk);
            } catch (Exception e) {
                FuckSable.LOGGER.error("Failed to save holding chunk at {}, skipping", chunkPos, e);
            }
            return;
        }
        this.fucksable$pendingIOFutures.add(CompletableFuture.runAsync(() -> {
            try {
                storage.attemptSaveHoldingChunk(chunkPos, holdingChunk);
            } catch (Exception e) {
                FuckSable.LOGGER.error("Failed to save holding chunk at {}, skipping", chunkPos, e);
            }
        }, this.fucksable$ioExecutor));
    }

    /**
     * 在 saveAll 返回前等待所有异步磁盘 IO 完成，保证数据落盘后再继续 unload。
     * <p>
     * Wait for all async disk IO to complete before saveAll returns,
     * ensuring data is flushed to disk before unload proceeds.
     */
    @Inject(method = "saveAll", at = @At("RETURN"), remap = false)
    private void fucksable$awaitPendingIO(CallbackInfo ci) {
        if (!FixRegistry.isEnabled("async-save")) return;
        if (this.fucksable$pendingIOFutures.isEmpty()) return;

        CompletableFuture<Void> all = CompletableFuture.allOf(
            this.fucksable$pendingIOFutures.toArray(new CompletableFuture[0])
        );
        this.fucksable$pendingIOFutures.clear();
        try {
            all.join();
        } catch (Exception e) {
            FuckSable.LOGGER.error("Failed to wait for async sub-level disk IO", e);
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
        if (this.fucksable$ioExecutor != null) {
            this.fucksable$ioExecutor.shutdown();
        }
    }
}
