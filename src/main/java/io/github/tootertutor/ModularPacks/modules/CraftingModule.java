package io.github.tootertutor.ModularPacks.modules;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.api.modules.AbstractModule;
import io.github.tootertutor.ModularPacks.config.ScreenType;
import io.github.tootertutor.ModularPacks.recipes.RecipeManager;
import net.kyori.adventure.text.Component;

/**
 * Crafting module that provides a 3x3 crafting table interface in backpacks.
 * Supports all vanilla and custom recipes with proper recipe book integration.
 */
public final class CraftingModule extends AbstractModule {

    private static final int RESULT_SLOT = 0;
    private static final int MATRIX_FIRST_SLOT = 1;
    private static final int MATRIX_SIZE = 9;

    public CraftingModule() {
        super("crafting", ScreenType.CRAFTING, "Crafting Module");
    }

    @Override
    public void open(ModularPacksPlugin plugin, Player player, UUID backpackId, String backpackType, UUID moduleId) {
        if (plugin == null || player == null || !player.isOnline())
            return;

        InventoryView view = MenuType.CRAFTING.builder()
                .title(Component.text("Crafting Module"))
                .location(player.getLocation())
                .checkReachable(false)
                .build(player);
        if (view == null)
            return;

        Inventory top = view.getTopInventory();

        // Load saved matrix
        byte[] state = loadState(plugin, backpackId, backpackType, moduleId);
        if (state != null && state.length > 0) {
            ItemStack[] saved = loadItemStackArray(state);
            int limit = Math.min(saved.length, top.getSize());
            for (int i = 0; i < limit; i++) {
                top.setItem(i, saved[i]);
            }
        }

        // Output is derived - clear it
        if (top.getSize() > 0) {
            top.setItem(RESULT_SLOT, null);
        }

        // Compute the derived output based on the current matrix
        CraftingModuleLogic.updateResult(plugin.recipes(), player, top);

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
        CraftingModuleLogic.updateResult(inv);
    }

    /**
     * Update the crafting result with recipe manager and player context.
     * 
     * @param recipes The recipe manager
     * @param player  The player
     * @param inv     The inventory
     */
    public void updateResult(RecipeManager recipes, Player player, Inventory inv) {
        CraftingModuleLogic.updateResult(recipes, player, inv);
    }

    @Override
    public boolean isValidInventoryView(InventoryView view) {
        return view != null && view.getTopInventory().getSize() >= MATRIX_FIRST_SLOT + MATRIX_SIZE;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * Handle a click event in the crafting module inventory.
     * 
     * @param recipes The recipe manager
     * @param e       The click event
     * @param player  The player who clicked
     * @return true if the click was handled
     */
    public boolean handleResultClick(RecipeManager recipes, InventoryClickEvent e, Player player) {
        return CraftingModuleLogic.handleResultClick(recipes, e, player);
    }

    @Override
    protected byte[] serializeState(Inventory inventory) {
        // Persist everything except the derived result slot
        ItemStack[] items = new ItemStack[inventory.getSize()];
        for (int i = 0; i < items.length; i++) {
            items[i] = inventory.getItem(i);
        }
        if (items.length > 0)
            items[RESULT_SLOT] = null;

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

    @Override
    public void handleClose(ModularPacksPlugin plugin, Player player, Inventory inventory) {
        if (plugin == null || player == null || inventory == null)
            return;

        ModuleSession session = removeSession(player);
        if (session == null)
            return;

        // Save state
        byte[] stateBytes = serializeState(inventory);
        saveState(plugin, session.backpackId(), session.backpackType(), session.moduleId(), stateBytes);

        // Clear matrix BEFORE vanilla returns items to the player
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, null);
        }

        // Notify session manager
        notifySessionClose(plugin, player, session.backpackId());
    }
}
