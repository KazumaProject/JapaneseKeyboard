package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.setting

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.tabs.TabLayoutMediator
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentSettingMainBinding
import com.kazumaproject.markdownhelperkeyboard.repository.RomajiMapRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.setting_activity.AppPreference
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.database.UserWord
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SettingMainFragment : Fragment() {

    private var _binding: FragmentSettingMainBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var appPreference: AppPreference

    @Inject
    lateinit var userDictionaryRepository: UserDictionaryRepository

    @Inject
    lateinit var romajiMapRepository: RomajiMapRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val romajiMapUpdated = appPreference.romaji_map_data_version
        lifecycleScope.launch(Dispatchers.IO) {
            userDictionaryRepository.apply {
                if (searchByReadingExactMatchSuspend("びゃんびゃんめん").isEmpty()) {
                    insert(
                        UserWord(
                            reading = "びゃんびゃんめん",
                            word = "\uD883\uDEDE\uD883\uDEDE麺",
                            posIndex = 0,
                            posScore = 4000
                        )
                    )
                }
                if (searchByReadingExactMatchSuspend("びゃん").isEmpty()) {
                    insert(
                        UserWord(
                            reading = "びゃん", word = "\uD883\uDEDE", posIndex = 0, posScore = 3000
                        )
                    )
                }
            }
            if (romajiMapUpdated == 0) {
                romajiMapRepository.updateDefaultMap()
                appPreference.romaji_map_data_version = 1
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingMainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = SettingPagerAdapter(this)
        binding.settingViewPager.adapter = adapter

        // タブのタイトル設定
        TabLayoutMediator(binding.settingTabLayout, binding.settingViewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "共通"
                1 -> "zenz"
                2 -> "辞書"
                3 -> "かな"
                4 -> "QWERTY"
                5 -> getString(R.string.category_sumire_input_keyboard_title) // スミレ入力
                6 -> getString(R.string.category_custom_keyboard_title) // カスタムキーボード
                7 -> getString(R.string.tablet_preference_category_title) // タブレット
                else -> ""
            }
        }.attach()

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    requireActivity().finish()
                }
            })
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            binding.settingProgressBar.isVisible = true
            val enabled = withContext(Dispatchers.IO) {
                isKeyboardBoardEnabled()
            }
            binding.settingProgressBar.isVisible = false
            if (enabled == false) {
                findNavController().navigate(
                    R.id.action_navigation_setting_to_enableKeyboardFragment
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun isKeyboardBoardEnabled(): Boolean? {
        val imm = getSystemService(requireContext(), InputMethodManager::class.java)
        return imm?.enabledInputMethodList?.any { it.packageName == requireContext().packageName }
    }
}
