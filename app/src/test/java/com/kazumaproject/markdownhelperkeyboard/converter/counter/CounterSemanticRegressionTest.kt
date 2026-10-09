package com.kazumaproject.markdownhelperkeyboard.converter.counter

import com.kazumaproject.counter.CounterDictionary
import com.kazumaproject.graph.Node
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NgramRule
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NgramRuleScorer
import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.NodeFeature
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CounterSemanticRegressionTest {
    private val converter = listOf(File("src/main/assets/counter/counter_rules.dat"),File("app/src/main/assets/counter/counter_rules.dat"))
        .first { it.exists() }.inputStream().use { CounterDictionary.read(it).converter() }

    @Test fun identicalDaySurfacesRetainCalendarAndDurationMeanings() {
        val meanings = converter.analyze("なのか")
        assertEquals(setOf("day_calendar","day_duration"), meanings.map { it.counterId }.toSet())
        assertTrue(meanings.all { it.number == 7L && it.forms.first().value == "7日" })
        assertEquals(listOf("7日","七日","７日"),converter.convert("なのか").candidates.map { it.value })
    }

    @Test fun outOfRangeHoursRemainSyntaxEvidenceWithoutGeneratingTimeCandidates() {
        assertTrue(converter.analyze("にじゅうよじ").isEmpty())
        val syntax = converter.analyze("にじゅうよじ",includeOutOfRange=true).single()
        assertEquals("clock_hour",syntax.counterId)
        assertEquals(24L,syntax.number)
        assertFalse(syntax.inRange)
        assertTrue(syntax.forms.isEmpty())
    }

    @Test fun numericPrefixCannotProduce214ButCaseParticleCanPrecede14() {
        val boundary = CounterBoundaryPolicy(converter)
        val prefix = node("2","に",0).copy(numberValue=2)
        val hour = node("14時","じゅうよじ",1).copy(counter=converter.analyze("じゅうよじ").first())
        assertFalse(boundary.canFollow(prefix,hour))
        assertTrue(boundary.canFollow(prefix.copy(tango="に",numberValue=null),hour))
    }

    @Test fun completeUnitAndClockRangesCannotBeBypassedByFragments() {
        val boundary = CounterBoundaryPolicy(converter)
        val two = node("2つ","ふたつ",0).copy(counter=converter.analyze("ふたつ").first())
        assertFalse(boundary.canFollow(two,node("部","ぶ",3)))
        val hour = node("3時","さんじ",0).copy(counter=converter.analyze("さんじ").first())
        val minute = node("60分","ろくじゅっぷん",3).copy(counter=converter.analyze("ろくじゅっぷん").first())
        assertFalse(boundary.canFollow(hour,minute))
    }

    @Test fun numericSpellingAliasesMatchOneMeaningAndNgramFeatureClass() {
        val day = converter.analyze("にじゅうさんにち").first()
        assertTrue(CounterNodePolicy.represents(day,"廿三日"))
        assertFalse(CounterNodePolicy.represents(day,"213日"))
        val amount = converter.analyze("さんびき").first()
        assertTrue(CounterNodePolicy.represents(amount,"三びき"))
        val scorer = NgramRuleScorer(listOf(
            NgramRule(listOf(NodeFeature(word="三匹"),NodeFeature(word="いる")), -100),
            NgramRule(listOf(NodeFeature(word="猫"),NodeFeature(word="三匹")), -200),
        ))
        val ascii = node("3匹","さんびき",0).copy(counter=amount)
        val kanji = ascii.copy(tango="三匹")
        val following = node("いる","いる",4)
        assertEquals(-100,scorer.score(ascii,following))
        assertEquals(-200,scorer.score(node("猫","ねこ",0),ascii))
        assertEquals(scorer.score(node("猫","ねこ",0),kanji),scorer.score(node("猫","ねこ",0),ascii))
        assertEquals(scorer.score(kanji,following),scorer.score(ascii,following))
        assertEquals(scorer.wordClass(kanji),scorer.wordClass(ascii))
    }

    @Test fun unreachableQuantityFragmentsDoNotOverflowForwardCosts() {
        for (bunsetsu in listOf(false, true)) {
            val boundary = CounterBoundaryPolicy(converter)
            val quantity = node("13日", "じゅうさんにち", 1).copy(
                counter = converter.analyze("じゅうさんにち").first(), counterBoundary = boundary)
            val fragment = node("後", "よ", 1 + quantity.len)
            val length = fragment.sPos + fragment.len
            val literal = node("通常", "にじゅうさんにちよ", 0).copy(score = 100, adjustedScore = 100)
            val graph = mutableMapOf(
                1 to mutableListOf(node("2", "に", 0).copy(numberValue = 2, counterBoundary = boundary)),
                (1 + quantity.len) to mutableListOf(quantity),
                length to mutableListOf(fragment, literal),
                (length + 1) to mutableListOf(node("EOS", "", length + 1)),
            )
            val finder = com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.FindPath()
            val candidates = if (bunsetsu) finder.backwardAStarWithBunsetsu(
                graph, length, ShortArray(4), 2, 8).candidates
            else finder.backwardAStar(graph, length, ShortArray(4), 2, 8)
            assertEquals(Int.MAX_VALUE, quantity.f)
            assertEquals(Int.MAX_VALUE, fragment.f)
            assertEquals(listOf("通常"), candidates.map { it.string })
            assertEquals(100, candidates.single().score)
        }
    }

    private fun node(surface:String,reading:String,start:Int) = Node(0,0,0,0,tango=surface,len=reading.length.toShort(),yomiUsed=reading,sPos=start)
}
