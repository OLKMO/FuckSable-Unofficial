package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.debug.BlockUpdateMonitor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 拦截 Level.setBlock 方法，监控指定位置的方块设置事件。
 * 这是比 sendBlockUpdated 更底层的拦截点，能捕获更多变化。
 */
@Mixin(Level.class)
public class LevelSetBlockMonitorMixin {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"))
    private void fucksable$onSetBlock(BlockPos pos, BlockState state, int flags, int maxUpdateDepth, CallbackInfoReturnable<Boolean> cir) {
        BlockUpdateMonitor.MonitorEntry monitor = BlockUpdateMonitor.getMonitor(pos);
        if (monitor == null) return;

        Level self = (Level) (Object) this;
        if (self.isClientSide()) return;

        ServerPlayer player = monitor.getPlayer();
        if (player == null || !player.isAlive()) {
            BlockUpdateMonitor.stopMonitoring(pos);
            return;
        }

        BlockState oldState = self.getBlockState(pos);

        // 获取调用栈
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StringBuilder traceBuilder = new StringBuilder();
        traceBuilder.append("=== fs2temp setBlock 监控 ===\n");
        traceBuilder.append("位置: ").append(pos.toShortString()).append("\n");
        traceBuilder.append("旧状态: ").append(oldState).append("\n");
        traceBuilder.append("新状态: ").append(state).append("\n");
        traceBuilder.append("更新标志: ").append(flags).append("\n");
        traceBuilder.append("最大更新深度: ").append(maxUpdateDepth).append("\n");
        traceBuilder.append("维度: ").append(self.dimension().location()).append("\n");
        traceBuilder.append("--- 调用链 ---\n");

        for (int i = 3; i < Math.min(stackTrace.length, 30); i++) {
            traceBuilder.append("  at ").append(stackTrace[i].toString()).append("\n");
        }
        if (stackTrace.length > 30) {
            traceBuilder.append("  ... 还有 ").append(stackTrace.length - 30).append(" 帧\n");
        }

        String traceText = traceBuilder.toString();

        Component message = Component.literal("[fs2temp setBlock] " + pos.toShortString())
            .withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, traceText))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("点击复制完整调用链")))
            );

        player.sendSystemMessage(message);
        FuckSable.LOGGER.info("[fs2temp] setBlock at {}: {} -> {}, flags={}", pos, oldState, state, flags);
    }
}
