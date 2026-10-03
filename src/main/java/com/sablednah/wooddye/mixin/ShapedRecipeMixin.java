package com.sablednah.wooddye.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.sablednah.wooddye.fireproof.FireproofCrafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipe;

/** Fireproof wood in, fireproof wood out: see {@link FireproofCrafting}. */
@Mixin(ShapedRecipe.class)
abstract class ShapedRecipeMixin {

    @ModifyReturnValue(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"))
    private ItemStack wooddye$carryFireproof(ItemStack result, CraftingInput input) {
        return FireproofCrafting.carry(result, input);
    }
}
