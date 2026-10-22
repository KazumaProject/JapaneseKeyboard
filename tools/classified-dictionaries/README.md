# Pinned classified dictionary distribution

`prepare.py` imports `v1.7.266`, verifies its archive and all 244 asset
hashes, and derives category provenance from hash-verified frozen source data.
The 70 categories include 22 person, 6 event and 15 term categories,
Kampo (432 records) and Medical (92,683 records). New subcategories default OFF;
retired person/event/term switches are not inherited. Other saved category
choices are preserved. Person IDs are normalized to their full asset paths.
Specialist provenance is matched to adopted source rows; license texts include
CC BY-SA 2.1 Japan, CC BY 4.0 and updated English-reading/CMUdict notices.
The five original input acquisition revisions and per-record copyright attribution
are unconfirmed. Notice snapshot revisions are recorded separately.

```sh
python3 tools/classified-dictionaries/prepare.py
python3 tools/classified-dictionaries/prepare.py --check
```

`ClassifiedDictionaryInstrumentedTest` compares all 846,402 decoded records
against count and SHA-256 multiset fingerprints generated from the frozen
reading/surface/left POS/right POS/cost records. A second fixture verifies all
5,138 English-reading pairs and costs against the pinned catalog. It also exercises category
selection across the legacy and incremental backends and all three Japanese
candidate modes, including prefix prediction. The SHA-256 fingerprints are summed
modulo 2^256 so record ordering does not affect the comparison; duplicates still
contribute independently.

## Literal surface repair

The importer derives literal surface overrides from the pinned adopted records.
This release needs no overrides: the former `あとこしゃらか / アトコシヤラカ`
record is explicitly excluded upstream. The sidecar is empty; the runtime guard
for bundled and byte-identical imported triples remains available and tested.
All 244 distributed assets remain byte-identical to the release.

The standalone unigram asset now contains seven whole-input preferences, including
`なにか` and `なら`. Instrumented tests cover all seven across modes and backends, and retain
compound and corrected-input exclusions.

## Isolated device tests

Build and install the separate probe packages; do not install over a user's IME.
The init script also gives the existing test providers unique authorities.

```sh
./gradlew -I tools/classified-dictionaries/device-probe.init.gradle \
  :app:assembleLiteStandardDebug :app:assembleLiteStandardDebugAndroidTest
adb install -r app/build/outputs/apk/liteStandard/debug/app-lite-standard-debug.apk
adb install -r app/build/outputs/apk/androidTest/liteStandard/debug/app-lite-standard-debug-androidTest.apk
adb shell am instrument -w \
  -e class com.kazumaproject.markdownhelperkeyboard.converter.ClassifiedDictionaryInstrumentedTest,com.kazumaproject.markdownhelperkeyboard.converter.ClassifiedDictionaryUiInstrumentedTest \
  com.kazumaproject.markdownhelperkeyboard.classifiedprobe.lite.test/androidx.test.runner.AndroidJUnitRunner
```

The UI tests verify all 70 dictionary settings change handlers and their refreshed state
in both the new settings screen and legacy pager,
render every category notice offline and type through the actual
Romaji keyboard, check the `[賞]`, `[漢方・生薬]` and `[医療]` labels, and commit candidates into an editor.
They also verify state shared with External dictionary settings.
The prior default IME is restored afterwards. Probe preferences are separate
from the user's keyboard preferences.

## Release comparison probe

`DictionaryReleasePerformanceInstrumentedTest` is opt-in and requires the isolated
probe package. Use the same test APK source, corpus and build settings on both
releases. It measures fresh conversion sessions on both backends with all 70
categories OFF and ON, ten warmup passes and three sets of 50 samples per input.
It records first observations, mean/p50/p95/max latency, Java/native heap and
process PSS in `files/dictionary-release-performance.tsv`. Engine resolution and
category reload timings are separate from IME input-to-display latency; the test
process includes instrumentation and does not represent a release-build SLA.

```sh
adb shell am instrument -w -e dictionaryReleasePerf true \
  -e class com.kazumaproject.markdownhelperkeyboard.converter.DictionaryReleasePerformanceInstrumentedTest \
  com.kazumaproject.markdownhelperkeyboard.classifiedprobe.lite.test/androidx.test.runner.AndroidJUnitRunner
```

The UI test also logs the time from the final injected key-up to observing a
category label, including its fixed 40 ms delay and up to 50 ms polling interval.
These are coarse UI observations, separate from engine latency. It selects and
commits the candidate, then deletes a character using the actual keyboard key.
Keep APKs, timing reports, logs and screenshots outside the repository; publish
measured results and limitations in the PR.
