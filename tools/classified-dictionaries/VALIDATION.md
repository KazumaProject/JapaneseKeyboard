# Verification results

Base: `dev` at `100391ee31058923f7024547a23e1de243036b53`.
Distribution: `v1.7.262-pre.3`, source revision
`d65dc98b023fa7ab8ca994daef2ec1640d2a1bf5`.

- Release archive SHA-256:
  `2f0ae2c9027d00a267475c7140e9457d537ae58cffd61407e1205f8a540759bd`.
- `prepare.py --check`: all 123 published assets, full distribution license,
  notice snapshots, expected-record fixtures and the generated surface-repair
  sidecar checksum passed. Specialist adopted records were matched to the
  hash-verified original source rows before assigning source notices.
- 161 relevant Lite JVM/Robolectric tests passed, with no failures or skips.
- Lite Standard debug APK/test APK and Full Standard debug APK builds passed.
- Pixel 6, Android API 37, isolated package
  `com.kazumaproject.markdownhelperkeyboard.classifiedprobe.lite`:
  ten instrumented tests passed together on the final APK (69.208 seconds).

The device tests decode all 861,053 category records and compare counts and
SHA-256 multiset fingerprints for reading, surface, left/right POS IDs and cost
with frozen input. One source-derived literal surface repair is included; raw
release binary assets are unchanged. Exactly one Place record needs this repair:
`あとこしゃらか / アトコシヤラカ` otherwise decodes as `アトコシャラカ`.

All 5,138 English-reading pairs and their costs match the pinned release's
catalog, including sync, fetch, async and webhook. Raw binary case selection
retains `car` and omits `Car`; the app's existing deferred case variants remain
available. Exact registered-reading/nonterminal-prefix lookup, English ON/OFF,
and multi-bunsetsu behavior passed the existing English-reading device tests.

Category tests exercise all 30 categories with and without bunsetsu separation,
legacy/incremental backends, default/conversion/prediction modes, category changes
on the same session and prefix prediction. The settings test calls every actual
Dictionary-screen switch change handler, verifies both states, turns all OFF,
then turns them ON from External dictionary settings and checks the returned
Dictionary screen. Unit tests cover default ON, reset, saved OFF without migration,
shared switch semantics, partial/invalid/incompatible external triples and search
routes that navigate to the store-backed handler instead of writing default prefs.

All 30 category license dialogs render offline with a nonzero viewport and
content height. Medical/Kampo notices were also inspected in device screenshots.
Actual Romaji touchscreen input and candidate clicks commit `[賞] IF賞`,
`[漢方・生薬] 阿仙薬` and `[医療] 朝痛み` through the editor's InputConnection.
The updated N-gram/unigram assets load and match on ART (1,715 + 472 rules).

## Startup and memory observations

On this Pixel 6, a controlled category reload (core dictionaries already loaded;
all categories disabled, GC, enable all, reload, GC) took approximately 2.0 seconds
and added approximately 26 MB of Java heap. This is a reload measurement, not a
cold-start benchmark; allocation/GC and device caches affect the result.

After force-stopping only the probe package, reselecting its IME and opening the
test editor, IMEService logged 897.558 ms for initial core loading and 793.173 ms
for initial optional loading with all categories ON. The editor activity launch
reported WARM / WaitTime 742 ms because selecting the IME already starts the
process. These load durations are individual observations, not an end-to-end
cold-start SLA. After initialization, dumpsys reported 205,535 KB total PSS and
62,948 KB Java-heap PSS for the probe process; these totals include the app/editor,
core dictionaries and keyboard UI, not only classified dictionaries.

The user's regular app installation was not replaced. The prior default IME was
restored to `com.kazumaproject.markdownhelperkeyboard/.ime_service.IMEService`
after the instrumented and process-restart checks.

## Scope limits

Full was built but not exercised on the device. Other Android versions, every
possible sentence/ranking, and upstream classification/automatic/Gemini reading
accuracy were not verified. Original acquisition revisions of the old five inputs
and per-record copyright attribution remain unconfirmed, as do the standalone
alphabetic English-word asset's source/data terms. These are labelled unconfirmed
in the notices; known specialist source versions and hashes are recorded separately.
