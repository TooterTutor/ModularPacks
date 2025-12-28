package io.github.tootertutor.SophiBackpacks.commands.sub;

import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.SophiBackpacks.SophiBackpacksPlugin;
import io.github.tootertutor.SophiBackpacks.commands.CommandContext;
import io.github.tootertutor.SophiBackpacks.commands.Subcommand;
import io.github.tootertutor.SophiBackpacks.data.BackpackData;
import io.github.tootertutor.SophiBackpacks.data.ItemStackCodec;
import io.github.tootertutor.SophiBackpacks.item.Keys;
import io.github.tootertutor.SophiBackpacks.text.Text;
import net.kyori.adventure.text.Component;

public final class SetTypeSubcommand implements Subcommand {

    private final SophiBackpacksPlugin plugin;

    public SetTypeSubcommand(SophiBackpacksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "settype";
    }

    @Override
    public String description() {
        return "Change a backpack item's tier/type (updates DB)";
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!ctx.sender().hasPermission("sophibackpacks.admin")) {
            ctx.sender().sendMessage(Component.text("You do not have permission."));
            return;
        }

        if (!(ctx.sender() instanceof Player player)) {
            ctx.sender().sendMessage(Component.text("This command must be run by a player."));
            return;
        }

        if (ctx.size() < 1) {
            ctx.sender().sendMessage(Component.text("Usage: /backpack settype <typeId> [--force]"));
            ctx.sender().sendMessage(Component.text("Edits the backpack in your main hand."));
            return;
        }

        String newTypeId = ctx.arg(0);
        boolean force = ctx.args().stream().anyMatch(s -> "--force".equalsIgnoreCase(s));

        var newType = plugin.cfg().findType(newTypeId);
        if (newType == null) {
            ctx.sender().sendMessage(Component.text("Unknown backpack type: " + newTypeId));
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        Keys keys = plugin.keys();
        UUID backpackId = readBackpackId(keys, hand);
        String oldTypeId = readBackpackType(keys, hand);
        if (backpackId == null || oldTypeId == null) {
            ctx.sender().sendMessage(Component.text("Hold a backpack item in your main hand."));
            return;
        }

        String currentDbType = plugin.repo().findBackpackType(backpackId);
        String effectiveOldType = (currentDbType != null ? currentDbType : oldTypeId);

        BackpackData data = plugin.repo().loadOrCreate(backpackId, effectiveOldType);

        int oldSize = plugin.cfg().findType(effectiveOldType) != null ? plugin.cfg().findType(effectiveOldType).rows() * 9
                : data.contentsBytes() == null ? 0 : ItemStackCodec.fromBytes(data.contentsBytes()).length;
        int newSize = newType.rows() * 9;

        if (newSize < oldSize && !force) {
            ItemStack[] logical = ItemStackCodec.fromBytes(data.contentsBytes());
            for (int i = newSize; i < logical.length; i++) {
                if (logical[i] != null && !logical[i].getType().isAir()) {
                    ctx.sender().sendMessage(Component.text(
                            "Backpack has items beyond the new size; use --force to truncate."));
                    return;
                }
            }
        }

        // Update DB row type (contents preserved; resize handled by render/load).
        data.backpackType(newType.id());
        plugin.repo().saveBackpack(data);

        // Update item meta (keep same ID).
        ItemStack updated = new ItemStack(newType.outputMaterial(), Math.max(1, hand.getAmount()));
        ItemMeta meta = updated.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(keys.BACKPACK_ID, PersistentDataType.STRING, backpackId.toString());
            meta.getPersistentDataContainer().set(keys.BACKPACK_TYPE, PersistentDataType.STRING, newType.id());
            meta.displayName(Text.c(newType.displayName()));
            updated.setItemMeta(meta);
        }
        player.getInventory().setItemInMainHand(updated);

        ctx.sender().sendMessage(Component.text("Updated backpack " + backpackId + " to type " + newType.id()));
    }

    @Override
    public List<String> tabComplete(CommandContext ctx) {
        if (ctx.size() == 1) {
            String prefix = safeLower(ctx.arg(0));
            return plugin.cfg().getTypes().stream()
                    .map(t -> t.id())
                    .filter(s -> s.toLowerCase().startsWith(prefix))
                    .sorted()
                    .toList();
        }
        if (ctx.size() == 2) {
            String prefix = safeLower(ctx.arg(1));
            return List.of("--force").stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }

    private static UUID readBackpackId(Keys keys, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta())
            return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return null;
        String idStr = meta.getPersistentDataContainer().get(keys.BACKPACK_ID, PersistentDataType.STRING);
        if (idStr == null || idStr.isBlank())
            return null;
        try {
            return UUID.fromString(idStr);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String readBackpackType(Keys keys, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta())
            return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return null;
        return meta.getPersistentDataContainer().get(keys.BACKPACK_TYPE, PersistentDataType.STRING);
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase();
    }
}
