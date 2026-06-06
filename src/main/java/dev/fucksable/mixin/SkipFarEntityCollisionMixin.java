package dev.fucksable.mixin;

import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Aggressive optimization: Skips SubLevel collision detection for entities
 * far from any player.
 * <p>
 * Entities more than 64 blocks from the nearest player will have their
 * SubLevel collision detection skipped, saving significant CPU time in
 * scenarios with many entities near physics structures.
 * RISK: Entities may clip through SubLevels when no player is nearby.
 */
@Mixin(SubLevelEntityCollision.class)
public abstract class SkipFarEntityCollisionMixin {

    @Unique
    private static final double fucksable$MAX_DISTANCE_SQ = 64.0 * 64.0;

    @Unique
    private static boolean fucksable$isEnabled() {
        return FixRegistry.isEnabled("skip-far-entity-collision");
    }

    /**
     * Skip collision for entities far from any player.
     */
    @Inject(method = "collide", at = @At("HEAD"), cancellable = true, remap = false)
    private static void fucksable$skipFarEntity(Entity entity, Vec3 collisionMotionMoj, Vec3 velocityMotionMoj, LevelReusedVectors sink, CallbackInfoReturnable<SubLevelEntityCollision.CollisionInfo> cir) {
        if (!fucksable$isEnabled()) return;
        if (entity instanceof Player) return; // Never skip players

        // Check distance to nearest player
        List<? extends Player> players = entity.level().players();
        for (Player player : players) {
            if (entity.distanceToSqr(player) < fucksable$MAX_DISTANCE_SQ) {
                return; // Near a player, don't skip
            }
        }

        // Far from all players, skip collision
        SubLevelEntityCollision.CollisionInfo info = new SubLevelEntityCollision.CollisionInfo();
        info.motion = collisionMotionMoj.add(velocityMotionMoj);
        cir.setReturnValue(info);
    }
}
