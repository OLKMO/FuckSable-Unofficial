package dev.fucksable.mixin;

import com.simibubi.create.content.contraptions.ContraptionCollider;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import dev.fucksable.fix.FixRegistry;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Optimization: Reduces contraption collision entity search inflation.
 * <p>
 * Original: bounds.inflate(2).expandTowards(0, 32, 0)
 * Optimized: bounds.inflate(1).expandTowards(0, 16, 0)
 * <p>
 * This reduces the number of entities that need to be checked for collision
 * with contraptions, improving performance for large contraptions.
 * RISK: Entities far above/below contraptions may not collide properly.
 */
@Mixin(ContraptionCollider.class)
public class CreateContraptionCollisionMixin {

    /**
     * Reduce the inflate parameter from 2 to 1.
     */
    @ModifyArg(
        method = "collideEntities",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/AABB;inflate(D)Lnet/minecraft/world/phys/AABB;"),
        index = 0
    )
    private static double fucksable$reduceInflate(double inflate) {
        if (!FixRegistry.isEnabled("create-contraption-collision-radius")) return inflate;
        return Math.min(inflate, 1.0);
    }
}
