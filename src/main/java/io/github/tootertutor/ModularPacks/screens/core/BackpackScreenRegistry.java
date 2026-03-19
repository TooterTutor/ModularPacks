package io.github.tootertutor.ModularPacks.screens.core;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.config.ScreenType;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;
import io.github.tootertutor.ModularPacks.gui.ModuleScreenHolder;
import net.kyori.adventure.text.Component;

public final class BackpackScreenRegistry {

    private static final String WHITELIST = "WHITELIST";

    private final ModularPacksPlugin plugin;
    private final Map<ScreenType, ScreenDefinition> definitions = new EnumMap<>(ScreenType.class);

    public BackpackScreenRegistry(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        registerDefaults();
    }

    public ScreenDefinition get(ScreenType screenType) {
        if (screenType == null)
            return null;
        return definitions.get(screenType);
    }

    public Inventory createInventory(ModuleScreenHolder holder) {
        String moduleType = resolveModuleType(holder.backpackId(), holder.moduleId());
        String filterMode = resolveFilterMode(holder.backpackId(), holder.moduleId());

        ScreenDefinition definition = resolveDefinition(moduleType, holder.screenType());
        if (definition == null)
            return Bukkit.createInventory(holder, 27, Component.text("Module"));

        Component title = Component.text(definition.titleResolver()
                .resolve(holder.screenType(), moduleType, filterMode));

        if (definition.usesInventoryType()) {
            return Bukkit.createInventory(holder, definition.inventoryType(), title);
        }
        return Bukkit.createInventory(holder, definition.inventorySize(), title);
    }

    private void registerDefaults() {
        register(ScreenDefinition.typed(ScreenType.CRAFTING, InventoryType.WORKBENCH,
                (screen, moduleType, filterMode) -> "Crafting Module"));
        register(ScreenDefinition.typed(ScreenType.SMITHING, InventoryType.SMITHING,
                (screen, moduleType, filterMode) -> "Smithing Module"));
        register(ScreenDefinition.typed(ScreenType.SMELTING, InventoryType.FURNACE,
                (screen, moduleType, filterMode) -> "Smelting Module"));
        register(ScreenDefinition.typed(ScreenType.BLASTING, InventoryType.BLAST_FURNACE,
                (screen, moduleType, filterMode) -> "Blasting Module"));
        register(ScreenDefinition.typed(ScreenType.SMOKING, InventoryType.SMOKER,
                (screen, moduleType, filterMode) -> "Smoking Module"));
        register(ScreenDefinition.typed(ScreenType.STONECUTTER, InventoryType.STONECUTTER,
                (screen, moduleType, filterMode) -> "Stonecutter Module"));
        register(ScreenDefinition.typed(ScreenType.ANVIL, InventoryType.ANVIL,
                (screen, moduleType, filterMode) -> "Anvil Module"));
        register(ScreenDefinition.typed(ScreenType.DROPPER, InventoryType.DROPPER,
                (screen, moduleType, filterMode) -> "Configuration"));
        register(ScreenDefinition.typed(ScreenType.HOPPER, InventoryType.HOPPER,
                (screen, moduleType, filterMode) -> "Configuration"));
        register(ScreenDefinition.typed(ScreenType.GENERIC, InventoryType.CHEST,
                (screen, moduleType, filterMode) -> "Custom Module"));
    }

    private void register(ScreenDefinition definition) {
        definitions.put(definition.screenType(), definition);
    }

    private ScreenDefinition resolveDefinition(String moduleType, ScreenType screenType) {
        ModuleDeclaration declaration = BuiltInModuleDeclarations.find(moduleType);
        if (declaration != null) {
            ScreenDefinition definition = declaration.getScreenDefinition(screenType);
            if (definition != null)
                return definition;
        }
        return definitions.get(screenType);
    }

    private String resolveModuleType(UUID backpackId, UUID moduleId) {
        try {
            BackpackData data = plugin.repo().loadOrCreate(backpackId, null);
            if (data == null)
                return null;

            byte[] snap = data.installedSnapshots().get(moduleId);
            if (snap == null || snap.length == 0)
                return null;

            ItemStack[] arr = ItemStackCodec.fromBytes(snap);
            if (arr.length == 0 || arr[0] == null || !arr[0].hasItemMeta())
                return null;

            ItemMeta meta = arr[0].getItemMeta();
            if (meta == null)
                return null;

            return meta.getPersistentDataContainer().get(plugin.keys().MODULE_TYPE, PersistentDataType.STRING);
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveFilterMode(UUID backpackId, UUID moduleId) {
        try {
            BackpackData data = plugin.repo().loadOrCreate(backpackId, null);
            if (data == null)
                return WHITELIST;

            byte[] snap = data.installedSnapshots().get(moduleId);
            if (snap == null || snap.length == 0)
                return WHITELIST;

            ItemStack[] arr = ItemStackCodec.fromBytes(snap);
            if (arr.length == 0 || arr[0] == null || !arr[0].hasItemMeta())
                return WHITELIST;

            ItemMeta meta = arr[0].getItemMeta();
            if (meta == null)
                return WHITELIST;

            String mode = meta.getPersistentDataContainer().get(plugin.keys().MODULE_FILTER_MODE,
                    PersistentDataType.STRING);
            return mode != null ? mode : WHITELIST;
        } catch (Exception ex) {
            return WHITELIST;
        }
    }
}
