# Physical candidate conversion: Sora Editor probe

Standalone Android test host for Sora Editor **0.24.6**. This project is deliberately
not included in the product build: Sora's Kotlin runtime does not alter the IME's
dependencies. Use an Android 35+ emulator with hardware keyboard enabled, or a device
with an alphabetic physical keyboard. Tests exercise Sumire Lite's real IME connection.

The host enables IME composition with `setDisableSoftKbdIfHardKbdAvailable(false)`.
Sora otherwise advertises `TYPE_NULL` with a hardware keyboard, which tests a different
(direct input) path. `disallowSuggestions` remains false.

From the repository root, with `ANDROID_HOME` and `ANDROID_SERIAL` set:

```sh
./gradlew :app:assembleLiteStandardDebug
./gradlew -p tools/physical_candidate_sora_probe assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/liteStandard/debug/app-lite-standard-debug.apk
adb install -r tools/physical_candidate_sora_probe/build/outputs/apk/debug/PR984SoraProbe-debug.apk
adb install -r tools/physical_candidate_sora_probe/build/outputs/apk/androidTest/debug/PR984SoraProbe-debug-androidTest.apk
adb shell am instrument -w -r dev.pr984.soraprobe.test/androidx.test.runner.AndroidJUnitRunner
```

Use Japanese romaji input, bunsetsu separation enabled, bunsetsu cursor movement disabled,
and live conversion disabled. The tests select the Lite IME and restore the previously
selected IME. Run on a dedicated test device: do not run another UI test concurrently.

Checks cover a cursor-separated trailing reading, partial Enter/continuation, and
implicit commit followed by another typed character. The main app's
`PhysicalCandidateCompositionInstrumentedTest` additionally tests kana input, clicks,
bunsetsu width changes and editor restart against standard `EditText`.
