package com.example.voiceapp.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.voiceapp.databinding.FragmentSettingsBinding
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.navigation.fragment.findNavController
import com.example.voiceapp.R
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val PREFS_NAME = "user_settings"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_AGENT_NAME = "agent_name"
        private const val KEY_USER_ICON_URI = "user_icon_uri"
        private const val KEY_PERSONALITY = "personality"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_EMOJI_TTS_ENABLED = "emoji_tts_enabled"

        // デフォルト値
        const val DEFAULT_USER_NAME = "ユーザー"
        const val DEFAULT_AGENT_NAME = "AIアシスタント"
        const val DEFAULT_PERSONALITY = "kind" // playful | kind | objective

        fun getUserName(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_USER_NAME, DEFAULT_USER_NAME) ?: DEFAULT_USER_NAME
        }

        fun getAgentName(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_AGENT_NAME, DEFAULT_AGENT_NAME) ?: DEFAULT_AGENT_NAME
        }

        fun getUserIconUri(context: Context): Uri? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val value = prefs.getString(KEY_USER_ICON_URI, null)
            return value?.let { Uri.parse(it) }
        }

        fun getPersonality(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_PERSONALITY, DEFAULT_PERSONALITY) ?: DEFAULT_PERSONALITY
        }

        fun isTtsEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_TTS_ENABLED, true)
        }

        fun isEmojiTtsEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_EMOJI_TTS_ENABLED, true)
        }

        fun resetToDefaults(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
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
        val iconUriString = sharedPreferences.getString(KEY_USER_ICON_URI, null)
        val personality = sharedPreferences.getString(KEY_PERSONALITY, DEFAULT_PERSONALITY)
        val ttsEnabled = sharedPreferences.getBoolean(KEY_TTS_ENABLED, true)
        val emojiTtsEnabled = sharedPreferences.getBoolean(KEY_EMOJI_TTS_ENABLED, true)

        binding.etUserName.setText(userName)
        binding.etAgentName.setText(agentName)
        if (iconUriString != null) {
            val uri = Uri.parse(iconUriString)
            binding.ivUserIcon.setImageURI(uri)
        }
        // 性格ラジオ反映
        when (personality) {
            "playful" -> binding.rgPersonality.check(binding.radioPlayful.id)
            "kind" -> binding.rgPersonality.check(binding.radioKind.id)
            "objective" -> binding.rgPersonality.check(binding.radioObjective.id)
            else -> binding.rgPersonality.check(binding.radioKind.id)
        }
        // 音声設定
        binding.switchTtsEnabled.isChecked = ttsEnabled
        binding.switchEmojiTts.isChecked = emojiTtsEnabled
    }

    private fun setupClickListeners() {
        binding.btnSaveUserSettings.setOnClickListener {
            saveUserSettings()
        }
        binding.btnPickUserIcon.setOnClickListener {
            pickImage()
        }
        binding.btnOpenDebug.setOnClickListener {
            findNavController().navigate(R.id.nav_debug)
        }
        // 性格: 選択変更で即時保存
        binding.rgPersonality.setOnCheckedChangeListener { _, checkedId ->
            val selectedPersonality = when (checkedId) {
                binding.radioPlayful.id -> "playful"
                binding.radioKind.id -> "kind"
                binding.radioObjective.id -> "objective"
                else -> DEFAULT_PERSONALITY
            }
            sharedPreferences.edit().putString(KEY_PERSONALITY, selectedPersonality).apply()
            // 必要に応じてナビゲーションヘッダー等を更新
            settingsSavedListener?.onSettingsSaved()
        }
        // TTS設定: 即時保存
        binding.switchTtsEnabled.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_TTS_ENABLED, isChecked).apply()
        }
        // 絵文字TTS設定: 即時保存
        binding.switchEmojiTts.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_EMOJI_TTS_ENABLED, isChecked).apply()
        }
    }

    private fun saveUserSettings() {
        val userName = binding.etUserName.text.toString().trim()
        val agentName = binding.etAgentName.text.toString().trim()
        val currentIconUri = selectedIconUri

        // 空の場合はデフォルト値を使用
        val finalUserName = if (userName.isEmpty()) DEFAULT_USER_NAME else userName
        val finalAgentName = if (agentName.isEmpty()) DEFAULT_AGENT_NAME else agentName
        val selectedPersonality = when (binding.rgPersonality.checkedRadioButtonId) {
            binding.radioPlayful.id -> "playful"
            binding.radioKind.id -> "kind"
            binding.radioObjective.id -> "objective"
            else -> DEFAULT_PERSONALITY
        }

        // SharedPreferencesに保存
        sharedPreferences.edit()
            .putString(KEY_USER_NAME, finalUserName)
            .putString(KEY_AGENT_NAME, finalAgentName)
            .putString(KEY_PERSONALITY, selectedPersonality)
            .apply {
                if (currentIconUri != null) {
                    putString(KEY_USER_ICON_URI, currentIconUri.toString())
                }
            }
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

    // 画像ピッカー
    private var selectedIconUri: Uri? = null
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            // 読み込み & 正方形中央クロップ & 円形加工
            val processed = processSelectedImage(uri)
            if (processed != null) {
                // 内部保存してそのUriを保持
                val savedUri = saveBitmapInternal(processed)
                if (savedUri != null) {
                    selectedIconUri = savedUri
                    binding.ivUserIcon.setImageBitmap(processed)
                } else {
                    binding.ivUserIcon.setImageBitmap(processed)
                }
            } else {
                binding.ivUserIcon.setImageURI(uri) // フォールバック
                selectedIconUri = uri
            }
        }
    }

    private fun processSelectedImage(uri: Uri): Bitmap? {
        return try {
            val input: InputStream? = requireContext().contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(input) ?: return null
            input?.close()

            // 正方形中央クロップ
            val size = minOf(original.width, original.height)
            val x = (original.width - size) / 2
            val y = (original.height - size) / 2
            val square = Bitmap.createBitmap(original, x, y, size, size)

            // 目的サイズ (72dp相当) を端末密度で
            val targetPx = (72 * resources.displayMetrics.density).toInt()
            val scaled = Bitmap.createScaledBitmap(square, targetPx, targetPx, true)

            // 円形マスク
            val output = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val path = Path()
            path.addOval(RectF(0f, 0f, targetPx.toFloat(), targetPx.toFloat()), Path.Direction.CW)
            canvas.clipPath(path)
            canvas.drawBitmap(scaled, 0f, 0f, paint)
            output
        } catch (e: Exception) {
            null
        }
    }

    private fun saveBitmapInternal(bitmap: Bitmap): Uri? {
        return try {
            val dir = File(requireContext().filesDir, "user_icons")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "icon.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            Uri.fromFile(file)
        } catch (e: Exception) {
            null
        }
    }

    private fun pickImage() {
        imagePickerLauncher.launch("image/*")
    }
}
