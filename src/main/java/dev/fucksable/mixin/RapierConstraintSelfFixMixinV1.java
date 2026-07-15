package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.PhysicsConstraintHandle;
import dev.ryanhcode.sable.physics.impl.rapier.Rapier3D;
import dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sable 1.x 版本的 self-constraint 抑制。
 * <p>
 * Sable 1.x 中 RapierPhysicsPipeline.addConstraint 的参数类型为 ServerSubLevel。
 * 此 mixin 仅在 Sable 1.x 上启用（由 FuckSableMixinConfigPlugin 控制）。
 */
@Mixin(RapierPhysicsPipeline.class)
public abstract class RapierConstraintSelfFixMixinV1 {

    @Unique
    private static final Set<String> fucksable$v1SelfConstraintWarned = ConcurrentHashMap.newKeySet();

    @Inject(method = "addConstraint", at = @At("HEAD"), cancellable = true, remap = false)
    private void fucksable$suppressSelfConstraintV1(ServerSubLevel bodyA, ServerSubLevel bodyB, PhysicsConstraintConfiguration<?> configuration, CallbackInfoReturnable<PhysicsConstraintHandle> cir) {
        if (!FixRegistry.isEnabled("constraint-self-fix")) return;

        if (bodyA == bodyB && bodyA != null) {
            String key = String.valueOf(Rapier3D.getID(bodyA));
            if (fucksable$v1SelfConstraintWarned.add(key)) {
                FuckSable.LOGGER.warn("Suppressed self-constraint on body id={} (same SubLevel), returning null. This warning will not repeat.", key);
            }
            cir.setReturnValue(null);
        }
    }
}
