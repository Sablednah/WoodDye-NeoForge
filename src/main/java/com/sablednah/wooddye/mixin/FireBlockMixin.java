package com.sablednah.wooddye.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.sablednah.wooddye.fireproof.Fireproofing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fire leaves fireproofed positions alone. Three questions fire asks about a neighbour, each
 * answered "no" when the position is marked: can it catch fire (which also decides where fire may
 * sit), does it burn away, and how fast does it help fire spread.
 *
 * <p>Forge 1.20.1: {@code canCatchFire} and {@code tryCatchFire} (Forge's replacement for
 * vanilla's {@code checkBurnOut}, with the face added) are Forge's own methods and keep their
 * names in production, hence {@code remap = false}; {@code getIgniteOdds} is vanilla and goes
 * through the refmap, while the call it makes is to a Forge-added method and does not.
 */
@Mixin(FireBlock.class)
abstract class FireBlockMixin {

    @Inject(method = "canCatchFire", at = @At("HEAD"), cancellable = true, remap = false)
    private void wooddye$fireproofCannotCatchFire(BlockGetter level, BlockPos pos, Direction face,
            CallbackInfoReturnable<Boolean> cir) {
        if (Fireproofing.isFireproof(level, pos)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "tryCatchFire(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;ILnet/minecraft/util/RandomSource;ILnet/minecraft/core/Direction;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void wooddye$fireproofDoesNotBurnAway(Level level, BlockPos pos, int chance, RandomSource random,
            int age, Direction face, CallbackInfo ci) {
        if (Fireproofing.isFireproof(level, pos)) {
            ci.cancel();
        }
    }

    @Redirect(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I",
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getFireSpreadSpeed(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I"))
    private int wooddye$fireproofDoesNotSpreadFire(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        return Fireproofing.isFireproof(level, pos) ? 0 : state.getFireSpreadSpeed(level, pos, face);
    }
}
