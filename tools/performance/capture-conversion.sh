#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 2 ] || [ "$#" -gt 6 ]; then
    echo "Usage: $0 OUTPUT_DIRECTORY snapshot|benchmark|stages|allocations [CASE [BACKEND [WARMUP [ROUNDS]]]]" >&2
    exit 2
fi

output_directory=$1
mode=$2
scenario=${3:-}
backend=${4:-INCREMENTAL_SESSION}
warmup=${5:-20}
iterations=${6:-100}
package_name=${CONVERSION_PARITY_PACKAGE:-com.kazumaproject.conversionparity.lite}
adb_command=(adb)
if [ -n "${CONVERSION_PARITY_ADB_PORT:-}" ]; then
    adb_command+=(-P "$CONVERSION_PARITY_ADB_PORT")
fi
if [ -n "${ANDROID_SERIAL:-}" ]; then
    adb_command+=(-s "$ANDROID_SERIAL")
fi

test_class=com.kazumaproject.markdownhelperkeyboard.converter.ConversionParityInstrumentedTest
case "$mode" in
    snapshot)
        test_method=captureConversionResults
        report=snapshot
        output_name=snapshot
        ;;
    benchmark|stages|allocations)
        if [[ ! $scenario =~ ^(ja|en)-[a-z-]+$ ]]; then
            echo "Pass a ja-* or en-* case ID for $mode" >&2
            exit 2
        fi
        if [[ $backend != LEGACY && $backend != INCREMENTAL_SESSION ]]; then
            echo "Use LEGACY or INCREMENTAL_SESSION as the backend" >&2
            exit 2
        fi
        test_method=measureConversion
        report=benchmark
        output_name="$mode-$backend-$scenario"
        ;;
    *)
        echo "Unknown measurement mode: $mode" >&2
        exit 2
        ;;
esac

mkdir -p "$output_directory"
log="$output_directory/$output_name.log"
destination="$output_directory/$output_name.json"
temp_report=$(mktemp "$output_directory/.conversion-report.XXXXXX")
trap 'rm -f -- "$temp_report"' EXIT

"${adb_command[@]}" shell am force-stop "$package_name"
"${adb_command[@]}" shell am instrument -w -r \
    -e class "$test_class#$test_method" \
    -e conversionParity "$mode" -e conversionCase "${scenario:-unused}" \
    -e conversionBackend "$backend" \
    -e conversionWarmup "$warmup" -e conversionIterations "$iterations" \
    "$package_name.test/androidx.test.runner.AndroidJUnitRunner" | tee "$log"
if ! grep -q 'OK (1 test)' "$log" || ! grep -q "CONVERSION_PARITY_REPORT conversion-$report.json" "$log"; then
    echo "Conversion instrumentation did not produce a successful result. See $log." >&2
    exit 1
fi
"${adb_command[@]}" exec-out run-as "$package_name" cat "files/conversion-$report.json" > "$temp_report"
python3 -m json.tool "$temp_report" >/dev/null
mv -- "$temp_report" "$destination"
