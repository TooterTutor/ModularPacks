package io.github.tootertutor.ModularPacks.modules;

import java.util.Iterator;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.plugin.Plugin;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.api.modules.AbstractModule;
import io.github.tootertutor.ModularPacks.config.ScreenType;
import io.github.tootertutor.ModularPacks.listeners.ModuleClickHandler;
import io.github.tootertutor.ModularPacks.util.ItemStacks;
import net.kyori.adventure.text.Component;

/**
 * Stonecutter module that provides a stonecutter interface in backpacks.
 * Allows converting stone-related items into different shapes and forms.
 */
public final class StonecutterModule extends AbstractModule {

    private static final int INPUT_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;

    public StonecutterModule() {
        super("stonecutter", ScreenType.STONECUTTER, "Stonecutter Module");
    }

    @Override
    public void open(ModularPacksPlugin plugin, Player player, UUID backpackId, String backpackType, UUID moduleId) {
        if (plugin == null || player == null || !player.isOnline())
            return;

        InventoryView view = MenuType.STONECUTTER.builder()
                .title(Component.text("Stonecutter Module"))
                .location(player.getLocation())
                .checkReachable(false)
                .build(player);
        if (view == null)
            return;

        Inventory top = view.getTopInventory();

        // Load saved state
        byte[] state = loadState(plugin, backpackId, backpackType, moduleId);
        if (state != null && state.length > 0) {
            ItemStack[] saved = loadItemStackArray(state);
            int limit = Math.min(saved.length, top.getSize());
            for (int i = 0; i < limit; i++) {
                top.setItem(i, saved[i]);
            }
        }

        // Output is derived - clear it
        if (top.getSize() > 1) {
            top.setItem(OUTPUT_SLOT, null);
        }

        player.openInventory(view);

        // Verify the inventory actually opened (another plugin could have cancelled it)
        InventoryView current = player.getOpenInventory();
        if (current == null || current.getTopInventory() != view.getTopInventory()) {
            return;
        }

        createSession(player, backpackId, backpackType, moduleId);
        player.updateInventory();
    }

    @Override
    public void updateResult(Inventory inv) {
        if (inv == null || inv.getSize() < 2)
            return;

        ItemStack input = inv.getItem(INPUT_SLOT);
        if (ItemStacks.isAir(input)) {
            inv.setItem(OUTPUT_SLOT, null);
            return;
        }

        StonecuttingRecipe recipe = findFirstMatch(input);
        if (recipe == null) {
            inv.setItem(OUTPUT_SLOT, null);
            return;
        }

        ItemStack result = recipe.getResult();
        if (ItemStacks.isAir(result)) {
            inv.setItem(OUTPUT_SLOT, null);
            return;
        }

        inv.setItem(OUTPUT_SLOT, result.clone());
    }

    @Override
    public boolean isValidInventoryView(InventoryView view) {
        return view != null && view.getTopInventory().getSize() >= 2;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * Handle a click event in the stonecutter module inventory.
     * 
     * @param plugin The plugin instance (optional, for delayed operations)
     * @param e      The click event
     * @param player The player who clicked
     * @return true if the click was handled
     */
    public boolean handleClick(Plugin plugin, InventoryClickEvent e, Player player) {
        Inventory inv = e.getView().getTopInventory();
        if (inv == null || inv.getSize() < 2)
            return false;

        return ModuleClickHandler.handleOutput(plugin, e, player, OUTPUT_SLOT,
                () -> craftOnceToCursor(player, inv),
                () -> craftShift(player, inv),
                () -> updateResult(inv));
    }

    @Override
    protected byte[] serializeState(Inventory inventory) {
        ItemStack[] items = new ItemStack[inventory.getSize()];
        for (int i = 0; i < items.length; i++) {
            items[i] = inventory.getItem(i);
        }
        // Don't save the output slot (it's derived)
        if (items.length > 1)
            items[OUTPUT_SLOT] = null;

        return saveItemStackArray(items);
    }

    @Override
    protected void deserializeState(Inventory inventory, byte[] stateBytes) {
        ItemStack[] saved = loadItemStackArray(stateBytes);
        int limit = Math.min(saved.length, inventory.getSize());
        for (int i = 0; i < limit; i++) {
            inventory.setItem(i, saved[i]);
        }
    }

    // ========== Private Crafting Logic ==========

    private void craftShift(Player player, Inventory inv) {
        ModuleLogicHelper.standardCraftShift(player, 64, () -> {
            ItemStack input = inv.getItem(INPUT_SLOT);
            if (ItemStacks.isAir(input))
                return null;
            StonecuttingRecipe recipe = findFirstMatch(input);
            if (recipe == null)
                return null;
            return recipe.getResult();
        }, () -> {
            ItemStack input = inv.getItem(INPUT_SLOT);
            inv.setItem(INPUT_SLOT, ModuleLogicHelper.decrementOne(input));
        });
    }

    private void craftOnceToCursor(Player player, Inventory inv) {
        ItemStack input = inv.getItem(INPUT_SLOT);
        if (ItemStacks.isAir(input))
            return;

        StonecuttingRecipe recipe = findFirstMatch(input);
        if (recipe == null)
            return;

        ItemStack result = recipe.getResult();

        ModuleLogicHelper.standardCraftOnceToCursor(player, result, () -> {
            inv.setItem(INPUT_SLOT, ModuleLogicHelper.decrementOne(input));
        });
    }

    private StonecuttingRecipe findFirstMatch(ItemStack input) {
        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (!(r instanceof StonecuttingRecipe sc))
                continue;
            if (sc.getInputChoice() != null && sc.getInputChoice().test(input))
                return sc;
        }
        return null;
    }
}
