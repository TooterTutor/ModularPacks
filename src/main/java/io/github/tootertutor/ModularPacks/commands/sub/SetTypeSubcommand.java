package io.github.tootertutor.ModularPacks.commands.sub;

import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.commands.AbstractSubcommand;
import io.github.tootertutor.ModularPacks.commands.CommandContext;
import io.github.tootertutor.ModularPacks.data.BackpackData;
import io.github.tootertutor.ModularPacks.data.ItemStackCodec;
import io.github.tootertutor.ModularPacks.item.BackpackItems;
import io.github.tootertutor.ModularPacks.item.Keys;
import io.github.tootertutor.ModularPacks.util.ItemStacks;

/**
 * Change a backpack's type/tier and update the database.
 */
public final class SetTypeSubcommand extends AbstractSubcommand {

    private final ModularPacksPlugin plugin;
    private final BackpackItems backpackItems;

    public SetTypeSubcommand(ModularPacksPlugin plugin) {
        this.plugin = plugin;
        this.backpackItems = new BackpackItems(plugin);
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
    public String permission() {
        return "modularpacks.admin";
    }

    @Override
    public String getUsage() {
        return "backpack settype <typeId> [--force]";
    }

    @Override
    public String getExtendedHelp() {
        return "Hold a backpack in your main hand. Edits that backpack's type.\n"
                + "Use --force to truncate items if the new size is smaller.";
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!checkPermission(ctx))
            return;

        Player player = requirePlayer(ctx);
        if (player == null)
            return;

        if (!requireArgs(ctx, 1))
            return;

        String newTypeId = ctx.arg(0);
        boolean force = ctx.args().stream().anyMatch(s -> "--force".equalsIgnoreCase(s));

        var newType = plugin.cfg().findType(newTypeId);
        if (newType == null) {
            ctx.sendError("Unknown backpack type: " + newTypeId);
            return;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        Keys keys = plugin.keys();
        UUID backpackId = readBackpackId(keys, hand);
        String oldTypeId = readBackpackType(keys, hand);
        if (backpackId == null || oldTypeId == null) {
            ctx.sendError("Hold a backpack item in your main hand.");
            return;
        }

        String currentDbType = plugin.repo().findBackpackType(backpackId);
        String effectiveOldType = (currentDbType != null ? currentDbType : oldTypeId);

        BackpackData data = plugin.repo().loadOrCreate(backpackId, effectiveOldType);

        int oldSize = plugin.cfg().findType(effectiveOldType) != null
                ? plugin.cfg().findType(effectiveOldType).rows() * 9
                : data.contentsBytes() == null ? 0 : ItemStackCodec.fromBytes(data.contentsBytes()).length;
        int newSize = newType.rows() * 9;

        if (newSize < oldSize && !force) {
            ItemStack[] logical = ItemStackCodec.fromBytes(data.contentsBytes());
            for (int i = newSize; i < logical.length; i++) {
                if (ItemStacks.isNotAir(logical[i])) {
                    ctx.sendError("Backpack has items beyond the new size. Use --force to truncate.");
                    return;
                }
            }
        }

        // Update DB row type (contents preserved; resize handled by render/load).
        data.backpackType(newType.id());
        plugin.repo().saveBackpack(data);
        plugin.sessions().refreshLinkedBackpacksThrottled(backpackId, data);

        ItemStack updated = backpackItems.createExisting(backpackId, newType.id());
        updated.setAmount(Math.max(1, hand.getAmount()));
        player.getInventory().setItemInMainHand(updated);

        ctx.sendInfo("Updated backpack " + backpackId + " to type " + newType.id());
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
        if (ItemStacks.isAir(item) || !item.hasItemMeta())
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
        if (ItemStacks.isAir(item) || !item.hasItemMeta())
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
