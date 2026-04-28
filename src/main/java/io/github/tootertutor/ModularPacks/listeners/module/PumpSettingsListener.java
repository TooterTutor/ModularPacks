package io.github.tootertutor.ModularPacks.listeners.module;

import java.util.List;

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
import io.github.tootertutor.ModularPacks.config.Placeholders;
import io.github.tootertutor.ModularPacks.config.UpgradeDef;
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
    private static final int MAX_TARGET_LEVEL = 100;

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

        if (isExpPump(holder.moduleType())) {
            if (raw == SLOT_FROM_PLAYER) {
                changed = toggleExpPumpMending(moduleItem);
            } else if (raw == SLOT_MODE) {
                changed = cycleExpPumpMode(moduleItem, e.getClick());
            } else if (raw == SLOT_TO_PLAYER) {
                changed = adjustExpPumpTargetLevel(moduleItem, e.getClick());
            }
        } else if (raw == SLOT_FROM_PLAYER) {
            changed = setPumpMode(moduleItem, "DEPOSIT");
        } else if (raw == SLOT_TO_PLAYER) {
            changed = setPumpMode(moduleItem, "WITHDRAW");
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
        var def = resolveUpgradeDef(moduleItem);

        inv.clear();
        inv.setItem(0, filler());
        inv.setItem(4, filler());

        if (expPump) {
            inv.setItem(SLOT_FROM_PLAYER, modeButton(
                    plugin.lang().get(mendingEnabled ? "expPumpMending.enabled" : "expPumpMending.disabled",
                            mendingEnabled ? "&eMending: Enabled" : "&eMending: Disabled"),
                    List.of(plugin.lang().get(
                            mendingEnabled ? "expPumpMending.enabledLore" : "expPumpMending.disabledLore",
                            mendingEnabled ? "Withdrawn tank XP can repair equipped Mending items."
                                    : "Tank XP will not auto-repair equipped items.")),
                    mendingEnabled,
                    List.of(Placeholders.expandLangText(plugin, def, moduleItem, "pumpSettingsActionHint.toggleMending",
                            "&7Click to toggle mending."))));
            inv.setItem(SLOT_MODE, modeButton(
                    expPumpModeTitle(def, moduleItem, pumpMode),
                    expPumpModeLore(def, moduleItem, pumpMode),
                    true,
                    Placeholders.expandLangList(plugin, def, moduleItem,
                            "pumpSettingsActionHint.modeCycle",
                            List.of("&8[&6ʟ-ᴄʟɪᴄᴋ&8]&7 Next Mode",
                                    "&8[&6ʀ-ᴄʟɪᴄᴋ&8]&7 Previous Mode"))));
            inv.setItem(SLOT_TO_PLAYER, levelButton(def, moduleItem));
        } else {
            inv.setItem(SLOT_FROM_PLAYER, directionButton(
                    plugin.lang().get("pumpDirection.fromPlayer.title", "&eFrom Player"),
                    plugin.lang().get("pumpDirection.fromPlayer.description",
                            "Move resources from player inventory/XP to the tank."),
                    !toPlayer));
            inv.setItem(SLOT_MODE, modeButton(
                    plugin.lang().get("pumpMode.standardTitle", "&eMode: Standard"),
                    List.of(plugin.lang().get("pumpMode.standardLore",
                            "Fluid pump uses standard bucket transfer behavior.")),
                    false,
                    List.of(Placeholders.expandLangText(plugin, def, moduleItem, "pumpSettingsActionHint.standard",
                            "&8No alternate mode."))));
            inv.setItem(SLOT_TO_PLAYER, directionButton(
                    plugin.lang().get("pumpDirection.toPlayer.title", "&eTo Player"),
                    plugin.lang().get("pumpDirection.toPlayer.description",
                            "Move resources from the tank to player inventory/XP."),
                    toPlayer));
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
        String normalizedCurrent = normalizePumpMode(current, false);
        String normalizedNew = normalizePumpMode(mode, false);
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

    private boolean cycleExpPumpMode(ItemStack moduleItem, ClickType clickType) {
        if (moduleItem == null || !moduleItem.hasItemMeta())
            return false;
        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return false;

        Keys keys = plugin.keys();
        var pdc = meta.getPersistentDataContainer();

        ExpPumpMode current = ExpPumpMode.fromString(
                pdc.get(keys.MODULE_PUMP_MODE, PersistentDataType.STRING),
                plugin.getConfig().getString("Upgrades.ExpPump.Mode", "Deposit"));
        ExpPumpMode next = isPreviousClick(clickType) ? current.previous() : current.next();
        if (current == next)
            return false;

        pdc.set(keys.MODULE_PUMP_MODE, PersistentDataType.STRING, next.name());
        moduleItem.setItemMeta(meta);
        applyModuleLore(moduleItem);
        return true;
    }

    private boolean isPreviousClick(ClickType clickType) {
        if (clickType == null)
            return false;
        return switch (clickType) {
            case RIGHT, SHIFT_RIGHT -> true;
            default -> false;
        };
    }

    private boolean adjustExpPumpTargetLevel(ItemStack moduleItem, ClickType clickType) {
        if (moduleItem == null || !moduleItem.hasItemMeta())
            return false;
        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return false;

        int delta = switch (clickType) {
            case SHIFT_LEFT -> 5;
            case SHIFT_RIGHT -> -5;
            case RIGHT -> -1;
            default -> 1;
        };
        if (delta == 0)
            return false;

        Keys keys = plugin.keys();
        var pdc = meta.getPersistentDataContainer();
        int current = resolveExpPumpTargetLevel(moduleItem);
        int next = clampExpPumpTargetLevel(current + delta);
        if (current == next)
            return false;

        pdc.set(keys.MODULE_EXP_PUMP_TARGET_LEVEL, PersistentDataType.INTEGER, next);
        moduleItem.setItemMeta(meta);
        applyModuleLore(moduleItem);
        return true;
    }

    private String resolvePumpMode(ItemStack moduleItem, String moduleType) {
        String fallback = plugin.getConfig().getString("Upgrades." + moduleType + ".Mode", "Deposit");
        if (moduleItem == null || !moduleItem.hasItemMeta())
            return normalizePumpMode(fallback, isExpPump(moduleType));

        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return normalizePumpMode(fallback, isExpPump(moduleType));

        String raw = meta.getPersistentDataContainer().get(plugin.keys().MODULE_PUMP_MODE, PersistentDataType.STRING);
        return normalizePumpMode(raw == null || raw.isBlank() ? fallback : raw, isExpPump(moduleType));
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

    private UpgradeDef resolveUpgradeDef(ItemStack moduleItem) {
        String type = resolveModuleType(moduleItem);
        if (type == null)
            return null;
        return plugin.cfg().findUpgrade(type);
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

    private String normalizePumpMode(String raw, boolean allowKeepLevel) {
        if (raw == null)
            return "DEPOSIT";
        String s = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (allowKeepLevel && (s.contains("KEEP") || s.contains("LEVEL")))
            return "KEEP_LEVEL";
        if (s.contains("WITHDRAW") || s.contains("OUT"))
            return "WITHDRAW";
        return "DEPOSIT";
    }

    private int resolveExpPumpTargetLevel(ItemStack moduleItem) {
        int fallback = plugin.getConfig().getInt("Upgrades.ExpPump.TargetLevel", 30);
        if (moduleItem == null || !moduleItem.hasItemMeta())
            return clampExpPumpTargetLevel(fallback);

        ItemMeta meta = moduleItem.getItemMeta();
        if (meta == null)
            return clampExpPumpTargetLevel(fallback);

        Integer stored = meta.getPersistentDataContainer().get(plugin.keys().MODULE_EXP_PUMP_TARGET_LEVEL,
                PersistentDataType.INTEGER);
        return clampExpPumpTargetLevel(stored == null ? fallback : stored.intValue());
    }

    private int clampExpPumpTargetLevel(int level) {
        return Math.max(0, Math.min(MAX_TARGET_LEVEL, level));
    }

    private String expPumpModeTitle(UpgradeDef def, ItemStack moduleItem,
            String pumpMode) {
        return switch (pumpMode) {
            case "WITHDRAW" -> Placeholders.expandLangText(plugin, def, moduleItem, "pumpMode.withdraw",
                    "&7Mode: &fWithdraw");
            case "KEEP_LEVEL" -> Placeholders.expandLangText(plugin, def, moduleItem, "pumpMode.keepLevel",
                    "&7Mode: &fKeep Player at Level {level}");
            default -> Placeholders.expandLangText(plugin, def, moduleItem, "pumpMode.deposit",
                    "&7Mode: &fDeposit");
        };
    }

    private List<String> expPumpModeLore(UpgradeDef def, ItemStack moduleItem, String pumpMode) {
        return switch (pumpMode) {
            case "WITHDRAW" -> Placeholders.expandLangList(plugin, def, moduleItem,
                    "expPumpModeLore.withdraw",
                    List.of("Pull XP from the tank and give it to the player.",
                            "Mending uses withdrawn XP first when enabled."));
            case "KEEP_LEVEL" -> Placeholders.expandLangList(plugin, def, moduleItem,
                    "expPumpModeLore.keepLevel",
                    List.of("Keep the player at level {level}.",
                            "Excess XP is stored, missing XP is restored from the tank."));
            default -> Placeholders.expandLangList(plugin, def, moduleItem,
                    "expPumpModeLore.deposit",
                    List.of("Move player XP into the tank.",
                            "Good for banking levels before they are lost."));
        };
    }

    private ItemStack levelButton(UpgradeDef def, ItemStack moduleItem) {
        ItemStack it = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.c(Placeholders.expandLangText(plugin, def, moduleItem,
                    "expPumpLevels.targetLevel", "&6Target Level: &f{level}")));
            meta.lore(Text.lore(List.of(
                    Placeholders.expandLangText(plugin, def, moduleItem, "expPumpLevels.levelUp1",
                            "&8[&6Left-click&8]&7 +1 Level"),
                    Placeholders.expandLangText(plugin, def, moduleItem, "expPumpLevels.levelDown1",
                            "&8[&6Right-click&8]&7 -1 Level"),
                    Placeholders.expandLangText(plugin, def, moduleItem, "expPumpLevels.levelUp5",
                            "&8[&6ꜱʜɪꜰᴛ + ʟ-ᴄʟɪᴄᴋ&8]&7 +5 Levels"),
                    Placeholders.expandLangText(plugin, def, moduleItem, "expPumpLevels.levelDown5",
                            "&8[&6ꜱʜɪꜰᴛ + ʀ-ᴄʟɪᴄᴋ&8]&7 -5 Levels"))));
            it.setItemMeta(meta);
        }
        return it;
    }

    private enum ExpPumpMode {
        DEPOSIT,
        WITHDRAW,
        KEEP_LEVEL;

        static ExpPumpMode fromString(String raw, String fallbackRaw) {
            ExpPumpMode parsed = parse(raw);
            if (parsed != null)
                return parsed;
            parsed = parse(fallbackRaw);
            if (parsed != null)
                return parsed;
            return DEPOSIT;
        }

        private static ExpPumpMode parse(String raw) {
            if (raw == null || raw.isBlank())
                return null;
            String s = raw.trim().toUpperCase(java.util.Locale.ROOT);
            if (s.contains("KEEP") || s.contains("LEVEL"))
                return KEEP_LEVEL;
            if (s.contains("WITHDRAW") || s.contains("OUT"))
                return WITHDRAW;
            if (s.contains("DEPOSIT") || s.contains("IN"))
                return DEPOSIT;
            return null;
        }

        ExpPumpMode next() {
            return switch (this) {
                case DEPOSIT -> WITHDRAW;
                case WITHDRAW -> KEEP_LEVEL;
                case KEEP_LEVEL -> DEPOSIT;
            };
        }

        ExpPumpMode previous() {
            return switch (this) {
                case DEPOSIT -> KEEP_LEVEL;
                case WITHDRAW -> DEPOSIT;
                case KEEP_LEVEL -> WITHDRAW;
            };
        }
    }

    private ItemStack directionButton(String title, String description, boolean selected) {
        ItemStack it = new ItemStack(selected ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            String suffix = selected
                    ? plugin.lang().get("pumpMode.selected", "&a(Selected)")
                    : plugin.lang().get("pumpMode.unselected", "&7(Click to select)");
            meta.displayName(Text.c(title + " " + suffix));
            meta.lore(Text.lore(List.of("&7" + description)));
            it.setItemMeta(meta);
        }
        return it;
    }

    private ItemStack modeButton(String title, List<String> lines, boolean enabled, List<String> actionHints) {
        Material icon = enabled ? Material.ENCHANTED_BOOK : Material.BOOK;
        ItemStack it = new ItemStack(icon);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.c(title));
            java.util.ArrayList<String> lore = new java.util.ArrayList<>();
            for (String line : lines) {
                lore.add("&7" + line);
            }
            for (String hint : actionHints) {
                lore.add(hint);
            }
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
