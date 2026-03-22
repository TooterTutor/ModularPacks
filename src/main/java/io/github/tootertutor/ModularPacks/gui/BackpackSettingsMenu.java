package io.github.tootertutor.ModularPacks.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.PlacedBackpack;
import io.github.tootertutor.ModularPacks.item.BackpackItems;
import io.github.tootertutor.ModularPacks.item.CustomModelDataUtil;
import io.github.tootertutor.ModularPacks.listeners.backpack.BackpackSharingHost;
import io.github.tootertutor.ModularPacks.listeners.backpack.BackpackSharingJoiner;
import io.github.tootertutor.ModularPacks.util.BackpackColorTints;
import io.github.tootertutor.ModularPacks.util.Text;
import net.wesjd.anvilgui.AnvilGUI;

/**
 * Settings menu for backpacks.
 * Provides options to:
 * - Toggle Host/Join/Private modes
 * - Change backpack color (via CustomModelData tints)
 * - Set custom backpack name
 */
public final class BackpackSettingsMenu {

    private static final int SLOT_SHARING = 11;
    private static final int SLOT_COLORS = 13;
    private static final int SLOT_NAME = 15;
    private static final int SLOT_BACK = 22;
    private static final String[] COLOR_GROUP_NAMES = {
            "Body",
            "Stripes",
            "Main Pocket",
            "Side Pockets",
            "Towel"
    };

    private final ModularPacksPlugin plugin;
    private final BackpackItems backpackItems;
    private final BackpackSharingHost sharingHost;
    private final BackpackSharingJoiner sharingJoiner;

    public BackpackSettingsMenu(ModularPacksPlugin plugin,
            BackpackSharingHost sharingHost, BackpackSharingJoiner sharingJoiner) {
        this.plugin = plugin;
        this.backpackItems = new BackpackItems(plugin);
        this.sharingHost = sharingHost;
        this.sharingJoiner = sharingJoiner;
    }

