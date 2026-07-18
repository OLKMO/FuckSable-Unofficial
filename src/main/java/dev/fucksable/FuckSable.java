package dev.fucksable;

import dev.fucksable.command.FuckSableCommand;
import dev.fucksable.command.FuckSableLangCommand;
import dev.fucksable.fix.FixEntry;
import dev.fucksable.fix.FixRegistry;
import dev.fucksable.i18n.LanguageManager;
import dev.fucksable.update.UpdateChecker;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Set;

@Mod(FuckSable.MOD_ID)
public class FuckSable {
    public static final String MOD_ID = "fucksable";
    public static final String VERSION = "1.7.1";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static FuckSableConfig config;
    private static ModContainer modContainer;

    public FuckSable(IEventBus bus, ModContainer container) {
        modContainer = container;

        // 1. 初始化i18n
        Path configDir = FMLPaths.CONFIGDIR.get();
        LanguageManager.init(configDir);

        // 2. 加载配置
        config = FuckSableConfig.load(configDir);

        // 3. 应用配置中的语言偏好
        LanguageManager.setLanguage(config.getLanguage());

        // 0. 启动动画（在配置加载后，以便判断彩蛋开关）
        printBanner(configDir);

        // 4. 注册内置修复项

        // === 核心修复 ===
        FixRegistry.register("async-save",
            "Redirects SubLevel save operations to an async I/O thread to prevent server freezes during saves",
            true, FixEntry.Side.BOTH);
        FixRegistry.register("panic-guard",
            "Adds safety checks before Rust native calls to prevent server crashes from panics in native code",
            true, FixEntry.Side.BOTH);
        FixRegistry.register("write-flush",
            "Ensures data is flushed to disk before updating storage file headers, preventing data corruption on crash",
            true, FixEntry.Side.BOTH);
        FixRegistry.register("corrupted-cleanup",
            "Removes corrupted sub-level pointers from holding chunks to prevent repeated load errors",
            true, FixEntry.Side.BOTH);

        // === 兼容性修复 ===
        FixRegistry.register("carryon-compat",
            "Fixes CarryOn placing players on physics sub-levels causing teleportation to wrong dimensions",
            true, Set.of("carryon"), FixEntry.Side.BOTH);
        FixRegistry.register("typewriter-server-fix",
            "Fixes Simulated mod typewriter crashing dedicated servers due to client-only class references in common code",
            true, Set.of("simulated"), FixEntry.Side.BOTH);
        FixRegistry.register("command-block-sublevel-fix",
            "Prevents command blocks (and variants) from being placed on physics sub-levels, which bypasses vanilla restrictions",
            true, Set.of("sable"), FixEntry.Side.BOTH);
        FixRegistry.register("aeronautics-server-fix",
            "Fixes Aeronautics SteamVentBlockEntity crashing dedicated servers due to client-only class references in common code",
            true, Set.of("aeronautics"), FixEntry.Side.BOTH);
        FixRegistry.register("aeronautics-slime-bearfix",
            "Fixes slime blocks sticking to bearing structures causing them to separate and clip through blocks",
            false, Set.of("aeronautics"), FixEntry.Side.BOTH);
        FixRegistry.register("physics-staff-drag-clipfix",
            "Prevents physics structures from clipping through physics blocks when dragged at high speed with the physics staff",
            true, Set.of("simulated"), FixEntry.Side.BOTH);
        FixRegistry.register("plot-holder-guard",
            "Prevents server crash when block changes occur in plot chunks without a holder (e.g. bamboo growing near unloaded physics structures)",
            true, Set.of("sable"), FixEntry.Side.BOTH);
        FixRegistry.register("copycats-lift-compat",
            "Prevents server crash when Copycats blocks with missing facing property trigger sable$getNormal in onBlockChange",
            true, Set.of("sable", "copycats"), FixEntry.Side.BOTH);
        FixRegistry.register("player-position-guard",
            "Clamps player position to world border when coordinates exceed boundaries, preventing server crashes from SubLevel physics",
            true, Set.of("sable"), FixEntry.Side.BOTH);
        FixRegistry.register("light-engine-bounds-guard",
            "Prevents light engine crashes when SubLevel sections exceed world height limits during light propagation",
            true, Set.of("sable"), FixEntry.Side.BOTH);
        FixRegistry.register("physics-ticket-guard",
            "Prevents server crash when PhysicsChunkTicketManager triggers DistanceManager internal state corruption (ArrayIndexOutOfBoundsException in LeveledPriorityQueue)",
            true, Set.of("sable"), FixEntry.Side.BOTH);
        FixRegistry.register("sublevel-entity-guard",
            "Prevents server freeze when SubLevelInclusiveLevelEntityGetter iterates over abnormally large AABBs caused by corrupted entity section storage",
            true, Set.of("sable"), FixEntry.Side.BOTH);
        FixRegistry.register("sublevel-volume-limit",
            "Limits the maximum block count of a single physics structure to prevent server lag and Rapier native crashes from oversized collision bodies",
            true, Set.of("sable"), FixEntry.Side.BOTH);
        FixRegistry.register("ctt-concurrent-fix",
            "Fixes ConcurrentModificationException when CreateThreadedTrains ticks trains on worker threads while main thread modifies passenger data",
            true, Set.of("createthreadedtrains"), FixEntry.Side.BOTH);
        FixRegistry.register("ctt-log-spam-fix",
            "Suppresses repeated warning logs from CreateThreadedTrains when train calculation fails, only logs once per error type",
            true, Set.of("createthreadedtrains"), FixEntry.Side.BOTH);
        FixRegistry.register("create-trackgraph-null-guard",
            "Prevents server crash when Create train navigation searches with a null TrackNode (corrupted train state from CTT concurrent issues): TrackGraph.getConnectionsFrom returns empty Map instead of null to avoid NullPointerException in Navigation.search",
            true, Set.of("create"), FixEntry.Side.BOTH);
        FixRegistry.register("create-train-detach-nulledge-guard",
            "Prevents server crash when TrackGraph.removeNode triggers Train.detachFromTracks on a train with corrupted state (null TravellingPoint.edge): skips TrainMigration creation for points with null edge instead of throwing NullPointerException in TrainMigration constructor",
            true, Set.of("create"), FixEntry.Side.BOTH);
        FixRegistry.register("sublevel-load-log-spam-fix",
            "Throttles repeated 'Couldn't find sub-level' ERROR log spam from SubLevelStorage.attemptLoadSubLevel when a sub-level storage entry is corrupted/missing: logs once per chunk+index per 60s window instead of every retry",
            true, Set.of("sable"), FixEntry.Side.BOTH);
        FixRegistry.register("frogport-extract-limit",
            "Prevents server freeze when FrogportBlockEntity.lazyTick pulls items from oversized adjacent inventories (hopper chains, Create warehouses): skips ItemHelper.extract when IItemHandler slot count exceeds 256, logs once per 60s",
            true, Set.of("create"), FixEntry.Side.BOTH);
        FixRegistry.register("ctt-posttick-timeout-guard",
            "Prevents Watchdog server crash when CreateThreadedTrains.postTick blocks the main thread waiting for a stuck async train worker: replaces Future.get() with a 10s timeout, cancels and skips on timeout to keep the server alive",
            true, Set.of("createthreadedtrains"), FixEntry.Side.BOTH);

        // === 客户端修复 ===
        FixRegistry.register("effortless-particle-fix",
            "Fixes Effortless client crash when interacting with Sable physics structures by skipping particle generation for unloaded chunks (Plot storage area coordinates)",
            true, Set.of("effortless", "sable"), FixEntry.Side.CLIENT);

        // === Vista 兼容修复 ===
        FixRegistry.register("vista-camera-chunk-fix",
            "Fixes Vista camera chunk loading incompatibility with Sable physics structures: projects ViewFinder SubLevel coordinates to world coordinates before force-loading chunks, preventing TPS drop and infinite loading loops",
            true, Set.of("vista", "sable"), FixEntry.Side.BOTH);

        // === 物理引擎修复 ===
        FixRegistry.register("constraint-self-fix",
            "Suppresses self-constraint errors in Sable physics pipeline: when a constraint is added between a SubLevel and itself, returns null instead of throwing IllegalArgumentException, preventing log spam",
            true, Set.of("sable"), FixEntry.Side.BOTH);

        // === 彩蛋 ===
        FixRegistry.register("fuck-op-player",
            "Easter egg: replaces the startup banner with 'fuck <random OP player name>' instead of 'FUCK SABLE'",
            false, null, FixEntry.Side.BOTH, true);

        // 5. 检测环境条件（前置mod是否加载）
        FixRegistry.checkEnvironment(modId -> {
            boolean loaded = net.neoforged.fml.loading.FMLLoader.getLoadingModList().getModFileById(modId) != null;
            if (!loaded) {
                LOGGER.info("Mod '{}' not found, related fixes will be disabled", modId);
            }
            return loaded;
        });

        // 6. 应用配置中的修复项状态
        for (FixEntry entry : FixRegistry.getAllFixes()) {
            Boolean state = config.getFixStates().get(entry.getId());
            if (state != null) {
                entry.setEnabled(state);
            }
        }

        // 7. 自动更新检查
        if (config.isAutoUpdate()) {
            UpdateChecker.checkAsync();
        }

        // 8. 注册事件监听
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);

