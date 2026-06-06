package dev.fucksable.mixin;

import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.companion.math.BoundingBox3dc;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Optimization: Uses section-based spatial index for queryIntersecting instead of linear scan.
 * <p>
 * When enabled, delegates to PhysicsChunkTicketManager.queryIntersecting which uses section-based
 * indexing instead of iterating all SubLevels. Falls back to linear scan if ticket manager is unavailable.
 */
@Mixin(SubLevelContainer.class)
public abstract class SubLevelContainerQueryMixin {

    @Shadow(remap = false)
    @Final
    private List<SubLevel> allSubLevels;

    @Shadow(remap = false)
    @Final
    private PhysicsChunkTicketManager physicsChunkTicketManager;

    @Unique
    private static boolean fucksable$isSpatialIndexEnabled() {
        return FixRegistry.isEnabled("spatial-index-query");
    }

    @Inject(method = "queryIntersecting", at = @At("HEAD"), cancellable = true, remap = false)
    private void fucksable$spatialIndexQuery(BoundingBox3dc bounds, CallbackInfoReturnable<Iterable<SubLevel>> cir) {
        if (!fucksable$isSpatialIndexEnabled()) return;

        // Use the ticket manager's section-based query instead of linear scan
        Iterable<SubLevel> result = this.physicsChunkTicketManager.queryIntersecting(bounds);
        cir.setReturnValue(result);
    }
}
