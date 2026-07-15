package dev.fucksable.mixin;

import dev.fucksable.fix.FixRegistry;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.slf4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 限流 Sable 的 SubLevelStorage.attemptLoadSubLevel 在 sub-level 数据缺失时的日志刷屏。
 * <p>
 * 问题分析：
 * 当 sub-level 存储文件中某个 index 的数据缺失或损坏时，attemptLoadSubLevel 会返回 null
 * 并打 ERROR 日志 "Couldn't find sub-level at index {} in storage file for chunk {}"。
 * Sable 会定期重试加载该 sub-level（有延时），导致同一行日志反复刷屏。
 * <p>
 * 修复方式：
 * @Redirect 拦截 LOGGER.error 调用，对同一个 (chunk, index) 组合按时间窗口限流：
 * 60 秒内只打一次，既抑制刷屏又保留状态可见性（数据修复后仍能看到日志变化）。
 * <p>
 * 跨版本兼容：attemptLoadSubLevel(ChunkPos, SavedSubLevelPointer) 签名在 Sable 1.x/2.x 一致。
 */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelStorage", remap = false)
public class SubLevelStorageLogSpamMixin {

    private static final long LOG_INTERVAL_NS = TimeUnit.SECONDS.toNanos(60);
    private static final int MAX_TRACKED_KEYS = 1024;
    private static final ConcurrentHashMap<String, Long> fucksable$logTimestamps = new ConcurrentHashMap<>();

    @Redirect(
        method = "attemptLoadSubLevel",
        at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;error(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"),
        remap = false
    )
    private void fucksable$throttleLog(Logger logger, String format, Object arg1, Object arg2) {
        if (!FixRegistry.isEnabled("sublevel-load-log-spam-fix")) {
            logger.error(format, arg1, arg2);
            return;
        }

        // arg1 = subLevelIndex (Short), arg2 = chunkPos (ChunkPos)
        String key = String.valueOf(arg2) + ":" + arg1;
        long now = System.nanoTime();
        Long last = fucksable$logTimestamps.get(key);
        if (last != null && (now - last) < LOG_INTERVAL_NS) {
            return; // 窗口内，抑制
        }
        fucksable$logTimestamps.put(key, now);

        // 防止内存无限增长：超过上限时清空（损坏区块有限，正常不会触发）
        if (fucksable$logTimestamps.size() > MAX_TRACKED_KEYS) {
            fucksable$logTimestamps.clear();
            fucksable$logTimestamps.put(key, now);
        }

        logger.error(format, arg1, arg2);
    }
}
