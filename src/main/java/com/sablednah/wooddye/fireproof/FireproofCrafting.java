package com.sablednah.wooddye.fireproof;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

/**
 * Carries fireproofing through the crafting grid. Fireproof planks make fireproof stairs, slabs,
 * doors and the rest &mdash; through vanilla's own recipes, and any mod's, because this looks at
 * the inputs rather than at which recipe ran. Called from the shaped and shapeless recipe mixins.
 */
public final class FireproofCrafting {

    private FireproofCrafting() {}

    /**
     * Stamp {@code result} if it is wood and every wood input was stamped. A grid mixing fireproof
     * and plain wood gives plain wood: fireproofing is never conjured, only carried.
     */
    public static ItemStack carry(ItemStack result, CraftingInput input) {
        if (result.isEmpty() || !FireproofComponents.isWood(result)) {
            return result;
        }
        int wood = 0;
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack ingredient = input.getItem(slot);
            if (FireproofComponents.isWood(ingredient)) {
                if (!FireproofComponents.isStamped(ingredient)) {
                    return result;
                }
                wood++;
            }
        }
        return wood > 0 ? FireproofComponents.stamp(result) : result;
    }
}
