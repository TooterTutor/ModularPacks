package io.github.tootertutor.ModularPacks.listeners.backpack;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.gui.AdminBackpackListMenu;
import io.github.tootertutor.ModularPacks.gui.AdminBackpackListMenuHolder;
import io.github.tootertutor.ModularPacks.gui.AdminBackpackListMenuHolder.AdminBackpackListEntry;
import io.github.tootertutor.ModularPacks.gui.AdminBackpackListMenuHolder.InteractionMode;
import io.github.tootertutor.ModularPacks.item.BackpackItems;
import io.github.tootertutor.ModularPacks.util.Text;

public final class AdminBackpackListMenuListener implements Listener {

    private final ModularPacksPlugin plugin;
    private final AdminBackpackListMenu menu;
    private final BackpackItems backpackItems;

    public AdminBackpackListMenuListener(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.menu = new AdminBackpackListMenu(plugin);
        this.backpackItems = new BackpackItems(plugin);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(e.getView().getTopInventory().getHolder() instanceof AdminBackpackListMenuHolder holder)) {
            return;
        }

        e.setCancelled(true);

        int raw = e.getRawSlot();
        int topSize = e.getView().getTopInventory().getSize();
        if (raw < 0 || raw >= topSize) {
            return;
        }

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null) {
            return;
        }

        if (menu.isPreviousButton(clicked)) {
            holder.page(holder.page() - 1);
            menu.render(holder);
            return;
        }

        if (menu.isNextButton(clicked)) {
            holder.page(holder.page() + 1);
            menu.render(holder);
            return;
        }

        if (menu.isSortButton(clicked)) {
            if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) {
                holder.toggleSortDirection();
            } else {
                holder.cycleSortField();
            }
            menu.render(holder);
            return;
        }

        if (menu.isModeButton(clicked)) {
            holder.toggleMode();
            menu.render(holder);
            return;
        }

        UUID backpackId = menu.extractBackpackId(clicked);
        if (backpackId == null) {
            return;
        }

        AdminBackpackListEntry entry = holder.findEntry(backpackId);
        if (entry == null) {
            player.sendMessage(Text.c("&cCould not resolve that backpack entry."));
            return;
        }

        if (holder.mode() == InteractionMode.RECOVER) {
            if (e.getClick().isRightClick()) {
                recoverToOwner(player, entry);
            } else {
                recoverToViewer(player, entry);
            }
            return;
        }

        String type = plugin.repo().findBackpackType(backpackId);
        if (type == null || type.isBlank()) {
            player.sendMessage(Text.c("&cThat backpack no longer exists in the database."));
            return;
        }

        plugin.sessions().tryLock(player, backpackId, true);
        plugin.getBackpackMenuRenderer().openMenu(player, backpackId, type);
    }

    private void recoverToViewer(Player viewer, AdminBackpackListEntry entry) {
        ItemStack item = backpackItems.createExisting(entry.backpackId(), entry.backpackType());
        var leftovers = viewer.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(it -> viewer.getWorld().dropItemNaturally(viewer.getLocation(), it));
        }

        plugin.repo().ensureBackpackExists(entry.backpackId(), entry.backpackType(), viewer.getUniqueId(),
                viewer.getName());
        viewer.sendMessage(Text.c("&aRecovered backpack to you: &f" + entry.backpackId()));
    }

    private void recoverToOwner(Player viewer, AdminBackpackListEntry entry) {
        UUID ownerUuid;
        try {
            ownerUuid = UUID.fromString(entry.ownerUuid());
        } catch (Exception ex) {
            viewer.sendMessage(Text.c("&cThis backpack has no valid owner UUID to recover to."));
            return;
        }

        Player owner = Bukkit.getPlayer(ownerUuid);
        if (owner == null) {
            viewer.sendMessage(Text.c("&cOwner is offline. Right-click recovery requires owner online."));
            return;
        }

        ItemStack item = backpackItems.createExisting(entry.backpackId(), entry.backpackType());
        var leftovers = owner.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            leftovers.values().forEach(it -> owner.getWorld().dropItemNaturally(owner.getLocation(), it));
        }

        plugin.repo().ensureBackpackExists(entry.backpackId(), entry.backpackType(), owner.getUniqueId(),
                owner.getName());
        viewer.sendMessage(
                Text.c("&aRecovered backpack to owner: &f" + owner.getName() + " &7(" + entry.backpackId() + ")"));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof AdminBackpackListMenuHolder)) {
            return;
        }

        int topSize = e.getView().getTopInventory().getSize();
        for (int raw : e.getRawSlots()) {
            if (raw >= 0 && raw < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
