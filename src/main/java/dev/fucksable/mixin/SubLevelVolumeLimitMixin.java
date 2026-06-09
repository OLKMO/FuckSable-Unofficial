package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

/**
 * 限制单个物理结构的体积（方块数量），防止过大的物理结构导致服务器卡顿或崩溃。
 * <p>
 * 问题分析：
 * 过大的物理结构（数千个方块）会导致：
 * 1. Rapier 物理引擎的碰撞检测计算量指数级增长，导致服务器卡顿
 * 2. 物理引擎内部状态不一致时触发 Rust panic，直接终止 JVM 进程
 * 3. 大型结构的碰撞体可能超出 Rapier 的安全处理范围
 * <p>
 * 修复方式：
 * 在 SubLevelAssemblyHelper.assembleBlocks 调用前检查方块数量，
 * 如果超过预设限制则拒绝本次组装操作。
 */
@Mixin(SubLevelAssemblyHelper.class)
public class SubLevelVolumeLimitMixin {

    /**
     * 默认最大方块数量限制。
     * 2048 个方块是一个相对安全的上限，大约相当于 16x16x8 的体积。
     * 这个值足够大，不会影响正常游戏体验，但能有效防止极端情况。
     */
    private static final int FUCKSABLE$MAX_BLOCK_COUNT = 2048;

    @Inject(method = "assembleBlocks", at = @At("HEAD"), cancellable = true, remap = false)
    private static void fucksable$checkVolumeLimit(ServerLevel level, BlockPos anchor,
                                                    Iterable<BlockPos> blocks, BoundingBox3ic bounds,
                                                    CallbackInfoReturnable<ServerSubLevel> cir) {
        if (!FixRegistry.isEnabled("sublevel-volume-limit")) return;

        int count = 0;
        for (BlockPos ignored : blocks) {
            count++;
            if (count > FUCKSABLE$MAX_BLOCK_COUNT) {
                FuckSable.LOGGER.warn("SubLevel assembly rejected: block count {} exceeds limit {}. Anchor: {}",
                    count, FUCKSABLE$MAX_BLOCK_COUNT, anchor);
                cir.setReturnValue(null);
                return;
            }
        }
    }
}
