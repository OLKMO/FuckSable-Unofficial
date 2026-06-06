package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 禁止命令方块被放置到物理化结构（sub-level）上。
 * <p>
 * 原版只阻止直接物理化命令方块，但可以先在已物理化的结构上放置命令方块。
 * 此 Mixin 在 BlockPlaceContext.canPlace 中检查，如果目标位置在 sub-level 内
 * 且手持物品是命令方块（含变体），则阻止放置。
 */
@Mixin(BlockPlaceContext.class)
public abstract class CommandBlockPlaceMixin {

    @Inject(method = "canPlace", at = @At("HEAD"), cancellable = true)
    private void fucksable$preventCommandBlockOnSubLevel(CallbackInfoReturnable<Boolean> cir) {
        if (!FixRegistry.isEnabled("command-block-sublevel-fix")) return;

        BlockPlaceContext self = (BlockPlaceContext) (Object) this;

        // 通过手持物品判断是否是命令方块
        var heldItem = self.getItemInHand();
        if (!(heldItem.getItem() instanceof BlockItem blockItem)) return;

        var block = blockItem.getBlock();
        if (block != Blocks.COMMAND_BLOCK
                && block != Blocks.REPEATING_COMMAND_BLOCK
                && block != Blocks.CHAIN_COMMAND_BLOCK) {
            return;
        }

        // 检查目标位置是否在 sub-level 内
        SubLevel subLevel = Sable.HELPER.getContaining(self.getLevel(), self.getClickedPos());
        if (subLevel != null) {
            FuckSable.LOGGER.debug("Prevented command block placement on sub-level at {}", self.getClickedPos());
            cir.setReturnValue(false);
        }
    }
}
