# Pinned classified dictionary distribution

`prepare.py` imports `v1.7.262-pre.1`, verifies its archive and all 117 asset
hashes, and derives category provenance from hash-verified frozen source data.
The five original input acquisition revisions and per-record copyright attribution
are unconfirmed. Notice snapshot revisions are recorded separately.

```sh
python3 tools/classified-dictionaries/prepare.py
python3 tools/classified-dictionaries/prepare.py --check
```

`ClassifiedDictionaryInstrumentedTest` compares all 767,274 decoded records
against count and SHA-256 multiset fingerprints generated from the frozen
reading/surface/left POS/right POS/cost records. It also exercises category
selection across the legacy and incremental backends and all three Japanese
candidate modes, including prefix prediction. The SHA-256 fingerprints are summed
modulo 2^256 so record ordering does not affect the comparison; duplicates still
contribute independently.

## Literal surface repair

The upstream token builder uses a Katakana sentinel for any pure Katakana
surface, even when that surface differs from a conversion of the reading.
The frozen record `あとこしゃらか / アトコシヤラカ` consequently decodes as
`アトコシャラカ` from the published binary. The importer derives one sidecar
entry from the frozen source, keyed by category, sentinel, reading, POS IDs and
cost. Runtime graph conversion and prediction share that literal decoder.
The repair applies to bundled dictionaries and byte-identical imported triples;
it is excluded for other external dictionaries. The published 117 assets remain
byte-identical to the release.

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

The UI tests verify all 28 settings change handlers and their refreshed state,
render every category notice offline and type through the actual
Romaji keyboard, check the `[賞]` label, and commit a candidate into an editor.
The prior default IME is restored afterwards. Probe preferences are separate
from the user's keyboard preferences.
