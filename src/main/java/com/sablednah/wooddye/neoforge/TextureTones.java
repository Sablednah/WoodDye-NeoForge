package com.sablednah.wooddye.neoforge;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sablednah.wooddye.core.Tone;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;

/**
 * Measures the tone of a modded wood straight from its texture.
 *
 * <p>Dyeing is decided on the server, which never loads a texture &mdash; but every mod's jar still
 * carries its assets, on a dedicated server as much as on a client, so the file can simply be read
 * out of the jar. The block is followed the way the client would follow it (blockstate &rarr; model
 * &rarr; texture), without needing the client to do it.
 *
 * <p>Vanilla is the one exception: the dedicated server jar has no assets at all. Vanilla tones are
 * therefore measured at build time by {@code tools/gen_resources.py}, with the same arithmetic, and
 * shipped in {@code wooddye/tones.json}.
 *
 * <p>Everything here is best-effort. A mod with an unusual model simply yields {@code null} and the
 * caller falls back to the block's map colour.
 */
final class TextureTones {

    /** Texture slots to try, best first, for a block whose every face is inner wood. */
    private static final List<String> WOOD_SLOTS = List.of("all", "texture", "side", "particle");
    /** ...and for a log, where {@code side} is the bark and {@code end} is the rings. */
    private static final List<String> BARK_SLOTS = List.of("side", "all", "texture", "particle");

    /** A model may name a parent, which may name another; nothing sane nests deeper than this. */
    private static final int MAX_PARENTS = 8;

    private TextureTones() {}

    /**
     * The tone of {@code block}'s texture, or {@code null} if it cannot be found or read.
     *
     * @param bark measure the bark (a log's side) rather than the inner wood
     */
    static Tone measure(ResourceLocation block, boolean bark) {
        try {
            ResourceLocation texture = texture(block, bark ? BARK_SLOTS : WOOD_SLOTS);
            if (texture == null) {
                // No model we could follow: fall back on the near-universal naming convention.
                texture = ResourceLocation.fromNamespaceAndPath(block.getNamespace(), "block/" + block.getPath());
            }
            return average(texture);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static Tone average(ResourceLocation texture) throws IOException {
        String path = "assets/" + texture.getNamespace() + "/textures/" + texture.getPath() + ".png";
        try (InputStream in = open(texture.getNamespace(), path)) {
            BufferedImage image = in == null ? null : ImageIO.read(in);
            if (image == null) {
                return null;
            }
            // An animated texture is its frames stacked in one tall image; averaging the whole
            // thing averages the animation, which is the tone a player actually sees.
            int[] argb = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            return Tone.average(argb);
        }
    }

    /** Follow blockstate &rarr; model &rarr; parents, and pick the first of {@code slots} that is set. */
    private static ResourceLocation texture(ResourceLocation block, List<String> slots) throws IOException {
        JsonObject blockstate = json(block.getNamespace(),
                "assets/" + block.getNamespace() + "/blockstates/" + block.getPath() + ".json");
        ResourceLocation model = blockstate == null ? null : firstModel(blockstate);

        Map<String, String> textures = new HashMap<>();
        for (int depth = 0; model != null && depth < MAX_PARENTS; depth++) {
            JsonObject json = json(model.getNamespace(),
                    "assets/" + model.getNamespace() + "/models/" + model.getPath() + ".json");
            if (json == null) {
                break; // typically a vanilla parent such as cube_all, which sets no textures anyway
            }
            if (json.has("textures") && json.get("textures").isJsonObject()) {
                for (Map.Entry<String, JsonElement> slot : json.getAsJsonObject("textures").entrySet()) {
                    if (slot.getValue().isJsonPrimitive()) {
                        textures.putIfAbsent(slot.getKey(), slot.getValue().getAsString()); // child wins
                    }
                }
            }
            model = json.has("parent") ? ResourceLocation.tryParse(json.get("parent").getAsString()) : null;
        }

        for (String slot : slots) {
            String value = textures.get(slot);
            // A slot may point at another slot ("#side"); a few hops is plenty.
            for (int hops = 0; value != null && value.startsWith("#") && hops < MAX_PARENTS; hops++) {
                value = textures.get(value.substring(1));
            }
            if (value != null && !value.startsWith("#")) {
                return ResourceLocation.tryParse(value);
            }
        }
        return null;
    }

    /** The model of a blockstate's first variant (or first multipart case): any one will do. */
    private static ResourceLocation firstModel(JsonObject blockstate) {
        JsonElement apply = null;
        if (blockstate.has("variants") && blockstate.get("variants").isJsonObject()) {
            for (Map.Entry<String, JsonElement> variant : blockstate.getAsJsonObject("variants").entrySet()) {
                apply = variant.getValue();
                break;
            }
        } else if (blockstate.has("multipart") && blockstate.get("multipart").isJsonArray()) {
            JsonArray parts = blockstate.getAsJsonArray("multipart");
            if (!parts.isEmpty() && parts.get(0).isJsonObject()) {
                apply = parts.get(0).getAsJsonObject().get("apply");
            }
        }
        if (apply != null && apply.isJsonArray() && !apply.getAsJsonArray().isEmpty()) {
            apply = apply.getAsJsonArray().get(0); // a weighted list of alternatives
        }
        if (apply == null || !apply.isJsonObject() || !apply.getAsJsonObject().has("model")) {
            return null;
        }
        return ResourceLocation.tryParse(apply.getAsJsonObject().get("model").getAsString());
    }

    private static JsonObject json(String namespace, String path) throws IOException {
        try (InputStream in = open(namespace, path)) {
            if (in == null) {
                return null;
            }
            JsonElement parsed = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        }
    }

    /**
     * Open a file from whichever mod jar holds it, or return {@code null}. The mod whose id matches
     * the namespace is tried first, since that is nearly always the owner; failing that every mod is
     * searched, because a namespace is not obliged to match a mod id.
     */
    private static InputStream open(String namespace, String path) throws IOException {
        IModFileInfo owner = ModList.get().getModFileById(namespace);
        if (owner != null) {
            InputStream in = open(owner, path);
            if (in != null) {
                return in;
            }
        }
        for (IModFileInfo file : ModList.get().getModFiles()) {
            if (file != owner) {
                InputStream in = open(file, path);
                if (in != null) {
                    return in;
                }
            }
        }
        return null;
    }

    private static InputStream open(IModFileInfo file, String path) throws IOException {
        Path found = file.getFile().findResource(path);
        return Files.isRegularFile(found) ? Files.newInputStream(found) : null;
    }
}
