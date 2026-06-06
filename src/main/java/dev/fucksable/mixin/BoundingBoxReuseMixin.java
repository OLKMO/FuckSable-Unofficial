package dev.fucksable.mixin;

import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Optimization: Reuses BoundingBox3d objects in hot paths via ThreadLocal pools.
 * <p>
 * Instead of creating new BoundingBox3d instances in frequently called methods,
 * this mixin adds ThreadLocal-cached instances to BoundingBox3d that can be
 * accessed via the injected getCachedInstance()/getCachedInstance2() methods.
 */
@Mixin(BoundingBox3d.class)
public class BoundingBoxReuseMixin {

    @Unique
    private static final ThreadLocal<BoundingBox3d> fucksable$CACHED_BBOX = ThreadLocal.withInitial(BoundingBox3d::new);

    @Unique
    private static final ThreadLocal<BoundingBox3d> fucksable$CACHED_BBOX2 = ThreadLocal.withInitial(BoundingBox3d::new);

    @Unique
    private static BoundingBox3d fucksable$getCached() {
        if (!FixRegistry.isEnabled("bbox-object-reuse")) {
            return new BoundingBox3d();
        }
        return fucksable$CACHED_BBOX.get();
    }

    @Unique
    private static BoundingBox3d fucksable$getCached2() {
        if (!FixRegistry.isEnabled("bbox-object-reuse")) {
            return new BoundingBox3d();
        }
        return fucksable$CACHED_BBOX2.get();
    }
}
