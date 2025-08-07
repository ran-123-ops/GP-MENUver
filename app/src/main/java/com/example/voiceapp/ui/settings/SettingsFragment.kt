package com.example.voiceapp.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.voiceapp.BuildConfig
import com.example.voiceapp.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val PREFS_NAME = "user_settings"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_AGENT_NAME = "agent_name"

        // デフォルト値
        const val DEFAULT_USER_NAME = "ユーザー"
        const val DEFAULT_AGENT_NAME = "AIアシスタント"

        fun getUserName(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_USER_NAME, DEFAULT_USER_NAME) ?: DEFAULT_USER_NAME
        }

        fun getAgentName(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_AGENT_NAME, DEFAULT_AGENT_NAME) ?: DEFAULT_AGENT_NAME
        }
    }

    interface OnSettingsSavedListener {
        fun onSettingsSaved()
    }
    private var settingsSavedListener: OnSettingsSavedListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnSettingsSavedListener) {
            settingsSavedListener = context
        }
    }

    override fun onDetach() {
        super.onDetach()
        settingsSavedListener = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        loadUserSettings()
        setupClickListeners()
    }

//    private fun setupUI() {
//        // APIキーの状態を表示
//        val apiKey = BuildConfig.OPENAI_API_KEY
//        if (apiKey.isNotEmpty() && apiKey != "your_openai_api_key_here") {
//            binding.tvApiKeyStatus.text = "APIキー: 設定済み (${apiKey.take(10)}...)"
//        } else {
//            binding.tvApiKeyStatus.text = "APIキー: 未設定"
//        }
//
//        // Base URLを表示
//        binding.tvBaseUrlStatus.text = "Base URL: ${BuildConfig.OPENAI_BASE_URL}"
//    }

    private fun loadUserSettings() {
        // 保存された設定を読み込み
        val userName = sharedPreferences.getString(KEY_USER_NAME, DEFAULT_USER_NAME)
        val agentName = sharedPreferences.getString(KEY_AGENT_NAME, DEFAULT_AGENT_NAME)

        binding.etUserName.setText(userName)
        binding.etAgentName.setText(agentName)
    }

    private fun setupClickListeners() {
        binding.btnSaveUserSettings.setOnClickListener {
            saveUserSettings()
        }
    }

    private fun saveUserSettings() {
        val userName = binding.etUserName.text.toString().trim()
        val agentName = binding.etAgentName.text.toString().trim()

        // 空の場合はデフォルト値を使用
        val finalUserName = if (userName.isEmpty()) DEFAULT_USER_NAME else userName
        val finalAgentName = if (agentName.isEmpty()) DEFAULT_AGENT_NAME else agentName

        // SharedPreferencesに保存
        sharedPreferences.edit()
            .putString(KEY_USER_NAME, finalUserName)
            .putString(KEY_AGENT_NAME, finalAgentName)
            .apply()

        // UIを更新
        binding.etUserName.setText(finalUserName)
        binding.etAgentName.setText(finalAgentName)

        Toast.makeText(context, "設定を保存しました", Toast.LENGTH_SHORT).show()
        settingsSavedListener?.onSettingsSaved()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
