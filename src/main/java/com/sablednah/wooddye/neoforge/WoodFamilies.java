package com.sablednah.wooddye.neoforge;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.sablednah.wooddye.WoodDye;
import com.sablednah.wooddye.WoodDyeConfig;
import com.sablednah.wooddye.core.Tone;
import com.sablednah.wooddye.core.WoodType.Form;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Finds the wood families in the game from block tags, and works out each one's tone.
 *
 * <p>WoodDye owns a {@code wooddye:dyeable/*} block tag per {@link Form}, each shipped as nothing
 * but a reference to the matching vanilla tag ({@code #minecraft:planks},
 * {@code #minecraft:wooden_slabs}, ...). So a wood Mojang adds, or a mod that tags its blocks
 * properly, turns up here with no code and no list to maintain; and a pack that wants a block in or
 * out edits a tag. Blocks are grouped into a family by namespace and name stem: {@code oak_planks},
 * {@code oak_slab} and {@code stripped_oak_log} are all {@code minecraft:oak}.
 *
 * <p>A family's tone comes from the first of these that has an answer:
 * <ol>
 *     <li>the {@code toneOverrides} config, for when an owner disagrees with the measurement;</li>
 *     <li>{@code wooddye/tones.json}, measured from vanilla's textures at build time;</li>
 *     <li>the texture in the owning mod's jar, measured now (see {@link TextureTones});</li>
 *     <li>the block's map colour, which every block has.</li>
 * </ol>
 */
public final class WoodFamilies {

    /** Where a tone came from, shown by {@code /wooddye woods}. */
    public enum Source { OVERRIDE, BAKED, TEXTURE, MAP_COLOUR }

    /** A tone together with how it was arrived at. */
    public record Measured(Tone tone, Source source) {}

    /**
     * One wood: its blocks by form, and the tones of its inner wood and its bark.
     *
     * @param id {@code namespace:stem}, e.g. {@code minecraft:dark_oak}
     */
    public record Family(String id, Map<Form, Block> blocks, Measured wood, Measured bark) {

        /** This wood's block of the given form, or {@code null} if it has none. */
        public Block block(Form form) {
            return blocks.get(form);
        }

        /** The id as a player would say it: vanilla woods lose their {@code minecraft:} prefix. */
        public String label() {
            return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
        }
    }

    private static final String BAKED_TONES = "/wooddye/tones.json";

    /** Vanilla tones by block id, loaded once. */
    private static Map<String, Tone> baked;

    /**
     * Texture measurements by block id and face. A jar's contents cannot change while the game
     * runs, so these are kept for good; a miss is remembered too, as an empty value.
     */
    private static final Map<String, Optional<Tone>> MEASURED = new ConcurrentHashMap<>();

    private WoodFamilies() {}

    /**
     * Every family the tags describe, whether or not the config lets it be dyed, with blocks only.
     * Used to learn what each block <em>is</em> (a door, a sign, ...), which matters for
     * fireproofing even where dyeing is switched off.
     */
    static Map<String, Map<Form, Block>> discover() {
        Map<String, Map<Form, Block>> families = new TreeMap<>();
        for (Form form : Form.values()) {
            if (form.pillar() && form != Form.LOG) {
                continue; // the four pillar forms share one tag; walk it once
            }
            for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag(form))) {
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(holder.value());
                if (id.getNamespace().equals(WoodDye.MODID)) {
                    continue; // our fireproof blocks sit in the vanilla tags; they are paired up separately
                }
                Form actual = form.pillar() ? Form.ofLog(id.getPath()) : form;
                String stem = actual == null ? null
                        : form.pillar() ? Form.logStem(id.getPath()) : actual.stem(id.getPath());
                if (stem == null) {
                    continue; // tagged, but not named in a way that says which wood it is
                }
                families.computeIfAbsent(id.getNamespace() + ":" + stem, key -> new EnumMap<>(Form.class))
                        .putIfAbsent(actual, holder.value());
            }
        }
        return families;
    }

    /** The families the current config allows to be dyed, each with its tones. In id order. */
    static List<Family> dyeable(Map<String, Map<Form, Block>> discovered) {
        Map<String, Tone> overrides = overrides();
        List<Family> families = new ArrayList<>();
        for (Map.Entry<String, Map<Form, Block>> entry : discovered.entrySet()) {
            Map<Form, Block> blocks = entry.getValue();
            Block planks = blocks.get(Form.PLANKS);
            if (planks == null || !enabled(entry.getKey(), planks)) {
                continue; // a family is placed by its plank colour, so without planks it has no place
            }
            Measured wood = tone(planks, false, overrides);
            Block log = blocks.containsKey(Form.LOG) ? blocks.get(Form.LOG) : blocks.get(Form.WOOD);
            Measured bark = log == null ? wood : tone(log, true, overrides);
            families.add(new Family(entry.getKey(), blocks, wood, bark));
        }
        return families;
    }

    private static TagKey<Block> tag(Form form) {
        return TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath(WoodDye.MODID, "dyeable/" + form.tag()));
    }

    private static boolean enabled(String id, Block planks) {
        String namespace = id.substring(0, id.indexOf(':'));
        if (!namespace.equals(ResourceLocation.DEFAULT_NAMESPACE) && !WoodDyeConfig.MODDED_WOODS.get()) {
            return false;
        }
        if (!WoodDyeConfig.NETHER_WOODS.get() && planks.asItem().getDefaultInstance().is(ItemTags.NON_FLAMMABLE_WOOD)) {
            return false;
        }
        for (String excluded : WoodDyeConfig.EXCLUDED_WOODS.get()) {
            String entry = excluded.trim();
            if (entry.equals(namespace) || entry.equals(id)) {
                return false;
            }
        }
        return true;
    }

    private static Measured tone(Block block, boolean bark, Map<String, Tone> overrides) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        String key = id.toString();

        Tone tone = overrides.get(key);
        if (tone != null) {
            return new Measured(tone, Source.OVERRIDE);
        }
        tone = baked().get(key);
        if (tone != null) {
            return new Measured(tone, Source.BAKED);
        }
        tone = MEASURED.computeIfAbsent(key + (bark ? "#bark" : "#wood"),
                unused -> Optional.ofNullable(TextureTones.measure(id, bark))).orElse(null);
        if (tone != null) {
            return new Measured(tone, Source.TEXTURE);
        }
        return new Measured(mapColour(block, bark), Source.MAP_COLOUR);
    }

    /**
     * The block's map colour as a tone. Coarse &mdash; maps have only a few dozen colours, so two
     * woods often share one &mdash; but it is always there. A log reports its end-grain colour when
     * upright and its bark colour on its side, so bark is read from a sideways log.
     */
    private static Tone mapColour(Block block, boolean bark) {
        BlockState state = block.defaultBlockState();
        if (bark && state.hasProperty(RotatedPillarBlock.AXIS)) {
            state = state.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        }
        return Tone.ofRgb(state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).col);
    }

    /** The {@code toneOverrides} config, as block id &rarr; tone. Malformed entries are ignored. */
    private static Map<String, Tone> overrides() {
        Map<String, Tone> overrides = new HashMap<>();
        for (String entry : WoodDyeConfig.TONE_OVERRIDES.get()) {
            int split = entry.indexOf('=');
            Tone tone = split < 0 ? null : Tone.parseHex(entry.substring(split + 1).trim());
            if (tone != null) {
                overrides.put(entry.substring(0, split).trim(), tone);
            }
        }
        return overrides;
    }

    private static synchronized Map<String, Tone> baked() {
        if (baked == null) {
            Map<String, Tone> tones = new HashMap<>();
            try (InputStream in = WoodFamilies.class.getResourceAsStream(BAKED_TONES)) {
                if (in != null) {
                    JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                        Tone tone = Tone.parseHex(entry.getValue().getAsString());
                        if (tone != null) {
                            tones.put(entry.getKey(), tone);
                        }
                    }
                }
            } catch (IOException | RuntimeException e) {
                WoodDye.LOGGER.warn("WoodDye: could not read {}; vanilla woods fall back to map colours", BAKED_TONES, e);
            }
            baked = tones;
        }
        return baked;
    }
}