    /**
     * Open the settings menu for a backpack.
     */
    public void openSettingsMenu(Player player, BackpackMenuHolder holder) {
        BackpackData data = holder.data();
        SettingsMenuHolder settingsHolder = new SettingsMenuHolder(holder.backpackId(), holder);
        Inventory inv = plugin.getServer().createInventory(settingsHolder, 27, Text.c("&8Backpack Settings"));

        // Get the backpack item from player's inventory
        ItemStack backpackItem = resolveBackpackItemForSettings(player, holder);
        if (backpackItem == null) {
            player.sendMessage(Text.c("&cCould not find backpack item in inventory."));
            return;
        }

        // Decorative frame for cleaner visual grouping
        ItemStack frame = createButton("&8", Material.GRAY_STAINED_GLASS_PANE, List.of("&8Backpack Settings"));
        int[] frameSlots = new int[] { 0, 1, 2, 3, 5, 6, 7, 8, 9, 10, 12, 14, 16, 17, 18, 19, 20, 21, 23, 24,
                25, 26 };
        for (int frameSlot : frameSlots) {
            inv.setItem(frameSlot, frame);
        }

        // Row 2: Dynamic sharing mode button (left)
        int slot = SLOT_SHARING;

        if (data.isShared()) {
            if (data.isShareHost()) {
                // Currently hosting
                List<String> lore = new ArrayList<>();
                lore.add("&7Currently hosting this backpack");
                lore.add("&7Password: &f"
                        + (data.sharePassword() == null || data.sharePassword().isEmpty() ? "(none)"
                                : data.sharePassword()));
                lore.add("&7");
                lore.add("&8[&6ʟ-ᴄʟɪᴄᴋ&8]&7 Stop hosting");
                lore.add("&8[&6ʀ-ᴄʟɪᴄᴋ&8]&7 Set host password");
                inv.setItem(slot, createButton("&bSharing: Host", Material.ENDER_EYE, lore));

            } else {
                // Currently joined
                List<String> lore = new ArrayList<>();
                lore.add("&7Currently joined to a backpack");
                lore.add("&7");
                lore.add("&8[&6ʟ-ᴄʟɪᴄᴋ&8]&7 Leave this backpack");
                lore.add("&8[&6ʀ-ᴄʟɪᴄᴋ&8]&7 Join a different host");
                inv.setItem(slot, createButton("&dSharing: Joined", Material.ENDER_PEARL, lore));
            }
        } else {
            // Private mode
            List<String> lore = new ArrayList<>();
            lore.add("&7Your backpack is private");
            lore.add("&7");
            lore.add("&8[&6ʟ-ᴄʟɪᴄᴋ&8]&7 Start hosting");
            lore.add("&8[&6ʀ-ᴄʟɪᴄᴋ&8]&7 Join shared backpack");
            inv.setItem(slot, createButton("&cSharing: Private", Material.BARRIER, lore));
        }

        // Row 2: Color settings (center)
        slot = SLOT_COLORS;
        int[] colors = BackpackColorTints.getColors(backpackItem);
        List<String> colorLore = new ArrayList<>();
        colorLore.add("&7Edit backpack model color groups");
        colorLore.add("&7");
        colorLore.add("&7Current colors:");
        for (int i = 0; i < 5; i++) {
            colorLore.add("&8• &f" + groupLabel(i) + ": #" + String.format("%06X", colors[i]));
        }
        colorLore.add("&7");
        colorLore.add("&8[&6ʟ-ᴄʟɪᴄᴋ&8]&7 Open color picker");
        inv.setItem(slot, createColorPreviewItem(backpackItem, colorLore));

        // Row 2: Name button (right)
        slot = SLOT_NAME;

        // Backpack name button
        List<String> nameLore = new ArrayList<>();
        String currentName = data.backpackName();
        if (currentName.isEmpty()) {
            nameLore.add("&7No custom name set");
        } else {
            nameLore.add("&7Current: &f" + currentName);
        }
        nameLore.add("&7");
        nameLore.add("&8[&6ʟ-ᴄʟɪᴄᴋ&8]&7 Set name");
        inv.setItem(slot, createButton("&6Backpack Name", Material.NAME_TAG, nameLore));

        // Row 3: Back button (bottom center)
        slot = SLOT_BACK;
        List<String> backLore = new ArrayList<>();
        backLore.add("&7Return to the backpack inventory");
        inv.setItem(slot, createButton("&eBack", Material.ARROW, backLore));

        player.openInventory(inv);
    }

    /**
     * Handle clicks in the settings menu.
     */
    public void handleClick(Player player, BackpackMenuHolder holder, int slot, ClickType click) {
        BackpackData data = holder.data();

        // Row 1: Dynamic mode button
        if (slot == SLOT_SHARING) {
            if (data.isShared()) {
                if (data.isShareHost()) {
                    if (click.isRightClick()) {
                        sharingHost.openPasswordDialog(player, holder);
                    } else {
                        sharingHost.stopHosting(player, holder);
                        openSettingsMenu(player, holder);
                    }
                } else {
                    if (click.isRightClick()) {
                        sharingJoiner.openJoinDialog(player, holder);
                    } else {
                        sharingJoiner.leaveJoinedBackpack(player, holder);
                        player.sendMessage(Text.c("&aLeft shared backpack."));
                        openSettingsMenu(player, holder);
                    }
                }
            } else {
                if (click.isRightClick()) {
                    sharingJoiner.openJoinDialog(player, holder);
                } else {
                    sharingHost.hostBackpack(player, holder);
                    openSettingsMenu(player, holder);
                }
            }
            return;
        }

        // Row 2: Single colors button
        if (slot == SLOT_COLORS) {
            openColorPickerMenu(player, holder);
            return;
        }

        // Row 3: Name button
        if (slot == SLOT_NAME) {
            openNameDialog(player, holder);
            return;
        }

        // Back button
        if (slot == SLOT_BACK) {
            plugin.getBackpackMenuRenderer().openMenu(player, holder.backpackId(), holder.type().id(), holder.page());
            return;
        }
    }

