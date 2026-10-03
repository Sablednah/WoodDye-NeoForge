package com.sablednah.wooddye.compat;

import java.util.ArrayList;
import java.util.List;

import com.sablednah.wooddye.WoodDye;
import com.sablednah.wooddye.core.WoodType.Form;
import com.sablednah.wooddye.fireproof.FireproofComponents;
import com.sablednah.wooddye.neoforge.WoodFamilies.Family;
import com.sablednah.wooddye.neoforge.WoodTransforms;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * The "In world" JEI category: what right-clicking a placed block with a dye, Magma Cream or a Wet
 * Sponge turns it into. These happen in the world, not at a bench, so without this JEI has no way
 * to show them. Built from the same tables the right-click uses, as this client sees them.
 */
final class JeiInWorld extends AbstractRecipeCategory<JeiInWorld.Treatment> {

    /** One card: the block clicked, what is held, the block it becomes, and how to click. */
    record Treatment(ItemStack block, List<ItemStack> held, ItemStack result, Component how) {}

    static final IRecipeType<Treatment> TYPE = IRecipeType.create(WoodDye.MODID, "in_world", Treatment.class);

    static final List<Item> DARKEN = List.of(Items.BLACK_DYE, Items.BROWN_DYE);
    static final List<Item> LIGHTEN = List.of(Items.WHITE_DYE, Items.LIGHT_GRAY_DYE, Items.BONE_MEAL);

    private static final int WIDTH = 116;
    private static final int HEIGHT = 34;

    private final IDrawableStatic arrow;

    JeiInWorld(IGuiHelper gui) {
        super(TYPE, Component.translatable("wooddye.jei.in_world"),
                gui.createDrawableItemStack(new ItemStack(Items.BROWN_DYE)), WIDTH, HEIGHT);
        this.arrow = gui.getRecipeArrow();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Treatment recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).setStandardSlotBackground().addItemStack(recipe.block());
        builder.addSlot(RecipeIngredientRole.INPUT, 25, 1).setStandardSlotBackground().addItemStacks(recipe.held());
        builder.addSlot(RecipeIngredientRole.OUTPUT, 95, 1).setStandardSlotBackground().addItemStack(recipe.result());
    }

    @Override
    public void draw(Treatment recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        arrow.draw(graphics, 57, 1);
        graphics.drawString(Minecraft.getInstance().font, recipe.how(), 1, 24, 0xFF404040, false);
    }

    /** Every in-world treatment this client knows of, fireproofing in the order of {@code woodItems}. */
    static List<Treatment> all(List<Item> woodItems) {
        List<Treatment> all = new ArrayList<>();
        Component click = Component.translatable("wooddye.jei.right_click");
        Component sneak = Component.translatable("wooddye.jei.sneak_right_click");
        Component bark = Component.translatable("wooddye.jei.right_click_side");

        for (Form form : Form.values()) {
            // Logs and all-bark wood dye along the bark order when clicked on a side (the default).
            boolean barkForm = form.order() != com.sablednah.wooddye.core.WoodType.Order.WOOD;
            List<Family> order = barkForm ? WoodTransforms.barkOrder() : WoodTransforms.woodOrder();
            List<Block> chain = new ArrayList<>();
            for (Family family : order) {
                Block block = family.block(form);
                if (block != null && block.asItem() != Items.AIR) {
                    chain.add(block);
                }
            }
            for (int i = 0; i + 1 < chain.size(); i++) {
                Block lighter = chain.get(i);
                Block darker = chain.get(i + 1);
                Component how = WoodTransforms.requiresSneak(lighter) ? sneak : barkForm ? bark : click;
                all.add(new Treatment(new ItemStack(lighter), stacks(DARKEN), new ItemStack(darker), how));
                all.add(new Treatment(new ItemStack(darker), stacks(LIGHTEN), new ItemStack(lighter), how));
            }
        }

        for (Item item : woodItems) {
            Block block = Block.byItem(item);
            Component how = WoodTransforms.requiresSneak(block) ? sneak : click;
            ItemStack plain = new ItemStack(item);
            ItemStack fireproof = FireproofComponents.stamp(new ItemStack(item));
            all.add(new Treatment(plain, List.of(new ItemStack(Items.MAGMA_CREAM)), fireproof, how));
            all.add(new Treatment(fireproof, List.of(new ItemStack(Items.WET_SPONGE)), plain, how));
        }
        return all;
    }

    private static List<ItemStack> stacks(List<Item> items) {
        return items.stream().map(ItemStack::new).toList();
    }
}
