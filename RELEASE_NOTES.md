## v1.6.7

### Bug Fixes
- Fix CTT log spam fix mixin crash: correct `Logger.warn` target signature from `(String, Object)` to `(String, Throwable)`

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
