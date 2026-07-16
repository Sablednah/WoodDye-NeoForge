package com.sablednah.wooddye.crafting;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;

/**
 * Base for WoodDye's two shaped recipes that need one behaviour a plain shaped recipe cannot express
 * — a config gate, or handing an ingredient back. Everything else (matching, assembly, display, JSON
 * shape) is delegated to a real {@link ShapedRecipe}, and the type stays {@code minecraft:crafting},
 * so the recipe book and JEI treat these as ordinary crafting recipes.
 *
 * <p>Subclasses override just the method they change, plus {@link #getSerializer()}.
 */
public abstract class DelegatingShapedRecipe implements CraftingRecipe {

    // Package-private, not private: the nested Serializer reads them through the type variable T,
    // which private access does not reach. Vanilla's ShapedRecipe scopes its fields the same way.
    final String group;
    final CraftingBookCategory category;
    final ShapedRecipePattern pattern;
    final ItemStack result;

    private final ShapedRecipe delegate;

    protected DelegatingShapedRecipe(String group, CraftingBookCategory category,
            ShapedRecipePattern pattern, ItemStack result) {
        this.group = group;
        this.category = category;
        this.pattern = pattern;
        this.result = result;
        this.delegate = new ShapedRecipe(group, category, pattern, result);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return delegate.matches(input, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return delegate.assemble(input, registries);
    }

    @Override
    public PlacementInfo placementInfo() {
        return delegate.placementInfo();
    }

    @Override
    public List<RecipeDisplay> display() {
        return delegate.display();
    }

    @Override
    public String group() {
        return group;
    }

    @Override
    public CraftingBookCategory category() {
        return category;
    }

    /**
     * Serializer for any subclass — the JSON is exactly a vanilla shaped recipe's, so only the
     * {@code type} field distinguishes them.
     */
    public static final class Serializer<T extends DelegatingShapedRecipe> implements RecipeSerializer<T> {

        /** Builds a recipe from the four fields every shaped recipe carries. */
        @FunctionalInterface
        public interface Factory<T extends DelegatingShapedRecipe> {
            T create(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result);
        }

        private final MapCodec<T> codec;
        private final StreamCodec<RegistryFriendlyByteBuf, T> streamCodec;

        public Serializer(Factory<T> factory) {
            this.codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("group", "").forGetter(recipe -> recipe.group),
                    CraftingBookCategory.CODEC.fieldOf("category")
                            .orElse(CraftingBookCategory.MISC).forGetter(recipe -> recipe.category),
                    // Reads the "pattern" and "key" fields.
                    ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
                    ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result))
                    .apply(instance, factory::create));

            this.streamCodec = StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, recipe -> recipe.group,
                    CraftingBookCategory.STREAM_CODEC, recipe -> recipe.category,
                    ShapedRecipePattern.STREAM_CODEC, recipe -> recipe.pattern,
                    ItemStack.STREAM_CODEC, recipe -> recipe.result,
                    factory::create);
        }

        @Override
        public MapCodec<T> codec() {
            return codec;
        }

        /** Deprecated upstream, but still abstract on the interface — vanilla's serializers implement it too. */
        @Deprecated
        @Override
        public StreamCodec<RegistryFriendlyByteBuf, T> streamCodec() {
            return streamCodec;
        }
    }
}
