## Fix release — update if you play 26.1, 26.2 or 26.3

**3.0.0 on Minecraft 26.1, 26.2 and 26.3 stopped clients joining a server** (including a
single-player world opened to LAN) with `Can't encode 'com.sablednah.wooddye.crafting.FireproofStampRecipe'`.
The bench fireproofing recipe could not be sent to the client. Fixed.

Also: NeoForge builds no longer log the harmless warning
`Reference map 'wooddye.refmap.json' … could not be read`.

1.20.1, 1.21.1 and 1.21.11 are rebuilt as 3.0.1 to keep all versions on one number; nothing else
changed for them.

## Requirements

| Jar | Minecraft | Loader | Java |
|---|---|---|---|
| `wooddye-3.0.1+mc26.3.jar` | 26.3 | NeoForge 26.3.0.33-beta – .36-beta | 25 |
| `wooddye-3.0.1+mc26.2.jar` | 26.2 | NeoForge 26.2 | 25 |
| `wooddye-3.0.1+mc26.1.2.jar` | 26.1 | NeoForge 26.1 | 25 |
| `wooddye-3.0.1+mc1.21.11.jar` | 1.21.11 | NeoForge 21.11 | 21 |
| `wooddye-3.0.1+mc1.21.1.jar` | 1.21.1 | NeoForge 21.1 | 21 |
| `wooddye-3.0.1+mc1.20.1.jar` | 1.20.1 | **Forge** 47 | 17 |

## Install

Replace the old jar in `mods/` on both the server and the client. See the
[3.0.0 notes](https://github.com/Sablednah/WoodDye-NeoForge/releases/tag/v3.0.0) for what 3.0 brought
and for upgrading from 2.x.
