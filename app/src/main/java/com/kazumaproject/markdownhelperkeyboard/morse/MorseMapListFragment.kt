package com.kazumaproject.markdownhelperkeyboard.morse

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Gravity
import android.view.MotionEvent
import android.widget.ImageButton
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.color.MaterialColors
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.morse_keyboard.MorseMap

class MorseMapListFragment : Fragment() {
    private lateinit var list: RecyclerView
    private val maps = mutableListOf<MorseMap>()
    private lateinit var mapAdapter: MapAdapter
    private lateinit var drag: ItemTouchHelper
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private val store get() = MorsePreferences(requireContext())
    private var exportJson: String? = null
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val json = requireContext().contentResolver.openInputStream(uri)!!.use {
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val read = it.read(buffer)
                    if (read < 0) break
                    require(output.size() + read <= 1_048_576)
                    output.write(buffer, 0, read)
                }
                output.toString("UTF-8")
            }
            store.importMaps(json)
            Toast.makeText(requireContext(), R.string.morse_imported, Toast.LENGTH_SHORT).show()
            render()
        }.onFailure { message(R.string.morse_invalid) }
    }
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = exportJson
        if (uri != null && json != null) runCatching {
            requireContext().contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(json) }
        }.onFailure { message(R.string.morse_invalid) }
        exportJson = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exportJson = savedInstanceState?.getString("export")
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); outState.putString("export", exportJson)
    }
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), 0) }
        body.addView(TextView(context).apply { setText(R.string.morse_map_help); textSize = 14f }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        body.addView(MaterialButton(context).apply {
            setText(R.string.morse_new_map); isAllCaps = false
            setOnClickListener {
                val all = store.allMaps()
                AlertDialog.Builder(context).setTitle(R.string.morse_new_map)
                    .setItems((listOf(getString(R.string.morse_empty_map)) + all.map { it.name }).toTypedArray()) { _, index ->
                        edit(null, all.getOrNull(index - 1)?.id)
                    }.show()
            }
        }, LinearLayout.LayoutParams(-1, -2))
        val files = LinearLayout(context)
        fun fileAction(title: Int, action: () -> Unit) {
            files.addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                setText(title); isAllCaps = false; setOnClickListener { action() }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(4), 0, dp(4), dp(8)) })
        }
        fileAction(R.string.morse_import) { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        fileAction(R.string.morse_export) {
            exportJson = MorseMapCodec.encode(store.allMaps()); exportFile.launch("morse-maps.json")
        }
        body.addView(files)
        mapAdapter = MapAdapter()
        list = RecyclerView(context).apply { layoutManager = LinearLayoutManager(context); adapter = mapAdapter }
        drag = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(view: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = source.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                maps.add(to, maps.removeAt(from))
                mapAdapter.notifyItemMoved(from, to)
                store.saveOrder(maps.map { it.id }, store.enabledIds())
                return true
            }
            override fun clearView(view: RecyclerView, holder: RecyclerView.ViewHolder) {
                super.clearView(view, holder)
                mapAdapter.notifyDataSetChanged()
            }
            override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit
        }).also { it.attachToRecyclerView(list) }
        body.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        return body
    }
    override fun onResume() { super.onResume(); if (::mapAdapter.isInitialized) render() }
    private fun edit(id: String?, base: String? = null) {
        findNavController().navigate(R.id.morseMapEditorFragment, bundleOf("mapId" to id.orEmpty(), "baseId" to base.orEmpty()))
    }
    private fun render() {
        maps.clear(); maps += store.allMaps(); mapAdapter.notifyDataSetChanged()
    }
    private inner class MapHolder(val card: MaterialCardView) : RecyclerView.ViewHolder(card) {
        val handle = ImageButton(requireContext()).apply {
            setImageResource(com.kazumaproject.core.R.drawable.ic_drag_handle)
            contentDescription = getString(R.string.morse_reorder_map)
            background = null
            imageTintList = android.content.res.ColorStateList.valueOf(MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnSurface))
        }
        val title = TextView(requireContext()).apply { textSize = 17f; setTextColor(MaterialColors.getColor(card, com.google.android.material.R.attr.colorOnSurface)) }
        val detail = TextView(requireContext()).apply { textSize = 12f; setPadding(0, dp(4), 0, 0) }
        val toggle = MaterialSwitch(requireContext())
        val menu = ImageButton(requireContext()).apply {
            setImageResource(com.kazumaproject.core.R.drawable.more_vert_24px)
            contentDescription = getString(R.string.morse_map_actions); background = null
            imageTintList = handle.imageTintList
        }
        init {
            val row = LinearLayout(requireContext()).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(12), 0, dp(12)) }
            val labels = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; addView(title); addView(detail) }
            row.addView(handle, LinearLayout.LayoutParams(dp(48), dp(56)))
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(toggle, LinearLayout.LayoutParams(dp(56), dp(48)))
            row.addView(menu, LinearLayout.LayoutParams(dp(48), dp(48)))
            card.addView(row)
            handle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) drag.startDrag(this)
                true
            }
            handle.setOnClickListener { menu.performClick() }
        }
    }
    private inner class MapAdapter : RecyclerView.Adapter<MapHolder>() {
        override fun getItemCount() = maps.size
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): MapHolder {
            val card = MaterialCardView(requireContext()).apply {
                radius = dp(16).toFloat(); cardElevation = 0f
                strokeWidth = dp(1); strokeColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant)
                layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
            }
            return MapHolder(card)
        }
        override fun onBindViewHolder(holder: MapHolder, position: Int) {
            val map = maps[position]
            holder.title.text = map.name
            holder.detail.text = getString(R.string.morse_map_detail, position + 1,
                getString(if (map.builtIn) R.string.morse_map_builtin else R.string.morse_map_custom), map.entries.size)
            holder.toggle.setOnCheckedChangeListener(null)
            holder.toggle.isChecked = map.id in store.enabledIds()
            holder.toggle.contentDescription = getString(R.string.morse_enable_map, map.name)
            holder.toggle.setOnCheckedChangeListener { _, checked ->
                val next = if (checked) store.enabledIds() + map.id else store.enabledIds() - map.id
                if (next.isEmpty()) { message(R.string.morse_at_least_one); holder.toggle.isChecked = true }
                else store.saveOrder(maps.map { it.id }, next)
            }
            holder.card.setOnClickListener { if (map.builtIn) edit(null, map.id) else edit(map.id) }
            holder.menu.setOnClickListener {
                val index = maps.indexOfFirst { it.id == map.id }
                val popup = PopupMenu(requireContext(), holder.menu)
                popup.menu.add(0, 0, 0, if (map.builtIn) R.string.morse_copy else R.string.morse_edit)
                if (!map.builtIn) popup.menu.add(0, 1, 1, R.string.morse_copy)
                popup.menu.add(0, 2, 2, R.string.morse_move_map_up).isEnabled = index > 0
                popup.menu.add(0, 3, 3, R.string.morse_move_map_down).isEnabled = index < maps.lastIndex
                if (!map.builtIn) popup.menu.add(0, 4, 4, R.string.morse_delete)
                popup.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        0 -> if (map.builtIn) edit(null, map.id) else edit(map.id)
                        1 -> edit(null, map.id)
                        2, 3 -> {
                            val target = index + if (item.itemId == 2) -1 else 1
                            if (target in maps.indices) {
                                maps.add(target, maps.removeAt(index))
                                store.saveOrder(maps.map { it.id }, store.enabledIds()); mapAdapter.notifyDataSetChanged()
                            }
                        }
                        4 -> AlertDialog.Builder(requireContext()).setMessage(R.string.morse_delete_confirm)
                            .setNegativeButton(android.R.string.cancel, null)
                            .setPositiveButton(R.string.morse_delete) { _, _ -> store.deleteMap(map.id); render() }.show()
                    }
                    true
                }
                popup.show()
            }
        }
    }
    override fun onDestroyView() {
        drag.attachToRecyclerView(null); list.adapter = null
        super.onDestroyView()
    }
    private fun message(text: Int) = Toast.makeText(requireContext(), text, Toast.LENGTH_LONG).show()
}
