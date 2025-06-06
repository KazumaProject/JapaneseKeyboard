// CustomSymbolKeyboardView.kt
package com.kazumaproject.symbol_keyboard

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatButton
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.recyclerview.widget.ConcatAdapter
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

    private var scrollToEndOnNextLoad = false

    /* UI 部品 */
    private val categoryTab: TabLayout
    private val modeTab: TabLayout
    private val recycler: RecyclerView
    private val symbolAdapter = SymbolAdapter()

    private val gridLM = GridLayoutManager(context, 3, RecyclerView.HORIZONTAL, false)

    /* データ保持 */
    private var emojiMap: Map<EmojiCategory, List<Emoji>> = emptyMap()
    private var emoticons: List<String> = emptyList()
    private var symbols: List<String> = emptyList()

    // 履歴用
    private var historyEmojiList: List<String> = emptyList()
    private var symbolsHistory: List<ClickedSymbol> = emptyList()

    private var currentMode: SymbolMode = SymbolMode.EMOJI

    /* others */
    private var pagingJob: Job? = null
    private var lifecycleOwner: LifecycleOwner? = null

    /* リスナ登録用 */
    private var returnListener: ReturnToTenKeyButtonClickListener? = null
    private var deleteClickListener: DeleteButtonSymbolViewClickListener? = null
    private var deleteLongListener: DeleteButtonSymbolViewLongClickListener? = null
    private var itemClickListener: SymbolRecyclerViewItemClickListener? = null
    private var itemLongClickListener: SymbolRecyclerViewItemLongClickListener? = null

    /** ライフサイクルオーナーを渡す */
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
     * 絵文字・顔文字・記号をセット（必須）
     */
    fun setSymbolLists(
        emojiList: List<Emoji>,
        emoticons: List<String>,
        symbols: List<String>,
        symbolsHistory: List<ClickedSymbol>,
        defaultMode: SymbolMode = SymbolMode.EMOJI,
    ) {
        // 履歴から EMOJI のものだけ取り出し
        this.symbolsHistory = symbolsHistory
        historyEmojiList = symbolsHistory.filter { it.mode == SymbolMode.EMOJI }
            .map { it.symbol }

        this.emoticons = emoticons
        this.symbols = symbols
        emojiMap = emojiList.groupBy { it.category }.toSortedMap(categoryOrder)

        currentMode = defaultMode
        buildModeTabs()
        buildCategoryTabs()

        modeTab.getTabAt(defaultMode.ordinal)?.select()
        categoryTab.getTabAt(0)?.select()
        updateSymbolsForCategory(0)
    }

    init {
        inflate(context, R.layout.symbol_keyboard_main_layout, this)

        categoryTab = findViewById(R.id.category_tab_layout)
        modeTab = findViewById(R.id.mode_tab_layout)
        recycler = findViewById(R.id.symbol_candidate_recycler_view)

        recycler.apply {
            layoutManager = gridLM
            adapter = symbolAdapter
            itemAnimator = null
            isSaveEnabled = false
        }

        // ① 通常クリック
        symbolAdapter.setOnItemClickListener { str ->
            itemClickListener?.onClick(ClickedSymbol(mode = currentMode, symbol = str))
        }

        // ② 長押しクリック → DB から削除も行う
        symbolAdapter.setOnItemLongClickListener { str ->
            if (categoryTab.selectedTabPosition == 0) {
                itemLongClickListener?.onLongClick(ClickedSymbol(mode = currentMode, symbol = str))
            }
        }

        symbolAdapter.addLoadStateListener { state ->
            if (state.refresh is LoadState.Loading) {
                recycler.post {
                    for (i in 0 until symbolAdapter.itemCount) {
                        symbolAdapter.notifyItemChanged(i)
                    }
                    recycler.scrollToPosition(0)
                }
            }
            if (state.refresh is LoadState.NotLoading && scrollToEndOnNextLoad) {
                val lastIndex = symbolAdapter.itemCount - 1
                if (lastIndex >= 0) {
                    recycler.post { recycler.scrollToPosition(lastIndex) }
                }
                scrollToEndOnNextLoad = false
            }
        }

        modeTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentMode = SymbolMode.entries.toTypedArray()[tab?.position ?: 0]
                buildCategoryTabs()
                categoryTab.getTabAt(0)?.select()
                updateSymbolsForCategory(0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        categoryTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let { updateSymbolsForCategory(it.position) }
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

    private fun updateSymbolsForCategory(index: Int) {
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

        symbolAdapter.symbolTextSize = if (currentMode == SymbolMode.EMOJI) 36f else 16f
        gridLM.orientation =
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                RecyclerView.HORIZONTAL else RecyclerView.VERTICAL

        pagingJob?.cancel()
        lifecycleOwner?.let { owner ->
            val showPrevButton = index > 0
            val showNextButton = index < categoryTab.tabCount - 1

            val prevAdapter = if (showPrevButton) ButtonAdapter(isNext = false) else null
            val nextAdapter = if (showNextButton) ButtonAdapter(isNext = true) else null
            val concatConfig = ConcatAdapter.Config.Builder()
                .setIsolateViewTypes(true)
                .build()
            val adaptersList = mutableListOf<RecyclerView.Adapter<out RecyclerView.ViewHolder>>()
            prevAdapter?.let { adaptersList.add(it) }
            adaptersList.add(symbolAdapter)
            nextAdapter?.let { adaptersList.add(it) }

            val concatAdapter = ConcatAdapter(concatConfig, adaptersList)
            recycler.adapter = concatAdapter

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

    private inner class ButtonAdapter(private val isNext: Boolean) :
        RecyclerView.Adapter<ButtonAdapter.ButtonViewHolder>() {

        inner class ButtonViewHolder(val button: AppCompatButton) :
            RecyclerView.ViewHolder(button)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonViewHolder {
            val button = AppCompatButton(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                text = if (isNext) ">" else "<"
                textSize = 18f
                gravity = Gravity.CENTER
                textAlignment = AppCompatButton.TEXT_ALIGNMENT_CENTER
                background = ContextCompat.getDrawable(
                    this.context,
                    com.kazumaproject.core.R.drawable.ten_keys_center_bg
                )
            }
            return ButtonViewHolder(button)
        }

        override fun onBindViewHolder(holder: ButtonViewHolder, position: Int) {
            holder.button.setOnClickListener {
                val currentTab = categoryTab.selectedTabPosition
                val tabCount = categoryTab.tabCount
                if (isNext) {
                    val nextTab = currentTab + 1
                    if (nextTab < tabCount) {
                        categoryTab.getTabAt(nextTab)?.select()
                    }
                } else {
                    val prevTab = currentTab - 1
                    if (prevTab >= 0) {
                        scrollToEndOnNextLoad = true
                        categoryTab.getTabAt(prevTab)?.select()
                    }
                }
            }
        }

        override fun getItemCount(): Int = 1
        override fun getItemViewType(position: Int): Int = Int.MAX_VALUE
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
