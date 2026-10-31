package io.github.tootertutor.ModularPacks.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.resource.ResourcePackDefinition;
import io.github.tootertutor.ModularPacks.resource.ResourcePackService;
import io.github.tootertutor.ModularPacks.update.PackCheckerService.AvailablePack;
import io.github.tootertutor.ModularPacks.util.Text;

/**
 * GUI for composing and applying a stack of up to four optional resource packs.
 */
public final class ResourcePackMenu {

    private static final int GUI_SIZE = 18;
    private static final int MAX_PACKS = 4;
    private static final int PACK_GAP = 1;

    private static final int NAV_BACK = 0;
    private static final int NAV_DISABLE_ALL = 3;
    private static final int NAV_APPLY = 4;
    private static final int NAV_DEFAULTS = 5;

    private final ModularPacksPlugin plugin;
    private final ResourcePackService service;

    public ResourcePackMenu(ModularPacksPlugin plugin, ResourcePackService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void open(Player player, BackpackMenuHolder source) {
        LinkedHashSet<String> selected = new LinkedHashSet<>(service.selectionFor(player));
        ResourcePackMenuHolder holder = new ResourcePackMenuHolder(source, selected);
        Inventory inventory = plugin.getServer().createInventory(holder, GUI_SIZE,
                Text.c(plugin.lang().text(player, "gui.packs.title")));
        render(player, holder, inventory);
        player.openInventory(inventory);
    }

    public void handleClick(Player player, ResourcePackMenuHolder holder, int slot) {
        Inventory inventory = player.getOpenInventory().getTopInventory();
        List<ResourcePackDefinition> definitions = visibleDefinitions();
        List<Integer> packSlots = packSlots(definitions.size());
        int definitionIndex = packSlots.indexOf(slot);

        if (definitionIndex >= 0) {
            String id = definitions.get(definitionIndex).id();
            if (!holder.selected().remove(id)) {
                holder.selected().add(id);
            }
            render(player, holder, inventory);
            return;
        }

        NavRow nav = navRow(inventory.getSize());
        if (slot == nav.back()) {
            BackpackMenuHolder source = holder.source();
            plugin.getBackpackMenuRenderer().openMenu(player, source.backpackId(), source.type().id(), source.page());
            return;
        }
        if (slot == nav.disableAll()) {
            holder.selected().clear();
            render(player, holder, inventory);
            return;
        }
        if (slot == nav.apply()) {
            service.saveAndApply(player, holder.selected());
            player.closeInventory();
            return;
        }
        if (slot == nav.defaults()) {
            holder.selected().clear();
            holder.selected().addAll(plugin.cfg().defaultResourcePacks());
            render(player, holder, inventory);
        }
    }

    private void render(Player player, ResourcePackMenuHolder holder, Inventory inventory) {
        inventory.clear();
        int navStart = SlotLayout.bottomRowStart(inventory.getSize());

        ItemStack filler = item(plugin.cfg().navBorderFiller(), "&8", List.of());
        for (int slot = navStart; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }

        List<ResourcePackDefinition> definitions = visibleDefinitions();
        List<Integer> slots = packSlots(definitions.size());
        for (int index = 0; index < definitions.size(); index++) {
            ResourcePackDefinition definition = definitions.get(index);
            boolean selected = holder.selected().contains(definition.id());
            AvailablePack available = service.availablePack(definition.id());
            String loadedHash = service.loadedHash(player, definition.id());
            List<String> lore = new ArrayList<>();
            lore.add(selected ? plugin.lang().text(player, "gui.packs.enabled")
                    : plugin.lang().text(player, "gui.packs.disabled"));
            lore.add(plugin.lang().text(player, "gui.packs.priority", "priority", definition.priority()));
            if (available == null) {
                lore.add(plugin.lang().text(player, "gui.packs.notDownloaded"));
            } else {
                lore.add(plugin.lang().text(player, "gui.packs.available", "hash", available.sha1().substring(0, 8)));
                if (loadedHash != null && loadedHash.equals(available.sha1())) {
                    lore.add(plugin.lang().text(player, "gui.packs.loaded"));
                } else if (loadedHash != null) {
                    lore.add(plugin.lang().text(player, "gui.packs.update"));
                }
            }
            lore.add("");
            lore.add(plugin.lang().text(player, "gui.packs.toggle"));
            String name = (selected ? "&a✔ " : "&7") + definition.displayName();
            inventory.setItem(slots.get(index), item(definition.icon(), name, lore));
        }

        NavRow nav = navRow(inventory.getSize());
        inventory.setItem(nav.back(),
                item(plugin.cfg().navPageButtons(), plugin.lang().text(player, "gui.packs.back"), List.of()));
        inventory.setItem(nav.disableAll(), item(Material.BARRIER, plugin.lang().text(player, "gui.packs.disableAll"),
                List.of(plugin.lang().text(player, "gui.packs.disableLore1"),
                        plugin.lang().text(player, "gui.packs.disableLore2"))));
        inventory.setItem(nav.apply(), item(Material.LIME_CONCRETE, plugin.lang().text(player, "gui.packs.apply"),
                List.of(plugin.lang().text(player, "gui.packs.applyCount", "count", holder.selected().size()),
                        plugin.lang().text(player, "gui.packs.applyTogether"))));
        inventory.setItem(nav.defaults(),
                item(Material.RECOVERY_COMPASS, plugin.lang().text(player, "gui.packs.defaults"),
                        List.of(plugin.lang().text(player, "gui.packs.defaultLore1"),
                                plugin.lang().text(player, "gui.packs.defaultLore2"))));
    }

    private List<ResourcePackDefinition> visibleDefinitions() {
        List<ResourcePackDefinition> definitions = service.definitions();
        return definitions.size() <= MAX_PACKS ? definitions : definitions.subList(0, MAX_PACKS);
    }

    private static List<Integer> packSlots(int packCount) {
        return SlotLayout.centeredRowSlots(0, Math.min(MAX_PACKS, packCount), PACK_GAP);
    }

    private static NavRow navRow(int inventorySize) {
        return new NavRow(
                SlotLayout.navRowSlot(inventorySize, NAV_BACK),
                SlotLayout.navRowSlot(inventorySize, NAV_DISABLE_ALL),
                SlotLayout.navRowSlot(inventorySize, NAV_APPLY),
                SlotLayout.navRowSlot(inventorySize, NAV_DEFAULTS));
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.c(name));
        meta.lore(Text.lore(lore));
        item.setItemMeta(meta);
        return item;
    }

    private record NavRow(int back, int disableAll, int apply, int defaults) {
    }
}
