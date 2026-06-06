package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.sublevel.storage.HoldingSubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.SubLevelHoldingChunk;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

/**
 * corrupted-cleanup 修复项的一部分：
 * 移除损坏指针后，其他 sub-level 的依赖列表中仍引用其 UUID，
 * 导致 "Sub-level dependency does not exist in chunk" 错误刷屏。
 * 此 mixin 预先清理依赖缺失的 sub-level，避免错误日志。
 */
@Mixin(SubLevelHoldingChunk.class)
public abstract class SubLevelHoldingChunkMixin {

    @Shadow(remap = false)
    private final Object2ObjectMap<UUID, HoldingSubLevel> loadedHoldingSubLevels = new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>();

    /**
     * 在 collectReadySubLevels 执行前，预先移除依赖缺失的 sub-level，
     * 这样原始代码中的依赖检查就不会遇到 null，不会打印错误。
     */
    @Inject(method = "collectReadySubLevels", at = @At("HEAD"), remap = false)
    private void fucksable$pruneOrphanedDependencies(ServerLevel level, Object2ObjectMap<UUID, HoldingSubLevel> readySubLevels, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("corrupted-cleanup")) return;

        Set<UUID> orphans = new HashSet<>();
        for (Map.Entry<UUID, HoldingSubLevel> entry : this.loadedHoldingSubLevels.object2ObjectEntrySet()) {
            List<UUID> deps = entry.getValue().data().dependencies();
            for (UUID dep : deps) {
                if (!this.loadedHoldingSubLevels.containsKey(dep)) {
                    orphans.add(entry.getKey());
                    break;
                }
            }
        }

        if (!orphans.isEmpty()) {
            FuckSable.LOGGER.debug("Pruning {} orphaned sub-level(s) with missing dependencies", orphans.size());
            orphans.forEach(this.loadedHoldingSubLevels::remove);
        }
    
    }
}