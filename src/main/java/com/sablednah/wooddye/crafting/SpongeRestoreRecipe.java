package com.sablednah.wooddye.crafting;

import com.sablednah.wooddye.WoodDyeConfig;
import com.sablednah.wooddye.registry.WoodDyeRecipes;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

/**
 * Restores eight fireproof blocks around one wet sponge, handing the sponge back instead of
 * consuming it — a wet sponge wrings the magma cream out without being used up, the way a water
 * bucket leaves its bucket behind.
 *
 * <p>The sponge comes back wet, or dry when {@code spongeDries} is set. That is read per craft, so
 * the config takes effect immediately. Restoring is never gated on {@code fireProof}: as in-world,
 * undoing fireproofing stays possible even where creating it is switched off.
 */
public class SpongeRestoreRecipe extends DelegatingShapedRecipe {

    public SpongeRestoreRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern,
            ItemStack result) {
        super(group, category, pattern, result);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = CraftingRecipe.defaultCraftingReminder(input);
        for (int slot = 0; slot < input.size(); slot++) {
            if (input.getItem(slot).is(Items.WET_SPONGE)) {
                remaining.set(slot, new ItemStack(
                        WoodDyeConfig.SPONGE_DRIES.get() ? Items.SPONGE : Items.WET_SPONGE));
            }
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<SpongeRestoreRecipe> getSerializer() {
        return WoodDyeRecipes.SPONGE_RESTORE.get();
    }
}
