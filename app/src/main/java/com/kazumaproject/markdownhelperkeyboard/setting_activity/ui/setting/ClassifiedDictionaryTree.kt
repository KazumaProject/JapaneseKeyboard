package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.content.Context
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.ClassifiedDictionaries
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryCategory
import org.xmlpull.v1.XmlPullParser
import java.text.Normalizer
import java.util.Locale

internal enum class DictionarySelectionFilter { ALL, ENABLED, DISABLED }

internal data class ClassifiedDictionaryNode(
    val key: String,
    val title: String,
    val category: DictionaryCategory? = null,
    val children: MutableList<ClassifiedDictionaryNode> = mutableListOf(),
) {
    fun leaves(): List<ClassifiedDictionaryNode> = if (category != null) listOf(this) else children.flatMap { it.leaves() }
    fun find(key: String): ClassifiedDictionaryNode? = if (this.key == key) this else children.firstNotNullOfOrNull { it.find(key) }
    fun pathTo(key: String): List<ClassifiedDictionaryNode> = if (this.key == key) listOf(this) else {
        children.firstNotNullOfOrNull { child -> child.pathTo(key).takeIf { it.isNotEmpty() }?.let { listOf(this) + it } }.orEmpty()
    }

    fun visible(query: String, filter: DictionarySelectionFilter, enabled: (DictionaryCategory) -> Boolean): List<ClassifiedDictionaryNode> {
        if (query.isBlank() && filter == DictionarySelectionFilter.ALL) return children
        val tokens = normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        return leaves().filter { node ->
            val searchable = normalize(pathTo(node.key).joinToString(" ") { it.title })
            tokens.all(searchable::contains) && when (filter) {
                DictionarySelectionFilter.ALL -> true
                DictionarySelectionFilter.ENABLED -> enabled(requireNotNull(node.category))
                DictionarySelectionFilter.DISABLED -> !enabled(requireNotNull(node.category))
            }
        }
    }

    companion object {
        private const val NS = "http://schemas.android.com/apk/res/android"
        private fun normalize(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)

        /** Read the existing localized hierarchy rather than maintaining a second category list. */
        fun load(context: Context): ClassifiedDictionaryNode {
            val root = ClassifiedDictionaryNode("classified_dictionaries", context.getString(R.string.classified_dictionary_settings_title))
            val specs = ClassifiedDictionaries.all.associateBy { it.preferenceKey }
            val stack = ArrayDeque<Pair<Int, ClassifiedDictionaryNode>>()
            context.resources.getXml(R.xml.pref_dictionary).use { parser ->
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG) {
                        val key = parser.getAttributeValue(NS, "key").orEmpty()
                        val title = parser.getAttributeResourceValue(NS, "title", 0).takeIf { it != 0 }?.let(context::getString).orEmpty()
                        when {
                            key == root.key -> stack.addLast(parser.depth to root)
                            stack.isNotEmpty() && key.startsWith("classified_group_") -> {
                                val group = ClassifiedDictionaryNode(key, title)
                                stack.last().second.children.add(group)
                                stack.addLast(parser.depth to group)
                            }
                            stack.isNotEmpty() && key in specs -> stack.last().second.children.add(
                                ClassifiedDictionaryNode(key, title, specs.getValue(key).category),
                            )
                        }
                    } else if (parser.eventType == XmlPullParser.END_TAG && stack.lastOrNull()?.first == parser.depth) {
                        stack.removeLast()
                    }
                    parser.next()
                }
            }
            val other = root.children.filter { it.category != null }
            root.children.removeAll(other.toSet())
            if (other.isNotEmpty()) root.children.add(ClassifiedDictionaryNode(
                "classified_group_other", context.getString(R.string.classified_dictionary_other), children = other.toMutableList(),
            ))
            check(root.leaves().map { it.key }.toSet() == specs.keys) { "Classified dictionary hierarchy is incomplete" }
            return root
        }
    }
}
