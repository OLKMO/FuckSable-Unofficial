package dev.fucksable.mixin;

import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.api.math.LevelReusedVectors;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.ryanhcode.sable.util.LevelAccelerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optimization: Returns empty VoxelShape for air blocks before expensive getCollisionShape computation.
 * <p>
 * In the original collide() code:
 *   final BlockState state = accel.getBlockState(block);
 *   final VoxelShape voxelShape = getSubLevelEntityCollisionShape(..., state, ...);
 *   if (state.isAir()) { continue; }
 * <p>
 * The getCollisionShape call happens before the isAir check, causing unnecessary
 * VoxelShape computation for air blocks. This mixin intercepts getSubLevelEntityCollisionShape
 * to return Shapes.empty() for air blocks, avoiding the expensive computation.
 */
@Mixin(SubLevelEntityCollision.class)
public class CollisionSkipAirMixin {

    @Inject(method = "getSubLevelEntityCollisionShape", at = @At("HEAD"), cancellable = true, remap = false)
    private static void fucksable$skipAirCollisionShape(Entity entity, Vector3dc boundsCenter, Pose3dc subLevelPose, BlockState state, LevelAccelerator level, BlockPos pos, LevelReusedVectors sink, CallbackInfoReturnable<VoxelShape> cir) {
        if (!FixRegistry.isEnabled("collision-skip-air")) return;

        if (state.isAir()) {
            cir.setReturnValue(net.minecraft.world.phys.shapes.Shapes.empty());
        }
    }
}
