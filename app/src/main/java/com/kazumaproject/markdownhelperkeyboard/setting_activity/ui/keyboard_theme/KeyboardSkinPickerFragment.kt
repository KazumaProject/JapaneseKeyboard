package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.keyboard_theme

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.kazumaproject.core.data.keyboard.ImportedKeyboardSkinDefinition
import com.kazumaproject.core.data.keyboard.KeyboardSkinId
import com.kazumaproject.core.data.keyboard.KeyboardSkinJsonParser
import com.kazumaproject.core.data.keyboard.KeyboardSkinMotionMode
import com.kazumaproject.core.data.keyboard.KeyboardSkinParseResult
import com.kazumaproject.core.data.keyboard.KeyboardSkinPreviewView
import com.kazumaproject.core.data.keyboard.KeyboardSkinRef
import com.kazumaproject.core.data.keyboard.KeyboardSkinRuntime
import com.kazumaproject.core.data.keyboard.KeyboardSkinStore
import com.kazumaproject.core.data.keyboard.KeyboardSkinValidationError
import com.kazumaproject.core.data.keyboard.StoreWriteResult
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class KeyboardSkinPickerFragment : Fragment(R.layout.fragment_keyboard_skin_picker) {

    @Inject
    lateinit var appPreference: AppPreference

    private lateinit var adapter: SkinAdapter
    private lateinit var store: KeyboardSkinStore

    private val openSkinDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importUri(uri)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        store = KeyboardSkinStore.fromContext(requireContext().applicationContext)
        val selectedSkin = KeyboardSkinRef.fromPreference(appPreference.keyboard_skin)
        val motionMode = KeyboardSkinMotionMode.fromPreference(appPreference.keyboard_skin_motion)
        adapter = SkinAdapter(selectedSkin, motionMode) { skin ->
            appPreference.keyboard_skin = skin.preferenceValue
        }
        view.findViewById<MaterialButton>(R.id.keyboard_skin_import_button).setOnClickListener {
            openSkinDocument.launch(arrayOf("application/json", "text/json", "text/plain"))
        }
        view.findViewById<RecyclerView>(R.id.keyboard_skin_grid).apply {
            layoutManager = GridLayoutManager(requireContext(), columnCount())
            adapter = this@KeyboardSkinPickerFragment.adapter
            itemAnimator = null
            setHasFixedSize(true)
        }
        val motionGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.keyboard_skin_motion_group)
        motionGroup.check(buttonIdFor(motionMode))
        motionGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val selectedMotion = motionForButtonId(checkedId)
            appPreference.keyboard_skin_motion = selectedMotion.preferenceValue
            adapter.updateMotion(selectedMotion)
        }
        loadImportedSkins()
    }

    private fun loadImportedSkins() {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val imported = withContext(Dispatchers.IO) {
                val stored = KeyboardSkinStore.fromContext(appContext).list()
                KeyboardSkinRuntime.replace(stored.map { it.definition })
                stored.map { it.definition }
            }
            adapter.setImported(imported)
        }
    }

    private fun importUri(uri: Uri) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = appContext.contentResolver.openInputStream(uri)?.use(::readLimited)
                        ?: return@runCatching KeyboardSkinParseResult.Failure(
                            listOf(KeyboardSkinValidationError("$", "ファイルを開けません")),
                        )
                    KeyboardSkinJsonParser.parse(bytes)
                }.getOrElse {
                    KeyboardSkinParseResult.Failure(
                        listOf(KeyboardSkinValidationError("$", it.message ?: "ファイルを読み込めません")),
                    )
                }
            }
            val success = result as? KeyboardSkinParseResult.Success
            if (success == null) {
                showValidationError((result as KeyboardSkinParseResult.Failure).errors)
                return@launch
            }
            val duplicate = withContext(Dispatchers.IO) {
                store.fileFor(success.definition.id).exists()
            }
            if (duplicate || success.definition.warnings.isNotEmpty()) {
                showImportConfirmation(success.definition, duplicate)
            } else {
                saveImported(success.definition, replace = false)
            }
        }
    }

    private fun showImportConfirmation(definition: ImportedKeyboardSkinDefinition, duplicate: Boolean) {
        val warningText = definition.warnings.takeIf { it.isNotEmpty() }?.let { warnings ->
            "コントラスト警告：\n" + warnings.joinToString("\n") { "・${it.path}: ${it.message}" }
        }
        val message = buildList {
            add(definition.name)
            if (duplicate) add("同じID（${definition.id}）が存在します。承認するとアプリ内のコピーを更新します。")
            if (warningText != null) add(warningText)
        }.joinToString("\n\n")
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (duplicate) R.string.keyboard_skin_update_confirm_title else R.string.keyboard_skin_warning_confirm_title)
            .setMessage(message)
            .setNegativeButton(R.string.keyboard_skin_cancel, null)
            .setPositiveButton(if (duplicate) R.string.keyboard_skin_update else R.string.keyboard_skin_import) { _, _ ->
                saveImported(definition, replace = duplicate)
            }
            .show()
    }

    private fun saveImported(definition: ImportedKeyboardSkinDefinition, replace: Boolean) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val result = store.save(definition, replace)
                if (result is StoreWriteResult.Saved) {
                    KeyboardSkinRuntime.reloadFromDisk(appContext)
                }
                result
            }
            when (outcome) {
                is StoreWriteResult.Saved -> {
                    appPreference.keyboard_skin = definition.reference.preferenceValue
                    adapter.setImported(KeyboardSkinRuntime.all())
                }
                is StoreWriteResult.Duplicate -> showValidationError(
                    listOf(KeyboardSkinValidationError("id", "同じIDが存在します。更新確認が必要です")),
                )
                is StoreWriteResult.Failure -> showValidationError(
                    listOf(KeyboardSkinValidationError("$", "保存できません: ${outcome.error.message ?: "I/Oエラー"}")),
                )
            }
        }
    }

    private fun confirmDelete(definition: ImportedKeyboardSkinDefinition) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.keyboard_skin_delete_confirm_title)
            .setMessage(getString(R.string.keyboard_skin_delete_confirm_message, definition.name, definition.id))
            .setNegativeButton(R.string.keyboard_skin_cancel, null)
            .setPositiveButton(R.string.keyboard_skin_delete) { _, _ -> deleteImported(definition) }
            .show()
    }

    private fun deleteImported(definition: ImportedKeyboardSkinDefinition) {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val deleted = withContext(Dispatchers.IO) {
                val deleted = store.delete(definition.id)
                if (deleted) {
                    KeyboardSkinRuntime.reloadFromDisk(appContext)
                }
                deleted
            }
            if (!deleted) return@launch
            if (appPreference.keyboard_skin == definition.reference.preferenceValue) {
                appPreference.keyboard_skin = KeyboardSkinId.DEFAULT.preferenceValue
                adapter.selectWithoutCallback(KeyboardSkinRef.DEFAULT)
            }
            adapter.setImported(KeyboardSkinRuntime.all())
        }
    }

    private fun showValidationError(errors: List<KeyboardSkinValidationError>) {
        val message = errors.joinToString("\n") { it.toString() }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.keyboard_skin_import_error)
            .setMessage(message)
            .setNegativeButton(R.string.keyboard_skin_close, null)
            .setPositiveButton(R.string.keyboard_skin_copy_error) { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("keyboard skin error", message))
            }
            .show()
    }

    private fun readLimited(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > KeyboardSkinJsonParser.MAX_UTF8_BYTES) {
                return ByteArray(KeyboardSkinJsonParser.MAX_UTF8_BYTES + 1)
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun buttonIdFor(mode: KeyboardSkinMotionMode): Int = when (mode) {
        KeyboardSkinMotionMode.FULL -> R.id.keyboard_skin_motion_full
        KeyboardSkinMotionMode.REDUCED -> R.id.keyboard_skin_motion_reduced
        KeyboardSkinMotionMode.OFF -> R.id.keyboard_skin_motion_off
    }

    private fun motionForButtonId(buttonId: Int): KeyboardSkinMotionMode = when (buttonId) {
        R.id.keyboard_skin_motion_reduced -> KeyboardSkinMotionMode.REDUCED
        R.id.keyboard_skin_motion_off -> KeyboardSkinMotionMode.OFF
        else -> KeyboardSkinMotionMode.FULL
    }

    private sealed interface SkinCardItem {
        val ref: KeyboardSkinRef

        data class BuiltIn(val id: KeyboardSkinId) : SkinCardItem {
            override val ref: KeyboardSkinRef = KeyboardSkinRef.BuiltIn(id)
        }

        data class Imported(val definition: ImportedKeyboardSkinDefinition) : SkinCardItem {
            override val ref: KeyboardSkinRef.Imported = definition.reference
        }
    }

    private inner class SkinAdapter(
        private var selectedSkin: KeyboardSkinRef,
        private var motionMode: KeyboardSkinMotionMode,
        private val onSelected: (KeyboardSkinRef) -> Unit,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var imported: List<ImportedKeyboardSkinDefinition> = emptyList()
        private val builtIns = KeyboardSkinId.entries.toList()
        private val items: List<SkinCardItem>
            get() = imported.map { SkinCardItem.Imported(it) } + builtIns.map { SkinCardItem.BuiltIn(it) }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            SkinViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_keyboard_skin, parent, false))

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            (holder as SkinViewHolder).bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        fun setImported(value: List<ImportedKeyboardSkinDefinition>) {
            imported = value.sortedWith(compareBy<ImportedKeyboardSkinDefinition> { it.name.lowercase() }.thenBy { it.id })
            notifyDataSetChanged()
        }

        fun updateMotion(value: KeyboardSkinMotionMode) {
            if (motionMode == value) return
            motionMode = value
            notifyItemRangeChanged(0, itemCount)
        }

        fun selectWithoutCallback(ref: KeyboardSkinRef) {
            selectedSkin = ref
            notifyDataSetChanged()
        }

        private fun select(ref: KeyboardSkinRef) {
            if (ref == selectedSkin) return
            selectedSkin = ref
            onSelected(ref)
            notifyDataSetChanged()
        }

        inner class SkinViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val card = itemView.findViewById<MaterialCardView>(R.id.keyboard_skin_card)
            private val preview = itemView.findViewById<KeyboardSkinPreviewView>(R.id.keyboard_skin_preview)
            private val name = itemView.findViewById<TextView>(R.id.keyboard_skin_name)
            private val material = itemView.findViewById<TextView>(R.id.keyboard_skin_material)
            private val badge = itemView.findViewById<View>(R.id.keyboard_skin_selected_badge)
            private val menu = itemView.findViewById<ImageButton>(R.id.keyboard_skin_menu)

            fun bind(item: SkinCardItem) {
                val isSelected = item.ref == selectedSkin
                val displayName: String
                val subtitle: String
                val definition: ImportedKeyboardSkinDefinition?
                when (item) {
                    is SkinCardItem.BuiltIn -> {
                        displayName = getString(nameResource(item.id))
                        subtitle = getString(materialResource(item.id))
                        definition = null
                    }
                    is SkinCardItem.Imported -> {
                        displayName = item.definition.name
                        subtitle = item.definition.author ?: getString(R.string.keyboard_skin_imported)
                        definition = item.definition
                    }
                }
                name.text = displayName
                material.text = subtitle
                badge.isVisible = isSelected
                menu.isVisible = definition != null
                preview.setSkin(item.ref, if (isSelected) motionMode else KeyboardSkinMotionMode.OFF)
                val selectedColor = MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary)
                val outlineColor = MaterialColors.getColor(card, com.google.android.material.R.attr.colorOutline)
                card.strokeColor = if (isSelected) selectedColor else outlineColor
                card.strokeWidth = resources.displayMetrics.density.times(if (isSelected) 3f else 1f).toInt().coerceAtLeast(1)
                card.contentDescription = getString(
                    R.string.keyboard_skin_accessibility_description,
                    displayName,
                    subtitle,
                    if (isSelected) getString(R.string.keyboard_skin_selected) else "",
                )
                card.tag = item.ref.preferenceValue
                card.setOnClickListener { select(item.ref) }
                menu.setOnClickListener { anchor ->
                    val importedDefinition = definition ?: return@setOnClickListener
                    androidx.appcompat.widget.PopupMenu(requireContext(), anchor).apply {
                        menu.add(R.string.keyboard_skin_delete)
                        setOnMenuItemClickListener {
                            confirmDelete(importedDefinition)
                            true
                        }
                    }.show()
                }
            }
        }
    }

    private fun nameResource(skin: KeyboardSkinId): Int = when (skin) {
        KeyboardSkinId.DEFAULT -> R.string.keyboard_skin_default
        KeyboardSkinId.FLAT -> R.string.keyboard_skin_flat
        KeyboardSkinId.GLASS -> R.string.keyboard_skin_glass
        KeyboardSkinId.NEUMORPHISM -> R.string.keyboard_skin_neumorphism
        KeyboardSkinId.MECHANICAL -> R.string.keyboard_skin_mechanical
        KeyboardSkinId.WASHI -> R.string.keyboard_skin_washi
        KeyboardSkinId.NEON -> R.string.keyboard_skin_neon
        KeyboardSkinId.TERMINAL -> R.string.keyboard_skin_terminal
        KeyboardSkinId.CUPERTINO -> R.string.keyboard_skin_cupertino
        KeyboardSkinId.CUPERTINO_DARK -> R.string.keyboard_skin_cupertino_dark
        KeyboardSkinId.SUMI_HANSHI -> R.string.keyboard_skin_sumi_hanshi
        KeyboardSkinId.LETTERPRESS -> R.string.keyboard_skin_letterpress
        KeyboardSkinId.PORCELAIN -> R.string.keyboard_skin_porcelain
        KeyboardSkinId.URUSHI -> R.string.keyboard_skin_urushi
        KeyboardSkinId.CHALKBOARD -> R.string.keyboard_skin_chalkboard
        KeyboardSkinId.LINEN -> R.string.keyboard_skin_linen
        KeyboardSkinId.MONOCHROME_LCD -> R.string.keyboard_skin_monochrome_lcd
    }

    private fun materialResource(skin: KeyboardSkinId): Int = when (skin) {
        KeyboardSkinId.DEFAULT -> R.string.keyboard_skin_material_default
        KeyboardSkinId.FLAT -> R.string.keyboard_skin_material_flat
        KeyboardSkinId.GLASS -> R.string.keyboard_skin_material_glass
        KeyboardSkinId.NEUMORPHISM -> R.string.keyboard_skin_material_neumorphism
        KeyboardSkinId.MECHANICAL -> R.string.keyboard_skin_material_mechanical
        KeyboardSkinId.WASHI -> R.string.keyboard_skin_material_washi
        KeyboardSkinId.NEON -> R.string.keyboard_skin_material_neon
        KeyboardSkinId.TERMINAL -> R.string.keyboard_skin_material_terminal
        KeyboardSkinId.CUPERTINO -> R.string.keyboard_skin_material_cupertino
        KeyboardSkinId.CUPERTINO_DARK -> R.string.keyboard_skin_material_cupertino_dark
        KeyboardSkinId.SUMI_HANSHI -> R.string.keyboard_skin_material_sumi_hanshi
        KeyboardSkinId.LETTERPRESS -> R.string.keyboard_skin_material_letterpress
        KeyboardSkinId.PORCELAIN -> R.string.keyboard_skin_material_porcelain
        KeyboardSkinId.URUSHI -> R.string.keyboard_skin_material_urushi
        KeyboardSkinId.CHALKBOARD -> R.string.keyboard_skin_material_chalkboard
        KeyboardSkinId.LINEN -> R.string.keyboard_skin_material_linen
        KeyboardSkinId.MONOCHROME_LCD -> R.string.keyboard_skin_material_monochrome_lcd
    }

    private fun columnCount(): Int = if (resources.configuration.screenWidthDp >= TABLET_MIN_WIDTH_DP) TABLET_COLUMN_COUNT else PHONE_COLUMN_COUNT

    companion object {
        private const val PHONE_COLUMN_COUNT = 2
        private const val TABLET_COLUMN_COUNT = 3
        private const val TABLET_MIN_WIDTH_DP = 600
    }
}
