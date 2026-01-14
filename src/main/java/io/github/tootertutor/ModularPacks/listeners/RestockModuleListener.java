package io.github.tootertutor.ModularPacks.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.config.ScreenType;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;
import io.github.tootertutor.ModularPacks.gui.ModuleScreenHolder;
import io.github.tootertutor.ModularPacks.item.Keys;
import io.github.tootertutor.ModularPacks.util.ItemStacks;
import io.github.tootertutor.ModularPacks.util.Text;
import net.kyori.adventure.text.Component;

public final class RestockModuleListener implements Listener {

    private static final int DEFAULT_THRESHOLD = 16;
    private static final int SLOT_DECREASE = 0;
    private static final int SLOT_CENTER = 2;
    private static final int SLOT_INCREASE = 4;

    private final ModularPacksPlugin plugin;

    public RestockModuleListener(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player player))
            return;

        Inventory top = e.getView() == null ? null : e.getView().getTopInventory();
        ModuleScreenHolder msh = top != null && top.getHolder() instanceof ModuleScreenHolder h ? h : null;
        if (msh == null || msh.screenType() != ScreenType.HOPPER)
            return;

        String moduleType = resolveModuleType(msh);
        if (moduleType == null || !moduleType.equalsIgnoreCase("Restock"))
            return;

        // Render threshold UI based on stored state; ignore whatever ScreenRouter
        // loaded.
        BackpackData data = plugin.repo().loadOrCreate(msh.backpackId(), msh.backpackType());
        int threshold = readStoredThreshold(data, msh.moduleId());
        top.clear();
        writeThreshold(top, threshold);
        renderIfNeeded(top);
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player))
            return;

        Inventory top = e.getView() == null ? null : e.getView().getTopInventory();
        ModuleScreenHolder msh = top != null && top.getHolder() instanceof ModuleScreenHolder h ? h : null;
        if (msh == null || msh.screenType() != ScreenType.HOPPER)
            return;

        String moduleType = resolveModuleType(msh);
        if (moduleType == null || !moduleType.equalsIgnoreCase("Restock"))
            return;

        // Fully lock this UI: no moving items in/out.
        e.setCancelled(true);

        int raw = e.getRawSlot();
        if (raw < 0 || raw >= top.getSize()) {
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
            return;
        }

        // Ensure layout exists even if the screen was opened with null state.
        renderIfNeeded(top);

        final int delta = (e.getClick() != null && e.getClick().isLeftClick())
                ? (e.isShiftClick() ? 8 : 1)
                : 0;

        if (raw == SLOT_DECREASE && delta > 0) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int t = readThreshold(top);
                writeThreshold(top, t - delta);
                player.updateInventory();
            });
            return;
        }

        if (raw == SLOT_INCREASE && delta > 0) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                int t = readThreshold(top);
                writeThreshold(top, t + delta);
                player.updateInventory();
            });
            return;
        }

        // Clicking the center item confirms and closes (close persists state).
        if (raw == SLOT_CENTER && (e.getClick() == ClickType.LEFT || e.getClick() == ClickType.SHIFT_LEFT
                || e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                renderIfNeeded(top);
                player.closeInventory();
            });
            return;
        }

        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView() == null ? null : e.getView().getTopInventory();
        if (!(top != null && top.getHolder() instanceof ModuleScreenHolder msh))
            return;
        if (msh.screenType() != ScreenType.HOPPER)
            return;

        String moduleType = resolveModuleType(msh);
        if (moduleType == null || !moduleType.equalsIgnoreCase("Restock"))
            return;

        // No dragging into this UI.
        int topSize = top.getSize();
        for (int raw : e.getRawSlots()) {
            if (raw >= 0 && raw < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private void renderIfNeeded(Inventory inv) {
        if (inv == null || inv.getSize() < 5)
            return;

        int threshold = readThreshold(inv);

        // Always render the arrows and filler; this also fixes any tampering.
        inv.setItem(SLOT_DECREASE, arrow("&cDecrease"));
        inv.setItem(SLOT_INCREASE, arrow("&aIncrease"));

        if (ItemStacks.isAir(inv.getItem(1)))
            inv.setItem(1, filler());
        if (ItemStacks.isAir(inv.getItem(3)))
            inv.setItem(3, filler());

        writeThreshold(inv, threshold);
    }

    private int readStoredThreshold(BackpackData data, java.util.UUID moduleId) {
        int fallback = plugin.getConfig().getInt("Upgrades.Restock.RestockThreshold", DEFAULT_THRESHOLD);
        fallback = Math.max(1, Math.min(64, fallback));
        if (data == null || moduleId == null)
            return fallback;

        byte[] bytes = data.moduleStates().get(moduleId);
        if (bytes == null || bytes.length == 0)
            return fallback;

        ItemStack[] arr;
        try {
            arr = ItemStackCodec.fromBytes(bytes);
        } catch (Exception ex) {
            return fallback;
        }
        if (arr == null || arr.length == 0)
            return fallback;

        // Prefer index 9 (merged state), fallback to slot 2 (old hopper-only).
        if (arr.length > 9 && ItemStacks.isNotAir(arr[9])) {
            int amt = arr[9].getAmount();
            return clampBetween1And64(amt);
        }
        if (arr.length > 2 && ItemStacks.isNotAir(arr[2])) {
            int amt = arr[2].getAmount();
            return clampBetween1And64(amt);
        }

        return fallback;
    }

    private int readThreshold(Inventory inv) {
        if (inv == null || inv.getSize() <= SLOT_CENTER)
            return DEFAULT_THRESHOLD;
        ItemStack center = inv.getItem(SLOT_CENTER);
        if (ItemStacks.isAir(center))
            return DEFAULT_THRESHOLD;
        int amt = center.getAmount();
        if (amt <= 0)
            return DEFAULT_THRESHOLD;
        return clampBetween1And64(amt);
    }

    private void writeThreshold(Inventory inv, int threshold) {
        if (inv == null || inv.getSize() <= SLOT_CENTER)
            return;

        int t = clampBetween1And64(threshold);

        ItemStack item = new ItemStack(Material.CHEST);
        item.setAmount(t);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.c("&eRestock Threshold &7(&f" + t + "&7)"));
            meta.lore(Text.lore(java.util.List.of(
                    "&8[&6ʟ-ᴄʟɪᴄᴋ&8]&7: &f-/+ 1",
                    "&8[&6ꜱʜɪꜰᴛ + ʟ-ᴄʟɪᴄᴋ&8]&7: &f-/+ 8",
                    "&7Click this item to &aconfirm")));
            item.setItemMeta(meta);
        }
        inv.setItem(SLOT_CENTER, item);
    }

    private ItemStack arrow(String name) {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.c(name));
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack filler() {
        Material mat = plugin.cfg().navBorderFiller();
        if (mat == null)
            mat = Material.GRAY_STAINED_GLASS_PANE;
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            it.setItemMeta(meta);
        }
        return it;
    }

    private String resolveModuleType(ModuleScreenHolder msh) {
        if (msh == null)
            return null;

        BackpackData data = plugin.repo().loadOrCreate(msh.backpackId(), msh.backpackType());
        if (data == null)
            return null;

        byte[] snap = data.installedSnapshots().get(msh.moduleId());
        if (snap == null || snap.length == 0)
            return null;

        ItemStack[] arr;
        try {
            arr = ItemStackCodec.fromBytes(snap);
        } catch (Exception ex) {
            return null;
        }
        if (arr.length == 0 || arr[0] == null || !arr[0].hasItemMeta())
            return null;

        ItemMeta meta = arr[0].getItemMeta();
        if (meta == null)
            return null;

        Keys keys = plugin.keys();
        return meta.getPersistentDataContainer().get(keys.MODULE_TYPE, PersistentDataType.STRING);
    }

    private static int clampBetween1And64(int threshold) {
        return Math.max(1, Math.min(64, threshold));
    }
}
