#!/usr/bin/env python3
"""Generate WoodDye's assets/data JSON (blockstates, item models, loot, tags, recipes, lang).

NeoForge 21.11 removed the client model-generator datagen classes (BlockStateProvider /
ItemModelProvider), so we author the JSON directly instead of using a GatherDataEvent provider.

Every fireproof_X block is a visual clone of vanilla's X, so its blockstate and item model are copied
from the vanilla client jar verbatim and left pointing at the vanilla models/textures — WoodDye ships
no models or textures of its own. Its loot table is vanilla's with the block/item ids remapped, which
inherits the fiddly bits for free (a door dropping only from its lower half, a double slab dropping
two). Cloning also means a resource pack that retextures oak planks retextures fireproof oak planks.

The block list here mirrors the fireproof() forms of core/WoodType.java — keep the two in step.

It also measures the tone of every vanilla plank and log texture into wooddye/tones.json, which is
what the dye order is sorted on (see core/Tone.java — the arithmetic here must match it). That needs
Pillow to read the PNGs:  pip install pillow

The script is the same file on every version branch. It reads the Minecraft version from
gradle.properties, works from that version's client jar, and skips whatever that version lacks (a
wood, a form, a tag), so retargeting a branch is: change gradle.properties, run this.

Formats are decided from the jar (folder names, ingredient and result shapes, item model location,
the stripping mechanism). The one thing a vanilla jar cannot say is which mod loader the branch
builds for; that is read from gradle.properties too, as forge_version being set (MinecraftForge,
the 1.20.1 line) rather than neo_version (NeoForge, every other line).

Run from the repo root:  python3 tools/gen_resources.py
"""
import json, math, os, shutil, zipfile

from PIL import Image

MODID = "wooddye"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")


_MISSING = object()


def gradle_property(name, default=_MISSING):
    with open(os.path.join(ROOT, "gradle.properties")) as f:
        for line in f:
            key, _, value = line.partition("=")
            if key.strip() == name:
                return value.strip()
    if default is _MISSING:
        raise SystemExit(f"gradle.properties has no {name}")
    return default


MC_VERSION = gradle_property("minecraft_version")
# MinecraftForge rather than NeoForge (see the note at the top). Three things follow from it, each
# marked FORGE below: the difference ingredient's id, no data maps, and a pack.mcmeta of our own.
FORGE = gradle_property("forge_version", None) is not None
MJAR = os.path.expanduser(f"~/.gradle/caches/neoformruntime/artifacts/minecraft_{MC_VERSION}_client.jar")
if not os.path.exists(MJAR):
    raise SystemExit(f"No client jar for {MC_VERSION} at {MJAR} — run a Gradle build on this branch first.")

# Every wood any supported version has, in rough light -> dark order. The order is cosmetic (it sets
# the creative tab order via the matching enum); the dye chain itself is sorted from measured tones.
# Woods this version lacks are dropped below, once the jar is open.
ALL_WOODS = ["pale_oak", "cherry", "birch", "bamboo", "poplar", "oak", "jungle", "acacia", "spruce",
             "mangrove", "dark_oak"]

# The per-wood vanilla log tag, which the planks recipe takes as its input.
def logs_tag(wood):
    return "bamboo_blocks" if wood == "bamboo" else f"{wood}_logs"


# The fireproof() forms of WoodType.Form: name template -> the vanilla tag each belongs to.
# mineable/axe applies to every form and is added separately (it is a block-only tag).
FORMS = {
    "%s_planks":          "planks",
    "%s_slab":            "wooden_slabs",
    "%s_stairs":          "wooden_stairs",
    "%s_log":             "logs",
    "stripped_%s_log":    "logs",
    "%s_wood":            "logs",
    "stripped_%s_wood":   "logs",
    "%s_fence":           "wooden_fences",
    "%s_fence_gate":      "fence_gates",
    "%s_door":            "wooden_doors",
    "%s_trapdoor":        "wooden_trapdoors",
    "%s_pressure_plate":  "wooden_pressure_plates",
    "%s_button":          "wooden_buttons",
}

