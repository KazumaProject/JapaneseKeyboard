# PR #984: physical keyboard conversion review

## Scope and implementation

The changed conversion behaviour is limited to physical-keyboard input. Ordinary
software-keyboard candidate generation and commit routing are unchanged.

- Store the queried reading and the disjoint cursor tail as an immutable session.
  Build each preview from that source and a UTF-16 reading range, not the candidate's
  display length or a suffix-overlap heuristic.
- Enter and candidate clicks commit only the selected reading and recompose the rest.
  Typing commits the selected candidate plus the remaining reading before composing
  the new character. Commit and initial new-character composition share a batch.
- `commitText` finishes the composing region itself; do not clear the region and
  redundantly call `finishComposingText` before restoring the tail. Sora's composing
  batch can otherwise flush a stale selection update between those operations.
- If typing arrives before the next candidate list, commit the remaining raw reading
  and process the key. Candidate previews wait for list submission rather than a
  fixed delay, and check the session generation. Candidate request tokens are checked
  again after dispatch to the main thread.
- Restore the complete reading on cancellation, reset physical bunsetsu conversion
  flags, and ignore standalone modifier keys as text input.

## Baseline reproduction

Baseline: `5d1f95d24` (original PR merged with `dev` at
`d84ff2c0b91339c9f96081706c9c946fee7b73a2`). Using the same hardware-key tests:

| Operation | Baseline result |
| --- | --- |
| Type あしたは, move left, Space | 明日; trailing は is lost |
| Select a partial candidate from あしたはれるといいですねはれた, then type a | あしたあ; remaining reading is lost |

Both are assertion failures in the added Android tests, not assumptions inferred
only from source inspection.

## Reproduction commands

Use a dedicated Android 35+ emulator with hardware keyboard enabled and set
`ANDROID_HOME` and `ANDROID_SERIAL`. Do not share it with another UI test.

```sh
./gradlew :core:testDebugUnitTest :app:testLiteStandardDebugUnitTest :gojuon_keyboard:testDebugUnitTest :app:assembleLiteStandardDebug :app:assembleLiteStandardDebugAndroidTest :app:assembleFullStandardDebug --max-workers=2
adb install -r app/build/outputs/apk/liteStandard/debug/app-lite-standard-debug.apk
adb install -r app/build/outputs/apk/androidTest/liteStandard/debug/app-lite-standard-debug-androidTest.apk
adb shell am instrument -w -r -e class com.kazumaproject.markdownhelperkeyboard.PhysicalCandidateCompositionInstrumentedTest com.kazumaproject.markdownhelperkeyboard.lite.test/androidx.test.runner.AndroidJUnitRunner
```

The independent Sora 0.24.6 host and its commands are in
[`tools/physical_candidate_sora_probe`](../../tools/physical_candidate_sora_probe/README.md).
It enables composing IME input when a hardware keyboard is present; Sora's default
hardware-keyboard setting instead advertises `TYPE_NULL` and exercises direct input.
The probe is not part of the product dependency graph.

## Validation record

Verified on 2026-09-08 against the final implementation, using a dedicated Android
35 arm64 emulator and its built-in `qwerty2` hardware-keyboard route:

| Check | Result |
| --- | --- |
| core unit tests | 40 passed |
| app Lite unit tests | 1,448 passed; 4 existing skips; 0 failures |
| gojuon unit tests | 1 passed |
| Standard EditText hardware-key instrumentation | 8 passed |
| Sora 0.24.6 hardware-key instrumentation | 3 passed |
| Lite debug APK and AndroidTest APK | Built successfully |
| Full debug APK, including pinned native submodules | Built successfully |
| Whitespace and conflict review | Clean |

The EditText cases include actual Shift down/up events, rapid Enter followed by a
character, kana and romaji input, cancellation, candidate clicks and editor restart.
Sora covers partial Enter/remaining-reading commit, implicit commit/new input and
cursor-tail cancellation. Its standalone test dependency is not shipped in the IME.

An intermediate run of the existing glide timing benchmark exceeded its threshold
under concurrent builds. The unchanged benchmark and the complete final suite passed
with `--max-workers=2`. Early UI runs affected by a shared emulator or incomplete IME
binding are not treated as product verification; the final runs use a dedicated
emulator and explicit Japanese-input readiness checks.

Re-review found no remaining merge-blocking defects in the verified paths. The PR
includes `dev` at the SHA above and preserves its editor-mutation revision tracking.
The actual PR merge is intentionally left to the maintainer.

Physical Japanese 109A hardware and Android 16/HyperOS were not available. Emulator
results must not be represented as a physical-device test. Full builds use the
repository's pinned native submodules.
