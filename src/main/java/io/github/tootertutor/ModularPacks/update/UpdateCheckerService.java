package io.github.tootertutor.ModularPacks.update;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;

public final class UpdateCheckerService implements Listener {

    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final int RELEASE_FETCH_LIMIT = 30;

    private final ModularPacksPlugin plugin;
    private final HttpClient httpClient;

    private BukkitTask periodicTask;
    private final AtomicReference<UpdateInfo> latestKnownUpdate = new AtomicReference<>();
    private volatile String announcedVersion;

    public UpdateCheckerService(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
    }

    public void start() {
        stop();

        if (!plugin.cfg().updateCheckerEnabled()) {
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);

        if (plugin.cfg().updateCheckerCheckOnStartup()) {
            checkNowAsync(true);
        }

        if (plugin.cfg().updateCheckerPeriodicCheck()) {
            long intervalTicks = Math.max(20L, plugin.cfg().updateCheckerIntervalHours() * 60L * 60L * 20L);
            periodicTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                    plugin,
                    () -> checkNow(false),
                    intervalTicks,
                    intervalTicks);
        }
    }

    public void stop() {
        if (periodicTask != null) {
            periodicTask.cancel();
            periodicTask = null;
        }
        HandlerList.unregisterAll(this);
        latestKnownUpdate.set(null);
        announcedVersion = null;
    }

    private void checkNowAsync(boolean startup) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> checkNow(startup));
    }

    private void checkNow(boolean startup) {
        String apiUrl = plugin.cfg().updateCheckerReleaseApiUrl();
        String currentVersion = currentVersion();

        try {
            UpdateInfo latest = fetchReleaseChangesSinceCurrent(apiUrl, currentVersion);
            if (latest == null || latest.tagName == null || latest.tagName.isBlank()) {
                return;
            }

            boolean newer = isNewerVersion(latest.tagName, currentVersion);
            if (!newer) {
                return;
            }

            latestKnownUpdate.set(latest);

            // Avoid repeating the same announcement every periodic check.
            if (Objects.equals(announcedVersion, latest.tagName)) {
                return;
            }
            announcedVersion = latest.tagName;

            logUpdateFound(currentVersion, latest, startup);
            notifyOnlinePlayers(latest);
        } catch (Exception ex) {
            plugin.getLogger().warning("Update check failed: " + ex.getMessage());
        }
    }

    private void logUpdateFound(String currentVersion, UpdateInfo latest, boolean startup) {
        String startupSuffix = startup ? " (startup)" : "";
        plugin.getLogger().warning("A new ModularPacks version is available" + startupSuffix + ": "
                + currentVersion + " -> " + latest.tagName);

        if (latest.url != null && !latest.url.isBlank()) {
            plugin.getLogger().warning("Download: " + latest.url);
        }

        if (plugin.cfg().updateCheckerShowChangeLog() && latest.body != null && !latest.body.isBlank()) {
            plugin.getLogger().warning("Changelog:\n" + latest.body);
        }
    }

    private void notifyOnlinePlayers(UpdateInfo latest) {
        String permission = plugin.cfg().updateCheckerNotifyPermission();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.hasPermission(permission)) {
                continue;
            }
            sendUpdateNotification(player, latest);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UpdateInfo latest = latestKnownUpdate.get();
        if (latest == null) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission(plugin.cfg().updateCheckerNotifyPermission())) {
            return;
        }

        sendUpdateNotification(player, latest);
    }

    private String currentVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    private void sendUpdateNotification(Player player, UpdateInfo latest) {
        player.sendMessage(Component.text("[ModularPacks] New update available: "
                + currentVersion() + " -> " + latest.tagName, NamedTextColor.YELLOW));

        if (latest.url == null || latest.url.isBlank()) {
            return;
        }

        Component link = Component.text("[ModularPacks] " + latest.url, NamedTextColor.AQUA)
                .clickEvent(ClickEvent.openUrl(latest.url));

        if (plugin.cfg().updateCheckerShowChangeLog() && latest.body != null && !latest.body.isBlank()) {
            link = link.hoverEvent(HoverEvent.showText(changelogHover(latest.body)));
        }

        player.sendMessage(link);
    }

    private static final int HOVER_LINES_PER_SECTION = 3;

    private static Component changelogHover(String changelogBody) {
        if (changelogBody == null || changelogBody.isBlank()) {
            return Component.text("No changelog available.", NamedTextColor.GRAY);
        }

        // Split into sections by version header lines (e.g. "v2.4.9:")
        Component hover = Component.text("Changelog", NamedTextColor.GOLD);
        String[] rawLines = changelogBody.split("\r?\n", -1);

        String currentSection = null;
        List<String> sectionLines = new ArrayList<>();

        for (String raw : rawLines) {
            String line = raw.stripTrailing();
            boolean isHeader = !line.isBlank() && line.endsWith(":");

            if (isHeader) {
                if (currentSection != null) {
                    hover = appendSection(hover, currentSection, sectionLines);
                }
                currentSection = line;
                sectionLines = new ArrayList<>();
            } else if (currentSection != null) {
                if (!line.isBlank()) {
                    sectionLines.add(line);
                }
            }
        }
        if (currentSection != null) {
            hover = appendSection(hover, currentSection, sectionLines);
        }

        return hover;
    }

    private static Component appendSection(Component hover, String header, List<String> lines) {
        hover = hover.append(Component.newline())
                .append(Component.text(header, NamedTextColor.GOLD));
        int shown = Math.min(lines.size(), HOVER_LINES_PER_SECTION);
        for (int i = 0; i < shown; i++) {
            hover = hover.append(Component.newline())
                    .append(Component.text("  " + lines.get(i), NamedTextColor.GRAY));
        }
        if (lines.size() > HOVER_LINES_PER_SECTION) {
            hover = hover.append(Component.newline())
                    .append(Component.text("  ...", NamedTextColor.DARK_GRAY));
        }
        return hover;
    }

    private UpdateInfo fetchReleaseChangesSinceCurrent(String apiUrl, String currentVersion)
            throws IOException, InterruptedException {
        String releasesUrl = normalizeReleaseListUrl(apiUrl);
        if (releasesUrl == null) {
            return fetchLatestReleaseFallback(apiUrl);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(releasesUrl))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ModularPacks-UpdateChecker")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " from update API");
        }

        List<ReleaseInfo> releases = parseReleaseList(response.body());
        if (releases.isEmpty()) {
            return fetchLatestReleaseFallback(apiUrl);
        }

        List<ReleaseInfo> newer = new ArrayList<>();
        for (ReleaseInfo release : releases) {
            if (release == null || release.tagName == null || release.tagName.isBlank()) {
                continue;
            }
            if (isNewerVersion(release.tagName, currentVersion)) {
                newer.add(release);
            }
        }

        if (newer.isEmpty()) {
            return null;
        }

        ReleaseInfo latest = newer.get(0);
        String combinedBody = combineReleaseBodies(newer);
        return new UpdateInfo(latest.tagName, latest.url, combinedBody);
    }

    private UpdateInfo fetchLatestReleaseFallback(String apiUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "ModularPacks-UpdateChecker")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " from update API");
        }

        String json = response.body();
        String tagName = extractJsonString(json, "tag_name");
        String htmlUrl = extractJsonString(json, "html_url");
        String body = extractJsonString(json, "body");
        return new UpdateInfo(tagName, htmlUrl, body);
    }

    private static String normalizeReleaseListUrl(String apiUrl) {
        if (apiUrl == null || apiUrl.isBlank()) {
            return null;
        }

        String trimmed = apiUrl.trim();
        String replacement = "/releases/latest";
        int idx = trimmed.indexOf(replacement);
        if (idx >= 0) {
            String prefix = trimmed.substring(0, idx);
            return prefix + "/releases?per_page=" + RELEASE_FETCH_LIMIT;
        }

        if (trimmed.contains("/releases?")) {
            if (trimmed.contains("per_page=")) {
                return trimmed;
            }
            return trimmed + "&per_page=" + RELEASE_FETCH_LIMIT;
        }

        return null;
    }

    private static List<ReleaseInfo> parseReleaseList(String jsonArray) {
        List<ReleaseInfo> out = new ArrayList<>();
        for (String rawObject : splitTopLevelObjects(jsonArray)) {
            boolean draft = extractJsonBoolean(rawObject, "draft");
            if (draft) {
                continue;
            }

            String tag = extractJsonString(rawObject, "tag_name");
            String url = extractJsonString(rawObject, "html_url");
            String body = extractJsonString(rawObject, "body");
            if (tag == null || tag.isBlank()) {
                continue;
            }
            out.add(new ReleaseInfo(tag, url, body));
        }
        return out;
    }

    private static List<String> splitTopLevelObjects(String jsonArray) {
        List<String> objects = new ArrayList<>();
        if (jsonArray == null || jsonArray.isBlank()) {
            return objects;
        }

        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaping = false;

        for (int i = 0; i < jsonArray.length(); i++) {
            char c = jsonArray.charAt(i);

            if (inString) {
                if (escaping) {
                    escaping = false;
                    continue;
                }
                if (c == '\\') {
                    escaping = true;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                continue;
            }

            if (c == '"') {
                inString = true;
                continue;
            }

            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
                continue;
            }

            if (c == '}') {
                if (depth > 0) {
                    depth--;
                    if (depth == 0 && start >= 0) {
                        objects.add(jsonArray.substring(start, i + 1));
                        start = -1;
                    }
                }
            }
        }

        return objects;
    }

    private static String extractJsonString(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyIdx = json.indexOf(marker);
        if (keyIdx < 0) {
            return null;
        }

        int colonIdx = json.indexOf(':', keyIdx + marker.length());
        if (colonIdx < 0) {
            return null;
        }

        int quoteIdx = json.indexOf('"', colonIdx + 1);
        if (quoteIdx < 0) {
            return null;
        }

        StringBuilder out = new StringBuilder();
        boolean escaping = false;
        for (int i = quoteIdx + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaping) {
                switch (c) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    default -> out.append(c);
                }
                escaping = false;
                continue;
            }

            if (c == '\\') {
                escaping = true;
                continue;
            }
            if (c == '"') {
                return out.toString();
            }
            out.append(c);
        }

        return null;
    }

    private static boolean extractJsonBoolean(String json, String key) {
        String marker = "\"" + key + "\"";
        int keyIdx = json.indexOf(marker);
        if (keyIdx < 0) {
            return false;
        }

        int colonIdx = json.indexOf(':', keyIdx + marker.length());
        if (colonIdx < 0) {
            return false;
        }

        int i = colonIdx + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }

        return i + 4 <= json.length() && json.regionMatches(true, i, "true", 0, 4);
    }

    private static String combineReleaseBodies(List<ReleaseInfo> releasesDescending) {
        if (releasesDescending == null || releasesDescending.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder();
        for (int i = releasesDescending.size() - 1; i >= 0; i--) {
            ReleaseInfo release = releasesDescending.get(i);
            if (release == null || release.tagName == null || release.tagName.isBlank()) {
                continue;
            }

            if (!out.isEmpty()) {
                out.append("\n\n");
            }

            out.append(release.tagName).append(':');
            if (release.body == null || release.body.isBlank()) {
                out.append("\n- No release notes provided.");
            } else {
                out.append('\n').append(release.body.trim());
            }
        }

        return out.toString();
    }

    /**
     * Strips a Minecraft-version prefix (e.g. "1.21.10-") from a plugin version
     * string so that "1.21.10-2.4.8" compares correctly against a tag like "2.4.9".
     * If there is no dash, the string is returned unchanged.
     */
    private static String pluginVersionPart(String version) {
        if (version == null) {
            return "";
        }
        int dash = version.lastIndexOf('-');
        if (dash >= 0 && dash < version.length() - 1) {
            String candidate = version.substring(dash + 1);
            if (Character.isDigit(candidate.charAt(0))) {
                return candidate;
            }
        }
        return version;
    }

    private static boolean isNewerVersion(String latest, String current) {
        List<Integer> latestNums = extractNumbers(pluginVersionPart(latest));
        List<Integer> currentNums = extractNumbers(pluginVersionPart(current));

        if (latestNums.isEmpty() || currentNums.isEmpty()) {
            return !latest.equalsIgnoreCase(current);
        }

        int max = Math.max(latestNums.size(), currentNums.size());
        for (int i = 0; i < max; i++) {
            int l = i < latestNums.size() ? latestNums.get(i) : 0;
            int c = i < currentNums.size() ? currentNums.get(i) : 0;
            if (l > c) {
                return true;
            }
            if (l < c) {
                return false;
            }
        }

        return false;
    }

    private static List<Integer> extractNumbers(String version) {
        List<Integer> numbers = new ArrayList<>();
        Matcher m = DIGITS.matcher(version == null ? "" : version);
        while (m.find()) {
            try {
                numbers.add(Integer.parseInt(m.group()));
            } catch (NumberFormatException ignored) {
                // Ignore malformed version chunks.
            }
        }
        return numbers;
    }

    private static final class UpdateInfo {
        private final String tagName;
        private final String url;
        private final String body;

        private UpdateInfo(String tagName, String url, String body) {
            this.tagName = tagName == null ? "" : tagName.trim();
            this.url = url == null ? "" : url.trim();
            this.body = normalizeBody(body);
        }

        private static String normalizeBody(String input) {
            if (input == null) {
                return "";
            }
            return input.replace("\r", "").trim();
        }
    }

    private static final class ReleaseInfo {
        private final String tagName;
        private final String url;
        private final String body;

        private ReleaseInfo(String tagName, String url, String body) {
            this.tagName = tagName == null ? "" : tagName.trim();
            this.url = url == null ? "" : url.trim();
            this.body = body == null ? "" : body.trim();
        }
    }
}
