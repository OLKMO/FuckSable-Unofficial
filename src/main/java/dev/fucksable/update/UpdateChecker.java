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
 * <p>
 * 同时查询两个源仓库（OLKMO/FuckSable-Unofficial 和 XSY-Team/fuck-sable），
 * 取版本号更高的作为更新提示。
 * <p>
 * Queries both source repos (OLKMO/FuckSable-Unofficial and XSY-Team/fuck-sable)
 * and uses the higher version as the update notification.
 */
public final class UpdateChecker {

    // 同时查询个人仓库和开源仓库，取版本更高的作为更新提示
    // Query both the personal repo and the open-source repo; use the higher version as the update notification
    private static final String[] GITHUB_API_URLS = {
        "https://api.github.com/repos/OLKMO/FuckSable-Unofficial/releases/latest",
        "https://api.github.com/repos/XSY-Team/fuck-sable/releases/latest"
    };
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private UpdateChecker() {}

    /**
     * 异步执行更新检查。在独立线程中运行，不阻塞主线程。
     * <p>
     * Async update check. Runs in a daemon thread to avoid blocking the main thread.
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

            String bestRemoteVersion = null;
            String bestHtmlUrl = null;

            for (String apiUrl : GITHUB_API_URLS) {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(apiUrl))
                        .timeout(TIMEOUT)
                        .header("Accept", "application/vnd.github+json")
                        .GET()
                        .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() != 200) {
                        continue;
                    }

                    JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                    String tagName = json.get("tag_name").getAsString();
                    // tag_name 可能是 "v1.6.3" 或 "1.6.3"
                    // tag_name may be "v1.6.3" or "1.6.3"
                    String remoteVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                    String htmlUrl = json.get("html_url").getAsString();

                    if (bestRemoteVersion == null || compareVersions(remoteVersion, bestRemoteVersion) > 0) {
                        bestRemoteVersion = remoteVersion;
                        bestHtmlUrl = htmlUrl;
                    }
                } catch (Exception ignored) {
                    // 单个仓库查询失败不影响其他仓库
                    // Failure of one repo query does not affect the others
                }
            }

            if (bestRemoteVersion == null) {
                FuckSable.LOGGER.warn("Update check failed: no release found in any source repo");
                return;
            }

            if (compareVersions(bestRemoteVersion, FuckSable.VERSION) <= 0) {
                FuckSable.LOGGER.info("Already up to date (v{})", FuckSable.VERSION);
                return;
            }

            FuckSable.LOGGER.info("");
            FuckSable.LOGGER.info("  ========================================");
            FuckSable.LOGGER.info("  |  New version available: v{} (current: v{})", bestRemoteVersion, FuckSable.VERSION);
            FuckSable.LOGGER.info("  |  Download: {}", bestHtmlUrl);
            FuckSable.LOGGER.info("  ========================================");
            FuckSable.LOGGER.info("");

        } catch (Exception e) {
            FuckSable.LOGGER.warn("Update check failed: {}", e.getMessage());
        }
    }

    /**
     * 比较语义化版本号。返回正数表示 a > b，负数表示 a < b，0 表示相等。
     * <p>
     * Compare semantic versions. Returns positive if a > b, negative if a < b, 0 if equal.
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
