#!/usr/bin/env bash
# Reproducible before/after measurements for the opt-in zenz correction probes.
set -euo pipefail

head_root="$(pwd)"
baseline_root="${RUNNER_TEMP:?}/zenz-correction-baseline"
artifact_dir="${ZENZ_PERF_ARTIFACT_DIR:?}"
baseline_ref="${ZENZ_PERF_BASELINE_REF:-dev}"
mkdir -p "$artifact_dir/logs"
collect_unit_results() {
    local kind="${1:-latest}" label root edition directory
    for label in before after; do
        root="$head_root"; if [[ "$label" == before ]]; then root="$baseline_root"; fi
        for edition in Full Lite; do
            directory="$root/app/build/test-results/test${edition}StandardDebugUnitTest"
            if [[ -d "$directory" ]]; then
                mkdir -p "$artifact_dir/$label/$kind/$edition"
                cp "$directory"/TEST-*.xml "$artifact_dir/$label/$kind/$edition/" 2>/dev/null || true
            fi
        done
    done
}
trap collect_unit_results EXIT
export IME_EMULATOR_LOG_DIR="$artifact_dir/logs"
export IME_EMULATOR_READINESS_LOG="$artifact_dir/logs/android-readiness.log"
source "$head_root/.github/scripts/ime-emulator-common.sh"
ime_emulator_prepare
serial="$IME_EMULATOR_SERIAL"
package=com.kazumaproject.markdownhelperkeyboard
runner="$package.test/androidx.test.runner.AndroidJUnitRunner"

# Use identical benchmark sources with the baseline converter. The feature-specific
# device assertion needs the new API and is deliberately not copied into baseline.
git worktree add --detach "$baseline_root" "$baseline_ref"
git -C "$baseline_root" submodule update --init --recursive
python3 - "$head_root" "$baseline_root" <<'PY'
from pathlib import Path
import sys
head, baseline = map(Path, sys.argv[1:])
prefix = Path('app/src/androidTest/java/com/kazumaproject/markdownhelperkeyboard')
for relative in ['converter/ZenzCorrectionPerformanceInstrumentedTest.kt', 'zenz/ZenzRuntimeInstrumentedTest.kt']:
    (baseline/prefix/relative).write_text((head/prefix/relative).read_text())
relative = prefix/'zenz/ZenzFloatingCandidateInstrumentedTest.kt'
source = (head/relative).read_text()
start = source.index('    @Test(timeout = 900_000)\n    fun measureFloatingResultLatency()')
end = source.index('    private fun restore(', start)
original = (baseline/relative).read_text()
position = original.index('    private fun restore(')
(baseline/relative).write_text(original[:position]+source[start:end]+original[position:])
PY

build() {
    local root="$1" label="$2"
    (cd "$root" && ./gradlew :app:assembleFullStandardDebug :app:assembleFullStandardDebugAndroidTest \
        --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g \
        -Pkotlin.compiler.execution.strategy=in-process -PzenzDebugOptimizedNative=true \
        --console=plain) > "$artifact_dir/logs/$label-build.txt" 2>&1
}
build "$baseline_root" before
build "$head_root" after
(cd "$head_root" && ./gradlew :app:testFullStandardDebugUnitTest \
    --tests '*Zenz*Test' --tests '*KanaKanjiConversionSessionParityTest' \
    --tests '*CandidateCorrectionPolicyTest' --tests '*EnglishCandidateCaseResolverTest' \
    --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g \
    -Pkotlin.compiler.execution.strategy=in-process --console=plain) \
    > "$artifact_dir/logs/related-unit-tests.txt" 2>&1

# Verify the edition without zenz as well, using the same scoped unit-test selection.
(cd "$head_root" && ./gradlew :app:testLiteStandardDebugUnitTest \
    --tests '*Zenz*Test' --tests '*KanaKanjiConversionSessionParityTest' \
    --tests '*CandidateCorrectionPolicyTest' --tests '*EnglishCandidateCaseResolverTest' \
    :app:assembleLiteStandardDebug \
    --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g \
    -Pkotlin.compiler.execution.strategy=in-process --console=plain) \
    > "$artifact_dir/logs/related-lite-tests-and-build.txt" 2>&1