# crafting recipe mapping: dye colour -> target wood. Tag inputs, so any wood can be converted.
# The classic WoodDye 6 plus the four modern woods, each on a distinct (thematic) dye colour.
DYE_MAP = {
    "white": "birch",
    "light_gray": "oak",
    "yellow": "jungle",
    "orange": "acacia",
    "brown": "spruce",
    "black": "dark_oak",
    "pink": "cherry",      # cherry blossom
    "red": "mangrove",     # reddish wood
    "lime": "bamboo",      # green stalk
    "gray": "pale_oak",    # muted / pale
    "green": "poplar",     # the one tall green tree left without a colour
}

# The wooddye:dyeable/* block tags, one per WoodType.Form#tag(), and the vanilla tags each refers to.
# These are how the mod finds wood: it never lists blocks, so anything tagged properly — a wood from
# a later Minecraft, or from another mod — is picked up. A pack adds to or removes from these.
# Bamboo's pillars are not in #minecraft:logs, hence the second entry.
# Blocks that belong in a dyeable tag but whose mod never tagged them, listed as optional entries
# ("required": false), which the tag loader skips without complaint when the mod is absent. Only
# for mods that put the wood's OTHER blocks in the vanilla tags; a mod that tags nothing is better
# served by a datapack of its own. Found by looking at /wooddye showcase with the mod installed.
DYEABLE_EXTRAS = {
    "logs":      ["cataclysm:chorus_stem"],       # Cataclysm tags its planks, slab, stairs and fence, not the stem
    "trapdoors": ["cataclysm:chorus_trapdoor"],   # ...nor the trapdoor
}

DYEABLE_TAGS = {
    "planks":             ["planks"],
    "slabs":              ["wooden_slabs"],
    "stairs":             ["wooden_stairs"],
    "logs":               ["logs", "bamboo_blocks"],
    "fences":             ["wooden_fences"],
    "fence_gates":        ["fence_gates"],
    "doors":              ["wooden_doors"],
    "trapdoors":          ["wooden_trapdoors"],
    "pressure_plates":    ["wooden_pressure_plates"],
    "buttons":            ["wooden_buttons"],
    "signs":              ["standing_signs"],
    "wall_signs":         ["wall_signs"],
    "hanging_signs":      ["ceiling_hanging_signs"],
    "wall_hanging_signs": ["wall_hanging_signs"],
    "shelves":            ["wooden_shelves"],
}

# Forms offering a crafting-bench equivalent of in-world dyeing. Logs are excluded: their bark and
# end-grain colours run in different orders, a choice only the in-world click can make.
RECIPE_FORMS = {t: tag for t, tag in FORMS.items() if "log" not in t and "wood" not in t}


def vanilla_name(template, wood):
    """The vanilla registry path for a form of a wood, or None where the wood lacks that form."""
    if wood == "bamboo":
        # Bamboo's pillar is bamboo_block; it has no all-bark "wood" variant.
        if template == "%s_log":
            return "bamboo_block"
        if template == "stripped_%s_log":
            return "stripped_bamboo_block"
        if template in ("%s_wood", "stripped_%s_wood"):
            return None
    return template % wood


def write(relpath, obj):
    path = os.path.join(RES, relpath)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def remap_loot(node, vanilla, fireproof):
    """Deep-copy a loot table, repointing vanilla's own block/item ids at the fireproof clone."""
    if isinstance(node, dict):
        return {k: remap_loot(v, vanilla, fireproof) for k, v in node.items()}
    if isinstance(node, list):
        return [remap_loot(v, vanilla, fireproof) for v in node]
    if node == f"minecraft:{vanilla}":
        return f"{MODID}:{fireproof}"
    if node == f"minecraft:blocks/{vanilla}":
        return f"{MODID}:blocks/{fireproof}"
    return node


