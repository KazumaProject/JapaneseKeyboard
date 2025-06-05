package com.kazumaproject.symbol_keyboard

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.LoadState
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.data.emoji.EmojiCategory
import com.kazumaproject.listeners.DeleteButtonSymbolViewClickListener
import com.kazumaproject.listeners.DeleteButtonSymbolViewLongClickListener
import com.kazumaproject.listeners.ReturnToTenKeyButtonClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemClickListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CustomSymbolKeyboardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    /* ───────────────── モード定義 ─────────────── */
    enum class Mode { EMOJI, EMOTICON, SYMBOL }

    /* ───────────────── UI 部品 ──────────────── */
    private val categoryTab: TabLayout
    private val modeTab: TabLayout
    private val recycler: RecyclerView
    private val adapter = SymbolAdapter()
    private val gridLM = GridLayoutManager(context, 3, RecyclerView.HORIZONTAL, false)

    /* ───────────────── データ保持 ─────────────── */
    private var emojiMap: Map<EmojiCategory, List<Emoji>> = emptyMap()
    private var emoticons: List<String> = emptyList()
    private var symbols: List<String> = emptyList()
    private var currentMode: Mode = Mode.EMOJI

    /* ───────────────── others ────────────────── */
    private var pagingJob: Job? = null
    private var lifecycleOwner: LifecycleOwner? = null

    /* ───────────────── リスナ登録用 ───────────── */
    private var returnListener: ReturnToTenKeyButtonClickListener? = null
    private var deleteClickListener: DeleteButtonSymbolViewClickListener? = null
    private var deleteLongListener: DeleteButtonSymbolViewLongClickListener? = null
    private var itemClickListener: SymbolRecyclerViewItemClickListener? = null

    /* ───────────────── public API ─────────────── */
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

    /**
     * 絵文字・顔文字・記号をセット（必須）
     */
    fun setSymbolLists(
        emojiList: List<Emoji>,
        emoticons: List<String>,
        symbols: List<String>,
        defaultMode: Mode = Mode.EMOJI
    ) {
        emojiMap = emojiList.groupBy { it.category }.toSortedMap(categoryOrder)
        this.emoticons = emoticons
        this.symbols = symbols

        currentMode = defaultMode
        buildModeTabs()              // 下段
        buildCategoryTabs()          // 上段

        modeTab.getTabAt(defaultMode.ordinal)?.select()
        categoryTab.getTabAt(0)?.select()
        updateSymbolsForCategory(0)  // 初期表示
    }

    /* ─────────────────── init ─────────────────── */
    init {
        inflate(context, R.layout.symbol_keyboard_main_layout, this)

        // find views
        categoryTab = findViewById(R.id.category_tab_layout)
        modeTab = findViewById(R.id.mode_tab_layout)
        recycler = findViewById(R.id.symbol_candidate_recycler_view)

        // RecyclerView
        recycler.apply {
            layoutManager = gridLM
            adapter = this@CustomSymbolKeyboardView.adapter
            setHasFixedSize(true)
            itemAnimator = null
        }
        adapter.setOnItemClickListener { itemClickListener?.onClick(it) }

        /* ≡=================== 重要: LoadStateListener ===================≡
         * データ切替後、一度だけ refresh → NotLoading を検出して
         * 一括再描画 & 先頭位置へスクロール。これで
         * 「スクロールしないと中身が変わらない」症状を根絶します。
         * =============================================================== */
        adapter.addLoadStateListener { state ->
            if (state.refresh is LoadState.NotLoading) {
                recycler.post {
                    adapter.notifyDataSetChanged()   // 表示中 item を即再バインド
                    recycler.scrollToPosition(0)
                }
            }
        }

        /* ─ 下段モードタブ ─ */
        modeTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentMode = Mode.entries.toTypedArray()[tab?.position ?: 0]
                buildCategoryTabs()
                categoryTab.getTabAt(0)?.select()       // 上段をリセット
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        /* ─ 上段カテゴリタブ ─ */
        categoryTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let { updateSymbolsForCategory(it.position) }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        /* ─ 戻る / 削除ボタン ─ */
        findViewById<ShapeableImageView>(R.id.return_jp_keyboard_button).setOnClickListener {
            returnListener?.onClick()
        }
        findViewById<ShapeableImageView>(R.id.symbol_keyboard_delete_key).apply {
            setOnClickListener { deleteClickListener?.onClick() }
            setOnLongClickListener { deleteLongListener?.onLongClickListener(); true }
        }
    }

    /* ────────────────── 下段モードタブ ───────────────── */
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

    /* ────────────────── 上段カテゴリタブ ──────────────── */
    private fun buildCategoryTabs() {
        categoryTab.removeAllTabs()
        when (currentMode) {
            Mode.EMOJI -> emojiMap.keys.forEach { cat ->
                categoryTab.addTab(
                    categoryTab.newTab()
                        .setIcon(categoryIconRes[cat] ?: com.kazumaproject.core.R.drawable.logo_key)
                )
            }

            Mode.EMOTICON -> categoryTab.addTab(categoryTab.newTab().setText("顔文字"))
            Mode.SYMBOL -> categoryTab.addTab(categoryTab.newTab().setText("記号"))
        }
    }

    /* ───────────── RecyclerView データ更新 ───────────── */
    private fun updateSymbolsForCategory(index: Int) {
        val list: List<String> = when (currentMode) {
            Mode.EMOJI -> {
                val key = emojiMap.keys.elementAt(index)
                emojiMap[key]?.map { it.symbol } ?: emptyList()
            }

            Mode.EMOTICON -> emoticons
            Mode.SYMBOL -> symbols
        }

        adapter.symbolTextSize = if (currentMode == Mode.EMOJI) 42f else 32f
        gridLM.orientation =
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL

        pagingJob?.cancel()          // 旧 Flow を止める
        lifecycleOwner?.let { owner ->
            adapter.refresh()        // ★ 既存データを即クリアし、再ロード待機
            pagingJob = owner.lifecycleScope.launch {
                Pager(
                    PagingConfig(pageSize = 50, enablePlaceholders = false, prefetchDistance = 10)
                ) { SymbolPagingSource(list) }.flow.collectLatest { adapter.submitData(it) }
            }
        }
    }

    /* ───────────── アイコン対応表 & 並び順 ───────────── */
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

    /* ───────────── cleanup ───────────── */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pagingJob?.cancel()
        lifecycleOwner = null
    }
}
