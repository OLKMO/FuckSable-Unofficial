package dev.fucksable.fix;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 单个修复项的注册条目。
 * <p>
 * 每个修复项有唯一ID、描述、默认启用状态、运行时启用状态、环境条件和自定义选项。
 * 描述直接在注册时传入，不依赖翻译包。
 * 环境条件指定了启用该修复项所需的前置mod，若前置mod缺失则全局禁用且无法开启。
 */
public class FixEntry {

    private final String id;
    private final String description;
    private final boolean defaultEnabled;
    private final Set<String> requiredMods;
    private boolean environmentMet;
    private boolean enabled;
    private final Map<String, Object> options;

    FixEntry(String id, String description, boolean defaultEnabled, Set<String> requiredMods) {
        this.id = id;
        this.description = description;
        this.defaultEnabled = defaultEnabled;
        this.requiredMods = requiredMods != null ? Set.copyOf(requiredMods) : Set.of();
        this.environmentMet = true;
        this.enabled = defaultEnabled;
        this.options = new LinkedHashMap<>();
    }

    public String getId() { return id; }
    public String getDescription() { return description; }
    public boolean isDefaultEnabled() { return defaultEnabled; }
    public Set<String> getRequiredMods() { return requiredMods; }
    public boolean isEnvironmentMet() { return environmentMet; }

    /**
     * 设置环境条件是否满足。由FixRegistry在mod加载检测后调用。
     */
    void setEnvironmentMet(boolean met) { this.environmentMet = met; }

    /**
     * 修复项是否实际启用：需要运行时启用状态为true且环境条件满足。
     */
    public boolean isEnabled() { return enabled && environmentMet; }

    /**
     * 运行时启用状态（不考虑环境条件）。
     */
    public boolean isExplicitlyEnabled() { return enabled; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, Object> getOptions() { return Collections.unmodifiableMap(options); }
    public void setOption(String key, Object value) { options.put(key, value); }
    public Object getOption(String key) { return options.get(key); }
    @SuppressWarnings("unchecked")
    public <T> T getOption(String key, T defaultValue) {
        Object v = options.get(key);
        if (v == null) return defaultValue;
        try { return (T) v; } catch (ClassCastException e) { return defaultValue; }
    }
}
