package io.github.tootertutor.ModularPacks.modules.crafting;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

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

    private enum SessionMode {
        CRAFTING,
        AUTOCRAFTING
    }

    private final ConcurrentMap<UUID, SessionMode> sessionModes = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> autocraftingDesiredAmounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> autocraftingCooldownTicks = new ConcurrentHashMap<>();

    public CraftingModule() {
        super("crafting", ScreenType.CRAFTING, "Crafting Module");
    }

    @Override
    public void open(ModularPacksPlugin plugin, Player player, UUID backpackId, String backpackType, UUID moduleId) {
        if (plugin == null || player == null || !player.isOnline())
            return;

        UUID playerId = player.getUniqueId();
        SessionMode mode = resolveSessionMode(plugin, backpackId, backpackType, moduleId);
        sessionModes.put(playerId, mode);

        InventoryView view = MenuType.CRAFTING.builder()
                .title(Component.text(mode == SessionMode.AUTOCRAFTING ? "Autocrafting Module" : "Crafting Module"))
                .location(player.getLocation())
                .checkReachable(false)
                .build(player);
        if (view == null)
            return;

        Inventory top = view.getTopInventory();

        // Load saved matrix
        byte[] state = loadState(plugin, backpackId, backpackType, moduleId);
        if (state != null && state.length > 0) {
            ItemStack[] saved;
            if (mode == SessionMode.AUTOCRAFTING) {
                var decoded = AutocraftingStateCodec.decode(state);
                saved = normalizeAutocraftingInventory(decoded.inventoryItems(), top.getSize());
                autocraftingDesiredAmounts.put(playerId,
                        AutocraftingStateCodec.clampDesiredAmount(decoded.desiredAmount()));
                autocraftingCooldownTicks.put(playerId, Math.max(0, decoded.cooldownTicks()));
            } else {
                saved = loadItemStackArray(state);
            }
            int limit = Math.min(saved.length, top.getSize());
            for (int i = 0; i < limit; i++) {
                top.setItem(i, saved[i]);
            }
        } else if (mode == SessionMode.AUTOCRAFTING) {
            autocraftingDesiredAmounts.put(playerId, 1);
            autocraftingCooldownTicks.put(playerId, 0);
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

    public boolean isAutocraftingSession(Player player) {
        if (player == null)
            return false;
        return sessionModes.get(player.getUniqueId()) == SessionMode.AUTOCRAFTING;
    }

    public boolean handleAutocraftingResultClick(InventoryClickEvent e, Player player) {
        if (player == null || e == null || !isAutocraftingSession(player))
            return false;

        Inventory inv = e.getView().getTopInventory();
        if (inv == null || inv.getSize() < MATRIX_FIRST_SLOT + MATRIX_SIZE)
            return false;

        if (e.getRawSlot() != RESULT_SLOT)
            return false;

        e.setCancelled(true);

        ClickType click = e.getClick();
        int step = click.isShiftClick() ? 8 : 1;
        int delta = 0;
        if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
            delta = step;
        } else if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            delta = -step;
        }

        if (delta != 0) {
            UUID playerId = player.getUniqueId();
            int current = getAutocraftingDesiredAmount(player);
            int updated = AutocraftingStateCodec.clampDesiredAmount(current + delta);
            autocraftingDesiredAmounts.put(playerId, updated);
            player.sendActionBar(Component.text("Autocraft batch amount: " + updated));
        }

        return true;
    }

    public int getAutocraftingDesiredAmount(Player player) {
        if (player == null)
            return 1;
        int desired = autocraftingDesiredAmounts.getOrDefault(player.getUniqueId(), 1);
        return AutocraftingStateCodec.clampDesiredAmount(desired);
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

        UUID playerId = player.getUniqueId();
        ModuleSession session = removeSession(player);
        if (session == null)
            return;

        SessionMode mode = sessionModes.remove(playerId);

        int desiredAmount = 1;
        int cooldownTicks = 0;
        if (mode == SessionMode.AUTOCRAFTING) {
            desiredAmount = getAutocraftingDesiredAmount(player);
            cooldownTicks = Math.max(0, autocraftingCooldownTicks.getOrDefault(playerId, 0));
        }
        autocraftingDesiredAmounts.remove(playerId);
        autocraftingCooldownTicks.remove(playerId);

        // Save state
        byte[] stateBytes;
        if (mode == SessionMode.AUTOCRAFTING) {
            ItemStack[] items = new ItemStack[inventory.getSize()];
            for (int i = 0; i < items.length; i++) {
                items[i] = inventory.getItem(i);
            }
            items = normalizeAutocraftingInventory(items, inventory.getSize());
            stateBytes = AutocraftingStateCodec
                    .encode(new AutocraftingStateCodec.State(items, desiredAmount, cooldownTicks));
        } else {
            stateBytes = serializeState(inventory);
        }
        saveState(plugin, session.backpackId(), session.backpackType(), session.moduleId(), stateBytes);

        // Clear matrix BEFORE vanilla returns items to the player
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, null);
        }

        // Notify session manager
        notifySessionClose(plugin, player, session.backpackId());
    }

    private SessionMode resolveSessionMode(ModularPacksPlugin plugin, UUID backpackId, String backpackType,
            UUID moduleId) {
        if (plugin == null || backpackId == null || backpackType == null || moduleId == null)
            return SessionMode.CRAFTING;

        var data = plugin.repo().loadOrCreate(backpackId, backpackType);
        byte[] snap = data.installedSnapshots().get(moduleId);
        if (snap == null || snap.length == 0)
            return SessionMode.CRAFTING;

        ItemStack[] arr = loadItemStackArray(snap);
        if (arr.length == 0 || arr[0] == null || !arr[0].hasItemMeta())
            return SessionMode.CRAFTING;

        ItemMeta meta = arr[0].getItemMeta();
        if (meta == null)
            return SessionMode.CRAFTING;

        String moduleType = meta.getPersistentDataContainer().get(plugin.keys().MODULE_TYPE, PersistentDataType.STRING);
        if (moduleType != null && moduleType.equalsIgnoreCase("Autocrafting")) {
            return SessionMode.AUTOCRAFTING;
        }
        return SessionMode.CRAFTING;
    }

    private ItemStack[] normalizeAutocraftingInventory(ItemStack[] items, int inventorySize) {
        int size = Math.max(0, inventorySize);
        ItemStack[] normalized = new ItemStack[size];
        if (items != null && items.length > 0) {
            System.arraycopy(items, 0, normalized, 0, Math.min(items.length, size));
        }

        if (normalized.length > RESULT_SLOT) {
            normalized[RESULT_SLOT] = null;
        }

        int limit = Math.min(normalized.length, MATRIX_FIRST_SLOT + MATRIX_SIZE);
        for (int slot = MATRIX_FIRST_SLOT; slot < limit; slot++) {
            ItemStack item = normalized[slot];
            if (item == null || item.getType().isAir()) {
                normalized[slot] = null;
                continue;
            }

            ItemStack ghost = item.clone();
            ghost.setAmount(1);
            normalized[slot] = ghost;
        }

        return normalized;
    }
}
