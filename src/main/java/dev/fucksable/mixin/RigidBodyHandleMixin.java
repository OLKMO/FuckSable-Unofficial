package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.physics.PhysicsPipelineBody;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 兜底拦截 RigidBodyHandle 对 PhysicsPipeline.getLinearVelocity / getAngularVelocity 的调用。
 * <p>
 * 现有的 RapierPhysicsPipelineMixin 在 HEAD 检查 activeSubLevels，但如果 body 在 activeSubLevels 中
 * 但 rapier native body 已被移除，HEAD 检查不生效，assertBodyValid 会抛 RuntimeException。
 * 本 mixin 在调用点用 try-catch 兜底，避免崩溃。
 * <p>
 * Fallback interception of RigidBodyHandle's calls to PhysicsPipeline.getLinearVelocity / getAngularVelocity.
 * The existing RapierPhysicsPipelineMixin checks activeSubLevels at HEAD, but if the body is still in
 * activeSubLevels while the rapier native body has been removed, the HEAD check does not take effect
 * and assertBodyValid throws RuntimeException. This mixin wraps the call with try-catch as a fallback.
 */
@Mixin(RigidBodyHandle.class)
public abstract class RigidBodyHandleMixin {

    @Redirect(
        method = "getLinearVelocity",
        at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;getLinearVelocity(Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;",
            remap = false),
        remap = false
    )
    private Vector3d fucksable$safeGetLinearVelocity(PhysicsPipeline pipeline, PhysicsPipelineBody body, Vector3d dest) {
        if (!FixRegistry.isEnabled("panic-guard")) {
            return pipeline.getLinearVelocity(body, dest);
        }
        try {
            return pipeline.getLinearVelocity(body, dest);
        } catch (RuntimeException e) {
            FuckSable.LOGGER.warn("Body has been removed, returning zero linear velocity");
            return dest.zero();
        }
    }

    @Redirect(
        method = "getAngularVelocity",
        at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;getAngularVelocity(Ldev/ryanhcode/sable/api/physics/PhysicsPipelineBody;Lorg/joml/Vector3d;)Lorg/joml/Vector3d;",
            remap = false),
        remap = false
    )
    private Vector3d fucksable$safeGetAngularVelocity(PhysicsPipeline pipeline, PhysicsPipelineBody body, Vector3d dest) {
        if (!FixRegistry.isEnabled("panic-guard")) {
            return pipeline.getAngularVelocity(body, dest);
        }
        try {
            return pipeline.getAngularVelocity(body, dest);
        } catch (RuntimeException e) {
            FuckSable.LOGGER.warn("Body has been removed, returning zero angular velocity");
            return dest.zero();
        }
    }
}
