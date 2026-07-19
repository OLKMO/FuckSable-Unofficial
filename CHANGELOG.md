# Changelog

All notable changes to FuckSable will be documented in this file.

## [1.7.4] - 2026-07-19

### Bug Fixes
- Fix `entity-lookup-remove-guard` mixin injection failure that crashed server startup in v1.7.3 (`MixinTransformerError: Critical injection failure: Redirector fucksable$safeEntityLookupRemove ... Scanned 0 target(s)`). The `@At` target descriptor incorrectly used `Entity` as the parameter type, but `EntityLookup<T extends EntityAccess>.remove(T)` and `PersistentEntitySectionManager.stopTracking(T)` are erased to `(Lnet/minecraft/world/level/entity/EntityAccess;)V` at compile time. Now the target descriptor and handler signature use `EntityAccess`.

## [1.7.3] - 2026-07-19

### New Fixes
- Add `entity-lookup-remove-guard`: catches `ArrayIndexOutOfBoundsException` thrown by `Int2ObjectLinkedOpenHashMap.fixPointers` inside `EntityLookup.remove` during `PersistentEntitySectionManager.stopTracking`, preventing single-entity removal failures from crashing the server tick loop. Root cause is Sable's SubLevel entity management corrupting the EntityLookup internal linked-map state (entry `prev`/`next` pointer set to `-1` and accessed as array index). Fix is symptomatic — suppresses the AIOOBE at the call site so the tick survives, with a WARN log per occurrence for diagnosis.

## [1.7.2] - 2026-07-19

### New Fixes
- Add `udp-invalid-packet-guard`: silently drop UDP packets with invalid packet IDs (e.g. legacy Minecraft server list ping packet ID 254) at the head of `SableUDPPacketDecoder.decode` instead of letting Sable throw `IOException("Received an invalid packet ID: 254")`. Stops the recurring `Server UDP channel caught exception` ERROR log spam triggered by server-list-ping probes and UDP scans hitting Sable's UDP port.

## [1.7.1] - 2026-07-18

### Bug Fixes
- Fix `FuckSableMixinConfigPlugin` crashing on Mohist/Youer 1.21.1 dedicated servers during mixin prepare phase:
  - `ArtifactVersion.compareTo` lookup via `getMethod("compareTo", artifactVersionClass)` threw `NoSuchMethodException` because `Comparable<ArtifactVersion>` bridge method has parameter type `Object` (erased), not `ArtifactVersion`. Replaced with direct `((Comparable) version).compareTo(threshold)` call dispatched via JVM polymorphism.
  - Fallback `detectByClassSignature` used `Class.forName("dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline")` which triggered `ReEntrantTransformerError` because loading a mixin-processed class during the prepare phase re-enters the mixin transformer. Rewritten to use `ClassLoader.getResourceAsStream` + ASM `ClassReader` to parse method descriptors directly from class bytecode without triggering class loading.

## [1.7.0] - 2026-07-16

First release on the `OLKMO/FuckSable-Unofficial` GitHub repository. This version rolls up all unreleased fixes from 1.6.11–1.6.14 plus cross-version Sable 1.x/2.x support.

### Major Changes
- **Cross-version Sable support**: rewritten `FuckSableMixinConfigPlugin` version detection. Now uses `ModList.getMods()` to read the Sable mod version at runtime, with a class-signature fallback that inspects `RapierPhysicsPipeline.addConstraint` parameter types. This fixes the `NoSuchMethodException: ModFileInfo.getModInfos()` bug that silently disabled both V1/V2 constraint self-fix mixins on every Sable version.
- **Version-specific constraint self-fix**: added `RapierConstraintSelfFixMixinV1` (Sable 1.x, `ServerSubLevel` params) and `RapierConstraintSelfFixMixinV2` (Sable 2.x, `PhysicsPipelineBody` params). The correct mixin is auto-selected by the plugin above, so a single FuckSable build now runs on both Sable 1.2.x and 2.0.x without compile-time dependencies.

### New Fixes
- `ServerLevelSendBlockUpdateMixin`: cancel `sendBlockUpdated` when the target plot holder is missing. Prevents the `UnsupportedOperationException: Cannot change blocks in nonexistent plot holder` crash that occurred on Sable 2.0.x when blocks were updated inside unloaded sub-levels.
- `SubLevelStorageLogSpamMixin`: throttle the "Couldn't find sub-level at index N" ERROR log to once per 60 seconds per chunk+index pair. Stops log flooding when sub-level storage entries are corrupted or missing.
- `FrogportItemExtractLimitMixin`: skip `ItemHelper.extract` when an adjacent inventory exceeds 256 slots. Prevents multi-second server freezes caused by FrogportBlockEntity scanning huge hopper chains / Create warehouses during `lazyTick`.
- `CttPostTickTimeoutGuardMixin`: wrap `Future.get()` inside CTT `CreateThreadedTrains.postTick` with a 10-second timeout. If the async train worker task is stuck (e.g. Sable physics self-constraint loop), the future is cancelled and a warning is logged instead of hanging the main thread and triggering a Watchdog crash.

### Changes
- `PlayerPositionGuardMixin`: world-border clamp relaxed to ±5 (was +1). Y-axis clamp is now creative-only — survival players fall normally, creative players are pulled back above `minBuildHeight + 5`.
- Update checker now queries the `OLKMO/FuckSable-Unofficial` Releases API.
- Built jar is now named `FuckSable-Unofficial-1.7.0.jar`.

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
