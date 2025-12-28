package io.github.tootertutor.SophiBackpacks.commands.sub;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import io.github.tootertutor.SophiBackpacks.SophiBackpacksPlugin;
import io.github.tootertutor.SophiBackpacks.commands.CommandContext;
import io.github.tootertutor.SophiBackpacks.commands.Subcommand;
import io.github.tootertutor.SophiBackpacks.data.SQLiteBackpackRepository.BackpackSummary;
import net.kyori.adventure.text.Component;

public final class ListSubcommand implements Subcommand {

    private final SophiBackpacksPlugin plugin;

    public ListSubcommand(SophiBackpacksPlugin plugin) {
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
    public void execute(CommandContext ctx) {
        if (!ctx.sender().hasPermission("sophibackpacks.admin")) {
            ctx.sender().sendMessage(Component.text("You do not have permission."));
            return;
        }

        String arg0 = ctx.size() >= 1 ? ctx.arg(0) : null;
        if (arg0 != null && "unowned".equalsIgnoreCase(arg0)) {
            List<BackpackSummary> rows = plugin.repo().listUnownedBackpacks(100);
            if (rows.isEmpty()) {
                ctx.sender().sendMessage(Component.text("No unowned backpacks found in DB."));
                return;
            }
            ctx.sender().sendMessage(Component.text("Unowned backpacks in DB (first " + rows.size() + "):"));

            java.util.Map<String, Integer> perTypeCount = new java.util.HashMap<>();
            for (BackpackSummary row : rows) {
                int idx = perTypeCount.merge(row.backpackType(), 1, Integer::sum);
                String shortId = row.backpackId().toString().substring(0, 8);
                ctx.sender().sendMessage(Component.text(" - " + row.backpackType() + " #" + idx + " " + shortId + "… (" + row.backpackId()
                        + ")"));
            }
            return;
        }

        OfflinePlayer target = null;
        String name = arg0;
        if (name == null && ctx.sender() instanceof Player p) {
            target = p;
        } else if (name != null) {
            target = Bukkit.getOfflinePlayer(name);
            if (target != null && !target.isOnline() && !target.hasPlayedBefore()) {
                ctx.sender().sendMessage(Component.text("Unknown player (no server cache): " + name));
                return;
            }
        } else {
            ctx.sender().sendMessage(Component.text("Usage: /backpack list <playerName>"));
            ctx.sender().sendMessage(Component.text("   or: /backpack list unowned"));
            return;
        }

        UUID ownerUuid = target.getUniqueId();
        List<BackpackSummary> rows = plugin.repo().listBackpacksByOwner(ownerUuid);
        if (rows.isEmpty()) {
            ctx.sender().sendMessage(Component.text("No backpacks found in DB for " + target.getName() + " (" + ownerUuid + ")."));
            return;
        }

        ctx.sender().sendMessage(Component.text("Backpacks in DB for " + target.getName() + " (" + ownerUuid + "):"));

        java.util.Map<String, Integer> perTypeCount = new java.util.HashMap<>();
        for (BackpackSummary row : rows) {
            int idx = perTypeCount.merge(row.backpackType(), 1, Integer::sum);
            String shortId = row.backpackId().toString().substring(0, 8);
            ctx.sender().sendMessage(Component.text(" - " + row.backpackType() + " #" + idx + " " + shortId + "… (" + row.backpackId()
                    + ")"));
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
            if ("unowned".startsWith(prefix))
                return java.util.stream.Stream.concat(players.stream(), java.util.stream.Stream.of("unowned")).toList();
            return players;
        }
        return List.of();
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase();
    }
}
