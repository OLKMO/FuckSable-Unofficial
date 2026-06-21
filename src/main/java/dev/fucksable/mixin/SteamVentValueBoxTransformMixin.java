package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.lang.reflect.Field;

/**
 * 修复航空学 SteamVentBlockEntity$SteamVentValueBoxTransform 在专用服务器上
 * 因引用客户端类 Minecraft 导致 NoClassDefFoundError 崩溃的问题。
 * <p>
 * isSideActive 和 fromSide 方法中的客户端代码（Minecraft.getInstance()）在 level.isClientSide 条件内，
 * 但 JVM 在类加载时就会尝试解析 Minecraft 类引用，导致服务端崩溃。
 * <p>
 * 修复方式：使用 @Overwrite 覆盖 isSideActive 和 fromSide 方法。
 * 服务端直接返回安全默认值。
 * 客户端通过反射调用 Minecraft.getInstance() 获取鼠标射线检测结果。
 * <p>
 * 注意：不使用 @Shadow direction 字段，因为 Create 不同版本中该字段名可能变化，
 * 改用反射设置方向字段以保证兼容性。
 */
@Mixin(targets = "dev.eriksonn.aeronautics.content.blocks.hot_air.steam_vent.SteamVentBlockEntity$SteamVentValueBoxTransform", remap = false)
public abstract class SteamVentValueBoxTransformMixin extends com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform.Sided {

    /**
     * 通过反射设置父类的方向字段。
     * Create 不同版本中该字段名可能为 direction 或其他名称，
     * 遍历父类字段找到 Direction 类型的字段并设置。
     */
    private void fucksable$setDirection(Direction dir) {
        try {
            Class<?> clazz = this.getClass().getSuperclass();
            while (clazz != null) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (f.getType() == Direction.class) {
                        f.setAccessible(true);
                        f.set(this, dir);
                        return;
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Exception e) {
            FuckSable.LOGGER.debug("SteamVent: failed to set direction field via reflection", e);
        }
    }

    /**
     * @author FuckSable
     * @reason 修复专用服务器上 fromSide 引用客户端类 Minecraft 导致的 NoClassDefFoundError
     */
    @Overwrite(remap = false)
    public com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform.Sided fromSide(final Direction dir) {
        this.fucksable$setDirection(dir);

        if (!FixRegistry.isEnabled("aeronautics-server-fix")) {
            return this;
        }

        // 服务端直接返回
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return this;
        }

        // 客户端：通过反射获取鼠标射线检测结果来调整方向
        if (dir == Direction.UP) {
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
                    double hx = (double) hitVec.getClass().getMethod("x").invoke(hitVec);
                    double hy = (double) hitVec.getClass().getMethod("y").invoke(hitVec);
                    double hz = (double) hitVec.getClass().getMethod("z").invoke(hitResult);

                    var beField = this.getClass().getDeclaredField("be");
                    beField.setAccessible(true);
                    Object be = beField.get(this);
                    var getBlockPos = be.getClass().getMethod("getBlockPos");
                    Object blockPos = getBlockPos.invoke(be);
                    int bx = (int) blockPos.getClass().getMethod("getX").invoke(blockPos);
                    int by = (int) blockPos.getClass().getMethod("getY").invoke(blockPos);
                    int bz = (int) blockPos.getClass().getMethod("getZ").invoke(blockPos);

                    double localY = hy - (by + 0.5);
                    if (localY < 0.4) {
                        double localX = hx - (bx + 0.5);
                        double localZ = hz - (bz + 0.5);
                        this.fucksable$setDirection(Direction.getNearest(localX, 0, localZ));
                    }
                }
            } catch (Exception e) {
                FuckSable.LOGGER.debug("SteamVent fromSide: failed to get client hit result", e);
            }
        }

        return this;
    }

    /**
     * @author FuckSable
     * @reason 修复专用服务器上 isSideActive 引用客户端类 Minecraft 导致的 NoClassDefFoundError
     */
    @Overwrite(remap = false)
    protected boolean isSideActive(final BlockState state, final Direction dir) {
        if (!FixRegistry.isEnabled("aeronautics-server-fix")) {
            return true;
        }

        // 服务端直接返回 true
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return true;
        }

        // 客户端：通过反射调用 Minecraft.getInstance() 获取鼠标射线检测结果
        if (dir != Direction.UP) {
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
                double y = (double) hitVec.getClass().getMethod("y").invoke(hitVec);

                var beField = this.getClass().getDeclaredField("be");
                beField.setAccessible(true);
                Object be = beField.get(this);
                var getBlockPos = be.getClass().getMethod("getBlockPos");
                Object blockPos = getBlockPos.invoke(be);
                int by = (int) blockPos.getClass().getMethod("getY").invoke(blockPos);

                double localY = y - (by + 0.5);
                return localY < 0.4;
            }
        } catch (Exception e) {
            FuckSable.LOGGER.debug("SteamVent isSideActive: failed to get client hit result", e);
        }

        return true;
    }
}
