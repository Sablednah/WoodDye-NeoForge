#!/usr/bin/env bash
# Build WoodDye and copy the jar into the CurseForge NeoForge test instance's mods/ folder,
# then you launch that instance from CurseForge to see the mod live.
#
# Usage: ./deploy.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"

# JDK 21: this repo has no bundled JDK, so fall back to the sibling MobHealth-Forge one.
# Override either path from the environment if your setup differs.
if [ -z "${JAVA_HOME:-}" ]; then
    for candidate in "$ROOT/tools/jdk21" "/mnt/d/Repos/sable/MobHealth-Forge/tools/jdk21"; do
        [ -x "$candidate/bin/java" ] && { JAVA_HOME="$candidate"; break; }
    done
fi
if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "!! No JDK 21 found; set JAVA_HOME" >&2
    exit 1
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

# The NeoForge 1.21.11 test instance (shared with the MobHealth port).
INSTANCE="${WOODDYE_INSTANCE:-/mnt/c/Users/darre/curseforge/minecraft/Instances/MobHealth - Forge}"
MODS="$INSTANCE/mods"

echo ">> Building WoodDye..."
"$ROOT/gradlew" build --console=plain

if [ ! -d "$MODS" ]; then
    echo "!! Instance mods folder not found: $MODS" >&2
    exit 1
fi

echo ">> Removing previous WoodDye jars from the instance..."
rm -f "$MODS"/wooddye-*.jar

JAR="$(ls -t "$ROOT"/build/libs/wooddye-*.jar 2>/dev/null | grep -v -- '-sources' | head -1 || true)"
if [ -z "$JAR" ]; then
    echo "!! No built jar found in build/libs" >&2
    exit 1
fi

cp "$JAR" "$MODS/"
echo ">> Deployed: $(basename "$JAR")"
echo ">> Launch the '$(basename "$INSTANCE")' instance in CurseForge to test."
