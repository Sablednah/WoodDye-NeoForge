package com.sablednah.wooddye.registry;

import com.sablednah.wooddye.WoodDye;
import com.sablednah.wooddye.core.WoodType;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * A single "WoodDye" creative tab listing every fireproof block.
 */
public final class WoodDyeCreativeTab {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WoodDye.MODID);

    public static final RegistryObject<CreativeModeTab> WOODDYE = TABS.register(
            "wooddye",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.wooddye"))
                    .icon(() -> new ItemStack(WoodDyeBlocks.get(WoodType.Form.PLANKS, WoodType.OAK).get()))
                    .displayItems((parameters, output) ->
                            WoodDyeItems.ALL.forEach(item -> output.accept(item.get())))
                    .build());

    private WoodDyeCreativeTab() {}

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
