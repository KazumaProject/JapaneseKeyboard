# Numeric reading policy and independent fixtures (PR #1006)

## Sources and scope

- Osaka Prefecture, [数字](https://www.pref.osaka.lg.jp/documents/26351/2suuji.pdf), supporter page 17 (PDF page 3): basic numbers, 14/19 terminal variants and 70. Product policy deliberately excludes standalone し / よ / く. They are not inferred from a dictionary decoder.
- Tokyo University of Foreign Studies, [数詞 (2)](https://www.coelang.tufs.ac.jp/mt/ja/gmod/contents/explanation/016.html): whole place combinations and the fixed hundred/thousand sound changes. This is not permission to replace voiced/unvoiced sounds globally.
- Japan Foundation, [Irodori 入門 word list](https://www.irodori.jpf.go.jp/assets/data/wordlist_X.pdf), L9-1 (PDF page 28), L13-3 (PDF page 46): hours; the minute endings including よんふん / よんぷん and はちふん / はっぷん.
- Japan Foundation, [Irodori 初級1](https://nd.jpf.go.jp/wp-content/uploads/2024/09/Y_All.pdf), L2-10: 人数, including ひとり / ふたり / よにん / きゅうにん and ななにん / しちにん.

- Agency for Cultural Affairs, [じかん と ようび](https://www.bunka.go.jp/seisaku/kokugo_nihongo/kyoiku/seikatsusha/h25_nihongo_program_a/pdf/a_24.pdf), printed page 15 (PDF page 16): explicitly lists ななふん / しちふん; this alternate is limited to the minute counter, not an inferred sound replacement.

- Sakai City, [やさしい日本語の防災資料](https://www.city.sakai.lg.jp/sakai/torikumi/bousaitorikumi/75416520221013112907343.files/zentaiban.pdf): the indexed tsunami-arrival example explicitly pairs 100分 with ひゃっぷん. The original download currently returns 404; the indexed passage, not a decoder result, supplies this fixed example. [Inagi City’s 2025 library list](https://www.city.inagi.tokyo.jp/_res/projects/default_project/_page_/001/008/624/r7magazinelist.pdf), entry 239, independently pairs 100分de名著 with ひゃっぷんでめいちょ.

Uncontracted いちちょう / はちちょう / じゅうちょう (including さんじゅうちょう) are not approved: an isolated valid coefficient is not evidence for an alternate reading before 兆. The fixture rejects these four former inventory approvals. The TUFS 兆 example and the explicitly requested contractions govern this position.

The user-specified contract fixes the supported grammar for descending 1–9999 coefficients of 万 / 億 / 兆, the four position-specific 兆 contractions, terminal じゅうし / じゅうく, and clock hours 0–29. Do not extend these rules to unsupported readings or other counters. The fixed minute ending changes apply only at the end of a complete number and before ぷん. 円 does not borrow the hour/people/minute contractions.

## Reviewed dictionary fixtures

`legacy-155-review.tsv` records every standalone reading accepted at d0550153f and its manual approve/reject decision. The old values are an **inventory**, not evidence of grammatical correctness. `approved-cardinals.tsv` fixes the 90 approved reading/value pairs; 65 inventory readings are rejected under the above rules. The replacement parser must match this complete map when all 747,244 dictionary readings are enumerated, without a decoder prefilter.

`legacy-53-counter-review.tsv` records the old 53 counter readings. 45 are approved; いちにん / ににん / くにん / さんふん / よんにん / よんじ / きゅうじ / ななじ are rejected under the dedicated counter rules. Adding the two independently specified irregular people readings produces 47 corpus matches. The complete expected map is fixed in `JapaneseNumberCandidateCorpusAuditTest`.

The numerical expectations for each approved example were reviewed as place-value arithmetic. The tests never regenerate these TSVs from production output. A corpus mismatch requires a reading/source review, not snapshot regeneration.

## Independent tests

- `NumberGrammarTest.spoken` constructs 0–9999 from arithmetic place indices and fixed pronunciations; it never calls a production parser to obtain expected readings or values.
- All 0–9999 values also pass through the 16 final candidate paths; independent arithmetic supplies all three required spellings and their commit text.
- The 5,000 malformed cases prepend one of five invalid hundred/thousand forms to independently constructed suffixes. Both parser entry points reject them. The final-path test also checks these cases in four modes, both backends and both segmentation settings.
- Complete-dictionary audits compare accepted pairs against fixed data, and final-path tests check candidate strings and commit strings, not just ON/OFF differences. Normal expected candidates are required, not merely absence of wrong ones.
- All six ordering permutations are checked in reused sessions, with direct half/full-width digit input and leading zeroes. Preference persistence, invalid values and both settings surfaces are covered.
- History tests inject malformed persisted pairs before lattice search; generated and dictionary candidates retain separate provenance after de-duplication. No history rows are deleted.

Device UI tests must be run on the updated APK. Earlier PR device results do **not** validate this revision. Do not report this work as accepted while updated-device selection, full commit and live conversion remain unexecuted.