collect_unit_results related

# Reproduce failures seen in the broad suite without modifying unrelated fixtures.
# Keep them outside the scoped gate, but fail if the proposed code introduces new failures.
for label in before after; do
    root="$head_root"; if [[ "$label" == before ]]; then root="$baseline_root"; fi
    audit_status=0
    (cd "$root" && ./gradlew :app:testFullStandardDebugUnitTest \
        --tests '*CustomDirectInputTransitionTest' --tests '*IMEServiceCupertinoCandidateAppearanceTest' \
        --tests '*IMEServiceFloatingCursorRegressionTest' --tests '*QwertyEnglishSpaceInputTest' \
        --tests '*SplitKeyboardServiceSyncTest' --tests '*DictionaryBrowserTest' \
        --no-daemon --max-workers=2 -Dorg.gradle.jvmargs=-Xmx2g \
        -Pkotlin.compiler.execution.strategy=in-process --console=plain) \
        > "$artifact_dir/logs/$label-baseline-audit.txt" 2>&1 || audit_status=$?
    mkdir -p "$artifact_dir/$label/audit"
    echo "$audit_status" > "$artifact_dir/$label/audit/exit-status.txt"
    cp "$root"/app/build/test-results/testFullStandardDebugUnitTest/TEST-*.xml "$artifact_dir/$label/audit/"
done
python3 - "$artifact_dir" <<'PY'
from pathlib import Path
import json,sys,xml.etree.ElementTree as ET
root=Path(sys.argv[1])
def failures(label):
    result=set()
    for file in (root/label/'audit').glob('TEST-*.xml'):
        for case in ET.parse(file).iter('testcase'):
            if case.find('failure') is not None or case.find('error') is not None:
                result.add(case.attrib['classname']+'#'+case.attrib['name'])
    return result
before,after=failures('before'),failures('after')
report=dict(before=sorted(before),after=sorted(after),new_failures=sorted(after-before))
(root/'baseline-test-audit.json').write_text(json.dumps(report,indent=2))
assert not report['new_failures'], report['new_failures']
PY

apk() {
    python3 - "$1" "$2" <<'PY'
from pathlib import Path
import sys
sub = 'app/build/outputs/apk/' + ('androidTest/' if sys.argv[2]=='test' else '') + 'fullStandard/debug'
files = list((Path(sys.argv[1])/sub).glob('*.apk'))
assert len(files)==1, files
print(files[0])
PY
}
record_build() {
    local root="$1" label="$2"
    git -C "$root" rev-parse HEAD > "$artifact_dir/$label-revision.txt"
    python3 - "$(apk "$root" app)" "$artifact_dir/$label-apk.json" <<'PY'
import hashlib,json,sys,zipfile
from pathlib import Path
assets={}
with zipfile.ZipFile(sys.argv[1]) as archive:
    for name in sorted(archive.namelist()):
        if name.startswith('assets/') and not name.endswith('/'):
            h=hashlib.sha256()
            with archive.open(name) as source:
                for chunk in iter(lambda:source.read(1024*1024), b''): h.update(chunk)
            assets[name]=h.hexdigest()
h=hashlib.sha256()
with open(sys.argv[1],'rb') as source:
    for chunk in iter(lambda:source.read(1024*1024), b''): h.update(chunk)
Path(sys.argv[2]).write_text(json.dumps(dict(sha256=h.hexdigest(), assets=assets), indent=2))
PY
}
record_build "$baseline_root" before
record_build "$head_root" after
python3 - "$artifact_dir" <<'PY'
import json,sys
from pathlib import Path
root=Path(sys.argv[1])
assert json.loads((root/'before-apk.json').read_text())['assets'] == json.loads((root/'after-apk.json').read_text())['assets'], 'Packaged dictionary/model assets differ'
PY
adb -s "$serial" shell getprop > "$artifact_dir/device-properties.txt"
free -h > "$artifact_dir/runner-memory.txt"
lscpu > "$artifact_dir/runner-cpu.txt"

