# Changelog

All notable changes to FuckSable will be documented in this file.

## [1.7.4] - 2026-07-19

### Bug 修复 / Bug Fixes
- 修复 v1.7.3 中 `entity-lookup-remove-guard` mixin 注入失败导致服务器启动崩溃的问题（`MixinTransformerError: Critical injection failure: Redirector fucksable$safeEntityLookupRemove ... Scanned 0 target(s)`）。`@At` 的 target 描述符错误使用了 `Entity` 作为参数类型，但 `EntityLookup<T extends EntityAccess>.remove(T)` 和 `PersistentEntitySectionManager.stopTracking(T)` 由于 Java 泛型擦除，编译后实际签名是 `(Lnet/minecraft/world/level/entity/EntityAccess;)V`。现在 target 描述符和 handler 签名都使用 `EntityAccess`。/ Fix `entity-lookup-remove-guard` mixin injection failure that crashed server startup in v1.7.3 (`MixinTransformerError: Critical injection failure: Redirector fucksable$safeEntityLookupRemove ... Scanned 0 target(s)`). The `@At` target descriptor incorrectly used `Entity` as the parameter type, but `EntityLookup<T extends EntityAccess>.remove(T)` and `PersistentEntitySectionManager.stopTracking(T)` are erased to `(Lnet/minecraft/world/level/entity/EntityAccess;)V` at compile time. Now the target descriptor and handler signature use `EntityAccess`.

## [1.7.3] - 2026-07-19

### 新增修复 / New Fixes
- 新增 `entity-lookup-remove-guard`：拦截 `PersistentEntitySectionManager.stopTracking` 中 `EntityLookup.remove` 调用里 `Int2ObjectLinkedOpenHashMap.fixPointers` 抛出的 `ArrayIndexOutOfBoundsException`，避免单个实体移除失败导致整个服务器 tick 崩溃。根因是 Sable 的 SubLevel 实体管理破坏了 EntityLookup 内部链表 map 的状态（entry 的 `prev`/`next` 指针被设为 `-1` 后被当作数组下标访问）。属于治标修复——在调用点抑制 AIOOBE 让 tick 存活，每次发生输出一条 WARN 日志便于排查。/ Add `entity-lookup-remove-guard`: catches `ArrayIndexOutOfBoundsException` thrown by `Int2ObjectLinkedOpenHashMap.fixPointers` inside `EntityLookup.remove` during `PersistentEntitySectionManager.stopTracking`, preventing single-entity removal failures from crashing the server tick loop. Root cause is Sable's SubLevel entity management corrupting the EntityLookup internal linked-map state (entry `prev`/`next` pointer set to `-1` and accessed as array index). Fix is symptomatic — suppresses the AIOOBE at the call site so the tick survives, with a WARN log per occurrence for diagnosis.

## [1.7.2] - 2026-07-19

### 新增修复 / New Fixes
- 新增 `udp-invalid-packet-guard`：在 `SableUDPPacketDecoder.decode` 头部静默丢弃 packet ID 越界的 UDP 数据包（如旧版 Minecraft 服务器列表 ping 的 packet ID 254），不让 Sable 抛出 `IOException("Received an invalid packet ID: 254")`。消除服务器列表 ping 探测和 UDP 扫描 Sable UDP 端口时反复刷屏的 `Server UDP channel caught exception` ERROR 日志。/ Add `udp-invalid-packet-guard`: silently drop UDP packets with invalid packet IDs (e.g. legacy Minecraft server list ping packet ID 254) at the head of `SableUDPPacketDecoder.decode` instead of letting Sable throw `IOException("Received an invalid packet ID: 254")`. Stops the recurring `Server UDP channel caught exception` ERROR log spam triggered by server-list-ping probes and UDP scans hitting Sable's UDP port.

## [1.7.1] - 2026-07-18

