package dev.fucksable.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 修复 Simulated mod 的打字机在专用服务器上崩溃的问题。
 * <p>
 * Simulated mod 的 LinkedTypewriterBlock.useItemOn() 方法引用了客户端类
 * LinkedTypewriterInteractionHandler（它导入了 net.minecraft.client.gui.screens.Screen），
 * 即使该引用在 if (level.isClientSide) 分支内，JVM 在方法验证时仍会尝试加载该类，
 * 导致专用服务器上抛出 NoClassDefFoundError。
 * <p>
 * 修复方式：通过 WrapMethod 包裹 Block.useItemOn 方法，
 * 捕获 NoClassDefFoundError 并优雅地返回 PASS 结果，防止服务器崩溃。
 */
@Mixin(Block.class)
public abstract class BlockUseItemMixin {

    @WrapMethod(method = "useItemOn")
    private ItemInteractionResult fucksable$catchClientClassError(
            ItemStack stack, BlockState state, Level level, Player player,
            InteractionHand hand, BlockHitResult hitResult,
            Operation<ItemInteractionResult> original) {
        if (!FixRegistry.isEnabled("typewriter-server-fix")) {
            return original.call(stack, state, level, player, hand, hitResult);
        }

        try {
            return original.call(stack, state, level, player, hand, hitResult);
        } catch (NoClassDefFoundError e) {
            FuckSable.LOGGER.warn("Caught NoClassDefFoundError during block interaction at {} - " +
                    "this is likely a mod bug referencing client-only classes in common code", hitResult.getBlockPos(), e);
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
    }
}
