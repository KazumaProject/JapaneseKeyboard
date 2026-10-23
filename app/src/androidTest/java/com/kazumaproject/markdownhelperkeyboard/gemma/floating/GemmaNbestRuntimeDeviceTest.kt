package com.kazumaproject.markdownhelperkeyboard.gemma.floating

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.gemma.runtime.GemmaRuntimeClient
import com.kazumaproject.markdownhelperkeyboard.ime_service.di.KanaKanjiEngineEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in comparison using the installed app's dictionaries and two real LiteRT-LM models. */
@RunWith(AndroidJUnit4::class)
class GemmaNbestRuntimeDeviceTest {
    private data class Case(val reading: String, val context: String, val correct: String)

    private val cases = listOf(
        Case("はし", "川の両岸をつなぐ建造物の名前は", "橋"),
        Case("はし", "ご飯をつまんで食べる二本の道具は", "箸"),
        Case("あつい", "真夏は気温が高くてとても", "暑い"),
        Case("あつい", "沸騰したお湯に触ると", "熱い"),
        Case("あつい", "千ページもある本なのでとても", "厚い"),
        Case("かみ", "文字を書くための白い", "紙"),
        Case("かみ", "美容院で切ってもらう頭の", "髪"),
        Case("かみ", "教会で祈りを捧げる相手は", "神"),
        Case("きる", "新しい服を体に", "着る"),
        Case("きる", "包丁で野菜を小さく", "切る"),
        Case("あめ", "空から水滴が降ってきたので傘で防ぐのは", "雨"),
        Case("あめ", "砂糖でできた甘いお菓子をなめる。そのお菓子は", "飴"),
        Case("はな", "くしゃみが出て、鼻水も出る。顔の器官は", "鼻"),
        Case("はな", "庭できれいに咲いている", "花"),
        Case("いし", "病院で患者を診察する職業は", "医師"),
        Case("いし", "道端に落ちている硬い小さな", "石"),
        Case("かえる", "池にいる緑色の両生類は", "蛙"),
        Case("かえる", "仕事が終わったので自分の家に", "帰る"),
        Case("かえる", "予定を別の日程に変更する。予定を", "変える"),
        Case("こうえん", "子供と遊ぶため、ブランコがある場所に来た。この場所は", "公園"),
        Case("こうえん", "大学で研究者が聴衆に研究内容を話す催しは", "講演"),
        Case("こうえん", "劇団が劇場で芝居を上演する催しは", "公演"),
        Case("せいかく", "人柄や気質を表す言葉は", "性格"),
        Case("せいかく", "誤りがなく、数値がぴったり合っている。計算が", "正確"),
        Case("しゅうかん", "毎朝歯を磨くことは長年の", "習慣"),
        Case("しゅうかん", "七日間を単位として、二", "週間"),
        Case("かいとう", "質問に対して返事をすることは", "回答"),
        Case("かいとう", "数学の問題を解いて答えを出すことは", "解答"),
        Case("かいとう", "冷凍した肉を常温に戻す処理は", "解凍"),
        Case("はしをわたる", "川の向こう岸まで歩いて行くために", "橋を渡る"),
        Case("はしをつかう", "和食を食べるときはフォークではなく", "箸を使う"),
        Case("かみをきる", "美容院で長く伸びた", "髪を切る"),
        Case("かみをきる", "工作でハサミを使い白い", "紙を切る"),
        Case("あめがふる", "天気予報では明日", "雨が降る"),
        Case("こうえんにいく", "子供とブランコで遊ぶために", "公園に行く"),
        Case("こうえんにいく", "有名な研究者のお話を聞くために", "講演に行く"),
        Case("せいかくなこたえ", "計算問題なので誤りのない", "正確な答え"),
        Case("いしにはなす", "診察室で自分の症状を", "医師に話す"),
        Case("いえにかえる", "今日の仕事が終わったので", "家に帰る"),
    )

