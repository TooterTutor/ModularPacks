package io.github.tootertutor.ModularPacks.config;

import java.io.File;
import java.util.Collections;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;

public final class LangManager {

    private final ModularPacksPlugin plugin;
    private YamlConfiguration lang;

    public LangManager(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File f = new File(plugin.getDataFolder(), "lang/en_us.yml");
        if (!f.exists()) {
            plugin.saveResource("lang/en_us.yml", false);
        }
        this.lang = YamlConfiguration.loadConfiguration(f);
    }

    public List<String> moduleActions() {
        if (lang == null)
            return Collections.emptyList();
        return lang.getStringList("moduleActions");
    }

    public String get(String path, String fallback) {
        if (lang == null)
            return fallback;
        return lang.getString(path, fallback);
    }

    public List<String> getList(String path) {
        if (lang == null)
            return Collections.emptyList();
        List<String> out = lang.getStringList(path);
        return out == null ? Collections.emptyList() : out;
    }
}
