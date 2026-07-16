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

Run from the repo root:  python3 tools/gen_resources.py
"""
import json, os, shutil, zipfile

MODID = "wooddye"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
MJAR = "/home/sable/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.11_client.jar"

# light -> dark shade order. Order here is cosmetic (per-block files and tags are order-independent);
# the actual dye chain is defined by the WoodType enum order in core/WoodType.java.
WOODS = ["pale_oak", "cherry", "birch", "bamboo", "oak", "jungle", "acacia", "spruce", "mangrove", "dark_oak"]

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
for stale in [f"assets/{MODID}/blockstates", f"assets/{MODID}/models", f"assets/{MODID}/items",
              f"assets/{MODID}/lang", f"data/{MODID}/loot_table", f"data/{MODID}/tags",
              f"data/{MODID}/recipe", "data/minecraft", "data/neoforge"]:
    shutil.rmtree(os.path.join(RES, stale), ignore_errors=True)

lang = {"itemGroup.wooddye": "WoodDye"}
tagged = {}       # vanilla tag path -> [fireproof block names]
everything = []
fireproofable = set()   # every vanilla block name that has a fireproof counterpart

with zipfile.ZipFile(MJAR) as z:
    vanilla_lang = json.loads(z.read("assets/minecraft/lang/en_us.json"))

    for wood in WOODS:
        for template, tag in FORMS.items():
            vanilla = vanilla_name(template, wood)
            if vanilla is None:
                continue
            name = f"fireproof_{vanilla}"

            # Blockstate + item model: verbatim clones, still pointing at the vanilla models.
            write(f"assets/{MODID}/blockstates/{name}.json",
                  json.loads(z.read(f"assets/minecraft/blockstates/{vanilla}.json")))
            write(f"assets/{MODID}/items/{name}.json",
                  json.loads(z.read(f"assets/minecraft/items/{vanilla}.json")))

            # Loot table: vanilla's, with its ids repointed at us.
            loot = json.loads(z.read(f"data/minecraft/loot_table/blocks/{vanilla}.json"))
            write(f"data/{MODID}/loot_table/blocks/{name}.json", remap_loot(loot, vanilla, name))

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
        for domain in ("block", "item"):
            write(f"data/minecraft/tags/{domain}/{tag}.json", taglist(names))
    write("data/minecraft/tags/block/mineable/axe.json", taglist(everything))

    # Our own tags, naming just the fireproof blocks, so recipes can require a fireproof input.
    for tag, names in tagged.items():
        write(f"data/{MODID}/tags/item/fireproof_{tag}.json", taglist(names))

    # ===================== axe stripping =====================
    # Vanilla's AxeItem resolves stripping through NeoForge's neoforge:strippables data map (its own
    # in-code map is deprecated and only a fallback), so a fireproof log is strippable purely by
    # naming it here — no event handler needed. Stripping keeps the fireproofing, and the data map
    # copies the log's axis across for us. Both pillar shapes strip: the log and the all-bark wood.
    strippables = {}
    for wood in WOODS:
        for base, stripped in (("%s_log", "stripped_%s_log"), ("%s_wood", "stripped_%s_wood")):
            frm, to = vanilla_name(base, wood), vanilla_name(stripped, wood)
            if frm and to:
                strippables[f"{MODID}:fireproof_{frm}"] = {"stripped_block": f"{MODID}:fireproof_{to}"}
    write("data/neoforge/data_maps/block/strippables.json", {"values": strippables})

    # Per-wood fireproof log tags, cloned from vanilla's, for the fireproof planks recipe.
    for wood in WOODS:
        vt = logs_tag(wood)
        values = json.loads(z.read(f"data/minecraft/tags/item/{vt}.json"))["values"]
        write(f"data/{MODID}/tags/item/fireproof_{vt}.json", taglist(
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
        if not (entry.startswith("data/minecraft/recipe/") and entry.endswith(".json")):
            continue
        recipe = json.loads(z.read(entry))
        result = recipe.get("result")
        if not isinstance(result, dict) or result.get("id", "").removeprefix("minecraft:") not in fireproofable:
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
        write(f"data/{MODID}/recipe/fireproof_{entry.split('/')[-1]}", cloned)
        built += 1

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

for wood in WOODS:
    for template in FORMS:
        vanilla = vanilla_name(template, wood)
        if vanilla is None:
            continue
        form = template.replace("%s_", "")
        write(f"data/{MODID}/recipe/fireproof_{vanilla}_from_magma_cream.json", {
            "type": f"{MODID}:fireproofing",
            "category": "misc",
            "group": f"wooddye_fireproofing_{form}",
            "pattern": EIGHT_AROUND_ONE,
            "key": {"#": f"minecraft:{vanilla}", "X": "minecraft:magma_cream"},
            "result": {"count": 8, "id": f"{MODID}:fireproof_{vanilla}"},
        })
        # ...and back out again. The sponge is handed back, so this costs nothing but the wood.
        write(f"data/{MODID}/recipe/{vanilla}_from_fireproof_wet_sponge.json", {
            "type": f"{MODID}:sponge_restore",
            "category": "misc",
            "group": f"wooddye_restoring_{form}",
            "pattern": EIGHT_AROUND_ONE,
            "key": {"#": f"{MODID}:fireproof_{vanilla}", "X": "minecraft:wet_sponge"},
            "result": {"count": 8, "id": f"minecraft:{vanilla}"},
        })

# ===================== dye recipes (shapeless: convertible wood + dye -> target wood) =====================
# Two families, kept mutually exclusive: fireproof wood dyes to fireproof wood, everything else to
# plain wood. The fireproof blocks are members of the vanilla tags, so the plain recipe has to
# subtract them back out or both recipes would match the same input.
for color, wood in DYE_MAP.items():
    for template, tag in RECIPE_FORMS.items():
        form = template.replace("%s_", "")
        result = vanilla_name(template, wood)
        write(f"data/{MODID}/recipe/dye_{wood}_{form}.json", {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "group": f"wooddye_{form}",
            "ingredients": [
                {"neoforge:ingredient_type": "neoforge:difference",
                 "base": f"#minecraft:{tag}",
                 "subtracted": f"#{MODID}:fireproof_{tag}"},
                f"minecraft:{color}_dye",
            ],
            "result": {"count": 1, "id": f"minecraft:{result}"},
        })
        write(f"data/{MODID}/recipe/dye_fireproof_{wood}_{form}.json", {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "group": f"wooddye_fireproof_{form}",
            "ingredients": [f"#{MODID}:fireproof_{tag}", f"minecraft:{color}_dye"],
            "result": {"count": 1, "id": f"{MODID}:fireproof_{result}"},
        })

write(f"assets/{MODID}/lang/en_us.json", lang)
print(f"Generated {len(everything)} fireproof blocks, {built} cloned construction recipes under {RES}")
