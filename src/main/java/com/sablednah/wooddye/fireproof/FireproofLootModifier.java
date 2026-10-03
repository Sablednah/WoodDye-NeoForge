package com.sablednah.wooddye.fireproof;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sablednah.wooddye.WoodDye;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Stamps the drops of a block that was fireproof. Forge 1.20.1 has no block-drops event, so this
 * is a global loot modifier (declared by {@code data/wooddye/loot_modifiers/fireproof_drops.json},
 * which the generator writes on this line only). The block is already gone when loot is rolled,
 * which is what {@link Fireproofing#wasFireproof} is for.
 */
public final class FireproofLootModifier extends LootModifier {

    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, WoodDye.MODID);

    public static final RegistryObject<Codec<FireproofLootModifier>> FIREPROOF_DROPS = SERIALIZERS.register(
            "fireproof_drops", () -> RecordCodecBuilder.create(instance ->
                    codecStart(instance).apply(instance, FireproofLootModifier::new)));

    public FireproofLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> drops, LootContext context) {
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin == null || !context.hasParam(LootContextParams.BLOCK_STATE)) {
            return drops; // not a block's loot
        }
        BlockPos pos = BlockPos.containing(origin);
        if (!Fireproofing.wasFireproof(context.getLevel(), pos)) {
            return drops;
        }
        for (int i = 0; i < drops.size(); i++) {
            if (FireproofComponents.isWood(drops.get(i))) {
                drops.set(i, FireproofComponents.stamp(drops.get(i)));
            }
        }
        Fireproofing.unmark(context.getLevel(), pos); // the block is gone, whatever flags removed it with
        return drops;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return FIREPROOF_DROPS.get();
    }
}
