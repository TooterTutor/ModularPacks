package io.github.tootertutor.ModularPacks.recipes;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

/**
 * Main-thread view of the recipe registry, preserving Bukkit's iteration order.
 */
public final class RecipeIndex {

    // Bukkit has no general event for recipe changes made by other plugins.
    private static final int REFRESH_INTERVAL_TICKS = 100;
    private static RecipeIndex current;

    private final int builtAt = Bukkit.getCurrentTick();
    private final List<CookingRecipe<?>> cooking = new ArrayList<>();
    private final List<Recipe> crafting = new ArrayList<>();
    private final List<SmithingRecipe> smithing = new ArrayList<>();
    private final List<StonecuttingRecipe> stonecutting = new ArrayList<>();

    private RecipeIndex() {
        Bukkit.recipeIterator().forEachRemaining(recipe -> {
            switch (recipe) {
                case CookingRecipe<?> entry -> cooking.add(entry);
                case ShapedRecipe entry -> crafting.add(entry);
                case ShapelessRecipe entry -> crafting.add(entry);
                case SmithingRecipe entry -> smithing.add(entry);
                case StonecuttingRecipe entry -> stonecutting.add(entry);
                default -> {
                }
            }
        });
    }

    private static RecipeIndex current() {
        if (current == null || Bukkit.getCurrentTick() - current.builtAt >= REFRESH_INTERVAL_TICKS) {
            current = new RecipeIndex();
        }
        return current;
    }

    public static void invalidate() {
        current = null;
    }

    public static Iterable<CookingRecipe<?>> cooking() {
        return current().cooking;
    }

    public static Iterable<Recipe> crafting() {
        return current().crafting;
    }

    public static Iterable<SmithingRecipe> smithing() {
        return current().smithing;
    }

    public static Iterable<StonecuttingRecipe> stonecutting() {
        return current().stonecutting;
    }
}
