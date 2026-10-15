package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.dictionary_browser

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentDictionaryBrowserBinding
import com.kazumaproject.markdownhelperkeyboard.databinding.ItemDictionaryBrowserWordBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DictionaryBrowserFragment : Fragment() {
    private val model: DictionaryBrowserViewModel by viewModels()
    private var binding: FragmentDictionaryBrowserBinding? = null
    private var renderedGeneration = -1
    private var restoringScroll = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentDictionaryBrowserBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = checkNotNull(binding)
        val catalog = DictionaryBrowserCatalog.all
        ui.dictionarySelector.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, catalog.map { getString(it.nameRes) },
        )
        ui.dictionarySelector.setSelection(catalog.indexOfFirst { it.category == model.state.value.category })
        ui.dictionarySelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                model.select(catalog[position].category)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        ui.dictionarySearch.setText(model.state.value.query)
        ui.dictionarySearch.addTextChangedListener { model.search(it?.toString().orEmpty()) }
        ui.dictionaryRetry.setOnClickListener { model.retry() }
        val words = WordAdapter()
        val layout = LinearLayoutManager(requireContext())
        ui.dictionaryWords.layoutManager = layout
        ui.dictionaryWords.adapter = words
        renderedGeneration = -1
        restoringScroll = true
        ui.dictionaryWords.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!restoringScroll) rememberScroll()
                if (layout.findLastVisibleItemPosition() >= words.itemCount - 20) model.loadMore()
            }
        })
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    if (renderedGeneration != state.generation) restoringScroll = true
                    ui.dictionaryProgress.isVisible = state.loading
                    ui.dictionaryRetry.isVisible = state.failed
                    ui.dictionarySource.text = state.version?.let { version ->
                        getString(
                            R.string.dictionary_browser_source_state,
                            getString(if (version.external) R.string.dictionary_browser_external else R.string.dictionary_browser_bundled),
                            getString(if (version.enabled) R.string.dictionary_browser_enabled else R.string.dictionary_browser_disabled),
                        )
                    }.orEmpty()
                    ui.dictionaryStatus.text = when {
                        state.failed -> getString(R.string.dictionary_browser_error)
                        state.loading && state.rows.isEmpty() -> getString(R.string.dictionary_browser_loading)
                        state.rows.isEmpty() -> getString(R.string.dictionary_browser_empty)
                        state.loading -> getString(R.string.dictionary_browser_loading_more, state.rows.size)
                        else -> getString(R.string.dictionary_browser_count, state.rows.size)
                    }
                    words.submitList(state.rows) {
                        if (binding !== ui || model.state.value.generation != state.generation) return@submitList
                        if (restoringScroll && !state.loading) {
                            layout.scrollToPositionWithOffset(model.scrollPosition, model.scrollOffset)
                            renderedGeneration = state.generation
                            // Wait until the restored position has actually been laid out.
                            ui.dictionaryWords.post {
                                if (binding === ui && model.state.value.generation == state.generation) {
                                    restoringScroll = false
                                    if (layout.findLastVisibleItemPosition() >= words.itemCount - 20) model.loadMore()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        model.refreshIfChanged()
    }

    private fun rememberScroll() {
        val layout = binding?.dictionaryWords?.layoutManager as? LinearLayoutManager ?: return
        val position = layout.findFirstVisibleItemPosition()
        if (position >= 0) model.rememberScroll(position, (layout.findViewByPosition(position)?.top ?: 0) - layout.paddingTop)
    }

    override fun onPause() {
        if (!restoringScroll) rememberScroll()
        super.onPause()
    }

    override fun onDestroyView() {
        if (!restoringScroll) rememberScroll()
        binding?.dictionaryWords?.adapter = null
        binding = null
        super.onDestroyView()
    }

    private class WordAdapter : ListAdapter<DictionaryBrowserRow, WordHolder>(object : DiffUtil.ItemCallback<DictionaryBrowserRow>() {
        override fun areItemsTheSame(old: DictionaryBrowserRow, new: DictionaryBrowserRow) = old == new
        override fun areContentsTheSame(old: DictionaryBrowserRow, new: DictionaryBrowserRow) = old == new
    }) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = WordHolder(
            ItemDictionaryBrowserWordBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )
        override fun onBindViewHolder(holder: WordHolder, position: Int) {
            val row = getItem(position)
            holder.ui.word.text = row.word
            holder.ui.reading.text = row.reading
        }
    }

    private class WordHolder(val ui: ItemDictionaryBrowserWordBinding) : RecyclerView.ViewHolder(ui.root)
}
