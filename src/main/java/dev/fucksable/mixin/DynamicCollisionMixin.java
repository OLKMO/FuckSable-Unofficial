package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Aggressive optimization: Dynamically reduces collision substeps when TPS is low,
 * and skips collision for non-critical entities.
 */
@Mixin(SubLevelEntityCollision.class)
public abstract class DynamicCollisionMixin {

    @Unique
    private static double fucksable$currentTPS = 20.0;

    @Unique
    private static long fucksable$lastTickTime = System.currentTimeMillis();

    /**
     * Dynamically reduce substeps based on TPS.
     * Targets the substeps variable: int substeps = Math.min(10, Math.max(1, (int)(collisionMotion.length() / (0.25 / 16.0))));
     */
    @ModifyVariable(
        method = "collide",
        at = @At(value = "STORE", ordinal = 0),
        ordinal = 0,
        remap = false
    )
    private static int fucksable$modifySubsteps(int substeps) {
        if (!FixRegistry.isEnabled("dynamic-collision-substep")) return substeps;

        // Update TPS estimate
        long now = System.currentTimeMillis();
        long delta = now - fucksable$lastTickTime;
        fucksable$lastTickTime = now;
        if (delta > 0 && delta < 200) {
            double tps = 1000.0 / delta;
            fucksable$currentTPS = fucksable$currentTPS * 0.9 + tps * 0.1;
        }

        // Reduce substeps when TPS is below threshold
        if (fucksable$currentTPS < 18.0) {
            int maxSubsteps = fucksable$currentTPS < 14.0 ? 2 : 4;
            substeps = Math.min(substeps, maxSubsteps);
        }
        return substeps;
    }

    /**
     * Skip collision for non-critical entities (items, XP orbs, projectiles).
     */
    @Inject(method = "collide", at = @At("HEAD"), cancellable = true, remap = false)
    private static void fucksable$skipNonCriticalEntity(Entity entity, Vec3 collisionMotionMoj, Vec3 velocityMotionMoj, LevelReusedVectors sink, CallbackInfoReturnable<SubLevelEntityCollision.CollisionInfo> cir) {
        if (!FixRegistry.isEnabled("skip-noncritical-collision")) return;

        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
            SubLevelEntityCollision.CollisionInfo info = new SubLevelEntityCollision.CollisionInfo();
            info.motion = collisionMotionMoj.add(velocityMotionMoj);
            cir.setReturnValue(info);
        }
    }
}
