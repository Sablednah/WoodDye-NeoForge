package com.sablednah.wooddye.crafting;

import java.util.List;

import com.sablednah.wooddye.WoodDyeConfig;
import com.sablednah.wooddye.registry.WoodDyeRecipes;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Fireproofs wood with magma cream on the bench, the crafting-grid twin of right-clicking a placed
 * block with it.
 *
 * <p>Identical to a shapeless recipe but for honouring the {@code fireProof} config: with it off,
 * the recipe simply never matches, so switching fireproofing off means <em>no</em> route to fireproof
 * wood, not just no in-world one. The config is read per match, so a change applies without a
 * restart.
 */
public class FireproofingRecipe extends DelegatingShapelessRecipe {

    public FireproofingRecipe(String group, CraftingBookCategory category, ItemStack result,
            List<Ingredient> ingredients) {
        super(group, category, result, ingredients);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return WoodDyeConfig.FIREPROOF.get() && super.matches(input, level);
    }

    @Override
    public RecipeSerializer<FireproofingRecipe> getSerializer() {
        return WoodDyeRecipes.FIREPROOFING.get();
    }
}
