package io.github.tootertutor.ModularPacks.commands.sub;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.commands.AbstractSubcommand;
import io.github.tootertutor.ModularPacks.commands.CommandContext;
import io.github.tootertutor.ModularPacks.data.SQLiteBackpackRepository.BackpackSummary;
import io.github.tootertutor.ModularPacks.gui.BackpackMenuRenderer;

/**
 * Open a backpack as a player, by UUID or by player/type index.
 */
public final class OpenSubcommand extends AbstractSubcommand {

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
    public String permission() {
        return "modularpacks.admin";
    }

    @Override
    public String getUsage() {
        return "backpack open <uuid> | backpack open <player> <Type#N>";
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!checkPermission(ctx))
            return;

        Player viewer = requirePlayer(ctx);
        if (viewer == null)
            return;

        if (!requireArgs(ctx, 1))
            return;

        // Single argument: UUID
        if (ctx.size() == 1) {
            openByUuid(ctx, viewer, ctx.arg(0));
            return;
        }

        // Two arguments: Player and Type#Index
        openByPlayerAndType(ctx, viewer, ctx.arg(0), ctx.arg(1));
    }

    private void openByUuid(CommandContext ctx, Player viewer, String uuidStr) {
        UUID id = parseUuid(uuidStr);
        if (id == null) {
            ctx.sendError("Invalid UUID: " + uuidStr);
            return;
        }

        String type = plugin.repo().findBackpackType(id);
        if (type == null) {
            ctx.sendError("Backpack not found in DB: " + id);
            return;
        }

        // Admin override: if another player has it open, close them and take over.
        plugin.sessions().tryLock(viewer, id, true);
        renderer.openMenu(viewer, id, type);
        ctx.sendInfo("Opened backpack " + id);
    }

    private void openByPlayerAndType(CommandContext ctx, Player viewer, String playerName, String typeToken) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null || (!target.isOnline() && !target.hasPlayedBefore())) {
            ctx.sendError("Unknown player (no server cache): " + playerName);
            return;
        }

        BackpackPick pick = parseTypeIndexToken(typeToken);
        if (pick == null) {
            ctx.sendError("Expected token like: Netherite#1");
            return;
        }

        List<BackpackSummary> rows = plugin.repo().listBackpacksByOwner(target.getUniqueId());
        rows = rows.stream()
                .filter(r -> r.backpackType() != null && r.backpackType().equalsIgnoreCase(pick.typeId))
                .toList();

        if (rows.isEmpty()) {
            ctx.sendError("No backpacks of type " + pick.typeId + " found for " + target.getName() + ".");
            return;
        }

        int idx = pick.index1 - 1;
        if (idx < 0 || idx >= rows.size()) {
            ctx.sendError("Index out of range. Max for " + pick.typeId + " is " + rows.size() + ".");
            return;
        }

        BackpackSummary chosen = rows.get(idx);
        plugin.sessions().tryLock(viewer, chosen.backpackId(), true);
        renderer.openMenu(viewer, chosen.backpackId(), chosen.backpackType());
        ctx.sendInfo("Opened " + target.getName() + "'s " + pick.typeId + " backpack #" + pick.index1);
    }

    @Override
    public List<String> tabComplete(CommandContext ctx) {
        if (ctx.size() == 1) {
            String prefix = safeLower(ctx.arg(0));
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .sorted()
                    .toList();
        }

        if (ctx.size() == 2) {
            String prefix = safeLower(ctx.arg(1));
            Player target = Bukkit.getPlayerExact(ctx.arg(0));
            if (target == null)
                return List.of();

            Map<String, Integer> maxPerType = new java.util.HashMap<>();
            for (BackpackSummary row : plugin.repo().listBackpacksByOwner(target.getUniqueId())) {
                if (row.backpackType() == null)
                    continue;
                maxPerType.merge(row.backpackType(), 1, (a, b) -> a + b);
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
