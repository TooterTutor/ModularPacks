package io.github.tootertutor.ModularPacks.commands.sub;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.commands.CommandContext;
import io.github.tootertutor.ModularPacks.commands.Subcommand;
import io.github.tootertutor.ModularPacks.data.SQLiteBackpackRepository.BackpackSummary;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuRenderer;
import net.kyori.adventure.text.Component;

public final class OpenSubcommand implements Subcommand {

    private final ModularPacksPlugin plugin;
    private final BackpackMenuRenderer renderer;

    public OpenSubcommand(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.renderer = new BackpackMenuRenderer(plugin);
    }

    @Override
    public String name() {
        return "open";
    }

    @Override
    public String description() {
        return "Open a backpack by UUID (admin)";
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!ctx.sender().hasPermission("modularpacks.admin")) {
            ctx.sender().sendMessage(Component.text("You do not have permission."));
            return;
        }

        if (!(ctx.sender() instanceof Player viewer)) {
            ctx.sender().sendMessage(Component.text("This command must be run by a player."));
            return;
        }

        if (ctx.size() < 1) {
            ctx.sender().sendMessage(Component.text("Usage: /backpack open <uuid>"));
            ctx.sender().sendMessage(Component.text("   or: /backpack open <player> <Type#N>"));
            return;
        }

        // Variant A: /backpack open <uuid>
        if (ctx.size() == 1) {
            UUID id = parseUuid(ctx.arg(0));
            if (id == null) {
                ctx.sender().sendMessage(Component.text("Invalid UUID."));
                return;
            }

            String type = plugin.repo().findBackpackType(id);
            if (type == null) {
                ctx.sender().sendMessage(Component.text("Backpack not found in DB: " + id));
                return;
            }

            // Admin override: if another player has it open, close them and take over.
            plugin.sessions().tryLock(viewer, id, true);
            renderer.openMenu(viewer, id, type);
            return;
        }

        // Variant B: /backpack open <player> <Type#N>
        String targetName = ctx.arg(0);
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target != null && !target.isOnline() && !target.hasPlayedBefore()) {
            ctx.sender().sendMessage(Component.text("Unknown player (no server cache): " + targetName));
            return;
        }

        String token = ctx.arg(1);
        BackpackPick pick = parseTypeIndexToken(token);
        if (pick == null) {
            ctx.sender().sendMessage(Component.text("Expected token like Netherite#1."));
            return;
        }

        List<BackpackSummary> rows = plugin.repo().listBackpacksByOwner(target.getUniqueId());
        rows = rows.stream()
                .filter(r -> r.backpackType() != null && r.backpackType().equalsIgnoreCase(pick.typeId))
                .toList();

        if (rows.isEmpty()) {
            ctx.sender().sendMessage(Component
                    .text("No backpacks of type " + pick.typeId + " found in DB for " + target.getName() + "."));
            return;
        }

        int idx = pick.index1 - 1;
        if (idx < 0 || idx >= rows.size()) {
            ctx.sender().sendMessage(
                    Component.text("Index out of range. Max for " + pick.typeId + " is " + rows.size() + "."));
            return;
        }

        BackpackSummary chosen = rows.get(idx);
        plugin.sessions().tryLock(viewer, chosen.backpackId(), true);
        renderer.openMenu(viewer, chosen.backpackId(), chosen.backpackType());
    }

    @Override
    public List<String> tabComplete(CommandContext ctx) {
        if (ctx.size() == 1) {
            String prefix = safeLower(ctx.arg(0));
            List<String> out = new ArrayList<>();
            out.addAll(Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .sorted()
                    .toList());
            return out;
        }
        if (ctx.size() == 2) {
            String prefix = safeLower(ctx.arg(1));
            // Suggest "<Type>#1" etc based on what the player actually has.
            Player target = Bukkit.getPlayerExact(ctx.arg(0));
            if (target == null)
                return List.of();

            java.util.Map<String, Integer> maxPerType = new java.util.HashMap<>();
            for (BackpackSummary row : plugin.repo().listBackpacksByOwner(target.getUniqueId())) {
                if (row.backpackType() == null)
                    continue;
                maxPerType.merge(row.backpackType(), 1, Integer::sum);
            }
            List<String> suggestions = new ArrayList<>();
            for (Map.Entry<String, Integer> e : maxPerType.entrySet()) {
                for (int i = 1; i <= Math.min(9, e.getValue()); i++) {
                    suggestions.add(e.getKey() + "#" + i);
                }
            }

            return suggestions.stream()
                    .filter(s -> s.toLowerCase().startsWith(prefix))
                    .sorted()
                    .toList();
        }
        return List.of();
    }

    private static UUID parseUuid(String s) {
        if (s == null)
            return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static BackpackPick parseTypeIndexToken(String token) {
        if (token == null)
            return null;
        int hash = token.lastIndexOf('#');
        if (hash <= 0 || hash >= token.length() - 1)
            return null;
        String typeId = token.substring(0, hash);
        String idxStr = token.substring(hash + 1);
        try {
            int n = Integer.parseInt(idxStr);
            if (n <= 0)
                return null;
            return new BackpackPick(typeId, n);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    private record BackpackPick(String typeId, int index1) {
    }
}