# Regenerate from scratch so blocks/forms dropped from the lists above leave no orphans behind.
# Only the generated trees are cleared; hand-kept files (e.g. wooddye.png) are untouched.
# The data folders are cleared under both of their names: 1.21 renamed them from plural to singular
# (see PLURAL_DATA_DIRS below), and this runs before the jar has said which this version uses.
for stale in [f"assets/{MODID}/blockstates", f"assets/{MODID}/models", f"assets/{MODID}/items",
              f"assets/{MODID}/lang", f"data/{MODID}/loot_table", f"data/{MODID}/loot_tables",
              f"data/{MODID}/tags", f"data/{MODID}/recipe", f"data/{MODID}/recipes",
              f"data/{MODID}/loot_modifiers", "data/minecraft", "data/neoforge", "data/forge", MODID]:
    shutil.rmtree(os.path.join(RES, stale), ignore_errors=True)

# Names for the in-game config screen (Mods -> WoodDye -> Config). NeoForge derives each key as
# "<modid>.configuration.<option>" with no call needed on the builder, and falls back to the option's
# TOML comment for the tooltip — so only the names live here. The logOrder values are named too,
# via LogOrder implementing TranslatableEnum; otherwise the dropdown shows raw constants.
CONFIG_LANG = {
    "wooddye.configuration.title": "WoodDye ReForged",
    "wooddye.configuration.useItems": "Consume Items",
    "wooddye.configuration.fireProof": "Allow Fireproofing",
    "wooddye.configuration.spongeDries": "Sponges Dry Out",
    "wooddye.configuration.logOrder": "Log Dye Order",
    "wooddye.configuration.logOrder.SAME_AS_PLANKS": "Match Plank Colour",
    "wooddye.configuration.logOrder.BARK": "Match Bark Colour",
    "wooddye.configuration.logOrder.INTELLIGENT": "Intelligent (by face)",
    "wooddye.configuration.dyeOrder": "Wood Order",
    "wooddye.configuration.dyeOrder.SHADE": "Light to Dark",
    "wooddye.configuration.dyeOrder.RAINBOW": "Rainbow",
    "wooddye.configuration.moddedWoods": "Modded Woods",
    "wooddye.configuration.netherWoods": "Nether Woods",
    "wooddye.configuration.excludedWoods": "Excluded Woods",
    "wooddye.configuration.toneOverrides": "Colour Overrides",
    "wooddye.configuration.showEffects": "Particles & Sound",
    "wooddye.configuration.showMessage": "Show Message",
    "wooddye.configuration.message": "Message Text",
    "wooddye.configuration.debugMode": "Debug Logging",
    "wooddye.already_fireproof": "Already fireproof",
    "item.wooddye.fireproof_name": "%s (Fireproof)",
    "wooddye.jade.fireproof": "Fireproof",
}

lang = {"itemGroup.wooddye": "WoodDye ReForged", **CONFIG_LANG}
tagged = {}       # vanilla tag path -> [fireproof block names]
everything = []
fireproofable = set()   # every vanilla block name that has a fireproof counterpart

