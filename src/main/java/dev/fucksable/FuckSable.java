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
    public static final String VERSION = "1.5.0";
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

        // 4. 注册内置修复项（描述直接写在注册代码中）
        FixRegistry.register("async-save",
            "Redirects SubLevel save operations to an async I/O thread to prevent server freezes during saves",
            true);
        FixRegistry.register("panic-guard",
            "Adds safety checks before Rust native calls to prevent server crashes from panics in native code",
            true);
        FixRegistry.register("write-flush",
            "Ensures data is flushed to disk before updating storage file headers, preventing data corruption on crash",
            true);
        FixRegistry.register("corrupted-cleanup",
            "Removes corrupted sub-level pointers from holding chunks to prevent repeated load errors",
            true);

        // 兼容性修复（需要对应mod存在才启用）
        FixRegistry.register("carryon-compat",
            "Fixes CarryOn placing players on physics sub-levels causing teleportation to wrong dimensions",
            true,
            Set.of("carryon"));
        FixRegistry.register("typewriter-server-fix",
            "Fixes Simulated mod typewriter crashing dedicated servers due to client-only class references in common code",
            true,
            Set.of("simulated"));
        FixRegistry.register("command-block-sublevel-fix",
            "Prevents command blocks (and variants) from being placed on physics sub-levels, which bypasses vanilla restrictions",
            true,
            Set.of("sable"));
        FixRegistry.register("aeronautics-server-fix",
            "Fixes Aeronautics SteamVentBlockEntity crashing dedicated servers due to client-only class references in common code",
            true,
            Set.of("aeronautics"));
        FixRegistry.register("aeronautics-slime-bearfix",
            "Fixes slime blocks sticking to bearing structures causing them to separate and clip through blocks",
            false,
            Set.of("aeronautics"));
        FixRegistry.register("physics-staff-drag-clipfix",
            "Prevents physics structures from clipping through physics blocks when dragged at high speed with the physics staff",
            true,
            Set.of("simulated"));
        FixRegistry.register("plot-holder-guard",
            "Prevents server crash when block changes occur in plot chunks without a holder (e.g. bamboo growing near unloaded physics structures)",
            true,
            Set.of("sable"));
        FixRegistry.register("sublevel-entity-guard",
            "Prevents server freeze when SubLevelInclusiveLevelEntityGetter iterates over abnormally large AABBs caused by corrupted entity section storage",
            true,
            Set.of("sable"));
        FixRegistry.register("cna-path-optimization",
            "Optimizes Create New Age network pathfinding by using HashSet/ArrayDeque instead of ArrayList/LinkedList for BFS",
            true,
            Set.of("create_new_age"));
        FixRegistry.register("cna-insert-optimization",
            "Optimizes Create New Age energy insertion by reducing redundant pathfinding and early exit on full consumers",
            true,
            Set.of("create_new_age"));
        FixRegistry.register("ctt-concurrent-fix",
            "Fixes ConcurrentModificationException when CreateThreadedTrains ticks trains on worker threads while main thread modifies passenger data",
            true,
            Set.of("createthreadedtrains"));
        FixRegistry.register("effortless-particle-fix",
            "Fixes Effortless client crash when interacting with Sable physics structures by skipping particle generation for unloaded chunks (Plot storage area coordinates)",
            true,
            Set.of("effortless", "sable"));

        // === 性能优化 - 普通（安全，无崩溃风险） ===

        FixRegistry.register("spatial-index-query",
            "Uses section-based spatial index for queryIntersecting instead of linear scan, significantly faster with many SubLevels",
            true,
            Set.of("sable"));
        FixRegistry.register("bbox-object-reuse",
            "Reuses BoundingBox3d objects in hot paths via ThreadLocal pools to reduce GC pressure from frequent allocations",
            true,
            Set.of("sable"));
        FixRegistry.register("inblock-state-cache",
            "Caches getInBlockState results when entity position hasn't changed significantly, reduces SubLevel traversal frequency",
            true,
            Set.of("sable"));
        FixRegistry.register("level-accelerator-cache",
            "Enables LevelAccelerator's Long2ObjectMap chunk cache for better hit rate during collision detection",
            true,
            Set.of("sable"));
        FixRegistry.register("collision-aabb-prefilter",
            "Uses AABB pre-filter before expensive SAT collision detection, skips SAT for blocks whose AABB doesn't intersect entity bounds",
            true,
            Set.of("sable"));
        FixRegistry.register("collision-skip-air",
            "Checks isAir() before calling getCollisionShape() in collision detection, avoids expensive VoxelShape computation for air blocks",
            true,
            Set.of("sable"));
        FixRegistry.register("fastutil-sublevel-maps",
            "Uses fastutil Object2ObjectOpenHashMap instead of java.util.HashMap for subLevelsByUUID, reduces Map.Entry allocation overhead",
            true,
            Set.of("sable"));
        FixRegistry.register("physics-traversal-merge",
            "Merges multiple SubLevel traversals in physics pipeline tick into fewer passes, reduces iteration count from substeps*4 to substeps*2",
            true,
            Set.of("sable"));
        FixRegistry.register("network-sync-batch",
            "Optimizes SubLevelTrackingSystem network sync by reusing ArrayList instances and skipping unchanged SubLevels early",
            true,
            Set.of("sable"));

        // === 性能优化 - 激进（效果显著但有行为异常风险） ===

        FixRegistry.register("dynamic-collision-substep",
            "Dynamically reduces collision substeps when server TPS is low (RISK: high-speed entities may pass through blocks)",
            false,
            Set.of("sable"));
        FixRegistry.register("skip-far-entity-collision",
            "Skips SubLevel collision detection for entities far from any player (RISK: entities may clip through SubLevels when no player is nearby)",
            false,
            Set.of("sable"));
        FixRegistry.register("collision-volume-threshold",
            "Lowers collision block volume threshold from 500^3 to 200^3, prevents lag from large rotated structures (RISK: large structures may have incomplete collision at edges)",
            false,
            Set.of("sable"));
        FixRegistry.register("dynamic-physics-substep",
            "Dynamically reduces physics substepsPerTick when TPS is low (RISK: physics precision decreases, may cause instability)",
            false,
            Set.of("sable"));
        FixRegistry.register("reduced-network-sync",
            "Reduces SubLevel network sync frequency when TPS is low (RISK: clients may see position/rotation desync)",
            false,
            Set.of("sable"));
        FixRegistry.register("skip-noncritical-collision",
            "Skips SubLevel collision for non-critical entities like items, XP orbs and arrows (RISK: these entities will pass through moving SubLevels)",
            false,
            Set.of("sable"));

        // === Create mod 性能优化 ===

        FixRegistry.register("create-kinetic-cache",
            "Caches stress/capacity calculation results in KineticNetwork, recalculates only when network is dirty instead of every tick",
            true,
            Set.of("create"));
        FixRegistry.register("create-contraption-collision-radius",
            "Reduces contraption collision entity search inflation to improve performance with large contraptions (RISK: entities far above/below contraptions may not collide properly)",
            false,
            Set.of("create"));

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

        // 7. 自动更新检查（默认关闭，需在配置中启用）
        if (config.isAutoUpdate()) {
            UpdateChecker.checkAsync();
        }

        // 8. 注册事件监听
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);

        LOGGER.info("fuck Sable v{} loaded - {} fixes registered", VERSION, FixRegistry.getAllFixes().size());
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