        LOGGER.info("fuck Sable v{} loaded - {} fixes registered", VERSION, FixRegistry.getAllFixes().size());
    }

    private void printBanner(Path configDir) {
        // 检查配置中是否启用了彩蛋
        Boolean fuckOpState = config.getFixStates().get("fuck-op-player");
        boolean fuckOpEnabled = fuckOpState != null && fuckOpState;

        if (fuckOpEnabled) {
            // 从 ops.json 读取 OP 玩家名
            java.util.List<String> opNames = new java.util.ArrayList<>();
            try {
                // configDir 是 <server>/config，ops.json 在 <server>/ops.json
                var opsPath = configDir.getParent().resolve("ops.json");
                if (java.nio.file.Files.exists(opsPath)) {
                    var reader = java.nio.file.Files.newBufferedReader(opsPath);
                    var arr = com.google.gson.JsonParser.parseReader(reader).getAsJsonArray();
                    reader.close();
                    for (var elem : arr) {
                        var obj = elem.getAsJsonObject();
                        if (obj.has("name")) {
                            opNames.add(obj.get("name").getAsString());
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.debug("Failed to read ops.json for fuck-op-player easter egg", e);
            }

            if (!opNames.isEmpty()) {
                String target = opNames.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(opNames.size()));
                ConsoleAnsiArtist.printAnsiText("FUCK " + target.toUpperCase(), "255,165,0", "");
                System.out.println();
                return;
            }
        }

        // 默认横幅
        ConsoleAnsiArtist.printAnsiText("FUCK SABLE", "255,80,80", "");
        System.out.println();
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        FuckSableCommand.register(event.getDispatcher());
        FuckSableLangCommand.register(event.getDispatcher());
    }

    private void onServerStopping(ServerStoppingEvent event) {
        config.save(FMLPaths.CONFIGDIR.get());
    }

    public static void saveConfig() {
        if (config != null) {
            config.save(FMLPaths.CONFIGDIR.get());
        }
    }

    public static ModContainer getModContainer() { return modContainer; }
}