with zipfile.ZipFile(MJAR) as z:
    present = set(z.namelist())
    vanilla_lang = json.loads(z.read("assets/minecraft/lang/en_us.json"))

    # ---- per-version formats, decided by what this version's jar actually contains ----
    # Item models: 1.21.4 introduced client items (assets/<ns>/items/<name>.json). Before that an
    # item's model is simply assets/<ns>/models/item/<name>.json. Either way ours is a verbatim clone
    # of vanilla's file, written to the same place under our namespace.
    CLIENT_ITEMS = "assets/minecraft/items/oak_planks.json" in present
    ITEM_MODEL_DIR = "items" if CLIENT_ITEMS else "models/item"
    # Data folders: 1.21 renamed them all at once, from plural (recipes, loot_tables, tags/blocks,
    # tags/items) to singular. Where vanilla's own oak planks recipe sits says which this is, and
    # ours go in folders of the same names — a file in the other spelling is silently never read.
    PLURAL_DATA_DIRS = "data/minecraft/recipes/oak_planks.json" in present
    RECIPE_DIR, LOOT_DIR, BLOCK_TAGS, ITEM_TAGS = (
        ("recipes", "loot_tables", "tags/blocks", "tags/items") if PLURAL_DATA_DIRS
        else ("recipe", "loot_table", "tags/block", "tags/item"))
    VANILLA_PLANKS_RECIPE = json.loads(z.read(f"data/minecraft/{RECIPE_DIR}/oak_planks.json"))
    # Recipe ingredients: 1.21.2 made them bare strings ("minecraft:x" / "#minecraft:tag"). Before
    # that each is an object ({"item": "minecraft:x"} / {"tag": "minecraft:tag"}, no '#'). Vanilla's
    # own oak planks recipe says which this version speaks. NeoForge's custom ingredients changed
    # with it: their discriminator is "type" in the object format (where it cannot clash with a
    # vanilla field) and "neoforge:ingredient_type" in the string format.
    OBJECT_INGREDIENTS = isinstance(VANILLA_PLANKS_RECIPE["ingredients"][0], dict)
    INGREDIENT_TYPE = "type" if OBJECT_INGREDIENTS else "neoforge:ingredient_type"
    # FORGE: the same ingredient (base minus subtracted, the same two fields) under Forge's id.
    DIFFERENCE_INGREDIENT = "forge:difference" if FORGE else "neoforge:difference"
    # Recipe results: 1.20.5 turned a result into an item stack, naming its item "id". Before that
    # the field is "item". Again vanilla's own recipe says which.
    RESULT_ITEM = "id" if "id" in VANILLA_PLANKS_RECIPE["result"] else "item"

    WOODS = [w for w in ALL_WOODS if f"assets/minecraft/blockstates/{w}_planks.json" in present]
    DYE_MAP = {color: wood for color, wood in DYE_MAP.items() if wood in WOODS}

    for wood in WOODS:
        for template, tag in FORMS.items():
            vanilla = vanilla_name(template, wood)
            if vanilla is None or f"assets/minecraft/blockstates/{vanilla}.json" not in present:
                continue
            name = f"fireproof_{vanilla}"

            # Blockstate + item model: verbatim clones, still pointing at the vanilla models.
            write(f"assets/{MODID}/blockstates/{name}.json",
                  json.loads(z.read(f"assets/minecraft/blockstates/{vanilla}.json")))
            write(f"assets/{MODID}/{ITEM_MODEL_DIR}/{name}.json",
                  json.loads(z.read(f"assets/minecraft/{ITEM_MODEL_DIR}/{vanilla}.json")))

            # Loot table: vanilla's, with its ids repointed at us.
            loot = json.loads(z.read(f"data/minecraft/{LOOT_DIR}/blocks/{vanilla}.json"))
            write(f"data/{MODID}/{LOOT_DIR}/blocks/{name}.json", remap_loot(loot, vanilla, name))

            lang[f"block.{MODID}.{name}"] = vanilla_lang[f"block.minecraft.{vanilla}"] + " (Fireproof)"
            tagged.setdefault(tag, []).append(name)
            everything.append(name)
            fireproofable.add(vanilla)

    # ===================== tags =====================
    def taglist(names):
        return {"values": [f"{MODID}:{n}" for n in names]}

    # Joining a *vanilla* tag means writing under data/minecraft/ — a tag's id comes from the
    # namespace of the folder holding it, so data/wooddye/tags/item/planks.json would define an
    # inert wooddye:planks rather than adding to minecraft:planks. The datapack loader merges our
    # values into vanilla's.
    #
    # Each fireproof block joins the same vanilla tag its original is in — except the *_that_burn
    # tags, which they are deliberately kept out of. #minecraft:logs is the non-burning superset of
    # #minecraft:logs_that_burn, so fireproof logs land in the former only.
    #
    # The per-wood log tags (#minecraft:oak_logs) are also deliberately skipped: they are the input
    # to vanilla's planks recipe, so joining them would let a fireproof log craft plain planks —
    # ambiguous against the fireproof planks recipe below.
    for tag, names in tagged.items():
        for tags_dir in (BLOCK_TAGS, ITEM_TAGS):
            write(f"data/minecraft/{tags_dir}/{tag}.json", taglist(names))
    write(f"data/minecraft/{BLOCK_TAGS}/mineable/axe.json", taglist(everything))

    # Our own tags, naming just the fireproof blocks, so recipes can require a fireproof input.
    for tag, names in tagged.items():
        write(f"data/{MODID}/{ITEM_TAGS}/fireproof_{tag}.json", taglist(names))

    # The legacy fireproof_* items exist only so 2.x worlds load; recipe viewers (JEI, EMI, REI)
    # all honour this common tag and hide the items and every recipe that makes them.
    write(f"data/c/{ITEM_TAGS}/hidden_from_recipe_viewers.json", taglist(everything))

    # The dyeable tags: references to vanilla's, and only to those this version has — a tag that
    # names a missing tag fails to load whole. A form with nothing to refer to (shelves, before
    # they existed) still gets its tag, empty, so a pack has somewhere to add to.
    for tag, refs in DYEABLE_TAGS.items():
        write(f"data/{MODID}/{BLOCK_TAGS}/dyeable/{tag}.json", {"values": [
            f"#minecraft:{ref}" for ref in refs if f"data/minecraft/{BLOCK_TAGS}/{ref}.json" in present]
            + [{"id": extra, "required": False} for extra in DYEABLE_EXTRAS.get(tag, [])]})

    # ===================== tones =====================
    # The average colour of every vanilla plank and log texture, which the mod sorts its dye chains
    # by. Measured here because a dedicated server's jar has no textures to measure; modded woods
    # are measured in game, from their own jars, by neoforge/TextureTones.java. Both follow the
    # block the way the client does — blockstate -> model -> texture slot — and both must average
    # the same way (core/Tone.java): alpha-weighted, in linear light.
    def to_linear(c):
        return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4

    def to_srgb(c):
        return c * 12.92 if c <= 0.0031308 else 1.055 * c ** (1 / 2.4) - 0.055

    def split_id(ident):
        ns, _, path = ident.rpartition(":")
        return ns or "minecraft", path

    def read_json(path):
        return json.loads(z.read(path)) if path in present else None

    def first_model(blockstate):
        if "variants" in blockstate:
            apply = next(iter(blockstate["variants"].values()))
        else:
            apply = blockstate["multipart"][0]["apply"]
        if isinstance(apply, list):
            apply = apply[0]
        return apply["model"]

    def texture_of(block, slots):
        model = first_model(read_json(f"assets/minecraft/blockstates/{block}.json"))
        textures = {}
        while model:
            ns, path = split_id(model)
            node = read_json(f"assets/{ns}/models/{path}.json")
            if node is None:
                break
            for slot, value in node.get("textures", {}).items():
                textures.setdefault(slot, value)   # the child's choice wins over its parent's
            model = node.get("parent")
        for slot in slots:
            value = textures.get(slot)
            while value and value.startswith("#"):
                value = textures.get(value[1:])
            if value:
                return value
        raise SystemExit(f"{block}: none of the texture slots {slots} is set")

    def average_hex(texture):
        ns, path = split_id(texture)
        with z.open(f"assets/{ns}/textures/{path}.png") as f:
            raw = Image.open(f).convert("RGBA").tobytes()
        total, weight = [0.0, 0.0, 0.0], 0.0
        for r, g, b, a in zip(*[iter(raw)] * 4):
            alpha = a / 255
            for i, channel in enumerate((r, g, b)):
                total[i] += alpha * to_linear(channel / 255)
            weight += alpha
        return "#%02x%02x%02x" % tuple(round(to_srgb(c / weight) * 255) for c in total)

    def tag_members(tag):
        """A vanilla block tag, flattened: block names without the namespace."""
        node = read_json(f"data/minecraft/{BLOCK_TAGS}/{tag}.json")
        members = []
        for value in (node or {}).get("values", []):
            value = value["id"] if isinstance(value, dict) else value
            if value.startswith("#"):
                members += tag_members(split_id(value[1:])[1])
            else:
                members.append(split_id(value)[1])
        return members

    tones = {}
    for block in tag_members("planks"):
        tones[f"minecraft:{block}"] = average_hex(texture_of(block, ["all", "texture", "side", "particle"]))
    for block in tag_members("logs") + tag_members("bamboo_blocks"):
        tones[f"minecraft:{block}"] = average_hex(texture_of(block, ["side", "all", "texture", "particle"]))
    write(f"{MODID}/tones.json", dict(sorted(tones.items())))

    # ===================== axe stripping =====================
    # Vanilla's AxeItem resolves stripping through NeoForge's neoforge:strippables data map (its own
    # in-code map is deprecated and only a fallback), so a fireproof log is strippable purely by
    # naming it here — no event handler needed. Stripping keeps the fireproofing, and the data map
    # copies the log's axis across for us. Both pillar shapes strip: the log and the all-bark wood.
    #
    # 26.3 made stripping data-driven in vanilla itself: data/minecraft/block_transformer/axe.json
    # holds a rule per log, and NeoForge dropped neoforge:strippables for a neoforge:transformables
    # data map that appends a rule to a transformer. Where the jar has that file, each fireproof
    # log gets vanilla's own rule for its original, with the ids repointed — so whatever vanilla
    # attaches to stripping (the sound, the tool damage) comes along.
    #
    # FORGE: MinecraftForge has no data maps at all, so nothing is written here and the same pairs
    # are stripped in code instead (neoforge/WoodDyeStripping.java, from the blocks the mod
    # registered). A data/neoforge folder would be dead weight in that jar.
    AXE_TRANSFORMER = "data/minecraft/block_transformer/axe.json"
    strip_pairs = []
    for wood in WOODS:
        for base, stripped in (("%s_log", "stripped_%s_log"), ("%s_wood", "stripped_%s_wood")):
            frm, to = vanilla_name(base, wood), vanilla_name(stripped, wood)
            if frm in fireproofable and to in fireproofable:
                strip_pairs.append((frm, to))

    if FORGE:
        pass   # stripped in code, as above
    elif AXE_TRANSFORMER in present:
        vanilla_rules = {}   # vanilla block -> (its rule, the transform entry the rule sits in)
        for entry in json.loads(z.read(AXE_TRANSFORMER)):
            for rule in entry["block_state_provider"].get("rules", []):
                vanilla_rules[rule["if_true"].get("blocks")] = (rule, entry)
        transformables = {}
        for frm, to in strip_pairs:
            rule, entry = vanilla_rules[f"minecraft:{frm}"]
            if rule["then"]["source"]["id"] != f"minecraft:{to}":
                raise SystemExit(f"{frm}: vanilla strips it to {rule['then']['source']['id']}, expected {to}")
            transformables[f"{MODID}:fireproof_{frm}"] = {
                "transformer": "minecraft:axe",
                "transform_data": {
                    **{k: v for k, v in entry.items() if k != "block_state_provider"},
                    "block_state_provider": {
                        **{k: v for k, v in entry["block_state_provider"].items() if k != "rules"},
                        "rules": [remap_loot(rule, frm, f"fireproof_{frm}") | {
                            "then": remap_loot(rule["then"], to, f"fireproof_{to}")}],
                    },
                },
            }
        write("data/neoforge/data_maps/block/transformables.json", {"values": transformables})
    else:
        write("data/neoforge/data_maps/block/strippables.json", {"values": {
            f"{MODID}:fireproof_{frm}": {"stripped_block": f"{MODID}:fireproof_{to}"}
            for frm, to in strip_pairs}})

    # Per-wood fireproof log tags, cloned from vanilla's, for the fireproof planks recipe.
    for wood in WOODS:
        vt = logs_tag(wood)
        values = json.loads(z.read(f"data/minecraft/{ITEM_TAGS}/{vt}.json"))["values"]
        write(f"data/{MODID}/{ITEM_TAGS}/fireproof_{vt}.json", taglist(
            [f"fireproof_{v.removeprefix('minecraft:')}" for v in values
             if isinstance(v, str) and v.removeprefix("minecraft:") in fireproofable]))

    # ===================== construction recipes (vanilla's, all-fireproof) =====================
    # Clone every vanilla recipe that builds one of our forms, repointing its wood ingredients and
    # its result at the fireproof counterparts, so fireproof wood builds fireproof everything.
    VANILLA_LOG_TAGS = {logs_tag(w) for w in WOODS}

    def remap_recipe(node):
        """Repoint wood ids at their fireproof clones. Returns (node, whether anything changed)."""
        if isinstance(node, dict):
            out, hit = {}, False
            for k, v in node.items():
                # Object format only: a tag is {"tag": "minecraft:oak_logs"}, with no '#' to tell
                # it from an item id, so it is recognised by its key instead.
                if (OBJECT_INGREDIENTS and k == "tag" and isinstance(v, str)
                        and v.removeprefix("minecraft:") in VANILLA_LOG_TAGS):
                    out[k], changed = f"{MODID}:fireproof_{v.removeprefix('minecraft:')}", True
                else:
                    out[k], changed = remap_recipe(v)
                hit |= changed
            return out, hit
        if isinstance(node, list):
            out, hit = [], False
            for v in node:
                item, changed = remap_recipe(v)
                out.append(item)
                hit |= changed
            return out, hit
        if isinstance(node, str):
            if node.removeprefix("minecraft:") in fireproofable:
                return f"{MODID}:fireproof_{node.removeprefix('minecraft:')}", True
            if node.removeprefix("#minecraft:") in VANILLA_LOG_TAGS and node.startswith("#"):
                return f"#{MODID}:fireproof_{node.removeprefix('#minecraft:')}", True
        return node, False

    built = 0
    for entry in sorted(z.namelist()):
        if not (entry.startswith(f"data/minecraft/{RECIPE_DIR}/") and entry.endswith(".json")):
            continue
        recipe = json.loads(z.read(entry))
        result = recipe.get("result")
        if not isinstance(result, dict) or result.get(RESULT_ITEM, "").removeprefix("minecraft:") not in fireproofable:
            continue
        inputs = {k: v for k, v in recipe.items() if k != "result"}
        cloned, has_fireproof_input = remap_recipe(inputs)
        # Every wood input must itself be fireproof, or the recipe would conjure fireproofing from
        # nothing (vanilla's bamboo_block is 9 plain bamboo, and there is no fireproof bamboo item).
        # Non-wood inputs such as sticks stay as they are — they have no fireproof counterpart.
        if not has_fireproof_input:
            continue
        cloned["result"], _ = remap_recipe(result)
        if "group" in cloned:
            cloned["group"] = f"fireproof_{cloned['group']}"
        write(f"data/{MODID}/{RECIPE_DIR}/fireproof_{entry.split('/')[-1]}", cloned)
        built += 1

    # ===================== pack metadata =====================
    # FORGE: Minecraft drops a pack that has no pack.mcmeta, and Forge 1.20.1 builds a mod's pack
    # without supplying one (ResourcePackLoader.createPackForMod makes a bare PathPackResources). The
    # log then says only "Missing metadata in pack mod:wooddye", and every tag, recipe and loot table
    # above is silently gone. NeoForge synthesises the metadata itself, so no other line ships this
    # file — and it must not be written there, where the mod pack's format is the loader's business.
    # The format number is this version's own, read from the jar; one pack serves both assets and
    # data, so the two formats have to agree for a single number to be right.
    if FORGE:
        formats = json.loads(z.read("version.json"))["pack_version"]
        if formats["data"] != formats["resource"]:
            raise SystemExit(f"{MC_VERSION}: data pack format {formats['data']} and resource pack format "
                             f"{formats['resource']} differ; pack.mcmeta needs Forge's per-type keys")
        write("pack.mcmeta", {"pack": {"description": "WoodDye ReForged resources",
                                       "pack_format": formats["data"]}})

        # FORGE: no block-drops event, so the drops of a fireproof position are stamped by a global
        # loot modifier (fireproof/FireproofLootModifier.java), which Forge finds through this pair
        # of files. NeoForge lines do it from BlockDropsEvent and write nothing here.
        write(f"data/{MODID}/loot_modifiers/fireproof_drops.json", {"type": f"{MODID}:fireproof_drops", "conditions": []})
        write("data/forge/loot_modifiers/global_loot_modifiers.json", {"replace": False, "entries": [f"{MODID}:fireproof_drops"]})

