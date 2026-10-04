# WoodDye — notes for the next session

## Releasing

GitHub release `vX.Y.Z` on main, titled `WoodDye ReForged X.Y.Z`, with every line's jar attached
(`wooddye-X.Y.Z+mc<ver>.jar`) and `docs/release-X.Y.Z.md` as the notes. Publishing it runs
`.github/workflows/curseforge.yml`, which uploads each jar to CurseForge project 1613045 with its
Minecraft version, loader (Forge for 1.20.x, else NeoForge) and Java read from the jar name. It
skips until the repo secret `CURSEFORGE_TOKEN` exists; re-run by hand with workflow_dispatch.
CurseForge dedupes by content, so a re-upload of the same jar is rejected in moderation.

## Lessons from 3.0.0 → 3.0.1 (2026-10-04)

- ⚠ **26.x syncs recipes to clients on join, through each serializer's stream codec.** A
  `StreamCodec.unit(x)` refuses to encode any value not `equals(x)`, so a custom recipe served by
  a unit codec needs value equality, or *no client can join* ("Can't encode … expected …").
  3.0.0 shipped that way on 26.1–26.3 because no headless test has a client join. **Before a
  release, join each line's dev server with a real client** (Vivo: `vivo-showcase.sh client`).
- ⚠ **26.3's default `server.properties` has `white-list=true`.** A fresh dev server turns the
  buddy client away with "You are not white-listed", which looks like a mod failure.
  `vivo-showcase.sh ship` now writes `white-list=false`.

## To do — next batch

