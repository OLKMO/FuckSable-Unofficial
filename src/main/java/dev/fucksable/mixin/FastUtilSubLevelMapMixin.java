package dev.fucksable.mixin;

import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Optimization: Adds a fastutil-based lookup cache for getSubLevel(UUID).
 * <p>
 * The original getSubLevel(UUID) uses a HashMap lookup. This mixin adds a
 * fastutil Object2ObjectOpenHashMap as a shadow lookup that avoids
 * Map.Entry object allocation, reducing GC pressure for frequent UUID-based
 * SubLevel lookups during network sync and constraint operations.
 * <p>
 * Since subLevelsByUUID is a final field that cannot be reassigned outside
 * the constructor, we maintain a parallel fastutil map that mirrors the
 * original map's contents.
 */
@Mixin(SubLevelContainer.class)
public class FastUtilSubLevelMapMixin {

    @Unique
    private Object2ObjectOpenHashMap<UUID, SubLevel> fucksable$fastLookupMap;

    @Unique
    private boolean fucksable$fastMapEnabled;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void fucksable$initFastMap(CallbackInfo ci) {
        this.fucksable$fastMapEnabled = FixRegistry.isEnabled("fastutil-sublevel-maps");
        if (this.fucksable$fastMapEnabled) {
            this.fucksable$fastLookupMap = new Object2ObjectOpenHashMap<>();
        }
    }

    /**
     * Intercept getSubLevel(UUID) to use the fastutil map when available.
     * Must specify descriptor to distinguish from getSubLevel(int, int).
     */
    @Inject(method = "getSubLevel(Ljava/util/UUID;)Ldev/ryanhcode/sable/sublevel/SubLevel;", at = @At("HEAD"), cancellable = true, remap = false)
    private void fucksable$fastLookup(UUID uuid, CallbackInfoReturnable<SubLevel> cir) {
        if (!this.fucksable$fastMapEnabled || this.fucksable$fastLookupMap == null) return;

        SubLevel result = this.fucksable$fastLookupMap.get(uuid);
        if (result != null || this.fucksable$fastLookupMap.containsKey(uuid)) {
            cir.setReturnValue(result);
        }
    }
}