# ===================== fireproofing recipes (wood + magma cream -> fireproof wood) =====================
# The bench equivalent of right-clicking a placed block, for wood still in your bag. Eight blocks
# around one treatment, laid out exactly like vanilla's stained glass and terracotta: the bench is
# how you do a stack at once, the in-world click is how you do just one.
#
# Unlike dyeing, these must keep the wood they are given, so each recipe names its specific input
# block rather than taking a tag — there is one per fireproof block, each way.
#
# Both types are shaped recipes with one twist JSON cannot express (see
# crafting/DelegatingShapedRecipe.java): wooddye:fireproofing obeys the fireProof config, and
# wooddye:sponge_restore hands the sponge back rather than consuming it.
EIGHT_AROUND_ONE = ["###", "#X#", "###"]


# An ingredient in this version's format (see OBJECT_INGREDIENTS above).
def item_ingredient(item_id):
    return {"item": item_id} if OBJECT_INGREDIENTS else item_id


def tag_ingredient(tag_id):
    return {"tag": tag_id} if OBJECT_INGREDIENTS else f"#{tag_id}"


# Fireproofing on the bench is two recipes for every wood at once (crafting/FireproofStampRecipe):
# eight of a wood item around a magma cream gives eight fireproof ones, with the fireproofing a
# component on the same item rather than a block of its own; eight fireproof ones around a wet
# sponge gives them back plain, sponge kept. Both are custom types, so the JSON is just the type.
write(f"data/{MODID}/{RECIPE_DIR}/fireproof_stamp.json", {"type": f"{MODID}:fireproof_stamp", "category": "misc"})
write(f"data/{MODID}/{RECIPE_DIR}/fireproof_unstamp.json", {"type": f"{MODID}:fireproof_unstamp", "category": "misc"})

