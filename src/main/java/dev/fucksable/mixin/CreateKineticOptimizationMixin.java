package dev.fucksable.mixin;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * Optimization: Caches stress/capacity calculation results in KineticNetwork.
 * <p>
 * The original code recalculates stress and capacity every time updateStress/updateCapacity
 * is called, which happens frequently. This mixin adds a dirty flag and only recalculates
 * when the network has actually changed.
 * <p>
 * Also replaces HashMap with fastutil Object2FloatOpenHashMap for better performance.
 */
@Mixin(KineticNetwork.class)
public class CreateKineticOptimizationMixin {

    @Shadow
    public Map<KineticBlockEntity, Float> sources;

    @Shadow
    public Map<KineticBlockEntity, Float> members;

    @Shadow
    private float currentCapacity;

    @Shadow
    private float currentStress;

    @Unique
    private boolean fucksable$stressDirty = true;

    @Unique
    private boolean fucksable$capacityDirty = true;

    /**
     * After constructor, replace HashMap with fastutil maps if enabled.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void fucksable$replaceMaps(CallbackInfo ci) {
        if (!FixRegistry.isEnabled("create-kinetic-cache")) return;

        // Replace with fastutil maps for better memory/performance
        Object2FloatOpenHashMap<KineticBlockEntity> newSources = new Object2FloatOpenHashMap<>();
        Object2FloatOpenHashMap<KineticBlockEntity> newMembers = new Object2FloatOpenHashMap<>();
        newSources.putAll(this.sources);
        newMembers.putAll(this.members);
        this.sources = newSources;
        this.members = newMembers;
    }

    /**
     * Mark dirty when capacity source changes.
     */
    @Inject(method = "updateCapacityFor", at = @At("HEAD"))
    private void fucksable$markCapacityDirty(KineticBlockEntity be, float capacity, CallbackInfo ci) {
        if (FixRegistry.isEnabled("create-kinetic-cache")) {
            fucksable$capacityDirty = true;
        }
    }

    /**
     * Mark dirty when stress member changes.
     */
    @Inject(method = "updateStressFor", at = @At("HEAD"))
    private void fucksable$markStressDirty(KineticBlockEntity be, float stress, CallbackInfo ci) {
        if (FixRegistry.isEnabled("create-kinetic-cache")) {
            fucksable$stressDirty = true;
        }
    }

    /**
     * Mark dirty when a member is added.
     */
    @Inject(method = "add", at = @At("HEAD"))
    private void fucksable$markDirtyOnAdd(KineticBlockEntity be, CallbackInfo ci) {
        if (FixRegistry.isEnabled("create-kinetic-cache")) {
            fucksable$stressDirty = true;
            fucksable$capacityDirty = true;
        }
    }

    /**
     * Mark dirty when a member is removed.
     */
    @Inject(method = "remove", at = @At("HEAD"))
    private void fucksable$markDirtyOnRemove(KineticBlockEntity be, CallbackInfo ci) {
        if (FixRegistry.isEnabled("create-kinetic-cache")) {
            fucksable$stressDirty = true;
            fucksable$capacityDirty = true;
        }
    }

    /**
     * Skip updateCapacity if not dirty.
     */
    @Inject(method = "updateCapacity", at = @At("HEAD"), cancellable = true)
    private void fucksable$skipCapacityIfClean(CallbackInfo ci) {
        if (!FixRegistry.isEnabled("create-kinetic-cache")) return;
        if (!fucksable$capacityDirty) {
            ci.cancel();
            return;
        }
        fucksable$capacityDirty = false;
    }

    /**
     * Skip updateStress if not dirty.
     */
    @Inject(method = "updateStress", at = @At("HEAD"), cancellable = true)
    private void fucksable$skipStressIfClean(CallbackInfo ci) {
        if (!FixRegistry.isEnabled("create-kinetic-cache")) return;
        if (!fucksable$stressDirty) {
            ci.cancel();
            return;
        }
        fucksable$stressDirty = false;
    }
}
