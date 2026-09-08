# Recorded Enter observations

- `gboard.jsonl.gz`: all 6,209 Gboard reference cases, captured with the on-screen Enter.
- `keyboard-normal.jsonl.gz`, `keyboard-floating.jsonl.gz`: all 6,209 cases per surface.
- `comparison.tsv`: one row per declared case; all statuses and normalized transport comparisons.
- `validation-status.json`: final counts, including unexecuted/blocked categories, and supplementary checks.
- `*.metadata.json`, `apk-sha256.json`: device, selected IME, capture conditions and tested APK identity.
- `dev-*.jsonl`, `dev-regressions.json`: four selected regressions from the pinned dev APK.
- `*-composition-*.jsonl`: composition/segment traces compared to dev, not Gboard.
- `layout-*.jsonl`, `layout-checks.json`: eight actions on five layouts and two surfaces; supplementary to the full matrix.
- `*.screenshots/INDEX.png`: original pre-tap screenshots; INDEX is the generated case index. The JSON screenshot field retains the original on-device file name.
- `manual-ui.json`: filtering, loading, actual software Enter and result-dialog check.
- `explicit-type-null-direct.jsonl`: the intentionally preserved explicit direct-input override, not a Gboard parity case.
- `guard-*.jsonl`, `harness-guards.json`: intentional interruptions; expected to stop without accepting the case.
- `rejected-observations.jsonl.gz`: diagnostics excluded from reference and success counts, with exclusion reasons. Not an oracle.
- `unit-tests.json`: JVM suite results and Python integrity-test count.
- `sha256.json`: hashes of the saved evidence files.

Key events are normalized to action/keyCode; event flags, modifier state, timestamps and device identity are outside this comparison. Compose records keyboard-action callbacks rather than raw InputConnection transport. See [the result report](../../../docs/enter-editor-parity-results.md) for provenance, scope and limitations.
