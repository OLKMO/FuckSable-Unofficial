## v1.7.4

### 修复 / Bug Fix

修复 v1.7.3 中 `entity-lookup-remove-guard` mixin 注入失败导致服务器启动崩溃的问题。

Fix `entity-lookup-remove-guard` mixin injection failure that crashed server startup in v1.7.3.

### 崩溃信息 / Crash

```
org.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError
Caused by: InjectionError: Critical injection failure:
  Redirector fucksable$safeEntityLookupRemove(Lnet/minecraft/world/level/entity/EntityLookup;Lnet/minecraft/world/entity/Entity;)V
  in fucksable.mixins.json:PersistentEntitySectionManagerStopTrackingGuardMixin
  failed injection check, (0/1) succeeded. Scanned 0 target(s).
```

### 根因 / Root Cause

`@At` 的 target 描述符错误地使用了 `Entity` 作为参数类型，但 `EntityLookup<T extends EntityAccess>` 的 `remove(T)` 和 `PersistentEntitySectionManager.stopTracking(T)` 在编译后由于泛型擦除，实际签名是 `(Lnet/minecraft/world/level/entity/EntityAccess;)V`。mixin 找不到匹配的调用点（Scanned 0 targets），导致 `MixinTransformerError` 让整个服务端启动崩溃。

The `@At` target descriptor incorrectly used `Entity` as the parameter type, but `EntityLookup<T extends EntityAccess>.remove(T)` and `PersistentEntitySectionManager.stopTracking(T)` are erased to `(Lnet/minecraft/world/level/entity/EntityAccess;)V` at compile time due to Java generics erasure. Mixin could not find any matching INVOKE site (Scanned 0 targets), causing `MixinTransformerError` and crashing the server at startup.

### 修复方式 / How

- target 描述符改为 `(Lnet/minecraft/world/level/entity/EntityAccess;)V`
- handler 方法参数改为 `EntityAccess`
- Change target descriptor to `(Lnet/minecraft/world/level/entity/EntityAccess;)V`
- Change handler parameter type to `EntityAccess`

### 注意 / Note

v1.7.3 是有问题的版本，请勿使用，请直接升级到 v1.7.4。

v1.7.3 is broken, please skip it and use v1.7.4 instead.

## v1.7.3

## 新增修复 / New Fix: `entity-lookup-remove-guard`

拦截 `PersistentEntitySectionManager.stopTracking` 中的 `EntityLookup.remove` 调用，捕获 `Int2ObjectLinkedOpenHashMap.fixPointers` 抛出的 `ArrayIndexOutOfBoundsException`，避免单个实体移除失败导致整个服务器 tick 崩溃。

Catches `ArrayIndexOutOfBoundsException` thrown by `Int2ObjectLinkedOpenHashMap.fixPointers` inside `EntityLookup.remove` during `PersistentEntitySectionManager.stopTracking`, preventing single-entity removal failures from crashing the server tick loop.

### 修复的崩溃 / Crash being fixed

```
java.lang.ArrayIndexOutOfBoundsException: Index -1 out of bounds for length 513
    at it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.fixPointers(Int2ObjectLinkedOpenHashMap.java:979)
    at it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.removeEntry(Int2ObjectLinkedOpenHashMap.java:263)
    at it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap.remove(Int2ObjectLinkedOpenHashMap.java:372)
    at net.minecraft.world.level.entity.EntityLookup.remove(EntityLookup.java:52)
    at net.minecraft.world.level.entity.PersistentEntitySectionManager.stopTracking(PersistentEntitySectionManager.java:157)
    ...
    at net.minecraft.server.MinecraftServer.tickServer(MinecraftServer.java:1383)
```

### 根因 / Root cause

Sable 的 SubLevel 实体管理（跨维度/多线程）破坏了 `EntityLookup` 内部 `Int2ObjectLinkedOpenHashMap` 的链表状态。当某个 entry 的 `prev`/`next` 指针被设为 `-1`（哨兵值表示"无链接"）却被当作数组下标访问时，`fixPointers` 抛出 `Index -1 out of bounds for length N`。异常沿 `PersistentEntitySectionManager.stopTracking` → `updateChunkStatus` → `ChunkMap.onFullChunkStatusChange` → `ChunkHolder.demoteFullChunk` → `MinecraftServer.tickServer` 一路传播，导致 tick 循环崩溃。

