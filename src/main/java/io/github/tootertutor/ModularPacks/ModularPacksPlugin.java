package io.github.tootertutor.ModularPacks;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import io.github.tootertutor.ModularPacks.api.ModularPacksAPI;
import io.github.tootertutor.ModularPacks.api.modules.ModuleFactory;
import io.github.tootertutor.ModularPacks.commands.CommandRouter;
import io.github.tootertutor.ModularPacks.commands.sub.GiveSubcommand;
import io.github.tootertutor.ModularPacks.commands.sub.ListSubcommand;
import io.github.tootertutor.ModularPacks.commands.sub.OpenSubcommand;
import io.github.tootertutor.ModularPacks.commands.sub.RecipeSubcommand;
import io.github.tootertutor.ModularPacks.commands.sub.RecoverSubcommand;
import io.github.tootertutor.ModularPacks.commands.sub.RefreshSkullsSubcommand;
import io.github.tootertutor.ModularPacks.commands.sub.ReloadSubcommand;
import io.github.tootertutor.ModularPacks.commands.sub.SetTypeSubcommand;
import io.github.tootertutor.ModularPacks.config.ConfigManager;
import io.github.tootertutor.ModularPacks.config.LangManager;
import io.github.tootertutor.ModularPacks.data.SQLiteBackpackRepository;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuRenderer;
import io.github.tootertutor.ModularPacks.gui.ScreenRouter;
import io.github.tootertutor.ModularPacks.item.Keys;
import io.github.tootertutor.ModularPacks.listeners.backpack.BackpackEverlastingListener;
import io.github.tootertutor.ModularPacks.listeners.backpack.BackpackMenuListener;
import io.github.tootertutor.ModularPacks.listeners.backpack.BackpackPlacementListener;
import io.github.tootertutor.ModularPacks.listeners.backpack.BackpackUseListener;
import io.github.tootertutor.ModularPacks.listeners.backpack.ClickDebugListener;
import io.github.tootertutor.ModularPacks.listeners.backpack.PlacedBackpackBreakListener;
import io.github.tootertutor.ModularPacks.listeners.backpack.PlacedBackpackInteractListener;
import io.github.tootertutor.ModularPacks.listeners.backpack.PreventNestingListener;
import io.github.tootertutor.ModularPacks.listeners.backpack.RecipePreviewListener;
import io.github.tootertutor.ModularPacks.listeners.module.AnvilModuleListener;
import io.github.tootertutor.ModularPacks.listeners.module.CraftingModuleListener;
import io.github.tootertutor.ModularPacks.listeners.module.FurnaceModuleListener;
import io.github.tootertutor.ModularPacks.listeners.module.ModuleFilterScreenListener;
import io.github.tootertutor.ModularPacks.listeners.module.ModuleRecipeListener;
import io.github.tootertutor.ModularPacks.listeners.module.PreventModulePlacementListener;
import io.github.tootertutor.ModularPacks.listeners.module.PreventModuleUseListener;
import io.github.tootertutor.ModularPacks.listeners.module.RestockModuleListener;
import io.github.tootertutor.ModularPacks.listeners.module.SmithingModuleListener;
import io.github.tootertutor.ModularPacks.listeners.module.StonecutterModuleListener;
import io.github.tootertutor.ModularPacks.modules.ModuleEngineService;
import io.github.tootertutor.ModularPacks.recipes.RecipeManager;

public final class ModularPacksPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private LangManager langManager;
    private SQLiteBackpackRepository repository;
    private Keys keys;
    private ModuleEngineService engines;
    private ClickDebugListener clickDebug;
    private RecipeManager recipes;
    private BackpackSessionManager sessions;
    private ModuleFactory moduleFactory;
    private PlacedBackpackManager placedBackpacks;
    private BackpackMenuRenderer backpackMenuRenderer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("lang/en_us.yml", false);

        // Initialize the public API
        ModularPacksAPI.initialize(this);
        ModularPacksAPI api = ModularPacksAPI.getInstance();
        this.moduleFactory = new ModuleFactory(this, api.getModuleRegistry());

        this.keys = new Keys(this);

        this.configManager = new ConfigManager(this);
        this.configManager.reload();

        this.langManager = new LangManager(this);
        this.langManager.reload();

        this.repository = new SQLiteBackpackRepository(this);
        this.repository.init();

        this.sessions = new BackpackSessionManager(this);

        this.placedBackpacks = new PlacedBackpackManager(this);

        // Create module instances
        ScreenRouter screenRouter = new ScreenRouter(
                this);

        this.engines = new ModuleEngineService(this, screenRouter);
        this.engines.start();

        this.recipes = new RecipeManager(this);
        this.recipes.reload();
        Bukkit.getPluginManager().registerEvents(this.recipes, this);

        this.backpackMenuRenderer = new BackpackMenuRenderer(this);

        Bukkit.getPluginManager().registerEvents(new BackpackUseListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BackpackMenuListener(this, backpackMenuRenderer, screenRouter),
                this);
        Bukkit.getPluginManager().registerEvents(new ModuleRecipeListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ModuleFilterScreenListener(this), this);
        Bukkit.getPluginManager().registerEvents(new RestockModuleListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PreventNestingListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PreventModulePlacementListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PreventModuleUseListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BackpackEverlastingListener(this), this);
        Bukkit.getPluginManager().registerEvents(new BackpackPlacementListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlacedBackpackInteractListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlacedBackpackBreakListener(this), this);
        Bukkit.getPluginManager().registerEvents(new AnvilModuleListener(this, screenRouter.getAnvilModule()), this);
        Bukkit.getPluginManager().registerEvents(new FurnaceModuleListener(this, screenRouter.getFurnaceModule()),
                this);
        Bukkit.getPluginManager().registerEvents(new CraftingModuleListener(this, screenRouter.getCraftingModule()),
                this);
        Bukkit.getPluginManager().registerEvents(new SmithingModuleListener(this, screenRouter.getSmithingModule()),
                this);
        Bukkit.getPluginManager()
                .registerEvents(new StonecutterModuleListener(this, screenRouter.getStonecutterModule()), this);
        Bukkit.getPluginManager().registerEvents(new RecipePreviewListener(), this);

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
        router.register(new RecoverSubcommand(this));
        router.register(new ReloadSubcommand(this));
        router.register(new SetTypeSubcommand(this));
        router.register(new RefreshSkullsSubcommand(this));
        router.register(new RecipeSubcommand(this));
        getCommand("backpack").setExecutor(router);
        getCommand("backpack").setTabCompleter(router);

        getLogger().info("modularpacks enabled.");

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

        if (placedBackpacks != null)
            placedBackpacks.shutdown();

        getLogger().info("modularpacks disabled.");
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

    public BackpackSessionManager sessions() {
        return sessions;
    }

    public ModuleFactory moduleFactory() {
        return moduleFactory;
    }

    public PlacedBackpackManager placedBackpacks() {
        return placedBackpacks;
    }

    public BackpackMenuRenderer getBackpackMenuRenderer() {
        return backpackMenuRenderer;
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
