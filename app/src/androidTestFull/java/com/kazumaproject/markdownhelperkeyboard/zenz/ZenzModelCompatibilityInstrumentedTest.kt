package com.kazumaproject.markdownhelperkeyboard.zenz

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kazumaproject.markdownhelperkeyboard.variant.AppVariantConfig
import com.kazumaproject.zenz.ZenzEngine
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Full-only: run the same inputs against separately installed baseline/proposed APKs. */
@RunWith(AndroidJUnit4::class)
class ZenzModelCompatibilityInstrumentedTest {
    @Test(timeout = 1_800_000)
    fun publishedModelsLoadGenerateScoreAndReload() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(AppVariantConfig.hasZenz && args.containsKey("zenzModelMatrixDir"))
        val directory = File(args.getString("zenzModelMatrixDir")!!)
        val manifest = JSONArray(File(directory, "models.json").readText())
        val report = JSONArray()
        val output = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "zenz-model-matrix-${args.getString("zenzMatrixLabel", "current")}.json")
        try {
            for (index in 0 until manifest.length()) {
                val model = manifest.getJSONObject(index)
                val file = File(directory, model.getString("filename"))
                val digest = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        digest.update(buffer, 0, count)
                    }
                }
                assertEquals(model.getString("sha256"), digest.digest().joinToString("") { "%02x".format(it) })
                assertTrue("Cannot load ${model.getString("repo")}", ZenzEngine.initModel(file.absolutePath))
                ZenzEngine.setRuntimeConfig(512, 2)
                val cases = JSONArray()
                val row = JSONObject().put("model", model).put("cases", cases)
                report.put(row)
                output.writeText(report.toString(2))
                for ((reading, candidates) in readings) {
                    val generated = ZenzEngine.generateWithContextAndConditionsV32(
                        "", "", "", "", "", "", reading, 64)
                    assertTrue("Empty result: ${file.name}/$reading", generated.isNotBlank())
                    assertFalse(generated.any { it in '\uEE00'..'\uEE0F' || it == '\uFFFD' })
                    val scores = ZenzEngine.scoreCandidatesV32("", "", "", "", "", "", reading, candidates)
                    assertEquals(candidates.size, scores.size)
                    assertTrue(scores.all(Float::isFinite))
                    val repeated = ZenzEngine.scoreCandidatesV32("", "", "", "", "", "", reading, candidates)
                    assertArrayEquals("Non-deterministic score: ${file.name}/$reading", scores, repeated, 1e-4f)
                    val evaluation = ZenzEngine.candidateEvaluateV32("", "", "", "", "", "", reading, generated)
                    assertNotEquals("ERROR", evaluation)
                    cases.put(JSONObject().put("reading", reading).put("generated", generated)
                        .put("candidates", JSONArray(candidates.toList()))
                        .put("scores", JSONArray(scores.map(Float::toDouble)))
                        .put("evaluation", evaluation))
                    output.writeText(report.toString(2))
                }
                val first = cases.getJSONObject(0).getString("generated")
                ZenzEngine.closeModel()
                assertTrue(ZenzEngine.initModel(file.absolutePath))
                ZenzEngine.setRuntimeConfig(512, 2)
                assertEquals("Reload changed output: ${file.name}", first,
                    ZenzEngine.generateWithContextAndConditionsV32("", "", "", "", "", "", readings.first().first, 64))
            }
            val invalid = File(directory, "invalid.gguf")
            invalid.writeText("This is not a GGUF model")
            assertFalse("Non-GGUF input was accepted", ZenzEngine.initModel(invalid.absolutePath))
            invalid.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46, 3, 0, 0, 0))
            assertFalse("Truncated GGUF input was accepted", ZenzEngine.initModel(invalid.absolutePath))
            val valid = File(directory, manifest.getJSONObject(0).getString("filename"))
            assertTrue("Cannot recover after an invalid model", ZenzEngine.initModel(valid.absolutePath))
            ZenzEngine.setRuntimeConfig(512, 2)
            assertTrue(ZenzEngine.generateWithContextAndConditionsV32("", "", "", "", "", "", "コンニチハ", 64).isNotBlank())
            invalid.delete()
        } finally {
            ZenzEngine.closeModel()
        }
    }

    private val readings = listOf(
        "コンニチハ" to arrayOf("こんにちは", "今日は", "こんにちは。"),
        "マツ" to arrayOf("松", "待つ", "末", "まつ"),
        "まつ" to arrayOf("松", "待つ", "末", "まつ"),
        "アリガトウ" to arrayOf("ありがとう", "有難う"),
        "セイドガタカイ" to arrayOf("精度が高い", "制度が高い", "精度が高い。"),
        "カゼガフク" to arrayOf("風が吹く", "風邪が吹く"),
        "キョウハイイテンキデス" to arrayOf("今日はいい天気です", "今日は良い天気です"),
        "ニホンゴヲニュウリョクスル" to arrayOf("日本語を入力する", "日本語を入力する。"),
        "トウキョウニイク" to arrayOf("東京に行く", "東京にいく"),
        "ハシヲワタル" to arrayOf("橋を渡る", "箸を渡る", "端を渡る"),
        "ハナガサク" to arrayOf("花が咲く", "鼻が咲く"),
        "アシタノヨテイ" to arrayOf("明日の予定", "あしたの予定"),
        "キシャノキシャ" to arrayOf("貴社の記者", "記者の汽車"),
        "コーヒーヲノム" to arrayOf("コーヒーを飲む", "コーヒーをのむ"),
        "ワタシハガクセイデス" to arrayOf("私は学生です", "わたしは学生です"),
        "パソコンヲツカウ" to arrayOf("パソコンを使う", "パソコンをつかう"),
        "カンジヘンカン" to arrayOf("漢字変換", "感じ変換"),
        "ABC123" to arrayOf("ABC123", "ＡＢＣ１２３", "abc123"),
        "２０２６ネン" to arrayOf("２０２６年", "2026年"),
        "オツカレサマデス" to arrayOf("お疲れ様です", "おつかれさまです"),
    )
}
