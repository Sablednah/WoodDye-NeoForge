package com.sablednah.wooddye.registry;

import com.sablednah.wooddye.WoodDye;
import com.sablednah.wooddye.fireproof.FireproofComponents;
import com.sablednah.wooddye.neoforge.WoodFamilies.Family;
import com.sablednah.wooddye.neoforge.WoodTransforms;
import com.sablednah.wooddye.core.WoodType;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * A single "WoodDye" creative tab: a fireproof version of every dyeable wood block, as the stamped
 * vanilla (or modded) item. The legacy {@code fireproof_*} items are deliberately not listed; they
 * exist only so 2.x worlds load, and are converted away on sight.
 */
public final class WoodDyeCreativeTab {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WoodDye.MODID);

    public static final RegistryObject<CreativeModeTab> WOODDYE = TABS.register(
            "wooddye",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.wooddye"))
                    .icon(() -> FireproofComponents.stamp(new ItemStack(Items.OAK_PLANKS)))
                    .displayItems((parameters, output) -> {
                        for (WoodType.Form form : WoodType.Form.values()) {
                            for (Family family : WoodTransforms.woodOrder()) {
                                Block block = family.block(form);
                                if (block != null && block.asItem() != Items.AIR) {
                                    output.accept(FireproofComponents.stamp(new ItemStack(block)));
                                }
                            }
                        }
                    })
                    .build());

    private WoodDyeCreativeTab() {}

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
