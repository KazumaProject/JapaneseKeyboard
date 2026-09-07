# Editor Enter parity laboratory

This change concerns what a software IME sends to an editor when no composition is
pending. Candidate confirmation, segment confirmation, conversion settings,
forced newline gestures and physical keyboard shortcuts retain their existing
routing. Direct-input overrides retain their explicit raw-key behavior. TYPE_NULL's default
still uses direct text input, while its default Enter now honors EditorInfo as
observed in Gboard. Explicit TYPE_NULL direct mode, shortcut overrides, custom
direct layouts and forced QWERTY direct input keep raw Enter behavior.

## Production change

`EditorEnterPolicy` reads the current `EditorInfo` for each editor-facing Enter.
It does not infer Enter behavior from email/password/number classifications or
hint text. `IME_FLAG_NO_ENTER_ACTION` suppresses an action; otherwise the standard
action bits select GO, SEARCH, SEND, NEXT, DONE or PREVIOUS. NONE/UNSPECIFIED and
unknown actions use an Enter key event, except SHORT_MESSAGE text fields, where
the Gboard observations use a direct newline commit. The existing service wrappers perform
dispatch; there is no second fallback action if an editor rejects an action.

The observation archive, rather than the resolver, supplies regression-test
expectations. In the recorded Gboard version, the raw matrix uses the standard
action bits rather than `actionLabel` or `actionId` for the on-key action.
Re-record and inspect differences before changing this contract for another
Gboard version. Android documentation alone is not proof of Gboard parity.

QWERTY labels, kana icons and built-in dynamic Enter states use the same decision.
The pre-existing dynamic states 0–5 retain their action mappings; new states are
appended for PREVIOUS, GO and SEND. The built-in number keyboard also updates its
Enter state. User-defined layouts and their action overrides remain user-owned.