### Bug 修复 / Bug Fixes
- 修复 `FuckSableMixinConfigPlugin` 在 Mohist/Youer 1.21.1 专用服务端 mixin prepare 阶段崩溃的问题：/ Fix `FuckSableMixinConfigPlugin` crashing on Mohist/Youer 1.21.1 dedicated servers during mixin prepare phase:
  - `ArtifactVersion.compareTo` 通过 `getMethod("compareTo", artifactVersionClass)` 查找失败抛 `NoSuchMethodException`，因为 `Comparable<ArtifactVersion>` 桥接方法参数类型被擦除为 `Object` 而非 `ArtifactVersion`。改为直接使用 `((Comparable) version).compareTo(threshold)` 通过 JVM 多态派发。/ `ArtifactVersion.compareTo` lookup via `getMethod("compareTo", artifactVersionClass)` threw `NoSuchMethodException` because `Comparable<ArtifactVersion>` bridge method has parameter type `Object` (erased), not `ArtifactVersion`. Replaced with direct `((Comparable) version).compareTo(threshold)` call dispatched via JVM polymorphism.
  - fallback 的 `detectByClassSignature` 使用 `Class.forName("dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline")` 触发 `ReEntrantTransformerError`，因为在 prepare 阶段加载一个被 mixin 处理的类会重新进入 mixin transformer。重写为使用 `ClassLoader.getResourceAsStream` + ASM `ClassReader` 直接从字节码解析方法描述符，不触发任何类加载。/ Fallback `detectByClassSignature` used `Class.forName("dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline")` which triggered `ReEntrantTransformerError` because loading a mixin-processed class during the prepare phase re-enters the mixin transformer. Rewritten to use `ClassLoader.getResourceAsStream` + ASM `ClassReader` to parse method descriptors directly from class bytecode without triggering class loading.

## [1.7.0] - 2026-07-16

`OLKMO/FuckSable-Unofficial` GitHub 仓库的首次发布。本版本汇总了 1.6.11–1.6.14 期间所有未发布的修复，并新增跨版本 Sable 1.x/2.x 支持。

First release on the `OLKMO/FuckSable-Unofficial` GitHub repository. This version rolls up all unreleased fixes from 1.6.11–1.6.14 plus cross-version Sable 1.x/2.x support.

### 重大变更 / Major Changes
- **跨版本 Sable 支持 / Cross-version Sable support**: 重写 `FuckSableMixinConfigPlugin` 版本检测。改用 `ModList.getMods()` 在运行时读取 Sable mod 版本，并添加类签名 fallback 检查 `RapierPhysicsPipeline.addConstraint` 参数类型。修复了 `NoSuchMethodException: ModFileInfo.getModInfos()` bug——该 bug 在所有 Sable 版本上都会静默禁用 V1/V2 自约束修复 mixin。/ Rewritten `FuckSableMixinConfigPlugin` version detection. Now uses `ModList.getMods()` to read the Sable mod version at runtime, with a class-signature fallback that inspects `RapierPhysicsPipeline.addConstraint` parameter types. This fixes the `NoSuchMethodException: ModFileInfo.getModInfos()` bug that silently disabled both V1/V2 constraint self-fix mixins on every Sable version.
- **按版本自适应的约束自修复 / Version-specific constraint self-fix**: 新增 `RapierConstraintSelfFixMixinV1`（Sable 1.x，`ServerSubLevel` 参数）和 `RapierConstraintSelfFixMixinV2`（Sable 2.x，`PhysicsPipelineBody` 参数）。正确的 mixin 由上面的插件自动选择，因此单个 FuckSable 构建现在可同时在 Sable 1.2.x 和 2.0.x 上运行，无需编译时依赖。/ Added `RapierConstraintSelfFixMixinV1` (Sable 1.x, `ServerSubLevel` params) and `RapierConstraintSelfFixMixinV2` (Sable 2.x, `PhysicsPipelineBody` params). The correct mixin is auto-selected by the plugin above, so a single FuckSable build now runs on both Sable 1.2.x and 2.0.x without compile-time dependencies.

