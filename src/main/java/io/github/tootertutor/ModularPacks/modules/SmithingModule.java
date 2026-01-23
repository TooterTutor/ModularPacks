package io.github.tootertutor.ModularPacks.modules;

import java.util.Iterator;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.SmithingTrimRecipe;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.plugin.Plugin;

import io.github.tootertutor.ModularPacks.ModularPacksPlugin;
import io.github.tootertutor.ModularPacks.api.modules.AbstractModule;
import io.github.tootertutor.ModularPacks.config.ScreenType;
import io.github.tootertutor.ModularPacks.listeners.ModuleClickHandler;
import io.github.tootertutor.ModularPacks.util.ItemStacks;
import net.kyori.adventure.text.Component;

/**
 * Smithing module that provides a smithing table interface in backpacks.
 * Handles both transform recipes (netherite upgrade) and trim recipes (armor
 * customization).
 */
public final class SmithingModule extends AbstractModule {

    private static final int TEMPLATE_SLOT = 0;
    private static final int BASE_SLOT = 1;
    private static final int ADDITION_SLOT = 2;
    private static final int OUTPUT_SLOT = 3;

    public SmithingModule() {
        super("smithing", ScreenType.SMITHING, "Smithing Module");
    }

    @Override
    public void open(ModularPacksPlugin plugin, Player player, UUID backpackId, String backpackType, UUID moduleId) {
        if (plugin == null || player == null || !player.isOnline())
            return;

        InventoryView view = MenuType.SMITHING.builder()
                .title(Component.text("Smithing Module"))
                .location(player.getLocation())
                .checkReachable(false)
                .build(player);
        if (view == null)
            return;

        Inventory top = view.getTopInventory();

        // Load saved state
        byte[] state = loadState(plugin, backpackId, backpackType, moduleId);
        if (state != null && state.length > 0) {
            ItemStack[] saved = loadItemStackArray(state);
            int limit = Math.min(saved.length, top.getSize());
            for (int i = 0; i < limit; i++) {
                top.setItem(i, saved[i]);
            }
        }

        // Output is derived - clear it
        if (top.getSize() > 3) {
            top.setItem(OUTPUT_SLOT, null);
        }

        player.openInventory(view);

        // Verify the inventory actually opened (another plugin could have cancelled it)
        InventoryView current = player.getOpenInventory();
        if (current == null || current.getTopInventory() != view.getTopInventory()) {
            return;
        }

        createSession(player, backpackId, backpackType, moduleId);
        player.updateInventory();
    }

    @Override
    public void updateResult(Inventory inv) {
        if (inv == null || inv.getSize() < 4)
            return;

        ItemStack template = inv.getItem(TEMPLATE_SLOT);
        ItemStack base = inv.getItem(BASE_SLOT);
        ItemStack addition = inv.getItem(ADDITION_SLOT);

        ItemStack result = computeResult(template, base, addition);
        inv.setItem(OUTPUT_SLOT, result);
    }

    @Override
    public boolean isValidInventoryView(InventoryView view) {
        return view != null && view.getTopInventory().getSize() >= 4;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    /**
     * Handle a click event in the smithing module inventory.
     * 
     * @param plugin The plugin instance (optional, for delayed operations)
     * @param e      The click event
     * @param player The player who clicked
     * @return true if the click was handled
     */
    public boolean handleClick(Plugin plugin, InventoryClickEvent e, Player player) {
        Inventory inv = e.getView().getTopInventory();
        if (inv == null || inv.getSize() < 4)
            return false;

        return ModuleClickHandler.handleOutput(plugin, e, player, OUTPUT_SLOT,
                () -> craftOnceToCursor(player, inv),
                () -> craftShift(player, inv),
                () -> updateResult(inv));
    }

    /**
     * Determine the preferred insertion slot for an item being added to the
     * smithing table.
     * 
     * @param stack The item to insert
     * @return The preferred slot index, or -1 if the item shouldn't be inserted
     */
    public int preferredInsertSlot(ItemStack stack) {
        if (ItemStacks.isAir(stack))
            return -1;

        Material t = stack.getType();
        String name = t.name();
        if (name.endsWith("_SMITHING_TEMPLATE"))
            return TEMPLATE_SLOT;

        // Trim materials & netherite ingot typically go in the "addition" slot
        if (asTrimMaterial(t) != null || t == Material.NETHERITE_INGOT)
            return ADDITION_SLOT;

        return BASE_SLOT;
    }

    @Override
    protected byte[] serializeState(Inventory inventory) {
        ItemStack[] items = new ItemStack[inventory.getSize()];
        for (int i = 0; i < items.length; i++) {
            items[i] = inventory.getItem(i);
        }
        // Don't save the output slot (it's derived)
        if (items.length > 3)
            items[OUTPUT_SLOT] = null;

        return saveItemStackArray(items);
    }

    @Override
    protected void deserializeState(Inventory inventory, byte[] stateBytes) {
        ItemStack[] saved = loadItemStackArray(stateBytes);
        int limit = Math.min(saved.length, inventory.getSize());
        for (int i = 0; i < limit; i++) {
            inventory.setItem(i, saved[i]);
        }
    }

    // ========== Private Crafting Logic ==========

    private void craftShift(Player player, Inventory inv) {
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

    private void craftOnceToCursor(Player player, Inventory inv) {
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

    private ItemStack computeResult(ItemStack template, ItemStack base, ItemStack addition) {
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

    private boolean matchesTransform(SmithingTransformRecipe recipe, ItemStack template, ItemStack base,
            ItemStack addition) {
        if (recipe.getTemplate() == null || recipe.getBase() == null || recipe.getAddition() == null)
            return false;
        return recipe.getTemplate().test(template) && recipe.getBase().test(base)
                && recipe.getAddition().test(addition);
    }

    private boolean matchesTrim(SmithingTrimRecipe recipe, ItemStack template, ItemStack base, ItemStack addition) {
        if (recipe.getTemplate() == null || recipe.getBase() == null || recipe.getAddition() == null)
            return false;
        return recipe.getTemplate().test(template) && recipe.getBase().test(base)
                && recipe.getAddition().test(addition);
    }

    private ItemStack applyTrim(SmithingTrimRecipe recipe, ItemStack base, ItemStack addition) {
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

    private TrimMaterial asTrimMaterial(Material mat) {
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
