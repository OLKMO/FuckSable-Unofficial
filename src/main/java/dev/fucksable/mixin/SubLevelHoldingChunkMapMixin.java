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
import net.neoforged.fml.ModList;
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

    @Unique
    private CompletableFuture<Void> fucksable$pendingSave;

    @Unique
    private final ThreadLocal<Boolean> fucksable$onIoThread = ThreadLocal.withInitial(() -> false);

    // c2me 兼容：c2me 的 preventAsyncEntityUnload 会阻止异步线程卸载实体
    // c2me compat: c2me's preventAsyncEntityUnload blocks async entity unload
    @Unique
    private Boolean fucksable$c2mePresent;

    // c2me 存在时，收集主线程 saveAll 中的异步磁盘 IO future
    // When c2me is present, collect async disk IO futures from main-thread saveAll
    @Unique
    private List<CompletableFuture<Void>> fucksable$pendingIOFutures;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void fucksable$init(CallbackInfo ci) {
        this.fucksable$ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "fuckSable Sub-Level I/O");
            t.setDaemon(true);
            return t;
        });
        this.fucksable$pendingSave = CompletableFuture.completedFuture(null);
        this.fucksable$pendingIOFutures = new ArrayList<>();
    }

    /**
     * 延迟检测 c2me 是否存在。
     * c2me 的 preventAsyncEntityUnload mixin 会在异步线程调用 ChunkMap.removeEntity 时
     * 抛出 ConcurrentModificationException，阻止异步实体卸载。
     * 因此当 c2me 存在时，saveAll 不能整体放到异步线程，需要把 unload 留在主线程，
     * 只把磁盘 IO 放到异步线程。
     * <p>
     * Lazily detect whether c2me is present.
     * c2me's preventAsyncEntityUnload mixin throws ConcurrentModificationException
     * when ChunkMap.removeEntity is called from an async thread.
     * When c2me is present, saveAll cannot run entirely on async thread;
     * unload must stay on main thread, only disk IO goes async.
     */
    @Unique
    private boolean fucksable$isC2mePresent() {
        if (fucksable$c2mePresent == null) {
            ModList modList = ModList.get();
            fucksable$c2mePresent = modList != null && modList.isLoaded("c2me");
        }
        return fucksable$c2mePresent;
    }

    // --- async-save 修复项 ---

    @Inject(method = "saveAll", at = @At("HEAD"), cancellable = true, remap = false)
    private void fucksable$redirectSaveAllToAsync(CallbackInfo ci) {
        if (!FixRegistry.isEnabled("async-save")) return;

        // c2me 兼容：c2me 存在时，saveAll 在主线程执行，只把磁盘 IO 放异步线程
        // c2me compat: when c2me is present, saveAll runs on main thread, only disk IO goes async
        if (fucksable$isC2mePresent()) {
            return;
        }

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
        if (fucksable$isC2mePresent() && !this.fucksable$onIoThread.get()) {
            // c2me 存在且当前在主线程：把磁盘 IO 提交到异步线程，避免阻塞主线程
            // c2me present and on main thread: submit disk IO to async thread to avoid blocking main thread
            this.fucksable$pendingIOFutures.add(CompletableFuture.runAsync(() -> {
                try {
                    storage.attemptSaveSubLevel(pointer, data);
                } catch (Exception e) {
                    FuckSable.LOGGER.error("Failed to save sub-level for pointer {}, skipping", pointer, e);
                }
            }, this.fucksable$ioExecutor));
            return;
        }
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
        if (fucksable$isC2mePresent() && !this.fucksable$onIoThread.get()) {
            this.fucksable$pendingIOFutures.add(CompletableFuture.runAsync(() -> {
                try {
                    storage.attemptSaveHoldingChunk(chunkPos, holdingChunk);
                } catch (Exception e) {
                    FuckSable.LOGGER.error("Failed to save holding chunk at {}, skipping", chunkPos, e);
                }
            }, this.fucksable$ioExecutor));
            return;
        }
        try {
            storage.attemptSaveHoldingChunk(chunkPos, holdingChunk);
        } catch (Exception e) {
            FuckSable.LOGGER.error("Failed to save holding chunk at {}, skipping", chunkPos, e);
        }
    }

    /**
     * c2me 存在时，在 saveAll 返回前等待所有异步磁盘 IO 完成，保证数据落盘后再继续 unload。
     * <p>
     * When c2me is present, wait for all async disk IO to complete before saveAll returns,
     * ensuring data is flushed to disk before unload proceeds.
     */
    @Inject(method = "saveAll", at = @At("RETURN"), remap = false)
    private void fucksable$awaitPendingIO(CallbackInfo ci) {
        if (!fucksable$isC2mePresent()) return;
        if (this.fucksable$onIoThread.get()) return; // 异步线程中不等待
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
        if (this.fucksable$pendingSave != null) {
            this.fucksable$pendingSave.join();
        }
        if (this.fucksable$ioExecutor != null) {
            this.fucksable$ioExecutor.shutdown();
        }
    }
}
