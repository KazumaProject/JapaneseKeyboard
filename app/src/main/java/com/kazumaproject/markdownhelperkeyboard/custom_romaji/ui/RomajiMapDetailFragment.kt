package com.kazumaproject.markdownhelperkeyboard.custom_romaji.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.database.RomajiMapEntity
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.ui.adapter.RomajiMapDetailAdapter
import com.kazumaproject.markdownhelperkeyboard.custom_romaji.ui.adapter.RomajiPair
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentRomajiMapDetailBinding
import com.kazumaproject.markdownhelperkeyboard.repository.RomajiMapRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RomajiMapDetailFragment : Fragment() {

    private var _binding: FragmentRomajiMapDetailBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var repository: RomajiMapRepository

    private val args: RomajiMapDetailFragmentArgs by navArgs()
    private lateinit var detailAdapter: RomajiMapDetailAdapter
    private var currentMap: RomajiMapEntity? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRomajiMapDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeMapDetails()

        binding.fabAddPair.setOnClickListener {
            showEditPairDialog(null)
        }
    }

    private fun setupRecyclerView() {
        detailAdapter = RomajiMapDetailAdapter(
            onEditClicked = { pair -> showEditPairDialog(pair) },
            onDeleteClicked = { pair ->
                val updatedMapData = currentMap?.mapData?.toMutableMap()?.apply { remove(pair.first) }
                updateDatabase(updatedMapData)
            }
        )
        binding.romajiPairRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = detailAdapter
        }
    }

    private fun observeMapDetails() {
        lifecycleScope.launch {
            repository.getMapById(args.mapId).collectLatest { mapEntity ->
                currentMap = mapEntity
                if (mapEntity != null) {
                    (activity as? AppCompatActivity)?.supportActionBar?.title = mapEntity.name
                    val sortedList = mapEntity.mapData.toList().sortedBy { it.first }
                    detailAdapter.submitList(sortedList)
                }
            }
        }
    }

    private fun showEditPairDialog(pairToEdit: RomajiPair?) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_edit_romaji_pair, null)
        val keyEditText = dialogView.findViewById<EditText>(R.id.romaji_key_edit_text)
        val valueEditText = dialogView.findViewById<EditText>(R.id.kana_value_edit_text)

        pairToEdit?.let {
            keyEditText.setText(it.first)
            valueEditText.setText(it.second.first)
        }

        val isEditing = pairToEdit != null

        AlertDialog.Builder(requireContext())
            .setTitle(if (isEditing) "ペアを編集" else "ペアを追加")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val newKey = keyEditText.text.toString().trim()
                val newValue = valueEditText.text.toString().trim()

                if (newKey.isEmpty() || newValue.isEmpty()) {
                    Toast.makeText(context, "キーと値の両方を入力してください", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val existingKeys = currentMap?.mapData?.keys ?: emptySet()
                if (newKey in existingKeys && (pairToEdit == null || newKey != pairToEdit.first)) {
                    Toast.makeText(context, "エラー: このローマ字キーは既に使用されています", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val updatedMapData = currentMap?.mapData?.toMutableMap() ?: mutableMapOf()
                if (isEditing) {
                    updatedMapData.remove(pairToEdit!!.first)
                }
                updatedMapData[newKey] = Pair(newValue, newKey.length)

                updateDatabase(updatedMapData)
            }
            .setNegativeButton("キャンセル", null)
            .show()
    }

    private fun updateDatabase(updatedMapData: Map<String, Pair<String, Int>>?) {
        if (updatedMapData == null) return
        currentMap?.let {
            val updatedEntity = it.copy(mapData = updatedMapData)
            lifecycleScope.launch {
                repository.update(updatedEntity)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
