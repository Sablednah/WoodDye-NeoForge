package com.sablednah.wooddye.crafting;

import com.sablednah.wooddye.WoodDyeConfig;
import com.sablednah.wooddye.fireproof.FireproofComponents;
import com.sablednah.wooddye.registry.WoodDyeRecipes;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * The bench half of fireproofing by mark, for any wood at all: eight of one wood item around a
 * Magma Cream gives eight fireproof ones, and eight fireproof ones around a Wet Sponge gives eight
 * plain ones back, with the sponge handed back (dry under {@code spongeDries}). Two instances of
 * one class, told apart by {@link #stamping}.
 *
 * <p>These replace a generated recipe per block: because the result is the same item with a
 * component, one recipe each way covers every wood, including a mod's.
 */
public class FireproofStampRecipe extends CustomRecipe {

    private static final int WOOD = 8;

    private final boolean stamping;

    public FireproofStampRecipe(CraftingBookCategory category, boolean stamping) {
        super(category);
        this.stamping = stamping;
    }

    /** The wood item eight of which fill the grid around the treatment, or empty if the grid is not that. */
    private ItemStack woodOf(CraftingInput input) {
        ItemStack wood = ItemStack.EMPTY;
        int woodCount = 0;
        int treatment = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(stamping ? Items.MAGMA_CREAM : Items.WET_SPONGE)) {
                treatment++;
            } else if (FireproofComponents.isWood(stack) && FireproofComponents.isStamped(stack) != stamping) {
                if (wood.isEmpty()) {
                    wood = stack;
                } else if (!ItemStack.isSameItemSameComponents(wood, stack)) {
                    return ItemStack.EMPTY; // two different woods
                }
                woodCount++;
            } else {
                return ItemStack.EMPTY;
            }
        }
        return treatment == 1 && woodCount == WOOD ? wood : ItemStack.EMPTY;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (stamping && !WoodDyeConfig.FIREPROOF.get()) {
            return false; // same gate as the in-world click: no route to fireproof wood while off
        }
        return !woodOf(input).isEmpty();
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = woodOf(input).copyWithCount(WOOD);
        return stamping ? FireproofComponents.stamp(result) : FireproofComponents.strip(result);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = super.getRemainingItems(input);
        if (!stamping) {
            for (int slot = 0; slot < input.size(); slot++) {
                if (input.getItem(slot).is(Items.WET_SPONGE)) {
                    remaining.set(slot, new ItemStack(WoodDyeConfig.SPONGE_DRIES.get() ? Items.SPONGE : Items.WET_SPONGE));
                }
            }
        }
        return remaining;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return stamping ? WoodDyeRecipes.FIREPROOF_STAMP.get() : WoodDyeRecipes.FIREPROOF_UNSTAMP.get();
    }
}