Sable's SubLevel entity management (cross-dimension / multi-threaded) corrupts the internal linked-map state of `EntityLookup`'s backing `Int2ObjectLinkedOpenHashMap`. When an entry's `prev` / `next` pointer is set to `-1` (sentinel meaning "no link") but later accessed as an array index, `fixPointers` throws `Index -1 out of bounds for length N`. The exception propagates up through `PersistentEntitySectionManager.stopTracking` -> `updateChunkStatus` -> `ChunkMap.onFullChunkStatusChange` -> `ChunkHolder.demoteFullChunk` -> `MinecraftServer.tickServer`, crashing the tick loop.

### 实现方式 / How

- 在 `PersistentEntitySectionManager.stopTracking` 中的 `EntityLookup.remove` 调用上加 `@Redirect`
- 在调用点捕获 `ArrayIndexOutOfBoundsException`（以及其他 `Throwable`）
- 每次发生时输出一条 `WARN` 日志（包含实体引用），便于排查
- tick 继续正常运行，仅跳过出错实体的移除操作

- `@Redirect` on the `EntityLookup.remove` invocation inside `PersistentEntitySectionManager.stopTracking`
- Catches `ArrayIndexOutOfBoundsException` (and any other `Throwable`) at the call site
- Emits a single `WARN` log per occurrence with the entity reference for diagnosis
- Tick continues normally; only the offending entity's removal is skipped

### 注意事项 / Caveats

这是**治标修复**。它让服务器保持运行但不会修复 `Int2ObjectLinkedOpenHashMap` 的底层状态——损坏的 entry 仍然存在，可能在后续 `remove` 调用中再次出现。真正的修复应在 Sable 的实体卸载 / SubLevel 实体追踪代码中（参见 `sable.mixins.json:entity.entity_unloading.PersistentEntitySectionManagerMixin` 和 `sable.mixins.json:entity.server_entities_tick.ChunkMapMixin`）。

This is a **symptomatic** fix. It keeps the server alive but does not repair the underlying `Int2ObjectLinkedOpenHashMap` state — the corrupted entry remains and may surface again on subsequent `remove` calls. The true fix belongs in Sable's entity unloading / SubLevel entity tracking code (see `sable.mixins.json:entity.entity_unloading.PersistentEntitySectionManagerMixin` and `sable.mixins.json:entity.server_entities_tick.ChunkMapMixin`).

### 兼容性 / Compatibility

- Mixin 目标 / Mixin target: `net.minecraft.world.level.entity.PersistentEntitySectionManager`
- Redirect 目标 / Redirect target: `stopTracking` 中的 `EntityLookup.remove(Entity)` 调用 / `EntityLookup.remove(Entity)` invocation in `stopTracking`
- Sable 的 `PersistentEntitySectionManagerMixin` 使用 `@Inject` 注入在不同方法（`processChunkUnload`）上，因此不会发生 handler 冲突 / Sable's `PersistentEntitySectionManagerMixin` uses `@Inject` on a different method (`processChunkUnload`), so there is no handler conflict.

## v1.7.2 - UDP 无效数据包防护 / UDP Invalid Packet Guard

### 新增修复 / New Fix: `udp-invalid-packet-guard`

在 `SableUDPPacketDecoder.decode` 头部静默丢弃 packet ID 越界的 UDP 数据包（如旧版 Minecraft 服务器列表 ping 的 packet ID 254），而不是让 Sable 抛出 `IOException("Received an invalid packet ID: 254")`。

Silently drops UDP packets with invalid packet IDs (e.g. legacy Minecraft server list ping packet ID 254) at the head of `SableUDPPacketDecoder.decode`, instead of letting Sable throw `IOException("Received an invalid packet ID: 254")`.

### 原因 / Why

没有这个防护时，Sable 读取第一个字节作为 packet ID，发现它 `>= SableUDPPacketType.VALUES.length`，抛出 `IOException`。异常沿 Netty pipeline 作为 `DecoderException` 向上传播，被 Sable 的 channel handler 捕获后输出 `Server UDP channel caught exception` ERROR 日志——每当有人 ping 或扫描 Sable 的 UDP 端口时都会重复刷屏。

