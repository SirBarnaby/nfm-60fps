#!/usr/bin/env bash
# Renders a recorded race's 3D sound to a WAV (48 kHz stereo, as for headphones), to measure or
# listen to what a change does to the sound. Build first (cloud/build.sh).
#
#   cloud/sound.sh <race.nfmr> <out.wav> [extra -D options]
#   e.g. cloud/sound.sh test/races/race-20261116-045258-734-stage01.nfmr /tmp/out.wav -Dnfm.features=looks
#
# With STEAMAUDIO=<path to libphonon.so> (Steam Audio 4.8's Linux library, from Valve's release
# zip on GitHub: lib/linux-x64/libphonon.so) the track's muffling, reverb and echoes are in it;
# -Dnfm.sound.debug=true prints each car's state once a second.
set -uo pipefail
cd "$(dirname "$0")/.."
export JAVA_TOOL_OPTIONS=
jdk=${JDK_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}
cp="madness.jar:$(ls cloud/natives/*.jar 2>/dev/null | tr '\n' ':')"
race=$1; out=$2; shift 2
steam=()
[ -n "${STEAMAUDIO:-}" ] && steam=(-Dnfm.steamaudio="$STEAMAUDIO")
xvfb-run -a "$jdk/bin/java" -XX:-DontCompileHugeMethods --enable-native-access=ALL-UNNAMED -Dnfm.debug=false \
    -Dnfm.verify="$race" -Dnfm.sound=3d -Dnfm.sound.render="$out" "${steam[@]}" "$@" -cp "$cp" nfm.lit.RunApp 2>&1 \
    | grep -E '^\[verify\] [0-9]+ matched|^car0 |ERROR|FAILED|DIFFER'