    /**
     * Handle clicks in hopper-based color picker.
     */
    public void handleColorPickerClick(Player player, BackpackMenuHolder holder, int slot, ClickType click) {
        ItemStack backpackItem = resolveBackpackItemForSettings(player, holder);
        if (backpackItem == null) {
            player.sendMessage(Text.c("&cCould not find backpack item to edit."));
            return;
        }

        // Slots 0-4 map directly to editable custom_model_data color indices 0-4.
        // Slot 5 (tier/index 5) is intentionally not exposed.
        if (slot >= 0 && slot <= 4) {
            if (click.isRightClick()) {
                BackpackColorTints.clearColorTint(backpackItem, slot);
                persistVisualChanges(holder, backpackItem);
                player.sendMessage(Text.c("&aRemoved custom color override for " + groupLabel(slot) + "."));
                openColorPickerMenu(player, holder);
            } else {
                openCustomRgbDialog(player, holder, backpackItem, slot);
            }
        }
    }

    private void openColorPickerMenu(Player player, BackpackMenuHolder holder) {
        ColorPickerHolder colorHolder = new ColorPickerHolder(holder.backpackId(), holder);
        Inventory picker = plugin.getServer().createInventory(colorHolder, InventoryType.HOPPER,
                Text.c("&8Backpack Colors"));

        ItemStack backpackItem = resolveBackpackItemForSettings(player, holder);
        if (backpackItem == null) {
            player.sendMessage(Text.c("&cCould not find backpack item to edit."));
            return;
        }

        int[] colors = BackpackColorTints.getColors(backpackItem);
        picker.setItem(0, createButton("&f" + groupLabel(0), Material.NAME_TAG,
                List.of("&7Model tint slot: &f0", "&7Current: &f#" + String.format("%06X", colors[0]),
                        "&8[&6ʟ-ᴄʟɪᴄᴋ&8]&7 Enter RGB/decimal",
                        "&8[&6ʀ-ᴄʟɪᴄᴋ&8]&7 Remove override")));
        picker.setItem(1, createButton("&f" + groupLabel(1), Material.NAME_TAG,
                List.of("&7Model tint slot: &f1", "&7Current: &f#" + String.format("%06X", colors[1]),
                        "&8[&6ʟ-ᴄʟɪᴄᴋ&8]&7 Enter RGB/decimal",
                        "&8[&6ʀ-ᴄʟɪᴄᴋ&8]&7 Remove override")));
        picker.setItem(2, createButton("&f" + groupLabel(2), Material.NAME_TAG,
                List.of("&7Model tint slot: &f2", "&7Current: &f#" + String.format("%06X", colors[2]),
                        "&8[&6ʟ-ᴄʟɪᴄᴋ&8]&7 Enter RGB/decimal",
                        "&8[&6ʀ-ᴄʟɪᴄᴋ&8]&7 Remove override")));
        picker.setItem(3, createButton("&f" + groupLabel(3), Material.NAME_TAG,
                List.of("&7Model tint slot: &f3", "&7Current: &f#" + String.format("%06X", colors[3]),
                        "&8[&6ʟ-ᴄʟɪᴄᴋ&8]&7 Enter RGB/decimal",
                        "&8[&6ʀ-ᴄʟɪᴄᴋ&8]&7 Remove override")));
        picker.setItem(4, createButton("&f" + groupLabel(4), Material.NAME_TAG,
                List.of("&7Model tint slot: &f4", "&7Current: &f#" + String.format("%06X", colors[4]),
                        "&8[&6ʟ-ᴄʟɪᴄᴋ&8]&7 Enter RGB/decimal",
                        "&8[&6ʀ-ᴄʟɪᴄᴋ&8]&7 Remove override")));

        player.openInventory(picker);
    }

