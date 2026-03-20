package io.github.tootertutor.ModularPacks.modules.smithing;

import java.util.Iterator;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmithingTrimRecipe;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.plugin.Plugin;

import io.github.tootertutor.ModularPacks.listeners.module.ModuleClickHandler;
import io.github.tootertutor.ModularPacks.modules.ModuleLogicHelper;
import io.github.tootertutor.ModularPacks.util.ItemStacks;

public final class SmithingModuleLogic {

    private static final int TEMPLATE_SLOT = 0;
    private static final int BASE_SLOT = 1;
    private static final int ADDITION_SLOT = 2;
    private static final int OUTPUT_SLOT = 3;

    private SmithingModuleLogic() {
    }

    public static void updateResult(Inventory inv) {
        if (inv == null || inv.getSize() < 4)
            return;

        ItemStack template = inv.getItem(TEMPLATE_SLOT);
        ItemStack base = inv.getItem(BASE_SLOT);
        ItemStack addition = inv.getItem(ADDITION_SLOT);

        ItemStack result = computeResult(template, base, addition);
        inv.setItem(OUTPUT_SLOT, result);
    }

    public static boolean handleClick(InventoryClickEvent e, Player player) {
        return handleClick(null, e, player);
    }

    public static boolean handleClick(Plugin plugin, InventoryClickEvent e, Player player) {
        Inventory inv = e.getView().getTopInventory();
        if (inv == null || inv.getSize() < 4)
            return false;

        return ModuleClickHandler.handleOutput(plugin, e, player, OUTPUT_SLOT,
                () -> craftOnceToCursor(player, inv),
                () -> craftShift(player, inv),
                () -> updateResult(inv));
    }

    public static int preferredInsertSlot(ItemStack stack) {
        if (ItemStacks.isAir(stack))
            return -1;

        Material t = stack.getType();
        String name = t.name();
        if (name.endsWith("_SMITHING_TEMPLATE"))
            return TEMPLATE_SLOT;

        // Trim materials & netherite ingot typically go in the "addition" slot.
        if (asTrimMaterial(t) != null || t == Material.NETHERITE_INGOT)
            return ADDITION_SLOT;

        return BASE_SLOT;
    }

    private static void craftShift(Player player, Inventory inv) {
        ModuleLogicHelper.standardCraftShift(player, 64, () -> {
            ItemStack template = inv.getItem(TEMPLATE_SLOT);
            ItemStack base = inv.getItem(BASE_SLOT);
            ItemStack addition = inv.getItem(ADDITION_SLOT);
            return computeResult(template, base, addition);
        }, () -> {
            ItemStack template = inv.getItem(TEMPLATE_SLOT);
            ItemStack base = inv.getItem(BASE_SLOT);
            ItemStack addition = inv.getItem(ADDITION_SLOT);
            inv.setItem(TEMPLATE_SLOT, ModuleLogicHelper.decrementOne(template));
            inv.setItem(BASE_SLOT, ModuleLogicHelper.decrementOne(base));
            inv.setItem(ADDITION_SLOT, ModuleLogicHelper.decrementOne(addition));
        });
    }

    private static void craftOnceToCursor(Player player, Inventory inv) {
        ItemStack template = inv.getItem(TEMPLATE_SLOT);
        ItemStack base = inv.getItem(BASE_SLOT);
        ItemStack addition = inv.getItem(ADDITION_SLOT);
        ItemStack result = computeResult(template, base, addition);

        ModuleLogicHelper.standardCraftOnceToCursor(player, result, () -> {
            inv.setItem(TEMPLATE_SLOT, ModuleLogicHelper.decrementOne(template));
            inv.setItem(BASE_SLOT, ModuleLogicHelper.decrementOne(base));
            inv.setItem(ADDITION_SLOT, ModuleLogicHelper.decrementOne(addition));
        });
    }

    private static ItemStack computeResult(ItemStack template, ItemStack base, ItemStack addition) {
        if (ModuleLogicHelper.isEmpty(template) || ModuleLogicHelper.isEmpty(base)
                || ModuleLogicHelper.isEmpty(addition))
            return null;

        Iterator<Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            Recipe r = it.next();
            if (r instanceof SmithingTransformRecipe tr) {
                if (matchesTransform(tr, template, base, addition)) {
                    ItemStack result = tr.getResult();
                    if (ItemStacks.isAir(result))
                        return null;

                    ItemStack out = new ItemStack(result.getType(), Math.max(1, result.getAmount()));
                    if (base != null && base.hasItemMeta()) {
                        out.setItemMeta(base.getItemMeta());
                    }
                    return out;
                }
            } else if (r instanceof SmithingTrimRecipe trim) {
                if (matchesTrim(trim, template, base, addition)) {
                    return applyTrim(trim, base, addition);
                }
            }
        }

        return null;
    }

    private static boolean matchesTransform(SmithingTransformRecipe recipe, ItemStack template, ItemStack base,
            ItemStack addition) {
        if (recipe.getTemplate() == null || recipe.getBase() == null || recipe.getAddition() == null)
            return false;
        return recipe.getTemplate().test(template) && recipe.getBase().test(base)
                && recipe.getAddition().test(addition);
    }

    private static boolean matchesTrim(SmithingTrimRecipe recipe, ItemStack template, ItemStack base,
            ItemStack addition) {
        if (recipe.getTemplate() == null || recipe.getBase() == null || recipe.getAddition() == null)
            return false;
        return recipe.getTemplate().test(template) && recipe.getBase().test(base)
                && recipe.getAddition().test(addition);
    }

    private static ItemStack applyTrim(SmithingTrimRecipe recipe, ItemStack base, ItemStack addition) {
        if (ModuleLogicHelper.isEmpty(base) || ModuleLogicHelper.isEmpty(addition))
            return null;

        if (!(base.getItemMeta() instanceof ArmorMeta))
            return null;

        var pattern = recipe.getTrimPattern();
        if (pattern == null)
            return null;

        TrimMaterial material = asTrimMaterial(addition.getType());
        if (material == null)
            return null;

        ItemStack out = base.clone();
        out.setAmount(1);
        ArmorMeta meta = (ArmorMeta) out.getItemMeta();
        meta.setTrim(new ArmorTrim(material, pattern));
        out.setItemMeta(meta);
        return out;
    }

    private static TrimMaterial asTrimMaterial(Material mat) {
        if (mat == null)
            return null;

        return switch (mat) {
            case AMETHYST_SHARD -> TrimMaterial.AMETHYST;
            case COPPER_INGOT -> TrimMaterial.COPPER;
            case DIAMOND -> TrimMaterial.DIAMOND;
            case EMERALD -> TrimMaterial.EMERALD;
            case GOLD_INGOT -> TrimMaterial.GOLD;
            case IRON_INGOT -> TrimMaterial.IRON;
            case LAPIS_LAZULI -> TrimMaterial.LAPIS;
            case NETHERITE_INGOT -> TrimMaterial.NETHERITE;
            case QUARTZ -> TrimMaterial.QUARTZ;
            case REDSTONE -> TrimMaterial.REDSTONE;
            case RESIN_CLUMP -> TrimMaterial.RESIN;
            default -> null;
        };
    }

}
