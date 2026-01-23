package io.github.tootertutor.ModularPacks.modules;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.FurnaceView;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.api.modules.AbstractModule;
import io.github.tootertutor.ModularPacks.config.ScreenType;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.util.ItemStacks;
import net.kyori.adventure.text.Component;

/**
 * Furnace module that provides furnace-like interfaces in backpacks.
 * Supports smelting (furnace), blasting (blast furnace), and smoking (smoker).
 * Handles burn time, cook time, and XP storage/distribution.
 */
public final class FurnaceModule extends AbstractModule {

    private static final int INPUT_SLOT = 0;
    private static final int FUEL_SLOT = 1;
    private static final int OUTPUT_SLOT = 2;

    // Track which screen type each player's session is using
    private final Map<UUID, ScreenType> sessionScreenTypes = new ConcurrentHashMap<>();

    // This module needs to store the specific furnace type since it handles
    // multiple variants
    public record FurnaceSession(UUID backpackId, String backpackType, UUID moduleId, ScreenType screenType) {
    }

    public FurnaceModule() {
        super("furnace", ScreenType.SMELTING, "Furnace Module");
    }

    /**
     * Get the screen type from the active furnace session.
     * 
     * @param player The player
     * @return The screen type, or null if no session exists
     */
    public ScreenType getSessionScreenType(Player player) {
        if (player == null)
            return null;
        return sessionScreenTypes.get(player.getUniqueId());
    }

    @Override
    public void open(ModularPacksPlugin plugin, Player player, UUID backpackId, String backpackType, UUID moduleId) {
        open(plugin, player, backpackId, backpackType, moduleId, ScreenType.SMELTING);
    }

    /**
     * Open a furnace module with a specific screen type.
     * 
     * @param plugin       The plugin instance
     * @param player       The player
     * @param backpackId   The backpack UUID
     * @param backpackType The backpack type
     * @param moduleId     The module instance ID
     * @param screenType   The furnace type (SMELTING, BLASTING, or SMOKING)
     */
    public void open(ModularPacksPlugin plugin, Player player, UUID backpackId, String backpackType, UUID moduleId,
            ScreenType screenType) {
        if (plugin == null || player == null || !player.isOnline())
            return;

        var builder = switch (screenType) {
            case SMELTING -> MenuType.FURNACE.builder();
            case BLASTING -> MenuType.BLAST_FURNACE.builder();
            case SMOKING -> MenuType.SMOKER.builder();
            default -> null;
        };
        if (builder == null)
            return;

        Component title = switch (screenType) {
            case SMELTING -> Component.text("Smelting Module");
            case BLASTING -> Component.text("Blasting Module");
            case SMOKING -> Component.text("Smoking Module");
            default -> Component.text("Furnace Module");
        };

        FurnaceView view = builder.title(title)
                .location(player.getLocation())
                .checkReachable(false)
                .build(player);
        if (view == null)
            return;

        // Load and restore state
        BackpackData data = plugin.repo().loadOrCreate(backpackId, backpackType);
        byte[] stateBytes = data.moduleStates().get(moduleId);
        FurnaceStateCodec.State s = FurnaceStateCodec.decode(stateBytes);

        Inventory top = view.getTopInventory();
        top.setItem(INPUT_SLOT, s.input);
        top.setItem(FUEL_SLOT, s.fuel);
        top.setItem(OUTPUT_SLOT, s.output);

        view.setBurnTime(s.burnTime, s.burnTotal);
        view.setCookTime(s.cookTime, s.cookTotal);

        player.openInventory(view);

        // Verify the inventory actually opened
        InventoryView current = player.getOpenInventory();
        if (current == null || current.getTopInventory() != view.getTopInventory())
            return;
        InventoryType t = current.getTopInventory().getType();
        switch (t) {
            case FURNACE, BLAST_FURNACE, SMOKER -> {
            }
            default -> {
                return;
            }
        }

        // Store session with screen type info
        sessions.put(player.getUniqueId(), new ModuleSession(backpackId, backpackType, moduleId));
        sessionScreenTypes.put(player.getUniqueId(), screenType);
        player.updateInventory();
    }

