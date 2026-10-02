package com.sablednah.wooddye.fireproof;

import com.sablednah.wooddye.WoodDye;
import com.sablednah.wooddye.neoforge.WoodTransforms;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Unit;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Fireproofing on an <em>item</em>: a {@code wooddye:fireproof} component on an ordinary wood item.
 * A stamped stack does not merge with plain ones, shows "(Fireproof)" in its name, survives fire
 * and lava as a dropped item, and when placed marks the position it lands on.
 */
public final class FireproofComponents {

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, WoodDye.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Unit>> FIREPROOF =
            COMPONENTS.registerComponentType("fireproof",
                    builder -> builder.persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE)));

    private FireproofComponents() {}

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }

    public static boolean isStamped(ItemStack stack) {
        return stack.has(FIREPROOF.get());
    }

    /** Whether the stack is a wood block item: the only kind of item a fireproof stamp applies to. */
    public static boolean isWood(ItemStack stack) {
        return stack.getItem() instanceof BlockItem item && WoodTransforms.isWood(item.getBlock());
    }

    /** Mark the stack fireproof: the component, the name, and vanilla's fire resistance for the dropped item. */
    public static ItemStack stamp(ItemStack stack) {
        if (!stack.isEmpty() && !isStamped(stack)) {
            stack.set(FIREPROOF.get(), Unit.INSTANCE);
            stack.set(DataComponents.FIRE_RESISTANT, Unit.INSTANCE);
            stack.set(DataComponents.ITEM_NAME, Component.translatable("item.wooddye.fireproof_name",
                    Component.translatable(stack.getItem().getDescriptionId(stack))));
        }
        return stack;
    }

    /** Undo {@link #stamp}. */
    public static ItemStack strip(ItemStack stack) {
        if (isStamped(stack)) {
            stack.remove(FIREPROOF.get());
            stack.remove(DataComponents.FIRE_RESISTANT);
            stack.remove(DataComponents.ITEM_NAME);
        }
        return stack;
    }
}