### 新增修复 / New Fixes
- `ServerLevelSendBlockUpdateMixin`: 当目标 plot holder 不存在时取消 `sendBlockUpdated` 调用。防止在 Sable 2.0.x 上方块在未加载的 sub-level 中更新时出现 `UnsupportedOperationException: Cannot change blocks in nonexistent plot holder` 崩溃。/ Cancel `sendBlockUpdated` when the target plot holder is missing. Prevents the `UnsupportedOperationException: Cannot change blocks in nonexistent plot holder` crash that occurred on Sable 2.0.x when blocks were updated inside unloaded sub-levels.
- `SubLevelStorageLogSpamMixin`: 将 "Couldn't find sub-level at index N" ERROR 日志限流为同一 chunk+index 每 60 秒输出一次。避免 sub-level 存储条目损坏或缺失时日志刷屏。/ Throttle the "Couldn't find sub-level at index N" ERROR log to once per 60 seconds per chunk+index pair. Stops log flooding when sub-level storage entries are corrupted or missing.
- `FrogportItemExtractLimitMixin`: 当相邻库存槽位数超过 256 时跳过 `ItemHelper.extract`。防止 FrogportBlockEntity 在 `lazyTick` 中扫描超大型漏斗链 / Create 仓库导致服务器卡死数秒。/ Skip `ItemHelper.extract` when an adjacent inventory exceeds 256 slots. Prevents multi-second server freezes caused by FrogportBlockEntity scanning huge hopper chains / Create warehouses during `lazyTick`.
- `CttPostTickTimeoutGuardMixin`: 在 CTT `CreateThreadedTrains.postTick` 中给 `Future.get()` 加 10 秒超时。若异步火车工作线程卡住（例如 Sable 物理自约束死循环），future 会被取消并打 WARN 日志，而不是让主线程挂起触发 Watchdog 崩溃。/ Wrap `Future.get()` inside CTT `CreateThreadedTrains.postTick` with a 10-second timeout. If the async train worker task is stuck (e.g. Sable physics self-constraint loop), the future is cancelled and a warning is logged instead of hanging the main thread and triggering a Watchdog crash.

### 变更 / Changes
- `PlayerPositionGuardMixin`: 世界边界钳制放宽到 ±5（原 +1）。Y 轴钳制改为仅创造模式生效——生存模式玩家正常坠落，创造模式玩家被拉回 `minBuildHeight + 5` 之上。/ World-border clamp relaxed to ±5 (was +1). Y-axis clamp is now creative-only — survival players fall normally, creative players are pulled back above `minBuildHeight + 5`.
- 更新检查器现在查询 `OLKMO/FuckSable-Unofficial` Releases API。/ Update checker now queries the `OLKMO/FuckSable-Unofficial` Releases API.
- 构建产物重命名为 `FuckSable-Unofficial-1.7.0.jar`。/ Built jar is now named `FuckSable-Unofficial-1.7.0.jar`.

## [1.6.14] - 2026-07-08

### Bug Fixes
- Fix `FuckSableMixinConfigPlugin` version detection: use `ModList.getMods()` instead of nonexistent `getModInfos()`, add class signature detection fallback for Sable version (fixes `NoSuchMethodException` that disabled both V1/V2 constraint self-fix mixins)

### New Fixes
- Add `CttPostTickTimeoutGuardMixin`: 10s timeout on `Future.get()` in CTT `postTick` to prevent Watchdog server crash when async train worker is stuck
- Add `RapierConstraintSelfFixMixinV1/V2`: version-specific mixins for Sable 1.x (ServerSubLevel params) and 2.x (PhysicsPipelineBody params) `addConstraint` method, auto-selected by `FuckSableMixinConfigPlugin`

## [1.6.13] - 2026-06-29

