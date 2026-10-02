#!/usr/bin/env bash
# Build WoodDye and copy the jar into the CurseForge test instance's mods/ folder, then you
# launch that instance from CurseForge to see the mod live.
#
# Usage:   ./deploy.sh
# Override the target instance:
#          WOODDYE_INSTANCE="/path/to/instance" ./deploy.sh
#
# One instance per Minecraft line, so the branch you are on decides where the jar goes.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

MC_VERSION="$(sed -n 's/^minecraft_version=//p' "$ROOT/gradle.properties" | head -1)"
if [ -z "$MC_VERSION" ]; then
    echo "!! Could not read minecraft_version from gradle.properties" >&2
    exit 1
fi

# The JDK tracks Minecraft: 1.20.x builds on 17, 1.21.x on 21, the calendar-versioned lines on
# 25. This repo has no bundled JDK, so fall back to the siblings that do. A JAVA_HOME set by the
# caller always wins.
if [ -z "${JAVA_HOME:-}" ]; then
    case "$MC_VERSION" in
        1.20.*) WANT_JDK="jdk17" ;;
        1.*)    WANT_JDK="jdk21" ;;
        *)      WANT_JDK="jdk25" ;;
    esac
    for candidate in "$ROOT/tools/$WANT_JDK" \
            "/mnt/d/Repos/sable/MobHealth-Forge/tools/$WANT_JDK" \
            "/mnt/d/Repos/sable/CityWorld-ReForged/tools/$WANT_JDK"; do
        [ -x "$candidate/bin/java" ] && { JAVA_HOME="$candidate"; break; }
    done
    if [ -z "${JAVA_HOME:-}" ]; then
        echo "!! Minecraft $MC_VERSION needs $WANT_JDK and none was found; set JAVA_HOME" >&2
        exit 1
    fi
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

# The test instances are shared with the other mods in this family. Most are named for their
# Minecraft version alone; the two below predate that.
case "$MC_VERSION" in
    1.21.11) DEFAULT_INSTANCE="MobHealth - Forge" ;;
    1.20.1)  DEFAULT_INSTANCE="1.20.1  Forge" ;;
    *)       DEFAULT_INSTANCE="$MC_VERSION" ;;
esac
INSTANCE="${WOODDYE_INSTANCE:-/mnt/c/Users/darre/curseforge/minecraft/Instances/$DEFAULT_INSTANCE}"
MODS="$INSTANCE/mods"

echo ">> Building WoodDye for Minecraft $MC_VERSION (JDK: $(basename "$JAVA_HOME"))..."
"$ROOT/gradlew" build --console=plain

if [ ! -d "$MODS" ]; then
    echo "!! Instance mods folder not found: $MODS" >&2
    exit 1
fi

# Name the jar exactly rather than taking the newest match. build/libs keeps whatever every other
# branch has built here, and "newest" is the right answer only until a build is up to date and
# does not rewrite its jar -- at which point another line's jar deploys silently.
MOD_VERSION="$(sed -n 's/^mod_version=//p' "$ROOT/gradle.properties" | head -1)"
JAR="$ROOT/build/libs/wooddye-${MOD_VERSION}+mc${MC_VERSION}.jar"
if [ ! -f "$JAR" ]; then
    echo "!! Expected jar not found: $JAR" >&2
    echo "!! build/libs holds: $(ls "$ROOT/build/libs" 2>/dev/null | tr '\n' ' ')" >&2
    exit 1
fi

# A running instance holds the jar open, so Windows refuses to replace it. Say so plainly: this
# otherwise fails looking like a success, and you test a stale jar wondering why nothing changed.
instance_locked() {
    echo "!! Could not $1 the jar in the instance's mods folder." >&2
    echo "!! Is the '$(basename "$INSTANCE")' instance still running? Close Minecraft and retry." >&2
    exit 1
}

echo ">> Removing previous WoodDye jars from the instance..."
rm -f "$MODS"/wooddye-*.jar || instance_locked "remove"

cp "$JAR" "$MODS/" || instance_locked "copy"

# Confirm the jar really landed and matches: a half-written copy is worse than a loud failure.
if ! cmp -s "$JAR" "$MODS/$(basename "$JAR")"; then
    echo "!! The deployed jar does not match the one just built." >&2
    exit 1
fi

echo ">> Deployed: $(basename "$JAR") ($(stat -c%s "$JAR") bytes)"
echo ">> Launch the '$(basename "$INSTANCE")' instance in CurseForge to test."
