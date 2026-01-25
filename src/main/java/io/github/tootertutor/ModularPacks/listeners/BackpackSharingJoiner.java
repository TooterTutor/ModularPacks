package io.github.tootertutor.ModularPacks.listeners;

import java.util.Collections;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.config.BackpackTypeDef;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuHolder;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuRenderer;
import io.github.tootertutor.ModularPacks.util.Text;
import net.wesjd.anvilgui.AnvilGUI;
import net.wesjd.anvilgui.AnvilGUI.ResponseAction;

/**
 * Handles backpack sharing functionality for joiners.
 * Manages the join dialog and validation logic for connecting to shared
 * backpacks.
 */
public class BackpackSharingJoiner {

    private final ModularPacksPlugin plugin;
    private final BackpackMenuRenderer renderer;
    private final BackpackSaveManager saveManager;

    public BackpackSharingJoiner(ModularPacksPlugin plugin, BackpackMenuRenderer renderer,
            BackpackSaveManager saveManager) {
        this.plugin = plugin;
        this.renderer = renderer;
        this.saveManager = saveManager;
    }

    /**
     * Leaves a joined backpack and restores the original contents/modules for the
     * joiner.
     */
    public void leaveJoinedBackpack(Player player, BackpackMenuHolder holder) {
        saveManager.flushSaveNow(player, holder, true);

        BackpackData restored = plugin.repo().loadJoinerContents(holder.backpackId());
        if (restored != null && restored.contentsBytes() != null) {
            holder.data().contentsBytes(restored.contentsBytes());
            holder.data().installedModules().clear();
            holder.data().installedSnapshots().clear();
            holder.data().moduleStates().clear();
            if (restored.installedModules() != null) {
                holder.data().installedModules().putAll(restored.installedModules());
            }
            if (restored.installedSnapshots() != null) {
                holder.data().installedSnapshots().putAll(restored.installedSnapshots());
            }
            if (restored.moduleStates() != null) {
                holder.data().moduleStates().putAll(restored.moduleStates());
            }
        }

        holder.data().setShared(false);
        holder.data().shareHostId(null);
        holder.data().sharePassword("");
        plugin.repo().saveBackpack(holder.data());
        plugin.sessions().refreshLinkedBackpacksThrottled(holder.backpackId(), holder.data());
        renderer.render(holder);
    }

    /**
     * Opens the dialog to join a shared backpack by host ID and password.
     *
     * @param player the player joining
     * @param holder the joiner's backpack menu holder
     */
    public void openJoinDialog(Player player, BackpackMenuHolder holder) {
        new AnvilGUI.Builder()
                .onClick((slot, stateSnapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return Collections.emptyList();
                    }

                    String input = stateSnapshot.getText();
                    if (input == null || input.trim().isEmpty()) {
                        return Collections.singletonList(ResponseAction.close());
                    }

                    String[] parts = input.trim().split("\\s+", 2);
                    String hostIdStr = parts[0];
                    String password = parts.length > 1 ? parts[1] : "";

                    UUID hostId;
                    try {
                        hostId = UUID.fromString(hostIdStr);
                    } catch (IllegalArgumentException ex) {
                        if (hostIdStr.length() > 0 && hostIdStr.length() <= 8) {
                            hostId = plugin.repo().findBackpackByUuidPrefix(hostIdStr);
                            if (hostId == null) {
                                player.sendMessage(Text.c("&cNo backpack found with ID starting with: " + hostIdStr));
                                return Collections.singletonList(ResponseAction.close());
                            }
                        } else {
                            player.sendMessage(
                                    Text.c("&cInvalid backpack ID format. Use full UUID or first 8 characters."));
                            return Collections.singletonList(ResponseAction.close());
                        }
                    }

                    String hostType = plugin.repo().findBackpackType(hostId);
                    if (hostType == null) {
                        player.sendMessage(Text.c("&cHost backpack not found"));
                        return Collections.singletonList(ResponseAction.close());
                    }

                    BackpackData hostData = plugin.repo().loadOrCreate(hostId, hostType);
                    if (!hostData.isShared() || !hostData.isShareHost()) {
                        player.sendMessage(Text.c("&cThat backpack is not shared or is not a host"));
                        return Collections.singletonList(ResponseAction.close());
                    }

                    BackpackTypeDef hostDef = plugin.cfg().findType(hostType);
                    BackpackTypeDef joinerDef = holder.type();

                    if (hostDef == null || joinerDef == null) {
                        player.sendMessage(Text.c("&cUnable to determine backpack tiers; cannot join."));
                        return Collections.singletonList(ResponseAction.close());
                    }

                    if (!hasMatchingTier(joinerDef, hostDef)) {
                        player.sendMessage(Text.c("&cYour backpack tier (" + joinerDef.displayName()
                                + "&c) must match the host tier (" + hostDef.displayName() + "&c)."));
                        return Collections.singletonList(ResponseAction.close());
                    }

                    if (!hostData.sharePassword().isEmpty() && !hostData.sharePassword().equals(password)) {
                        player.sendMessage(Text.c("&cIncorrect password"));
                        return Collections.singletonList(ResponseAction.close());
                    }

                    plugin.repo().saveJoinerBackup(holder.backpackId(), holder.data());

                    holder.data().setShared(true);
                    holder.data().shareHostId(hostId);
                    holder.data().sharePassword(password);
                    player.sendMessage(Text.c("&aSet password to: &f'" + password + "'"));
                    plugin.repo().saveShareMetadataOnly(holder.data());
                    player.sendMessage(Text.c("&aSuccessfully joined backpack"));

                    return java.util.Arrays.asList(
                            ResponseAction.close(),
                            ResponseAction.run(() -> {
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    renderer.openMenu(player, holder.backpackId(), holder.type().id(), holder.page());
                                });
                            }));
                })
                .text("Backpack-ID password")
                .title("Join Shared Backpack")
                .plugin(plugin)
                .open(player);
    }

    /**
     * Checks if two backpack tier definitions match for joining purposes.
     *
     * @param joiner the joiner's backpack tier
     * @param host   the host's backpack tier
     * @return true if tiers match (same ID, case-insensitive), false otherwise
     */
    public static boolean hasMatchingTier(BackpackTypeDef joiner, BackpackTypeDef host) {
        if (joiner == null || host == null)
            return false;
        String a = joiner.id();
        String b = host.id();
        if (a == null || b == null)
            return false;
        return a.equalsIgnoreCase(b);
    }
}
