package io.github.tootertutor.ModularPacks.listeners;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuHolder;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuRenderer;
import io.github.tootertutor.ModularPacks.gui.ModuleScreenHolder;
import io.github.tootertutor.ModularPacks.util.Text;
import net.wesjd.anvilgui.AnvilGUI;

/**
 * Handles backpack sharing functionality for hosts.
 * Manages password dialogs and host-related share settings.
 */
public class BackpackSharingHost {

    private final ModularPacksPlugin plugin;
    private final BackpackMenuRenderer renderer;
    private final BackpackSaveManager saveManager;

    public BackpackSharingHost(ModularPacksPlugin plugin, BackpackMenuRenderer renderer,
            BackpackSaveManager saveManager) {
        this.plugin = plugin;
        this.renderer = renderer;
        this.saveManager = saveManager;
    }

    /**
     * Marks this backpack as hosted (shared) without requiring a password.
     * Password can be set later via {@link #openPasswordDialog}.
     */
    public void hostBackpack(Player player, BackpackMenuHolder holder) {
        // Persist any in-flight UI state first to avoid dupes when toggling modes
        saveManager.flushSaveNow(player, holder, true);

        holder.data().setShared(true);
        holder.data().shareHostId(null);
        // Do a full save so contents+metadata are consistent immediately
        plugin.repo().saveBackpack(holder.data());
        plugin.sessions().refreshLinkedBackpacksThrottled(holder.backpackId(), holder.data());

        player.sendMessage(Text.c("&aBackpack is now shared!"));
        renderer.render(holder); // refresh mode button and visuals
    }

    /**
     * Stops hosting: disconnects joined backpacks, closes their UIs, releases
     * locks,
     * and persists the host backpack state.
     */
    public void stopHosting(Player player, BackpackMenuHolder holder) {
        // Persist current state before changing mode
        saveManager.flushSaveNow(player, holder, true);

        var joinedIds = plugin.repo().disconnectAllJoinedBackpacks(holder.backpackId());
        for (var joinedId : joinedIds) {
            for (Player online : Bukkit.getOnlinePlayers()) {
                Inventory top = online.getOpenInventory().getTopInventory();
                InventoryHolder invHolder = top != null ? top.getHolder() : null;
                boolean matches = (invHolder instanceof BackpackMenuHolder bmh && joinedId.equals(bmh.backpackId()))
                        || (invHolder instanceof ModuleScreenHolder msh && joinedId.equals(msh.backpackId()));
                if (matches) {
                    online.sendMessage(Text.c("&cThe host closed sharing; your backpack is now private."));
                    online.closeInventory();
                }
            }

            var viewerId = plugin.sessions().lockedTo(joinedId);
            if (viewerId != null) {
                plugin.sessions().releaseAllFor(viewerId);
            }
            plugin.sessions().releaseLock(joinedId);
        }

        holder.data().setShared(false);
        holder.data().sharePassword("");
        holder.data().shareHostId(null);
        plugin.repo().saveBackpack(holder.data());
        plugin.sessions().refreshLinkedBackpacksThrottled(holder.backpackId(), holder.data());

        player.sendMessage(Text.c("&aBackpack is no longer shared."));
        renderer.render(holder);
    }

    /**
     * Opens the password dialog for setting a share password on the host backpack.
     * If the password is changed, all joined backpacks are disconnected.
     *
     * @param player the player opening the dialog
     * @param holder the backpack menu holder
     */
    public void openPasswordDialog(Player player, BackpackMenuHolder holder) {
        String currentPassword = holder.data().sharePassword();

        new AnvilGUI.Builder()
                .onClick((slot, stateSnapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return java.util.Collections.emptyList();
                    }

                    String password = stateSnapshot.getText();
                    if (password == null) {
                        password = "";
                    }

                    // Check if password actually changed
                    boolean passwordChanged = !password.equals(currentPassword);

                    holder.data().sharePassword(password);
                    plugin.repo().saveShareMetadataOnly(holder.data());

                    // If password changed, disconnect all joined backpacks
                    if (passwordChanged) {
                        var joinedIds = plugin.repo().disconnectAllJoinedBackpacks(holder.backpackId());
                        for (var joinedId : joinedIds) {
                            for (Player online : Bukkit.getOnlinePlayers()) {
                                Inventory top = online.getOpenInventory().getTopInventory();
                                InventoryHolder invHolder = top != null ? top.getHolder() : null;
                                boolean matches = (invHolder instanceof BackpackMenuHolder bmh
                                        && joinedId.equals(bmh.backpackId()))
                                        || (invHolder instanceof ModuleScreenHolder msh
                                                && joinedId.equals(msh.backpackId()));
                                if (matches) {
                                    online.sendMessage(
                                            Text.c("&cHost changed the password; you have been disconnected."));
                                    online.closeInventory();
                                }
                            }

                            var viewerId = plugin.sessions().lockedTo(joinedId);
                            if (viewerId != null) {
                                plugin.sessions().releaseAllFor(viewerId);
                            }
                            plugin.sessions().releaseLock(joinedId);
                        }
                        player.sendMessage(Text.c("&aPassword changed; all joined backpacks disconnected."));
                    }

                    return java.util.Arrays.asList(
                            AnvilGUI.ResponseAction.close(),
                            AnvilGUI.ResponseAction.run(() -> {
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    renderer.openMenu(player, holder.backpackId(), holder.type().id(), holder.page());
                                });
                            }));
                })
                .text(currentPassword.isEmpty() ? "Enter password" : currentPassword)
                .title("Set Share Password")
                .plugin(plugin)
                .open(player);
    }
}
