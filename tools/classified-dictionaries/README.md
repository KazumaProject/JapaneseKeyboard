# Pinned classified dictionary distribution

`prepare.py` imports `v1.7.277`, verifies its archive and all 463 asset
hashes, and derives category provenance from hash-verified frozen source data.
The 143 categories include 29 person, 59 facility, 6 event, 15 term and 8 media audience categories,
Kampo (434 records) and Medical (92,683 records). New subcategories default OFF;
retired person/event/term/facility switches are not inherited. Other saved category
choices are preserved. Of the 67 distribution additions since v1.7.268, 65 populated categories are selectable and default OFF.
The two empty media categories are excluded from the app registry and settings;
the pinned distribution and provenance remain intact.
The dictionary settings group people, facilities, events, terms, works and media audiences in collapsible headings. Groups start closed, retain their last state and expand temporarily for search navigation. External settings retain the existing layout.
Name-origin categories are grouped with their corresponding unconfirmed names;
philosophers and thinkers use only source-verified upstream entries. Person IDs are normalized to their full asset paths.
Specialist provenance is matched to adopted source rows; license texts include
CC BY-SA 2.1 Japan, CC BY 4.0 and updated English-reading/CMUdict notices.
The v1.7.277 MHLW additions are tracked with source and review hashes in
`category-provenance.json`; their category page cites PDL1.0 and the processing
performed. Mozc-derived records keep the source-specific notices supplied by
the converter.
The five original input acquisition revisions and per-record copyright attribution
are unconfirmed. Notice snapshot revisions are recorded separately.

```sh
python3 tools/classified-dictionaries/prepare.py
python3 tools/classified-dictionaries/prepare.py --check
```

`ClassifiedDictionaryInstrumentedTest` compares all 866,217 decoded records
against count and SHA-256 multiset fingerprints generated from the frozen
reading/surface/left POS/right POS/cost records. A second fixture verifies all
5,138 English-reading pairs and costs against the pinned catalog. It also exercises category
selection across the legacy and incremental backends and all three Japanese
candidate modes, including prefix prediction. The SHA-256 fingerprints are summed
modulo 2^256 so record ordering does not affect the comparison; duplicates still
contribute independently.

## Literal surface repair

The importer derives three literal surface repairs from the hash-verified source
records for the comedian category: `くりき / くりきひなた`, `しゅうじ / シュージ`
and `まつだ / ﾏﾂﾀﾞ`. The packed kana sentinels cannot preserve these spellings.
A small sidecar restores them during conversion and browsing only when all three
selected dictionary files match the pinned SHA-256 hashes. Byte-identical external
triples are supported; other external triples retain their own decoding.
All 463 distributed assets remain byte-identical to the release. Tests cover the
literal spellings and reject repairs for different external archive bytes.

The standalone unigram asset now contains seven whole-input preferences, including
`なにか` and `なら`. Instrumented tests cover all seven across modes and backends, and retain
compound and corrected-input exclusions.

## Final token group boundaries

The v1.7.271 temple and information/communications token bitsets end exactly on
64-bit boundaries. The reader uses the token array length as the implicit final
separator when no padding zero exists. This preserves the final records
`やくようしょくぶつしどうせんたー / 薬用植物指導センター` and
`じーぴーてぃーしっくすてんいちそる / GPT-6.1 Sol` without changing release assets.
Boundary tests cover all Japanese token accessors; record fingerprints and both
conversion backends verify the decoded results.

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

The UI tests verify all 141 populated dictionary settings change handlers and their refreshed state
in both the new settings screen and legacy pager,
render every category notice offline and type through the actual
Romaji keyboard, check the `[賞]`, `[漢方・生薬]` and `[医療]` labels, and commit candidates into an editor.
They also verify state shared with External dictionary settings.
The prior default IME is restored afterwards. Probe preferences are separate
from the user's keyboard preferences.

## Release comparison probe

`DictionaryUpdatePerformanceInstrumentedTest` is opt-in and requires the isolated
probe package. Install the **identical test APK** against each application APK.
Its fixed 24-input corpus includes short readings, dictionary names, the three
literal spelling repairs, and 16/32/64/96/128-character sentences. A corpus SHA-256
and complete environment/settings record are emitted with each run.

The probe measures both backends, three query modes, bunsetsu OFF/ON and optional
categories OFF/all ON, with five warmups and three rounds of ten samples per input.
Fresh-session engine latency excludes UI rendering and file reporting. Separate
240-query workloads sample Java heap, native allocated heap and process PSS every
20 ms; retained memory is recorded after GC. The allocation counter includes the
instrumentation/sampler process, and sampled peaks are lower bounds on true peaks.

Use fresh processes and reset **only the probe package**, run versions in A-B-B-A
order, and retain the raw TSVs outside the repository. This compares emulated debug
builds, not release-device end-to-end keyboard latency.

```sh
adb shell pm clear com.kazumaproject.markdownhelperkeyboard.classifiedprobe.lite
adb shell am instrument -w -e dictionaryUpdatePerf true -e dictionaryVersion v1.7.277 \
  -e class com.kazumaproject.markdownhelperkeyboard.converter.DictionaryUpdatePerformanceInstrumentedTest \
  com.kazumaproject.markdownhelperkeyboard.classifiedprobe.lite.test/androidx.test.runner.AndroidJUnitRunner
```

Read `files/dictionary-update-performance.tsv` from the probe app after each run.
The older `DictionaryReleasePerformanceInstrumentedTest` remains a separate,
release-specific broad corpus probe and must not be used for cross-version
comparisons unless its corpus is frozen and its size assertions are updated.

The UI test also logs the time from the final injected key-up to observing a
category label, including its fixed 40 ms delay and up to 50 ms polling interval.
These are coarse UI observations, separate from engine latency. It selects and
commits the candidate, then deletes a character using the actual keyboard key.
Keep APKs, timing reports, logs and screenshots outside the repository; publish
measured results and limitations in the PR.

## Candidate order comparison

`DictionaryCandidateOrderInstrumentedTest` is a separate opt-in snapshot probe
(`dictionaryCandidateOrder=true`). Its frozen 185-input corpus combines ordinary
readings, homophones, long sentences, literal spelling repairs and representative
readings from the dev, v1.7.266 and v1.7.268 catalogs. Install the identical test APK
against the isolated application APK for each version, resetting only the probe
package before each run.

For n=4 and beam=20, it saves all candidate records and the standard IME's duplicate
merge/input-promotion result across optional dictionaries OFF/all ON, both backends,
three query modes and bunsetsu OFF/ON. Fifteen readings also exercise incremental
typing and delete/retype, for 5,160 snapshots per version. Learning, user dictionary,
templates, NG words, manual order overrides and additional Romaji candidates are
absent. Read `files/dictionary-candidate-order.jsonl` via `run-as` and keep results
outside the repository. Compare display sequences separately from candidate
identity: a newly added dictionary candidate may have the same spelling as an
existing kana fallback but a different type, reading or POS.

To verify an upgrade that preserves the v1.7.266 enabled switches, pass
`dictionaryExcludeIds` with the seven new category IDs separated by commas:
`person/thought,person/surname_origin_japanese,person/surname_origin_foreign,person/given_name_origin_japanese,person/given_name_origin_foreign,person/personal_name_origin_japanese,person/personal_name_origin_foreign`.
The output records the excluded IDs and selected category identities. This is a
separate comparison from enabling every v1.7.268 category: names moved into a new,
default-OFF origin category will no longer be supplied by the old unconfirmed
category until their new category is enabled.