### New Fixes
- Add `FrogportItemExtractLimitMixin`: skip `ItemHelper.extract` when adjacent inventory exceeds 256 slots to prevent server freeze from oversized inventories (hopper chains, Create warehouses)

### Changes
- Update `PlayerPositionGuardMixin`: clamp to world border+5 (was +1), creative-only Y-axis clamp (survival mode falls normally, creative mode clamped to minBuildHeight+5)

## [1.6.12] - 2026-06-29

### New Fixes
- Add `SubLevelStorageLogSpamMixin`: throttle "Couldn't find sub-level" ERROR log to once per 60s per chunk+index, preventing log spam when sub-level storage entry is corrupted/missing

## [1.6.11] - 2026-06-29

### New Fixes
- Add `ServerLevelSendBlockUpdateMixin`: cancel `sendBlockUpdated` when plot holder is missing to prevent `UnsupportedOperationException: Cannot change blocks in nonexistent plot holder` crash on Sable 2.0.x

## [1.6.10] - 2026-06-27

### Bug Fixes
- Prevent server crash when `TrackGraph.removeNode` triggers `Train.detachFromTracks` on a train with corrupted state (null `TravellingPoint.edge`): skips `TrainMigration` creation for points with null edge instead of throwing `NullPointerException` in `TrainMigration` constructor (fixes server crash when placing/breaking rails near trains with corrupted carriage state)

## [1.6.9] - 2026-06-25

### Bug Fixes
- Prevent server crash when Create train navigation searches with a null TrackNode (corrupted train state from CTT concurrent issues): `TrackGraph.getConnectionsFrom` returns empty Map instead of null to avoid NullPointerException in `Navigation.search` (fixes server crash when driving trains with corrupted carriage state)

## [1.6.8] - 2026-06-19

### Bug Fixes
- Fix Vista camera chunk loading incompatibility with Sable physics structures: project ViewFinder SubLevel coordinates to world coordinates before force-loading chunks, preventing TPS drop and infinite loading loops
- Fix `SteamVentValueBoxTransformMixin` crash on Aeronautics 1.3.0+: remove `@Shadow direction` field (removed in upstream), use reflection to set direction field for cross-version compatibility

## [1.6.7] - 2026-06-19

### Bug Fixes
- Fix CTT log spam fix mixin crash: correct `Logger.warn` target signature from `(String, Object)` to `(String, Throwable)`
- Fix `RapierPhysicsPipelineMixin` crash: remove unused `poseCache` `@Shadow` field that doesn't exist on some Sable versions
- Fix startup animation character misalignment

## [1.6.6] - 2026-06-18

### Bug Fixes
- Suppress repeated CTT (CreateThreadedTrains) warning logs when train calculation fails — only logs once per error type
- Fix physics structures spamming logs when repeatedly out of world bounds — now only warns once per SubLevel

## [1.6.5] - 2026-06-18

### Bug Fixes
- Fix physics structures spamming logs when repeatedly out of world bounds — now only warns once per SubLevel, silences subsequent clamps

### Changes
- Add Discord community link to README

## [1.6.4] - 2026-06-17

### Bug Fixes
- Fix `ParticleEngine.crack()` method signature mismatch causing client crash (fixes #1)
- Fix `RapierPhysicsPipelineMixin` crash: `sceneId` field removed, `cache` renamed to `poseCache` in Sable 2.0.2+ (fixes #2)
- Update `SteamVentValueBoxTransformMixin` to also cover `fromSide` method for Aeronautics compat (fixes #3)

### Changes
- Change 18 fix entries from `Side.SERVER` to `Side.BOTH` so fixes also work in singleplayer (integrated server)

## [1.6.3] - 2026-06-16

### Bug Fixes
- Fix crash on Sable 2.0.2+: `@Shadow field sceneId was not located in RapierPhysicsPipeline` (field removed in upstream)
- Remove fstemp3/fsban/fslook features (conflicted with core functionality)
- Restore auto-update to config-controlled behavior

## [1.6.0] - 2026-06-14

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
