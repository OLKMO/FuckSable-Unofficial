package dev.fucksable.mixin;

import dev.fucksable.FuckSable;
import dev.fucksable.fix.FixRegistry;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复 PersistentEntitySectionManager.stopTracking 在调用 EntityLookup.remove 时
 * 触发 Int2ObjectLinkedOpenHashMap AIOOBE 导致服务器 tick 崩溃的问题。
 * <p>
 * 崩溃堆栈（error.log L16-L63）：
 *   java.lang.ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 513
 *     at Int2ObjectLinkedOpenHashMap.fixPointers(Int2ObjectLinkedOpenHashMap.java:979)
 *     at Int2ObjectLinkedOpenHashMap.removeEntry(Int2ObjectLinkedOpenHashMap.java:263)
 *     at Int2ObjectLinkedOpenHashMap.remove(Int2ObjectLinkedOpenHashMap.java:372)
 *     at EntityLookup.remove(EntityLookup.java:52)
 *     at PersistentEntitySectionManager.stopTracking(PersistentEntitySectionManager.java:157)
 *     ...
 *     at MinecraftServer.tickServer(MinecraftServer.java:1383)
 * <p>
 * 根因：
 * Sable 的 SubLevel 系统在跨维度/多线程管理 entity 时破坏了 EntityLookup 内部
 * Int2ObjectLinkedOpenHashMap 的链表状态（某个 entry 的 prev/next 指针被设为 -1，
 * 表示不存在，但被当作数组下标访问）。当 PersistentEntitySectionManager.stopTracking
 * 遍历 chunk 内 entity 并调用 EntityLookup.remove 时，map 内部 fixPointers 访问
 * index -1 抛出 AIOOBE，传播到 MinecraftServer.tickServer 导致服务器崩溃。
 * <p>
 * 修复方式：
 * @Redirect 拦截 stopTracking 中的 EntityLookup.remove 调用，
 * 捕获 AIOOBE 并以 WARN 日志记录，避免单个 entity 的 remove 失败导致整个 tick 崩溃。
 * <p>
 * 重要：EntityLookup 是泛型类 &lt;T extends EntityAccess&gt;，stopTracking 的参数类型
 * 也是 T。由于 Java 泛型擦除，编译后方法签名是：
 *   EntityLookup.remove(Lnet/minecraft/world/level/entity/EntityAccess;)V
 *   PersistentEntitySectionManager.stopTracking(Lnet/minecraft/world/level/entity/EntityAccess;)V
 * 因此 @At 的 target 描述符必须用 EntityAccess 而不是 Entity，否则 mixin 找不到
 * 匹配的调用点（Scanned 0 target(s)）。
 */
@Mixin(PersistentEntitySectionManager.class)
public class PersistentEntitySectionManagerStopTrackingGuardMixin {

    /**
     * 拦截 EntityLookup.remove 调用，捕获内部 map 状态损坏导致的 AIOOBE。
     * <p>
     * 参数类型必须用 EntityAccess（泛型擦除后的真实类型），不能用 Entity。
     * 被吞掉的异常会以 WARN 日志记录，便于排查但不影响服务器运行。
     */
    @Redirect(
        method = "stopTracking",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/EntityLookup;remove(Lnet/minecraft/world/level/entity/EntityAccess;)V"),
        remap = false
    )
    private void fucksable$safeEntityLookupRemove(EntityLookup instance, EntityAccess entity) {
        if (!FixRegistry.isEnabled("entity-lookup-remove-guard")) {
            instance.remove(entity);
            return;
        }

        try {
            instance.remove(entity);
        } catch (ArrayIndexOutOfBoundsException e) {
            FuckSable.LOGGER.warn("EntityLookup.remove skipped AIOOBE for entity {} (Int2ObjectLinkedOpenHashMap state corrupted, see Sable issue tracker)", entity, e);
        } catch (Throwable t) {
            FuckSable.LOGGER.warn("EntityLookup.remove skipped unexpected error for entity {}", entity, t);
        }
    }
}
