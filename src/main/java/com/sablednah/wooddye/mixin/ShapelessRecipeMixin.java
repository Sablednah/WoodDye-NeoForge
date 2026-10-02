package com.sablednah.wooddye.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.sablednah.wooddye.fireproof.FireproofCrafting;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/** Fireproof wood in, fireproof wood out: see {@link FireproofCrafting}. */
@Mixin(ShapelessRecipe.class)
abstract class ShapelessRecipeMixin {

    @ModifyReturnValue(method = "assemble(Lnet/minecraft/world/item/crafting/CraftingInput;Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"))
    private ItemStack wooddye$carryFireproof(ItemStack result, CraftingInput input, HolderLookup.Provider registries) {
        return FireproofCrafting.carry(result, input);
    }
}