install_build() {
    local root="$1"
    adb -s "$serial" install -r -t "$(apk "$root" app)" > /dev/null
    adb -s "$serial" install -r -t "$(apk "$root" test)" > /dev/null
}
run_probe() {
    local label="$1" name="$2" test="$3" output="$4" sample="$5"
    shift 5
    local directory="$artifact_dir/$label"
    mkdir -p "$directory"
    adb -s "$serial" shell am force-stop "$package"
    local stop_file="$directory/$name-sampling-finished" sampler=""
    rm -f "$stop_file"
    if [[ "$sample" == true ]]; then
        (
            while [[ ! -f "$stop_file" ]]; do
                date -u '+%Y-%m-%dT%H:%M:%SZ'
                adb -s "$serial" shell dumpsys meminfo "$package" || true
                adb -s "$serial" shell dumpsys meminfo "$package:zenz" || true
                sleep 1
            done
        ) > "$directory/$name-pss-samples.txt" 2>&1 &
        sampler=$!
    fi
    echo "Running $label/$name"
    local result=0
    timeout 7200 adb -s "$serial" shell am instrument -w -r -e class "$test" "$@" "$runner" \
        > "$directory/$name-instrumentation.txt" 2>&1 || result=$?
    touch "$stop_file"
    if [[ -n "$sampler" ]]; then wait "$sampler" || true; fi
    rm -f "$stop_file"
    adb -s "$serial" exec-out run-as "$package" cat "files/$output" \
        > "$directory/$name-report.txt" 2> "$directory/$name-pull-errors.txt" || true
    if ((result != 0)) || ! grep -q 'OK (' "$directory/$name-instrumentation.txt"; then
        cat "$directory/$name-instrumentation.txt"
        return 1
    fi
}
core="$package.converter.ZenzCorrectionPerformanceInstrumentedTest#measureCorrectionConversion"
native="$package.zenz.ZenzRuntimeInstrumentedTest#measureGenerationPerformance"
floating="$package.zenz.ZenzFloatingCandidateInstrumentedTest#measureFloatingResultLatency"

# Alternate order for each set, with fresh processes and the same warm-up each time.
for set in 0 1 2; do
    order=(before after)
    if ((set % 2)); then order=(after before); fi
    for label in "${order[@]}"; do
        root="$head_root"; if [[ "$label" == before ]]; then root="$baseline_root"; fi
        install_build "$root"
        run_probe "$label" "time-set$set" "$core" zenz-correction-perf.txt false \
            -e zenzCorrectionPerf true -e sets 1 -e iterations 100 -e warmups 20
    done
done
for label in before after; do
    root="$head_root"; if [[ "$label" == before ]]; then root="$baseline_root"; fi
    install_build "$root"
    run_probe "$label" memory "$core" zenz-correction-perf.txt true \
        -e zenzCorrectionPerf true -e memoryOnly true -e sets 1 -e iterations 100 -e warmups 20
    for cold in 0 1 2 3 4; do
        run_probe "$label" "cold$cold" "$core" zenz-correction-perf.txt false -e zenzCorrectionPerf true -e coldOnly true
        run_probe "$label" "zenz-cold$cold" "$native" zenz-generation-perf.txt false -e zenzGenerationPerf true -e coldOnly true
    done
    run_probe "$label" generation "$native" zenz-generation-perf.txt false -e zenzGenerationPerf true
    run_probe "$label" generation-memory "$native" zenz-generation-perf.txt true -e zenzGenerationPerf true
    run_probe "$label" floating "$floating" zenz-floating-perf.txt false -e zenzFloatingPerf true
    run_probe "$label" floating-memory "$floating" zenz-floating-perf.txt true -e zenzFloatingPerf true
done
install_build "$head_root"
run_probe after selection "$core" zenz-correction-perf.txt false \
    -e zenzCorrectionPerf true -e selectionOnly true -e sets 1 -e iterations 100 -e warmups 20
run_probe after correction-ime "$package.zenz.ZenzFloatingCandidateInstrumentedTest#correctionSettingsAutomaticallySelectTheMinimumNBestReading" \
    zenz-correction-ime.txt false -e zenzCorrectionImeProbe true
