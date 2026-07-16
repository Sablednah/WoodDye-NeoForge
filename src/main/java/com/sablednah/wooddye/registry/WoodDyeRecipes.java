package com.sablednah.wooddye.registry;

import com.sablednah.wooddye.WoodDye;
import com.sablednah.wooddye.crafting.DelegatingShapelessRecipe;
import com.sablednah.wooddye.crafting.FireproofingRecipe;
import com.sablednah.wooddye.crafting.SpongeRestoreRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * WoodDye's recipe serializers — the two shapeless variants that need behaviour JSON cannot express.
 * Every other recipe the mod ships is a plain vanilla type, generated as JSON by
 * {@code tools/gen_resources.py}.
 */
public final class WoodDyeRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, WoodDye.MODID);

    /** {@code wooddye:sponge_restore} — shapeless, but the sponge survives the craft. */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<SpongeRestoreRecipe>>
            SPONGE_RESTORE = SERIALIZERS.register("sponge_restore",
                    () -> new DelegatingShapelessRecipe.Serializer<>(SpongeRestoreRecipe::new));

    /** {@code wooddye:fireproofing} — shapeless, but only while the {@code fireProof} config is on. */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FireproofingRecipe>>
            FIREPROOFING = SERIALIZERS.register("fireproofing",
                    () -> new DelegatingShapelessRecipe.Serializer<>(FireproofingRecipe::new));

    private WoodDyeRecipes() {}

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