    @Test(timeout = 3_600_000)
    fun compareLabelProbabilitiesAndWholeTextLikelihoods() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Explicitly opt into real-model comparison",
            InstrumentationRegistry.getArguments().getString("gemmaNbestBenchmark") == "true")
        val context = instrumentation.targetContext
        val entry = EntryPointAccessors.fromApplication(context, KanaKanjiEngineEntryPoint::class.java)
        val requests = withContext(Dispatchers.Default) {
            val engine = entry.kanaKanjiEngine()
            cases.mapNotNull { case ->
                val candidates = engine.getCandidatesOriginal(
                    input = case.reading, n = 4, userDictionaryRepository = entry.userDictionaryRepository(),
                    learnRepository = null, isOmissionSearchEnable = false,
                    typoCorrectionOffsetScore = 0, omissionSearchOffsetScore = 0,
                )
                GemmaNbestRequest.from(case.reading, case.context, candidates)?.let { case to it }
            }
        }
        assertTrue("No whole-input N-best candidate sets were available", requests.isNotEmpty())
        val directory = File(requireNotNull(context.getExternalFilesDir(null)), "gemma")
        val models = listOf("gemma3-270m-it-q8.litertlm", "gemma-4-E2B-it.litertlm")
        val report = File(context.getExternalFilesDir(null), "gemma-nbest-comparison.jsonl")
        report.writeText("")
        for (name in models) {
            val model = File(directory, name)
            assertTrue("Install the real model before comparison: $name", model.isFile)
            val client = GemmaRuntimeClient(context)
            try {
                val start = SystemClock.elapsedRealtime()
                assertEquals("CPU", client.initialize(model.absolutePath, "cpu", 0))
                report.appendText(JSONObject().put("model", name).put("initializationMs",
                    SystemClock.elapsedRealtime() - start).put("backend", "CPU").toString() + "\n")
                val warmup = requests.first().second
                for (method in GemmaNbestMethod.entries) {
                    assertNotNull(client.scorePreview(GemmaNbestScoring.prompt(warmup, method),
                        GemmaNbestScoring.targets(warmup, method)))
                }
                for ((caseIndex, pair) in requests.withIndex()) {
                    val (case, request) = pair
                    // Alternate order to avoid consistently giving one method the warmer runtime.
                    val methods = if (caseIndex % 2 == 0) GemmaNbestMethod.entries else GemmaNbestMethod.entries.reversed()
                    for (method in methods) {
                        val started = SystemClock.elapsedRealtime()
                        val scores = requireNotNull(client.scorePreview(GemmaNbestScoring.prompt(request, method),
                            GemmaNbestScoring.targets(request, method)))
                        val elapsed = SystemClock.elapsedRealtime() - started
                        assertEquals(request.candidates.size, scores.logLikelihoods.size)
                        assertTrue("Expected real finite log probabilities", scores.logLikelihoods.all { it.isFinite() && it <= 0f })
                        assertTrue("Expected positive token counts", scores.tokenCounts.all { it > 0 })
                        val best = requireNotNull(GemmaNbestScoring.bestIndex(request, method, scores))
                        val selected = request.candidates[best]
                        val covered = case.correct in request.candidates
                        report.appendText(JSONObject()
                            .put("model", name).put("method", method.name).put("reading", case.reading)
                            .put("context", case.context).put("candidates", JSONArray(request.candidates))
                            .put("expected", case.correct).put("covered", covered).put("selected", selected)
                            .put("normalFirst", request.candidates.first()).put("correct", selected == case.correct)
                            .put("falseSuggestion", covered && request.candidates.first() == case.correct && best != 0)
                            .put("inferenceMs", elapsed).put("logLikelihoods", JSONArray(scores.logLikelihoods.toList()))
                            .put("tokenCounts", JSONArray(scores.tokenCounts.toList()))
                            .put("relativeProbabilities", JSONArray(requireNotNull(GemmaNbestScoring.relativeProbabilities(request, method, scores))))
                            .toString() + "\n")
                    }
                }
                // Request cancellation and verify that the shared engine remains usable afterwards.
                val probe = requests.first().second
                val cancelled = async {
                    runCatching { client.scorePreview(GemmaNbestScoring.prompt(probe, GemmaNbestMethod.FULL_TEXT),
                        GemmaNbestScoring.targets(probe, GemmaNbestMethod.FULL_TEXT)) }
                }
                delay(1)
                client.cancelPreview()
                cancelled.await()
                val stable = requireNotNull(client.scorePreview(GemmaNbestScoring.prompt(probe, GemmaNbestMethod.LABELS),
                    GemmaNbestScoring.targets(probe, GemmaNbestMethod.LABELS)))
                assertNotNull(GemmaNbestScoring.bestIndex(probe, GemmaNbestMethod.LABELS, stable))
            } finally {
                client.close()
            }
        }
    }
}
