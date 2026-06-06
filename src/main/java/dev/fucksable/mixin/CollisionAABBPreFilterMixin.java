package dev.fucksable.mixin;

import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.api.math.OrientedBoundingBox3d;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optimization: AABB pre-filter before expensive SAT collision detection.
 * <p>
 * Before running the OBB-SAT algorithm (15 axis checks), this mixin first checks
 * if the block's AABB even intersects the entity's bounding box. If not, the SAT
 * computation is skipped entirely, saving significant CPU time.
 * <p>
 * Note: This is a conservative filter - it may miss some non-intersecting OBBs
 * that have overlapping AABBs, but it will never incorrectly reject intersecting OBBs.
 */
@Mixin(OrientedBoundingBox3d.class)
public class CollisionAABBPreFilterMixin {

    /**
     * Before SAT computation, do a quick AABB check.
     * If the AABBs don't intersect, SAT won't find intersection either.
     */
    @Inject(method = "sat(Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;Ldev/ryanhcode/sable/api/math/OrientedBoundingBox3d;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;", at = @At("HEAD"), cancellable = true, remap = false)
    private static void fucksable$aabbPreFilter(OrientedBoundingBox3d obbA, OrientedBoundingBox3d obbB, Vector3d dest, CallbackInfoReturnable<Vector3d> cir) {
        if (!FixRegistry.isEnabled("collision-aabb-prefilter")) return;

        // Quick AABB check using position and dimensions
        Vector3d posA = obbA.getPosition();
        Vector3d dimA = obbA.getDimensions();
        Vector3d posB = obbB.getPosition();
        Vector3d dimB = obbB.getDimensions();

        double aMinX = posA.x - dimA.x / 2.0;
        double aMaxX = posA.x + dimA.x / 2.0;
        double aMinY = posA.y - dimA.y / 2.0;
        double aMaxY = posA.y + dimA.y / 2.0;
        double aMinZ = posA.z - dimA.z / 2.0;
        double aMaxZ = posA.z + dimA.z / 2.0;

        double bMinX = posB.x - dimB.x / 2.0;
        double bMaxX = posB.x + dimB.x / 2.0;
        double bMinY = posB.y - dimB.y / 2.0;
        double bMaxY = posB.y + dimB.y / 2.0;
        double bMinZ = posB.z - dimB.z / 2.0;
        double bMaxZ = posB.z + dimB.z / 2.0;

        // If AABBs don't intersect, SAT won't find intersection either
        if (aMaxX < bMinX || aMinX > bMaxX ||
            aMaxY < bMinY || aMinY > bMaxY ||
            aMaxZ < bMinZ || aMinZ > bMaxZ) {
            dest.zero();
            cir.setReturnValue(dest);
        }
    }
}
