package io.github.tootertutor.ModularPacks.resource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuHolder;
import io.github.tootertutor.ModularPacks.gui.ResourcePackMenu;
import io.github.tootertutor.ModularPacks.gui.ResourcePackMenuHolder;
import io.github.tootertutor.ModularPacks.update.PackCheckerService;
import io.github.tootertutor.ModularPacks.update.PackCheckerService.AvailablePack;
import io.github.tootertutor.ModularPacks.util.Text;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;

/** Owns player preferences and applies configured resource-pack layers. */
public final class ResourcePackService implements Listener {

    private final ModularPacksPlugin plugin;
    private final PackCheckerService checker;
    private final ResourcePackMenu menu;
    private final Map<UUID, List<String>> preferenceCache = new HashMap<>();
    private final Map<UUID, Map<String, String>> loadedHashes = new HashMap<>();

    public ResourcePackService(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.checker = new PackCheckerService(plugin, this::onPackPublished);
        this.menu = new ResourcePackMenu(plugin, this);
    }

    public void start() {
        stop();
        if (!plugin.cfg().resourcePackEnabled()) {
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        checker.start();
    }

    public void stop() {
        HandlerList.unregisterAll(this);
        checker.stop();
        preferenceCache.clear();
        loadedHashes.clear();
    }

    public List<ResourcePackDefinition> definitions() {
        return plugin.cfg().resourcePacks();
    }

    public AvailablePack availablePack(String id) {
        return checker.availablePack(id);
    }

    public List<String> selectionFor(Player player) {
        return selectionFor(player.getUniqueId());
    }

    public String loadedHash(Player player, String packId) {
        return loadedHashes.getOrDefault(player.getUniqueId(), Map.of()).get(packId);
    }

    public void openMenu(Player player, BackpackMenuHolder source) {
        menu.open(player, source);
    }

    public void handleMenuClick(Player player, ResourcePackMenuHolder holder,
            int slot) {
        menu.handleClick(player, holder, slot);
    }

    public void saveAndApply(Player player, Set<String> selectedIds) {
        List<String> ordered = definitions().stream()
                .map(ResourcePackDefinition::id)
                .filter(selectedIds::contains)
                .toList();
        preferenceCache.put(player.getUniqueId(), ordered);
        plugin.repo().saveResourcePackPreference(new PlayerResourcePackPreference(player.getUniqueId(), ordered));
        apply(player, ordered, true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        selectionFor(player);
        if (plugin.cfg().resourcePackApplyOnJoin()) {
            Bukkit.getScheduler().runTaskLater(plugin,
                    () -> {
                        if (player.isOnline()) {
                            apply(player, selectionFor(player), false);
                        }
                    }, 40L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        preferenceCache.remove(playerId);
        loadedHashes.remove(playerId);
    }

    private List<String> selectionFor(UUID playerId) {
        return preferenceCache.computeIfAbsent(playerId, ignored -> {
            PlayerResourcePackPreference stored = plugin.repo().loadResourcePackPreference(playerId);
            List<String> selected = stored == null ? plugin.cfg().defaultResourcePacks() : stored.enabledPackIds();
            Set<String> configured = definitions().stream()
                    .map(ResourcePackDefinition::id)
                    .collect(java.util.stream.Collectors.toSet());
            return selected.stream()
                    .map(id -> id.toLowerCase(Locale.ROOT))
                    .filter(configured::contains)
                    .distinct()
                    .toList();
        });
    }

    private void apply(Player player, List<String> selectedIds, boolean userInitiated) {
        for (ResourcePackDefinition definition : definitions()) {
            player.removeResourcePack(definition.packId());
        }
        loadedHashes.remove(player.getUniqueId());

        List<ResourcePackInfo> packInfos = new ArrayList<>();
        Map<UUID, AvailablePack> requestPacks = new HashMap<>();
        List<String> unavailable = new ArrayList<>();
        for (String id : selectedIds) {
            AvailablePack hosted = checker.availablePack(id);
            if (hosted == null) {
                unavailable.add(id);
                continue;
            }
            ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(
                    hosted.definition().packId(), hosted.publicUri(), hosted.sha1());
            packInfos.add(info);
            requestPacks.put(hosted.definition().packId(), hosted);
        }

        if (!unavailable.isEmpty()) {
            player.sendMessage(Text.c("&eSome selected resource packs are not available yet: &f"
                    + String.join(", ", unavailable)));
        }
        if (packInfos.isEmpty()) {
            if (userInitiated) {
                if (selectedIds.isEmpty()) {
                    player.sendMessage(Text.c("&aModularPacks resource packs disabled."));
                } else {
                    player.sendMessage(Text.c("&cNo selected resource packs are currently available."));
                }
            }
            return;
        }

        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
                .packs(packInfos)
                .replace(false)
                .required(plugin.cfg().resourcePackRequired())
                .prompt(Text.c(plugin.cfg().resourcePackPrompt()))
                .callback((packId, status, audience) -> handlePackStatus(player.getUniqueId(), packId, status,
                        audience, requestPacks))
                .build();
        player.sendResourcePacks(request);
        if (userInitiated) {
            player.sendMessage(Text.c("&aApplying " + packInfos.size() + " ModularPacks resource-pack layer"
                    + (packInfos.size() == 1 ? "." : "s.")));
        }
    }

    private void handlePackStatus(UUID expectedPlayer, UUID packId, ResourcePackStatus status, Audience audience,
            Map<UUID, AvailablePack> requestPacks) {
        if (!(audience instanceof Player player) || !player.getUniqueId().equals(expectedPlayer)) {
            return;
        }
        if (!plugin.isEnabled()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            AvailablePack hosted = requestPacks.get(packId);
            if (hosted == null) {
                return;
            }
            if (status == ResourcePackStatus.SUCCESSFULLY_LOADED) {
                loadedHashes.computeIfAbsent(expectedPlayer, ignored -> new HashMap<>())
                        .put(hosted.definition().id(), hosted.sha1());
            } else if (!status.intermediate()) {
                player.sendMessage(Text.c("&cCould not load resource pack &f"
                        + hosted.definition().displayName() + "&c: " + status.name().toLowerCase(Locale.ROOT)));
            }
        });
    }

    private void onPackPublished(AvailablePack previous, AvailablePack replacement) {
        if (previous == null || previous.sha1().equals(replacement.sha1())) {
            return;
        }
        String id = replacement.definition().id();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!selectionFor(player).contains(id)) {
                continue;
            }
            String loaded = loadedHash(player, id);
            if (loaded == null || loaded.equals(replacement.sha1())) {
                continue;
            }
            player.sendMessage(Text.c("&eA newer ModularPacks resource pack is available for &f"
                    + replacement.definition().displayName()
                    + "&e. Relog, or open Backpack Settings → Resource Packs and press Apply."));
        }
    }
}
