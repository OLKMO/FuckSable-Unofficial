package dev.fucksable.mixin;

import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Optimization: Replaces HashMap with fastutil Object2ObjectOpenHashMap for subLevelsByUUID.
 * <p>
 * fastutil's open hash map avoids Map.Entry object allocation, reducing GC pressure
 * and improving lookup performance for UUID-based SubLevel lookups which happen
 * frequently during network sync and constraint operations.
 */
@Mixin(SubLevelContainer.class)
public class FastUtilSubLevelMapMixin {

    @Shadow(remap = false)
    private Map<UUID, SubLevel> subLevelsByUUID;

    /**
     * After construction, replace HashMap with fastutil Object2ObjectOpenHashMap.
     */
    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void fucksable$replaceMap(CallbackInfo ci) {
        if (!FixRegistry.isEnabled("fastutil-sublevel-maps")) return;

        Object2ObjectOpenHashMap<UUID, SubLevel> newMap = new Object2ObjectOpenHashMap<>();
        newMap.putAll(this.subLevelsByUUID);
        this.subLevelsByUUID = newMap;
    }
}
