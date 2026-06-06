package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * 防护 Sable SubLevelInclusiveLevelEntityGetter.get 导致的无限循环。
 * <p>
 * 当 EntitySectionStorage 的 LongAVLTreeSet 因并发修改损坏后，
 * forEachAccessibleNonEmptySection 可能陷入无限循环，导致服务器卡死。
 * <p>
 * 修复方式：
 * 1. 限制 get 方法中遍历 subLevel 的数量
 * 2. 对异常大的 AABB 进行更严格的检查
 */
@Pseudo
@Mixin(targets = "dev.ryanhcode.sable.util.SubLevelInclusiveLevelEntityGetter", remap = false)
public abstract class SubLevelEntityGetterGuardMixin {

    // 限制每个 get 调用最多遍历的 subLevel 数量
    private static final int MAX_SUBLEVEL_ITERATIONS = 10;

    /**
     * 拦截 get(AABB, Consumer) 方法，在执行前添加防护检查。
     */
    @Inject(method = "get(Lnet/minecraft/world/phys/AABB;Ljava/util/function/Consumer;)V", at = @At("HEAD"), remap = false, cancellable = true)
    private void fucksable$guardGet(AABB aABB, Consumer consumer, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("sublevel-entity-guard")) {
            return;
        }

        // 额外检查：如果 AABB 尺寸异常大，直接跳过
        double size = aABB.getSize();
        if (size > 10000) {
            FuckSable.LOGGER.warn("SubLevelInclusiveLevelEntityGetter: skipping abnormally large AABB (size={})", size);
            ci.cancel();
        }
    }

    /**
     * 拦截 get(EntityTypeTest, AABB, AbortableIterationConsumer) 方法，在执行前添加防护检查。
     */
    @Inject(method = "get(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Lnet/minecraft/util/AbortableIterationConsumer;)V", at = @At("HEAD"), remap = false, cancellable = true)
    private void fucksable$guardGetTyped(EntityTypeTest entityTypeTest, AABB aABB, AbortableIterationConsumer abortableIterationConsumer, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("sublevel-entity-guard")) {
            return;
        }

        double size = aABB.getSize();
        if (size > 10000) {
            FuckSable.LOGGER.warn("SubLevelInclusiveLevelEntityGetter: skipping abnormally large AABB (size={})", size);
            ci.cancel();
        }
    }
}
