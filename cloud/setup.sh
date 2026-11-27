#!/usr/bin/env bash
# One-time setup of a Linux (cloud) workspace for Need For Madness: LIT.
# Installs JDK 25 and a virtual display, and fetches the Linux natives of the
# game's libraries (the repository's lib\ holds only the Windows ones) into
# cloud/natives, which git ignores. Run as root: cloud/setup.sh
set -euo pipefail
cd "$(dirname "$0")/.."
if [ ! -x /usr/lib/jvm/java-25-openjdk-amd64/bin/javac ]; then
    apt-get update -q && apt-get install -y -q openjdk-25-jdk xvfb
fi
mkdir -p cloud/natives
version=3.4.3
for module in lwjgl lwjgl-sdl lwjgl-openal lwjgl-bgfx; do
    jar="cloud/natives/$module-$version-natives-linux.jar"
    for attempt in 1 2 3 4 5; do
        if [ -s "$jar" ] && unzip -tq "$jar" >/dev/null 2>&1; then break; fi
        curl -sS -o "$jar" "https://repo1.maven.org/maven2/org/lwjgl/$module/$version/$module-$version-natives-linux.jar" || true
        sleep $((attempt * 5))
    done
    unzip -tq "$jar" >/dev/null 2>&1 || echo "warning: $jar could not be fetched (only the graphics card renderer needs lwjgl-bgfx)"
done
# PhysX's Linux natives, for the PhysX extra's checks and hidden runs (the Windows ones are in lib/)
jar=cloud/natives/physx-jni-2.9.0-natives-linux.jar
for attempt in 1 2 3 4 5; do
    if [ -s "$jar" ] && unzip -tq "$jar" >/dev/null 2>&1; then break; fi
    curl -sS -o "$jar" "https://repo1.maven.org/maven2/de/fabmax/physx-jni/2.9.0/physx-jni-2.9.0-natives-linux.jar" || true
    sleep $((attempt * 10))
done
unzip -tq "$jar" >/dev/null 2>&1 || echo "warning: $jar could not be fetched (only the PhysX extra needs it; without it that extra stays off)"
echo "Setup done. Build with cloud/build.sh."
