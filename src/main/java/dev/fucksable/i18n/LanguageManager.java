package dev.fucksable.i18n;

import dev.fucksable.FuckSable;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 语言管理器。
 * <p>
 * 初始化时从JAR内嵌语言包解压到 config/fucksable/lang/，
 * 检查版本一致性，不匹配则替换。运行时支持切换语言，回退到en。
 */
public final class LanguageManager {

    private static final String LANG_DIR_NAME = "lang";
    private static final String CONFIG_DIR_NAME = "fucksable";

    private static String currentLang = "en";
    private static Map<String, String> translations = new LinkedHashMap<>();
    private static Map<String, String> fallbackTranslations = new LinkedHashMap<>();
    private static Path langPath;
    private static final Set<String> availableLanguages = new LinkedHashSet<>();

    // 缓存所有已加载的语言包，用于 getRaw 查询
    private static final Map<String, Map<String, String>> loadedPacks = new LinkedHashMap<>();

    private LanguageManager() {}

    public static void init(Path configDir) {
        langPath = configDir.resolve(CONFIG_DIR_NAME).resolve(LANG_DIR_NAME);

        try {
            Files.createDirectories(langPath);
        } catch (IOException e) {
            FuckSable.LOGGER.error("Failed to create language directory", e);
        }

        // 解压并校验内嵌语言包
        extractAndVerify("en");
        extractAndVerify("zh");

        // 扫描额外语言文件
        scanLanguageFiles();

        // 预加载所有语言包（供FixRegistry注册时校验）
        for (String lang : availableLanguages) {
            loadedPacks.put(lang, loadLanguageFile(lang));
        }

        // 加载回退语言(en)
        fallbackTranslations = loadedPacks.getOrDefault("en", new LinkedHashMap<>());

        // 加载当前语言
        loadCurrentLanguage();

        FuckSable.LOGGER.info("Language manager initialized. Current: {}, Available: {}", currentLang, availableLanguages);
    }

    private static void extractAndVerify(String lang) {
        String resourcePath = "/fucksable/lang/" + lang + ".yml";
        Path targetPath = langPath.resolve(lang + ".yml");

        try (InputStream embedded = LanguageManager.class.getResourceAsStream(resourcePath)) {
            if (embedded == null) {
                FuckSable.LOGGER.warn("No embedded language pack found for: {}", lang);
                return;
            }

            String embeddedContent = new String(embedded.readAllBytes());

            if (Files.exists(targetPath)) {
                String existingContent = Files.readString(targetPath);
                if (contentMatches(embeddedContent, existingContent)) {
                    return;
                }
                FuckSable.LOGGER.info("Language pack {} is outdated, replacing with embedded version", lang);
            }

            Files.writeString(targetPath, embeddedContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            FuckSable.LOGGER.info("Extracted language pack: {}", lang);

        } catch (IOException e) {
            FuckSable.LOGGER.error("Failed to extract language pack: {}", lang, e);
        }
    }

    private static boolean contentMatches(String embedded, String existing) {
        // 内容完全一致则跳过，否则替换（确保嵌入版本始终覆盖本地）
        return embedded.trim().equals(existing.trim());
    }

    static String extractVersion(String content) {
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("_version:")) {
                String value = trimmed.substring("_version:".length()).trim();
                if ((value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }

    private static void scanLanguageFiles() {
        availableLanguages.clear();
        if (!Files.isDirectory(langPath)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(langPath, "*.yml")) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                availableLanguages.add(name.substring(0, name.length() - 4));
            }
        } catch (IOException e) {
            FuckSable.LOGGER.error("Failed to scan language files", e);
        }
    }

    private static Map<String, String> loadLanguageFile(String lang) {
        Path file = langPath.resolve(lang + ".yml");
        if (!Files.exists(file)) return new LinkedHashMap<>();
        try (Reader reader = Files.newBufferedReader(file)) {
            Map<String, Object> nested = SimpleYamlReader.read(reader);
            return SimpleYamlReader.flatten(nested);
        } catch (IOException e) {
            FuckSable.LOGGER.error("Failed to load language file: {}", lang, e);
            return new LinkedHashMap<>();
        }
    }

    private static void loadCurrentLanguage() {
        translations = loadedPacks.getOrDefault(currentLang, new LinkedHashMap<>());
    }

    /**
     * 获取翻译文本，当前语言 -> en回退 -> 返回key本身
     */
    public static String get(String key) {
        String value = translations.get(key);
        if (value != null) return value;
        value = fallbackTranslations.get(key);
        return value != null ? value : key;
    }

    /**
     * 带格式化参数的翻译
     */
    public static String get(String key, Object... args) {
        return String.format(get(key), args);
    }

    /**
     * 直接获取指定语言包的原始值，不回退。用于FixRegistry注册时校验描述是否存在。
     */
    public static String getRaw(String lang, String key) {
        Map<String, String> pack = loadedPacks.get(lang);
        return pack != null ? pack.get(key) : null;
    }

    public static void setLanguage(String lang) {
        if (!availableLanguages.contains(lang)) {
            FuckSable.LOGGER.warn("Language not available: {}", lang);
            return;
        }
        currentLang = lang;
        loadCurrentLanguage();
    }

    public static String getCurrentLanguage() { return currentLang; }
    public static Set<String> getAvailableLanguages() { return Collections.unmodifiableSet(availableLanguages); }
    public static Path getLangPath() { return langPath; }
}
