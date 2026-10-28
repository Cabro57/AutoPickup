package us.thezircon.play.autopickup.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import us.thezircon.play.autopickup.AutoPickup;

import java.util.*;

import static us.thezircon.play.autopickup.AutoPickup.smeltRecipeCache;

public class AutoSmeltUtils {

    private static final AutoPickup PLUGIN = AutoPickup.getPlugin(AutoPickup.class);

    public boolean isAutoSmeltEnabled = false;
    public static Material[] ignore = {Material.COAL_ORE, Material.REDSTONE_ORE, Material.DIAMOND_ORE, Material.EMERALD_ORE, Material.LAPIS_ORE, Material.NETHER_QUARTZ_ORE};
    public static List<Material> ignoreMaterials = Collections.unmodifiableList(Arrays.asList(ignore));

    public boolean isEnabled() {
        return isAutoSmeltEnabled;
    }

    /** Furnace recipes of custom items (their ingredient is an exact item, e.g. ItemsAdder items). */
    private static final List<CookingRecipe<?>> customRecipes = new ArrayList<>();
    /** When the custom recipes were read last (ms). Plugins like ItemsAdder add their recipes after we enable. */
    private static long customRecipesLoadedAt = 0;
    private static final long CUSTOM_RECIPES_REFRESH_MS = 30000;

    public static void loadFurnaceRecipes(Map<Material, FurnaceRecipe> smeltRecipeCache) {
        Iterator<Recipe> iter = Bukkit.recipeIterator();
        while (iter.hasNext()) {
            Recipe recipe = iter.next();
            if (recipe instanceof FurnaceRecipe) {
                FurnaceRecipe furnaceRecipe = (FurnaceRecipe) recipe;
                // Recipes of custom items must not be cached by their base material, or every
                // plain item of that material (paper...) would smelt into the custom result.
                if (furnaceRecipe.getInputChoice() instanceof RecipeChoice.ExactChoice) {
                    continue;
                }
                Material inputType = furnaceRecipe.getInput().getType();
                // Only cache the first found recipe per material
                smeltRecipeCache.putIfAbsent(inputType, furnaceRecipe);
            }
        }
    }

    /**
     * Reads the furnace recipes whose ingredient is an exact item (custom items of other plugins).
     */
    private static void loadCustomRecipes() {
        customRecipes.clear();
        Iterator<Recipe> iter = Bukkit.recipeIterator();
        while (iter.hasNext()) {
            Recipe recipe = iter.next();
            if (recipe instanceof FurnaceRecipe || recipe instanceof BlastingRecipe) {
                CookingRecipe<?> cooking = (CookingRecipe<?>) recipe;
                if (cooking.getInputChoice() instanceof RecipeChoice.ExactChoice) {
                    customRecipes.add(cooking);
                }
            }
        }
        customRecipesLoadedAt = System.currentTimeMillis();
    }

    /**
     * Finds the furnace recipe of a custom item (an item with meta, like an ItemsAdder ore or raw metal).
     *
     * @param itemStack item to smelt
     * @return recipe or null if no plugin registered a furnace recipe for exactly this item
     */
    private static CookingRecipe<?> findCustomRecipe(ItemStack itemStack) {
        // Other plugins register recipes after we enable, so look again now and then
        if (System.currentTimeMillis() - customRecipesLoadedAt > CUSTOM_RECIPES_REFRESH_MS) {
            loadCustomRecipes();
        }
        ItemStack single = itemStack.clone();
        single.setAmount(1);
        for (CookingRecipe<?> recipe : customRecipes) {
            if (recipe.getInputChoice().test(single)) {
                return recipe;
            }
        }
        return null;
    }


    public static ItemStack smelt(ItemStack itemStack, Player player) {
        List<String> blacklist = PLUGIN.getBlacklistConf().getStringList("AutoSmeltBlacklist");

        if (ignoreMaterials.contains(itemStack.getType()) || blacklist.contains(itemStack.getType().toString())) {
            return itemStack;
        }

        // Special case: logs into charcoal
        if (Tag.LOGS_THAT_BURN.isTagged(itemStack.getType())) {
            player.giveExp(0); // Adjust XP if needed
            return new ItemStack(Material.CHARCOAL, itemStack.getAmount());
        }

        // Custom items (ItemsAdder ores, raw metals...) have their own recipe for exactly that item
        if (itemStack.hasItemMeta()) {
            CookingRecipe<?> custom = findCustomRecipe(itemStack);
            if (custom != null) {
                ItemStack result = custom.getResult().clone();
                result.setAmount(result.getAmount() * itemStack.getAmount());
                player.giveExp((int) (custom.getExperience() * itemStack.getAmount()));
                return result;
            }
        }

        FurnaceRecipe recipe = smeltRecipeCache.get(itemStack.getType());
        if (recipe != null) {
            ItemStack result = recipe.getResult().clone();
            result.setAmount(itemStack.getAmount());
            player.giveExp((int) (recipe.getExperience() * itemStack.getAmount()));
            return result;
        }

        return itemStack;
    }

}
