package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * 修复航空学 SteamVentBlockEntity$SteamVentValueBoxTransform 在专用服务器上
 * 因引用客户端类 Minecraft 导致 NoClassDefFoundError 崩溃的问题。
 * <p>
 * isSideActive 方法中的客户端代码（Minecraft.getInstance()）在 level.isClientSide 条件内，
 * 但 JVM 在类加载时就会尝试解析 Minecraft 类引用，导致服务端崩溃。
 * <p>
 * 修复方式：使用 @Overwrite 覆盖 isSideActive 方法。
 * 服务端直接返回 true（客户端逻辑对服务端无意义）。
 * 客户端通过反射调用 Minecraft.getInstance() 获取鼠标射线检测结果。
 */
@Mixin(targets = "dev.eriksonn.aeronautics.content.blocks.hot_air.steam_vent.SteamVentBlockEntity$SteamVentValueBoxTransform", remap = false)
public abstract class SteamVentValueBoxTransformMixin {

    /**
     * @author FuckSable
     * @reason 修复专用服务器上引用客户端类 Minecraft 导致的 NoClassDefFoundError
     */
    @Overwrite(remap = false)
    protected boolean isSideActive(final BlockState state, final Direction direction) {
        if (!FixRegistry.isEnabled("aeronautics-server-fix")) {
            return true;
        }

        // 服务端直接返回 true
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return true;
        }

        // 客户端：通过反射调用 Minecraft.getInstance() 获取鼠标射线检测结果
        if (direction != Direction.UP) {
            return true;
        }

        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            var getInstance = minecraftClass.getDeclaredMethod("getInstance");
            Object mc = getInstance.invoke(null);
            var hitResultField = minecraftClass.getDeclaredField("hitResult");
            hitResultField.setAccessible(true);
            Object hitResult = hitResultField.get(mc);

            if (hitResult != null && hitResult.getClass().getName().contains("BlockHitResult")) {
                var getLocation = hitResult.getClass().getMethod("getLocation");
                Object hitVec = getLocation.invoke(hitResult);
                var x = hitVec.getClass().getMethod("x").invoke(hitVec);
                var y = hitVec.getClass().getMethod("y").invoke(hitVec);
                var z = hitVec.getClass().getMethod("z").invoke(hitVec);

                // 获取 this.be.getBlockPos()
                var beField = this.getClass().getDeclaredField("be");
                beField.setAccessible(true);
                Object be = beField.get(this);
                var getBlockPos = be.getClass().getMethod("getBlockPos");
                Object blockPos = getBlockPos.invoke(be);
                var bpGetX = blockPos.getClass().getMethod("getX");
                var bpGetY = blockPos.getClass().getMethod("getY");
                var bpGetZ = blockPos.getClass().getMethod("getZ");
                int bx = (int) bpGetX.invoke(blockPos);
                int by = (int) bpGetY.invoke(blockPos);
                int bz = (int) bpGetZ.invoke(blockPos);

                double localY = (double) y - (by + 0.5);
                return localY < 0.4;
            }
        } catch (Exception e) {
            FuckSable.LOGGER.debug("SteamVent isSideActive: failed to get client hit result", e);
        }

        return true;
    }
}
