## Fix release — update if you play 26.3

**On Minecraft 26.3, the creative inventory would not open** with WoodDye 3.0.x installed
(`Stack [Pale Oak Sign (Fireproof)] has already been added to the tab WoodDye ReForged`). The WoodDye
creative tab listed each fireproof sign twice, because a standing sign and its wall sign share one
item, and NeoForge 26.3 refuses a duplicate. Fixed on every version.

**26.3 now needs NeoForge 26.3.0.58-beta or later.** NeoForge 26.3.0.37 renamed a configuration
type the mod uses, so earlier 3.0.x jars refused anything from .37 up; 3.0.2 is built for .58 and
keeps your existing `wooddye-common.toml`. If your 26.3 pack is still on .33–.36, update NeoForge.

All six jars are rebuilt as 3.0.2 to keep one version number; nothing else changed on the others.

## Requirements

| Jar | Minecraft | Loader | Java |
|---|---|---|---|
| `wooddye-3.0.2+mc26.3.jar` | 26.3 | NeoForge 26.3.0.58-beta or later | 25 |
| `wooddye-3.0.2+mc26.2.jar` | 26.2 | NeoForge 26.2 | 25 |
| `wooddye-3.0.2+mc26.1.2.jar` | 26.1 | NeoForge 26.1 | 25 |
| `wooddye-3.0.2+mc1.21.11.jar` | 1.21.11 | NeoForge 21.11 | 21 |
| `wooddye-3.0.2+mc1.21.1.jar` | 1.21.1 | NeoForge 21.1 | 21 |
| `wooddye-3.0.2+mc1.20.1.jar` | 1.20.1 | **Forge** 47 | 17 |

## Install

Replace the old jar in `mods/` on both the server and the client. See the
[3.0.0 notes](https://github.com/Sablednah/WoodDye-NeoForge/releases/tag/v3.0.0) for what 3.0 brought
and for upgrading from 2.x.
