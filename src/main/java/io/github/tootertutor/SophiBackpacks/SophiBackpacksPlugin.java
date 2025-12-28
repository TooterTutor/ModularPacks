package io.github.tootertutor.SophiBackpacks;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.tootertutor.SophiBackpacks.commands.CommandRouter;
import io.github.tootertutor.SophiBackpacks.commands.sub.GiveSubcommand;
import io.github.tootertutor.SophiBackpacks.commands.sub.ListSubcommand;
import io.github.tootertutor.SophiBackpacks.commands.sub.OpenSubcommand;
import io.github.tootertutor.SophiBackpacks.commands.sub.ReloadSubcommand;
import io.github.tootertutor.SophiBackpacks.commands.sub.SetTypeSubcommand;
import io.github.tootertutor.SophiBackpacks.config.ConfigManager;
import io.github.tootertutor.SophiBackpacks.config.LangManager;
import io.github.tootertutor.SophiBackpacks.data.SQLiteBackpackRepository;
import io.github.tootertutor.SophiBackpacks.gui.BackpackMenuRenderer;
import io.github.tootertutor.SophiBackpacks.item.Keys;
import io.github.tootertutor.SophiBackpacks.listeners.AnvilModuleListener;
import io.github.tootertutor.SophiBackpacks.listeners.BackpackMenuListener;
import io.github.tootertutor.SophiBackpacks.listeners.BackpackUseListener;
import io.github.tootertutor.SophiBackpacks.listeners.ClickDebugListener;
import io.github.tootertutor.SophiBackpacks.listeners.ModuleRecipeListener;
import io.github.tootertutor.SophiBackpacks.listeners.PreventModulePlacementListener;
import io.github.tootertutor.SophiBackpacks.listeners.PreventNestingListener;
import io.github.tootertutor.SophiBackpacks.modules.ModuleEngineService;
import io.github.tootertutor.SophiBackpacks.recipes.RecipeManager;

public final class SophiBackpacksPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private LangManager langManager;
    private SQLiteBackpackRepository repository;
    private Keys keys;
    private ModuleEngineService engines;
    private ClickDebugListener clickDebug;
    private RecipeManager recipes;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("lang/en_us.yml", false);

        this.keys = new Keys(this);

        this.configManager = new ConfigManager(this);
        this.configManager.reload();

        this.langManager = new LangManager(this);
        this.langManager.reload();

        this.repository = new SQLiteBackpackRepository(this);
        this.repository.init();

        this.engines = new ModuleEngineService(this);
        this.engines.start();

        this.recipes = new RecipeManager(this);
        this.recipes.reload();
        Bukkit.getPluginManager().registerEvents(this.recipes, this);

        BackpackMenuRenderer renderer = new BackpackMenuRenderer(this);

        Bukkit.getPluginManager().registerEvents(new BackpackUseListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BackpackMenuListener(this, renderer), this);
        Bukkit.getPluginManager().registerEvents(new ModuleRecipeListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PreventNestingListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PreventModulePlacementListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AnvilModuleListener(this), this);

        if (cfg().debugClickLog()) {
            this.clickDebug = new ClickDebugListener(this);
            this.clickDebug.start();
            Bukkit.getPluginManager().registerEvents(this.clickDebug, this);
            getLogger().warning("Click debug logging is enabled; writing to click-events.log");
        }

        CommandRouter router = new CommandRouter(this);
        router.register(new GiveSubcommand(this));
        router.register(new ListSubcommand(this));
        router.register(new OpenSubcommand(this));
        router.register(new ReloadSubcommand(this));
        router.register(new SetTypeSubcommand(this));
        getCommand("backpack").setExecutor(router);
        getCommand("backpack").setTabCompleter(router);

        getLogger().info("SophiBackpacks enabled.");

    }

    @Override
    public void onDisable() {
        if (clickDebug != null)
            clickDebug.stop();

        // remove recipes on disable so reloads don't duplicate
        if (recipes != null)
            recipes.close();

        if (repository != null)
            repository.close();

        if (engines != null)
            engines.stop();

        getLogger().info("SophiBackpacks disabled.");
    }

    public ConfigManager cfg() {
        return configManager;
    }

    public LangManager lang() {
        return langManager;
    }

    public SQLiteBackpackRepository repo() {
        return repository;
    }

    public Keys keys() {
        return keys;
    }

    public RecipeManager recipes() {
        return recipes;
    }

    public void reloadAll() {
        cfg().reload();
        lang().reload();
        if (recipes != null)
            recipes.reload();

        boolean wantClickLog = cfg().debugClickLog();
        if (wantClickLog && clickDebug == null) {
            this.clickDebug = new ClickDebugListener(this);
            this.clickDebug.start();
            Bukkit.getPluginManager().registerEvents(this.clickDebug, this);
            getLogger().warning("Click debug logging is enabled; writing to click-events.log");
        } else if (!wantClickLog && clickDebug != null) {
            HandlerList.unregisterAll(clickDebug);
            clickDebug.stop();
            clickDebug = null;
        }
    }
}
