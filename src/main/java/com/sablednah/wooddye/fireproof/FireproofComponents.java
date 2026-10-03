package com.sablednah.wooddye.fireproof;

import com.sablednah.wooddye.neoforge.WoodTransforms;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * Fireproofing on an <em>item</em>: a {@code wooddye_fireproof} tag on an ordinary wood item.
 * A stamped stack does not merge with plain ones, shows "(Fireproof)" in its name, and when placed
 * marks the position it lands on.
 *
 * <p>1.20.1 has no item components, so this is NBT, and no per-stack fire resistance exists
 * either: a dropped fireproof item burns in lava like any other. (The class keeps its name so the
 * rest of the code reads the same on every line.)
 */
public final class FireproofComponents {

    private static final String KEY = "wooddye_fireproof";

    private FireproofComponents() {}

    public static boolean isStamped(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(KEY);
    }

    /** Whether the stack is a wood block item: the only kind of item a fireproof stamp applies to. */
    public static boolean isWood(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && WoodTransforms.isWood(item.getBlock());
    }

    /** Mark the stack fireproof: the tag, and the name (un-italicised, so it reads as a name, not a rename). */
    public static ItemStack stamp(ItemStack stack) {
        if (!stack.isEmpty() && !isStamped(stack)) {
            stack.getOrCreateTag().putBoolean(KEY, true);
            stack.setHoverName(Component.translatable("item.wooddye.fireproof_name",
                    Component.translatable(stack.getItem().getDescriptionId(stack)))
                    .withStyle(style -> style.withItalic(false)));
        }
        return stack;
    }

    /** Undo {@link #stamp}. */
    public static ItemStack strip(ItemStack stack) {
        if (isStamped(stack)) {
            stack.removeTagKey(KEY);
            stack.resetHoverName();
            if (stack.getTag() != null && stack.getTag().isEmpty()) {
                stack.setTag(null);
            }
        }
        return stack;
    }
}
