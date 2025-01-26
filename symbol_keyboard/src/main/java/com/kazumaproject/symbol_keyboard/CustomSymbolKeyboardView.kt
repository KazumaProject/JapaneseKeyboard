package com.kazumaproject.symbol_keyboard

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.tabs.TabLayout
import com.kazumaproject.listeners.DeleteButtonSymbolViewClickListener
import com.kazumaproject.listeners.DeleteButtonSymbolViewLongClickListener
import com.kazumaproject.listeners.ReturnToTenKeyButtonClickListener
import com.kazumaproject.listeners.SymbolRecyclerViewItemClickListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class CustomSymbolKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

    private lateinit var symbolAdapter: SymbolAdapter
    private var returnToTenKeyButtonClickListener: ReturnToTenKeyButtonClickListener? = null
    private var deleteButtonSymbolViewClickListener: DeleteButtonSymbolViewClickListener? = null
    private var deleteButtonSymbolViewLongClickListener: DeleteButtonSymbolViewLongClickListener? =
        null
    private var symbolRecyclerViewItemClickListener: SymbolRecyclerViewItemClickListener? = null

    private var moodSymbols: List<String> = listOf()
    private var emoticonSymbols: List<String> = listOf()
    private var starSymbols: List<String> = listOf()

    private var symbolLoadJob: Job? = null
    private var lifecycleOwner: LifecycleOwner? = null

    private lateinit var symbolRecyclerView: RecyclerView
    private lateinit var tabLayout: TabLayout

    init {
        inflate(context, R.layout.symbol_keyboard_main_layout, this)
        setUpSymbolRecyclerView()
        setupTabs()
        setUpButtons()
    }

    fun setOnReturnToTenKeyButtonClickListener(returnToTenKeyButtonClickListener: ReturnToTenKeyButtonClickListener) {
        this.returnToTenKeyButtonClickListener = returnToTenKeyButtonClickListener
    }

    fun setOnDeleteButtonSymbolViewClickListener(deleteButtonSymbolViewClickListener: DeleteButtonSymbolViewClickListener) {
        this.deleteButtonSymbolViewClickListener = deleteButtonSymbolViewClickListener
    }

    fun setOnDeleteButtonSymbolViewLongClickListener(deleteButtonSymbolViewLongClickListener: DeleteButtonSymbolViewLongClickListener) {
        this.deleteButtonSymbolViewLongClickListener = deleteButtonSymbolViewLongClickListener
    }

    fun setOnSymbolRecyclerViewItemClickListener(symbolRecyclerViewItemClickListener: SymbolRecyclerViewItemClickListener) {
        this.symbolRecyclerViewItemClickListener = symbolRecyclerViewItemClickListener
    }

    fun setLifecycleOwner(owner: LifecycleOwner) {
        this.lifecycleOwner = owner
    }

    fun getTabPosition(): Int {
        return tabLayout.selectedTabPosition
    }

    fun setTabPosition(tabPosition: Int) {
        tabLayout.getTabAt(tabPosition).apply {
            tabLayout.selectTab(this)
        }
        symbolRecyclerView.scrollToPosition(0)
    }

    fun setSymbolLists(
        moodSymbols: List<String>,
        emoticonSymbols: List<String>,
        starSymbols: List<String>,
        tabPosition: Int
    ) {
        this.moodSymbols = moodSymbols
        this.emoticonSymbols = emoticonSymbols
        this.starSymbols = starSymbols
        updateSymbolsForTab(tabPosition)
    }

    private fun setupTabs() {
        tabLayout = findViewById(R.id.bottom_tab_layout)

        addCustomTab(tabLayout, R.drawable.mood_24px)
        addCustomTab(tabLayout, R.drawable.emoticon_24px)
        addCustomTab(tabLayout, R.drawable.star_24px)

        tabLayout.getTabAt(0)?.let {
            tabLayout.selectTab(it)
            updateSymbolsForTab(0)
            setTabItemIconTintSelectedColor(tabLayout, 0)
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let {
                    val position = it.position
                    updateSymbolsForTab(position)
                    val iconView = it.customView?.findViewById<ImageView>(R.id.tab_icon)
                    iconView?.setColorFilter(
                        ContextCompat.getColor(context, R.color.tab_selected),
                        android.graphics.PorterDuff.Mode.SRC_IN
                    )
                    iconView?.isSelected = true
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {
                tab?.let {
                    val iconView = it.customView?.findViewById<ImageView>(R.id.tab_icon)
                    iconView?.setColorFilter(
                        ContextCompat.getColor(context, R.color.tab_unselected),
                        android.graphics.PorterDuff.Mode.SRC_IN
                    )
                    iconView?.isSelected = false
                }
            }

            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setTabItemIconTintSelectedColor(tabLayout: TabLayout, tabPosition: Int) {
        tabLayout.getTabAt(tabPosition)?.let {
            tabLayout.getTabAt(tabPosition)?.customView?.findViewById<ImageView>(R.id.tab_icon)
                ?.setColorFilter(
                    ContextCompat.getColor(context, R.color.tab_selected),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
        }
    }

    private fun addCustomTab(tabLayout: TabLayout, iconResId: Int) {
        val customView =
            LayoutInflater.from(context).inflate(R.layout.custom_tab_icon, tabLayout, false)
        val tabIcon = customView.findViewById<ImageView>(R.id.tab_icon)

        tabIcon.setImageResource(iconResId)
        val tab = tabLayout.newTab()
        tab.customView = customView
        tabLayout.addTab(tab)
    }

    private lateinit var gridLayoutManager: GridLayoutManager

    private fun setUpSymbolRecyclerView() {
        symbolRecyclerView = findViewById(R.id.symbol_candidate_recycler_view)
        gridLayoutManager = GridLayoutManager(
            context,
            5,
            GridLayoutManager.HORIZONTAL,
            false
        )
        symbolAdapter = SymbolAdapter()
        symbolRecyclerView.apply {
            adapter = symbolAdapter
            layoutManager = gridLayoutManager
            setHasFixedSize(true)
            itemAnimator = null
        }
        symbolAdapter.setOnItemClickListener { symbol ->
            symbolRecyclerViewItemClickListener?.onClick(symbol)
        }
    }

    private fun updateSymbolsForTab(tabPosition: Int) {
        symbolRecyclerView.scrollToPosition(0)
        val selectedSymbols = when (tabPosition) {
            0 -> moodSymbols
            1 -> emoticonSymbols
            2 -> starSymbols
            else -> listOf()
        }

        symbolLoadJob?.cancel()

        val textSize = when (tabPosition) {
            0 -> 58f
            1 -> 28f
            2 -> 42f
            else -> 42f
        }
        val newOrientation =
            if (context.applicationContext.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL
        gridLayoutManager.apply {
            orientation = newOrientation
            requestLayout()
        }
        symbolAdapter.symbolTextSize = textSize
        val owner = lifecycleOwner
        if (owner != null) {
            symbolLoadJob = owner.lifecycleScope.launch {
                getSymbolsPagingData(selectedSymbols).collectLatest { pagingData ->
                    symbolAdapter.submitData(pagingData)
                }
            }
        } else {
            Log.e("CustomSymbolKeyboardView", "LifecycleOwner is null. Cannot load data.")
        }
    }

    private fun getSymbolsPagingData(symbols: List<String>) =
        Pager(
            config = PagingConfig(
                pageSize = 50,
                enablePlaceholders = false,
                prefetchDistance = 10
            ),
            pagingSourceFactory = { SymbolPagingSource(symbols) }
        ).flow

    private fun setUpButtons() {
        val returnToTenKeyButton = findViewById<ShapeableImageView>(R.id.return_jp_keyboard_button)
        val deleteButton = findViewById<ShapeableImageView>(R.id.symbol_keyboard_delete_key)
        returnToTenKeyButton.setOnClickListener {
            returnToTenKeyButtonClickListener?.onClick()
        }
        deleteButton.setOnLongClickListener {
            deleteButtonSymbolViewLongClickListener?.onLongClickListener()
            false
        }
        deleteButton.setOnClickListener {
            deleteButtonSymbolViewClickListener?.onClick()
        }
    }
}
