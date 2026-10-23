package com.kazumaproject.markdownhelperkeyboard.converter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.kazumaproject.markdownhelperkeyboard.converter.session.*
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.ExactInputCandidatePromotionPolicy
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.*
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import com.kazumaproject.markdownhelperkeyboard.ime_service.candidate.CandidateDisplayMerger
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Opt-in snapshot probe. Run the identical test APK against each isolated app APK. */
@RunWith(AndroidJUnit4::class)
class DictionaryCandidateOrderInstrumentedTest {
    @Test fun snapshotCandidateOrder() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("dictionaryCandidateOrder") == "true")
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName.contains(".classifiedprobe"))
        val corpus = instrumentation.context.assets.open("candidate-order-corpus.json").use { it.readBytes() }
        val inputs = JsonParser.parseString(corpus.toString(Charsets.UTF_8)).asJsonArray.map { it.asString }
        check(inputs.size == inputs.distinct().size)
        val excludedIds = args.getString("dictionaryExcludeIds").orEmpty().split(',').filter { it.isNotBlank() }.toSet()
        val selected = ClassifiedDictionaries.all.filter { it.id !in excludedIds }.map { it.category }.toSet()
        val typedInputs = setOf("しょう", "ほのか", "かな", "なら", "かみ", "とうきょう", "じんこうちのう",
            "なつめそうせき", "おおたにしょうへい", "あせんやく", "くりき", "しゅうじ", "まつだ", "あいがな", "あいあいえす")
        val store = DictionaryOverrideStore(context, DictionaryOverrideValidator())
        val previous = ClassifiedDictionaries.all.associate { it.category to store.isOptionalBundledEnabled(it.category) }
        ClassifiedDictionaries.all.forEach { store.setOptionalBundledEnabled(it.category, false) }
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        val engine = entry.kanaKanjiEngine()
        val repository = entry.userDictionaryRepository()
        val gson = Gson()
        var snapshots = 0
        try {
            File(context.filesDir, "dictionary-candidate-order.jsonl").bufferedWriter().use { out ->
                out.appendLine(gson.toJson(mapOf("version" to args.getString("dictionaryVersion"),
                    "inputs" to inputs.size, "categories" to ClassifiedDictionaries.all.size, "n" to 4, "beam" to 20,
                    "excludedIds" to excludedIds.sorted(), "enabledCategories" to selected.map { it.name }.sorted(),
                    "corpusSha256" to MessageDigest.getInstance("SHA-256").digest(corpus).joinToString("") { "%02x".format(it) })))
                for (enabled in listOf(false, true)) {
                    ClassifiedDictionaries.all.forEach { store.setOptionalBundledEnabled(it.category, enabled && it.category in selected) }
                    engine.applyDictionaryOverrideState(context)
                    for (backend in ConversionBackend.entries) for (mode in listOf(CandidateQueryMode.CONVERSION,
                        CandidateQueryMode.NO_TAB_DEFAULT, CandidateQueryMode.PREDICTION)) for (bunsetsu in listOf(false, true)) {
                        for (input in inputs) {
                            fun request(text: String) = KanaKanjiQueryRequest(
                                input = text, mode = mode, bunsetsuSeparation = bunsetsu, n = 4,
                                enabledDictionaryCategories = if (enabled) selected else emptySet(),
                                userDictionaryRepository = repository, learnRepository = null,
                                omissionSearchEnabled = false, typoCorrectionJapaneseFlickEnabled = false,
                                typoCorrectionQwertyEnglishEnabled = false, typoCorrectionOffsetScore = 3000,
                                omissionSearchOffsetScore = 1900, beamWidth = 20,
                            )
                            fun save(scenario: String, result: KanaKanjiQueryResult) {
                                check(result.candidates.isNotEmpty()) { "Empty candidates: $input" }
                                // The standard IME merges duplicate surfaces before input promotion.
                                // Personal learning, templates, NG words and manual order overrides
                                // are absent in this isolated comparison.
                                val display = ExactInputCandidatePromotionPolicy.promote(
                                    input, CandidateDisplayMerger.merge(result.candidates))
                                out.appendLine(gson.toJson(mapOf("enabled" to enabled, "backend" to backend.name,
                                    "mode" to mode.name, "bunsetsu" to bunsetsu, "input" to input, "scenario" to scenario,
                                    "displaySurfaces" to display.map { it.string },
                                    "candidates" to result.candidates.map { candidate -> mapOf(
                                        "surface" to candidate.string, "commit" to candidate.commitText,
                                        "score" to candidate.score, "type" to candidate.type.toInt(),
                                        "length" to candidate.length.toInt(), "reading" to candidate.yomi,
                                        "left" to candidate.leftId, "right" to candidate.rightId,
                                        "standalone" to candidate.standaloneUnigramMatched,
                                        "categories" to candidate.dictionaryCategories.map { it.name }.sorted(),
                                    ) })))
                                snapshots++
                            }
                            save("fresh", KanaKanjiConversionSession(engine, backend).query(request(input)))
                            if (input in typedInputs) {
                                val session = KanaKanjiConversionSession(engine, backend)
                                for (length in 1 until input.length) session.query(request(input.take(length)))
                                save("typed", session.query(request(input)))
                                session.query(request(input.dropLast(1)))
                                save("delete_restore", session.query(request(input)))
                            }
                        }
                        out.flush()
                        println("CANDIDATE_ORDER $enabled/$backend/$mode/$bunsetsu snapshots=$snapshots")
                    }
                }
            }
            println("CANDIDATE_ORDER_COMPLETE inputs=${inputs.size} snapshots=$snapshots")
        } finally {
            previous.forEach { (category, enabled) -> store.setOptionalBundledEnabled(category, enabled) }
            engine.applyDictionaryOverrideState(context)
        }
    }
}
