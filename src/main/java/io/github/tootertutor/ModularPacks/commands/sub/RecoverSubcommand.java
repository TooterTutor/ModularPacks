package io.github.tootertutor.ModularPacks.commands.sub;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.commands.AbstractSubcommand;
import io.github.tootertutor.ModularPacks.commands.CommandContext;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;
import io.github.tootertutor.ModularPacks.data.SQLiteBackpackRepository.VoidedItemRecord;
import io.github.tootertutor.ModularPacks.data.SQLiteBackpackRepository.VoidedItemSummary;
import io.github.tootertutor.ModularPacks.item.BackpackItems;
import io.github.tootertutor.ModularPacks.util.ItemStacks;

public final class RecoverSubcommand extends AbstractSubcommand {

    private final ModularPacksPlugin plugin;
    private final BackpackItems backpackItems;

    public RecoverSubcommand(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.backpackItems = new BackpackItems(plugin);
    }

    @Override
    public String name() {
        return "recover";
    }

    @Override
    public String description() {
        return "Recover lost backpacks and voided items (admin)";
    }

    @Override
    public String permission() {
        return "modularpacks.admin";
    }

    @Override
    public String getUsage() {
        return "backpack recover <void|backpack> ...";
    }

    @Override
    public String getExtendedHelp() {
        return "Recover lost backpacks or voided items.\n"
                + "  recover void <player|uuid> list [limit] [all] - List voided items\n"
                + "  recover void <player|uuid> <id|latest> [receiver] - Recover voided item\n"
                + "  recover backpack <player> <uuid> - Recreate backpack item";
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!checkPermission(ctx))
            return;

        if (!requireArgs(ctx, 1))
            return;