- **Sync the wood list and order from server to client** (asked for by Sable, 2026-10-03).
  JEI's "WoodDye: in world" dye cards (`compat/JeiInWorld`) are built from the *client's*
  `WoodTransforms`, which follows the client's own copy of the common config (`moddedWoods`,
  `netherWoods`, `excludedWoods`, `dyeOrder`, `toneOverrides`). On a server with
  `moddedWoods = true`, a client whose config has it off sees no dye cards for modded woods, though
  dyeing them works; a different `dyeOrder` shows the wrong chain. Seen on the Vivo 1.20.1 rig with
  Biomes O' Plenty: fireproofing cards for jacaranda (those use every wood, not the config), no dye
  cards. Shape of the fix: on login and on `/wooddye reload`, the server sends the wood and bark
  chains (block ids in order) in a custom payload; the client's JEI plugin uses them instead of
  its own tables, and re-adds its in-world recipes when they change. Needs a payload per line
  (NeoForge `CustomPacketPayload` on 1.21.x/26.x, Forge `SimpleChannel` on 1.20.1), and must
  tolerate a server without WoodDye (fall back to the client's tables).

## Version branches (set up 2026-10-02)

One branch per Minecraft line, each checked out permanently under
`/mnt/d/Repos/sable/WoodDye-worktrees/<branch>`. **Do not switch branches in this checkout.**

| Branch | Minecraft | Loader | JDK |
|---|---|---|---|
| `main` (trunk) | 1.21.11 | NeoForge 21.11.42 | 21 |
| `mc26.1` | 26.1.2 | NeoForge 26.1.2.95 | 25 |
| `mc26.2` | 26.2 | NeoForge 26.2.0.59 | 25 |
| `mc26.3` | 26.3 | NeoForge 26.3.0.33-beta (range capped below .37-beta) | 25 |
| `mc1.21.1` | 1.21.1 | NeoForge 21.1.251 | 21 |
| `mc1.20.1` | 1.20.1 | MinecraftForge 47.4.23 | 17 |

- **Features land on `main` and are cherry-picked** (`git cherry-pick -x <sha>`) into each
  worktree. Documentation lives on `main` only. The family standard is
  `SableCraft-Standards/CROSS-VERSION.md`.
- The branches chain: `mc26.1` → `mc26.2` → `mc26.3` each add to the one before, and
  `mc1.20.1` was cut from `mc1.21.1`.
- **`tools/gen_resources.py` is one file, identical on every branch.** It reads
  `minecraft_version` from `gradle.properties` and adapts to what that version's client jar
  contains (woods, forms, tags, item-model location, ingredient format, how stripping is
  declared). Change it on `main`, check `main`'s output is unchanged, cherry-pick, and re-run
  it on each branch. After a cherry-pick that touches it, `git status` after regenerating must
  be clean.
- JDKs are borrowed: `MobHealth-Forge/tools/{jdk21,jdk25}`, `CityWorld-ReForged/tools/jdk17`.
  `deploy.sh` picks the JDK and the CurseForge instance from `minecraft_version`.
- `WoodDye-worktrees/runserver.sh <worktree> <jdk>` starts the dev server headless, waits for
  "Done", stops it and prints the lines that matter. Gradle does not forward stdin, so console
  commands cannot be scripted; the mod logs its wood order on server start for that reason.
  Each worktree's `run/server.properties` has its own port so runs can overlap.

- `WoodDye-worktrees/rcontest.sh <worktree> <jdk> "<command>" ...` does the same but runs server
  commands over RCON before stopping, which is how `/wooddye woods` and `/wooddye showcase` get
  exercised headless (`execute if block ...` answers "Test passed"/"Test failed").

### Photographing the showcase on Vivo

Vivo is the shared Ubuntu test laptop (`ssh -i ~/.ssh/vivo_ed25519 sable@192.168.7.246`; its
manual, `~/dev/README.md` there, is the authority). A client runs on a private Xvfb display and can
be driven and screenshotted from a script, which Windows cannot do. **WoodDye's claim there is
display `:14`, game port 25585, RCON 25595** (password `wddev`), recorded in that README's table.

`WoodDye-worktrees/vivo-showcase.sh` runs it in stages: `ship <branch> [mod jars]`, `server`,
`client` (the `runClientBuddy` config, which auto-joins `dev_server_port`), `rcon "<cmd>"`,
`shoot <out.png>`, `stop`. It ships a `git archive` of the branch to `~/dev/WoodDye-<branch>`, so
**commit before shipping**. Always finish with `stop`: a client left running on Vivo burns five
cores unseen.

What each line needed beyond a retarget (the trunk's tag and tone engine compiled unchanged
on all the NeoForge lines):

- **26.1** — the recipe API: `Recipe.CommonInfo` + `CraftingBookInfo`, `ItemStackTemplate`
  results, `RecipeSerializer` is a record. `displayClientMessage(t, true)` → `sendOverlayMessage(t)`.
- **26.2** — dye items are a `ColorCollection`: `Items.DYE.pick(DyeColor.BLACK)`.
- **26.3** — poplar arrived with no code. `swing` and `drop` gained arguments. ⚠ The
  `neoforge:strippables` data map is **gone**: stripping is vanilla block transformers, appended
  to through `neoforge:transformables`. The old file only produced a WARN
  (`non-existent data map type`), i.e. fireproof logs would silently have stopped stripping.
  Grep each new line's server log for that warning.
- **1.21.1** — `ResourceLocation`, older registry/recipe signatures, item models in
  `models/item`, ingredients as `{item}`/`{tag}` objects, `IModFile.findResource`.
- **1.20.1 (Forge)** — the biggest step: `net.minecraftforge.*`, `ForgeConfigSpec`,
  `RegistryObject`, the pre-codec recipe API, plural data folders, `pack.mcmeta` (without it Forge
  drops the whole datapack silently), `mods.toml`. Three real differences from the other lines:
  **no in-game config screen** (Forge 47 has none; `client/WoodDyeClient` does not exist there),
  **stripping is code** (`neoforge/WoodDyeStripping`, a `BlockToolModificationEvent` handler,
  because Forge has no data maps), and the wet-sponge sound is `item.bucket.fill`
  (`SPONGE_ABSORB` is newer). "Loaded N recipes" in its log counts recipe *types*, not recipes.
  ⚠ **Production mod jars do not load in the 1.20.1 dev runtime**: their mixins are SRG-named
  (`InvalidMixinException ... terrablender`). Testing with Biomes O' Plenty there needs a real
  Forge server install running the reobfuscated jar from `build/libs`; photographing it would
  need a production client too. The NeoForge lines have no such limit — BOP loads straight
  into `run/mods`.

## Fireproofing as data — on every line since 2026-10-03

Fireproofing is a mark on a position (`fireproof/FireproofMarks`, vanilla `SavedData` per
dimension) plus a `wooddye:fireproof` item component (NBT on 1.20.1). Fire and lava skip marked
positions through four mixin injections; placing, breaking, drops and pistons keep marks in step
(`fireproof/FireproofEvents`); Create carries them through contraptions (`compat/CreateFireproof`,
a `MovementBehaviour` on every wood block); shaped and shapeless recipes carry the component
(mixins on `assemble`). The legacy 2.0 `fireproof_*` blocks stay registered so worlds load, convert
on chunk load (`FireproofMigration`), and are hidden from creative and recipe viewers.

Developed on branch `fireproof-data` (cut from `mc1.21.1`, where Create 6 runs in the dev
server), then ported to `main` and every line. Per-line differences worth knowing:

- **1.21.11 / 26.x:** `SavedDataType` + codec (id is an `Identifier` on 26.x), `damage_resistant`
  with the fire tag (a holder set on 26.x, resolved from the running server), `CustomRecipe` with
  no category on 26.x and a `RecipeSerializer` record built from a unit codec.
- **1.20.1 Forge:** NBT stamp; no per-stack fire resistance; drops via a global loot modifier
  (`FireproofLootModifier`, JSON written by the generator on that line only); mixins need the
  refmap recipe below, and `remap = false` for Forge-added targets; Create via
  `modCompileOnly` with the `slim` classifier.
- Create is compiled against (`create_compile` in gradle.properties) on every line, using the
  1.21.1 build where no build for that line exists; the hook only runs when Create is loaded.
- **Jade** (`compat/JadeFireproof`, `jade_compile` from Modrinth's Maven) adds a "Fireproof" line
  on the NeoForge lines (confirmed in game on 1.21.11). ⚠ The data provider and the tooltip
  provider must be **separate objects**: since 1.21.6 Jade rejects a data provider that is also a
  component provider, but only on a physical client, so a headless server reports the plugin as
  loaded while the game throws it out. Check the client log for `JadeFireproof loaded`. Not on 1.20.1: Jade 11 only syncs server data for blocks with a block
  entity, so a plain planks block can never carry the mark to the client there.
- **JEI** (`compat/JeiFireproof`, `jei_compile`): fireproof items as their own subtypes, the
  stamped stacks listed, and the two custom bench recipes shown as ordinary crafting recipes, one
  per wood item each way. Confirmed in a real client on 1.21.11 (Vivo dev) and 1.20.1 (Vivo
  production rig, with BOP). Three traps:
  - ⚠ `jei_compile` is a **Modrinth version id**, not a version number: JEI uses one number across
    loaders and the number resolves to the Fabric jar (intermediary names, `class_2960` errors).
  - ⚠ JEI can start **before the client has its tags** (1.20.1 did: WoodDye saw 0 tagged woods),
    so subtypes go to every block item with a wood sound type, and the stacks and recipes are
    added at runtime on `TagsUpdatedEvent` if they could not be registered.
  - 1.21.11: JEI after 27.21.0.54 needs NeoForge 21.11.44; the build and instances are on .42.
    JEI 15 (1.20.1) must be on the server too, or the client is refused (`mezz_config`).
- Legacy-block migration logs one notice and a session total; per-chunk lines only under
  `debugMode`.
- After an in-world treatment the whole inventory is resent (`sendAllDataToRemote`): the client
  predicts placing a wet sponge (a block item) and the single slot update lost to that on 1.20.1.

**Verified over RCON on every line** (`rcontest.sh`): fire, lava, vanilla piston, drops, legacy
conversion; with Create on 1.21.1 (bearing + radial chassis quarter turn) and on a production
Forge 1.20.1 server with Create 6.0.8 and Biomes O' Plenty. Crafting carry-through and stamped
placement were checked with a temporary self-check on 1.21.1 only. Sable confirmed in game on
1.20.1 (2026-10-03): vanilla pistons push **and** sticky-pull (spammed, no loss), Create's
mechanical piston extends **and** retracts, including reversed mid-run, and the wet-sponge restore
works once the inventory resync was in.

Two traps found that way, both fixed:
- `PistonEvent.Pre`'s structure resolver reports nothing on a retraction (the head is still in
  the way), so piston moves are read off the world after the move instead: a marked block that
  left its place and is now a moving-piston block one step along carries its mark.
- Create tells actors they are moving **before** the contraption entity exists, so the lift
  position is `anchor + localPos` — wrong for a retracting mechanical piston, which assembles at
  the extended end. `PistonContraption`'s protected `orientation` and `initialExtensionProgress`
  give the offset (read reflectively). `debugMode` makes the hook log lift/landing positions;
  that log is what found it. **Not tested anywhere:** block movers
other than pistons and Create; the creative tab and item names as seen on a client.

**1.20.1 mixin build recipe:** in `build.gradle` add
`maven { url = 'https://repo.spongepowered.org/repository/maven-public/' }`,
`annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'`, inside `legacyForge {}` a
`mixin { add sourceSets.main, 'wooddye.refmap.json'; config 'wooddye.mixins.json' }`, and
`tasks.named('jar', Jar) { manifest.attributes 'MixinConfigs': 'wooddye.mixins.json' }` (the
plugin wires only the dev runs). Untouched vanilla methods remap through the refmap; Forge-added
or Forge-replaced methods keep their names and need `remap = false` — `LavaFluid.isFlammable(…,
Direction)`, `FireBlock.canCatchFire`, and `FireBlock.tryCatchFire(…, Direction)`, which is what
1.20.1 Forge calls `checkBurnOut`. The processor warns "Unable to determine descriptor" when a
target's signature differs from the vanilla mapping and then writes an **empty refmap**: treat
that warning as an error. A Create jar on this line must come in through `modCompileOnly`
(`compileOnly` resolves to nothing), and the artifact has only `slim`/`all` classifiers.

## Fixed 2026-10-02: the config message ate ampersands

`WoodDyeInteractions.colourCodes()` now translates `&` only where a real format code follows, and
`feedback()` substitutes `%P` after it. The rest of this section is kept as the record of what was
wrong and why the fix is as small as it is.

`WoodDyeInteractions.feedback()` used to build the action-bar message like this:

```java
String text = message.replace('&', '§').replace("%P", player.getName().getString());
player.displayClientMessage(Component.literal(text), true);
```

`message` is `WoodDyeConfig.MESSAGE.get()` — written by the server owner.

**The bug:** `replace('&', '§')` is unconditional, so an ampersand that is not a
colour code still becomes a section sign, and the character after it is eaten as
though it were a code. An owner who configures

    Treated by %P & co.

gets `Treated by Steve § co.` on the action bar — the space swallowed. The
correct rule is to translate only where a real format code follows:

```java
// only where the next character is a genuine format code
ChatFormatting code = c == '&' && i + 1 < text.length()
        ? ChatFormatting.getByCode(text.charAt(i + 1))
        : null;
```

**Scope is genuinely small here**, so do not over-fix it:

- The text goes to `displayClientMessage(..., true)` — an action bar, seen only
  by a client. It is never read back through `getString()`, so WoodDye does
  *not* have the console/RCON half of this bug (see below).
- `%P` is substituted *after* the translation, so a player name cannot inject
  colour codes. That ordering is correct — keep it that way if you touch this.

## The wider pattern, since three sibling mods had it

Three of Sable's mods hit versions of the same thing in August 2026, and the
shared cause is worth knowing before writing any colour handling here:

1. **Blind `replace('&','§')`** — fine while every string is a lang template we
   wrote and correct by construction; wrong the moment owner- or player-authored
   text goes through it. This is WoodDye's half.
2. **Section signs stored in the text of a `Component.literal`** — renders
   correctly in game and hands the raw codes back to anything that is not a
   client, so consoles, logs and RCON tools get gibberish. Fixed by parsing into
   real `Style` objects. **WoodDye does not have this one** — it was checked.

Both were invisible from inside and obvious from outside, for the same reason:
you cannot see a representation error while looking through the thing that
interprets the representation. The client renders `§7` correctly, so the client
can never tell you that storing `§7` was the wrong choice.

**The cheap defence, if tests are ever added here:** assert on `getString()` as
well as on rendering. A check that a formatted message reads back as plain text
with no section sign in it catches the whole category at once.

Reference fixes: SableCraft-Standards `38cb7a0`, LegendQuest-ReForged `dda06b6`.

Reported from the LegendQuest session, 2026-08-21; fixed in the 3.0.0 work, 2026-10-02.
