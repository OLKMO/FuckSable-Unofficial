package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 防止 CTT postTick 在主线程无限阻塞导致 Watchdog 超时崩溃。
 * <p>
 * 问题分析：
 * CreateThreadedTrains.postTick 在主线程调用 tasks.poll().get() 阻塞等待异步列车计算任务。
 * Future.get() 无超时，若异步任务卡住（如 Sable 物理引擎死循环、自约束 bug），
 * 主线程会无限等待，最终触发 Watchdog 超时崩溃。
 * <p>
 * 修复方式：
 * @Redirect 拦截 Future.get()，改为 get(10, SECONDS) 带超时等待。
 * 超时后 cancel(true) 中断异步任务并放行主线程，避免服务器崩溃。
 * 正常 tick 远不到 10 秒，不影响正常逻辑。
 */
@Mixin(targets = "de.mrjulsen.ctt.CreateThreadedTrains", remap = false)
public class CttPostTickTimeoutGuardMixin {

    private static final long TIMEOUT_SECONDS = 10;

    @Redirect(
        method = "postTick",
        at = @At(value = "INVOKE", target = "Ljava/util/concurrent/Future;get()Ljava/lang/Object;", remap = false),
        remap = false
    )
    private static Object fucksable$getWithTimeout(Future<?> future) throws InterruptedException, ExecutionException {
        if (!FixRegistry.isEnabled("ctt-posttick-timeout-guard")) {
            return future.get();
        }
        try {
            return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            FuckSable.LOGGER.warn("CTT train worker task timed out after {}s, cancelled to prevent server freeze", TIMEOUT_SECONDS);
            return null;
        }
    }
}
