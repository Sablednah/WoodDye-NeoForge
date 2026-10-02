package com.sablednah.wooddye.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.sablednah.wooddye.fireproof.Fireproofing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.LavaFluid;

/** Lava does not set fireproofed wood alight. */
@Mixin(LavaFluid.class)
abstract class LavaFluidMixin {

    @Inject(method = "isFlammable(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
            at = @At("HEAD"), cancellable = true)
    private void wooddye$lavaIgnoresFireproof(LevelReader level, BlockPos pos, Direction face,
            CallbackInfoReturnable<Boolean> cir) {
        if (Fireproofing.isFireproof(level, pos)) {
            cir.setReturnValue(false);
        }
    }
}
