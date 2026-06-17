package dev.fucksable.update;

import com.google.gson.*;
import dev.fucksable.FuckSable;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/**
 * 自动更新检查器。
 * <p>
 * 启动时访问 GitHub Releases API 获取最新版本信息，
 * 如有新版本则在日志中提示下载链接。
 */
public final class UpdateChecker {

    private static final String GITHUB_API = "https://api.github.com/repos/XSY-HYH/fuck-sable/releases/latest";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private UpdateChecker() {}

    /**
     * 异步执行更新检查。在独立线程中运行，不阻塞主线程。
     */
    public static void checkAsync() {
        Thread thread = new Thread(UpdateChecker::check, "fuckSable Update Checker");
        thread.setDaemon(true);
        thread.start();
    }

    private static void check() {
        try {
            FuckSable.LOGGER.info("Checking for updates via GitHub...");

            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_API))
                .timeout(TIMEOUT)
                .header("Accept", "application/vnd.github+json")
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                FuckSable.LOGGER.warn("Update check failed: HTTP {}", response.statusCode());
                return;
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String tagName = json.get("tag_name").getAsString();
            // tag_name 可能是 "v1.6.3" 或 "1.6.3"
            String remoteVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
            String htmlUrl = json.get("html_url").getAsString();

            if (compareVersions(remoteVersion, FuckSable.VERSION) <= 0) {
                FuckSable.LOGGER.info("Already up to date (v{})", FuckSable.VERSION);
                return;
            }

            FuckSable.LOGGER.info("");
            FuckSable.LOGGER.info("  ========================================");
            FuckSable.LOGGER.info("  |  New version available: v{} (current: v{})", remoteVersion, FuckSable.VERSION);
            FuckSable.LOGGER.info("  |  Download: {}", htmlUrl);
            FuckSable.LOGGER.info("  ========================================");
            FuckSable.LOGGER.info("");

        } catch (Exception e) {
            FuckSable.LOGGER.warn("Update check failed: {}", e.getMessage());
        }
    }

    /**
     * 比较语义化版本号。返回正数表示 a > b，负数表示 a < b，0 表示相等。
     */
    private static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int na = i < pa.length ? Integer.parseInt(pa[i]) : 0;
            int nb = i < pb.length ? Integer.parseInt(pb[i]) : 0;
            if (na != nb) return Integer.compare(na, nb);
        }
        return 0;
    }
}
