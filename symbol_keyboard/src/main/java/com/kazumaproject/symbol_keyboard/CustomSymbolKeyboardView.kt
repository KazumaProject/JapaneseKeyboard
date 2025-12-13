package com.kazumaproject.symbol_keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
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
import com.kazumaproject.data.emoticon.Emoticon
import com.kazumaproject.data.emoticon.EmoticonCategory
import com.kazumaproject.data.symbol.Symbol
import com.kazumaproject.data.symbol.SymbolCategory
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

    // View References for functional keys
    private val returnButton: ShapeableImageView
    private val deleteButton: ShapeableImageView

    // Theme Colors (Default values)
    private var themeBackgroundColor: Int = Color.WHITE
    private var themeIconColor: Int = Color.GRAY
    private var themeSelectedIconColor: Int = Color.BLUE
    private var themeKeyBackgroundColor: Int = Color.LTGRAY
    private var liquidGlassEnable: Boolean = false

    // Flag to check if custom theme is applied
    private var isCustomThemeApplied = false

    private var emojiMap: Map<EmojiCategory, List<Emoji>> = emptyMap()
    private var emoticonMap: Map<EmoticonCategory, List<String>> = emptyMap()
    private var symbolMap: Map<SymbolCategory, List<String>> = emptyMap()

    private var historyEmojiList: MutableList<String> = mutableListOf()
    private var historyEmoticonList: MutableList<String> = mutableListOf()
    private var historySymbolList: MutableList<String> = mutableListOf()

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
    private var onDeleteFingerUpListener: (() -> Unit)? = null

    init {
        inflate(context, R.layout.symbol_keyboard_main_layout, this)

        categoryTab = findViewById(R.id.category_tab_layout)
        modeTab = findViewById(R.id.mode_tab_layout)
        recycler = findViewById(R.id.symbol_candidate_recycler_view)
        returnButton = findViewById(R.id.return_jp_keyboard_button)
        deleteButton = findViewById(R.id.symbol_keyboard_delete_key)

        // Initialize default colors
        themeIconColor =
            ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_icon_color)
        themeSelectedIconColor =
            ContextCompat.getColor(context, com.kazumaproject.core.R.color.enter_key_bg)
        themeKeyBackgroundColor =
            ContextCompat.getColor(context, com.kazumaproject.core.R.color.keyboard_bg)

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
            val historyList = when (currentMode) {
                SymbolMode.EMOJI -> historyEmojiList
                SymbolMode.EMOTICON -> historyEmoticonList
                SymbolMode.SYMBOL -> historySymbolList
                else -> emptyList()
            }
            if (historyList.isNotEmpty()
                && categoryTab.selectedTabPosition == 0
                && pos in historyList.indices
            ) {
                itemLongClickListener?.onLongClick(
                    ClickedSymbol(mode = currentMode, symbol = str),
                    position = pos
                )
                when (currentMode) {
                    SymbolMode.EMOJI -> historyEmojiList =
                        historyEmojiList.toMutableList().apply { removeAt(pos) }

                    SymbolMode.EMOTICON -> historyEmoticonList =
                        historyEmoticonList.toMutableList().apply { removeAt(pos) }

                    SymbolMode.SYMBOL -> historySymbolList =
                        historySymbolList.toMutableList().apply { removeAt(pos) }

                    else -> {}
                }
                updateSymbolsForCategory(0)
            }
        }

        returnButton.setOnClickListener {
            returnListener?.onClick()
        }

        deleteButton.apply {
            val handler = Handler(Looper.getMainLooper())
            var isLongPressed = false

            val longPressRunnable = Runnable {
                isLongPressed = true
                deleteLongListener?.onLongClickListener()
            }

            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isLongPressed = false
                        handler.postDelayed(
                            longPressRunnable,
                            ViewConfiguration.getLongPressTimeout().toLong()
                        )
                        true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPressRunnable)
                        if (isLongPressed) {
                            onDeleteFingerUpListener?.invoke()
                        } else {
                            deleteClickListener?.onClick()
                        }
                        true
                    }

                    else -> false
                }
            }
            setOnClickListener(null)
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

    /**
     * 動的にテーマカラーを適用するメソッド
     */
    fun setKeyboardTheme(
        @ColorInt backgroundColor: Int,
        @ColorInt iconColor: Int,
        @ColorInt selectedIconColor: Int,
        @ColorInt keyBackgroundColor: Int,
        liquidGlassEnable: Boolean,
    ) {
        this.themeBackgroundColor = backgroundColor
        this.themeIconColor = iconColor
        this.themeSelectedIconColor = selectedIconColor
        this.themeKeyBackgroundColor = keyBackgroundColor
        this.isCustomThemeApplied = true
        this.liquidGlassEnable = liquidGlassEnable

        // 1. 全体の背景色
        if (liquidGlassEnable){
            this.setBackgroundColor(ColorUtils.setAlphaComponent(backgroundColor, 0))
        }else{
            this.setBackgroundColor(backgroundColor)
        }

        // 2. ColorStateList の作成
        val states = arrayOf(
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(-android.R.attr.state_selected)
        )
        val colors = intArrayOf(
            selectedIconColor,
            iconColor
        )
        val tabColorStateList = ColorStateList(states, colors)
        val bgTintList = ColorStateList.valueOf(backgroundColor)

        // 3. Category Tab の全体設定
        categoryTab.backgroundTintList = bgTintList

        categoryTab.tabIconTint = tabColorStateList
        categoryTab.setTabTextColors(iconColor, selectedIconColor)
        categoryTab.setSelectedTabIndicatorColor(Color.TRANSPARENT)
        categoryTab.tabRippleColor = null // リップル削除

        // ★重要: ニューモーフィズムの影が切れないようにクリッピングを無効化
        disableClipping(categoryTab)

        // ★重要: タブの生成完了を待ってから背景を適用 (postを使用)
        categoryTab.post {
            applyThemeToTabs(categoryTab, backgroundColor)
        }

        // 4. Mode Tab (Bottom Bar) の全体設定
        modeTab.backgroundTintList = bgTintList
        modeTab.tabIconTint = tabColorStateList
        modeTab.setSelectedTabIndicatorColor(Color.TRANSPARENT)
        modeTab.tabRippleColor = null

        // ★重要: クリッピング無効化と遅延適用
        disableClipping(modeTab)
        modeTab.post {
            applyThemeToTabs(modeTab, backgroundColor)
        }

        // 5. 機能キー (Return/Delete) のニューモーフィズム設定
        val keyRadius = dpToPx(25).toFloat()
        returnButton.background = getTabNeumorphDrawable(keyBackgroundColor, keyRadius)
        deleteButton.background = getTabNeumorphDrawable(keyBackgroundColor, keyRadius)

        val p = dpToPx(8)
        returnButton.setPadding(p, p, p, p)
        deleteButton.setPadding(p, p, p, p)

        returnButton.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN)
        deleteButton.setColorFilter(iconColor, PorterDuff.Mode.SRC_IN)

        if (currentMode == SymbolMode.CLIPBOARD) {
            buildCategoryTabs()
        }

        symbolAdapter.setThemeColors(
            textColor = iconColor,
            highlightColor = selectedIconColor
        )
    }

    /**
     * TabLayoutとその内部のSlidingTabStripのクリッピングを無効にする
     * これにより、領域外の「影」が描画されるようになります
     */
    private fun disableClipping(tabLayout: TabLayout) {
        tabLayout.clipChildren = false
        tabLayout.clipToPadding = false
        val slidingTabStrip = tabLayout.getChildAt(0) as? ViewGroup
        slidingTabStrip?.clipChildren = false
        slidingTabStrip?.clipToPadding = false
    }

    /**
     * TabLayout内のすべてのタブViewに対して、ニューモーフィズム背景とマージンを適用する
     */
    private fun applyThemeToTabs(tabLayout: TabLayout, @ColorInt baseColor: Int) {
        val slidingTabStrip = tabLayout.getChildAt(0) as? ViewGroup ?: return

        for (i in 0 until slidingTabStrip.childCount) {
            val tabView = slidingTabStrip.getChildAt(i)

            // マージンを設定 (タブ間に隙間を作り、影を表示させるスペースを確保)
            val params = tabView.layoutParams as? ViewGroup.MarginLayoutParams
            if (params != null) {
                val m = dpToPx(4)
                params.setMargins(m, m, m, m)
                // 必要に応じてサイズ指定を解除（MATCH_PARENTなどで潰れないように）
                // params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                tabView.layoutParams = params
            }

            // 背景を設定
            val radius = dpToPx(8).toFloat()
            tabView.background = getTabNeumorphDrawable(baseColor, radius)

            // パディング調整 (View内部のアイコン/テキストが影に重ならないように)
            val p = dpToPx(4)
            tabView.setPadding(p, p, p, p)

            // 再描画要求
            tabView.invalidate()
        }
        tabLayout.requestLayout()
    }

    /**
     * タブ用のニューモーフィズムDrawableを生成する
     * state_selected (選択中) の時に「凹む」ように設定
     */
    private fun getTabNeumorphDrawable(@ColorInt baseColor: Int, radius: Float): Drawable {
        // ハイライトとシャドウの色計算
        val highlightColor = ColorUtils.blendARGB(baseColor, Color.WHITE, 0.6f)
        val shadowColor = ColorUtils.blendARGB(baseColor, Color.BLACK, 0.2f)

        val distance = dpToPx(3)

        // --- A. 通常時 (Unselected / Extruded) ---
        val shadowDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(shadowColor)
        }
        val highlightDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(highlightColor)
        }
        val surfaceDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(baseColor)
        }

        // LayerDrawableで重ねる (Shadow -> Highlight -> Surface)
        val idleLayer = LayerDrawable(arrayOf(shadowDrawable, highlightDrawable, surfaceDrawable))
        // Shadow: 右下
        idleLayer.setLayerInset(0, distance, distance, 0, 0)
        // Highlight: 左上
        idleLayer.setLayerInset(1, 0, 0, distance, distance)
        // Surface: 中央
        idleLayer.setLayerInset(2, distance, distance, distance, distance)


        // --- B. 選択時・押下時 (Selected / Pressed / Pressed In) ---
        // ベースより少し暗くして凹みを表現
        val pressedSurface = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(ColorUtils.blendARGB(baseColor, Color.BLACK, 0.1f))
        }
        val pressedLayer = LayerDrawable(arrayOf(pressedSurface))
        // 凹んでいるので、Surfaceの位置はそのままか、少しずらす
        pressedLayer.setLayerInset(0, distance, distance, distance, distance)


        // --- C. StateListDrawable ---
        val stateList = StateListDrawable()

        // 選択中 (TabLayoutのタブ用)
        stateList.addState(intArrayOf(android.R.attr.state_selected), pressedLayer)
        // 押下中 (通常のボタン用)
        stateList.addState(intArrayOf(android.R.attr.state_pressed), pressedLayer)
        // 通常
        stateList.addState(intArrayOf(), idleLayer)

        return stateList
    }

    fun setOnDeleteButtonFingerUpListener(listener: () -> Unit) {
        onDeleteFingerUpListener = listener
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

    fun setOnImageItemClickListener(l: ImageItemClickListener) {
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

    fun setClipboardHistoryEnabled(isEnabled: Boolean) {
        this.isClipboardHistoryEnabled = isEnabled
        if (currentMode == SymbolMode.CLIPBOARD) {
            categoryTab.getTabAt(0)?.customView?.let {
                val switch = it.findViewById<SwitchMaterial>(R.id.clipboard_tab_switch)
                switch?.isChecked = isEnabled
            }
        }
    }

    fun setOnClipboardHistoryToggleListener(l: ClipboardHistoryToggleListener) {
        this.clipboardHistoryToggleListener = l
    }

    fun setSymbolLists(
        emojiList: List<Emoji>,
        emoticons: List<Emoticon>,
        symbols: List<Symbol>,
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
        historyEmoticonList = symbolsHistory
            .filter { it.mode == SymbolMode.EMOTICON }
            .map { it.symbol }
            .toMutableList()
        historySymbolList = symbolsHistory
            .filter { it.mode == SymbolMode.SYMBOL }
            .map { it.symbol }
            .toMutableList()

        this.emoticonMap = emoticons
            .groupBy { it.category }
            .mapValues { entry -> entry.value.map { it.symbol } }
        this.symbolMap = symbols
            .groupBy { it.category }
            .mapValues { entry -> entry.value.map { it.symbol } }
        this.emojiMap = emojiList
            .groupBy { it.category }
            .toSortedMap(categoryOrder)

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

        // ★ テーマ適用フラグが立っている場合、タブ再構築後にテーマを適用
        if (isCustomThemeApplied) {
            // postを使って描画後に適用
            modeTab.post {
                applyThemeToTabs(modeTab, themeBackgroundColor)
            }
        }
    }

    private fun buildCategoryTabs() {
        categoryTab.removeAllTabs()
        val historyIcon = com.kazumaproject.core.R.drawable.history_24dp

        val normalColor = themeIconColor
        val selectedColor = themeSelectedIconColor

        categoryTab.setTabTextColors(normalColor, selectedColor)
        categoryTab.setSelectedTabIndicatorColor(if (isCustomThemeApplied) Color.TRANSPARENT else selectedColor)

        val states = arrayOf(
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(-android.R.attr.state_selected)
        )
        val colors = intArrayOf(
            selectedColor,
            normalColor
        )
        val tabColorStateList = ColorStateList(states, colors)
        categoryTab.tabIconTint = tabColorStateList

        when (currentMode) {
            SymbolMode.EMOJI -> {
                if (historyEmojiList.isNotEmpty()) {
                    categoryTab.addTab(categoryTab.newTab().setIcon(historyIcon))
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
                if (historyEmoticonList.isNotEmpty()) {
                    categoryTab.addTab(categoryTab.newTab().setIcon(historyIcon))
                }
                val orderedKeys = EmoticonCategory.entries
                orderedKeys.forEach { category ->
                    if (emoticonMap.containsKey(category)) {
                        val tabText = when (category) {
                            EmoticonCategory.SMILE -> "笑顔"
                            EmoticonCategory.SWEAT -> "焦っている顔"
                            EmoticonCategory.SURPRISE -> "驚いている顔"
                            EmoticonCategory.SADNESS -> "泣いている顔"
                            EmoticonCategory.DISPLEASURE -> "不満げな顔"
                            EmoticonCategory.UNKNOWN -> "その他"
                        }
                        categoryTab.addTab(categoryTab.newTab().setText(tabText))
                    }
                }
            }

            SymbolMode.SYMBOL -> {
                if (historySymbolList.isNotEmpty()) {
                    categoryTab.addTab(categoryTab.newTab().setIcon(historyIcon))
                }
                val orderedKeys = SymbolCategory.entries
                orderedKeys.forEach { category ->
                    if (symbolMap.containsKey(category)) {
                        val tabText = when (category) {
                            SymbolCategory.BRACKETS_AND_QUOTES -> "括弧と引用符"
                            SymbolCategory.PUNCTUATION_AND_DIACRITICS -> "区切り文字と発音区別符号"
                            SymbolCategory.Hankaku -> "半角"
                            SymbolCategory.GENERAL -> "全般"
                            SymbolCategory.ARROWS -> "矢印"
                            SymbolCategory.MATH_AND_UNITS -> "数学と単位"
                            SymbolCategory.GEOMETRIC_SHAPES -> "図形"
                            SymbolCategory.ALPHABET_LATIN -> "ラテン文字"
                            SymbolCategory.ALPHABET_GREEK -> "ギリシャ文字"
                            SymbolCategory.ALPHABET_CYRILLIC -> "キリル文字"
                            SymbolCategory.BOX_DRAWING -> "罫線"
                            SymbolCategory.PICTOGRAPHS_AND_ICONS -> "アイコン"
                            SymbolCategory.ROMAN_NUMERALS -> "ローマ数字"
                            SymbolCategory.ENCLOSED_CHARACTERS -> "囲み文字"
                            SymbolCategory.PHONETIC_SYMBOLS -> "発音記号"
                            SymbolCategory.JAPANESE_KANA_AND_VARIANTS -> "日本語仮名・特殊文字"
                            SymbolCategory.CJK_AND_RADICALS -> "CJK・部首"
                            SymbolCategory.CONTROL_CHARACTERS -> "制御文字"
                        }
                        categoryTab.addTab(categoryTab.newTab().setText(tabText))
                    }
                }
            }

            SymbolMode.CLIPBOARD -> {
                val tab = categoryTab.newTab().setCustomView(R.layout.custom_tab_clipboard)
                categoryTab.addTab(tab)
                tab.customView?.let { customView ->
                    val switch = customView.findViewById<SwitchMaterial>(R.id.clipboard_tab_switch)
                    switch.isChecked = isClipboardHistoryEnabled
                    switch.setOnCheckedChangeListener { _, isChecked ->
                        isClipboardHistoryEnabled = isChecked
                        clipboardHistoryToggleListener?.onToggled(isChecked)
                    }
                    customView.findViewById<TextView>(R.id.clipboard_tab_text)
                        .setTextColor(normalColor)
                }
            }
        }

        // ★ テーマ適用フラグが立っている場合、タブ再構築後にテーマを適用
        if (isCustomThemeApplied) {
            // postを使って描画後に適用
            categoryTab.post {
                applyThemeToTabs(categoryTab, themeBackgroundColor)
            }
        }
    }

    private fun updateSymbolsForCategory(index: Int) {
        pagingJob?.cancel()
        lifecycleOwner?.let { owner ->
            pagingJob = owner.lifecycleScope.launch {
                symbolAdapter.submitData(PagingData.empty())
                clipboardAdapter.submitData(PagingData.empty())
                recycler.scrollToPosition(0)

                when (currentMode) {
                    SymbolMode.CLIPBOARD -> {
                        recycler.adapter = clipboardAdapter
                        gridLM.spanCount =
                            if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 2 else 4
                        gridLM.orientation = RecyclerView.VERTICAL
                        Pager(
                            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                            pagingSourceFactory = { ClipboardPagingSource(clipBoardItems) }
                        ).flow.collectLatest { clipboardAdapter.submitData(it) }
                    }

                    else -> {
                        recycler.adapter = symbolAdapter
                        val listForPaging = when (currentMode) {
                            SymbolMode.EMOJI -> {
                                val hasHistory = historyEmojiList.isNotEmpty()
                                if (hasHistory && index == 0) historyEmojiList
                                else {
                                    val adj = index - if (hasHistory) 1 else 0
                                    emojiMap.keys.elementAtOrNull(adj)
                                        ?.let { emojiMap[it]?.map { e -> e.symbol } } ?: emptyList()
                                }
                            }

                            SymbolMode.EMOTICON -> {
                                val hasHistory = historyEmoticonList.isNotEmpty()
                                if (hasHistory && index == 0) historyEmoticonList
                                else {
                                    val adj = index - if (hasHistory) 1 else 0
                                    val orderedKeys = EmoticonCategory.entries
                                        .filter { emoticonMap.containsKey(it) }
                                    orderedKeys.elementAtOrNull(adj)?.let { emoticonMap[it] }
                                        ?: emptyList()
                                }
                            }

                            SymbolMode.SYMBOL -> {
                                val hasHistory = historySymbolList.isNotEmpty()
                                if (hasHistory && index == 0) historySymbolList
                                else {
                                    val adj = index - if (hasHistory) 1 else 0
                                    val orderedKeys = SymbolCategory.entries
                                        .filter { symbolMap.containsKey(it) }
                                    orderedKeys.elementAtOrNull(adj)?.let { symbolMap[it] }
                                        ?: emptyList()
                                }
                            }

                            else -> emptyList()
                        }

                        when (currentMode) {
                            SymbolMode.EMOTICON -> symbolAdapter.setItemMargins(10, 8, context)
                            SymbolMode.SYMBOL -> symbolAdapter.setItemMargins(14, 8, context)
                            else -> symbolAdapter.setItemMargins(4, 3, context)
                        }

                        symbolAdapter.symbolTextSize = when (currentMode) {
                            SymbolMode.EMOJI -> {
                                if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) 36f else 30f
                            }

                            SymbolMode.EMOTICON -> 14f
                            SymbolMode.SYMBOL -> 13f
                            SymbolMode.CLIPBOARD -> 16f
                        }

                        gridLM.spanCount = when (currentMode) {
                            SymbolMode.EMOJI -> 7
                            SymbolMode.EMOTICON -> 3
                            SymbolMode.SYMBOL -> 5
                            else -> 5
                        }
                        gridLM.orientation = RecyclerView.VERTICAL

                        Pager(
                            config = PagingConfig(pageSize = 100, enablePlaceholders = false),
                            pagingSourceFactory = { SymbolPagingSource(listForPaging) }
                        ).flow.collectLatest { symbolAdapter.submitData(it) }
                    }
                }
            }
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

    fun release() {
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

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
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
