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
import com.google.gson.reflect.TypeToken
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.database.RomajiMapEntity
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.ui.adapter.RomajiMapAdapter
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentRomajiMapBinding
import com.kazumaproject.markdownhelperkeyboard.repository.RomajiMapRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> exportRomajiMaps(uri) }
        }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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
                    val action = RomajiMapFragmentDirections.actionRomajiMapFragmentToRomajiMapDetailFragment(map.id)
                    findNavController().navigate(action)
                } else {
                    Toast.makeText(context, "デフォルトマップは編集できません", Toast.LENGTH_SHORT).show()
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
                    android.R.id.home ->{
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
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_romaji_map, null)
        val editText = dialogView.findViewById<EditText>(R.id.map_name_edit_text)
        map?.let { editText.setText(it.name) }

        AlertDialog.Builder(requireContext())
            .setTitle(if (map == null) "新しいマップを作成" else "名前を編集")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val name = editText.text.toString()
                if (name.isNotBlank()) {
                    lifecycleScope.launch {
                        if (map == null) {
                            val newMap = RomajiMapEntity(
                                name = name,
                                mapData = repository.getActiveMap().first()?.mapData ?: emptyMap(), // Copy from current active map
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
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun showDeleteConfirmationDialog(map: RomajiMapEntity) {
        AlertDialog.Builder(requireContext())
            .setTitle("マップの削除")
            .setMessage("「${map.name}」を削除しますか？この操作は元に戻せません。")
            .setPositiveButton("削除") { _, _ ->
                lifecycleScope.launch { repository.delete(map) }
            }
            .setNegativeButton("キャンセル", null)
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
                Toast.makeText(context, "エクスポートするカスタムマップがありません", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "エクスポートが完了しました", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "エクスポートに失敗しました: ${e.message}", Toast.LENGTH_SHORT).show()
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
                    val type = object : TypeToken<List<RomajiMapEntity>>() {}.type
                    val maps: List<RomajiMapEntity> = gson.fromJson(jsonString, type)
                    val mapsToInsert = maps.map { it.copy(id = 0, isActive = false) }
                    repository.insertAll(mapsToInsert)
                    Toast.makeText(context, "${maps.size}件のマップをインポートしました", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "インポートに失敗しました。ファイル形式が正しくありません。", Toast.LENGTH_LONG).show()
            } finally {
                binding.progressBar.isVisible = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
