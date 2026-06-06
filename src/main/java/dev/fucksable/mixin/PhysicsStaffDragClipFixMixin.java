package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复物理手杖高速拖动物理结构时穿模穿透物理方块的问题。
 * <p>
 * PhysicsStaffServerHandler 使用 FreeConstraint 以弹簧力方式拖动物理结构。
 * 当手杖高速拖动时，弹簧力可能不足以在单个物理步长内阻止结构穿过其他物理碰撞体。
 * 虽然 Rapier 有 CCD（连续碰撞检测），但 CCD 的 max_ccd_substeps 仅为 3，
 * 对于高速移动的大型结构可能不够。
 * <p>
 * 修复方式：在 DragSession.physicsTick 中限制目标位置的变化速率，
 * 防止单帧内目标位置跳变过大导致物理引擎无法正确处理碰撞。
 */
@Pseudo
@Mixin(targets = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffServerHandler$DragSession", remap = false)
public abstract class PhysicsStaffDragClipFixMixin {

    @Shadow(remap = false)
    private Vector3d localGoal;

    // 上一帧的目标位置，用于限制变化速率
    @Unique
    private Vector3d fucksable$lastLocalGoal = null;

    // 每帧最大位移限制（方块单位），防止穿模
    @Unique
    private static final double FUCKSABLE$MAX_GOAL_DELTA = 2.0;

    /**
     * 在 physicsTick 的目标位置计算之后、设置 motor 之前，
     * 限制目标位置的变化速率。
     */
    @Inject(method = "physicsTick", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/constraint/PhysicsConstraintHandle;setMotor(Ldev/ryanhcode/sable/api/physics/constraint/ConstraintJointAxis;DDDZD)V", ordinal = 0, shift = At.Shift.BEFORE), remap = false)
    private void fucksable$limitGoalDelta(Object physicsSystem, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("physics-staff-drag-clipfix")) {
            return;
        }

        if (this.fucksable$lastLocalGoal != null) {
            double dx = this.localGoal.x - this.fucksable$lastLocalGoal.x;
            double dy = this.localGoal.y - this.fucksable$lastLocalGoal.y;
            double dz = this.localGoal.z - this.fucksable$lastLocalGoal.z;
            double delta = Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (delta > FUCKSABLE$MAX_GOAL_DELTA) {
                double scale = FUCKSABLE$MAX_GOAL_DELTA / delta;
                this.localGoal.set(
                    this.fucksable$lastLocalGoal.x + dx * scale,
                    this.fucksable$lastLocalGoal.y + dy * scale,
                    this.fucksable$lastLocalGoal.z + dz * scale
                );
                FuckSable.LOGGER.debug("Physics staff drag clip fix: clamped goal delta from {} to {}", delta, FUCKSABLE$MAX_GOAL_DELTA);
            }
        }

        this.fucksable$lastLocalGoal = new Vector3d(this.localGoal);
    }
}
