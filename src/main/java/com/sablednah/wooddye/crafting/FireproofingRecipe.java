package com.sablednah.wooddye.crafting;

import com.sablednah.wooddye.WoodDyeConfig;
import com.sablednah.wooddye.registry.WoodDyeRecipes;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

/**
 * Fireproofs eight wooden blocks around one magma cream, the bench equivalent of right-clicking a
 * placed block with it — laid out like vanilla's stained glass, eight around the dye.
 *
 * <p>Identical to a shaped recipe but for honouring the {@code fireProof} config: with it off, the
 * recipe simply never matches, so switching fireproofing off means <em>no</em> route to fireproof
 * wood, not just no in-world one. The config is read per match, so a change applies without a
 * restart.
 */
public class FireproofingRecipe extends DelegatingShapedRecipe {

    public FireproofingRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern,
            ItemStack result) {
        super(group, category, pattern, result);
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
