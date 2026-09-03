#!/usr/bin/env bash
# =====================================================================
# LarpingAntiCheat - local build script (no Maven/Maven repo required)
#
# Compiles the plugin against the Paper API and produces
#   target/LarpingAntiCheat.jar
#
# Requirements:
#   * A JDK or JRE 21+ available as JAVA_HOME (or `java` on PATH)
#   * The Eclipse compiler batch jar (ecj)  -- downloaded automatically
#     on first use if Maven Central is reachable, otherwise set
#     ECJ_JAR to point at an existing
#     org.eclipse.jdt.core.compiler.batch_*.jar
#   * Paper API jar on the classpath (provided scope normally) - set
#     PAPER_API to a paper-api-1.21.x jar; otherwise a minimal stub
#     API (stubs/ dir) is generated from the scripts/mkstubs.sh.
#
# The normal CI build is `mvn package` (see pom.xml). This script is a
# fallback for environments where the Maven repository is unreachable.
# =====================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
SRC="$ROOT/src/main/java"
RES="$ROOT/src/main/resources"
OUT="$ROOT/target/classes"
JAR="$ROOT/target/LarpingAntiCheat.jar"

JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
ECJ_JAR="${ECJ_JAR:-}"
PAPER_API="${PAPER_API:-}"

if [ -z "$ECJ_JAR" ]; then
  ECJ_JAR="$ROOT/target/ecj.jar"
  if [ ! -f "$ECJ_JAR" ]; then
    echo "Downloading Eclipse compiler (ecj) ..."
    curl -fsSL -o "$ECJ_JAR" \
      "https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/3.41.0/ecj-3.41.0.jar" \
      || { echo "ERROR: cannot download ecj; set ECJ_JAR manually"; exit 1; }
  fi
fi

CP=""
if [ -n "$PAPER_API" ] && [ -f "$PAPER_API" ]; then
  CP="$PAPER_API"
else
  echo "WARNING: PAPER_API not set; build a stub API via scripts/mkstubs.sh"
  STUB_JAR="$ROOT/target/bukkit-stub.jar"
  [ -f "$STUB_JAR" ] && CP="$STUB_JAR"
fi

rm -rf "$OUT"; mkdir -p "$OUT"
find "$SRC" -name '*.java' > "$ROOT/target/sources.txt"
echo "Compiling $(wc -l < "$ROOT/target/sources.txt") sources ..."
"$JAVA" -jar "$ECJ_JAR" -21 -nowarn -proc:none \
  ${CP:+-cp "$CP"} -d "$OUT" @"$ROOT/target/sources.txt"

cp -r "$RES"/* "$OUT"/
rm -f "$JAR"
( cd "$OUT" && zip -qr "$JAR" . )
echo "Built $JAR ($(du -h "$JAR" | cut -f1))"
