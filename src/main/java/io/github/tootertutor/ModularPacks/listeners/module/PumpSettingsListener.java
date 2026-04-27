package io.github.tootertutor.ModularPacks.listeners.module;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.config.Placeholders;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;
import io.github.tootertutor.ModularPacks.gui.PumpSettingsHolder;
import io.github.tootertutor.ModularPacks.item.Keys;
import io.github.tootertutor.ModularPacks.util.ItemStacks;
import io.github.tootertutor.ModularPacks.util.Text;
import net.kyori.adventure.text.Component;

public final class PumpSettingsListener implements Listener {

    private static final int SLOT_FROM_PLAYER = 1;
    private static final int SLOT_MODE = 2;
    private static final int SLOT_TO_PLAYER = 3;

    private final ModularPacksPlugin plugin;

    public PumpSettingsListener(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player player))
            return;
        Inventory top = e.getView() == null ? null : e.getView().getTopInventory();
        if (!(top != null && top.getHolder() instanceof PumpSettingsHolder holder))
            return;

        render(top, holder);
        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player))
            return;
        Inventory top = e.getView() == null ? null : e.getView().getTopInventory();
        if (!(top != null && top.getHolder() instanceof PumpSettingsHolder holder))
            return;

        int raw = e.getRawSlot();
        if (raw < 0 || raw >= top.getSize())
            return;

        e.setCancelled(true);

        ItemStack moduleItem = resolveModuleSnapshotItem(holder);
        if (ItemStacks.isAir(moduleItem) || !moduleItem.hasItemMeta()) {
            player.closeInventory();
            return;
        }

        boolean changed = false;

        if (raw == SLOT_FROM_PLAYER) {
            changed = setPumpMode(moduleItem, "DEPOSIT");
        } else if (raw == SLOT_TO_PLAYER) {
            changed = setPumpMode(moduleItem, "WITHDRAW");
        } else if (raw == SLOT_MODE) {
            if (isExpPump(holder.moduleType())) {
                changed = toggleExpPumpMending(moduleItem);
            }
        }

        if (changed) {
            persistModuleSnapshot(holder, moduleItem);
            render(top, holder);
        }

        Bukkit.getScheduler().runTask(plugin, player::updateInventory);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent e) {
        Inventory top = e.getView() == null ? null : e.getView().getTopInventory();
        if (!(top != null && top.getHolder() instanceof PumpSettingsHolder))
            return;

        int topSize = top.getSize();
        for (int raw : e.getRawSlots()) {
            if (raw >= 0 && raw < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }

    private void render(Inventory inv, PumpSettingsHolder holder) {
        if (inv == null || holder == null || inv.getSize() < 5)
            return;

        ItemStack moduleItem = resolveModuleSnapshotItem(holder);
        String pumpMode = resolvePumpMode(moduleItem, holder.moduleType());
        boolean toPlayer = "WITHDRAW".equals(pumpMode);
        boolean expPump = isExpPump(holder.moduleType());
        boolean mendingEnabled = expPump && isExpPumpMendingEnabled(moduleItem);

        inv.clear();
        inv.setItem(0, filler());
        inv.setItem(4, filler());

        inv.setItem(SLOT_FROM_PLAYER, directionButton(
                "&eFrom Player",
                "Move resources from player inventory/XP to the tank.",
                !toPlayer));
        inv.setItem(SLOT_TO_PLAYER, directionButton(
                "&eTo Player",
                "Move resources from the tank to player inventory/XP.",
                toPlayer));

        if (expPump) {
            inv.setItem(SLOT_MODE, modeButton(
                    mendingEnabled ? "&eMode: Mending First" : "&eMode: XP First",
                    mendingEnabled
                            ? List.of("XP repairs equipped Mending items first.", "Remaining XP goes to player.")
                            : List.of("XP goes directly to player levels.", "No auto-repair is applied."),
                    true,
                    "&7Click to toggle mode."));
        } else {
            inv.setItem(SLOT_MODE, modeButton(
                    "&eMode: Standard",
                    List.of("Fluid pump uses standard bucket transfer behavior."),
                    false,
                    "&8No alternate mode."));
        }
    }

    private boolean setPumpMode(ItemStack moduleItem, String mode) {
        if (moduleItem == null || !moduleItem.hasItemMeta() || mode == null)
            return false;
        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return false;

        Keys keys = plugin.keys();
        String current = meta.getPersistentDataContainer().get(keys.MODULE_PUMP_MODE, PersistentDataType.STRING);
        String normalizedCurrent = normalizePumpMode(current);
        String normalizedNew = normalizePumpMode(mode);
        if (normalizedCurrent.equals(normalizedNew))
            return false;

        meta.getPersistentDataContainer().set(keys.MODULE_PUMP_MODE, PersistentDataType.STRING, normalizedNew);
        moduleItem.setItemMeta(meta);
        applyModuleLore(moduleItem);
        return true;
    }

    private boolean toggleExpPumpMending(ItemStack moduleItem) {
        if (moduleItem == null || !moduleItem.hasItemMeta())
            return false;
        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return false;

        Keys keys = plugin.keys();
        var pdc = meta.getPersistentDataContainer();

        boolean fallback = plugin.getConfig().getBoolean("Upgrades.ExpPump.MendEquippedItems", false);
        Byte stored = pdc.get(keys.MODULE_EXP_PUMP_MENDING, PersistentDataType.BYTE);
        boolean current = stored == null ? fallback : stored == 1;
        boolean next = !current;

        pdc.set(keys.MODULE_EXP_PUMP_MENDING, PersistentDataType.BYTE, (byte) (next ? 1 : 0));
        moduleItem.setItemMeta(meta);
        applyModuleLore(moduleItem);
        return true;
    }

    private String resolvePumpMode(ItemStack moduleItem, String moduleType) {
        String fallback = plugin.getConfig().getString("Upgrades." + moduleType + ".Mode", "Deposit");
        if (moduleItem == null || !moduleItem.hasItemMeta())
            return normalizePumpMode(fallback);

        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return normalizePumpMode(fallback);

        String raw = meta.getPersistentDataContainer().get(plugin.keys().MODULE_PUMP_MODE, PersistentDataType.STRING);
        return normalizePumpMode(raw == null || raw.isBlank() ? fallback : raw);
    }

    private boolean isExpPumpMendingEnabled(ItemStack moduleItem) {
        boolean fallback = plugin.getConfig().getBoolean("Upgrades.ExpPump.MendEquippedItems", false);
        if (moduleItem == null || !moduleItem.hasItemMeta())
            return fallback;
        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return fallback;

        Byte b = meta.getPersistentDataContainer().get(plugin.keys().MODULE_EXP_PUMP_MENDING, PersistentDataType.BYTE);
        if (b == null)
            return fallback;
        return b == 1;
    }

    private void applyModuleLore(ItemStack moduleItem) {
        if (moduleItem == null || !moduleItem.hasItemMeta())
            return;

        String type = resolveModuleType(moduleItem);
        if (type == null)
            return;

        var def = plugin.cfg().findUpgrade(type);
        if (def == null)
            return;

        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return;

        meta.displayName(Text.c(Placeholders.expandText(plugin, def, moduleItem, def.displayName())));
        List<String> expanded = Placeholders.expandLore(plugin, def, moduleItem, def.lore());
        meta.lore(Text.lore(expanded));
        moduleItem.setItemMeta(meta);
    }

    private ItemStack resolveModuleSnapshotItem(PumpSettingsHolder holder) {
        if (holder == null)
            return null;

        BackpackData data = plugin.repo().loadOrCreate(holder.backpackId(), holder.backpackType());
        if (data == null)
            return null;

        byte[] snap = data.installedSnapshots().get(holder.moduleId());
        if (snap == null || snap.length == 0)
            return null;

        try {
            ItemStack[] arr = ItemStackCodec.fromBytes(snap);
            if (arr.length == 0 || ItemStacks.isAir(arr[0]))
                return null;
            return arr[0].clone();
        } catch (Exception ex) {
            return null;
        }
    }

    private void persistModuleSnapshot(PumpSettingsHolder holder, ItemStack moduleItem) {
        if (holder == null || moduleItem == null)
            return;

        BackpackData data = plugin.repo().loadOrCreate(holder.backpackId(), holder.backpackType());
        if (data == null)
            return;

        data.installedSnapshots().put(holder.moduleId(),
                ItemStackCodec.toBytes(new ItemStack[] { moduleItem.clone() }));
        plugin.repo().saveBackpack(data);
        plugin.sessions().refreshLinkedBackpacksThrottled(holder.backpackId(), data);
    }

    private String resolveModuleType(ItemStack moduleItem) {
        if (moduleItem == null || !moduleItem.hasItemMeta())
            return null;
        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return null;
        return meta.getPersistentDataContainer().get(plugin.keys().MODULE_TYPE, PersistentDataType.STRING);
    }

    private boolean isExpPump(String moduleType) {
        return moduleType != null && moduleType.equalsIgnoreCase("ExpPump");
    }

    private String normalizePumpMode(String raw) {
        if (raw == null)
            return "DEPOSIT";
        String s = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (s.contains("WITHDRAW") || s.contains("OUT"))
            return "WITHDRAW";
        return "DEPOSIT";
    }

    private ItemStack directionButton(String title, String description, boolean selected) {
        ItemStack it = new ItemStack(selected ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.c(title + (selected ? " &a(Selected)" : " &7(Click to select)")));
            meta.lore(Text.lore(List.of("&7" + description)));
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack modeButton(String title, List<String> lines, boolean enabled, String actionHint) {
        Material icon = enabled ? Material.ENCHANTED_BOOK : Material.BOOK;
        ItemStack it = new ItemStack(icon);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.c(title));
            java.util.ArrayList<String> lore = new java.util.ArrayList<>();
            for (String line : lines) {
                lore.add("&7" + line);
            }
            lore.add(actionHint);
            meta.lore(Text.lore(lore));
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
}
