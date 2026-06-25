# Japanese Bunsetsu Conversion Phase 0-2

This note records the first implementation slice for Japanese-only bunsetsu conversion quality work. It intentionally does not change English conversion, English prediction, English rewriters, IME lifecycle, UI, clipboard, or settings.

## Scope

- Phase 0: Japanese bunsetsu fixtures, Kotlin trace, upstream-vs-Kotlin TSV join, root-cause summary, and benchmark output.
- Phase 1: Split N-best path state from `Node.g` and `Node.next` in `FindPath`.
- Phase 2: Add Japanese candidate identity and dedupe tracing so value-only duplicate loss can be measured.

No `.so`, JNI, NDK, C++, or prebuilt binary was added. The Mozc source in `/Users/kazuma/Downloads/mozc-master.zip` was read only as the upstream reference.

## Upstream References Read

- `src/converter/immutable_converter.cc`
  - Reference flow: lattice construction, segmentation, Viterbi, resegment, N-best, boundary check, candidate filtering, and rewriting.
- `src/converter/nbest_generator.cc`
  - Reference concepts: `QueueElement`, `BoundaryCheck`, `STRICT`, `ONLY_MID`, `ONLY_EDGE`, and weak-connected penalty handling.
- `src/converter/segmenter.cc`
  - Reference APIs: `IsBoundary(rid, lid)`, `GetPrefixPenalty(lid)`, and `GetSuffixPenalty(rid)`.

Phase 3 and later should connect a Kotlin Japanese segmenter/boundary checker using these references. This slice keeps the current `isIndependentWord` boundary heuristic as the default production behavior.

## Reports

Generated under:

```text
app/build/reports/japanese-bunsetsu/
```

Files:

- `inputs.tsv`: Japanese-only fixture inputs.
- `kotlin_trace.tsv`: Kotlin engine trace for the fixture set.
- `upstream_trace.tsv`: Mozc upstream trace if supplied by `-PupstreamJapaneseBunsetsuTrace=...`; otherwise a placeholder marked `UPSTREAM_TRACE_MISSING`.
- `japanese_bunsetsu_quality_report.tsv`: Joined report with required columns for top1, top10, boundary, first divergence, first missing candidate, and reason.
- `root_cause_summary.tsv`: Count by first divergence stage.
- `benchmark.tsv`: Japanese bunsetsu path-state speed and memory measurements.

Current root cause without an upstream trace:

```text
first_divergence_stage	count
UPSTREAM_TRACE_MISSING	15
```

This is deliberate: the report does not invent Mozc expected values or use fallback matching. Supplying a real upstream TSV enables top1/top10/boundary match measurement without changing production code.

## Gradle Tasks

```bash
./gradlew :app:generateJapaneseBunsetsuGoldenFixtures
./gradlew :app:traceUpstreamJapaneseBunsetsu
./gradlew :app:traceKotlinJapaneseBunsetsu
./gradlew :app:joinJapaneseBunsetsuTrace
./gradlew :app:analyzeJapaneseBunsetsuRootCause
./gradlew :app:reportJapaneseBunsetsuQuality
```

To join a real Mozc trace:

```bash
./gradlew :app:reportJapaneseBunsetsuQuality -PupstreamJapaneseBunsetsuTrace=/absolute/path/to/upstream_trace.tsv
```

Benchmark command:

```bash
./gradlew :app:testLiteStandardDebugUnitTest --tests com.kazumaproject.markdownhelperkeyboard.converter.bunsetsu.JapaneseBunsetsuPathBenchmarkTest
```

## Fixture Cases

The Phase 0 fixture is Japanese-only and contains:

```text
きょうはいいてんきです
わたしはがっこうにいきます
あしたとうきょうにいきます
かみにかく
せいどがたかい
えいせいはぶんりした
くんくんにおっているので
かにかまれる
きほんかのように
かんじかなまじりぶん
きょうのてんきははれです
とうきょうえきまでいきたい
にほんごのれんぶんせつへんかん
かいしゃにでんわをかける
きのうかったほんをよむ
```

## Phase 1 Result

`FindPath.backwardAStar` and `FindPath.backwardAStarWithBunsetsu` now use immutable per-path `PathElement` state for:

- current node
- next path element
- accumulated cost
- priority cost
- word cost
- structure cost
- split positions

N-best path construction no longer depends on mutating `Node.g` or `Node.next`. `Node.f` and `Node.prev` remain part of the existing forward dynamic-programming heuristic and were not expanded in this phase.

Added tests cover:

- no `Node.g` / `Node.next` path-state mutation during bunsetsu N-best search
- deterministic repeated conversion
- same `Node` shared by multiple paths
- prevention of candidate collapse caused by `next` overwrites

## Phase 2 Result

Japanese bunsetsu candidates now carry optional `JapaneseCandidateIdentity`:

- `value`
- `key`
- `contentValue`
- `contentKey`
- `leftId`
- `rightId`
- `splitPattern`
- `wordCost`
- `structureCost`
- `candidateSource`

The default production dedupe mode remains value-based for compatibility. Identity mode and dedupe traces are available for tests, reports, and future Phase 3+ trials.

Trace distinguishes:

- value duplicate dropped by legacy behavior
- identity duplicate dropped by identity behavior
- candidates that identity mode retains but value-only dedupe would have dropped

Final UI display can still deduplicate by visible string.

## Current Metrics

Because no real upstream Mozc trace has been supplied yet:

- top1 match before/after: not measured
- top10 contains before/after: not measured
- boundary match before/after: not measured
- improved/regressed cases: not classified
- unresolved cases: all 15 fixture cases are unresolved as `UPSTREAM_TRACE_MISSING`

The Kotlin trace and join report are ready for upstream comparison once the upstream TSV is provided.

## Next Phase 3 Work

Phase 3 should add a Kotlin Japanese segmenter API and wire it behind a feature flag:

```kotlin
interface JapaneseMozcSegmenter {
    fun isBoundary(leftRid: Int, rightLid: Int): Boolean
    fun getPrefixPenalty(lid: Int): Int
    fun getSuffixPenalty(rid: Int): Int
}
```

Important checks before connecting it to production:

- verify current dictionary `lid` / `rid` IDs match Mozc segmenter data IDs
- preserve a `LEGACY_INDEPENDENT_WORD` mode for comparison
- trace `rid`, `lid`, boundary result, prefix penalty, suffix penalty, and candidate identity
- compare boundary match before enabling the new mode by default
