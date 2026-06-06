package dev.fucksable.mixin;

import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;

/**
 * Aggressive optimization: Lowers collision block volume threshold from 500^3 to 200^3.
 * <p>
 * The original code skips collision detection when localBounds volume exceeds 500^3.
 * This mixin lowers that threshold to 200^3, preventing lag from large rotated structures
 * at the cost of potentially incomplete collision at the edges of large structures.
 */
@Mixin(SubLevelEntityCollision.class)
public class CollisionVolumeThresholdMixin {

    /**
     * Modify the volume threshold constant from 500*500*500 to 200*200*200.
     * The original code: if (localBounds.volume() > 500 * 500 * 500)
     */
    @ModifyConstant(
        method = "collide",
        constant = @Constant(intValue = 500),
        remap = false
    )
    private static int fucksable$modifyVolumeThreshold(int original) {
        if (!FixRegistry.isEnabled("collision-volume-threshold")) return original;
        return 200;
    }
}
