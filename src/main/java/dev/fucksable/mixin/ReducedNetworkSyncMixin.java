package dev.fucksable.mixin;

import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.system.SubLevelTrackingSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Aggressive optimization: Reduces SubLevel network sync frequency when TPS is low.
 * <p>
 * When server TPS drops below 18, this mixin skips every other network sync tick,
 * reducing bandwidth usage and network packet processing overhead at the cost of
 * clients seeing slightly delayed position/rotation updates.
 */
@Mixin(SubLevelTrackingSystem.class)
public abstract class ReducedNetworkSyncMixin {

    @Unique
    private double fucksable$syncTPS = 20.0;

    @Unique
    private long fucksable$lastSyncTime = System.currentTimeMillis();

    @Unique
    private int fucksable$syncSkipCounter = 0;

    /**
     * Before network sync, check if we should skip this tick.
     */
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private void fucksable$maybeSkipSync(SubLevelContainer container, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("reduced-network-sync")) return;

        // Estimate TPS
        long now = System.currentTimeMillis();
        long delta = now - fucksable$lastSyncTime;
        fucksable$lastSyncTime = now;
        if (delta > 0 && delta < 200) {
            double tps = 1000.0 / delta;
            fucksable$syncTPS = fucksable$syncTPS * 0.95 + tps * 0.05;
        }

        // Skip every other tick when TPS is below 18
        if (fucksable$syncTPS < 18.0) {
            fucksable$syncSkipCounter++;
            if (fucksable$syncSkipCounter % 2 == 0) {
                ci.cancel();
            }
        } else {
            fucksable$syncSkipCounter = 0;
        }
    }
}
