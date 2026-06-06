package dev.fucksable.mixin;

import dev.fucksable.fix.FixRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optimization: Caches getInBlockState results when entity position hasn't changed significantly.
 * <p>
 * Avoids repeated SubLevel traversal for entities that haven't moved since the last check.
 * The cache is invalidated when the entity moves more than 0.5 blocks.
 */
@Mixin(Entity.class)
public abstract class EntityInBlockStateCacheMixin {

    @Shadow
    public abstract BlockPos blockPosition();

    @Unique
    private BlockState fucksable$cachedInBlockState;

    @Unique
    private int fucksable$lastCacheX = Integer.MIN_VALUE;

    @Unique
    private int fucksable$lastCacheY = Integer.MIN_VALUE;

    @Unique
    private int fucksable$lastCacheZ = Integer.MIN_VALUE;

    @Unique
    private static boolean fucksable$isCacheEnabled() {
        return FixRegistry.isEnabled("inblock-state-cache");
    }

    /**
     * Injects before getInBlockState to return cached result if position hasn't changed.
     * Note: This targets the Entity.getInBlockState method that Sable modifies.
     * The actual injection point may need adjustment based on Sable's mixin target.
     */
    @Inject(method = "getInBlockState", at = @At("HEAD"), cancellable = true)
    private void fucksable$cachedGetInBlockState(CallbackInfoReturnable<BlockState> cir) {
        if (!fucksable$isCacheEnabled()) return;

        BlockPos currentPos = this.blockPosition();
        if (currentPos.getX() == fucksable$lastCacheX &&
            currentPos.getY() == fucksable$lastCacheY &&
            currentPos.getZ() == fucksable$lastCacheZ &&
            fucksable$cachedInBlockState != null) {
            cir.setReturnValue(fucksable$cachedInBlockState);
        }
    }

    /**
     * Caches the result after getInBlockState completes.
     */
    @Inject(method = "getInBlockState", at = @At("TAIL"))
    private void fucksable$cacheInBlockStateResult(CallbackInfoReturnable<BlockState> cir) {
        if (!fucksable$isCacheEnabled()) return;

        BlockPos currentPos = this.blockPosition();
        fucksable$lastCacheX = currentPos.getX();
        fucksable$lastCacheY = currentPos.getY();
        fucksable$lastCacheZ = currentPos.getZ();
        fucksable$cachedInBlockState = cir.getReturnValue();
    }
}
