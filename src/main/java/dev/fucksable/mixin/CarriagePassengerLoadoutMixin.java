package dev.fucksable.mixin;

import com.simibubi.create.content.trains.entity.Carriage;
import dev.fucksable.fix.FixRegistry;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;
import java.util.Set;

/**
 * 修复 CTT (CreateThreadedTrains) 与 Create 的 ConcurrentModificationException。
 * <p>
 * CTT 将火车 tick 移到工作线程，但 updatePassengerLoadout 中遍历 serialisedPassengers (HashMap)
 * 时，主线程可能同时修改该 map，导致 ConcurrentModificationException。
 * <p>
 * 修复方式：在遍历 serialisedPassengers 时使用快照副本（new HashMap），避免并发修改异常。
 */
@Mixin(value = Carriage.DimensionalCarriageEntity.class, remap = false)
public abstract class CarriagePassengerLoadoutMixin {

    // serialisedPassengers 在外部类 Carriage 中，内部类通过 this$0 访问
    @Shadow(remap = false)
    Carriage this$0;

    /**
     * 将 serialisedPassengers.entrySet() 的遍历替换为快照副本遍历
     */
    @Redirect(
        method = "updatePassengerLoadout",
        remap = false,
        at = @At(value = "INVOKE", target = "Ljava/util/Map;entrySet()Ljava/util/Set;", remap = false)
    )
    private Set<Map.Entry<Integer, CompoundTag>> fucksable$useSnapshotEntrySet(Map<Integer, CompoundTag> instance) {
        if (!FixRegistry.isEnabled("ctt-concurrent-fix")) {
            return instance.entrySet();
        }
        // 使用快照副本，避免 ConcurrentModificationException
        return new java.util.HashMap<>(instance).entrySet();
    }
}
