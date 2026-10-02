# WoodDye — notes for the next session

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
