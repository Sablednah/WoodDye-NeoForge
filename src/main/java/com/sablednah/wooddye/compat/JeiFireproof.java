package com.sablednah.wooddye.compat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.sablednah.wooddye.WoodDye;
import com.sablednah.wooddye.fireproof.FireproofComponents;
import com.sablednah.wooddye.neoforge.WoodTransforms;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;

/**
 * JEI knows about fireproof wood. A fireproof plank is an ordinary plank with a component on it,
 * which JEI would otherwise fold into the plain item; so it is told the component makes a
 * different item, given the fireproof stacks to list, and shown the two bench recipes — which are
 * really one custom recipe each way that JEI cannot see into — as ordinary crafting recipes, one
 * per wood item. Only loaded by JEI, through the annotation.
 */
@JeiPlugin
public final class JeiFireproof implements IModPlugin {

    private static final Identifier UID = Identifier.fromNamespaceAndPath(WoodDye.MODID, "fireproof");

    /** The wood items, sorted by id so JEI lists them in a stable order. */
    private static List<Item> woodItems() {
        List<Item> items = new ArrayList<>();
        WoodTransforms.markableWood().forEach(block -> {
            Item item = block.asItem();
            if (item != Items.AIR && !items.contains(item)) {
                items.add(item);
            }
        });
        items.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()));
        return items;
    }

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        for (Item item : woodItems()) {
            registration.registerFromDataComponentTypes(item, FireproofComponents.FIREPROOF.get());
        }
    }

    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        registration.addExtraItemStacks(woodItems().stream()
                .map(item -> FireproofComponents.stamp(new ItemStack(item))).toList());
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<RecipeHolder<CraftingRecipe>> recipes = new ArrayList<>();
        for (Item item : woodItems()) {
            ItemStack plain = new ItemStack(item);
            ItemStack fireproof = FireproofComponents.stamp(new ItemStack(item));
            String path = BuiltInRegistries.ITEM.getKey(item).toString().replace(':', '/');
            recipes.add(eightAroundOne("jei/fireproof/" + path, Ingredient.of(item),
                    Ingredient.of(Items.MAGMA_CREAM), fireproof.copyWithCount(8)));
            recipes.add(eightAroundOne("jei/restore/" + path, DataComponentIngredient.of(false, fireproof),
                    Ingredient.of(Items.WET_SPONGE), plain.copyWithCount(8)));
        }
        registration.addRecipes(RecipeTypes.CRAFTING, recipes);
    }

    private static RecipeHolder<CraftingRecipe> eightAroundOne(String id, Ingredient wood, Ingredient centre, ItemStack result) {
        ShapedRecipePattern pattern = ShapedRecipePattern.of(Map.of('#', wood, 'X', centre), "###", "#X#", "###");
        CraftingRecipe recipe = new ShapedRecipe(new Recipe.CommonInfo(false),
                new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, "wooddye_fireproofing"),
                pattern, ItemStackTemplate.fromNonEmptyStack(result));
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE,
                Identifier.fromNamespaceAndPath(WoodDye.MODID, id)), recipe);
    }
}
