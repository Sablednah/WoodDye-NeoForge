package com.sablednah.wooddye.registry;

import com.sablednah.wooddye.WoodDye;
import com.sablednah.wooddye.crafting.DelegatingShapedRecipe;
import com.sablednah.wooddye.crafting.FireproofStampRecipe;
import com.sablednah.wooddye.crafting.FireproofingRecipe;
import com.sablednah.wooddye.crafting.SpongeRestoreRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * WoodDye's recipe serializers — the two shaped variants that need behaviour JSON cannot express.
 * Every other recipe the mod ships is a plain vanilla type, generated as JSON by
 * {@code tools/gen_resources.py}.
 */
public final class WoodDyeRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, WoodDye.MODID);

    /** {@code wooddye:sponge_restore} — shaped, but the sponge survives the craft. */
    public static final RegistryObject<RecipeSerializer<SpongeRestoreRecipe>>
            SPONGE_RESTORE = SERIALIZERS.register("sponge_restore",
                    () -> new DelegatingShapedRecipe.Serializer<>(SpongeRestoreRecipe::new));

    /** {@code wooddye:fireproofing} — shaped, but only while the {@code fireProof} config is on. */
    public static final RegistryObject<RecipeSerializer<FireproofingRecipe>>
            FIREPROOFING = SERIALIZERS.register("fireproofing",
                    () -> new DelegatingShapedRecipe.Serializer<>(FireproofingRecipe::new));

    /** {@code wooddye:fireproof_stamp} — 8 of any wood item + magma cream -> 8 fireproof ones. */
    public static final RegistryObject<RecipeSerializer<FireproofStampRecipe>>
            FIREPROOF_STAMP = SERIALIZERS.register("fireproof_stamp",
                    () -> new SimpleCraftingRecipeSerializer<>((id, category) -> new FireproofStampRecipe(id, category, true)));

    /** {@code wooddye:fireproof_unstamp} — 8 fireproof wood items + wet sponge -> 8 plain, sponge kept. */
    public static final RegistryObject<RecipeSerializer<FireproofStampRecipe>>
            FIREPROOF_UNSTAMP = SERIALIZERS.register("fireproof_unstamp",
                    () -> new SimpleCraftingRecipeSerializer<>((id, category) -> new FireproofStampRecipe(id, category, false)));

    private WoodDyeRecipes() {}

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