        String kind = ctx.arg(0).toLowerCase(Locale.ROOT);
        switch (kind) {
            case "void" -> handleVoid(ctx);
            case "backpack" -> handleBackpack(ctx);
            default -> ctx.sendError("Unknown subcommand: " + kind);
        }
    }

    @Override
    public List<String> tabComplete(CommandContext ctx) {
        if (ctx.size() == 1) {
            String prefix = safeLower(ctx.arg(0));
            return filterPrefix(List.of("void", "backpack"), prefix);
        }
        if ("void".equalsIgnoreCase(ctx.arg(0))) {
            if (ctx.size() == 2) {
                String prefix = safeLower(ctx.arg(1));
                List<String> out = new ArrayList<>();
                out.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                out.addAll(Bukkit.getOnlinePlayers().stream().map(p -> p.getUniqueId().toString()).toList());
                return filterPrefix(out, prefix);
            }
            if (ctx.size() == 3) {
                return filterPrefix(List.of("list", "latest"), safeLower(ctx.arg(2)));
            }
            if (ctx.size() == 4) {
                String prefix = safeLower(ctx.arg(3));
                return filterPrefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), prefix);
            }
        }
        if ("backpack".equalsIgnoreCase(ctx.arg(0))) {
            if (ctx.size() == 2) {
                String prefix = safeLower(ctx.arg(1));
                return filterPrefix(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), prefix);
            }
        }
        return List.of();
    }

    private void handleVoid(CommandContext ctx) {
        if (ctx.size() < 3) {
            ctx.sendError("Usage: /backpack recover void <player|uuid> list [limit] [all]");
            ctx.sendError("       or: /backpack recover void <player|uuid> <id|latest> [receiver]");
            return;
        }

        UUID playerUuid = parsePlayerOrUuid(ctx.arg(1));
        if (playerUuid == null) {
            ctx.sendError("Unknown player/UUID: " + ctx.arg(1));
            return;
        }

        String action = ctx.arg(2);
        if ("list".equalsIgnoreCase(action)) {
            int limit = 20;
            boolean includeRecovered = false;
            for (int i = 3; i < ctx.size(); i++) {
                String a = ctx.arg(i);
                if (a == null)
                    continue;
                if ("all".equalsIgnoreCase(a) || "--all".equalsIgnoreCase(a) || "recovered".equalsIgnoreCase(a)) {
                    includeRecovered = true;
                    continue;
                }
                Integer n = parseInt(a);
                if (n != null)
                    limit = clamp(n, 1, 200);
            }

            List<VoidedItemSummary> rows = plugin.repo().listVoidedItemsByPlayer(playerUuid, limit, includeRecovered);
            if (rows.isEmpty()) {
                ctx.sendError("No voided items found for " + playerUuid + ".");
                return;
            }

            ctx.sendInfo("Voided items for " + playerUuid + " (showing " + rows.size()
                    + (includeRecovered ? ", including recovered" : ", unrecovered only") + "):");
            for (VoidedItemSummary row : rows) {
                String when = Instant.ofEpochMilli(row.createdAt).toString();
                String status = row.recoveredAt == null ? "UNRECOVERED" : "RECOVERED";
                String backpackShort = row.backpackId == null ? "?"
                        : (row.backpackId.length() >= 8 ? row.backpackId.substring(0, 8) + "…" : row.backpackId);
                ctx.sendInfo(
                        " - #" + row.id + " [" + status + "] " + when + " " + row.itemType + " x" + row.amount + " (bp "
                                + backpackShort + ")");
            }
            return;
        }

        long id;
        if ("latest".equalsIgnoreCase(action)) {
            List<VoidedItemSummary> rows = plugin.repo().listVoidedItemsByPlayer(playerUuid, 1, false);
            if (rows.isEmpty()) {
                ctx.sendError("No unrecovered voided items found for " + playerUuid + ".");
                return;
            }
            id = rows.get(0).id;
        } else {
            Long parsed = parseLong(action);
            if (parsed == null || parsed <= 0) {
                ctx.sendError("Expected an id number or 'latest'.");
                return;
            }
            id = parsed;
        }

        VoidedItemRecord rec = plugin.repo().getVoidedItem(id);
        if (rec == null) {
            ctx.sendError("Voided item not found: #" + id);
            return;
        }
        if (rec.playerUuid != null && !rec.playerUuid.equalsIgnoreCase(playerUuid.toString())) {
            ctx.sendError("That voided item does not belong to " + playerUuid + ".");
            return;
        }
        if (rec.recoveredAt != null) {
            ctx.sendError("That voided item was already recovered: #" + id);
            return;
        }

        ItemStack[] decoded = ItemStackCodec.fromBytes(rec.itemBytes);
        if (decoded.length == 0 || ItemStacks.isAir(decoded[0])) {
            ctx.sendError("Failed to decode stored item data for #" + id + ".");
            return;
        }

        Player receiver = resolveOnlinePlayer(ctx.arg(3));
        if (receiver == null && ctx.sender() instanceof Player p)
            receiver = p;
        if (receiver == null) {
            ctx.sendError("Console must specify an online receiver.");
            return;
        }

        ItemStack item = decoded[0].clone();
        Map<Integer, ItemStack> leftovers = receiver.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            for (ItemStack it : leftovers.values()) {
                receiver.getWorld().dropItemNaturally(receiver.getLocation(), it);
            }
        }

        UUID recoveredByUuid = (ctx.sender() instanceof Player p) ? p.getUniqueId() : null;
        String recoveredByName = ctx.sender().getName();
        plugin.repo().markVoidedItemRecovered(id, recoveredByUuid, recoveredByName);

        ctx.sendInfo("Recovered voided item #" + id + " to " + receiver.getName() + ".");
    }

    private void handleBackpack(CommandContext ctx) {
        if (ctx.size() < 3) {
            ctx.sendError("Usage: /backpack recover backpack <player> <backpackUuid>");
            return;
        }

        Player target = Bukkit.getPlayerExact(ctx.arg(1));
        if (target == null) {
            ctx.sendError("Player must be online: " + ctx.arg(1));
            return;
        }

        UUID backpackId = parseUuid(ctx.arg(2));
        if (backpackId == null) {
            ctx.sendError("Invalid backpack UUID.");
            return;
        }

        String typeId = plugin.repo().findBackpackType(backpackId);
        if (typeId == null) {
            ctx.sendError("Backpack not found in DB: " + backpackId);
            return;
        }

        ItemStack item = backpackItems.createExisting(backpackId, typeId);
        Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            for (ItemStack it : leftovers.values()) {
                target.getWorld().dropItemNaturally(target.getLocation(), it);
            }
        }

        plugin.repo().ensureBackpackExists(backpackId, typeId, target.getUniqueId(), target.getName());
        ctx.sendInfo("Recreated backpack item " + typeId + " (" + backpackId + ") for " + target.getName() + ".");
    }

    private UUID parsePlayerOrUuid(String raw) {
        UUID asUuid = parseUuid(raw);
        if (asUuid != null)
            return asUuid;

        OfflinePlayer op = raw == null ? null : Bukkit.getOfflinePlayer(raw);
        if (op == null)
            return null;

        // Allow recovering for offline players if the server has their UUID cached.
        if (!op.isOnline() && !op.hasPlayedBefore())
            return null;

        return op.getUniqueId();
    }

    private static Player resolveOnlinePlayer(String nameOrUuid) {
        if (nameOrUuid == null)
            return null;
        Player byName = Bukkit.getPlayerExact(nameOrUuid);
        if (byName != null)
            return byName;
        UUID u = parseUuid(nameOrUuid);
        return u == null ? null : Bukkit.getPlayer(u);
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank())
            return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Integer parseInt(String raw) {
        if (raw == null)
            return null;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long parseLong(String raw) {
        if (raw == null)
            return null;
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    private static List<String> filterPrefix(List<String> items, String prefix) {
        return items.stream()
                .distinct()
                .filter(s -> s != null && s.toLowerCase().startsWith(prefix))
                .sorted()
                .toList();
    }
}
