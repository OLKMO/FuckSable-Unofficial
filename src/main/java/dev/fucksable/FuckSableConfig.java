package dev.fucksable;

import com.google.gson.*;
import dev.fucksable.fix.FixEntry;
import dev.fucksable.fix.FixRegistry;
import dev.fucksable.i18n.LanguageManager;

import java.io.*;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 配置持久化。保存/加载修复项启用状态、语言偏好和自动更新开关到 config/fucksable/config.json
 */
public final class FuckSableConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String language = "en";
    private boolean autoUpdate = false;
    private final Map<String, Boolean> fixes = new LinkedHashMap<>();
    private boolean existedOnDisk = false;

    private FuckSableConfig() {}

    public static FuckSableConfig load(Path configDir) {
        Path configPath = configDir.resolve("fucksable").resolve("config.json");
        FuckSableConfig config = new FuckSableConfig();
        config.existedOnDisk = Files.exists(configPath);
        if (config.existedOnDisk) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                JsonObject obj = JsonParser.parseReader(reader).getAsJsonObject();
                if (obj.has("language")) {
                    config.language = obj.get("language").getAsString();
                }
                if (obj.has("autoUpdate")) {
                    config.autoUpdate = obj.get("autoUpdate").getAsBoolean();
                }
                if (obj.has("fixes")) {
                    JsonObject fixesObj = obj.getAsJsonObject("fixes");
                    for (Map.Entry<String, JsonElement> entry : fixesObj.entrySet()) {
                        config.fixes.put(entry.getKey(), entry.getValue().getAsBoolean());
                    }
                }
            } catch (Exception e) {
                FuckSable.LOGGER.warn("Failed to load config, using defaults", e);
            }
        }
        return config;
    }

    public void save(Path configDir) {
        if (!existedOnDisk) return;
        Path configPath = configDir.resolve("fucksable").resolve("config.json");
        try {
            Files.createDirectories(configPath.getParent());
            JsonObject obj = new JsonObject();
            obj.addProperty("language", LanguageManager.getCurrentLanguage());
            obj.addProperty("autoUpdate", autoUpdate);
            JsonObject fixesObj = new JsonObject();
            for (FixEntry entry : FixRegistry.getAllFixes()) {
                fixesObj.addProperty(entry.getId(), entry.isEnabled());
            }
            obj.add("fixes", fixesObj);
            Files.writeString(configPath, GSON.toJson(obj));
        } catch (IOException e) {
            FuckSable.LOGGER.error("Failed to save config", e);
        }
    }

    public String getLanguage() { return language; }
    public boolean isAutoUpdate() { return autoUpdate; }
    public Map<String, Boolean> getFixStates() { return fixes; }
}
