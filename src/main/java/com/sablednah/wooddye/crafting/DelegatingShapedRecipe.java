package com.sablednah.wooddye.crafting;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
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
 *
 * <p>Delegating rather than subclassing {@link ShapedRecipe}: its {@code getSerializer()} is typed to
 * itself, so a subclass could not return its own serializer.
 */
public abstract class DelegatingShapedRecipe implements CraftingRecipe {

    // Package-private, not private: the serializer reads them through the type variable T, which
    // private access does not reach.
    final Recipe.CommonInfo commonInfo;
    final CraftingRecipe.CraftingBookInfo bookInfo;
    final ShapedRecipePattern pattern;
    final ItemStackTemplate result;

    private final ShapedRecipe delegate;

    protected DelegatingShapedRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
            ShapedRecipePattern pattern, ItemStackTemplate result) {
        this.commonInfo = commonInfo;
        this.bookInfo = bookInfo;
        this.pattern = pattern;
        this.result = result;
        this.delegate = new ShapedRecipe(commonInfo, bookInfo, pattern, result);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return delegate.matches(input, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return delegate.assemble(input);
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
    public boolean showNotification() {
        return commonInfo.showNotification();
    }

    @Override
    public String group() {
        return bookInfo.group();
    }

    @Override
    public CraftingBookCategory category() {
        return bookInfo.category();
    }

    /** Builds a recipe from the four parts every shaped recipe carries. */
    @FunctionalInterface
    public interface Factory<T extends DelegatingShapedRecipe> {
        T create(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo,
                ShapedRecipePattern pattern, ItemStackTemplate result);
    }

    /**
     * Serializer for any subclass — the JSON is exactly a vanilla shaped recipe's, so only the
     * {@code type} field distinguishes them. Mirrors {@link ShapedRecipe#MAP_CODEC} field for field.
     */
    public static <T extends DelegatingShapedRecipe> RecipeSerializer<T> serializer(Factory<T> factory) {
        MapCodec<T> codec = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Recipe.CommonInfo.MAP_CODEC.forGetter(recipe -> recipe.commonInfo),
                CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(recipe -> recipe.bookInfo),
                // Reads the "pattern" and "key" fields.
                ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
                ItemStackTemplate.CODEC.fieldOf("result").forGetter(recipe -> recipe.result))
                .apply(instance, factory::create));

        StreamCodec<RegistryFriendlyByteBuf, T> streamCodec = StreamCodec.composite(
                Recipe.CommonInfo.STREAM_CODEC, recipe -> recipe.commonInfo,
                CraftingRecipe.CraftingBookInfo.STREAM_CODEC, recipe -> recipe.bookInfo,
                ShapedRecipePattern.STREAM_CODEC, recipe -> recipe.pattern,
                ItemStackTemplate.STREAM_CODEC, recipe -> recipe.result,
                factory::create);

        return new RecipeSerializer<>(codec, streamCodec);
    }
}
