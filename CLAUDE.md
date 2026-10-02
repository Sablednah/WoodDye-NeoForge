# WoodDye — notes for the next session

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

Reported from the LegendQuest session, 2026-08-21; fixed in the 2.1.0 work, 2026-10-02.
