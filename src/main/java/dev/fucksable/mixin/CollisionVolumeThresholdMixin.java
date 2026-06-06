package dev.fucksable.mixin;

import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;

/**
 * Aggressive optimization: Lowers collision block volume threshold.
 * <p>
 * The original code skips collision detection when localBounds.volume() > 1.25E8 (500^3).
 * This mixin lowers that threshold to 8.0E6 (200^3), preventing lag from large rotated
 * structures at the cost of potentially incomplete collision at the edges of large structures.
 */
@Mixin(SubLevelEntityCollision.class)
public class CollisionVolumeThresholdMixin {

    /**
     * Modify the volume threshold constant from 500^3 (1.25E8) to 200^3 (8.0E6).
     * The original code: if (localBounds.volume() > 1.25E8)
     */
    @ModifyConstant(
        method = "collide",
        constant = @Constant(doubleValue = 1.25E8),
        remap = false
    )
    private static double fucksable$modifyVolumeThreshold(double original) {
        if (!FixRegistry.isEnabled("collision-volume-threshold")) return original;
        return 8.0E6; // 200^3
    }
}
