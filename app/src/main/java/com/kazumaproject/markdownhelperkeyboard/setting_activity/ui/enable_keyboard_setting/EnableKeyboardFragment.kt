package com.kazumaproject.markdownhelperkeyboard.setting_activity.ui.enable_keyboard_setting

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getSystemService
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.kazumaproject.markdownhelperkeyboard.R
import com.kazumaproject.markdownhelperkeyboard.databinding.FragmentEnableKeyboardBinding
import dagger.hilt.android.AndroidEntryPoint
import java.util.Calendar // 1. Calendarをインポート

@AndroidEntryPoint
class EnableKeyboardFragment : Fragment() {

    private var _binding: FragmentEnableKeyboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEnableKeyboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setGreetingBasedOnTime()

        binding.btnEnableKeyboard.setOnClickListener {
            goToKeyboardSettingScreen()
        }
    }

    private fun setGreetingBasedOnTime() {
        val calendar = Calendar.getInstance()
        val hourOfDay = calendar.get(Calendar.HOUR_OF_DAY) // 0〜23時の24時間形式

        // 時間帯に応じて使用するstringリソースを決定
        val greetingResId = when (hourOfDay) {
            in 4..8 -> R.string.greeting_morning    // 午前4:00〜午前8:59
            in 9..17 -> R.string.greeting_afternoon // 午前9:00〜午後5:59 (17:59)
            else -> R.string.greeting_evening      // それ以外 (午後6:00〜午前3:59)
        }

        // TextViewに挨拶を設定
        binding.textTitle.text = getString(greetingResId)
    }

    override fun onResume() {
        super.onResume()
        (activity as? AppCompatActivity)?.supportActionBar?.apply {
            title = ""
            setDisplayHomeAsUpEnabled(false)
        }

        isKeyboardBoardEnabled()?.let {
            if (it) {
                findNavController().navigate(R.id.navigation_setting)
            }
        }

        setGreetingBasedOnTime()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun isKeyboardBoardEnabled(): Boolean? {
        val imm = getSystemService(requireContext(), InputMethodManager::class.java)
        return imm?.enabledInputMethodList?.any { it.packageName == requireContext().packageName }
    }

    private fun goToKeyboardSettingScreen() {
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        requireActivity().startActivity(intent)
    }
}
