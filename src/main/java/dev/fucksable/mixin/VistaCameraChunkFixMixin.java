package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.companion.SableCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复 Vista 摄像头区块加载与 Sable 物理结构的不兼容问题。
 * <p>
 * 当 ViewFinder 放置在物理结构（SubLevel）上时，其 GlobalPos 中的坐标是 SubLevel 内部坐标，
 * 但 ServerCameraChunkManager 会在主世界上 force-load 这些坐标对应的区块，
 * 导致 force-load 了错误位置的区块，可能触发无限加载循环、TPS 掉 0 等问题。
 * <p>
 * 修复方式：拦截 setChunksForceLoaded 调用，将 ViewFinder 位置通过
 * SableCompanion.projectOutOfSubLevel 投影到主世界坐标后再 force-load。
 */
@Pseudo
@Mixin(targets = "net.mehvahdjukaar.vista.common.chunk_tracking.ServerCameraChunkManager", remap = false)
public class VistaCameraChunkFixMixin {

    /**
     * 拦截 onServerPlayerTick 中对 setChunksForceLoaded 的调用（添加 ViewFinder 时），
     * 将 vf.pos() 投影到主世界坐标后再 force-load。
     */
    @Redirect(
            method = "onServerPlayerTick",
            at = @At(value = "INVOKE",
                    target = "Lnet/mehvahdjukaar/vista/common/chunk_tracking/ServerCameraChunkManager;setChunksForceLoaded(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;IZ)V"),
            remap = false
    )
    private static void fucksable$projectAndForceLoadOnAdd(ServerLevel level, BlockPos pos, int radius, boolean force) {
        fucksable$setChunksForceLoadedProjected(level, pos, radius, force);
    }

    /**
     * 拦截 updateVfReferences 中对 setChunksForceLoaded 的调用（移除 ViewFinder 时），
     * 将 vf.pos() 投影到主世界坐标后再 unforce-load。
     */
    @Redirect(
            method = "updateVfReferences",
            at = @At(value = "INVOKE",
                    target = "Lnet/mehvahdjukaar/vista/common/chunk_tracking/ServerCameraChunkManager;setChunksForceLoaded(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;IZ)V"),
            remap = false
    )
    private static void fucksable$projectAndForceLoadOnRemove(ServerLevel level, BlockPos pos, int radius, boolean force) {
        fucksable$setChunksForceLoadedProjected(level, pos, radius, force);
    }

    /**
     * 投影后的 setChunksForceLoaded 实现。
     * 如果 ViewFinder 在 SubLevel 内，将其坐标投影到主世界坐标后再 force-load 区块。
     */
    private static void fucksable$setChunksForceLoadedProjected(ServerLevel level, BlockPos pos, int radius, boolean force) {
        if (FixRegistry.isEnabled("vista-camera-chunk-fix")) {
            try {
                Vec3 projected = SableCompanion.INSTANCE.projectOutOfSubLevel(level, Vec3.atLowerCornerOf(pos));
                BlockPos newPos = BlockPos.containing(projected);
                if (!newPos.equals(pos)) {
                    FuckSable.LOGGER.debug("Vista camera chunk fix: projected ViewFinder {} -> {}", pos, newPos);
                    pos = newPos;
                }
            } catch (Exception e) {
                FuckSable.LOGGER.debug("Vista camera chunk fix: failed to project position", e);
            }
        }

        ChunkPos cp = new ChunkPos(pos);
        ChunkPos.rangeClosed(cp, radius)
                .filter(p -> p.distanceSquared(cp) <= radius * radius)
                .forEach(p -> level.setChunkForced(p.x, p.z, force));
    }
}
