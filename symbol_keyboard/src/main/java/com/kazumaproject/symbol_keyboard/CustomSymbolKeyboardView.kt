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
import com.kazumaproject.data.emoji.Emoji
import com.kazumaproject.data.emoji.EmojiCategory
import com.kazumaproject.listeners.DeleteButtonSymbolViewClickListener
import com.kazumaproject.listeners.DeleteButtonSymbolViewLongClickListener
import com.kazumaproject.listeners.ReturnToTenKeyButtonClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemClickListener
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

    /* ───────────────── モード定義 ─────────────── */
    enum class Mode { EMOJI, EMOTICON, SYMBOL }

    private var scrollToEndOnNextLoad = false

    /* ───────────────── UI 部品 ──────────────── */
    private val categoryTab: TabLayout
    private val modeTab: TabLayout
    private val recycler: RecyclerView
    private val symbolAdapter = SymbolAdapter()

    // 1行あたり3列（横スクロール時は1行3列、縦スクロール時は縦に3列）に設定
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
        // カテゴリごとにグループ化し、所定の順番でソートした Map を用意
        emojiMap = emojiList.groupBy { it.category }.toSortedMap(categoryOrder)
        this.emoticons = emoticons
        this.symbols = symbols

        currentMode = defaultMode
        buildModeTabs()              // 下段モードタブ
        buildCategoryTabs()          // 上段カテゴリタブ

        // デフォルトで選択するタブをセット
        modeTab.getTabAt(defaultMode.ordinal)?.select()
        categoryTab.getTabAt(0)?.select()
        // 初期表示：必ず最初のカテゴリ（index=0）をロード
        updateSymbolsForCategory(0)
    }

    /* ─────────────────── init ─────────────────── */
    init {
        inflate(context, R.layout.symbol_keyboard_main_layout, this)

        // find views
        categoryTab = findViewById(R.id.category_tab_layout)
        modeTab = findViewById(R.id.mode_tab_layout)
        recycler = findViewById(R.id.symbol_candidate_recycler_view)

        // RecyclerView 設定
        recycler.apply {
            layoutManager = gridLM
            // 最初はシンボルアダプタを直接セットしておく（あとで ConcatAdapter に置き換える）
            adapter = symbolAdapter
            itemAnimator = null
            isSaveEnabled = false
        }
        // クリックリスナは Adapter に設定
        symbolAdapter.setOnItemClickListener { itemClickListener?.onClick(it) }

        /* ── LoadStateListener でデータ切り替え後にスクロールと再描画 ── */
        symbolAdapter.addLoadStateListener { state ->
            // 「データロード中（新しいカテゴリの先頭を読み込む際）」は先頭へスクロール
            if (state.refresh is LoadState.Loading) {
                recycler.post {
                    // 新ロード中の間も旧セルをnotifyして見栄えを整える
                    for (i in 0 until symbolAdapter.itemCount) {
                        symbolAdapter.notifyItemChanged(i)
                    }
                    recycler.scrollToPosition(0)
                }
            }

            // 「前のカテゴリに戻る」ボタンが押されていた場合は末尾へスクロール
            if (state.refresh is LoadState.NotLoading && scrollToEndOnNextLoad) {
                val lastIndex = symbolAdapter.itemCount - 1
                if (lastIndex >= 0) {
                    recycler.post { recycler.scrollToPosition(lastIndex) }
                }
                scrollToEndOnNextLoad = false
            }
        }

        /* ── 下段モードタブのリスナ ── */
        modeTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                // 選択されたタブに応じてモードを設定
                currentMode = Mode.values()[tab?.position ?: 0]
                buildCategoryTabs()
                // 上段カテゴリタブは必ず先頭を選択し直す
                categoryTab.getTabAt(0)?.select()
                // 再度ロード
                updateSymbolsForCategory(0)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        /* ── 上段カテゴリタブのリスナ ── */
        categoryTab.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let { updateSymbolsForCategory(it.position) }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        /* ── 戻る／削除ボタン ── */
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
            Mode.EMOJI -> {
                emojiMap.keys.forEach { cat ->
                    categoryTab.addTab(
                        categoryTab.newTab()
                            .setIcon(
                                categoryIconRes[cat] ?: com.kazumaproject.core.R.drawable.logo_key
                            )
                    )
                }
            }

            Mode.EMOTICON -> {
                val tabColor = ContextCompat.getColor(
                    this.context,
                    com.kazumaproject.core.R.color.keyboard_icon_color
                )
                categoryTab.setTabTextColors(tabColor, tabColor)
                categoryTab.addTab(categoryTab.newTab().setText("顔文字"))
            }

            Mode.SYMBOL -> {
                val tabColor = ContextCompat.getColor(
                    this.context,
                    com.kazumaproject.core.R.color.keyboard_icon_color
                )
                categoryTab.setTabTextColors(tabColor, tabColor)
                categoryTab.addTab(categoryTab.newTab().setText("記号"))
            }
        }
    }

    /* ───────────── RecyclerView データ更新 ───────────── */
    private fun updateSymbolsForCategory(index: Int) {
        // ────────── ここで旧データをクリア ──────────
        // PagingDataAdapter に空のデータを即時流し込んで、旧モードのアイテムを消す
        CoroutineScope(Dispatchers.IO).launch {
            symbolAdapter.submitData(PagingData.from(emptyList()))
        }

        // (1) カテゴリに応じた文字列リストを取得
        val listForPaging: List<String> = when (currentMode) {
            Mode.EMOJI -> {
                val key = emojiMap.keys.elementAtOrNull(index)
                key?.let { emojiMap[it]?.map { e -> e.symbol } } ?: emptyList()
            }

            Mode.EMOTICON -> emoticons
            Mode.SYMBOL -> symbols
        } ?: emptyList()

        // (2) シンボル表示サイズを切り替え
        symbolAdapter.symbolTextSize = if (currentMode == Mode.EMOJI) 36f else 16f

        // (3) 画面の向きに応じて GridLayoutManager の向きを切り替え
        gridLM.orientation =
            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                RecyclerView.HORIZONTAL else RecyclerView.VERTICAL

        // (4) 既存の Flow をキャンセル
        pagingJob?.cancel()

        lifecycleOwner?.let { owner ->
            // (5) 「前のカテゴリ」「次のカテゴリ」ボタン表示判定
            val showPrevButton = index > 0
            val showNextButton = index < categoryTab.tabCount - 1

            // (6) ボタン用 Adapter を作成
            val prevAdapter = if (showPrevButton) ButtonAdapter(isNext = false) else null
            val nextAdapter = if (showNextButton) ButtonAdapter(isNext = true) else null

            // (7) ConcatAdapter 用 Config を作成（ViewType 衝突防止）
            val concatConfig = ConcatAdapter.Config.Builder()
                .setIsolateViewTypes(true)
                .build()

            // (8) ConcatAdapter に渡す子アダプタのリストを準備
            val adaptersList = mutableListOf<RecyclerView.Adapter<out RecyclerView.ViewHolder>>()
            prevAdapter?.let { adaptersList.add(it) }
            adaptersList.add(symbolAdapter)
            nextAdapter?.let { adaptersList.add(it) }

            // (9) ConcatAdapter を構築して RecyclerView にセット
            val concatAdapter = ConcatAdapter(concatConfig, adaptersList)
            recycler.adapter = concatAdapter

            // (10) PagingDataAdapter のリフレッシュと新 Flow の購読開始
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

    /* ───────────── cleanup ───────────── */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pagingJob?.cancel()
        lifecycleOwner = null
    }

    /**
     * 「前のカテゴリ」「次のカテゴリ」に切り替えるためのボタン用 Adapter
     */
    private inner class ButtonAdapter(private val isNext: Boolean) :
        RecyclerView.Adapter<ButtonAdapter.ButtonViewHolder>() {

        inner class ButtonViewHolder(val button: AppCompatButton) :
            RecyclerView.ViewHolder(button)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ButtonViewHolder {
            // ボタンをプログラムで作成。高さは RecyclerView の高さに合わせ、幅は WRAP_CONTENT。
            val button = AppCompatButton(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // ボタン文字列（">" または "<"）
                text = if (isNext) ">" else "<"
                textSize = 18f
                gravity = Gravity.CENTER
                textAlignment = AppCompatButton.TEXT_ALIGNMENT_CENTER
                // 背景や色を設定
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
                    // 次のカテゴリに移動
                    val nextTab = currentTab + 1
                    if (nextTab < tabCount) {
                        categoryTab.getTabAt(nextTab)?.select()
                    }
                } else {
                    // 前のカテゴリに移動
                    val prevTab = currentTab - 1
                    if (prevTab >= 0) {
                        // 「前のカテゴリに戻る」ので、ロード完了後に末尾へスクロールさせる
                        scrollToEndOnNextLoad = true
                        categoryTab.getTabAt(prevTab)?.select()
                    }
                }
            }
        }

        override fun getItemCount(): Int = 1
        private val BUTTON_VIEW_TYPE = Int.MAX_VALUE  // 大きめの定数を指定

        // ButtonAdapter はシンプルなボタンのみなので、ViewType は固定値でOK
        override fun getItemViewType(position: Int): Int = BUTTON_VIEW_TYPE
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
}
