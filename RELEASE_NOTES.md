## v1.7.1

### Bug Fixes
- Fix server startup crash on **Mohist/Youer 1.21.1** dedicated servers caused by `ReEntrantTransformerError: Re-entrance error` in `FuckSableMixinConfigPlugin`:
  - **`NoSuchMethodException` on `ArtifactVersion.compareTo`**: on hybrid servers (Mohist/Youer), `ArtifactVersion`'s `compareTo` is a `Comparable<ArtifactVersion>` bridge method whose erased parameter type is `Object`, so `getMethod("compareTo", artifactVersionClass)` failed to find it. Replaced with a direct `((Comparable) version).compareTo(threshold)` call dispatched via JVM polymorphism.
  - **`ReEntrantTransformerError` in `detectByClassSignature`**: the fallback used `Class.forName("dev.ryanhcode.sable.physics.impl.rapier.RapierPhysicsPipeline")` during the mixin prepare phase, which re-entered the mixin transformer (loading a mixin-processed class while the transformer was still preparing). Rewrote `detectByClassSignature` to use `ClassLoader.getResourceAsStream` + ASM `ClassReader` to parse method descriptors directly from class bytecode, without triggering any class loading.

## v1.7.0

First release on the `OLKMO/FuckSable-Unofficial` GitHub repository. Rolls up all unreleased fixes from 1.6.11–1.6.14 plus cross-version Sable 1.x/2.x support.

### Major Changes
- **Cross-version Sable support**: rewritten `FuckSableMixinConfigPlugin` version detection. Now uses `ModList.getMods()` to read the Sable mod version at runtime, with a class-signature fallback that inspects `RapierPhysicsPipeline.addConstraint` parameter types. Fixes the `NoSuchMethodException: ModFileInfo.getModInfos()` bug that silently disabled both V1/V2 constraint self-fix mixins on every Sable version.
- **Version-specific constraint self-fix**: added `RapierConstraintSelfFixMixinV1` (Sable 1.x, `ServerSubLevel` params) and `RapierConstraintSelfFixMixinV2` (Sable 2.x, `PhysicsPipelineBody` params). The correct mixin is auto-selected by the plugin above, so a single FuckSable build now runs on both Sable 1.2.x and 2.0.x without compile-time dependencies.

### New Fixes
- `ServerLevelSendBlockUpdateMixin`: cancel `sendBlockUpdated` when the target plot holder is missing. Prevents `UnsupportedOperationException: Cannot change blocks in nonexistent plot holder` crash on Sable 2.0.x.
- `SubLevelStorageLogSpamMixin`: throttle "Couldn't find sub-level at index N" ERROR log to once per 60s per chunk+index. Stops log flooding when sub-level storage entries are corrupted.
- `FrogportItemExtractLimitMixin`: skip `ItemHelper.extract` when adjacent inventory exceeds 256 slots. Prevents multi-second server freezes caused by FrogportBlockEntity scanning huge hopper chains / Create warehouses.
- `CttPostTickTimeoutGuardMixin`: 10s timeout on `Future.get()` in CTT `postTick`. If the async train worker is stuck (e.g. Sable physics self-constraint loop), the future is cancelled and a warning is logged instead of hanging the main thread and triggering a Watchdog crash.

### Changes
- `PlayerPositionGuardMixin`: world-border clamp relaxed to ±5 (was +1). Y-axis clamp is now creative-only — survival players fall normally, creative players are pulled back above `minBuildHeight + 5`.
- Update checker now queries the `OLKMO/FuckSable-Unofficial` Releases API.
- Built jar is now named `FuckSable-Unofficial-1.7.0.jar`.

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
