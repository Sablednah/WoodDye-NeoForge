package com.sablednah.wooddye.registry;

import java.util.ArrayList;
import java.util.List;

import com.sablednah.wooddye.WoodDye;

import com.sablednah.wooddye.core.WoodType;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * {@link BlockItem}s for every fireproof block. The items are also {@code fireResistant()} so a
 * dropped stack survives lava/fire, matching the block's fireproof theme.
 */
public final class WoodDyeItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, WoodDye.MODID);

    /** Every registered block item, in the same order as {@link WoodDyeBlocks#ALL}. */
    public static final List<RegistryObject<? extends BlockItem>> ALL = new ArrayList<>();

    static {
        for (WoodDyeBlocks.Entry entry : WoodDyeBlocks.ALL) {
            String name = entry.block().getId().getPath();
            if (entry.form() == WoodType.Form.DOOR) {
                // Doors are two blocks tall; vanilla places them with a DoubleHighBlockItem, which
                // clears the space above before the door block fills it in.
                ALL.add(ITEMS.register(name,
                        () -> new DoubleHighBlockItem(entry.block().get(),
                                new Item.Properties().fireResistant())));
            } else {
                ALL.add(ITEMS.register(name,
                        () -> new BlockItem(entry.block().get(),
                                new Item.Properties().fireResistant())));
            }
        }
    }

    private WoodDyeItems() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
