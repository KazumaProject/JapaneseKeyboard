# Continuous Kana reference observations

> 2026-09-09 追記：候補上部の余白を修正し、高さ設定の追加検証と iOS 中断検証を実施。[最新記録](spacing-followup.md)。以下は前回時点の記録を含みます。
Reference is the actual UIKit Japanese Kana keyboard on iOS 26.4.1 (23E254a),
not a reconstruction. Discovery run `continuous-discovery-v2` succeeded; v1 had
an invalid diagnostic callback signature and is not accepted evidence.

The four v2 input traces each have exactly one DOWN and one UP, with respectively
129, 129, 115 and 128 MOVE events. Each returns to its original coordinate
(220, 746) points. Light/Dark and 60 ms/1200 ms initial holds are separate cases.
The movie and original event JSON are retained under `build/keyboard-skins/`.
Portrait and landscape reference matrices completed 80 gestures each: one warm-up
and three measured trials per appearance, key (な/た/は/あ/わ) and hold duration
(60/1200 ms). Every trace contains a continuous contact through the waypoints;
return to center commits the original key. Input completion alone is not a passing
presentation comparison. `continuous-portrait-v2` additionally retained 24,273
lossless CoreSimulator callbacks, including the whole center guide.

## Observed center-key states

| Input phase | Normal flick | Guide already shown after hold |
| --- | --- | --- |
| Initial contact | Original key remains visible and pressed | Same, until guide onset |
| Direction selected | One directional balloon with one selected glyph | One contiguous cross; only selected cell changes color/glyph contrast |
| Direction changes | Old balloon disappears and new direction's balloon appears in its own position | Cross cells remain at their original positions; selection moves between cells |
| Finger crosses center | Normal balloon disappears while center is selected | Center is eligible again; exact recognition boundary is not inferred from screenshots |
| Release | Balloon retires; original key returns to idle | Whole cross retires; surrounding label restoration is distinct from cross lifetime |

For Light, the recorded right-to-left normal transition (movie 10.650–11.253 s)
shows the right balloon stationary through 10.987 s, absent by 11.002 s, and the
left balloon visible by 11.137 s. There is no balloon travelling across the anchor.
These are movie timestamps and do not certify absolute input-to-display latency.
The no-balloon interval includes actual MOVE events passing through the center.
It must not be replaced with a hardcoded blank delay.

The normal balloon does not erase the original anchor glyph. A guide replaces
its underlying key areas with opaque contiguous cells; its center and arms must
not coexist with a separate normal flick balloon. Neighboring key labels dim
independently. Directional balloons and guide cells are different shapes and
must not share a geometry transition that animates one into the other.

## Unresolved measurements

The edge and landscape matrices confirm stationary directional surfaces and a
single fixed guide. Light's solid guide fill is RGB (255,255,255), selection
(0,136,255), measured from lossless callbacks. Native character availability and
recognition differ: iOS わ has no bottom alternative; the existing Android key
has 〜. Preserve the application's input mappings rather than deleting a character
to fit a reference image. Android's angular recognition is also unchanged.

Onset/boundary/reverse reference captures are complete as documented below; the
final Android viewport regression has passed its recorded cases. Cancellation and exact transition timing still need separately
reported evidence; completed input sequences cannot substitute for them. The discovery
movie alone does not establish their behavior or satisfy the acceptance gate.
No claim of iOS parity or merge readiness follows from these observations.

## Additional reference runs

`continuous-extra-v2` completed all 96 continuous gestures (24 declared warm-ups,
72 measured): portrait/landscape, Light/Dark, reverse direction order, center-boundary
oscillation, and movement starting at fixed 450/550 ms offsets. Six separate
per-launch trace files each contain 16 completed continuous contacts. The new
`--capture-name` diagnostic argument prevents a later app launch from overwriting
an earlier trace. v1's movie and successful input assertions remain diagnostic;
its overwritten per-launch trace files are not complete timing evidence.

`continuous-cancel-discovery-v1` is excluded. XCTest rejected a Home-button action
while a continuous pointer record was active (only one gesture can run at a time).
The attempted discovery method was removed; no product code was changed by that
failure. Direct Simulator UI control was also unavailable while the Mac was locked.
Android's explicit ACTION_CANCEL cleanup is covered on the physical device; this
is not an iOS cancellation timing comparison. The current evidence therefore does
not certify every timing clause of the frozen gate.

## Selected-cell transition measurement

The center-key guide was sampled from source-timestamped recordings: portrait
`continuous-portrait-v2` and landscape `continuous-landscape-timing-v1` (5,034 raw
callbacks), compared with the final Pixel 6 viewport APK recordings. Each platform
has Light/Dark × portrait/landscape × three measured trials: 24 measured gestures
in total. All five selected cells were observed in each trial. At the observed
frames, no partial selected-cell fill (10–90% of the stable blue signal) persisted
between states. The cell patch excludes the text; neighboring label fade and input
recognition latency are separate measurements.

This is evidence of an immediate fill replacement at the available capture cadence,
not proof of zero physical latency. Android video coalesces unchanged frames.
The result must not be advertised as complete input-to-display timing acceptance.
`measure-guide-transitions.py` retains the source intervals and per-trial results.


## Actual guide-onset boundary

The initial 450/550 ms paths occurred after guide onset on both platforms and must
not be counted as before/after-onset coverage. Source frames put the first solid
blue cell approximately 390–417 ms after the first recorded contact frame on iOS,
and 307–321 ms on Android. The app's existing configured default is 300 ms; it was
not changed to match iOS. `continuous-native-onset-v1` therefore repeats one warm-up
and three measured trials with movement starting at 350/450 ms on iOS and
250/350 ms on Android, in both appearances and orientations (32 gestures/platform).
The source offsets, not the legacy 60/1200 labels used to choose the pair, define
these paths. All input assertions passed. See the per-run manifests for coverage.

A second cancellation discovery, `continuous-external-cancel-v1`, launched Settings
through simctl while the injected contact was active. It is also rejected: the
reference app restarted, and crash attachments show UIKit's Japanese flick selector
constraint stack. The runner's equality check could therefore appear successful
only because the editor reset. The retained trace restarted at serial 1 and does
not prove a cancelled contact. The discovery method was removed; neither this
runner result nor a reset editor is counted as passing cancellation evidence.
