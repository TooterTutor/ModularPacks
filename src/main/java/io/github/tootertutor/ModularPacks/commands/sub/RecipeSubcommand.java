package io.github.tootertutor.ModularPacks.commands.sub;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.commands.CommandContext;
import io.github.tootertutor.ModularPacks.commands.Subcommand;
import io.github.tootertutor.ModularPacks.config.Placeholders;
import io.github.tootertutor.ModularPacks.gui.RecipePreviewUi;
import io.github.tootertutor.ModularPacks.text.Text;
import net.kyori.adventure.text.Component;

public final class RecipeSubcommand implements Subcommand {

    private final ModularPacksPlugin plugin;

    public RecipeSubcommand(ModularPacksPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String name() {
        return "recipe";
    }

    @Override
    public String description() {
        return "Preview a backpack/module recipe";
    }

    @Override
    public String permission() {
        return "modularpacks.recipe";
    }

    @Override
    public void execute(CommandContext ctx) {
        if (!(ctx.sender() instanceof Player player)) {
            ctx.sender().sendMessage(Component.text("This command must be run by a player."));
            return;
        }

        if (!player.hasPermission("modularpacks.recipe")) {
            player.sendMessage(Component.text("You do not have permission."));
            return;
        }

        if (ctx.size() < 2) {
            ctx.sender().sendMessage(Component.text("Usage: /backpack recipe backpack <typeId> [variantId]"));
            ctx.sender().sendMessage(Component.text("   or: /backpack recipe module <upgradeId>"));
            return;
        }

        String category = ctx.arg(0);
        String id = ctx.arg(1);

        if ("module".equalsIgnoreCase(category) || "upgrade".equalsIgnoreCase(category)) {
            openUpgradeRecipe(player, id);
            return;
        }

        if ("backpack".equalsIgnoreCase(category) || "type".equalsIgnoreCase(category)) {
            String variant = ctx.arg(2);
            openBackpackRecipe(player, id, variant);
            return;
        }

        ctx.sender().sendMessage(Component.text("Usage: /backpack recipe backpack <typeId> [variantId]"));
        ctx.sender().sendMessage(Component.text("   or: /backpack recipe module <upgradeId>"));
    }

    @Override
    public List<String> tabComplete(CommandContext ctx) {
        if (ctx.size() == 1) {
            String prefix = safeLower(ctx.arg(0));
            return List.of("backpack", "module").stream()
                    .filter(s -> s.startsWith(prefix))
                    .sorted()
                    .toList();
        }

        if (ctx.size() == 2) {
            String cat = ctx.arg(0);
            String prefix = safeLower(ctx.arg(1));
            if ("module".equalsIgnoreCase(cat) || "upgrade".equalsIgnoreCase(cat)) {
                return plugin.cfg().getUpgrades().stream()
                        .map(u -> u.id())
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .sorted()
                        .toList();
            }
            if ("backpack".equalsIgnoreCase(cat) || "type".equalsIgnoreCase(cat)) {
                return plugin.cfg().getTypes().stream()
                        .map(t -> t.id())
                        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .sorted()
                        .toList();
            }
        }

        if (ctx.size() == 3 && ("backpack".equalsIgnoreCase(ctx.arg(0)) || "type".equalsIgnoreCase(ctx.arg(0)))) {
            var type = plugin.cfg().findType(ctx.arg(1));
            if (type == null)
                return List.of();
            ConfigurationSection typeSec = plugin.getConfig().getConfigurationSection("BackpackTypes." + type.id());
            List<RecipeVariant> variants = readRecipeVariants(typeSec);
            String prefix = safeLower(ctx.arg(2));
            return variants.stream()
                    .map(v -> v.id)
                    .filter(s -> s != null && s.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .toList();
        }

        return List.of();
    }

    private void openUpgradeRecipe(Player player, String upgradeIdInput) {
        var def = plugin.cfg().findUpgrade(upgradeIdInput);
        if (def == null) {
            player.sendMessage(Component.text("Unknown module/upgrade id: " + upgradeIdInput));
            return;
        }

        ConfigurationSection upgradeSec = plugin.getConfig().getConfigurationSection("Upgrades." + def.id());
        if (upgradeSec == null) {
            player.sendMessage(Component.text("Missing config section: Upgrades." + def.id()));
            return;
        }

        ConfigurationSection recipeSec = upgradeSec.getConfigurationSection("CraftingRecipe");
        if (recipeSec == null) {
            player.sendMessage(Component.text("No CraftingRecipe configured for module: " + def.id()));
            return;
        }

        String kind = recipeSec.getString("Type", "Crafting");
        if (!"Crafting".equalsIgnoreCase(kind)) {
            player.sendMessage(Component.text("Unsupported recipe type for module: " + kind));
            return;
        }

        ItemStack[] grid = buildCraftingGrid(recipeSec, new UUIDResolver());
        Component title = Component.text("Recipe: Module " + def.id());
        ItemStack out = createUpgradePreview(def.id());
        RecipePreviewUi.openCrafting(plugin, player, title, grid, out);
    }

    private void openBackpackRecipe(Player player, String typeIdInput, String variantIdOrNull) {
        var type = plugin.cfg().findType(typeIdInput);
        if (type == null) {
            player.sendMessage(Component.text("Unknown backpack type: " + typeIdInput));
            return;
        }

        ConfigurationSection typeSec = plugin.getConfig().getConfigurationSection("BackpackTypes." + type.id());
        if (typeSec == null) {
            player.sendMessage(Component.text("Missing config section: BackpackTypes." + type.id()));
            return;
        }

        List<RecipeVariant> variants = readRecipeVariants(typeSec);
        if (variants.isEmpty()) {
            player.sendMessage(Component.text("No CraftingRecipe configured for backpack: " + type.id()));
            return;
        }

        RecipeVariant chosen = null;
        if (variantIdOrNull != null && !variantIdOrNull.isBlank()) {
            for (RecipeVariant v : variants) {
                if (v != null && v.id != null && v.id.equalsIgnoreCase(variantIdOrNull)) {
                    chosen = v;
                    break;
                }
            }
            if (chosen == null) {
                player.sendMessage(Component.text("Unknown variant: " + variantIdOrNull));
                player.sendMessage(Component.text(
                        "Available variants: " + variants.stream().map(v -> v.id).sorted().toList()));
                return;
            }
        } else {
            chosen = variants.get(0);
            if (variants.size() > 1) {
                player.sendMessage(Component.text("Multiple variants available: "
                        + variants.stream().map(v -> v.id).sorted().toList()
                        + " (use /backpack recipe backpack " + type.id() + " <variantId>)"));
            }
        }

        ConfigurationSection recipe = chosen.section;
        String kind = recipe.getString("Type", "Crafting");

        if ("Crafting".equalsIgnoreCase(kind)) {
            ItemStack[] grid = buildCraftingGrid(recipe, new UUIDResolver());
            Component title = Component.text("Recipe: Backpack " + type.id() + " (" + chosen.id + ")");
            ItemStack out = createBackpackPreview(type.id());
            RecipePreviewUi.openCrafting(plugin, player, title, grid, out);
            return;
        }

        if ("Smithing".equalsIgnoreCase(kind)) {
            String templateStr = recipe.getString("Template");
            String baseStr = recipe.getString("Base");
            String additionStr = recipe.getString("Addition");
            if (templateStr == null || baseStr == null || additionStr == null) {
                player.sendMessage(Component.text("Smithing recipe is missing Template/Base/Addition."));
                return;
            }

            Material templateMat = parseMaterial(templateStr);
            Material additionMat = parseMaterial(additionStr);
            String baseTypeId = resolveBackpackTypeId(baseStr);
            if (templateMat == null || additionMat == null || baseTypeId == null) {
                player.sendMessage(Component.text("Invalid smithing recipe config for backpack: " + type.id()));
                return;
            }

            ItemStack template = new ItemStack(templateMat);
            ItemStack base = createBackpackPreview(baseTypeId);
            ItemStack addition = new ItemStack(additionMat);
            Component title = Component.text("Recipe: Backpack " + type.id() + " (Smithing)");
            ItemStack out = createBackpackPreview(type.id());
            RecipePreviewUi.openSmithing(plugin, player, title, template, base, addition, out);
            return;
        }

        player.sendMessage(Component.text("Unsupported recipe type: " + kind));
    }

    private ItemStack[] buildCraftingGrid(ConfigurationSection recipeSec, UUIDResolver uuids) {
        List<String> pattern = recipeSec.getStringList("Pattern");
        ConfigurationSection ing = recipeSec.getConfigurationSection("Ingredients");

        ItemStack[] grid = new ItemStack[9];
        if (pattern == null || pattern.isEmpty())
            return grid;

        for (int row = 0; row < Math.min(3, pattern.size()); row++) {
            String line = pattern.get(row);
            if (line == null)
                continue;
            for (int col = 0; col < Math.min(3, line.length()); col++) {
                char c = line.charAt(col);
                if (c == ' ')
                    continue;
                String raw = ing == null ? null : ing.getString(String.valueOf(c));
                ParsedIngredient parsed = parseIngredient(raw);
                grid[row * 3 + col] = toDisplayItem(parsed, uuids);
            }
        }
        return grid;
    }

    private ItemStack toDisplayItem(ParsedIngredient parsed, UUIDResolver uuids) {
        if (parsed == null)
            return null;
        return switch (parsed.kind) {
            case MATERIAL -> parsed.material == null ? null : new ItemStack(parsed.material);
            case BACKPACK_TYPE -> {
                yield createBackpackPreview(parsed.id);
            }
            case MODULE_TYPE -> createUpgradePreview(parsed.id);
        };
    }

    private ItemStack createBackpackPreview(String typeId) {
        var type = plugin.cfg().findType(typeId);
        if (type == null)
            return null;

        ItemStack item = new ItemStack(type.outputMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        meta.displayName(Text.c(type.displayName()));

        List<String> lore = type.lore();
        if (lore != null && !lore.isEmpty()) {
            List<String> filtered = lore.stream()
                    .filter(s -> s != null && !s.contains("{backpackId}"))
                    .toList();
            if (!filtered.isEmpty()) {
                meta.lore(Text.lore(Placeholders.expandBackpackLore(plugin, type, null, filtered)));
            } else {
                meta.lore(null);
            }
        } else {
            meta.lore(null);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createUpgradePreview(String upgradeId) {
        var def = plugin.cfg().findUpgrade(upgradeId);
        if (def == null)
            return null;

        ItemStack item = new ItemStack(def.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null)
            return item;

        meta.displayName(Text.c(Placeholders.expandText(plugin, def, item, def.displayName())));
        List<String> lore = def.lore();
        if (lore != null && !lore.isEmpty()) {
            meta.lore(Text.lore(Placeholders.expandLore(plugin, def, lore)));
        } else {
            meta.lore(null);
        }

        item.setItemMeta(meta);
        return item;
    }

    private enum IngredientKind {
        MATERIAL,
        BACKPACK_TYPE,
        MODULE_TYPE
    }

    private record ParsedIngredient(IngredientKind kind, String id, Material material) {
    }

    private ParsedIngredient parseIngredient(String raw) {
        if (raw == null || raw.isBlank())
            return new ParsedIngredient(IngredientKind.MATERIAL, null, null);

        String s = raw.trim();

        Material m = parseMaterial(s);
        if (m != null)
            return new ParsedIngredient(IngredientKind.MATERIAL, null, m);

        if (s.regionMatches(true, 0, "BACKPACK:", 0, "BACKPACK:".length())) {
            String typeToken = s.substring("BACKPACK:".length()).trim();
            String typeId = resolveBackpackTypeId(typeToken);
            if (typeId != null)
                return new ParsedIngredient(IngredientKind.BACKPACK_TYPE, typeId, Material.PLAYER_HEAD);
        }

        if (s.regionMatches(true, 0, "MODULE:", 0, "MODULE:".length())
                || s.regionMatches(true, 0, "UPGRADE:", 0, "UPGRADE:".length())) {
            int idx = s.indexOf(':');
            String upgradeToken = idx >= 0 ? s.substring(idx + 1).trim() : "";
            var def = plugin.cfg().findUpgrade(upgradeToken);
            if (def != null)
                return new ParsedIngredient(IngredientKind.MODULE_TYPE, def.id(), def.material());
        }

        if (s.toLowerCase(Locale.ROOT).endsWith("_backpack")) {
            String typeId = resolveBackpackTypeId(s);
            if (typeId != null)
                return new ParsedIngredient(IngredientKind.BACKPACK_TYPE, typeId, Material.PLAYER_HEAD);
        }

        return new ParsedIngredient(IngredientKind.MATERIAL, null, null);
    }

    private static Material parseMaterial(String name) {
        if (name == null)
            return null;
        String s = name.trim();
        if (s.isEmpty())
            return null;
        if (s.regionMatches(true, 0, "minecraft:", 0, "minecraft:".length())) {
            s = s.substring("minecraft:".length());
        }
        s = s.trim().replace(' ', '_').toUpperCase(Locale.ROOT);
        return Material.getMaterial(s);
    }

    private String resolveBackpackTypeId(String token) {
        if (token == null || token.isBlank())
            return null;
        String s = token.trim();
        if (s.regionMatches(true, 0, "BACKPACK:", 0, "BACKPACK:".length()))
            s = s.substring("BACKPACK:".length()).trim();
        if (s.toLowerCase(Locale.ROOT).endsWith("_backpack")) {
            s = s.substring(0, s.length() - "_backpack".length());
        }
        var type = plugin.cfg().findType(s);
        return type == null ? null : type.id();
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private record RecipeVariant(String id, ConfigurationSection section) {
    }

    private static List<RecipeVariant> readRecipeVariants(ConfigurationSection typeSec) {
        if (typeSec == null)
            return List.of();

        ConfigurationSection sec = typeSec.getConfigurationSection("CraftingRecipe");
        if (sec != null) {
            if (sec.contains("Type") || sec.contains("Pattern") || sec.contains("Ingredients")) {
                return List.of(new RecipeVariant("main", sec));
            }
            ArrayList<RecipeVariant> out = new ArrayList<>();
            for (String k : sec.getKeys(false)) {
                ConfigurationSection child = sec.getConfigurationSection(k);
                if (child != null) {
                    out.add(new RecipeVariant(k, child));
                }
            }
            if (!out.isEmpty())
                return out;
        }

        List<?> rawList = typeSec.getList("CraftingRecipe");
        if (rawList == null || rawList.isEmpty())
            return List.of();

        ArrayList<RecipeVariant> out = new ArrayList<>();
        int idx = 0;
        for (Object elem : rawList) {
            String fallbackId = Integer.toString(idx + 1);

            ConfigurationSection direct = asSection(elem);
            if (direct != null) {
                out.add(new RecipeVariant(fallbackId, direct));
                idx++;
                continue;
            }

            if (elem instanceof Map<?, ?> wrapper && !wrapper.isEmpty()) {
                if (wrapper.containsKey("Type") || wrapper.containsKey("Pattern") || wrapper.containsKey("Ingredients")) {
                    ConfigurationSection child = asSection(wrapper);
                    if (child != null)
                        out.add(new RecipeVariant(fallbackId, child));
                    idx++;
                    continue;
                }

                for (Map.Entry<?, ?> e : wrapper.entrySet()) {
                    String id = e.getKey() == null ? fallbackId : e.getKey().toString();
                    ConfigurationSection child = asSection(e.getValue());
                    if (child != null)
                        out.add(new RecipeVariant(id, child));
                }
            }
            idx++;
        }
        return out;
    }

    private static ConfigurationSection asSection(Object value) {
        if (value == null)
            return null;
        if (value instanceof ConfigurationSection cs)
            return cs;
        if (value instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) m;
            MemoryConfiguration mem = new MemoryConfiguration();
            return mem.createSection("r", typed);
        }
        return null;
    }

    private static final class UUIDResolver {
        // placeholder for future slot-specific rendering; kept to avoid changing the call sites too much.
        @SuppressWarnings("unused")
        private final Map<String, Object> unused = new java.util.HashMap<>();
    }
}
