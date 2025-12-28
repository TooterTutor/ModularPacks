package io.github.tootertutor.SophiBackpacks.commands.sub;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.SophiBackpacks.SophiBackpacksPlugin;
import io.github.tootertutor.SophiBackpacks.commands.CommandContext;
import io.github.tootertutor.SophiBackpacks.commands.Subcommand;
import io.github.tootertutor.SophiBackpacks.item.BackpackItems;
import io.github.tootertutor.SophiBackpacks.item.Keys;
import io.github.tootertutor.SophiBackpacks.item.UpgradeItems;
import net.kyori.adventure.text.Component;

public final class GiveSubcommand implements Subcommand {

    private final SophiBackpacksPlugin plugin;
    private final BackpackItems backpackItems;
    private final UpgradeItems upgradeItems;

    public GiveSubcommand(SophiBackpacksPlugin plugin) {
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
    public void execute(CommandContext ctx) {
        if (ctx.size() < 1) {
            ctx.sender().sendMessage(Component.text("Usage: /backpack give type <typeId> [player] [amount]"));
            ctx.sender().sendMessage(Component.text("   or: /backpack give module <id> [player] [amount]"));
            return;
        }

        String category = ctx.arg(0); // "type" | "module"
        String a1 = ctx.arg(1);
        String a2 = ctx.arg(2);
        String a3 = ctx.arg(3);

        /*
         * ---------------------------------------------------------
         * /backpack give module <id> [player] [amount]
         * ---------------------------------------------------------
         */
        if ("module".equalsIgnoreCase(category) || "upgrade".equalsIgnoreCase(category)) {
            if (a1 == null) {
                ctx.sender().sendMessage(Component.text("Usage: /backpack give module <id> [player] [amount]"));
                return;
            }

            String moduleId = a1;
            Player target = resolvePlayer(ctx, a2);
            if (target == null)
                return;

            int amount = parseAmount(a3, 1);

            var def = plugin.cfg().findUpgrade(moduleId);
            if (def == null) {
                ctx.sender().sendMessage(Component.text("Unknown module/upgrade id: " + moduleId));
                return;
            }

            for (int i = 0; i < amount; i++) {
                target.getInventory().addItem(upgradeItems.create(def.id()));
            }

            ctx.sender().sendMessage(Component.text(
                    "Gave " + target.getName() + " x" + amount + " module(s): " + def.id()));
            return;
        }

        /*
         * ---------------------------------------------------------
         * /backpack give type <typeId> [player] [amount]
         * ---------------------------------------------------------
         */
        if (!"type".equalsIgnoreCase(category)) {
            ctx.sender().sendMessage(Component.text("Usage: /backpack give type <typeId> [player] [amount]"));
            ctx.sender().sendMessage(Component.text("   or: /backpack give module <id> [player] [amount]"));
            return;
        }

        String typeInput = a1;
        if (typeInput == null) {
            ctx.sender().sendMessage(Component.text("Usage: /backpack give type <typeId> [player] [amount]"));
            return;
        }

        Player target = resolvePlayer(ctx, a2);
        if (target == null)
            return;

        int amount = parseAmount(a3, 1);

        var type = plugin.cfg().findType(typeInput);
        if (type == null) {
            ctx.sender().sendMessage(Component.text("Unknown backpack type: " + typeInput));
            return;
        }

        for (int i = 0; i < amount; i++) {
            ItemStack item = backpackItems.create(type.id());
            target.getInventory().addItem(item);
            ensureOwnedBackpackRow(target, item);
        }

        ctx.sender().sendMessage(Component.text(
                "Gave " + target.getName() + " x" + amount + " backpack(s): " + type.id()));
    }

    @Override
    public List<String> tabComplete(CommandContext ctx) {
        if (ctx.size() == 1) {
            String prefix = safeLower(ctx.arg(0));
            return concatAndFilter(List.of("type", "module"), List.of(), prefix);
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

    private Player resolvePlayer(CommandContext ctx, String nameOrNull) {
        Player target = null;
        if (nameOrNull != null)
            target = Bukkit.getPlayerExact(nameOrNull);
        if (target == null && ctx.sender() instanceof Player p)
            target = p;

        if (target == null) {
            ctx.sender().sendMessage(Component.text("Console must specify a player."));
            return null;
        }
        return target;
    }

    private int parseAmount(String raw, int def) {
        if (raw == null)
            return def;
        try {
            int n = Integer.parseInt(raw);
            return Math.max(1, Math.min(64, n));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    private static List<String> concatAndFilter(List<String> a, List<String> b, String prefix) {
        return java.util.stream.Stream.concat(a.stream(), b.stream())
                .distinct()
                .filter(s -> s.toLowerCase().startsWith(prefix))
                .sorted()
                .toList();
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
}
