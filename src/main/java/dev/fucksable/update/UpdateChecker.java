package dev.fucksable.update;

import com.google.gson.*;
import dev.fucksable.FuckSable;

import javax.net.ssl.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自动更新检查器。
 * <p>
 * 启动时访问远程 API 获取最新版本信息，比较版本号和哈希，
 * 如有新版本则下载并替换当前 mod jar（不触发重启，下次启动生效）。
 * 默认关闭，需在 config.json 中设置 autoUpdate: true 启用。
 */
public final class UpdateChecker {

    private static final String API_URL = "https://110.42.98.47:59113/api/files?path=fucksable";
    private static final Pattern VERSION_PATTERN = Pattern.compile("fucksable-(\\d+\\.\\d+\\.\\d+)\\.jar");
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
            FuckSable.LOGGER.info("Checking for updates...");

            HttpClient client = createInsecureClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(TIMEOUT)
                .GET()
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                FuckSable.LOGGER.warn("Update check failed: HTTP {}", response.statusCode());
                return;
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonArray files = json.getAsJsonArray("files");
            if (files == null || files.isEmpty()) {
                FuckSable.LOGGER.info("No files found on remote");
                return;
            }

            // 找到最新的 fucksable jar
            JsonObject latestFile = null;
            String latestVersion = null;
            for (JsonElement elem : files) {
                JsonObject file = elem.getAsJsonObject();
                String name = file.get("name").getAsString();
                Matcher matcher = VERSION_PATTERN.matcher(name);
                if (matcher.find()) {
                    String version = matcher.group(1);
                    if (latestVersion == null || compareVersions(version, latestVersion) > 0) {
                        latestVersion = version;
                        latestFile = file;
                    }
                }
            }

            if (latestFile == null) {
                FuckSable.LOGGER.info("No fucksable jar found on remote");
                return;
            }

            String remoteVersion = latestVersion;
            String remoteSha256 = latestFile.get("sha256").getAsString();
            String remoteUrl = latestFile.get("url").getAsString();
            long remoteSize = latestFile.get("size").getAsLong();

            if (compareVersions(remoteVersion, FuckSable.VERSION) <= 0) {
                FuckSable.LOGGER.info("Already up to date (v{})", FuckSable.VERSION);
                return;
            }

            FuckSable.LOGGER.info("New version available: v{} (current: v{})", remoteVersion, FuckSable.VERSION);

            // 下载新版本（API格式：/api/download/<url路径>?_t=时间戳）
            String downloadUrl = "https://110.42.98.47:59113/api/download/" + remoteUrl.replace("/", "%2F") + "?_t=" + System.currentTimeMillis();
            Path modJar = findCurrentJar();
            if (modJar == null) {
                FuckSable.LOGGER.warn("Cannot locate current mod jar, skipping update");
                return;
            }

            Path updateDir = modJar.getParent().resolve("fucksable-update");
            Files.createDirectories(updateDir);
            Path newJar = updateDir.resolve(latestFile.get("name").getAsString());

            FuckSable.LOGGER.info("Downloading v{}...", remoteVersion);
            HttpRequest downloadRequest = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

            Path tempJar = newJar.resolveSibling(newJar.getFileName() + ".tmp");
            try (InputStream is = client.send(downloadRequest, HttpResponse.BodyHandlers.ofInputStream()).body()) {
                Files.copy(is, tempJar, StandardCopyOption.REPLACE_EXISTING);
            }

            // 校验哈希
            String actualHash = sha256(tempJar);
            if (!actualHash.equalsIgnoreCase(remoteSha256)) {
                FuckSable.LOGGER.error("SHA256 mismatch! Expected: {}, Got: {}. Update aborted.", remoteSha256, actualHash);
                Files.deleteIfExists(tempJar);
                return;
            }

            // 重命名临时文件为正式文件
            Files.move(tempJar, newJar, StandardCopyOption.REPLACE_EXISTING);

            // 替换旧 jar（下次启动生效）
            Path backupJar = modJar.resolveSibling(modJar.getFileName() + ".bak");
            Files.deleteIfExists(backupJar);
            Files.move(modJar, backupJar, StandardCopyOption.REPLACE_EXISTING);
            Files.move(newJar, modJar, StandardCopyOption.REPLACE_EXISTING);

            FuckSable.LOGGER.info("Update to v{} complete! Restart server to apply.", remoteVersion);

        } catch (Exception e) {
            FuckSable.LOGGER.warn("Update check failed: {}", e.getMessage());
        }
    }

    /**
     * 创建禁用 SSL 验证的 HttpClient（包括证书验证和主机名验证）
     */
    private static HttpClient createInsecureClient() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
            }
        };

        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, trustAll, new java.security.SecureRandom());

        return HttpClient.newBuilder()
            .sslContext(sc)
            .sslParameters(new SSLParameters() {{
                setEndpointIdentificationAlgorithm("");
            }})
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    /**
     * 查找当前 mod jar 文件路径
     */
    private static Path findCurrentJar() {
        try {
            String jarPath = UpdateChecker.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI().getPath();
            // Windows 路径可能以 / 开头
            if (jarPath.startsWith("/") && jarPath.length() > 2 && jarPath.charAt(2) == ':') {
                jarPath = jarPath.substring(1);
            }
            Path path = Path.of(jarPath);
            return Files.isRegularFile(path) ? path : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 计算文件 SHA-256
     */
    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
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
