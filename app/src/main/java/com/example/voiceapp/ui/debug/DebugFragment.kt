package com.example.voiceapp.ui.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.voiceapp.BuildConfig
import com.example.voiceapp.databinding.FragmentDebugBinding
import com.example.voiceapp.ui.chat.ChatHistoryStorage
import com.example.voiceapp.ui.settings.SettingsFragment

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
        setupListeners()
    }

    private fun renderEnvironmentInfo() {
        val appVersion = BuildConfig.VERSION_NAME.ifBlank { BuildConfig.VERSION_CODE.toString() }
        binding.tvAppVersion.text = getString(com.example.voiceapp.R.string.debug_app_version_format, appVersion)
        binding.tvBuildType.text = getString(com.example.voiceapp.R.string.debug_build_type_format, BuildConfig.BUILD_TYPE)
        binding.tvPackageName.text = getString(com.example.voiceapp.R.string.debug_package_name_format, requireContext().packageName)
    }

    private fun setupListeners() {
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
