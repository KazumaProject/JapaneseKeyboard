package com.kazumaproject.symbol_keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.core.data.clicked_symbol.SymbolMode
import com.kazumaproject.core.data.clipboard.ClipboardItem
import com.kazumaproject.data.clicked_symbol.ClickedSymbol
import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.data.emoji.EmojiCategory
import com.kazumaproject.listeners.ClipboardHistoryToggleListener
import com.kazumaproject.listeners.ClipboardItemLongClickListener
import com.kazumaproject.listeners.DeleteButtonSymbolViewClickListener
import com.kazumaproject.listeners.DeleteButtonSymbolViewLongClickListener
import com.kazumaproject.listeners.ImageItemClickListener
import com.kazumaproject.listeners.ReturnToTenKeyButtonClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemLongClickListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SuppressLint("ClickableViewAccessibility")
class CustomSymbolKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private val categoryTab: TabLayout
    private val modeTab: TabLayout
    private val recycler: RecyclerView
    private val symbolAdapter = SymbolAdapter()
    private val clipboardAdapter = ClipboardAdapter()
    private val gridLM = GridLayoutManager(context, 3, RecyclerView.HORIZONTAL, false)

    private var emojiMap: Map<EmojiCategory, List<Emoji>> = emptyMap()
    private var emoticons: List<String> = emptyList()
    private var symbols: List<String> = emptyList()
    private var historyEmojiList: MutableList<String> = mutableListOf()
    private var symbolsHistory: List<ClickedSymbol> = emptyList()
    private var clipBoardItems: List<ClipboardItem> = emptyList()
    private var currentMode: SymbolMode = SymbolMode.EMOJI

    private var pagingJob: Job? = null
    private var lifecycleOwner: LifecycleOwner? = null

    private var returnListener: ReturnToTenKeyButtonClickListener? = null
    private var deleteClickListener: DeleteButtonSymbolViewClickListener? = null
    private var deleteLongListener: DeleteButtonSymbolViewLongClickListener? = null
    private var itemClickListener: SymbolRecyclerViewItemClickListener? = null
    private var itemLongClickListener: SymbolRecyclerViewItemLongClickListener? = null
    private var imageItemClickListener: ImageItemClickListener? = null
    private var clipboardItemLongClickListener: ClipboardItemLongClickListener? = null
    private var clipboardHistoryToggleListener: ClipboardHistoryToggleListener? = null
    private var isClipboardHistoryEnabled: Boolean = false

    init {
        inflate(context, R.layout.symbol_keyboard_main_layout, this)

        categoryTab = findViewById(R.id.category_tab_layout)
        modeTab = findViewById(R.id.mode_tab_layout)
        recycler = findViewById(R.id.symbol_candidate_recycler_view)

        recycler.apply {
            layoutManager = gridLM
            adapter = symbolAdapter
            itemAnimator = null
        }

        symbolAdapter.setOnItemClickListener { str ->
            itemClickListener?.onClick(ClickedSymbol(mode = currentMode, symbol = str))
        }

        clipboardAdapter.setOnItemClickListener { item ->
            when (item) {
                is ClipboardItem.Text -> {
                    itemClickListener?.onClick(
                        ClickedSymbol(
                            mode = currentMode,
                            symbol = item.text
                        )
                    )
                }

                is ClipboardItem.Image -> {
                    imageItemClickListener?.onImageClick(item.bitmap)
                }

                else -> {}
            }
        }

        clipboardAdapter.setOnItemLongClickListener { item, position ->
            clipboardItemLongClickListener?.onLongClick(item, position)
        }

        symbolAdapter.setOnItemLongClickListener { str, pos ->
            if (currentMode == SymbolMode.EMOJI
                && historyEmojiList.isNotEmpty()
                && categoryTab.selectedTabPosition == 0
                && pos in 0 until historyEmojiList.size
            ) {
                itemLongClickListener?.onLongClick(
                    ClickedSymbol(mode = currentMode, symbol = str),
                    position = pos
                )
                historyEmojiList = historyEmojiList.toMutableList().apply { removeAt(pos) }
                updateSymbolsForCategory(0)
            }
        }

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

        modeTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentMode = SymbolMode.entries[tab?.position ?: 0]
                buildCategoryTabs()
                categoryTab.getTabAt(0)?.select()
                updateSymbolsForCategory(0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        categoryTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                updateSymbolsForCategory(tab?.position ?: 0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent) = true

            private val SWIPE_DISTANCE_THRESHOLD = 30f
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (e1 == null) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y

                if (kotlin.math.abs(dx) > SWIPE_DISTANCE_THRESHOLD
                    && kotlin.math.abs(vx) > SWIPE_VELOCITY_THRESHOLD
                    && kotlin.math.abs(dx) > kotlin.math.abs(dy)
                ) {
                    if (dx > 0) selectPreviousCategory()
                    else selectNextCategory()
                    return true
                }
                return false
            }
        }
    )

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val loc = IntArray(2).also { recycler.getLocationOnScreen(it) }
        val y = ev.rawY
        if (y >= loc[1] && y <= loc[1] + recycler.height) {
            gestureDetector.onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

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

    fun setOnImageItemClickListener(l: ImageItemClickListener) { // <<< 追加
        imageItemClickListener = l
    }

    fun setOnClipboardItemLongClickListener(l: ClipboardItemLongClickListener) {
        clipboardItemLongClickListener = l
    }

    fun updateClipboardItems(newItems: List<ClipboardItem>) {
        this.clipBoardItems = newItems
        if (currentMode == SymbolMode.CLIPBOARD) {
            updateSymbolsForCategory(categoryTab.selectedTabPosition)
        }
    }

    /**
     * クリップボード履歴の有効/無効を設定し、表示に反映させる
     * @param isEnabled スイッチをONにする場合はtrue
     */
    fun setClipboardHistoryEnabled(isEnabled: Boolean) {
        this.isClipboardHistoryEnabled = isEnabled
        // すでにタブが表示されている場合は、スイッチの状態を即座に更新する
        if (currentMode == SymbolMode.CLIPBOARD) {
            categoryTab.getTabAt(0)?.customView?.let {
                val switch = it.findViewById<SwitchMaterial>(R.id.clipboard_tab_switch)
                switch?.isChecked = isEnabled
            }
        }
    }

    /**
     * クリップボード履歴のOn/Offが切り替わったときのリスナーを設定する
     */
    fun setOnClipboardHistoryToggleListener(l: ClipboardHistoryToggleListener) {
        this.clipboardHistoryToggleListener = l
    }

    fun setSymbolLists(
        emojiList: List<Emoji>,
        emoticons: List<String>,
        symbols: List<String>,
        clipBoardItems: List<ClipboardItem>,
        symbolsHistory: List<ClickedSymbol>,
        symbolMode: SymbolMode = SymbolMode.EMOJI
    ) {
        this.symbolsHistory = symbolsHistory
        this.clipBoardItems = clipBoardItems
        historyEmojiList = symbolsHistory
            .filter { it.mode == SymbolMode.EMOJI }
            .map { it.symbol }
            .toMutableList()
        this.emoticons = emoticons
        this.symbols = symbols
        emojiMap = emojiList.groupBy { it.category }.toSortedMap(categoryOrder)

        currentMode = symbolMode
        buildModeTabs()
        buildCategoryTabs()
        modeTab.getTabAt(symbolMode.ordinal)?.select()
        categoryTab.getTabAt(0)?.select()
        updateSymbolsForCategory(0)
    }

    private fun buildModeTabs() {
        modeTab.removeAllTabs()
        listOf(
            com.kazumaproject.core.R.drawable.mood_24px,
            com.kazumaproject.core.R.drawable.emoticon_24px,
            com.kazumaproject.core.R.drawable.star_24px,
            com.kazumaproject.core.R.drawable.clip_board,
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

            SymbolMode.CLIPBOARD -> {
                // 1. 新しいタブを作成し、カスタムビューを設定
                val tab = categoryTab.newTab().setCustomView(R.layout.custom_tab_clipboard)

                // 2. タブをレイアウトに追加
                categoryTab.addTab(tab)

                // 3. カスタムビュー内のスイッチを取得して設定
                tab.customView?.let { customView ->
                    val switch = customView.findViewById<SwitchMaterial>(R.id.clipboard_tab_switch)
                    // 外部から設定された状態をスイッチに反映
                    switch.isChecked = isClipboardHistoryEnabled
                    // スイッチが操作されたときのリスナーを設定
                    switch.setOnCheckedChangeListener { _, isChecked ->
                        // 状態を内部プロパティに保存し、外部リスナーを呼び出す
                        isClipboardHistoryEnabled = isChecked
                        clipboardHistoryToggleListener?.onToggled(isChecked)
                    }
                    // テキストの色をテーマに合わせる
                    val textColor = ContextCompat.getColor(
                        context,
                        com.kazumaproject.core.R.color.keyboard_icon_color
                    )
                    customView.findViewById<TextView>(R.id.clipboard_tab_text)
                        .setTextColor(textColor)
                }
            }

            SymbolMode.EMOTICON, SymbolMode.SYMBOL -> {
                val c = ContextCompat.getColor(
                    context,
                    com.kazumaproject.core.R.color.keyboard_icon_color
                )
                categoryTab.setTabTextColors(c, c)
                val text = when (currentMode) {
                    SymbolMode.EMOTICON -> "顔文字"
                    SymbolMode.SYMBOL -> "記号"
                    SymbolMode.CLIPBOARD -> "クリップボード"
                    else -> ""
                }
                categoryTab.addTab(categoryTab.newTab().setText(text))
            }
        }
    }

    private fun updateSymbolsForCategory(index: Int) {
        pagingJob?.cancel()
        lifecycleOwner?.let { owner ->
            // ▼▼▼ ここから修正 ▼▼▼
            pagingJob = owner.lifecycleScope.launch {
                // 1. 最初に両方のアダプターのデータをクリアする
                // これにより、タブ切り替え時に古いデータが残るのを防ぐ
                symbolAdapter.submitData(PagingData.empty())
                clipboardAdapter.submitData(PagingData.empty())

                // 2. RecyclerViewを先頭にスクロール
                recycler.scrollToPosition(0)

                // 3. 現在のモードに応じて適切なアダプターとデータを設定する
                when (currentMode) {
                    SymbolMode.CLIPBOARD -> {
                        recycler.adapter = clipboardAdapter
                        gridLM.spanCount =
                            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 2 else 4
                        gridLM.orientation = RecyclerView.VERTICAL

                        Pager(
                            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                            pagingSourceFactory = { ClipboardPagingSource(clipBoardItems) }
                        ).flow.collectLatest { pagingData ->
                            clipboardAdapter.submitData(pagingData)
                        }
                    }

                    else -> {
                        recycler.adapter = symbolAdapter

                        val listForPaging = when (currentMode) {
                            SymbolMode.EMOJI -> {
                                if (historyEmojiList.isNotEmpty() && index == 0) historyEmojiList
                                else {
                                    val adj = index - if (historyEmojiList.isNotEmpty()) 1 else 0
                                    emojiMap.keys.elementAtOrNull(adj)
                                        ?.let { emojiMap[it]?.map { e -> e.symbol } } ?: emptyList()
                                }
                            }

                            SymbolMode.EMOTICON -> emoticons
                            SymbolMode.SYMBOL -> symbols
                            else -> emptyList()
                        }

                        symbolAdapter.symbolTextSize = if (currentMode == SymbolMode.EMOJI) {
                            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 36f else 30f
                        } else 16f

                        gridLM.spanCount = when (currentMode) {
                            SymbolMode.EMOJI -> 7
                            SymbolMode.EMOTICON, SymbolMode.SYMBOL -> 5
                            else -> 5
                        }
                        gridLM.orientation = when (currentMode) {
                            SymbolMode.EMOJI -> RecyclerView.VERTICAL
                            SymbolMode.EMOTICON, SymbolMode.SYMBOL -> RecyclerView.HORIZONTAL
                            else -> RecyclerView.VERTICAL
                        }

                        Pager(
                            config = PagingConfig(pageSize = 100, enablePlaceholders = false),
                            pagingSourceFactory = { SymbolPagingSource(listForPaging) }
                        ).flow.collectLatest { pagingData ->
                            symbolAdapter.submitData(pagingData)
                        }
                    }
                }
            }
            // ▲▲▲ ここまで修正 ▲▲▲
        }
    }

    private fun selectPreviousCategory() {
        val i = categoryTab.selectedTabPosition
        if (i > 0) categoryTab.getTabAt(i - 1)?.select()
    }

    private fun selectNextCategory() {
        val i = categoryTab.selectedTabPosition
        val last = categoryTab.tabCount - 1
        if (i < last) categoryTab.getTabAt(i + 1)?.select()
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
        imageItemClickListener = null
        clipboardItemLongClickListener = null
        clipboardHistoryToggleListener = null
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
        listOf(
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
        ).indexOf(a).compareTo(
            listOf(
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
            ).indexOf(b)
        )
    }
}
