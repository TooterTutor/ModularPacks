package io.github.tootertutor.ModularPacks.update;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.resource.ResourcePackDefinition;

/**
 * Downloads and verifies configured GitHub release assets for client delivery.
 */
public final class PackCheckerService {

    public record AvailablePack(ResourcePackDefinition definition, String sha1, Path file, URI publicUri) {
    }

    private final ModularPacksPlugin plugin;
    private final HttpClient httpClient;
    private final Map<String, AvailablePack> available = new ConcurrentHashMap<>();
    private final AtomicBoolean checking = new AtomicBoolean();
    private final BiConsumer<AvailablePack, AvailablePack> updateListener;

    private BukkitTask periodicTask;
    private Path packRoot;
    private Path stateFile;
    private YamlConfiguration state;

    public PackCheckerService(ModularPacksPlugin plugin, BiConsumer<AvailablePack, AvailablePack> updateListener) {
        this.plugin = plugin;
        this.updateListener = updateListener;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void start() {
        stop();
        if (!plugin.cfg().resourcePackEnabled()) {
            return;
        }
        packRoot = plugin.getDataFolder().toPath().resolve("resource-packs");
        stateFile = plugin.getDataFolder().toPath().resolve("resource-pack-state.yml");
        state = YamlConfiguration.loadConfiguration(stateFile.toFile());
        try {
            Files.createDirectories(packRoot);
            loadCachedPacks();
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not initialize resource-pack cache: " + ex.getMessage());
            return;
        }
        if (!plugin.cfg().resourcePackUpdatesEnabled()) {
            return;
        }
        if (plugin.cfg().resourcePackCheckOnStartup()) {
            checkNowAsync();
        }
        if (plugin.cfg().resourcePackPeriodicCheck()) {
            long ticks = plugin.cfg().resourcePackCheckIntervalHours() * 60L * 60L * 20L;
            periodicTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::checkNow,
                    Math.max(20L, ticks), Math.max(20L, ticks));
        }
    }

