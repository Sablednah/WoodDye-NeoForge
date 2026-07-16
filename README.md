# WoodDye (NeoForge)

*No mod does wood more good!* Stain your wood with dyes, bleach it with bone meal, and fireproof it with magma cream.

A modern **NeoForge** rewrite of the classic [WoodDye](https://github.com/Sablednah/WoodDye) Bukkit plugin.

| | |
|---|---|
| Minecraft | 1.21.11 |
| Loader | NeoForge 21.11.42+ |
| Java | 21 |
| License | MIT |
| Side | Server-side logic; the client needs it installed too (it adds blocks) |

## Install

Drop `wooddye-<version>.jar` into `mods/` on both the server and the client. No dependencies.

## What it does

Right-click a wooden block with a dye to shift it one step along a light→dark "wood shade" chain —
no crafting, no block breaking, done in place:

```
Pale Oak → Cherry → Birch → Bamboo → Oak → Jungle → Acacia → Spruce → Mangrove → Dark Oak
```

- **Darken** one step with **black** or **brown** dye.
- **Lighten** one step with **white** or **light gray** dye, or **bone meal**.
- **Fireproof** wood by right-clicking it with **Magma Cream** — it becomes a matching `fireproof_*`
  block that will not burn or catch fire. Fireproof wood is still fully dyeable.
- **Un-fireproof** it again by right-clicking with a **Wet Sponge**, which soaks the magma cream back
  out and returns the plain vanilla wood (always available, even if fireproofing is disabled).

Every successful treatment plays a particle + sound and shows a configurable action-bar message.

### What can be treated

| Form | Dye | Fireproof |
|------|-----|-----------|
| Planks, slabs, stairs | ✅ | ✅ |
| Logs, stripped logs, wood, stripped wood | ✅ | ✅ |
| Fences, fence gates | ✅ | ✅ |
| Doors, trapdoors | ✅ | ✅ |
| Pressure plates, buttons | ✅ | ✅ |
| Signs (standing, wall, hanging) | ✅ | — *(already fireproof in vanilla)* |
| Shelves | ✅ | — *(already fireproof in vanilla)* |

Blocks that **do something when you right-click them** — gates, doors, trapdoors, buttons, signs and
shelves — are only dyed when you **sneak** + right-click, so an ordinary click still just opens,
presses, or edits them. Everything else dyes on a plain right-click.

A door is re-dyed as a whole (both halves), a sign keeps its text, and a shelf is only dyed while
**empty**, so nothing it holds can be lost.

*(All 10 woods have log forms. Bamboo's are `bamboo_block` / `stripped_bamboo_block` — its pillar
equivalent of a log; it has no all-bark "wood" form.)*

The dye→wood conversions are also available as **shapeless crafting recipes** (any wood block + dye)
for every form above except logs — a log's bark and end-grain run in different colour orders, a
choice only the in-world click can make:

| Dye | Wood | | Dye | Wood |
|-----|------|-|-----|------|
| White | Birch | | Black | Dark Oak |
| Light Gray | Oak | | Pink | Cherry |
| Yellow | Jungle | | Red | Mangrove |
| Orange | Acacia | | Lime | Bamboo |
| Brown | Spruce | | Gray | Pale Oak |

**Dyeing preserves fireproofing:** dye a *fireproof* block on the bench and you get the fireproof
form of the new wood. Only the magma cream / wet sponge decide whether wood is fireproof.

### Fireproof wood builds fireproof everything

Fireproof blocks have the **same crafting recipes as their vanilla originals** — fireproof logs make
fireproof planks, which make fireproof slabs, stairs, fences, doors, and the rest, at vanilla ratios.
Every *wooden* ingredient must itself be fireproof, so fireproofing can never be crafted into
existence; it only ever enters via magma cream. (Non-wood parts, like the sticks in a fence, are
ordinary — there is no fireproof stick.)

An **axe strips fireproof logs and wood** just as it strips the vanilla ones, keeping both the
fireproofing and the block's orientation.

Fireproof blocks also join the vanilla wood tags (`#minecraft:planks`, `#minecraft:wooden_doors`,
`#minecraft:mineable/axe`, …), so they mine and build like the wood they copy — but never the
`*_that_burn` tags. One consequence worth knowing: because they are in `#minecraft:planks`, a vanilla
recipe that takes any planks (a crafting table, say) accepts fireproof ones and gives an ordinary
result. Use a wet sponge if you want the plain wood back deliberately.

## Configuration (`config/wooddye-common.toml`)

| Option | Default | Purpose |
|--------|---------|---------|
| `useItems` | `false` | Consume the dye / magma cream / sponge when used (never in creative). |
| `fireProof` | `true` | Enable Magma Cream fireproofing. |
| `logOrder` | `INTELLIGENT` | Dye order for logs (see below). |
| `showEffects` | `true` | Play a particle + sound when wood is treated. |
| `showMessage` | `true` | Show an action-bar message on success. |
| `message` | `&aWood treated!` | The message text — supports `&` colour codes and `%P` (player name). |
| `debugMode` | `false` | Extra logging. |

Settings are read live; edits to the TOML apply on save. You can also edit them **in-game** from the
Mods menu → WoodDye → **Config** (single-player / LAN host). On a dedicated server, edit
`config/wooddye-common.toml` directly.

### Log dye order

A log's **bark** (sides) and **end-grain rings** (top/bottom) run through different colour orders, so
one fixed order can only look right on one of them. `logOrder` chooses how logs step:

- **`SAME_AS_PLANKS`** — by inner-wood colour, matching planks (looks right on the ends).
- **`BARK`** — by bark colour (looks right on the sides). Order:
  Birch → Acacia → Pale Oak → Bamboo → Oak → Jungle → Mangrove → Dark Oak → Spruce → Cherry.
- **`INTELLIGENT`** (default) — picks per click: clicking a **side** uses bark order, clicking a
  **top/bottom** uses wood order. The log keeps its orientation.

## Commands & permissions

- `/wooddye reload` — re-reads config (op / permission level `LEVEL_GAMEMASTERS`).
- Permission node `wooddye.candye` — may a player dye wood in-world (default: allow). Install a
  permissions manager (e.g. LuckPerms for NeoForge) to restrict it per group.

## Building from source

Requires JDK 21. Standard NeoForge ModDevGradle setup:

```
./gradlew build             # jar in build/libs/wooddye-<version>.jar
./gradlew runClient         # dev client
./gradlew runServer         # dev dedicated server
python3 tools/gen_resources.py   # regenerate assets/data under src/main/resources
```

NeoForge 21.11 removed the client model-generator datagen classes, so the mod's blockstates, item
models, loot tables, tags, recipes and lang are generated by `tools/gen_resources.py` (cloned from
the vanilla client jar) and committed under `src/main/resources`. Re-run it after changing the block
list in `core/WoodType.java`.

### How it fits together

`core/WoodType.java` is the single source of truth: an enum of the 10 woods, and a `Form` enum of the
wooden families (planks, slab, stairs, log, wood, fence, door, …) carrying what each one needs —
whether it gets a fireproof block, whether it needs sneak-to-dye, which colour chain it follows, and
any special in-world handling. Everything else is driven from it:

| Path | Role |
|------|------|
| `core/WoodType.java` | The woods, the forms, and their properties. **Start here.** |
| `registry/WoodDyeBlocks.java` | Registers a `fireproof_*` block per fireproof form × wood (128). |
| `registry/WoodDyeItems.java` | Their block items (doors get a `DoubleHighBlockItem`). |
| `neoforge/WoodTransforms.java` | The lookup tables: dye chains, vanilla↔fireproof, sneak/door/sign/shelf sets. |
| `neoforge/WoodDyeInteractions.java` | The right-click behaviour. |
| `tools/gen_resources.py` | Generates all assets/data by cloning vanilla's. Mirrors `WoodType.Form`. |

`gen_resources.py` clones rather than authors: it copies each vanilla blockstate and item model
verbatim (pointing at vanilla's models — the mod ships no textures), and remaps the ids in vanilla's
loot tables and recipes. That inherits the fiddly parts for free, like a door dropping only from its
lower half and a double slab dropping two. Keep the script's form list in step with `WoodType.Form`.

## Credits & licence

Originally a Bukkit plugin by **Sablednah** ([Sablednah/WoodDye](https://github.com/Sablednah/WoodDye)),
rewritten from the ground up for NeoForge. Released under the **MIT** licence — the original's
CC-BY-NC-ND terms were relicensed by the same author.
