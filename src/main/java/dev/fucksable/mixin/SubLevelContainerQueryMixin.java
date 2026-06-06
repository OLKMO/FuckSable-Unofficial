package dev.fucksable.mixin;

import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optimization: Uses section-based spatial index for queryIntersecting instead of linear scan.
 * <p>
 * When enabled, delegates to SubLevelPhysicsSystem.getTicketManager().queryIntersecting()
 * which uses section-based indexing instead of iterating all SubLevels.
 * Falls back to linear scan if physics system is unavailable.
 */
@Mixin(SubLevelContainer.class)
public abstract class SubLevelContainerQueryMixin {

    @Unique
    private static boolean fucksable$isSpatialIndexEnabled() {
        return FixRegistry.isEnabled("spatial-index-query");
    }

    @Inject(method = "queryIntersecting", at = @At("HEAD"), cancellable = true, remap = false)
    private void fucksable$spatialIndexQuery(BoundingBox3dc bounds, CallbackInfoReturnable<Iterable<SubLevel>> cir) {
        if (!fucksable$isSpatialIndexEnabled()) return;

        // Get the physics system for this level and use its ticket manager's spatial index
        SubLevelPhysicsSystem physicsSystem = SubLevelPhysicsSystem.get(((SubLevelContainer)(Object)this).getLevel());
        if (physicsSystem != null) {
            Iterable<SubLevel> result = physicsSystem.getTicketManager().queryIntersecting(bounds);
            cir.setReturnValue(result);
        }
    }
}
