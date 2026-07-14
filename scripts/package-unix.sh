#!/usr/bin/env bash
# Builds a native installer for Budget Guardian on macOS or Linux using jpackage.
#
# Prerequisites:
#   - JDK 21 (provides jpackage) on PATH
#   - Maven on PATH
#   - Linux: fakeroot + dpkg (deb) or rpmbuild (rpm)
#   - macOS: Xcode command-line tools (for dmg/pkg)
#
# Usage:
#   ./scripts/package-unix.sh              # platform default installer
#   ./scripts/package-unix.sh app-image    # portable app folder (no extra tools)

set -euo pipefail
cd "$(dirname "$0")/.."

TYPE="${1:-}"                      # empty = jpackage platform default
VERSION="$(grep -m1 '<version>' pom.xml | sed -E 's/.*<version>(.*)<\/version>.*/\1/')"
JAR="budget-guardian-${VERSION}-app.jar"

echo "Building fat jar (${JAR}) ..."
mvn -q -DskipTests clean package

if [ ! -f "target/${JAR}" ]; then
    echo "Expected target/${JAR} was not produced." >&2
    exit 1
fi

OUT="target/installer"
mkdir -p "${OUT}"

TYPE_ARG=()
if [ -n "${TYPE}" ]; then
    TYPE_ARG=(--type "${TYPE}")
fi

echo "Running jpackage ..."
jpackage \
    "${TYPE_ARG[@]}" \
    --name "Budget Guardian" \
    --app-version "${VERSION}" \
    --input target \
    --main-jar "${JAR}" \
    --main-class com.budgetguardian.app.Launcher \
    --dest "${OUT}" \
    --vendor "Budget Guardian" \
    --description "Personal finance data-structures showcase"

echo "Done. Output in ${OUT}"