Without this guard, Sable reads the first byte as a packet ID, sees it is `>= SableUDPPacketType.VALUES.length`, and throws `IOException`. The exception propagates up the Netty pipeline as a `DecoderException`, gets caught by Sable's channel handler, and produces a recurring `Server UDP channel caught exception` ERROR log entry every time someone pings or scans the Sable UDP port.

### 实现方式 / How

- 在 `decode` HEAD 处 `@Inject`
- `@Inject` at `decode` HEAD
- peek 第一个字节，不消费 `readerIndex`
- Peek the first byte without consuming `readerIndex`
- 若 packet ID 超过 `SableUDPPacketType.VALUES.length`，cancel decode 调用
- If packet ID exceeds `SableUDPPacketType.VALUES.length`, cancel the decode call
- 合法 ID 上界通过反射从 `SableUDPPacketType.VALUES` 读取（首次调用后缓存），反射失败时回退到 5（Sable 1.2.2 有 6 个 packet type）
- Valid ID upper bound is read via reflection from `SableUDPPacketType.VALUES` (cached after first call), falls back to 5 (Sable 1.2.2 has 6 packet types) if reflection fails

## v1.7.1

### Bug 修复 / Bug Fixes

修复在 Mohist/Youer 1.21.1 专用服务端上由 `ReEntrantTransformerError: Re-entrance error` in `FuckSableMixinConfigPlugin` 引起的服务器启动崩溃：

Fix server startup crash on **Mohist/Youer 1.21.1** dedicated servers caused by `ReEntrantTransformerError: Re-entrance error` in `FuckSableMixinConfigPlugin`:

- **`ArtifactVersion.compareTo` 的 `NoSuchMethodException`**: 在混合服务端（Mohist/Youer）上，`ArtifactVersion` 的 `compareTo` 是 `Comparable<ArtifactVersion>` 桥接方法，参数类型被擦除为 `Object`，所以 `getMethod("compareTo", artifactVersionClass)` 找不到它。改为直接使用 `((Comparable) version).compareTo(threshold)` 通过 JVM 多态派发。/ **`NoSuchMethodException` on `ArtifactVersion.compareTo`**: on hybrid servers (Mohist/Youer), `ArtifactVersion`'s `compareTo` is a `Comparable<ArtifactVersion>` bridge method whose erased parameter type is `Object`, so `getMethod("compareTo", artifactVersionClass)` failed to find it. Replaced with a direct `((Comparable) version).compareTo(threshold)` call dispatched via JVM polymorphism.

- **`detectByClassSignature` 中的 `ReEntrantTransformerError`**: fallback 使用 `Class.forName("dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline")` 在 mixin prepare 阶段重新进入了 mixin transformer（在 transformer 仍在准备时加载了一个被 mixin 处理的类）。重写 `detectByClassSignature` 使用 `ClassLoader.getResourceAsStream` + ASM `ClassReader` 直接从字节码解析方法描述符，不触发任何类加载。/ **`ReEntrantTransformerError` in `detectByClassSignature`**: the fallback used `Class.forName("dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline")` during the mixin prepare phase, which re-entered the mixin transformer (loading a mixin-processed class while the transformer was still preparing). Rewrote `detectByClassSignature` to use `ClassLoader.getResourceAsStream` + ASM `ClassReader` to parse method descriptors directly from class bytecode, without triggering any class loading.

## v1.7.0

`OLKMO/FuckSable-Unofficial` GitHub 仓库的首次发布。汇总了 1.6.11–1.6.14 期间所有未发布的修复，并新增跨版本 Sable 1.x/2.x 支持。

First release on the `OLKMO/FuckSable-Unofficial` GitHub repository. Rolls up all unreleased fixes from 1.6.11–1.6.14 plus cross-version Sable 1.x/2.x support.

### 重大变更 / Major Changes

- **跨版本 Sable 支持 / Cross-version Sable support**: 重写 `FuckSableMixinConfigPlugin` 版本检测。改用 `ModList.getMods()` 在运行时读取 Sable mod 版本，并添加类签名 fallback 检查 `RapierPhysicsPipeline.addConstraint` 参数类型。修复了 `NoSuchMethodException: ModFileInfo.getModInfos()` bug——该 bug 在所有 Sable 版本上都会静默禁用 V1/V2 自约束修复 mixin。/ Rewritten `FuckSableMixinConfigPlugin` version detection. Now uses `ModList.getMods()` to read the Sable mod version at runtime, with a class-signature fallback that inspects `RapierPhysicsPipeline.addConstraint` parameter types. Fixes the `NoSuchMethodException: ModFileInfo.getModInfos()` bug that silently disabled both V1/V2 constraint self-fix mixins on every Sable version.

