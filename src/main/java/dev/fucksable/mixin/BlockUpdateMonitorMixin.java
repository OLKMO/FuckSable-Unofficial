package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.debug.BlockUpdateMonitor;
import dev.fucksable.i18n.LanguageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截 ServerLevel.sendBlockUpdated 方法，监控指定位置的方块更新事件。
 * 当监控位置发生更新时，记录完整调用链并通知玩家。
 */
@SuppressWarnings("null")
@Mixin(ServerLevel.class)
public class BlockUpdateMonitorMixin {

    @Inject(method = "sendBlockUpdated", at = @At("HEAD"))
    private void fucksable$onBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
        BlockUpdateMonitor.MonitorEntry monitor = BlockUpdateMonitor.getMonitor(pos);
        if (monitor == null) return;

        ServerPlayer player = monitor.getPlayer();
        if (player == null || !player.isAlive()) {
            BlockUpdateMonitor.stopMonitoring(pos);
            return;
        }

        // 获取调用栈
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StringBuilder traceBuilder = new StringBuilder();
        traceBuilder.append(LanguageManager.get("monitor.blockupdate-header")).append("\n");
        traceBuilder.append(LanguageManager.get("monitor.position", pos.toShortString())).append("\n");
        traceBuilder.append(LanguageManager.get("monitor.old-state", oldState)).append("\n");
        traceBuilder.append(LanguageManager.get("monitor.new-state", newState)).append("\n");
        traceBuilder.append(LanguageManager.get("monitor.flags", flags)).append("\n");
        traceBuilder.append(LanguageManager.get("monitor.dimension", ((ServerLevel) (Object) this).dimension().location())).append("\n");
        traceBuilder.append(LanguageManager.get("monitor.call-chain")).append("\n");

        // 跳过前几帧（getStackTrace, 本方法, sendBlockUpdated）
        for (int i = 3; i < Math.min(stackTrace.length, 30); i++) {
            traceBuilder.append("  at ").append(stackTrace[i].toString()).append("\n");
        }
        if (stackTrace.length > 30) {
            traceBuilder.append(LanguageManager.get("monitor.more-frames", stackTrace.length - 30)).append("\n");
        }

        String traceText = traceBuilder.toString();

        // 发送可复制的消息
        Component message = Component.literal(LanguageManager.get("monitor.blockupdate-title", pos.toShortString()))
            .withStyle(style -> style
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, traceText))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(LanguageManager.get("monitor.click-to-copy"))))
            );

        player.sendSystemMessage(message);

        // 同时发送简要信息
        FuckSable.LOGGER.info("[fs2temp] Block update at {}: {} -> {}, flags={}", pos, oldState, newState, flags);
    }
}
