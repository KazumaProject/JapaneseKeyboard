package com.kazumaproject.markdownhelperkeyboard.converter.mozc.dictionary

class MozcDictionaryGroup(
    private val systemDictionary: MozcDictionary,
    private val systemUserDictionary: MozcDictionary?,
    private val wikiDictionary: MozcDictionary?,
    private val webDictionary: MozcDictionary?,
    private val personDictionary: MozcDictionary?,
    private val placesDictionary: MozcDictionary?,
    private val neologdDictionary: MozcDictionary?,
) {
    fun lookupPrefix(
        key: String,
        options: MozcDictionaryLookupOptions,
        callback: (MozcDictionaryToken) -> Unit,
    ) {
        systemDictionary.lookupPrefix(key, callback)
        systemUserDictionary?.lookupPrefix(key, callback)
        if (options.enableWiki) wikiDictionary?.lookupPrefix(key, callback)
        if (options.enableWeb) webDictionary?.lookupPrefix(key, callback)
        if (options.enablePersonName) personDictionary?.lookupPrefix(key, callback)
        if (options.enablePlaces) placesDictionary?.lookupPrefix(key, callback)
        if (options.enableNeologd) neologdDictionary?.lookupPrefix(key, callback)
    }
}