- **按版本自适应的约束自修复 / Version-specific constraint self-fix**: 新增 `RapierConstraintSelfFixMixinV1`（Sable 1.x，`ServerSubLevel` 参数）和 `RapierConstraintSelfFixMixinV2`（Sable 2.x，`PhysicsPipelineBody` 参数）。正确的 mixin 由上面的插件自动选择，因此单个 FuckSable 构建现在可同时在 Sable 1.2.x 和 2.0.x 上运行，无需编译时依赖。/ Added `RapierConstraintSelfFixMixinV1` (Sable 1.x, `ServerSubLevel` params) and `RapierConstraintSelfFixMixinV2` (Sable 2.x, `PhysicsPipelineBody` params). The correct mixin is auto-selected by the plugin above, so a single FuckSable build now runs on both Sable 1.2.x and 2.0.x without compile-time dependencies.

### 新增修复 / New Fixes

- `ServerLevelSendBlockUpdateMixin`: 当目标 plot holder 不存在时取消 `sendBlockUpdated` 调用。防止在 Sable 2.0.x 上出现 `UnsupportedOperationException: Cannot change blocks in nonexistent plot holder` 崩溃。/ Cancels `sendBlockUpdated` when the target plot holder is missing. Prevents `UnsupportedOperationException: Cannot change blocks in nonexistent plot holder` crash on Sable 2.0.x.

- `SubLevelStorageLogSpamMixin`: 将 "Couldn't find sub-level at index N" ERROR 日志限流为同一 chunk+index 每 60 秒输出一次。避免 sub-level 存储条目损坏时日志刷屏。/ Throttles "Couldn't find sub-level at index N" ERROR log to once per 60s per chunk+index. Stops log flooding when sub-level storage entries are corrupted.

- `FrogportItemExtractLimitMixin`: 当相邻库存槽位数超过 256 时跳过 `ItemHelper.extract`。防止 FrogportBlockEntity 扫描超大型漏斗链 / Create 仓库导致服务器卡死数秒。/ Skips `ItemHelper.extract` when adjacent inventory exceeds 256 slots. Prevents multi-second server freezes caused by FrogportBlockEntity scanning huge hopper chains / Create warehouses.

- `CttPostTickTimeoutGuardMixin`: 在 CTT `postTick` 中给 `Future.get()` 加 10 秒超时。若异步火车工作线程卡住（例如 Sable 物理自约束死循环），future 会被取消并打 WARN 日志，而不是让主线程挂起触发 Watchdog 崩溃。/ 10s timeout on `Future.get()` in CTT `postTick`. If the async train worker is stuck (e.g. Sable physics self-constraint loop), the future is cancelled and a warning is logged instead of hanging the main thread and triggering a Watchdog crash.

### 变更 / Changes

- `PlayerPositionGuardMixin`: 世界边界钳制放宽到 ±5（原 +1）。Y 轴钳制改为仅创造模式生效——生存模式玩家正常坠落，创造模式玩家被拉回 `minBuildHeight + 5` 之上。/ World-border clamp relaxed to ±5 (was +1). Y-axis clamp is now creative-only — survival players fall normally, creative players are pulled back above `minBuildHeight + 5`.

- 更新检查器现在查询 `OLKMO/FuckSable-Unofficial` Releases API。/ Update checker now queries the `OLKMO/FuckSable-Unofficial` Releases API.

- 构建产物重命名为 `FuckSable-Unofficial-1.7.0.jar`。/ Built jar is now named `FuckSable-Unofficial-1.7.0.jar`.

## v1.6.14

### Bug Fixes
- Fix `FuckSableMixinConfigPlugin` version detection: use `ModList.getMods()` + class signature fallback (fixes `NoSuchMethodException` that disabled both V1/V2 constraint self-fix mixins)

