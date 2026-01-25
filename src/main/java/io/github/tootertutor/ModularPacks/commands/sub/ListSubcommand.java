package io.github.tootertutor.ModularPacks.commands.sub;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.commands.AbstractSubcommand;
import io.github.tootertutor.ModularPacks.commands.CommandContext;
import io.github.tootertutor.ModularPacks.data.SQLiteBackpackRepository.BackpackSummary;

/**
 * List backpacks in the database by player or show unowned backpacks.
 */
public final class ListSubcommand extends AbstractSubcommand {

    private final ModularPacksPlugin plugin;

    public ListSubcommand(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "list";
    }

    @Override
    public String description() {
        return "List a player's backpacks from DB";
    }

    @Override
    public String permission() {
        return "modularpacks.admin";
    }

    @Override
    public String getUsage() {
        return "backpack list [playerName | unowned]";
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!checkPermission(ctx))
            return;

        String arg0 = ctx.arg(0);

        if (arg0 != null && "unowned".equalsIgnoreCase(arg0)) {
            listUnownedBackpacks(ctx);
            return;
        }

        listPlayerBackpacks(ctx, arg0);
    }

    private void listUnownedBackpacks(CommandContext ctx) {
        List<BackpackSummary> rows = plugin.repo().listUnownedBackpacks(100);
        if (rows.isEmpty()) {
            ctx.sendInfo("No unowned backpacks found in DB.");
            return;
        }

        ctx.sendInfo("Unowned backpacks in DB (first " + rows.size() + "):");

        java.util.Map<String, Integer> perTypeCount = new java.util.HashMap<>();
        for (BackpackSummary row : rows) {
            int idx = perTypeCount.merge(row.backpackType(), 1, (a, b) -> a + b);
            String shortId = row.backpackId().toString().substring(0, 8);
            ctx.sendInfo(" - " + row.backpackType() + " #" + idx + " " + shortId + "… (" + row.backpackId() + ")");
        }
    }

    private void listPlayerBackpacks(CommandContext ctx, String playerName) {
        OfflinePlayer target = null;

        if (playerName == null) {
            Player sender = ctx.requirePlayer();
            if (sender == null) {
                ctx.sendUsage(getUsage());
                return;
            }
            target = sender;
        } else {
            target = Bukkit.getOfflinePlayer(playerName);
            if (target != null && !target.isOnline() && !target.hasPlayedBefore()) {
                ctx.sendError("Unknown player (no server cache): " + playerName);
                return;
            }
        }

        if (target == null) {
            ctx.sendError("Could not resolve player: " + playerName);
            return;
        }

        UUID ownerUuid = target.getUniqueId();
        List<BackpackSummary> rows = plugin.repo().listBackpacksByOwner(ownerUuid);

        if (rows.isEmpty()) {
            ctx.sendInfo("No backpacks found in DB for " + target.getName() + " (" + ownerUuid + ").");
            return;
        }

        ctx.sendInfo("Backpacks in DB for " + target.getName() + " (" + ownerUuid + "):");

        java.util.Map<String, Integer> perTypeCount = new java.util.HashMap<>();
        for (BackpackSummary row : rows) {
            int idx = perTypeCount.merge(row.backpackType(), 1, (a, b) -> a + b);
            String shortId = row.backpackId().toString().substring(0, 8);
            ctx.sendInfo(" - " + row.backpackType() + " #" + idx + " " + shortId + "… (" + row.backpackId() + ")");
        }
    }

    @Override
    public List<String> tabComplete(CommandContext ctx) {
        if (ctx.size() == 1) {
            String prefix = safeLower(ctx.arg(0));
            List<String> players = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .sorted()
                    .toList();
            if ("unowned".startsWith(prefix)) {
                return java.util.stream.Stream.concat(players.stream(), java.util.stream.Stream.of("unowned")).toList();
            }
            return players;
        }
        return List.of();
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase();
    }
}
