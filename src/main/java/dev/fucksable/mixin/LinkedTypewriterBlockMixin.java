package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * 修复 Simulated mod 的打字机在专用服务器上崩溃的问题。
 * <p>
 * Simulated mod 的 LinkedTypewriterBlock.useItemOn() 方法引用了客户端类
 * LinkedTypewriterInteractionHandler（它导入了 net.minecraft.client.gui.screens.Screen），
 * 即使该引用在 if (level.isClientSide) 分支内，JVM 在方法验证时仍会尝试加载该类，
 * 导致专用服务器上抛出 NoClassDefFoundError。
 * <p>
 * 修复方式：使用 @Overwrite 重写 useItemOn 方法，移除对客户端类的直接引用。
 * 所有对 Simulated mod 方法的调用均通过反射完成，避免 @Shadow 在无 refmap 环境下
 * 因 SRG 映射不匹配而失败。
 */
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlock")
public abstract class LinkedTypewriterBlockMixin {

    /**
     * 通过反射调用 withBlockEntityDo 方法，避免 @Shadow 在无 refmap 环境下失败。
     */
    @SuppressWarnings("unchecked")
    private void fucksable$withBlockEntityDo(Level level, BlockPos pos, Consumer<BlockEntity> action) {
        try {
            // 目标类是 LinkedTypewriterBlock 的父类，方法签名: <T extends BlockEntity> void withBlockEntityDo(Level, BlockPos, Consumer<T>)
            // 通过反射查找并调用
            Object self = this;
            Class<?> clazz = self.getClass();
            // 遍历类层次查找 withBlockEntityDo 方法
            java.lang.reflect.Method method = null;
            while (clazz != null && method == null) {
                for (java.lang.reflect.Method m : clazz.getDeclaredMethods()) {
                    if (m.getName().equals("withBlockEntityDo") || m.getName().equals("m_142435_")) {
                        method = m;
                        break;
                    }
                }
                if (method == null) clazz = clazz.getSuperclass();
            }
            if (method != null) {
                method.setAccessible(true);
                method.invoke(self, level, pos, action);
            } else {
                FuckSable.LOGGER.warn("Could not find withBlockEntityDo method on typewriter block");
            }
        } catch (Exception e) {
            FuckSable.LOGGER.error("Failed to invoke withBlockEntityDo via reflection", e);
        }
    }

    /**
     * @author FuckSable
     * @reason 修复专用服务器上因客户端类引用导致的 NoClassDefFoundError 崩溃
     */
    @Overwrite
    protected ItemInteractionResult useItemOn(final ItemStack itemStack, final BlockState blockState, final Level level, final BlockPos blockPos, final Player player, final InteractionHand interactionHand, final BlockHitResult blockHitResult) {
        if (!FixRegistry.isEnabled("typewriter-server-fix")) {
            throw new UnsupportedOperationException("typewriter-server-fix is disabled, original method was overwritten");
        }

        final ItemStack heldItem = player.getItemInHand(interactionHand);

        // 检查是否持有 Linked Controller - 通过反射获取 AllItems.LINKED_CONTROLLER
        Object linkedControllerItem = null;
        try {
            Class<?> allItemsClass = Class.forName("com.simibubi.create.AllItems");
            var field = allItemsClass.getField("LINKED_CONTROLLER");
            var supplier = field.get(null);
            var asItemMethod = supplier.getClass().getMethod("asItem");
            linkedControllerItem = asItemMethod.invoke(supplier);
        } catch (Exception e) {
            FuckSable.LOGGER.debug("Could not find Create's Linked Controller item", e);
        }

        if (linkedControllerItem instanceof net.minecraft.world.item.Item controllerItem) {
            if (player.getMainHandItem().is(controllerItem) || player.getOffhandItem().is(controllerItem)) {
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        if (heldItem.isEmpty() && interactionHand == InteractionHand.MAIN_HAND) {
            final boolean[] success = {false};

            this.fucksable$withBlockEntityDo(level, blockPos, (BlockEntity be) -> {
                try {
                    UUID uuid = player.getUUID();
                    boolean isShiftKeyDown = player.isShiftKeyDown();
                    boolean checkAndStart = (boolean) be.getClass().getMethod("checkAndStartUsing", UUID.class).invoke(be, uuid);

                    if (isShiftKeyDown && checkAndStart) {
                        if (!level.isClientSide) {
                            try {
                                Class<?> simMenuServiceClass = Class.forName("dev.simulated_team.simulated.service.SimMenuService");
                                Object instance = simMenuServiceClass.getField("INSTANCE").get(null);
                                Class<?> beClass = be.getClass();
                                var sendToMenu = beClass.getMethod("sendToMenu");
                                simMenuServiceClass.getMethod("openScreen", ServerPlayer.class, BlockEntity.class, Consumer.class)
                                        .invoke(instance, (ServerPlayer) player, be, (Consumer<Object>) o -> {
                                            try {
                                                sendToMenu.invoke(be);
                                            } catch (Exception ex) {
                                                throw new RuntimeException(ex);
                                            }
                                        });
                            } catch (Exception e) {
                                FuckSable.LOGGER.error("Failed to open typewriter screen", e);
                            }
                        }
                        success[0] = true;
                        return;
                    }

                    if (checkAndStart) {
                        success[0] = true;
                        return;
                    }

                    boolean checkUser = (boolean) be.getClass().getMethod("checkUser", UUID.class).invoke(be, uuid);
                    if (checkUser) {
                        try {
                            Class<?> extClass = Class.forName("dev.simulated_team.simulated.mixin_interface.PlayerTypewriterExtension");
                            extClass.getMethod("simulated$setCurrentTypewriter", Object.class).invoke(player, (Object) null);
                        } catch (Exception e) {
                            FuckSable.LOGGER.error("Failed to clear typewriter for player", e);
                        }
                        be.getClass().getMethod("disconnectUser").invoke(be);
                        success[0] = true;
                    }
                } catch (Exception e) {
                    FuckSable.LOGGER.error("Error during typewriter interaction", e);
                }
            });

            if (success[0]) {
                return ItemInteractionResult.SUCCESS;
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