### New Fixes
- Add `ctt-posttick-timeout-guard`: 10s timeout on `Future.get()` in CTT `postTick` to prevent Watchdog server crash
- Add `RapierConstraintSelfFixMixinV1/V2`: version-specific mixins for Sable 1.x and 2.x `addConstraint`, auto-selected by `FuckSableMixinConfigPlugin`

## v1.6.13

### New Fixes
- Add `frogport-extract-limit`: skip `ItemHelper.extract` when adjacent inventory exceeds 256 slots to prevent server freeze

### Changes
- Update `player-position-guard`: clamp to world border+5, creative-only Y-axis clamp (survival falls normally)

## v1.6.12

### New Fixes
- Add `sublevel-load-log-spam-fix`: throttle "Couldn't find sub-level" ERROR log to once per 60s per chunk+index

## v1.6.11

### New Fixes
- Add `ServerLevelSendBlockUpdateMixin`: cancel `sendBlockUpdated` when plot holder missing to prevent crash on Sable 2.0.x

## v1.6.10

### Bug Fixes
- Prevent server crash when `TrackGraph.removeNode` triggers `Train.detachFromTracks` on a train with corrupted state (null `TravellingPoint.edge`): skips `TrainMigration` creation for points with null edge instead of throwing `NullPointerException` in `TrainMigration` constructor

## v1.6.9

### Bug Fixes
- Prevent server crash when Create train navigation searches with a null TrackNode (corrupted train state from CTT concurrent issues): `TrackGraph.getConnectionsFrom` returns empty Map instead of null to avoid NullPointerException in `Navigation.search`

## v1.6.8

### Bug Fixes
- Fix Vista camera chunk loading incompatibility with Sable physics structures: project ViewFinder SubLevel coordinates to world coordinates before force-loading chunks, preventing TPS drop and infinite loading loops
- Fix `SteamVentValueBoxTransformMixin` crash on Aeronautics 1.3.0+: remove `@Shadow direction` field (removed in upstream), use reflection to set direction field for cross-version compatibility

## v1.6.7

### Bug Fixes
- Fix CTT log spam fix mixin crash: correct `Logger.warn` target signature from `(String, Object)` to `(String, Throwable)`
- Fix `RapierPhysicsPipelineMixin` crash: remove unused `poseCache` `@Shadow` field that doesn't exist on some Sable versions
- Fix startup animation character misalignment

## v1.6.6

### Bug Fixes
- Suppress repeated CTT (CreateThreadedTrains) warning logs when train calculation fails — only logs once per error type

## v1.6.5

### Bug Fixes
- Fix physics structures spamming logs when repeatedly out of world bounds — now only warns once per SubLevel, silences subsequent clamps

### Changes
- Add Discord community link to README

## v1.6.4

### Bug Fixes
- Fix `ParticleEngine.crack()` method signature mismatch causing client crash (fixes #1)
- Fix `RapierPhysicsPipelineMixin` crash: `sceneId` field removed, `cache` renamed to `poseCache` in Sable 2.0.2+ (fixes #2)
- Update `SteamVentValueBoxTransformMixin` to also cover `fromSide` method for Aeronautics compat (fixes #3)

### Changes
- Change 18 fix entries from `Side.SERVER` to `Side.BOTH` so fixes also work in singleplayer (integrated server)

## v1.6.3

### Bug Fixes
- Fix crash on Sable 2.0.2+: `@Shadow field sceneId was not located in RapierPhysicsPipeline` (field removed in upstream)
- Remove fstemp3/fsban/fslook features (conflicted with core functionality)
- Restore auto-update to config-controlled behavior

## v1.6.0

### Breaking Changes
- Remove 16 low-impact performance optimization mixins to reduce compatibility risks
- Add FixEntry.Side mechanism (SERVER/CLIENT/BOTH) so fixes only apply on their target side

### New Fixes
- Light engine bounds guard: prevent crash when SubLevel sections exceed world height limits
- Player position guard: clamp player position to world border when coordinates exceed boundaries
- Physics ticket guard: prevent crash from DistanceManager internal state corruption
- Copycats compat: prevent crash when Copycats blocks missing facing property
- SubLevel entity getter guard: prevent server freeze from abnormally large AABBs

### Bug Fixes
- Fix typewriter sneak-click not opening config GUI (@Overwrite -> @WrapMethod)
- Add try-catch to CarryOn placement/teleport projection to prevent crashes
