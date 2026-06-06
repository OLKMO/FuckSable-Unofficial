package dev.fucksable.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复 CTT (CreateThreadedTrains) 的并发问题。
 * <p>
 * 问题：CTT 将 Carriage.travel 移到工作线程执行，但只把 manageEntities 调度到主线程，
 * 而 updateContraptionAnchors（修改 Carriage 状态）仍在工作线程执行。
 * 这导致主线程执行 manageEntities 时，Carriage 的状态可能处于不一致的中间状态，
 * 从而引发 LongAVLTreeSet NPE 和 EntitySectionStorage 损坏，进而导致服务器卡死。
 * <p>
 * 修复方式：在 manageEntities 入口处先调用 updateContraptionAnchors，
 * 确保主线程读取的 Carriage 状态是最新的。
 * updateContraptionAnchors 是幂等的，多次调用不会有副作用。
 */
@Mixin(value = Carriage.class, remap = false)
public abstract class CarriageTravelConcurrentMixin {

    @Shadow(remap = false)
    protected abstract void updateContraptionAnchors();

    /**
     * 在 manageEntities 执行前先更新锚点位置。
     * <p>
     * 当 CTT 在工作线程执行 travel 时，updateContraptionAnchors 可能还没执行完
     * （或还没被调度到主线程），导致 manageEntities 读取到过时的 positionAnchor。
     * 在 manageEntities 入口处先调用 updateContraptionAnchors 可以确保状态一致。
     */
    @Inject(method = "manageEntities", at = @At("HEAD"), remap = false)
    private void fucksable$updateAnchorsBeforeManageEntities(Level level, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("ctt-concurrent-fix")) {
            return;
        }

        // 检查是否在主线程
        if (level.getServer() != null && !level.getServer().isSameThread()) {
            // 如果不在主线程，说明 manageEntities 被错误地在工作线程调用
            FuckSable.LOGGER.warn("CTT concurrent fix: manageEntities called on non-main thread, skipping anchor update");
            return;
        }

        // 在主线程执行 manageEntities 前，先更新锚点位置
        try {
            this.updateContraptionAnchors();
        } catch (Exception e) {
            FuckSable.LOGGER.warn("CTT concurrent fix: error updating anchors before manageEntities", e);
        }
    }
}
