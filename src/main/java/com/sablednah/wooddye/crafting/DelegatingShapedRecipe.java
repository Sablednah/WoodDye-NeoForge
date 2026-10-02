package com.sablednah.wooddye.crafting;

import com.google.gson.JsonObject;

import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.IShapedRecipe;

/**
 * Base for WoodDye's two shaped recipes that need one behaviour a plain shaped recipe cannot express
 * — a config gate, or handing an ingredient back. Everything else (matching, assembly, display, JSON
 * shape) is delegated to a real {@link ShapedRecipe}, and the type stays {@code minecraft:crafting},
 * so the recipe book and JEI treat these as ordinary crafting recipes.
 *
 * <p>Subclasses override just the method they change, plus {@link #getSerializer()}.
 *
 * <p>1.20.1 predates recipe codecs, so there are no separate fields to encode here: the wrapped
 * {@link ShapedRecipe} is read and written whole by vanilla's own shaped serializer. Being an
 * {@link IShapedRecipe} is how this version's recipe book (and JEI) learn the grid's width and
 * height, which they would otherwise take from the {@code ShapedRecipe} class itself.
 */
public abstract class DelegatingShapedRecipe implements CraftingRecipe, IShapedRecipe<CraftingContainer> {

    // Package-private, not private: the nested Serializer reads it through the type variable T,
    // which private access does not reach.
    final ShapedRecipe delegate;

    protected DelegatingShapedRecipe(ShapedRecipe delegate) {
        this.delegate = delegate;
    }

    @Override
    public ResourceLocation getId() {
        return delegate.getId();
    }

    @Override
    public boolean matches(CraftingContainer input, Level level) {
        return delegate.matches(input, level);
    }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess registries) {
        return delegate.assemble(input, registries);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return delegate.canCraftInDimensions(width, height);
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return delegate.getResultItem(registries);
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return delegate.getIngredients();
    }

    /** A shaped pattern has empty cells; the default would call every such recipe incomplete. */
    @Override
    public boolean isIncomplete() {
        return delegate.isIncomplete();
    }

    @Override
    public String getGroup() {
        return delegate.getGroup();
    }

    @Override
    public CraftingBookCategory category() {
        return delegate.category();
    }

    @Override
    public boolean showNotification() {
        return delegate.showNotification();
    }

    @Override
    public int getRecipeWidth() {
        return delegate.getRecipeWidth();
    }

    @Override
    public int getRecipeHeight() {
        return delegate.getRecipeHeight();
    }

    /**
     * Serializer for any subclass — the JSON is exactly a vanilla shaped recipe's, so only the
     * {@code type} field distinguishes them. Vanilla's shaped serializer does all three jobs (JSON,
     * and the network encoding in each direction); this only wraps what it produces.
     */
    public static final class Serializer<T extends DelegatingShapedRecipe> implements RecipeSerializer<T> {

        /** Builds a recipe around the shaped recipe it behaves like. */
        @FunctionalInterface
        public interface Factory<T extends DelegatingShapedRecipe> {
            T create(ShapedRecipe shaped);
        }

        private final Factory<T> factory;

        public Serializer(Factory<T> factory) {
            this.factory = factory;
        }

        @Override
        public T fromJson(ResourceLocation id, JsonObject json) {
            return factory.create(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
        }

        @Override
        public T fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return factory.create(RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buffer));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, T recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buffer, recipe.delegate);
        }
    }
}