    public void stop() {
        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }
        checking.set(false);
        available.clear();
    }

    public AvailablePack availablePack(String id) {
        return id == null ? null : available.get(id.toLowerCase(Locale.ROOT));
    }

    private void checkNowAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::checkNow);
    }

    private void checkNow() {
        if (!checking.compareAndSet(false, true)) {
            return;
        }
        try {
            String json = fetchText(plugin.cfg().resourcePackReleaseApiUrl(), "GitHub release API");
            List<RemoteAsset> assets = parseAssets(json);
            for (ResourcePackDefinition definition : plugin.cfg().resourcePacks()) {
                RemoteAsset asset = assets.stream()
                        .filter(candidate -> candidate.name().equals(definition.assetName()))
                        .findFirst().orElse(null);
                if (asset == null) {
                    plugin.getLogger().warning("Resource-pack asset not found in latest release: "
                            + definition.assetName());
                    continue;
                }
                try {
                    updateFromAsset(definition, asset);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Could not update resource pack " + definition.id() + ": "
                            + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Resource-pack update check failed: " + ex.getMessage());
        } finally {
            checking.set(false);
        }
    }

    private void updateFromAsset(ResourcePackDefinition definition, RemoteAsset asset) throws Exception {
        String stateRoot = "Packs." + definition.id();
        long knownAssetId = state.getLong(stateRoot + ".AssetId", -1L);
        AvailablePack current = availablePack(definition.id());
        if (knownAssetId == asset.id() && current != null && Files.isRegularFile(current.file())) {
            return;
        }

        Path directory = packRoot.resolve(definition.id());
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".download-", ".zip");
        try {
            download(asset.url(), temporary);
            validatePackZip(temporary);
            String sha1 = sha1(temporary);
            Path destination = directory.resolve(sha1 + ".zip");
            if (Files.notExists(destination)) {
                try {
                    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            AvailablePack replacement = availablePack(definition, sha1, destination, asset.url());
            AvailablePack previous = available.put(definition.id(), replacement);
            state.set(stateRoot + ".AssetId", asset.id());
            state.set(stateRoot + ".Sha1", sha1);
            state.set(stateRoot + ".DownloadUrl", asset.url());
            state.save(stateFile.toFile());
            if (previous == null || !previous.sha1().equals(sha1)) {
                plugin.getLogger().info("Cached resource pack " + definition.id() + " (" + sha1 + ")");
                notifyUpdate(previous, replacement);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void notifyUpdate(AvailablePack previous, AvailablePack replacement) {
        if (updateListener != null && plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, () -> updateListener.accept(previous, replacement));
        }
    }

    private void loadCachedPacks() throws IOException {
        for (ResourcePackDefinition definition : plugin.cfg().resourcePacks()) {
            Path directory = packRoot.resolve(definition.id());
            if (!Files.isDirectory(directory)) {
                continue;
            }
            String preferredHash = state.getString("Packs." + definition.id() + ".Sha1");
            String downloadUrl = state.getString("Packs." + definition.id() + ".DownloadUrl");
            Path selected = preferredHash == null ? null : directory.resolve(preferredHash + ".zip");
            if (selected != null && Files.isRegularFile(selected)
                    && downloadUrl != null && !downloadUrl.isBlank()) {
                available.put(definition.id(), availablePack(definition, preferredHash, selected, downloadUrl));
            }
        }
    }

    private AvailablePack availablePack(ResourcePackDefinition definition, String hash, Path file,
            String downloadUrl) {
        return new AvailablePack(definition, hash, file, URI.create(downloadUrl));
    }

    private String fetchText(String url, String label) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ModularPacks-ResourcePackChecker")
                .timeout(Duration.ofSeconds(15)).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " from " + label);
        }
        return response.body();
    }

    private void download(String url, Path destination) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/octet-stream")
                .header("User-Agent", "ModularPacks-ResourcePackChecker")
                .timeout(Duration.ofMinutes(2)).GET().build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            response.body().close();
            throw new IOException("HTTP " + response.statusCode() + " downloading " + url);
        }
        try (InputStream body = response.body()) {
            Files.copy(body, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void validatePackZip(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            ZipEntry metadata = zip.getEntry("pack.mcmeta");
            if (metadata == null || metadata.isDirectory()) {
                throw new IOException("ZIP does not contain pack.mcmeta at its root");
            }
        }
    }

    private static String sha1(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-1 is unavailable", ex);
        }
    }

    private static List<RemoteAsset> parseAssets(String json) {
        String assetsArray = extractArray(json, "assets");
        List<RemoteAsset> assets = new ArrayList<>();
        for (String object : splitTopLevelObjects(assetsArray)) {
            String name = extractJsonString(object, "name");
            String url = extractJsonString(object, "browser_download_url");
            long id = extractJsonLong(object, "id", -1L);
            if (name != null && url != null && id >= 0L) {
                assets.add(new RemoteAsset(id, name, url));
            }
        }
        return assets;
    }

    private static String extractArray(String json, String key) {
        if (json == null) {
            return "";
        }
        int keyIndex = json.indexOf("\"" + key + "\"");
        int start = keyIndex < 0 ? -1 : json.indexOf('[', keyIndex);
        if (start < 0) {
            return "";
        }
        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (c == '\\') {
                    escaping = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '[') {
                depth++;
            } else if (c == ']' && --depth == 0) {
                return json.substring(start, i + 1);
            }
        }
        return "";
    }

    private static List<String> splitTopLevelObjects(String jsonArray) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaping = false;
        for (int i = 0; i < jsonArray.length(); i++) {
            char c = jsonArray.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (c == '\\') {
                    escaping = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                if (depth++ == 0) {
                    start = i;
                }
            } else if (c == '}' && --depth == 0 && start >= 0) {
                objects.add(jsonArray.substring(start, i + 1));
                start = -1;
            }
        }
        return objects;
    }

    private static String extractJsonString(String json, String key) {
        int keyIndex = json.indexOf("\"" + key + "\"");
        int colon = keyIndex < 0 ? -1 : json.indexOf(':', keyIndex);
        int quote = colon < 0 ? -1 : json.indexOf('"', colon);
        if (quote < 0) {
            return null;
        }
        StringBuilder value = new StringBuilder();
        boolean escaping = false;
        for (int i = quote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaping) {
                value.append(c == 'n' ? '\n' : c);
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else if (c == '"') {
                return value.toString();
            } else {
                value.append(c);
            }
        }
        return null;
    }

    private static long extractJsonLong(String json, String key, long fallback) {
        int keyIndex = json.indexOf("\"" + key + "\"");
        int colon = keyIndex < 0 ? -1 : json.indexOf(':', keyIndex);
        if (colon < 0) {
            return fallback;
        }
        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private record RemoteAsset(long id, String name, String url) {
    }
}
