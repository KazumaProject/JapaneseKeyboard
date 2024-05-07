package com.kazumaproject.single_kanji

import com.kazumaproject.dictionary.models.Dictionary

class SingleKanjiBuilder {

    fun build(fileName: String): List<Dictionary> {
        val singleKanjiInMap = getSingleKanjiList(fileName)
        val tempList = mutableListOf<Dictionary>()
        for (entry in singleKanjiInMap){
            for (singleKanji in entry.value){
                tempList.add(
                    Dictionary(
                        yomi = entry.key,
                        leftId = 1916,
                        rightId = 1916,
                        cost = 8000,
                        tango = singleKanji.toString()
                    )
                )
            }
        }
        return tempList.toList()
    }

    private fun getSingleKanjiList(fileName: String): Map<String, List<Char>> {
        val lines = this::class.java.getResourceAsStream(fileName)
            ?.bufferedReader()
            ?.readLines()

        lines?.let {  l ->
            val tempList = l.map { str1 ->
                str1.split(",".toRegex()).flatMap { str2 ->
                    str2.split("\\t".toRegex())
                }
            }
            return tempList.associate {
                it[0] to it[1].toList()
            }
        }

        return emptyMap()
    }

}