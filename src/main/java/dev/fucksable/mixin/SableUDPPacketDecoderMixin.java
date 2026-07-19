package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 修复 Sable UDP 解码器对非法 packet ID 抛 IOException 导致日志刷屏的问题。
 * <p>
 * 问题分析：
 * 当客户端发送 packet ID 254（Minecraft Legacy Server List Ping 协议）等非法值到 Sable 的 UDP 端口时，
 * SableUDPPacketDecoder.decode 会读取第一个字节作为 packet ID，然后判断 ID >= SableUDPPacketType.VALUES.length
 * 时抛出 IOException("Received an invalid packet ID: <id>")。该异常被 Netty 包装成 DecoderException 并向上传播，
 * 最终被 Sable 的 channel handler 捕获并打 ERROR 日志 "Server UDP channel caught exception"。
 * <p>
 * 触发场景：
 * - 服务器列表 ping 探测 (legacy 1.6 之前的协议)
 * - 任意 UDP 扫描行为
 * - 非客户端误发的字节
 * <p>
 * 修复方式：
 * 在 decode 方法 HEAD 处插入检查，peek 第一个字节（不消费 readerIndex），
 * 如果 packet ID 大于等于 SableUDPPacketType.VALUES.length，cancel 调用静默丢弃。
 * 这样既不会触发 IOException，也不会污染日志。
 * <p>
 * 跨版本兼容：通过反射读取 SableUDPPacketType.VALUES.length，缓存后复用，避免反射开销。
 */
@Mixin(targets = "dev.ryanhcode.sable.network.udp.SableUDPPacketDecoder", remap = false)
public class SableUDPPacketDecoderMixin {

    /**
     * 缓存的 packet ID 上界（合法 packet ID 的最大值，即 VALUES.length - 1）。
     * -1 表示未初始化。
     */
    private static volatile int fucksable$maxValidPacketId = -1;

    @Inject(
        method = "decode",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void fucksable$skipInvalidPacket(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out, CallbackInfo ci) {
        if (!FixRegistry.isEnabled("udp-invalid-packet-guard")) {
            return;
        }

        try {
            ByteBuf buf = msg.content();
            if (buf.readableBytes() < 1) {
                return;
            }

            // peek 第一个字节（不消费 readerIndex）
            int packetId = buf.getUnsignedByte(buf.readerIndex());
            int maxValid = fucksable$getMaxValidPacketId();
            if (packetId > maxValid) {
                // 静默丢弃非法 packet，避免触发 IOException 和后续 ERROR 日志刷屏
                ci.cancel();
            }
        } catch (Throwable t) {
            FuckSable.LOGGER.debug("UDP invalid packet guard: failed to inspect packet, falling through to vanilla decode", t);
        }
    }

    /**
     * 通过反射读取 SableUDPPacketType.VALUES.length - 1，缓存后复用。
     * 反射失败时回退到硬编码上界 5（Sable 1.2.2 有 6 个 packet type）。
     */
    private static int fucksable$getMaxValidPacketId() {
        int cached = fucksable$maxValidPacketId;
        if (cached >= 0) {
            return cached;
        }
        synchronized (SableUDPPacketDecoderMixin.class) {
            cached = fucksable$maxValidPacketId;
            if (cached >= 0) {
                return cached;
            }
            try {
                Class<?> enumClass = Class.forName("dev.ryanhcode.sable.network.udp.SableUDPPacketType");
                java.lang.reflect.Field valuesField = enumClass.getDeclaredField("VALUES");
                valuesField.setAccessible(true);
                Object[] values = (Object[]) valuesField.get(null);
                cached = values.length - 1;
            } catch (Throwable t) {
                FuckSable.LOGGER.warn("UDP invalid packet guard: failed to read SableUDPPacketType.VALUES, falling back to hardcoded limit 5", t);
                cached = 5;
            }
            fucksable$maxValidPacketId = cached;
            return cached;
        }
    }
}
