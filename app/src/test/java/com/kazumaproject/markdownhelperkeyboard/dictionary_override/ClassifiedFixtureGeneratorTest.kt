package com.kazumaproject.markdownhelperkeyboard.dictionary_override

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.engine.TripleDictionaryData
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File
import java.io.ObjectInputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Rebuilds the expected-record fixture when a filtered release changes its published triples. */
class ClassifiedFixtureGeneratorTest {
    @Test fun generateFromStagedAssets() {
        val directory = System.getProperty("classifiedFixtureRoot")
        assumeNotNull(directory)
        val root = File(requireNotNull(directory))
        val manifest = JsonParser.parseString(root.resolve("tools/classified-dictionaries/distribution-manifest.json").readText())
            .asJsonObject
        val overrides = JsonParser.parseString(root.resolve("app/src/main/assets/categories/surface-overrides.json").readText())
            .asJsonObject.getAsJsonObject("categories")
        val (leftIds, rightIds) = ObjectInputStream(root.resolve("app/src/main/assets/pos_table.dat").inputStream()).use {
            (it.readObject() as ShortArray) to (it.readObject() as ShortArray)
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val modulus = BigInteger.ONE.shiftLeft(256)
        val expected = JsonObject()
        for (entry in manifest.getAsJsonArray("categories")) {
            val category = entry.asJsonObject
            val id = category.get("id").asString
            val assets = category.getAsJsonArray("assetPaths").map { it.asString }
            fun <T> read(role: String, decode: (ObjectInputStream) -> T): T {
                val file = root.resolve(assets.single { it.endsWith("/$role.dat.zip") })
                return ZipFile(file).use { zip ->
                    ObjectInputStream(zip.getInputStream(zip.entries().nextElement())).use(decode)
                }
            }
            val yomi = read("yomi", LOUDSWithTermId()::readExternalNotCompress)
            val tango = read("tango", LOUDS()::readExternalNotCompress)
            val token = TokenArray().apply {
                read("token", ::readExternal)
                setPOSTable(leftIds, rightIds)
            }
            val literal = overrides.getAsJsonObject(id)?.getAsJsonObject("surfaces")
                ?.entrySet()?.associate { it.key to it.value.asString }.orEmpty()
            val lbs = SuccinctBitVector(yomi.LBS)
            val leaf = SuccinctBitVector(yomi.isLeaf)
            val tokens = SuccinctBitVector(token.bitvector)
            val dictionary = TripleDictionaryData(tango, yomi, token, lbs, leaf, tokens, SuccinctBitVector(tango.LBS), literal)
            var count = 0
            var sum = BigInteger.ZERO
            var sampleReading = ""
            var sampleSurface = ""
            yomi.predictiveSearch("", lbs).forEach { reading ->
                val term = yomi.getTermId(yomi.getNodeIndex(reading, lbs), leaf)
                check(term >= 0) { "$id has an invalid term for $reading" }
                token.forEachDictionaryByYomiTermId(term, tokens) { pos, cost, node ->
                    val surface = dictionary.surfaceFor(node, reading)
                    val row = "$reading\t${leftIds[pos.toInt()]}\t${rightIds[pos.toInt()]}\t$cost\t$surface"
                    sum += BigInteger(1, digest.digest(row.toByteArray(Charsets.UTF_8)))
                    count++
                    if (sampleReading.isEmpty() || (reading.length in 4..8 && sampleReading.length !in 4..8)) {
                        sampleReading = reading
                        sampleSurface = surface
                    }
                }
            }
            check(count == category.get("recordCount").asInt) { "$id decoded $count records" }
            expected.add(id, JsonObject().apply {
                addProperty("count", count)
                addProperty("digestSum", sum.mod(modulus).toString(16).padStart(64, '0'))
                add("sample", JsonObject().apply {
                    addProperty("reading", sampleReading)
                    addProperty("surface", sampleSurface)
                })
            })
        }
        val fixtureDir = root.resolve("app/src/androidTest/assets").apply { mkdirs() }
        val json = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
        fixtureDir.resolve("classified-expected-records.json").writeText(json.toJson(expected) + "\n")
    }
}
