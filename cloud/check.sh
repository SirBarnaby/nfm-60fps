#!/usr/bin/env bash
# The hidden checks that run on Linux: the recorded races (gameplay unchanged)
# and the extras' own checks. Build first (cloud/build.sh).
#
#   cloud/check.sh                 races + extras
#   cloud/check.sh races [args]    only the recorded races; extra -D options are passed on,
#                                  e.g. cloud/check.sh races -Dnfm.verify.rollback=50
#   cloud/check.sh looks           the recorded races with every look and sound extra on, no golden
#                                  frames: they must still match tick for tick
#   cloud/check.sh extras [name]   only the extras' checks (all, or one extra)
#
# Golden frames are compared with cloud/golden (made on Linux: its fonts differ
# from Windows', so test\golden never matches here). Make them once on a
# commit known to be good with: cloud/check.sh races -Dnfm.verify.golden.update=true
set -uo pipefail
cd "$(dirname "$0")/.."
export JAVA_TOOL_OPTIONS=
jdk=${JDK_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}
cp="madness.jar:$(ls cloud/natives/*.jar 2>/dev/null | tr '\n' ':')"
what=${1:-all}; shift || true
status=0
if [ "$what" = all ] || [ "$what" = races ] || [ "$what" = looks ]; then
    mkdir -p cloud/golden
    golden=-Dnfm.verify.golden=cloud/golden
    [ "$what" = looks ] && golden=-Dnfm.features=looks
    xvfb-run -a "$jdk/bin/java" -XX:-DontCompileHugeMethods --enable-native-access=ALL-UNNAMED -Dnfm.debug=false \
        -Dnfm.verify=test/races $golden "$@" -cp "$cp" nfm.lit.RunApp 2>&1 \
        | grep -E '^\[verify\] ([0-9]+ matched|golden frames:)|CHANGED|ERROR|FAILED|DIFFER' | tee /tmp/nfm-verify.txt
    grep -q ' 0 differed, 0 could not be run' /tmp/nfm-verify.txt || status=1
    grep -q 'CHANGED\|ERROR' /tmp/nfm-verify.txt && status=1
fi
if [ "$what" = all ] || [ "$what" = extras ]; then
    "$jdk/bin/java" -Djava.awt.headless=true -cp madness.jar nfm.lit.features.ExtrasCheck "${1:-all}" 2>&1 \
        | grep -E '^\[extras\].*(FAILED|checks:)' | tee /tmp/nfm-extras.txt
    grep -q ' 0 failed' /tmp/nfm-extras.txt || status=1
fi
exit $status
