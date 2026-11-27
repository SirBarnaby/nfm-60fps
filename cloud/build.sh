#!/usr/bin/env bash
# Builds madness.jar on Linux exactly as build.ps1 does on Windows (same javac
# flags, same manifest), without the Windows-only steps: the baked shaders
# (the game compiles them at its first start instead), the private runtime and
# the AOT cache. The jar runs on the PC with the runtime already there:
# copy it over madness.jar and delete madness.aot (it belongs to the old jar).
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_TOOL_OPTIONS=
jdk=${JDK_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}
build=$(mktemp -d)
trap 'rm -rf "$build"' EXIT
mkdir "$build/classes"
find src -name '*.java' > "$build/sources.txt"
libs=$(ls lib/*.jar)
"$jdk/bin/javac" -nowarn -encoding UTF-8 --release 25 -cp "$(echo "$libs" | tr '\n' ':')" -d "$build/classes" "@$build/sources.txt" 2>&1 | grep -v '^Picked up' || true
[ -f "$build/classes/nfm/lit/RunApp.class" ] || { echo 'Compilation failed.'; exit 1; }
cp -r src/nfm/lit/aimg "$build/classes/nfm/lit/aimg"
(cd src && find nfm/lit/features -type f ! -name '*.java' -exec cp --parents {} "$build/classes/" \;)
# one library to a line (a line that goes on starts with a space): the jar tool reads no line longer than 512 bytes
{
    echo 'Main-Class: nfm.lit.RunApp'
    echo "Class-Path:$(echo "$libs" | sed 's/^/  /' | sed '1s/^ //')"
    echo 'Enable-Native-Access: ALL-UNNAMED'
    echo
} > "$build/manifest.txt"
"$jdk/bin/jar" --create --file madness.jar --manifest "$build/manifest.txt" -C "$build/classes" . 2>&1 | grep -v '^Picked up' || true
commit=$(git rev-parse --verify --quiet HEAD || true)
dirty=$([ -n "$(git status --porcelain)" ] && echo true || echo false)
printf '{\n    "commit": "%s",\n    "dirty": %s,\n    "jarSha256": "%s",\n    "time": "%s",\n    "builtOn": "linux"\n}\n' \
    "$commit" "$dirty" "$(sha256sum madness.jar | cut -d' ' -f1)" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > build-info.json
echo "Done: madness.jar ($(stat -c %s madness.jar) bytes) from ${commit:0:7}$([ $dirty = true ] && echo ' + uncommitted changes')."
