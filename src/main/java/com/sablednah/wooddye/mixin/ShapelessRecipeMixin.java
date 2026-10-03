package com.sablednah.wooddye.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.sablednah.wooddye.fireproof.FireproofCrafting;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/** Fireproof wood in, fireproof wood out: see {@link FireproofCrafting}. A vanilla method, remapped. */
@Mixin(ShapelessRecipe.class)
abstract class ShapelessRecipeMixin {

    @Inject(method = "assemble(Lnet/minecraft/world/inventory/CraftingContainer;Lnet/minecraft/core/RegistryAccess;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"), cancellable = true)
    private void wooddye$carryFireproof(CraftingContainer input, RegistryAccess registries, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack carried = FireproofCrafting.carry(cir.getReturnValue(), input);
        if (carried != cir.getReturnValue()) {
            cir.setReturnValue(carried);
        }
    }
}
