package io.github.tootertutor.ModularPacks.listeners.module;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.modules.crafting.CraftingModule;
import io.github.tootertutor.ModularPacks.modules.crafting.CraftingModuleLogic;
import io.github.tootertutor.ModularPacks.util.ItemStacks;

public final class CraftingModuleListener implements Listener {

    private static final int RESULT_SLOT = 0;
    private static final int MATRIX_FIRST_SLOT = 1;
    private static final int MATRIX_LAST_SLOT = 9;

    private final ModularPacksPlugin plugin;
    private final CraftingModule module;

    public CraftingModuleListener(ModularPacksPlugin plugin, CraftingModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player))
            return;
        if (!module.hasSession(player))
            return;

        Inventory top = e.getView().getTopInventory();
        if (top == null || top.getSize() < 10)
            return;

        // Block disallowed items from entering the crafting module's persistent
        // storage.
        boolean clickedTop = e.getClickedInventory() != null && e.getClickedInventory().equals(top);
        int raw = e.getRawSlot();
        if (clickedTop && raw >= 0 && raw < top.getSize()) {
            InventoryAction action = e.getAction();
            if (action == InventoryAction.PLACE_ALL
                    || action == InventoryAction.PLACE_ONE
                    || action == InventoryAction.PLACE_SOME
                    || action == InventoryAction.SWAP_WITH_CURSOR) {
                ItemStack cursor = e.getCursor();
                if (ItemStacks.isNotAir(cursor) && !plugin.cfg().isAllowedInBackpack(cursor)) {
                    e.setCancelled(true);
                    Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                    return;
                }
            }
            if (action == InventoryAction.HOTBAR_SWAP) {
                int btn = e.getHotbarButton();
                if (btn >= 0 && btn <= 8) {
                    ItemStack hotbar = player.getInventory().getItem(btn);
                    if (ItemStacks.isNotAir(hotbar) && !plugin.cfg().isAllowedInBackpack(hotbar)) {
                        e.setCancelled(true);
                        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                        return;
                    }
                }
            }
        }

        if (module.isAutocraftingSession(player)) {
            if (handleAutocraftingMatrixClick(e, player, top)) {
                return;
            }

            if (!clickedTop && e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                e.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                return;
            }
        }

        // Autocrafting module uses output slot clicks to adjust desired batch amount.
        if (module.handleAutocraftingResultClick(e, player)) {
            return;
        }

        // Our custom crafting result handling (dynamic outputs, anti-dupe).
        if (CraftingModuleLogic.handleResultClick(plugin.recipes(), e, player)) {
            return;
        }

        // Any matrix change should refresh output next tick (covers recipe book
        // auto-fill too).
        if (raw >= 0 && raw < top.getSize()) {
            Bukkit.getScheduler().runTask(plugin,
                    () -> CraftingModuleLogic.updateResult(plugin.recipes(), player, top));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player))
            return;
        if (!module.hasSession(player))
            return;

        Inventory top = e.getView().getTopInventory();
        if (top == null || top.getSize() < 10)
            return;

        // Prevent dragging into the output slot.
        if (e.getRawSlots().contains(0)) {
            e.setCancelled(true);
            return;
        }

        if (module.isAutocraftingSession(player) && hasAutocraftingMatrixSlot(e.getRawSlots(), top.getSize())) {
            e.setCancelled(true);

            ItemStack cursor = e.getOldCursor();
            if (ItemStacks.isNotAir(cursor)) {
                if (!plugin.cfg().isAllowedInBackpack(cursor)) {
                    Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                    return;
                }

                ItemStack ghost = ghostCopy(cursor);
                for (int raw : e.getRawSlots()) {
                    if (isAutocraftingMatrixSlot(raw, top.getSize())) {
                        top.setItem(raw, ghost.clone());
                    }
                }
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                CraftingModuleLogic.updateResult(plugin.recipes(), player, top);
                player.updateInventory();
            });
            return;
        }

        ItemStack cursor = e.getOldCursor();
        if (ItemStacks.isNotAir(cursor) && !plugin.cfg().isAllowedInBackpack(cursor)) {
            int topSize = top.getSize();
            for (int raw : e.getRawSlots()) {
                if (raw >= 0 && raw < topSize) {
                    e.setCancelled(true);
                    Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                    return;
                }
            }
        }

        // Any drag into the top inventory should refresh output next tick.
        for (int raw : e.getRawSlots()) {
            if (raw >= 0 && raw < top.getSize()) {
                Bukkit.getScheduler().runTask(plugin,
                        () -> CraftingModuleLogic.updateResult(plugin.recipes(), player, top));
                return;
            }
        }
    }

    @EventHandler
    public void onPrepare(PrepareItemCraftEvent e) {
        if (e == null || e.getView() == null)
            return;
        if (!(e.getView().getPlayer() instanceof Player player))
            return;
        if (!module.hasSession(player))
            return;

        Inventory top = e.getInventory();
        if (top == null || top.getSize() < 10)
            return;

        // Ensure result reflects dynamic recipes when recipe book fills the grid.
        CraftingModuleLogic.updateResult(plugin.recipes(), player, top);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player))
            return;
        if (!module.hasSession(player))
            return;

        // e.getInventory() is the top inventory being closed for crafting views.
        module.handleClose(plugin, player, e.getInventory());
    }

    private boolean handleAutocraftingMatrixClick(InventoryClickEvent e, Player player, Inventory top) {
        int raw = e.getRawSlot();
        if (raw == RESULT_SLOT) {
            return false;
        }

        if (!isAutocraftingMatrixSlot(raw, top.getSize())) {
            return false;
        }

        e.setCancelled(true);

        InventoryAction action = e.getAction();
        if (action == InventoryAction.NOTHING) {
            return true;
        }

        ItemStack replacement = null;
        boolean clearSlot = false;

        if (action == InventoryAction.HOTBAR_SWAP) {
            int button = e.getHotbarButton();
            if (button >= 0 && button <= 8) {
                ItemStack hotbar = player.getInventory().getItem(button);
                if (ItemStacks.isNotAir(hotbar)) {
                    if (!plugin.cfg().isAllowedInBackpack(hotbar)) {
                        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                        return true;
                    }
                    replacement = ghostCopy(hotbar);
                } else {
                    clearSlot = true;
                }
            }
        } else if (action == InventoryAction.PLACE_ALL
                || action == InventoryAction.PLACE_ONE
                || action == InventoryAction.PLACE_SOME
                || action == InventoryAction.SWAP_WITH_CURSOR) {
            ItemStack cursor = e.getCursor();
            if (ItemStacks.isNotAir(cursor)) {
                if (!plugin.cfg().isAllowedInBackpack(cursor)) {
                    Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                    return true;
                }
                replacement = ghostCopy(cursor);
            } else {
                clearSlot = true;
            }
        } else if (action == InventoryAction.PICKUP_ALL
                || action == InventoryAction.PICKUP_HALF
                || action == InventoryAction.PICKUP_ONE
                || action == InventoryAction.PICKUP_SOME
                || action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.DROP_ALL_SLOT
                || action == InventoryAction.DROP_ONE_SLOT
                || e.getClick() == ClickType.MIDDLE) {
            clearSlot = true;
        }

        if (replacement != null) {
            top.setItem(raw, replacement);
        } else if (clearSlot) {
            top.setItem(raw, null);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            CraftingModuleLogic.updateResult(plugin.recipes(), player, top);
            player.updateInventory();
        });
        return true;
    }

    private boolean hasAutocraftingMatrixSlot(java.util.Set<Integer> rawSlots, int topSize) {
        for (int raw : rawSlots) {
            if (isAutocraftingMatrixSlot(raw, topSize)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAutocraftingMatrixSlot(int rawSlot, int topSize) {
        return rawSlot >= MATRIX_FIRST_SLOT && rawSlot <= MATRIX_LAST_SLOT && rawSlot < topSize;
    }

    private ItemStack ghostCopy(ItemStack stack) {
        ItemStack ghost = stack.clone();
        ghost.setAmount(1);
        return ghost;
    }
}
