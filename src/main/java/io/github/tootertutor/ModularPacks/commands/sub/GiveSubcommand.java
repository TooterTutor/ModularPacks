package io.github.tootertutor.ModularPacks.commands.sub;

import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.commands.AbstractSubcommand;
import io.github.tootertutor.ModularPacks.commands.CommandContext;
import io.github.tootertutor.ModularPacks.item.BackpackItems;
import io.github.tootertutor.ModularPacks.item.Keys;
import io.github.tootertutor.ModularPacks.item.UpgradeItems;

/**
 * Give backpacks or modules/upgrades to players.
 * Usage: /backpack give type <typeId> [player] [amount]
 * /backpack give module <id> [player] [amount]
 */
public final class GiveSubcommand extends AbstractSubcommand {

    private final ModularPacksPlugin plugin;
    private final BackpackItems backpackItems;
    private final UpgradeItems upgradeItems;

    public GiveSubcommand(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.backpackItems = new BackpackItems(plugin);
        this.upgradeItems = new UpgradeItems(plugin);
    }

    @Override
    public String name() {
        return "give";
    }

    @Override
    public String description() {
        return "Give backpacks/modules for testing";
    }

    @Override
    public String permission() {
        return "modularpacks.admin";
    }

    @Override
    public String getUsage() {
        return "backpack give type <typeId> [player] [amount] | backpack give module <id> [player] [amount]";
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!checkPermission(ctx))
            return;

        if (!requireArgs(ctx, 1))
            return;

        String category = ctx.arg(0).toLowerCase(Locale.ROOT);
        String a1 = ctx.arg(1);
        String a2 = ctx.arg(2);
        String a3 = ctx.arg(3);

        switch (category) {
            case "module", "upgrade" -> executeGiveModule(ctx, a1, a2, a3);
            case "type" -> executeGiveType(ctx, a1, a2, a3);
            default -> ctx.sendUsage(getUsage());
        }
    }

    private void executeGiveModule(CommandContext ctx, String moduleId, String playerName, String amountStr) {
        if (moduleId == null) {
            ctx.sendUsage("backpack give module <id> [player] [amount]");
            return;
        }

        Player target = resolveTarget(ctx, playerName);
        if (target == null)
            return;

        int amount = parseAmount(amountStr, 1);

        var def = plugin.cfg().findUpgrade(moduleId);
        if (def == null) {
            ctx.sendError("Unknown module/upgrade id: " + moduleId);
            return;
        }

        for (int i = 0; i < amount; i++) {
            target.getInventory().addItem(upgradeItems.create(def.id()));
        }

        ctx.sendInfo("Gave " + target.getName() + " x" + amount + " module(s): " + def.id());
    }

    private void executeGiveType(CommandContext ctx, String typeId, String playerName, String amountStr) {
        if (typeId == null) {
            ctx.sendUsage("backpack give type <typeId> [player] [amount]");
            return;
        }

        Player target = resolveTarget(ctx, playerName);
        if (target == null)
            return;

        int amount = parseAmount(amountStr, 1);

        var type = plugin.cfg().findType(typeId);
        if (type == null) {
            ctx.sendError("Unknown backpack type: " + typeId);
            return;
        }

        for (int i = 0; i < amount; i++) {
            ItemStack item = backpackItems.create(type.id());
            target.getInventory().addItem(item);
            ensureOwnedBackpackRow(target, item);
        }

        ctx.sendInfo("Gave " + target.getName() + " x" + amount + " backpack(s): " + type.id());
    }

    /**
     * Resolve target player: use specified player or fall back to sender if they're
     * a player.
     */
    private Player resolveTarget(CommandContext ctx, String playerName) {
        if (playerName != null) {
            Player player = ctx.resolvePlayer(playerName);
            if (player == null) {
                ctx.sendError("Player not found: " + playerName);
            }
            return player;
        }

        Player sender = ctx.requirePlayer();
        if (sender == null) {
            ctx.sendError("Console must specify a player name.");
        }
        return sender;
    }

    private int parseAmount(String raw, int def) {
        if (raw == null)
            return def;
        try {
            int n = parseInt(raw, def);
            return Math.max(1, Math.min(64, n));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private void ensureOwnedBackpackRow(Player owner, ItemStack backpackItem) {
        if (owner == null || backpackItem == null || !backpackItem.hasItemMeta())
            return;
        ItemMeta meta = backpackItem.getItemMeta();
        if (meta == null)
            return;
        Keys keys = plugin.keys();
        var pdc = meta.getPersistentDataContainer();
        String idStr = pdc.get(keys.BACKPACK_ID, PersistentDataType.STRING);
        String typeId = pdc.get(keys.BACKPACK_TYPE, PersistentDataType.STRING);
        if (idStr == null || typeId == null)
            return;
        try {
            java.util.UUID id = java.util.UUID.fromString(idStr);
            plugin.repo().ensureBackpackExists(id, typeId, owner.getUniqueId(), owner.getName());
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Override
    public List<String> tabComplete(CommandContext ctx) {
        if (ctx.size() == 1) {
            String prefix = safeLower(ctx.arg(0));
            return List.of("type", "module").stream()
                    .filter(s -> s.startsWith(prefix))
                    .sorted()
                    .toList();
        }

        if ("module".equalsIgnoreCase(ctx.arg(0)) || "upgrade".equalsIgnoreCase(ctx.arg(0))) {
            if (ctx.size() == 2) {
                String prefix = safeLower(ctx.arg(1));
                return plugin.cfg().getUpgrades().stream()
                        .map(u -> u.id())
                        .filter(s -> s.toLowerCase().startsWith(prefix))
                        .sorted()
                        .toList();
            }
            if (ctx.size() == 3) {
                String prefix = safeLower(ctx.arg(2));
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(prefix))
                        .sorted()
                        .toList();
            }
            return List.of();
        }

        if ("type".equalsIgnoreCase(ctx.arg(0))) {
            if (ctx.size() == 2) {
                String prefix = safeLower(ctx.arg(1));
                return plugin.cfg().getTypes().stream()
                        .map(t -> t.id())
                        .filter(s -> s.toLowerCase().startsWith(prefix))
                        .sorted()
                        .toList();
            }
            if (ctx.size() == 3) {
                String prefix = safeLower(ctx.arg(2));
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(prefix))
                        .sorted()
                        .toList();
            }
        }

        return List.of();
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase();
    }
}