    @Override
    public void updateResult(Inventory inv) {
        // Furnace result is computed by vanilla, no need to update
    }

    @Override
    public boolean isValidInventoryView(InventoryView view) {
        if (view == null)
            return false;
        InventoryType type = view.getTopInventory().getType();
        return type == InventoryType.FURNACE || type == InventoryType.BLAST_FURNACE || type == InventoryType.SMOKER;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    protected byte[] serializeState(Inventory inventory) {
        ItemStack input = inventory.getItem(INPUT_SLOT);
        ItemStack fuel = inventory.getItem(FUEL_SLOT);
        ItemStack output = inventory.getItem(OUTPUT_SLOT);

        // Get existing state to preserve progress values
        ModuleSession session = null;
        for (var entry : sessions.entrySet()) {
            session = entry.getValue();
            break; // Get any session (should only be one)
        }

        FurnaceStateCodec.State fs = new FurnaceStateCodec.State();
        fs.input = input;
        fs.fuel = fuel;
        fs.output = output;

        // Preserve existing progress if we have a session
        if (session != null) {
            BackpackData data = ModularPacksPlugin.getPlugin(ModularPacksPlugin.class).repo()
                    .loadOrCreate(session.backpackId(), session.backpackType());
            byte[] existing = data.moduleStates().get(session.moduleId());
            FurnaceStateCodec.State old = FurnaceStateCodec.decode(existing);

            fs.burnTime = old.burnTime;
            fs.burnTotal = old.burnTotal;
            fs.cookTime = old.cookTime;
            fs.cookTotal = old.cookTotal;
            fs.xpStored = reconcileXpStoredOnClose(old, output);
        }

        return FurnaceStateCodec.encode(fs);
    }

    @Override
    protected void deserializeState(Inventory inventory, byte[] stateBytes) {
        FurnaceStateCodec.State s = FurnaceStateCodec.decode(stateBytes);
        inventory.setItem(INPUT_SLOT, s.input);
        inventory.setItem(FUEL_SLOT, s.fuel);
        inventory.setItem(OUTPUT_SLOT, s.output);
    }

    @Override
    public void handleClose(ModularPacksPlugin plugin, Player player, Inventory inventory) {
        if (plugin == null || player == null || inventory == null)
            return;

        ModuleSession session = removeSession(player);
        sessionScreenTypes.remove(player.getUniqueId());
        if (session == null)
            return;

        // Save state with progress preservation
        byte[] stateBytes = serializeState(inventory);
        saveState(plugin, session.backpackId(), session.backpackType(), session.moduleId(), stateBytes);

        // Clear BEFORE vanilla tries to return items
        inventory.setItem(INPUT_SLOT, null);
        inventory.setItem(FUEL_SLOT, null);
        inventory.setItem(OUTPUT_SLOT, null);

        // Notify session manager
        notifySessionClose(plugin, player, session.backpackId());
    }

    /**
     * Reconcile stored XP when closing based on output changes.
     */
    private double reconcileXpStoredOnClose(FurnaceStateCodec.State old, ItemStack output) {
        if (old == null)
            return 0.0;

        double xpStored = Math.max(0.0, old.xpStored);
        if (xpStored <= 0.0)
            return 0.0;

        if (ItemStacks.isAir(output))
            return 0.0;

        // If we can't reliably reconcile against the old output stack, keep the stored
        // XP
        if (ItemStacks.isAir(old.output) || !old.output.isSimilar(output))
            return xpStored;

        int oldAmt = old.output.getAmount();
        int newAmt = output.getAmount();
        if (oldAmt <= 0 || newAmt <= 0)
            return 0.0;
        if (oldAmt == newAmt)
            return xpStored;

        return xpStored * (newAmt / (double) oldAmt);
    }
}
