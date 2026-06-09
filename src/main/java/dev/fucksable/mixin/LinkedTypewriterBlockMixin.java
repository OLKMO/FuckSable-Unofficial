package dev.fucksable.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 修复 Simulated mod 的打字机在专用服务器上崩溃的问题。
 * <p>
 * Simulated mod 的 LinkedTypewriterBlock.useItemOn() 方法引用了客户端类
 * LinkedTypewriterInteractionHandler（它导入了 net.minecraft.client.Minecraft 等），
 * 即使该引用在 if (level.isClientSide) 分支内，JVM 在方法验证时仍会尝试加载该类，
 * 导致专用服务器上抛出 NoClassDefFoundError。
 * <p>
 * 之前的 @Overwrite 方案虽然阻止了崩溃，但也导致潜行点击打字机无法打开配置界面，
 * 因为 @Overwrite 替换了整个方法体，移除了客户端的 setMode 调用和服务端的 displayScreen 调用。
 * <p>
 * 修复方式：通过 @WrapMethod 包裹 useItemOn 方法，捕获 NoClassDefFoundError。
 * 这样原版逻辑完全保留，在客户端上 setMode(SCREEN_BINDING) 正常调用，
 * 在服务端上 displayScreen 正常打开菜单，只是在专用服务器上捕获类加载失败并优雅降级。
 */
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterBlock")
public abstract class LinkedTypewriterBlockMixin {

    @WrapMethod(method = "useItemOn", remap = false)
    private ItemInteractionResult fucksable$catchClientClassError(
            ItemStack stack, BlockState state, Level level, BlockPos blockPos,
            Player player, InteractionHand hand, BlockHitResult hitResult,
            Operation<ItemInteractionResult> original) {
        if (!FixRegistry.isEnabled("typewriter-server-fix")) {
            return original.call(stack, state, level, blockPos, player, hand, hitResult);
        }

        try {
            return original.call(stack, state, level, blockPos, player, hand, hitResult);
        } catch (NoClassDefFoundError e) {
            FuckSable.LOGGER.warn("Caught NoClassDefFoundError during typewriter interaction at {} - " +
                    "client-only class referenced in common code", hitResult.getBlockPos(), e);
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
    }
}
