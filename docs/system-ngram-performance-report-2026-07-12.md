# Scoreless system n-gram version 3 implementation and performance

Date: 2026-07-12

## Implementation

JapaneseKeyboard now reads the converter's version 3 binary directly from
`app/src/main/assets/ngram/system_ngram.dat`. The reader owns the one `ByteArray`
allocated by the Android asset loader. It does not use mmap, `MappedByteBuffer`,
`FileChannel.map`, a temporary file, or a second dictionary-sized byte array.

The format reader validates the magic, version, complete header/offset layout,
CRC, signatures, context-to-coarse-POS table, bucket ranges, hash entry record
IDs and ordering, front-coded block ranges, and every encoded record boundary.
Lookup encodes a canonical query into a thread-local scratch buffer, searches
the dynamically sized bucketed 64-bit hash index, decodes a hit's 16-record
front-coding block into a second scratch buffer, and finally performs a complete
byte-for-byte canonical-key comparison. A 48-bit hash hit alone never matches.

Exact words, coarse POS, and `*` are supported for rules of two through five
nodes. `*` consumes exactly one node. Matching is performed against the `Node`
path used to construct a candidate. The scoreless result is only a Boolean
ordering key; no `Candidate.score` is added, subtracted, or overwritten. The
existing user n-gram scorer remains a separate provider and code path.

When no system dictionary is present, path search stops at the requested count.
When present, it searches up to a 32–64 candidate safety bound only while no
match has been found, and stops as soon as both the requested count and a match
exist. A small inline match set removed the allocation caused by the original
`LinkedHashSet` backing table.

## 100,000-rule dictionary

The Kotlin converter built the three release rules plus 99,997 deterministic
temporary exact rules. The temporary source was deleted and both repositories
were restored to the three-rule release asset after measurement.

| Item | Value |
|---|---:|
| Rules | 100,000 |
| File size | 6,175,023 bytes (5.89 MiB) |
| Hash index | 1,262,148 bytes |
| Front-coded records | 4,885,111 bytes |
| Final release rules | 3 |
| Final release asset | 3,890 bytes |

## Pixel 6 measurement

Physical Pixel 6, Android 16 / API 36, `liteStandardDebug`, input
`ふくをきる`, requested candidates 4. Both configurations were warmed at
least 30 times. Timing used 100 enabled and 100 disabled samples in alternating
order. This is a debug build, so absolute timing is not a release benchmark.

| System n-gram | Java heap retained | Native heap delta | PSS delta | Allocated/conversion | GC | p50 | p95 | p99 | First candidate | Score | Match |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|---|
| Disabled | 0 B | +1,840 B | +17,408 B | 155,648 B | 0 | 2.680 ms | 3.146 ms | 3.206 ms | 服を切る | 10,440 | no |
| Enabled | +6,176,768 B | -96 B | +6,182,912 B | 155,648 B | 0 | 2.755 ms | 3.266 ms | 3.352 ms | 服を着る | 10,691 | yes |

Enabled minus disabled timing was +0.075 ms at p50, +0.120 ms at p95,
and +0.146 ms at p99. PSS enabled-minus-disabled was 6,165,504 bytes.
The native-heap difference is allocator/snapshot noise; dictionary storage is
on the Java heap. Retained Java heap was only 1,745 bytes above the binary size.
No additional per-conversion allocation or GC was measured after replacing the
match-tracking `LinkedHashSet` with inline storage.

Without the dictionary, `服を着る` retained score 10,691 but ranked behind
`服を切る` (10,440). With the dictionary it moved to first without changing
that score. Disabling the dictionary restored the original first candidate.

## Verification and CI

The reader test covers exact mismatch, POS mismatch, one-node wildcard arity,
2–5 grams, bad magic/version/CRC/offsets, truncation, a hash index redirected to
another record, and reference identity of the owned dictionary byte array. An
instrumented test exercises real candidate generation and enable/disable
restoration. Gradle verifies the version 3 asset and CRC, creates a ZIP with the
required asset path, and verifies its contents. Pull-request and release Actions
run those checks and save the build report. A separate manual workflow checks
out the Kotlin converter, generates the 100,000-rule asset, and runs the Android
performance test on an emulator.

The remaining concern is that timing and PSS have normal device snapshot noise;
the measured deltas are comfortably within the requested p95/p99 targets, but
release-profile measurements should be repeated when compiler or graph-search
code changes materially.
