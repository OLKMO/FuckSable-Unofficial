package dev.fucksable.fix;

import dev.fucksable.FuckSable;

import java.util.*;

/**
 * 修复项注册中心。
 * <p>
 * 提供内部API（FuckSable自身使用）和开放插件API（外部mod注册修复项）。
 * 描述直接在注册时传入，不依赖翻译包。
 * 支持环境条件：注册时可指定requiredMods，若前置mod缺失则修复项全局禁用且无法开启。
 */
public final class FixRegistry {

    private static final Map<String, FixEntry> fixes = new LinkedHashMap<>();

    private FixRegistry() {}

    // --- 内部API ---

    /**
     * 注册一个内置修复项。内部API，由FuckSable自身在初始化时调用。
     *
     * @param id             修复项唯一标识
     * @param description    修复项描述（直接写在注册代码中）
     * @param defaultEnabled 默认是否启用
     * @return 注册的 FixEntry 实例
     */
    public static FixEntry register(String id, String description, boolean defaultEnabled) {
        return register(id, description, defaultEnabled, null);
    }

    /**
     * 注册一个内置修复项，带环境条件。
     *
     * @param id             修复项唯一标识
     * @param description    修复项描述
     * @param defaultEnabled 默认是否启用
     * @param requiredMods   启用该修复项所需的前置mod ID集合，null或空表示无要求
     * @return 注册的 FixEntry 实例
     */
    public static FixEntry register(String id, String description, boolean defaultEnabled, Set<String> requiredMods) {
        if (fixes.containsKey(id)) {
            throw new IllegalArgumentException("Fix already registered: " + id);
        }
        if (description == null || description.isBlank()) {
            throw new IllegalStateException("Fix '" + id + "' must have a non-empty description");
        }
        FixEntry entry = new FixEntry(id, description, defaultEnabled, requiredMods);
        fixes.put(id, entry);
        FuckSable.LOGGER.debug("Registered fix: {} (default: {}, requiredMods: {})", id, defaultEnabled, requiredMods);
        return entry;
    }

    // --- 开放插件API ---

    /**
     * 外部插件注册修复项的入口。
     *
     * @param id             修复项唯一标识，建议使用 namespace:name 格式避免冲突
     * @param description    修复项描述
     * @param defaultEnabled 默认是否启用
     * @return 注册的 FixEntry 实例
     */
    public static FixEntry registerFix(String id, String description, boolean defaultEnabled) {
        return register(id, description, defaultEnabled);
    }

    /**
     * 外部插件注册修复项的入口，带环境条件。
     *
     * @param id             修复项唯一标识
     * @param description    修复项描述
     * @param defaultEnabled 默认是否启用
     * @param requiredMods   启用该修复项所需的前置mod ID集合
     * @return 注册的 FixEntry 实例
     */
    public static FixEntry registerFix(String id, String description, boolean defaultEnabled, Set<String> requiredMods) {
        return register(id, description, defaultEnabled, requiredMods);
    }

    // --- 环境条件检测 ---

    /**
     * 检测所有修复项的环境条件，更新environmentMet状态。
     * 在mod加载完成后调用，通过NeoForge的ModList检测mod是否加载。
     *
     * @param modChecker 检查mod是否加载的函数
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
