package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.tenkey_letter_size_setting

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.kazumaproject.core.domain.extensions.dpToPx
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.converter.candidate.Candidate
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentTenkeyCandidateLetterSizeBinding
import com.kazumaproject.markdownhelperkeyboard.ime_service.adapters.SuggestionAdapter
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TenKeyCandidateLetterSizeFragment : Fragment() {

    @Inject
    lateinit var appPreference: AppPreference

    private var _binding: FragmentTenkeyCandidateLetterSizeBinding? = null

    // レイアウトファイル名が fragment_ten_key_letter_size_setting.xml であると仮定しています
    private val binding get() = _binding!!

    private lateinit var suggestionAdapter: SuggestionAdapter

    // 定数定義 (KeyCandidateLetterSizeFragmentから引用)
    private val minKeyTextSize = 12f
    private val maxKeyTextSize = 40f
    private val defaultKeyTextSize = 17.0f

    // 候補サイズもプレビュー用に必要であれば定義
    private val defaultCandidateTextSize = 14.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        suggestionAdapter = SuggestionAdapter()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTenkeyCandidateLetterSizeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // UI初期化
        setupRecyclerView()
        setupPreviewData() // ダミー候補データのセット

        // サイズ調整とリスナー設定
        setKeyboardSize()
        setupKeyLetterSizeSeekBar()
        setupMenu()

        // プレビュー用なのでタッチイベントを無効化
        binding.tenkeyLetterSizePreview.apply {
            isClickable = false
            isFocusable = false
            setOnClickListener { }
            setOnLongClickListener { false }
            setOnTouchListener { _, _ -> true }
        }
    }

    private fun setKeyboardSize() {
        val heightPref = appPreference.keyboard_height ?: 280
        val widthPref = appPreference.keyboard_width ?: 280
        val density = resources.displayMetrics.density
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val screenWidth = resources.displayMetrics.widthPixels
        val positionPref = appPreference.keyboard_position ?: true
        val clampedHeight = heightPref.coerceIn(180, 420)

        val heightPx = (clampedHeight * density).toInt()

        val widthPx = when {
            widthPref == 100 -> {
                ViewGroup.LayoutParams.MATCH_PARENT
            }

            else -> {
                (screenWidth * (widthPref / 100f)).toInt()
            }
        }

        val keyboardHeight = if (isPortrait) {
            heightPx + requireContext().dpToPx(
                appPreference.candidate_view_empty_height_dp ?: 110
            )
        } else {
            heightPx + requireContext().dpToPx(
                appPreference.candidate_view_empty_height_dp ?: 110
            )
        }

        // 候補ビュー（RecyclerView）のレイアウト調整
        (binding.suggestionLetterSizeRecyclerview.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)?.let { params ->
            params.width = widthPx
            if (positionPref) {
                params.startToStart = -1
                params.endToEnd =
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            } else {
                params.endToEnd = -1
                params.startToStart =
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            }
            binding.suggestionLetterSizeRecyclerview.layoutParams = params
        }

        // TenKeyプレビューのレイアウト調整
        (binding.tenkeyLetterSizePreview.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams)?.let { params ->
            params.width = widthPx
            params.height = keyboardHeight
            params.bottomMargin = 56
            params.bottomToBottom =
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            if (positionPref) {
                params.startToStart = -1
                params.endToEnd =
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            } else {
                params.endToEnd = -1
                params.startToStart =
                    androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            }
            binding.tenkeyLetterSizePreview.layoutParams = params
        }
    }

    private fun setupKeyLetterSizeSeekBar() {
        binding.keyLetterSizeSeekbar.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val newSize =
                        minKeyTextSize + (progress.toFloat() / 100f) * (maxKeyTextSize - minKeyTextSize)
                    binding.tenkeyLetterSizePreview.setKeyLetterSize(newSize)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    val newSize =
                        minKeyTextSize + (it.progress.toFloat() / 100f) * (maxKeyTextSize - minKeyTextSize)

                    // デフォルトサイズとの差分を保存するロジック (KeyCandidateLetterSizeFragmentのロジックを踏襲)
                    val sizeDelta = newSize - defaultKeyTextSize
                    appPreference.key_letter_size = sizeDelta
                }
            }
        })

        binding.keyLetterSizeSeekbar.max = 100

        // 保存された設定を読み込む
        val savedDelta = appPreference.key_letter_size ?: 0.0f
        val actualSize = defaultKeyTextSize + savedDelta

        val keyProgress =
            (100 * (actualSize - minKeyTextSize) / (maxKeyTextSize - minKeyTextSize)).toInt()

        binding.keyLetterSizeSeekbar.progress = keyProgress

        // ビューが描画された後にサイズを適用
        binding.tenkeyLetterSizePreview.post {
            binding.tenkeyLetterSizePreview.setKeyLetterSize(actualSize)
        }
    }

    private fun setupRecyclerView() {
        binding.suggestionLetterSizeRecyclerview.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = suggestionAdapter
            setHasFixedSize(true)
        }

        // 候補の文字サイズも現在の設定に合わせておく（プレビューの一貫性のため）
        val savedCandidateSize = appPreference.candidate_letter_size ?: defaultCandidateTextSize
        suggestionAdapter.setCandidateTextSize(savedCandidateSize)
    }

    private fun setupPreviewData() {
        val previewCandidates = listOf(
            Candidate(
                string = "プレビュー",
                type = 1.toByte(),
                length = (5).toUByte(),
                score = 4000
            ),
            Candidate(
                string = "文字サイズ",
                type = 1.toByte(),
                length = (5).toUByte(),
                score = 4001
            ),
            Candidate(string = "設定", type = 1.toByte(), length = (2).toUByte(), score = 4002)
        )
        suggestionAdapter.suggestions = previewCandidates
    }

    private fun setupMenu() {
        val menuHost: MenuHost = requireActivity()
        menuHost.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.fragment_reset_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_reset -> {
                        resetSettings()
                        true
                    }

                    android.R.id.home -> {
                        parentFragmentManager.popBackStack()
                        true
                    }

                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun resetSettings() {
        // キー文字サイズのリセット
        appPreference.key_letter_size = 0.0f // 差分を0に戻す
        val keyProgress =
            (100 * (defaultKeyTextSize - minKeyTextSize) / (maxKeyTextSize - minKeyTextSize)).toInt()

        binding.keyLetterSizeSeekbar.progress = keyProgress
        binding.tenkeyLetterSizePreview.setKeyLetterSize(defaultKeyTextSize)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.suggestionLetterSizeRecyclerview.adapter = null
        _binding = null
    }
}