References:
- [EditorInfo and NO_ENTER_ACTION](https://developer.android.com/reference/android/view/inputmethod/EditorInfo#IME_FLAG_NO_ENTER_ACTION)
- [InputType flags](https://developer.android.com/reference/android/text/InputType)

## Declared coverage

`tools/enter_parity/generate_cases.py` generates 6,209 unique cases:

| Group | Cases | Construction |
| --- | ---: | --- |
| Raw EditorInfo Cartesian matrix | 5,632 | 22 types/variations × 8 actions × 4 MULTI_LINE/IME_MULTI_LINE combinations × 2 NO_ENTER_ACTION values × 4 custom-label/ID states |
| Independent probes | 528 | Each of 33 flags/metadata probes × 8 actions × 2 NO_ENTER_ACTION values |
| Captured field regression | 1 | `inputType=0x264001`, `imeOptions=0x40000006` |
| Framework widget integration | 48 | EditText / Compose / WebView × 8 actions × single/multiline |

The 22 types include all 15 public text variations, both numeric variations,
phone, three datetime variations, and TYPE_NULL. Non-text multiline flags are
retained and tagged as deliberately nonstandard inputs. Probes include all other
public text flags, numeric signed/decimal combinations, other public IME flags,
reserved high bits, and search/password/ordinary hints, field names and private
IME options. If a probe changes the Enter decision, promote it into the
Cartesian matrix and re-record the expanded matrix.

This is a complete execution of the declared EditorInfo matrix, not a claim about
all possible apps, arbitrary private IME options, app-specific behavior or future
Gboard releases. Real widgets may normalize an EditorInfo request: their actual
values are recorded and compared separately. WebView's NONE/UNSPECIFIED both map
to HTML `enterkeyhint=enter`, and native framework transformations are expected.

## Device capture

The opt-in `enter-parity-lab` app is never packaged with the keyboard. It contains:
- A raw editor that supplies the exact requested EditorInfo and wraps its real
  InputConnection to record commits, key events, actions and composition calls.
- A normal EditText and WebView with the framework-generated EditorInfo.
- A real Compose BasicTextField. Compose action callbacks and final text are
  recorded, and its actual EditorInfo is read from the IME service dump. The
  runner waits for two stable text EditorInfo samples and rejects the transient
  empty EditorInfo seen during floating-window reconnection. Raw
  InputConnection transport is not intercepted in this adapter.

All fields start with `seed` and cursor 4, without composing spans. Actions are
local sinks: no messages, forms or posts are transmitted. NEXT/PREVIOUS move to
a local second field where the widget adapter supports that action. After raw
editor initialization, later raw cases use `restartInput`; changing widget
families replaces the field. Thus the matrix also exercises editor restarts and
field transitions.

The runner resolves the actual software Enter key through accessibility, waits
for a stable on-screen location and injects touchscreen DOWN/UP. It never uses
`adb input keyevent ENTER` or directly calls the production policy to simulate a
device result. Interrupted pointer streams are cancelled before a new run, and
failed input injection is reported as blocked. Capture stops if the selected IME
changes or the laboratory leaves the foreground, before sending further input. Missing key geometry or missing
actual EditorInfo is also blocked, never successful.

The launcher screen also offers a case-ID filter, a selector, a Load button and
a result dialog for manual comparison. Filtering does not steal focus by
automatically loading a case. Instrumentation uses the same fields with the selector hidden.

Build and install:

```sh
python3 tools/enter_parity/generate_cases.py
./gradlew -PenterParityLab :enter-parity-lab:assembleDebug
adb -s SERIAL install -r enter-parity-lab/build/outputs/apk/debug/enter-parity-lab-debug.apk
./gradlew -I tools/enter_parity/isolated-app.gradle :app:assembleLiteStandardDebug
adb -s SERIAL install -r app/build/outputs/apk/liteStandard/debug/app-lite-standard-debug.apk
adb -s SERIAL shell ime enable com.kazumaproject.markdownhelperkeyboard.lite.enterparity/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService
```

The isolated build uses a separate application ID and data directory. It does not
replace the user's installed keyboard or settings.

```sh
python3 tools/enter_parity/run_device.py --serial SERIAL \
  --ime com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME \
  --layout-note 'record the selected Gboard language and layout' --output gboard.jsonl
python3 tools/enter_parity/run_device.py --serial SERIAL \
  --ime com.kazumaproject.markdownhelperkeyboard.lite.enterparity/com.kazumaproject.markdownhelperkeyboard.ime_service.IMEService \
  --layout-note 'record the selected keyboard layout' --output keyboard.jsonl
python3 tools/enter_parity/compare.py gboard.jsonl keyboard.jsonl comparison.json
python3 tools/enter_parity/export_fixture.py gboard.jsonl app/src/test/resources/enter_parity/gboard.tsv
```

`--start`, `--end`, and `--filter` support diagnostics and interrupted runs.
A partial run is not the full matrix. For a supplementary layout/floating run,
`configure_profile.py --serial SERIAL --keyboard TENKEY --floating` changes only
the isolated APK's layout preferences; select that IME again before capture. The driver saves the previous IME and
animation scales and restores them in `finally` if they still have the values
set by this run. An external IME change stops capture and is left intact;
`--keep-animations` disables
that temporary animation adjustment. Restore these manually if the host process
is killed without running `finally`.

## Acceptance and regression

The comparator iterates the declared cases, not just the rows found in a file.
Its statuses are match, mismatch, not-run, blocked, editor-info-mismatch and
ime-mismatch. Both inputs must come from distinct, consistent IMEs, and raw
EditorInfo values must match the declared case.
Two blocked or absent observations cannot pass. A wrong or extra action fails
even if the resulting text is identical. Transport differences are reported
separately from text/action/focus differences and also fail the comparison.
Transport comparison includes only
editor-facing key/commit/action calls. Key events are normalized to action and
keyCode; event timestamps, device IDs, flags and modifier state are not compared.
Composition bookkeeping calls are
recorded but are not made a Gboard conversion contract. Key labels and bounds
are retained for presentation inspection, not pixel-compared across themes.

The TSV uses `\N` for null cells. The JVM fixture test requires all 6,161 raw
observations with unique IDs. It
checks actual Gboard decisions, dispatch, field transitions and presentation
states. Related tests cover input classification, direct-input dispatch,
composition state, physical shortcuts and special-key overrides. The Python
comparison tests verify that incomplete or invalid captures cannot pass.

Device versions, capture results and post-implementation review findings are
recorded in [the result report](enter-editor-parity-results.md).


## Conversion preservation checks

`CompositionRegressionRunner` types `watashihanihonjin` on the actual ROMAJI keys,
then records composition confirmation and candidate conversion confirmation,
including every incremental Enter press until no composing span remains. No
editor action may be emitted during those confirmations. The next idle Enter
must emit DONE once. Run this on the original dev APK and the patched APK, with
`conversion_bunsetsu_separation_preference` both off and on, and compare text,
composing spans and actions at each stage. Gboard is intentionally not the
reference for these conversion behaviors.

`configure_profile.py` can select `--keyboard ROMAJI --bunsetsu-separation on`
(or off); `--baseline` selects only the separately installed `.enterbaseline`
APK. `--type-null-mode direct_commit` supports an explicit-direct-mode check.
These options only address isolated test package IDs, never the user's normal
keyboard package.

```sh
adb -s SERIAL shell am instrument -w -e output composition.jsonl \
  com.kazumaproject.enterparity/.CompositionRegressionRunner
```


For the original source baseline, create a detached worktree at the starting dev
commit and pass this branch's `isolated-app.gradle` with `-PenterParityBaseline`.
This produces `.enterbaseline`, which can coexist with `.enterparity` and the
user's original installation. Record the baseline commit in the results; an
arbitrary already installed APK is not evidence for the starting dev source.


`--screenshots` saves the pre-tap screen alongside a capture, for presentation
review. The complete matrix uses accessibility-based key discovery. For an
inaccessible legacy key, `--verified-enter X Y` is an explicit operator mode:
it requires exactly one selected case and `--screenshots`, and records the key
as operator screenshot-verified. Inspect the actual screen before supplying
coordinates, then inspect the saved pre-tap screenshot to verify that the touch
hit Enter. This is not an automatic coordinate fallback, and is not used for
matrix acceptance. It was needed only for the original dev number key, whose
icon had an empty accessibility label.

For composition captures, use the same driver with
`--runner CompositionRegressionRunner`, so environment and completion status
are recorded and IME selection is restored as with matrix captures.
