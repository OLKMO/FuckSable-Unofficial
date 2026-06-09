package dev.fucksable.fix;

import dev.fucksable.FuckSable;

import java.util.*;

/**
 * 修复项注册中心。
 * <p>
 * 提供内部API（FuckSable自身使用）和开放插件API（外部mod注册修复项）。
 * 描述直接在注册时传入，不依赖翻译包。
 * 支持环境条件：注册时可指定requiredMods，若前置mod缺失则修复项全局禁用且无法开启。
 * 支持端侧分离：注册时可指定Side，修复项只在对应端生效。
 */
public final class FixRegistry {

    private static final Map<String, FixEntry> fixes = new LinkedHashMap<>();

    private FixRegistry() {}

    // --- 内部API ---

    /**
     * 注册一个内置修复项（双端生效）。
     */
    public static FixEntry register(String id, String description, boolean defaultEnabled) {
        return register(id, description, defaultEnabled, null, FixEntry.Side.BOTH);
    }

    /**
     * 注册一个内置修复项，带环境条件（双端生效）。
     */
    public static FixEntry register(String id, String description, boolean defaultEnabled, Set<String> requiredMods) {
        return register(id, description, defaultEnabled, requiredMods, FixEntry.Side.BOTH);
    }

    /**
     * 注册一个内置修复项，带端侧指定（无环境条件）。
     */
    public static FixEntry register(String id, String description, boolean defaultEnabled, FixEntry.Side side) {
        return register(id, description, defaultEnabled, null, side);
    }

    /**
     * 注册一个内置修复项，带环境条件和端侧指定。
     */
    public static FixEntry register(String id, String description, boolean defaultEnabled, Set<String> requiredMods, FixEntry.Side side) {
        if (fixes.containsKey(id)) {
            throw new IllegalArgumentException("Fix already registered: " + id);
        }
        if (description == null || description.isBlank()) {
            throw new IllegalStateException("Fix '" + id + "' must have a non-empty description");
        }
        FixEntry entry = new FixEntry(id, description, defaultEnabled, requiredMods, side);
        fixes.put(id, entry);
        FuckSable.LOGGER.debug("Registered fix: {} (default: {}, side: {}, requiredMods: {})", id, defaultEnabled, side, requiredMods);
        return entry;
    }

    // --- 开放插件API ---

    /**
     * 外部插件注册修复项的入口。
     */
    public static FixEntry registerFix(String id, String description, boolean defaultEnabled) {
        return register(id, description, defaultEnabled);
    }

    /**
     * 外部插件注册修复项的入口，带环境条件。
     */
    public static FixEntry registerFix(String id, String description, boolean defaultEnabled, Set<String> requiredMods) {
        return register(id, description, defaultEnabled, requiredMods);
    }

    // --- 环境条件检测 ---

    /**
     * 检测所有修复项的环境条件，更新environmentMet状态。
     */
    public static void checkEnvironment(java.util.function.Function<String, Boolean> modChecker) {
        for (FixEntry entry : fixes.values()) {
            if (entry.getRequiredMods().isEmpty()) {
                entry.setEnvironmentMet(true);
                continue;
            }
            boolean met = true;
            for (String modId : entry.getRequiredMods()) {
                if (!modChecker.apply(modId)) {
                    met = false;
                    FuckSable.LOGGER.info("Fix '{}' requires mod '{}' which is not loaded, fix disabled", entry.getId(), modId);
                    break;
                }
            }
            entry.setEnvironmentMet(met);
        }
    }

    // --- 查询API ---

    public static boolean isEnabled(String id) {
        FixEntry entry = fixes.get(id);
        return entry != null && entry.isEnabled();
    }

    public static void setEnabled(String id, boolean enabled) {
        FixEntry entry = fixes.get(id);
        if (entry != null) {
            entry.setEnabled(enabled);
        }
    }

    public static FixEntry getFix(String id) { return fixes.get(id); }

    public static Collection<FixEntry> getAllFixes() {
        return Collections.unmodifiableCollection(fixes.values());
    }

    public static String getDescription(String id) {
        FixEntry entry = fixes.get(id);
        return entry != null ? entry.getDescription() : id;
    }
}
