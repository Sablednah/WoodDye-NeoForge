## Requirements

One jar per Minecraft version — pick the one whose name ends in your version:

| Jar | Minecraft | Loader | Java |
|---|---|---|---|
| `wooddye-3.0.0+mc26.3.jar` | 26.3 | NeoForge 26.3.0.33-beta – .36-beta | 25 |
| `wooddye-3.0.0+mc26.2.jar` | 26.2 | NeoForge 26.2 | 25 |
| `wooddye-3.0.0+mc26.1.2.jar` | 26.1 | NeoForge 26.1 | 25 |
| `wooddye-3.0.0+mc1.21.11.jar` | 1.21.11 | NeoForge 21.11 | 21 |
| `wooddye-3.0.0+mc1.21.1.jar` | 1.21.1 | NeoForge 21.1 | 21 |
| `wooddye-3.0.0+mc1.20.1.jar` | 1.20.1 | **Forge** 47 | 17 |

No dependencies. Optional: **Create** (fireproofing rides contraptions), **Jade** (shows "Fireproof"),
**JEI** (fireproof items and an "in world" tab).

## Install

Drop the jar into `mods/` on both the server and the client.

**Upgrading from 2.x:** back up your world first, as for any major version. Fireproof blocks from 2.x
turn into the ordinary block, fireproofed, the first time their chunk loads; fireproof items turn into
fireproof ordinary items as they pass through a player's inventory. Nothing to do by hand.

## What's new in 3.0

### Every wood, in a measured order
- Woods are found through block tags, so a wood from a newer Minecraft or from another mod is picked
  up with no code. Switch on `moddedWoods` (and `netherWoods` for crimson and warped).
- The dye order is **measured** from each wood's texture rather than written by hand — vanilla's at
  build time, a mod's from its own jar at server start. `dyeOrder = RAINBOW` sorts around the colour
  wheel instead of light to dark.
- The bark order changed slightly as a result (bamboo is now second, oak ahead of pale oak).
- New config: `dyeOrder`, `moddedWoods`, `netherWoods`, `excludedWoods`, `toneOverrides`.
- New commands: `/wooddye woods` (the order, with each wood's measured lightness) and
  `/wooddye showcase` (builds every wood side by side).

### Fireproofing as data
- Fireproofing is now a property of the block where it stands, not a block of its own, so **any wood**
  can be fireproofed, a mod's included.
- It follows the block: drops as a fireproof item, places as fireproof, crafts into fireproof
  products, and travels with pistons (sticky pulls included), axe stripping, and Create contraptions.
- Hold Magma Cream or a Wet Sponge to see fireproof blocks nearby.
- Bench: 8 of any wood around Magma Cream, or 8 fireproof around a Wet Sponge to restore.
- `/wooddye fireproof [<pos> [set|clear]]` for admins.

### Integrations
- **Create** — marks ride mechanical pistons, bearings and chassis.
- **Jade** — "Fireproof" under a fireproof block (not on 1.20.1).
- **JEI** — fireproof items listed with their bench recipes, plus a "WoodDye: in world" tab for every
  right-click.

### Versions
- New: Minecraft 26.1, 26.2, 26.3 (with the new **poplar** wood), 1.21.1, and 1.20.1 on Forge.

### Fixes
- An ampersand in the action-bar message is no longer eaten when it isn't a colour code.
- The client no longer shows an empty hand after restoring with a wet sponge.

### Known limitations
- JEI's in-world dye cards follow the player's own config, not the server's.
- 1.20.1: no in-game config screen (edit the TOML); a *dropped* fireproof item is not itself
  fire-resistant.
