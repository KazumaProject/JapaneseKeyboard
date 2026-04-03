package com.kazumaproject.markdownhelperkeyboard.custom_romaji.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.database.MapTypeConverter
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.database.RomajiMapEntity
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.ui.adapter.RomajiMapAdapter
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentRomajiMapBinding
import com.kazumaproject.markdownhelperkeyboard.repository.RomajiMapRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.FileOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class RomajiMapFragment : Fragment() {

    private var _binding: FragmentRomajiMapBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var repository: RomajiMapRepository

    private lateinit var romajiMapAdapter: RomajiMapAdapter
    private val gson = Gson()
    private val mapTypeConverter = MapTypeConverter()

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri -> exportRomajiMaps(uri) }
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri -> importRomajiMaps(uri) }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRomajiMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lifecycleScope.launch {
            repository.createDefaultMapIfNotExist()
        }

        setupRecyclerView()
        setupMenu()
        observeMaps()

        binding.fabAddMap.setOnClickListener {
            showEditDialog(null)
        }
    }

    private fun setupRecyclerView() {
        romajiMapAdapter = RomajiMapAdapter(
            onCardClicked = { map ->
                // Navigate to detail screen if the map is deletable (i.e., not the default)
                if (map.isDeletable) {
                    val action =
                        RomajiMapFragmentDirections.actionRomajiMapFragmentToRomajiMapDetailFragment(
                            map.id
                        )
                    findNavController().navigate(action)
                } else {
                    Toast.makeText(
                        context,
                        getString(R.string.default_map_cannot_edit_text),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onActivateClicked = { map ->
                lifecycleScope.launch { repository.setActiveMap(map.id) }
            },
            onEditClicked = { map ->
                showEditDialog(map)
            },
            onDeleteClicked = { map ->
                showDeleteConfirmationDialog(map)
            }
        )
        binding.romajiMapRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = romajiMapAdapter
        }
    }


    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.romaji_map_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_export_romaji_map -> {
                        launchExportFilePicker()
                        true
                    }

                    R.id.action_import_romaji_map -> {
                        launchImportFilePicker()
                        true
                    }

                    android.R.id.home -> {
                        findNavController().popBackStack()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun observeMaps() {
        lifecycleScope.launch {
            repository.getAllMaps().collect { maps ->
                romajiMapAdapter.submitList(maps)
            }
        }
    }

    private fun showEditDialog(map: RomajiMapEntity?) {
        val dialogView =
            LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_romaji_map, null)
        val editText = dialogView.findViewById<EditText>(R.id.map_name_edit_text)
        map?.let { editText.setText(it.name) }

        AlertDialog.Builder(requireContext())
            .setTitle(
                if (map == null) getString(R.string.create_new_keymap_dialog_title) else getString(
                    R.string.edit_keymap_dialog_title
                )
            )
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save_string)) { _, _ ->
                val name = editText.text.toString()
                if (name.isNotBlank()) {
                    lifecycleScope.launch {
                        if (map == null) {
                            val newMap = RomajiMapEntity(
                                name = name,
                                mapData = repository.getActiveMap().first()?.mapData
                                    ?: emptyMap(), // Copy from current active map
                                isActive = false,
                                isDeletable = true
                            )
                            repository.insert(newMap)
                        } else {
                            repository.update(map.copy(name = name))
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel_string), null)
            .show()
    }

    private fun showDeleteConfirmationDialog(map: RomajiMapEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_keymap_dialog_title))
            .setMessage("「${map.name}」${getString(R.string.confirm_to_delete_text)}")
            .setPositiveButton(getString(R.string.delete_string)) { _, _ ->
                lifecycleScope.launch { repository.delete(map) }
            }
            .setNegativeButton(getString(R.string.cancel_string), null)
            .show()
    }

    private fun launchExportFilePicker() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "romaji_maps_backup.json")
        }
        exportLauncher.launch(intent)
    }

    private fun launchImportFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        importLauncher.launch(intent)
    }

    private fun exportRomajiMaps(uri: Uri) {
        lifecycleScope.launch {
            binding.progressBar.isVisible = true
            val mapsToExport = repository.getAllMaps().first().filter { it.isDeletable }
            if (mapsToExport.isEmpty()) {
                Toast.makeText(
                    context,
                    getString(R.string.no_export_keymap_string),
                    Toast.LENGTH_SHORT
                ).show()
                binding.progressBar.isVisible = false
                return@launch
            }
            try {
                val jsonString = gson.toJson(mapsToExport)
                requireContext().contentResolver.openFileDescriptor(uri, "w")?.use {
                    FileOutputStream(it.fileDescriptor).use { fos ->
                        fos.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                }
                Toast.makeText(
                    context,
                    getString(R.string.success_to_export_string),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    "${getString(R.string.fail_to_export_string)} ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    private fun importRomajiMaps(uri: Uri) {
        lifecycleScope.launch {
            binding.progressBar.isVisible = true
            try {
                val jsonString = requireContext().contentResolver.openInputStream(uri)?.use {
                    it.reader(Charsets.UTF_8).readText()
                }

                if (jsonString != null) {
                    val mapsToInsert = parseImportMaps(jsonString)
                    if (mapsToInsert.isEmpty()) {
                        Timber.w(
                            "Romaji import parsed zero maps. uri=%s, jsonPrefix=%s",
                            uri,
                            jsonString.take(300)
                        )
                        throw IllegalArgumentException("No valid romaji maps in import file")
                    }
                    repository.insertAll(mapsToInsert)
                    Timber.i("Romaji import succeeded. uri=%s, imported=%d", uri, mapsToInsert.size)
                    Toast.makeText(
                        context,
                        "${mapsToInsert.size}${getString(R.string.import_text_string)}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Romaji import failed. uri=%s", uri)
                Toast.makeText(
                    context,
                    getString(R.string.fail_to_import_string),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    private fun parseImportMaps(jsonString: String): List<RomajiMapEntity> {
        val normalized = jsonString.removePrefix("\uFEFF")
        val root = runCatching { JsonParser.parseString(normalized) }.getOrNull() ?: return emptyList()
        val mapObjects = extractMapObjects(root)
        if (mapObjects.isEmpty()) {
            Timber.w("Romaji import: no map objects found in root JSON")
            return emptyList()
        }

        return mapObjects.mapNotNull { obj ->
            val name = (
                obj.readString("name")
                    ?: obj.readString("mapName")
                    ?: obj.readString("title")
                )?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            val mapData = obj.readMapData("mapData")
                .ifEmpty { obj.readMapData("map") }
                .ifEmpty { obj.readMapData("data") }
            if (mapData.isEmpty()) {
                Timber.w("Romaji import: mapData empty for name=%s", name)
                return@mapNotNull null
            }

            RomajiMapEntity(
                id = 0,
                name = name,
                mapData = mapData,
                isActive = false,
                isDeletable = obj.readBoolean("isDeletable") ?: true
            )
        }
    }

    private fun extractMapObjects(root: JsonElement): List<JsonObject> {
        if (root.isJsonArray) {
            return root.asJsonArray.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
        }
        if (!root.isJsonObject) return emptyList()

        val obj = root.asJsonObject
        val arrayKeys = listOf("maps", "romajiMaps", "romaji_maps", "items", "list")
        for (key in arrayKeys) {
            val candidate = obj.get(key)
            if (candidate != null && candidate.isJsonArray) {
                return candidate.asJsonArray.mapNotNull {
                    it.takeIf(JsonElement::isJsonObject)?.asJsonObject
                }
            }
        }

        return listOf(obj)
    }

    private fun JsonObject.readString(key: String): String? {
        val value = get(key) ?: return null
        if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) return null
        return value.asString
    }

    private fun JsonObject.readBoolean(key: String): Boolean? {
        val value = get(key) ?: return null
        if (!value.isJsonPrimitive) return null
        val primitive = value.asJsonPrimitive
        return when {
            primitive.isBoolean -> primitive.asBoolean
            primitive.isString -> primitive.asString.toBooleanStrictOrNull()
            else -> null
        }
    }

    private fun JsonObject.readMapData(key: String): Map<String, Pair<String, Int>> {
        val value = get(key) ?: return emptyMap()
        return when {
            value.isJsonObject -> {
                val parsed = mapTypeConverter.toMap(value.toString())
                if (parsed.isNotEmpty()) parsed else value.asJsonObject.readLegacyCompactMapData()
            }
            value.isJsonPrimitive && value.asJsonPrimitive.isString -> mapTypeConverter.toMap(value.asString)
            else -> emptyMap()
        }
    }

    private fun JsonObject.readLegacyCompactMapData(): Map<String, Pair<String, Int>> {
        val result = linkedMapOf<String, Pair<String, Int>>()
        entrySet().forEach { (romaji, node) ->
            if (romaji.isBlank() || !node.isJsonObject) return@forEach
            val obj = node.asJsonObject

            val kana = obj.readString("c") ?: obj.readString("kana")
            val consume = obj.readNumberAsInt("d") ?: obj.readNumberAsInt("consume") ?: romaji.length

            if (!kana.isNullOrBlank()) {
                result[romaji] = kana to consume.coerceAtLeast(1)
            }
        }
        return result
    }

    private fun JsonObject.readNumberAsInt(key: String): Int? {
        val value = get(key) ?: return null
        if (!value.isJsonPrimitive) return null
        val primitive = value.asJsonPrimitive
        return when {
            primitive.isNumber -> runCatching { primitive.asBigDecimal.toInt() }.getOrNull()
            primitive.isString -> primitive.asString.toDoubleOrNull()?.toInt()
            else -> null
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
