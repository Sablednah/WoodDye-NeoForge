package com.sablednah.wooddye.compat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.sablednah.wooddye.WoodDye;
import com.sablednah.wooddye.fireproof.FireproofComponents;
import com.sablednah.wooddye.neoforge.WoodTransforms;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.common.crafting.StrictNBTIngredient;

/**
 * JEI knows about fireproof wood. A fireproof plank is an ordinary plank with a tag on it, which
 * JEI would otherwise fold into the plain item; so it is told the tag makes a different item, given
 * the fireproof stacks to list, and shown the two bench recipes — really one custom recipe each way
 * that JEI cannot see into — as ordinary crafting recipes, one per wood item. Only loaded by JEI,
 * through the annotation.
 */
@JeiPlugin
public final class JeiFireproof implements IModPlugin {

    private static final ResourceLocation UID = new ResourceLocation(WoodDye.MODID, "fireproof");

    /** The live JEI, while there is one; set once JEI has started. */
    private static volatile IJeiRuntime runtime;
    /** Whether the fireproof stacks and recipes are in this JEI session yet. */
    private static volatile boolean added;
    private static boolean listening;

    /**
     * Whether the client has its tags yet. On joining a server JEI can start before they arrive
     * (it did on 1.20.1), and the wood list comes from tags, so until then it holds only the woods
     * registered in code.
     */
    private static boolean tagsReady() {
        return Blocks.OAK_PLANKS.defaultBlockState().is(BlockTags.PLANKS);
    }

    /** The wood items, sorted by id so JEI lists them in a stable order. */
    private static List<Item> woodItems() {
        WoodTransforms.invalidate(); // never trust tables built before the tags came
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

    /**
     * Whether an item could be a wood that gets fireproofed. Subtypes have to be registered before
     * the tags may have arrived, so this goes by the block's sound instead: every wooden block,
     * vanilla or modded, sounds like wood. Registering a few too many is harmless; an unstamped
     * stack is the same subtype as before.
     */
    private static boolean soundsLikeWood(Item item) {
        if (!(item instanceof BlockItem blockItem)) {
            return false;
        }
        SoundType sound = blockItem.getBlock().defaultBlockState().getSoundType();
        return sound == SoundType.WOOD || sound == SoundType.CHERRY_WOOD || sound == SoundType.BAMBOO_WOOD
                || sound == SoundType.NETHER_WOOD || sound == SoundType.HANGING_SIGN
                || sound == SoundType.NETHER_WOOD_HANGING_SIGN || sound == SoundType.CHERRY_WOOD_HANGING_SIGN
                || sound == SoundType.BAMBOO_WOOD_HANGING_SIGN;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (soundsLikeWood(item)) {
                registration.registerSubtypeInterpreter(item, FireproofSubtype.INSTANCE);
            }
        }
    }

    @Override
    public void registerExtraIngredients(IExtraIngredientRegistration registration) {
        if (tagsReady()) {
            registration.addExtraItemStacks(stamped(woodItems()));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (tagsReady()) {
            registration.addRecipes(RecipeTypes.CRAFTING, recipes(woodItems()));
            added = true;
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        if (!listening) {
            listening = true;
            MinecraftForge.EVENT_BUS.addListener(JeiFireproof::onTagsUpdated);
        }
        if (!added && tagsReady()) {
            addAtRuntime();
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
        added = false;
    }

    /** The tags have come after JEI started: add what registration could not see. */
    private static void onTagsUpdated(TagsUpdatedEvent event) {
        if (runtime != null && !added && tagsReady()) {
            addAtRuntime();
        }
    }

    private static void addAtRuntime() {
        List<Item> items = woodItems();
        runtime.getIngredientManager().addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, stamped(items));
        runtime.getRecipeManager().addRecipes(RecipeTypes.CRAFTING, recipes(items));
        added = true;
    }

    private static List<ItemStack> stamped(List<Item> items) {
        return items.stream().map(item -> FireproofComponents.stamp(new ItemStack(item))).toList();
    }

    private static List<CraftingRecipe> recipes(List<Item> items) {
        List<CraftingRecipe> recipes = new ArrayList<>();
        for (Item item : items) {
            ItemStack plain = new ItemStack(item);
            ItemStack fireproof = FireproofComponents.stamp(new ItemStack(item));
            String path = BuiltInRegistries.ITEM.getKey(item).toString().replace(':', '/');
            recipes.add(eightAroundOne("jei/fireproof/" + path, Ingredient.of(item),
                    Ingredient.of(Items.MAGMA_CREAM), fireproof.copyWithCount(8)));
            recipes.add(eightAroundOne("jei/restore/" + path, StrictNBTIngredient.of(fireproof),
                    Ingredient.of(Items.WET_SPONGE), plain.copyWithCount(8)));
        }
        return recipes;
    }

    private static CraftingRecipe eightAroundOne(String id, Ingredient wood, Ingredient centre, ItemStack result) {
        NonNullList<Ingredient> grid = NonNullList.withSize(9, wood);
        grid.set(4, centre);
        return new ShapedRecipe(new ResourceLocation(WoodDye.MODID, id), "wooddye_fireproofing",
                CraftingBookCategory.MISC, 3, 3, grid, result);
    }

    /** Fireproof or not is the whole of the subtype. */
    private enum FireproofSubtype implements ISubtypeInterpreter<ItemStack> {
        INSTANCE;

        @Override
        public Object getSubtypeData(ItemStack stack, UidContext context) {
            return FireproofComponents.isStamped(stack) ? Boolean.TRUE : null;
        }

        @Override
        public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
            return FireproofComponents.isStamped(stack) ? "fireproof" : "";
        }
    }
}
