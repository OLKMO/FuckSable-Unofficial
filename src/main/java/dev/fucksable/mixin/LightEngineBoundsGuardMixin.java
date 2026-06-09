package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 Sable SubLevel 光照更新越界导致崩溃的问题。
 * <p>
 * 问题分析：
 * Sable 的 ServerLevelPlot 在加载和传播光照时，可能生成超出世界高度限制的
 * SectionPos，导致 LevelLightEngine 内部的 ArrayIndexOutOfBoundsException
 * 或其他越界异常。
 * <p>
 * 修复方式：
 * 在 queueSectionData 和 updateSectionStatus 调用前检查 section Y 坐标
 * 是否在有效范围内，跳过越界的光照更新。
 */
@Mixin(LevelLightEngine.class)
public class LightEngineBoundsGuardMixin {

    /**
     * 拦截 queueSectionData，当 section Y 越界时跳过。
     */
    @Inject(method = "queueSectionData", at = @At("HEAD"), cancellable = true)
    private void fucksable$guardQueueSectionData(LightLayer layer, SectionPos pos, DataLayer data, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("light-engine-bounds-guard")) return;

        LevelLightEngine self = (LevelLightEngine) (Object) this;
        int sectionY = pos.getY();
        if (sectionY < self.getMinLightSection() || sectionY >= self.getMaxLightSection()) {
            FuckSable.LOGGER.debug("Light engine bounds guard: skipping queueSectionData for out-of-bounds section Y={}", sectionY);
            ci.cancel();
        }
    }

    /**
     * 拦截 updateSectionStatus，当 section Y 越界时跳过。
     */
    @Inject(method = "updateSectionStatus", at = @At("HEAD"), cancellable = true)
    private void fucksable$guardUpdateSectionStatus(SectionPos pos, boolean notEmpty, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("light-engine-bounds-guard")) return;

        LevelLightEngine self = (LevelLightEngine) (Object) this;
        int sectionY = pos.getY();
        if (sectionY < self.getMinLightSection() || sectionY >= self.getMaxLightSection()) {
            FuckSable.LOGGER.debug("Light engine bounds guard: skipping updateSectionStatus for out-of-bounds section Y={}", sectionY);
            ci.cancel();
        }
    }
}
