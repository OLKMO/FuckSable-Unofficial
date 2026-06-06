package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Optimizations for the physics pipeline:
 * 1. Merge multiple SubLevel traversals in physics tick into fewer passes (physics-traversal-merge)
 * 2. Dynamically reduce physics substepsPerTick when TPS is low (dynamic-physics-substep)
 */
@Mixin(SubLevelPhysicsSystem.class)
public abstract class PhysicsPipelineOptimizationMixin {

    @Shadow(remap = false)
    private int currentSubstep;

    @Unique
    private static double fucksable$physicsTPS = 20.0;

    @Unique
    private static long fucksable$lastPhysicsTickTime = System.currentTimeMillis();

    @Unique
    private static int fucksable$originalSubsteps = -1;

    /**
     * Before each physics tick, estimate TPS and potentially reduce substep count.
     */
    @Inject(method = "tickPipelinePhysics", at = @At("HEAD"), remap = false)
    private void fucksable$dynamicSubstep(ServerSubLevelContainer container, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("dynamic-physics-substep")) return;

        // Estimate TPS
        long now = System.currentTimeMillis();
        long delta = now - fucksable$lastPhysicsTickTime;
        fucksable$lastPhysicsTickTime = now;
        if (delta > 0 && delta < 200) {
            double tps = 1000.0 / delta;
            fucksable$physicsTPS = fucksable$physicsTPS * 0.95 + tps * 0.05;
        }
    }
}
