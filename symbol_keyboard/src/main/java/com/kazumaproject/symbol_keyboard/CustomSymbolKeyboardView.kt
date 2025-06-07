// CustomSymbolKeyboardView.kt
package com.kazumaproject.symbol_keyboard

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.util.Log
import androidx.appcompat.widget.AppCompatImageButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.core.data.clicked_symbol.SymbolMode
import com.kazumaproject.data.clicked_symbol.ClickedSymbol
import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.data.emoji.EmojiCategory
import com.kazumaproject.listeners.DeleteButtonSymbolViewClickListener
import com.kazumaproject.listeners.DeleteButtonSymbolViewLongClickListener
import com.kazumaproject.listeners.ReturnToTenKeyButtonClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemLongClickListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CustomSymbolKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {
    private val categoryTab: TabLayout
    private val modeTab: TabLayout
    private val recycler: RecyclerView
    private val prevButton: AppCompatImageButton
    private val nextButton: AppCompatImageButton
    private val symbolAdapter = SymbolAdapter()
    private val gridLM = GridLayoutManager(context, 3, RecyclerView.HORIZONTAL, false)

    private var emojiMap: Map<EmojiCategory, List<Emoji>> = emptyMap()
    private var emoticons: List<String> = emptyList()
    private var symbols: List<String> = emptyList()

    // 履歴を可変リストに変更
    private var historyEmojiList: MutableList<String> = mutableListOf()
    private var symbolsHistory: List<ClickedSymbol> = emptyList()

    private var currentMode: SymbolMode = SymbolMode.EMOJI

    private var pagingJob: Job? = null
    private var lifecycleOwner: LifecycleOwner? = null

    private var returnListener: ReturnToTenKeyButtonClickListener? = null
    private var deleteClickListener: DeleteButtonSymbolViewClickListener? = null
    private var deleteLongListener: DeleteButtonSymbolViewLongClickListener? = null
    private var itemClickListener: SymbolRecyclerViewItemClickListener? = null
    private var itemLongClickListener: SymbolRecyclerViewItemLongClickListener? = null

    fun setLifecycleOwner(owner: LifecycleOwner) {
        lifecycleOwner = owner
    }

    fun setOnReturnToTenKeyButtonClickListener(l: ReturnToTenKeyButtonClickListener) {
        returnListener = l
    }

    fun setOnDeleteButtonSymbolViewClickListener(l: DeleteButtonSymbolViewClickListener) {
        deleteClickListener = l
    }

    fun setOnDeleteButtonSymbolViewLongClickListener(l: DeleteButtonSymbolViewLongClickListener) {
        deleteLongListener = l
    }

    fun setOnSymbolRecyclerViewItemClickListener(l: SymbolRecyclerViewItemClickListener) {
        itemClickListener = l
    }

    fun setOnSymbolRecyclerViewItemLongClickListener(l: SymbolRecyclerViewItemLongClickListener) {
        itemLongClickListener = l
    }

    /**
     * 絵文字・顔文字・記号・履歴 をセット
     */
    fun setSymbolLists(
        emojiList: List<Emoji>,
        emoticons: List<String>,
        symbols: List<String>,
        symbolsHistory: List<ClickedSymbol>,
        defaultMode: SymbolMode = SymbolMode.EMOJI
    ) {
        this.symbolsHistory = symbolsHistory

        // 履歴から EMOJI のものだけ取り出し、可変リストにする
        historyEmojiList = symbolsHistory
            .filter { it.mode == SymbolMode.EMOJI }
            .map { it.symbol }
            .toMutableList()

        this.emoticons = emoticons
        this.symbols = symbols
        emojiMap = emojiList.groupBy { it.category }.toSortedMap(categoryOrder)

        currentMode = defaultMode
        buildModeTabs()
        buildCategoryTabs()

        modeTab.getTabAt(defaultMode.ordinal)?.select()
        categoryTab.getTabAt(0)?.select()
        updateSymbolsForCategory(0)
        updatePrevNextButtons()
    }

    init {
        inflate(context, R.layout.symbol_keyboard_main_layout, this)

        categoryTab = findViewById(R.id.category_tab_layout)
        modeTab = findViewById(R.id.mode_tab_layout)
        recycler = findViewById(R.id.symbol_candidate_recycler_view)
        prevButton = findViewById(R.id.button_prev_category)
        nextButton = findViewById(R.id.button_next_category)

        recycler.apply {
            layoutManager = gridLM
            adapter = symbolAdapter
            itemAnimator = null
            isSaveEnabled = false
        }

        symbolAdapter.setOnItemClickListener { str ->
            itemClickListener?.onClick(ClickedSymbol(mode = currentMode, symbol = str))
        }

        symbolAdapter.setOnItemLongClickListener { str, pos ->
            // ← 修正：historyEmojiList を直接変更せず、新しいリストを構築して再代入する
            if (currentMode == SymbolMode.EMOJI
                && historyEmojiList.isNotEmpty()
                && categoryTab.selectedTabPosition == 0
                && pos in 0 until historyEmojiList.size
            ) {
                itemLongClickListener?.onLongClick(
                    ClickedSymbol(mode = currentMode, symbol = str),
                    position = pos
                )

                // 1) 一度コピーしてから削除し、新リストで置き換える
                val newHistory = historyEmojiList.toMutableList().apply {
                    removeAt(pos)
                }
                historyEmojiList = newHistory

                // 2) カテゴリ0を再読み込み（PagingSource生成→先頭スクロール）
                updateSymbolsForCategory(0)

            }
        }

        // LoadStateListener: 読み込み中・完了後のスクロール制御
        symbolAdapter.addLoadStateListener { state ->
            when (state.refresh) {
                is LoadState.Loading -> {}

                is LoadState.NotLoading -> {
                    recycler.scrollToPosition(0)
                }

                else -> {
                    // エラー時などは特に何もしない
                }
            }
        }

        prevButton.setOnClickListener {
            val currentTab = categoryTab.selectedTabPosition
            if (currentTab > 0) {
                categoryTab.getTabAt(currentTab - 1)?.select()
            }
        }
        nextButton.setOnClickListener {
            val currentTab = categoryTab.selectedTabPosition
            val lastIndex = categoryTab.tabCount - 1
            Log.d("nextButton", "$currentTab $lastIndex")
            if (currentTab < lastIndex) {
                categoryTab.getTabAt(currentTab + 1)?.select()
            }
        }

        modeTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentMode = SymbolMode.entries.toTypedArray()[tab?.position ?: 0]
                buildCategoryTabs()
                updatePrevNextButtons()
                categoryTab.getTabAt(0)?.select()
                updateSymbolsForCategory(0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        categoryTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    updateSymbolsForCategory(it.position)
                    updatePrevNextButtons()
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        findViewById<ShapeableImageView>(R.id.return_jp_keyboard_button).setOnClickListener {
            returnListener?.onClick()
        }
        findViewById<ShapeableImageView>(R.id.symbol_keyboard_delete_key).apply {
            setOnClickListener { deleteClickListener?.onClick() }
            setOnLongClickListener {
                deleteLongListener?.onLongClickListener()
                true
            }
        }
    }

    private fun buildModeTabs() {
        modeTab.removeAllTabs()
        listOf(
            com.kazumaproject.core.R.drawable.mood_24px,
            com.kazumaproject.core.R.drawable.emoticon_24px,
            com.kazumaproject.core.R.drawable.star_24px
        ).forEach { res ->
            modeTab.addTab(modeTab.newTab().setIcon(res))
        }
    }

    private fun buildCategoryTabs() {
        categoryTab.removeAllTabs()
        when (currentMode) {
            SymbolMode.EMOJI -> {
                if (historyEmojiList.isNotEmpty()) {
                    categoryTab.addTab(
                        categoryTab.newTab().setIcon(com.kazumaproject.core.R.drawable.history_24dp)
                    )
                }
                emojiMap.keys.forEach { cat ->
                    categoryTab.addTab(
                        categoryTab.newTab().setIcon(
                            categoryIconRes[cat] ?: com.kazumaproject.core.R.drawable.logo_key
                        )
                    )
                }
            }

            SymbolMode.EMOTICON -> {
                val tabColor = ContextCompat.getColor(
                    this.context,
                    com.kazumaproject.core.R.color.keyboard_icon_color
                )
                categoryTab.setTabTextColors(tabColor, tabColor)
                categoryTab.addTab(categoryTab.newTab().setText("顔文字"))
            }

            SymbolMode.SYMBOL -> {
                val tabColor = ContextCompat.getColor(
                    this.context,
                    com.kazumaproject.core.R.color.keyboard_icon_color
                )
                categoryTab.setTabTextColors(tabColor, tabColor)
                categoryTab.addTab(categoryTab.newTab().setText("記号"))
            }
        }
    }

    private fun updatePrevNextButtons() {
        val current = categoryTab.selectedTabPosition
        val last = categoryTab.tabCount - 1
        prevButton.isVisible = (current > 0)
        nextButton.isVisible = (current < last)
        prevButton.isEnabled = (current > 0)
        nextButton.isEnabled = (current < last)
        prevButton.alpha = if (prevButton.isEnabled) 1f else 0.3f
        nextButton.alpha = if (nextButton.isEnabled) 1f else 0.3f
    }

    private fun updateSymbolsForCategory(index: Int) {
        // 新しいカテゴリを選択した瞬間にRecyclerViewを先頭へスクロールしておく
        recycler.scrollToPosition(0)

        // 既存データをクリア
        CoroutineScope(Dispatchers.IO).launch {
            symbolAdapter.submitData(PagingData.from(emptyList()))
        }

        val listForPaging: List<String> = when (currentMode) {
            SymbolMode.EMOJI -> {
                if (historyEmojiList.isNotEmpty() && index == 0) {
                    historyEmojiList
                } else {
                    val adjustedIndex = index - if (historyEmojiList.isNotEmpty()) 1 else 0
                    val key = emojiMap.keys.elementAtOrNull(adjustedIndex)
                    key?.let { emojiMap[it]?.map { e -> e.symbol } } ?: emptyList()
                }
            }

            SymbolMode.EMOTICON -> emoticons
            SymbolMode.SYMBOL -> symbols
        }

        symbolAdapter.symbolTextSize = if (currentMode == SymbolMode.EMOJI) {
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 24f else 20f
        } else {
            16f
        }
        if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            gridLM.spanCount = 3
        } else {
            gridLM.spanCount = 5
        }

        recycler.adapter = symbolAdapter

        pagingJob?.cancel()
        lifecycleOwner?.let { owner ->
            symbolAdapter.refresh()
            pagingJob = owner.lifecycleScope.launch {
                Pager(
                    config = PagingConfig(
                        pageSize = 50,
                        enablePlaceholders = false,
                        prefetchDistance = 10
                    )
                ) {
                    SymbolPagingSource(listForPaging)
                }.flow.collectLatest { pagingData ->
                    symbolAdapter.submitData(pagingData)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pagingJob?.cancel()
        pagingJob = null
        lifecycleOwner = null
        returnListener = null
        deleteClickListener = null
        deleteLongListener = null
        itemClickListener = null
        itemLongClickListener = null
    }

    private val categoryIconRes = mapOf(
        EmojiCategory.EMOTICONS to com.kazumaproject.core.R.drawable.mood_24px,
        EmojiCategory.GESTURES to com.kazumaproject.core.R.drawable.thumb_up_24dp,
        EmojiCategory.PEOPLE_BODY to com.kazumaproject.core.R.drawable.person_24dp,
        EmojiCategory.ANIMALS_NATURE to com.kazumaproject.core.R.drawable.pets_24dp,
        EmojiCategory.FOOD_DRINK to com.kazumaproject.core.R.drawable.fastfood_24dp,
        EmojiCategory.TRAVEL_PLACES to com.kazumaproject.core.R.drawable.travel_explore_24dp,
        EmojiCategory.ACTIVITIES to com.kazumaproject.core.R.drawable.celebration_24dp,
        EmojiCategory.OBJECTS to com.kazumaproject.core.R.drawable.lightbulb_24dp,
        EmojiCategory.SYMBOLS to com.kazumaproject.core.R.drawable.emoji_symbols,
        EmojiCategory.FLAGS to com.kazumaproject.core.R.drawable.flag_24dp,
        EmojiCategory.UNKNOWN to com.kazumaproject.core.R.drawable.question_mark_24dp
    )

    private val categoryOrder = Comparator<EmojiCategory> { a, b ->
        val order = listOf(
            EmojiCategory.EMOTICONS,
            EmojiCategory.GESTURES,
            EmojiCategory.PEOPLE_BODY,
            EmojiCategory.ANIMALS_NATURE,
            EmojiCategory.FOOD_DRINK,
            EmojiCategory.TRAVEL_PLACES,
            EmojiCategory.ACTIVITIES,
            EmojiCategory.OBJECTS,
            EmojiCategory.SYMBOLS,
            EmojiCategory.FLAGS,
            EmojiCategory.UNKNOWN
        )
        order.indexOf(a).compareTo(order.indexOf(b))
    }
}
