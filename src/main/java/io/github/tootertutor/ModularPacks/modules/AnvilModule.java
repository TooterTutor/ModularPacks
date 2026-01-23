package io.github.tootertutor.ModularPacks.modules;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.api.modules.AbstractModule;
import io.github.tootertutor.ModularPacks.config.ScreenType;
import net.kyori.adventure.text.Component;

/**
 * Anvil module that provides an anvil interface in backpacks.
 * Allows renaming items, combining items, and repairing with experience cost.
 */
public final class AnvilModule extends AbstractModule {

    // Anvil slots: 0 = left, 1 = right, 2 = output
    private static final int LEFT = 0;
    private static final int RIGHT = 1;
    private static final int OUTPUT = 2;

    public AnvilModule() {
        super("anvil", ScreenType.ANVIL, "Anvil Module");
    }

    @Override
    public void open(ModularPacksPlugin plugin, Player player, UUID backpackId, String backpackType, UUID moduleId) {
        if (plugin == null || player == null || !player.isOnline())
            return;

        // Real vanilla anvil UI (no block required when checkReachable=false)
        InventoryView view = MenuType.ANVIL.builder()
                .title(Component.text("Anvil Module"))
                .location(player.getLocation())
                .checkReachable(false)
                .build(player);
        if (view == null)
            return;

        Inventory top = view.getTopInventory();
        if (!(top instanceof AnvilInventory anvil) || top.getType() != InventoryType.ANVIL)
            return;

        // Load persisted left/right
        byte[] state = loadState(plugin, backpackId, backpackType, moduleId);
        ItemStack left = null;
        ItemStack right = null;

        if (state != null && state.length > 0) {
            ItemStack[] saved = loadItemStackArray(state);
            if (saved.length > 0)
                left = saved[0];
            if (saved.length > 1)
                right = saved[1];
        }

        anvil.setItem(LEFT, left);
        anvil.setItem(RIGHT, right);

        // Never seed output; vanilla computes it. Clear any stale output.
        if (anvil.getSize() > OUTPUT)
            anvil.setItem(OUTPUT, null);

        // Actually open the view for the player
        player.openInventory(view);

        // Verify the inventory actually opened (another plugin could have cancelled it)
        InventoryView current = player.getOpenInventory();
        if (current == null || current.getTopInventory() != view.getTopInventory()
                || current.getTopInventory().getType() != InventoryType.ANVIL) {
            return;
        }

        createSession(player, backpackId, backpackType, moduleId);
        player.updateInventory();
    }

    @Override
    public void updateResult(Inventory inv) {
        // Anvil result is computed by vanilla, no need to update
    }

    @Override
    public boolean isValidInventoryView(InventoryView view) {
        return view != null && view.getTopInventory() instanceof AnvilInventory;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    protected byte[] serializeState(Inventory inventory) {
        if (!(inventory instanceof AnvilInventory anvil))
            return new byte[0];

        ItemStack left = anvil.getItem(LEFT);
        ItemStack right = anvil.getItem(RIGHT);

        // Persist ONLY slots 0/1 (inputs), not the output
        return saveItemStackArray(new ItemStack[] { left, right });
    }

    @Override
    protected void deserializeState(Inventory inventory, byte[] stateBytes) {
        if (!(inventory instanceof AnvilInventory anvil))
            return;

        ItemStack[] saved = loadItemStackArray(stateBytes);
        if (saved.length > 0)
            anvil.setItem(LEFT, saved[0]);
        if (saved.length > 1)
            anvil.setItem(RIGHT, saved[1]);
    }

    @Override
    public void handleClose(ModularPacksPlugin plugin, Player player, Inventory inventory) {
        if (player == null || inventory == null)
            return;
        if (inventory.getType() != InventoryType.ANVIL)
            return;

        ModuleSession session = removeSession(player);
        if (session == null)
            return;

        if (!(inventory instanceof AnvilInventory anvil))
            return;

        // Save state
        byte[] stateBytes = serializeState(inventory);
        saveState(plugin, session.backpackId(), session.backpackType(), session.moduleId(), stateBytes);

        // ✅ CRITICAL: clear inputs BEFORE vanilla returns them to the player
        anvil.setItem(LEFT, null);
        anvil.setItem(RIGHT, null);
        if (anvil.getSize() > OUTPUT)
            anvil.setItem(OUTPUT, null);

        // Notify session manager
        notifySessionClose(plugin, player, session.backpackId());
    }
}