    private void openCustomRgbDialog(Player player, BackpackMenuHolder holder, ItemStack backpackItem, int colorIndex) {
        new AnvilGUI.Builder()
                .onClick((slot, stateSnapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return java.util.Collections.emptyList();
                    }

                    String input = stateSnapshot.getText();
                    Integer rgb = parseRgbInput(input);
                    if (rgb == null) {
                        player.sendMessage(Text.c("&cInvalid color. Use #RRGGBB or a decimal 0-16777215."));
                        return java.util.Collections.emptyList();
                    }

                    BackpackColorTints.setColorTint(backpackItem, colorIndex, rgb);
                    persistVisualChanges(holder, backpackItem);
                    player.sendMessage(
                            Text.c("&aUpdated " + groupLabel(colorIndex) + " to &f#" + String.format("%06X", rgb)));

                    return java.util.Arrays.asList(
                            AnvilGUI.ResponseAction.close(),
                            AnvilGUI.ResponseAction.run(() -> plugin.getServer().getScheduler().runTask(plugin,
                                    () -> openColorPickerMenu(player, holder))));
                })
                .text("#FFAA33")
                .title(groupLabel(colorIndex) + " RGB")
                .plugin(plugin)
                .open(player);
    }

    private Integer parseRgbInput(String input) {
        if (input == null) {
            return null;
        }

        String normalized = input.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        // Decimal integer path
        if (normalized.matches("^[0-9]+$")) {
            try {
                int decimal = Integer.parseInt(normalized);
                if (decimal < 0 || decimal > 0xFFFFFF) {
                    return null;
                }
                return decimal;
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        // Hex path (#RRGGBB, 0xRRGGBB, or RRGGBB)
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        } else if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }

        if (!normalized.matches("^[0-9A-Fa-f]{6}$")) {
            return null;
        }

        try {
            return Integer.parseInt(normalized, 16);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Open a dialog to set the backpack name.
     */
    private void openNameDialog(Player player, BackpackMenuHolder holder) {
        String currentName = holder.data().backpackName();

        new AnvilGUI.Builder()
                .onClick((slot, stateSnapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return java.util.Collections.emptyList();
                    }

                    String name = stateSnapshot.getText();
                    if (name == null) {
                        name = "";
                    }

                    // Trim and validate
                    name = name.trim();
                    if (name.length() > 32) {
                        name = name.substring(0, 32);
                    }

                    holder.data().backpackName(name);
                    plugin.repo().saveBackpack(holder.data());
                    plugin.sessions().refreshLinkedBackpacksThrottled(holder.backpackId(), holder.data());

                    if (name.isEmpty()) {
                        player.sendMessage(Text.c("&aBackpack name reset to default"));
                    } else {
                        player.sendMessage(Text.c("&aBackpack name set to &f" + name));
                    }

                    return java.util.Arrays.asList(
                            AnvilGUI.ResponseAction.close(),
                            AnvilGUI.ResponseAction.run(() -> {
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    openSettingsMenu(player, holder);
                                });
                            }));
                })
                .text(currentName.isEmpty() ? "Enter backpack name" : currentName)
                .title("Set Backpack Name")
                .plugin(plugin)
                .open(player);
    }

    /**
     * Helper to create a button with name and lore.
     */
    private ItemStack createButton(String name, Material material, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.c(name));
            meta.lore(Text.lore(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createColorPreviewItem(ItemStack backpackItem, List<String> lore) {
        ItemStack preview = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta previewMeta = preview.getItemMeta();
        if (previewMeta == null) {
            return createButton("&dBackpack Colors", Material.PLAYER_HEAD, lore);
        }

        previewMeta.displayName(Text.c("&dBackpack Colors"));
        previewMeta.lore(Text.lore(lore));

        if (backpackItem != null) {
            ItemMeta sourceMeta = backpackItem.getItemMeta();
            if (sourceMeta != null) {
                int customModelData = CustomModelDataUtil.getCustomModelData(sourceMeta);
                if (customModelData > 0) {
                    CustomModelDataUtil.setCustomModelData(previewMeta, customModelData);
                }

                CustomModelDataUtil.setCustomModelDataStrings(previewMeta,
                        CustomModelDataUtil.getCustomModelDataStrings(sourceMeta));
                CustomModelDataUtil.setCustomModelDataColors(previewMeta,
                        CustomModelDataUtil.getCustomModelDataColors(sourceMeta));
            }
        }

        preview.setItemMeta(previewMeta);
        return preview;
    }

    private String groupLabel(int groupIndex) {
        if (groupIndex < 0 || groupIndex >= COLOR_GROUP_NAMES.length) {
            return "Group " + (groupIndex + 1);
        }
        return COLOR_GROUP_NAMES[groupIndex];
    }

    /**
     * Find the backpack ItemStack in the player's inventory by looking for the
     * backpack ID in the item's PersistentDataContainer.
     */
    private ItemStack findBackpackInInventory(Player player, UUID backpackId) {
        if (backpackId == null) {
            return null;
        }

        // Search player inventory for the backpack item
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getItemMeta() == null) {
                continue;
            }

            ItemMeta meta = item.getItemMeta();
            org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();

            String itemBackpackId = pdc.get(plugin.keys().BACKPACK_ID,
                    org.bukkit.persistence.PersistentDataType.STRING);
            if (itemBackpackId != null && backpackId.toString().equals(itemBackpackId)) {
                return item;
            }
        }

        // If not found in inventory scan, explicitly check main hand and offhand by
        // exact ID
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.getItemMeta() != null) {
            String id = mainHand.getItemMeta().getPersistentDataContainer().get(plugin.keys().BACKPACK_ID,
                    org.bukkit.persistence.PersistentDataType.STRING);
            if (id != null && backpackId.toString().equals(id)) {
                return mainHand;
            }
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && offHand.getItemMeta() != null) {
            String id = offHand.getItemMeta().getPersistentDataContainer().get(plugin.keys().BACKPACK_ID,
                    org.bukkit.persistence.PersistentDataType.STRING);
            if (id != null && backpackId.toString().equals(id)) {
                return offHand;
            }
        }

        return null;
    }

    private ItemStack resolveBackpackItemForSettings(Player player, BackpackMenuHolder holder) {
        if (holder == null) {
            return null;
        }

        if (holder.isPlacedContext()) {
            Location placedLocation = holder.placedLocation();
            if (placedLocation == null) {
                return null;
            }

            PlacedBackpack placed = plugin.placedBackpacks().getAt(placedLocation);
            if (placed == null) {
                return null;
            }

            ItemStack item = backpackItems.createExisting(placed.backpackId(), placed.backpackType());
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                return item;
            }

            CustomModelDataUtil.setCustomModelDataStrings(meta,
                    placed.modelDataStrings());
            if (!placed.modelDataColors().isEmpty()) {
                List<Color> colors = new ArrayList<>(placed.modelDataColors().size());
                for (Integer rgb : placed.modelDataColors()) {
                    int value = rgb == null ? 0xFFFFFF : (rgb & 0xFFFFFF);
                    colors.add(Color.fromRGB((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF));
                }
                CustomModelDataUtil.setCustomModelDataColors(meta, colors);
            }

            item.setItemMeta(meta);
            return item;
        }

        return findBackpackInInventory(player, holder.backpackId());
    }

    private void persistVisualChanges(BackpackMenuHolder holder, ItemStack backpackItem) {
        if (holder == null || backpackItem == null || !holder.isPlacedContext()) {
            return;
        }

        Location placedLocation = holder.placedLocation();
        if (placedLocation != null) {
            plugin.placedBackpacks().updatePlacedVisuals(placedLocation, backpackItem);
        }
    }
}
