package io.github.tootertutor.ModularPacks.config;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;

public final class LangManager {

    private static final String DEFAULT_LOCALE = "en_us";

    private final ModularPacksPlugin plugin;
    private YamlConfiguration lang;
    /** Cache of non-default locale files, keyed by normalized locale string. */
    private final Map<String, YamlConfiguration> localeCache = new ConcurrentHashMap<>();

    public LangManager(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    /** Reloads the default language file and clears any cached locale overrides. */
    public void reload() {
        File f = new File(plugin.getDataFolder(), "lang/" + DEFAULT_LOCALE + ".yml");
        if (!f.exists()) {
            plugin.saveResource("lang/" + DEFAULT_LOCALE + ".yml", false);
        }
        this.lang = YamlConfiguration.loadConfiguration(f);
        localeCache.clear();
    }

    /**
     * Returns a string from the default locale file.
     *
     * @param path     YAML path to resolve
     * @param fallback value returned when the path is missing
     * @return translated string or the provided fallback
     */
    public String get(String path, String fallback) {
        if (lang == null)
            return fallback;
        return lang.getString(path, fallback);
    }

    /**
     * Returns a string list from the default locale file.
     *
     * @param path YAML path to resolve
     * @return translated list or an empty list when missing
     */
    public List<String> getList(String path) {
        if (lang == null)
            return Collections.emptyList();
        List<String> out = lang.getStringList(path);
        return out == null ? Collections.emptyList() : out;
    }

    /**
     * Returns the raw YAML value at the given path (String, Number, Boolean, List,
     * etc.). Useful for dynamic placeholder expansion.
     */
    public Object raw(String path) {
        if (lang == null || path == null)
            return null;
        return lang.get(path);
    }

    public boolean has(String path) {
        if (lang == null || path == null)
            return false;
        return lang.contains(path);
    }

    /**
     * Returns a player-localized string, falling back to the default locale when
     * the player's locale file or key is missing.
     *
     * @param player   player whose locale should be used
     * @param path     YAML path to resolve
     * @param fallback value returned when the path is missing everywhere
     * @return translated string or the provided fallback
     */
    public String get(Player player, String path, String fallback) {
        YamlConfiguration cfg = localeFor(player);
        String value = cfg.getString(path);
        if (value != null)
            return value;
        // Fall back to default locale
        return get(path, fallback);
    }

    /**
     * Returns a player-localized string list, falling back to the default locale
     * when the player's locale file or key is missing.
     *
     * @param player player whose locale should be used
     * @param path   YAML path to resolve
     * @return translated list or an empty list when missing
     */
    public List<String> getList(Player player, String path) {
        YamlConfiguration cfg = localeFor(player);
        List<String> out = cfg.getStringList(path);
        if (out != null && !out.isEmpty())
            return out;
        // Fall back to default locale
        return getList(path);
    }

    public Object raw(Player player, String path) {
        if (player == null || path == null)
            return raw(path);
        YamlConfiguration cfg = localeFor(player);
        Object value = cfg.get(path);
        return value != null ? value : raw(path);
    }

    public boolean has(Player player, String path) {
        if (player == null || path == null)
            return has(path);
        return localeFor(player).contains(path) || has(path);
    }

    /** Returns the YamlConfiguration for the player's locale, or the default. */
    private YamlConfiguration localeFor(Player player) {
        if (player == null)
            return lang != null ? lang : new YamlConfiguration();
        String locale = normalizeLocale(player.locale());
        if (DEFAULT_LOCALE.equals(locale))
            return lang != null ? lang : new YamlConfiguration();
        return localeCache.computeIfAbsent(locale, this::loadLocaleFile);
    }

    /**
     * Loads a locale YAML file. Returns an empty config (causing fallback to
     * en_us) if the file doesn't exist.
     */
    private YamlConfiguration loadLocaleFile(String locale) {
        File f = new File(plugin.getDataFolder(), "lang/" + locale + ".yml");
        if (!f.exists())
            return new YamlConfiguration(); // empty → callers fall back to en_us
        return YamlConfiguration.loadConfiguration(f);
    }

    /** Normalises a {@link Locale} to a file-safe string like "en_us". */
    private static String normalizeLocale(Locale locale) {
        if (locale == null)
            return DEFAULT_LOCALE;
        String lang = locale.getLanguage();
        String country = locale.getCountry();
        String result = country.isEmpty() ? lang : lang + "_" + country.toLowerCase(Locale.ROOT);
        return result.isBlank() ? DEFAULT_LOCALE : result;
    }
}
