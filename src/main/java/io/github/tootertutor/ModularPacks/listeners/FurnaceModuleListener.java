package io.github.tootertutor.ModularPacks.listeners;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.item.Keys;
import io.github.tootertutor.ModularPacks.modules.FurnaceModuleLogic;
import io.github.tootertutor.ModularPacks.modules.FurnaceStateCodec;
import io.github.tootertutor.ModularPacks.util.ItemStacks;

public final class FurnaceModuleListener implements Listener {

    private final ModularPacksPlugin plugin;

    public FurnaceModuleListener(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isFurnaceTop(Player player, InventoryType type) {
        if (player == null)
            return false;
        var top = player.getOpenInventory().getTopInventory();
        if (top == null)
            return false;
        return top.getType() == type;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player))
            return;

        FurnaceModuleLogic.Session session = FurnaceModuleLogic.session(player);
        if (session == null)
            return;

        InventoryType topType = e.getView().getTopInventory().getType();
        switch (topType) {
            case FURNACE, BLAST_FURNACE, SMOKER -> {
            }
            default -> {
                return;
            }
        }

        // Award stored furnace XP when the player takes items from the output slot.
        // Vanilla stores XP in the block entity and drops it when output is removed; we
        // emulate this by storing XP in the module state and paying it out on output
        // removal.
        boolean clickedTop = e.getClickedInventory() != null
                && e.getClickedInventory().equals(e.getView().getTopInventory());
        int raw = e.getRawSlot();
        if (clickedTop && raw == 2) { // output slot
            ItemStack before = e.getCurrentItem();
            if (ItemStacks.isNotAir(before) && before.getAmount() > 0) {
                int beforeAmt = before.getAmount();
                UUID expectedModuleId = session.moduleId();
                UUID expectedBackpackId = session.backpackId();
                String expectedBackpackType = session.backpackType();

                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    FurnaceModuleLogic.Session s2 = FurnaceModuleLogic.session(player);
                    if (s2 == null)
                        return;
                    if (!expectedModuleId.equals(s2.moduleId()))
                        return;
                    if (!expectedBackpackId.equals(s2.backpackId()))
                        return;

                    var top = player.getOpenInventory() != null ? player.getOpenInventory().getTopInventory() : null;
                    if (top == null)
                        return;
                    InventoryType t = top.getType();
                    switch (t) {
                        case FURNACE, BLAST_FURNACE, SMOKER -> {
                        }
                        default -> {
                            return;
                        }
                    }

                    ItemStack after = top.getItem(2);
                    int afterAmt = ItemStacks.isAir(after) ? 0 : after.getAmount();
                    int removed = beforeAmt - afterAmt;
                    if (removed <= 0)
                        return;

                    BackpackData data = plugin.repo().loadOrCreate(expectedBackpackId, expectedBackpackType);
                    byte[] stateBytes = data.moduleStates().get(expectedModuleId);
                    FurnaceStateCodec.State state = FurnaceStateCodec.decode(stateBytes);

                    double xpStored = Math.max(0.0, state.xpStored);
                    if (xpStored <= 0.0)
                        return;

                    // Best-effort scaling if state output amount is slightly out of sync with
                    // the view (click timing vs engine tick persistence).
                    if (ItemStacks.isNotAir(state.output) && state.output.isSimilar(before)) {
                        int storedAmt = state.output.getAmount();
                        if (storedAmt > 0 && storedAmt != beforeAmt) {
                            xpStored *= (beforeAmt / (double) storedAmt);
                        }
                    }

                    // Proportional payout based on how many output items were removed.
                    double award = xpStored * (removed / (double) beforeAmt);
                    award = Math.max(0.0, Math.min(award, xpStored));
                    double remaining = xpStored - award;
                    if (afterAmt <= 0)
                        remaining = 0.0;

                    int xp = toVanillaXp(award);
                    if (xp > 0) {
                        try {
                            player.giveExp(xp);
                        } catch (Exception ignored) {
                        }
                    }

                    // Persist the reconciled output + XP so partial takes can't claim all XP.
                    state.input = top.getItem(0);
                    state.fuel = top.getItem(1);
                    state.output = after;
                    state.xpStored = remaining;

                    data.moduleStates().put(expectedModuleId, FurnaceStateCodec.encode(state));
                    plugin.repo().saveBackpack(data);
                });
            }
        }

        // Prevent putting backpacks into the module storage slots.
        ItemStack cursor = e.getCursor();
        if (isBackpack(cursor)) {
            if (raw >= 0 && raw < e.getView().getTopInventory().getSize()) {
                e.setCancelled(true);
                return;
            }
        }

        // Respect container rules for module storage too.
        int topSize = e.getView().getTopInventory().getSize();
        // raw/clickedTop declared above

        if (clickedTop && raw >= 0 && raw < topSize) {
            InventoryAction action = e.getAction();
            if (action == InventoryAction.PLACE_ALL
                    || action == InventoryAction.PLACE_ONE
                    || action == InventoryAction.PLACE_SOME
                    || action == InventoryAction.SWAP_WITH_CURSOR) {
                if (ItemStacks.isNotAir(cursor) && !plugin.cfg().isAllowedInBackpack(cursor)) {
                    e.setCancelled(true);
                    return;
                }
            }
            if (action == InventoryAction.HOTBAR_SWAP) {
                int btn = e.getHotbarButton();
                if (btn >= 0 && btn <= 8) {
                    ItemStack hotbar = player.getInventory().getItem(btn);
                    if (ItemStacks.isNotAir(hotbar) && !plugin.cfg().isAllowedInBackpack(hotbar)) {
                        e.setCancelled(true);
                        return;
                    }
                }
            }
        }

        if (!clickedTop && e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            ItemStack moving = e.getCurrentItem();
            if (ItemStacks.isNotAir(moving) && !plugin.cfg().isAllowedInBackpack(moving)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player))
            return;
        if (!FurnaceModuleLogic.hasSession(player))
            return;

        InventoryType topType = e.getView().getTopInventory().getType();
        switch (topType) {
            case FURNACE, BLAST_FURNACE, SMOKER -> {
            }
            default -> {
                return;
            }
        }

        ItemStack cursor = e.getOldCursor();
        if (ItemStacks.isAir(cursor))
            return;
        if (plugin.cfg().isAllowedInBackpack(cursor))
            return;

        int topSize = e.getView().getTopInventory().getSize();
        for (int raw : e.getRawSlots()) {
            if (raw >= 0 && raw < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player))
            return;
        if (!FurnaceModuleLogic.hasSession(player))
            return;

        InventoryType type = e.getInventory().getType();
        switch (type) {
            case FURNACE, BLAST_FURNACE, SMOKER -> {
            }
            default -> {
                return;
            }
        }

        FurnaceModuleLogic.handleClose(plugin, player, e.getInventory());
    }

    private static int toVanillaXp(double xp) {
        if (xp <= 0.0)
            return 0;

        int whole = (int) Math.floor(xp);
        double frac = xp - whole;
        if (frac > 0.0 && java.util.concurrent.ThreadLocalRandom.current().nextDouble() < frac) {
            whole++;
        }
        return Math.max(0, whole);
    }

    private boolean isBackpack(ItemStack item) {
        if (item == null || !item.hasItemMeta())
            return false;
        Keys keys = plugin.keys();
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(keys.BACKPACK_ID, PersistentDataType.STRING)
                && pdc.has(keys.BACKPACK_TYPE, PersistentDataType.STRING);
    }
}
