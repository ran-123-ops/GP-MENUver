package com.example.voiceapp.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.voiceapp.BuildConfig
import com.example.voiceapp.R
import com.example.voiceapp.api.ChatRequestMessage
import com.example.voiceapp.api.OpenAIClient
import com.example.voiceapp.databinding.FragmentDebugBinding
import com.example.voiceapp.ui.chat.ChatHistoryStorage
import com.example.voiceapp.ui.settings.SettingsFragment
import kotlinx.coroutines.launch

class DebugFragment : Fragment() {

    private var _binding: FragmentDebugBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDebugBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        renderEnvironmentInfo()
        loadApiSettings()
        setupListeners()
    }

    private fun renderEnvironmentInfo() {
        val appVersion = BuildConfig.VERSION_NAME.ifBlank { BuildConfig.VERSION_CODE.toString() }
        binding.tvAppVersion.text = getString(com.example.voiceapp.R.string.debug_app_version_format, appVersion)
        binding.tvBuildType.text = getString(com.example.voiceapp.R.string.debug_build_type_format, BuildConfig.BUILD_TYPE)
        binding.tvPackageName.text = getString(com.example.voiceapp.R.string.debug_package_name_format, requireContext().packageName)
    }

    private fun loadApiSettings() {
        val prefs = requireContext().getSharedPreferences("voiceapp_settings", Context.MODE_PRIVATE)
        val serverIp = prefs.getString("custom_server_ip", "") ?: ""
        val serverPort = prefs.getString("custom_server_port", "") ?: ""
        val apiKey = prefs.getString("custom_api_key", "") ?: ""
        
        binding.etServerIp.setText(serverIp)
        binding.etServerPort.setText(serverPort)
        binding.etApiKey.setText(apiKey)
    }

    private fun saveApiSettings() {
        val serverIp = binding.etServerIp.text?.toString()?.trim() ?: ""
        val serverPort = binding.etServerPort.text?.toString()?.trim() ?: ""
        val apiKey = binding.etApiKey.text?.toString()?.trim() ?: ""
        
        val prefs = requireContext().getSharedPreferences("voiceapp_settings", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("custom_server_ip", serverIp)
            putString("custom_server_port", serverPort)
            putString("custom_api_key", apiKey)
            apply()
        }
        
        Toast.makeText(
            requireContext(), 
            "API設定を保存しました", 
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun testConnection() {
        val serverIp = binding.etServerIp.text?.toString()?.trim() ?: ""
        val serverPort = binding.etServerPort.text?.toString()?.trim() ?: ""
        val customApiKey = binding.etApiKey.text?.toString()?.trim() ?: ""
        
        // ベースURL構築
        val baseUrl = if (serverIp.isNotEmpty()) {
            val port = if (serverPort.isNotEmpty()) ":$serverPort" else ""
            val protocol = if (serverPort == "443") "https" else "http"
            // URLが既に/v1/で終わっていない場合のみ追加
            val url = "$protocol://$serverIp$port"
            if (url.endsWith("/v1") || url.endsWith("/v1/")) {
                if (url.endsWith("/v1")) "$url/" else url
            } else {
                "$url/v1/"
            }
        } else {
            BuildConfig.OPENAI_BASE_URL
        }

        // APIキーを決定（カスタムキーがあればそれを使用、なければビルド設定値）
        val apiKey = if (customApiKey.isNotEmpty()) customApiKey else BuildConfig.OPENAI_API_KEY

        // ステータス表示を表示
        binding.tvConnectionStatus.visibility = View.VISIBLE
        binding.tvConnectionStatus.text = "接続テスト中...\nURL: $baseUrl"
        binding.tvConnectionStatus.setTextColor(
            ContextCompat.getColor(requireContext(), R.color.ios_secondary_label)
        )
        binding.btnTestConnection.isEnabled = false

        // API接続テスト
        lifecycleScope.launch {
            try {
                val client = OpenAIClient(apiKey, baseUrl)
                
                // シンプルなテストメッセージを送信
                val testMessages = listOf(
                    ChatRequestMessage(
                        role = "user",
                        content = listOf(
                            com.example.voiceapp.api.MessageContent(
                                type = "text",
                                text = "Hello"
                            )
                        )
                    )
                )
                
                val result = client.sendMessage(testMessages)
                
                result.fold(
                    onSuccess = { response ->
                        binding.tvConnectionStatus.text = "✓ 接続成功!\nURL: $baseUrl\nレスポンス: ${response.take(50)}..."
                        binding.tvConnectionStatus.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.ios_green)
                        )
                    },
                    onFailure = { error ->
                        binding.tvConnectionStatus.text = "✗ 接続失敗\nURL: $baseUrl\nエラー: ${error.message}"
                        binding.tvConnectionStatus.setTextColor(
                            ContextCompat.getColor(requireContext(), R.color.ios_red)
                        )
                    }
                )
            } catch (e: Exception) {
                binding.tvConnectionStatus.text = "✗ 接続エラー\nURL: $baseUrl\n例外: ${e.message}"
                binding.tvConnectionStatus.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.ios_red)
                )
            } finally {
                binding.btnTestConnection.isEnabled = true
            }
        }
    }

    private fun setupListeners() {
        binding.btnTestConnection.setOnClickListener {
            testConnection()
        }

        binding.btnSaveApiSettings.setOnClickListener {
            saveApiSettings()
        }

        binding.btnCopyDeviceInfo.setOnClickListener {
            copyDeviceInfoToClipboard()
        }

        binding.btnClearChatHistory.setOnClickListener {
            ChatHistoryStorage(requireContext()).clear()
            Toast.makeText(requireContext(), com.example.voiceapp.R.string.debug_cleared_chat_history, Toast.LENGTH_SHORT).show()
        }

        binding.btnResetSettings.setOnClickListener {
            SettingsFragment.resetToDefaults(requireContext())
            Toast.makeText(requireContext(), com.example.voiceapp.R.string.debug_reset_settings_done, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyDeviceInfoToClipboard() {
        val info = buildString {
            appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Build Type: ${BuildConfig.BUILD_TYPE}")
            appendLine("Package: ${requireContext().packageName}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        }
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("VoiceApp Debug Info", info))
        Toast.makeText(requireContext(), com.example.voiceapp.R.string.debug_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