# The legacy fireproof_* blocks from 2.x still restore to plain wood on the bench, so nobody is
# left holding blocks they cannot convert. (Their magma-cream recipes are gone: they would match
# the very grid the generic recipe takes.)
for wood in WOODS:
    for template in FORMS:
        vanilla = vanilla_name(template, wood)
        if vanilla not in fireproofable:
            continue
        form = template.replace("%s_", "")
        write(f"data/{MODID}/{RECIPE_DIR}/{vanilla}_from_fireproof_wet_sponge.json", {
            "type": f"{MODID}:sponge_restore",
            "category": "misc",
            "group": f"wooddye_restoring_{form}",
            "pattern": EIGHT_AROUND_ONE,
            "key": {"#": item_ingredient(f"{MODID}:fireproof_{vanilla}"),
                    "X": item_ingredient("minecraft:wet_sponge")},
            "result": {"count": 8, RESULT_ITEM: f"minecraft:{vanilla}"},
        })

# ===================== dye recipes (shapeless: convertible wood + dye -> target wood) =====================
# Two families, kept mutually exclusive: fireproof wood dyes to fireproof wood, everything else to
# plain wood. The fireproof blocks are members of the vanilla tags, so the plain recipe has to
# subtract them back out or both recipes would match the same input.
for color, wood in DYE_MAP.items():
    for template, tag in RECIPE_FORMS.items():
        form = template.replace("%s_", "")
        result = vanilla_name(template, wood)
        write(f"data/{MODID}/{RECIPE_DIR}/dye_{wood}_{form}.json", {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "group": f"wooddye_{form}",
            "ingredients": [
                {INGREDIENT_TYPE: DIFFERENCE_INGREDIENT,
                 "base": tag_ingredient(f"minecraft:{tag}"),
                 "subtracted": tag_ingredient(f"{MODID}:fireproof_{tag}")},
                item_ingredient(f"minecraft:{color}_dye"),
            ],
            "result": {"count": 1, RESULT_ITEM: f"minecraft:{result}"},
        })
        write(f"data/{MODID}/{RECIPE_DIR}/dye_fireproof_{wood}_{form}.json", {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "group": f"wooddye_fireproof_{form}",
            "ingredients": [tag_ingredient(f"{MODID}:fireproof_{tag}"),
                            item_ingredient(f"minecraft:{color}_dye")],
            "result": {"count": 1, RESULT_ITEM: f"{MODID}:fireproof_{result}"},
        })

write(f"assets/{MODID}/lang/en_us.json", lang)
print(f"Minecraft {MC_VERSION}: {len(WOODS)} woods, {len(everything)} fireproof blocks, "
      f"{built} cloned construction recipes, {len(tones)} tones under {RES}")
