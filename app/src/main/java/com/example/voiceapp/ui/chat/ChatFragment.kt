package com.example.voiceapp.ui.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.view.inputmethod.InputMethodManager
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import kotlin.math.max
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.voiceapp.R
import com.example.voiceapp.databinding.FragmentChatBinding
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.voiceapp.api.OpenAIClient
import com.example.voiceapp.BuildConfig
import com.example.voiceapp.ui.settings.SettingsFragment
import io.noties.markwon.Markwon
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var chatViewModel: ChatViewModel
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var markwon: Markwon

    private var mediaPlayer: MediaPlayer? = null
    private var lastMessageCount: Int = 0
    private val spokenAssistantMessages = mutableMapOf<Long, String>()

    // 追加: 音声認識関連
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false

    private var lastPersonality: String? = null
    private var selectedImage: ImageAttachment? = null
    
    // 位置情報取得用
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var cachedLocationInfo: String = "位置情報: 取得中..."

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            showImagePreview(uri)
        } else if (isAdded) {
            Toast.makeText(requireContext(), "画像が選択されませんでした", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val REQ_RECORD_AUDIO = 1001
        private const val TAG = "ChatFragment"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
    chatViewModel = ViewModelProvider(this, ChatViewModelFactory(requireContext()))[ChatViewModel::class.java]
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        markwon = Markwon.create(requireContext())
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        setupRecyclerView()
        setupClickListeners()
        setupSpeechRecognizer() // 追加
        setupImagePreview()
        setupInsetsHandling()
        observeViewModel()
        // 起動時の性格を記録
        lastPersonality = com.example.voiceapp.ui.settings.SettingsFragment.getPersonality(requireContext())
        // 位置情報の初回取得を開始
        getCurrentLocationInfo()
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter(markwon)
        binding.rvMessages.apply {
            adapter = chatAdapter
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            if (message.isNotEmpty() || selectedImage != null) {
                val currentPersonality = com.example.voiceapp.ui.settings.SettingsFragment.getPersonality(requireContext())
                if (lastPersonality != null && lastPersonality != currentPersonality && (chatViewModel.messages.value?.isNotEmpty() == true)) {
                    chatViewModel.clearChat()
                    clearSelectedImage()
                    Toast.makeText(requireContext(), "性格設定を反映しました。新しい会話を開始します。", Toast.LENGTH_SHORT).show()
                }
                val systemPrompt = buildSystemPrompt()
                chatViewModel.sendMessage(message.takeIf { it.isNotEmpty() }, selectedImage, systemPrompt)
                lastPersonality = currentPersonality
                binding.etMessage.setText("")
                clearSelectedImage()
                hideKeyboard()
            } else {
                Toast.makeText(requireContext(), "メッセージまたは画像を入力してください", Toast.LENGTH_SHORT).show()
            }
        }
        binding.btnMic.setOnClickListener { onMicClicked() }
        // 追加: 全消去
        binding.btnClear.setOnClickListener {
            chatViewModel.clearChat()
            clearSelectedImage()
            Toast.makeText(requireContext(), "チャットをクリアしました", Toast.LENGTH_SHORT).show()
        }
        binding.btnAttach.setOnClickListener {
            launchImagePicker()
        }
    }

    private fun setupImagePreview() {
        binding.cardImagePreview.isVisible = false
        binding.btnRemoveImage.setOnClickListener { clearSelectedImage() }
    }

    private fun setupInsetsHandling() {
        val initialRvPaddingBottom = binding.rvMessages.paddingBottom
        val initialInputPaddingBottom = binding.messageInputContainer.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            val navBarInset = systemBars.bottom
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val imeExtra = max(0, imeInsets.bottom - navBarInset)
            val bottomInset = if (imeVisible) imeExtra else navBarInset

            binding.rvMessages.updatePadding(bottom = initialRvPaddingBottom + bottomInset)
            binding.messageInputContainer.updatePadding(bottom = initialInputPaddingBottom + bottomInset)

            if (imeVisible) {
                binding.rvMessages.post {
                    if (chatAdapter.itemCount > 0) {
                        binding.rvMessages.scrollToPosition(chatAdapter.itemCount - 1)
                    }
                }
            }

            insets
        }

        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun launchImagePicker() {
        pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun showImagePreview(uri: android.net.Uri) {
        val attachment = createImageAttachment(uri)
        if (attachment == null) {
            if (isAdded) {
                Toast.makeText(requireContext(), "画像の読み込みに失敗しました", Toast.LENGTH_SHORT).show()
            }
            clearSelectedImage()
            return
        }
        selectedImage = attachment
        binding.cardImagePreview.isVisible = true
        binding.ivImagePreview.setImageURI(uri)
    }

    private fun clearSelectedImage() {
        selectedImage = null
        binding.cardImagePreview.isVisible = false
        binding.ivImagePreview.setImageDrawable(null)
    }

    private fun createImageAttachment(uri: android.net.Uri): ImageAttachment? {
        return try {
            val resolver = requireContext().contentResolver
            val mimeType = resolver.getType(uri).orFallbackMime(uri)
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            val dataUrl = "data:$mimeType;base64,$base64"
            ImageAttachment(uri = uri, dataUrl = dataUrl)
        } catch (e: Exception) {
            Log.e(TAG, "画像のエンコードに失敗しました", e)
            null
        }
    }

    private fun String?.orFallbackMime(uri: android.net.Uri): String {
        if (!this.isNullOrBlank()) return this
        val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
        if (!extension.isNullOrBlank()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())?.let { return it }
        }
        return "image/png"
    }

    // 追加: 音声認識初期化
    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            Toast.makeText(requireContext(), "音声認識が端末で利用できません", Toast.LENGTH_LONG).show()
            binding.btnMic.isEnabled = false
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext()).apply {
            setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    binding.btnMic.icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.presence_audio_online)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    isListening = false
                    binding.btnMic.icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_btn_speak_now)
                    Toast.makeText(requireContext(), "認識エラー: $error", Toast.LENGTH_SHORT).show()
                }
                override fun onResults(results: Bundle?) {
                    isListening = false
                    binding.btnMic.icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_btn_speak_now)
                    val texts = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val recognized = texts?.joinToString(" ") ?: ""
                    if (recognized.isNotBlank()) {
                        binding.etMessage.setText(recognized)
                        binding.etMessage.setSelection(recognized.length)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val texts = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partial = texts?.firstOrNull() ?: return
                    binding.etMessage.setText(partial)
                    binding.etMessage.setSelection(partial.length)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun onMicClicked() {
        if (isListening) {
            stopListening()
            return
        }
        // 権限チェック
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermission()
        } else {
            startListening()
        }
    }

    private fun requestAudioPermission() {
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_RECORD_AUDIO)
    }

    private fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ja-JP")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "話しかけてください")
        }
        try {
            speechRecognizer?.startListening(intent)
            isListening = true
            binding.btnMic.icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.presence_audio_away)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "開始できません: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        binding.btnMic.icon = ContextCompat.getDrawable(requireContext(), android.R.drawable.ic_btn_speak_now)
    }

    // 権限結果
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_RECORD_AUDIO) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening()
            } else {
                Toast.makeText(requireContext(), "マイク権限が必要です", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        chatViewModel.messages.observe(viewLifecycleOwner) { messages ->
            val previousCount = lastMessageCount
            chatAdapter.submitList(messages) {
                if (messages.isNotEmpty()) {
                    binding.rvMessages.scrollToPosition(messages.size - 1)
                }
                // ストリーミング中の読み上げは削除（isLoadingで制御）
                lastMessageCount = messages.size
            }
        }

        chatViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.btnSend.isEnabled = !isLoading
            binding.btnAttach.isEnabled = !isLoading
            
            // ストリーミング完了時に読み上げをトリガー
            if (!isLoading) {
                val messages = chatViewModel.messages.value ?: emptyList()
                handleAssistantSpeechComplete(messages)
            }
        }

        chatViewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                binding.tvError.text = error
                binding.tvError.visibility = View.VISIBLE
                Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            } else {
                binding.tvError.visibility = View.GONE
            }
        }
    }

    private fun buildSystemPrompt(): String {
        val userName = SettingsFragment.getUserName(requireContext())
        val agentName = SettingsFragment.getAgentName(requireContext())
        val isWebSearchEnabled = SettingsFragment.isWebSearchEnabled(requireContext())
        
        // 現在時刻と現在地情報を取得
        val currentDateTime = getCurrentDateTime()
        val locationInfo = getCurrentLocationInfo()
        
        // Web Search有効時の追加指示
        val webSearchInstruction = if (isWebSearchEnabled) {
            "\n\n【重要】あなたはGPT-4o Search Previewモデル（gpt-4o-2024-11-20）です。" +
            "最新の情報やリアルタイムのデータが必要な質問の場合、Web検索機能を使用して" +
            "インターネットから最新情報を取得して回答してください。情報源がある場合はそれを明示してください。"
        } else {
            ""
        }
        
        // コンテキスト情報
        val contextInfo = "\n\n【コンテキスト情報】\n現在時刻: $currentDateTime\n$locationInfo"
        
        return when (SettingsFragment.getPersonality(requireContext())) {
            //おちゃめ
            "playful" -> """
                あなたは${agentName}。${userName}と日本語で会話するAIアシスタントです。
                ・自分を指すときは一人称「私」を使います。必要に応じて冒頭で「${agentName}です」と名乗っても構いません。
                ・${userName}の名前を適切に呼びかけ、親しみやすく対応します。
                スタイル: おちゃめで親しみやすく、軽いユーモアや絵文字を時々交えます(例: 😊, ✨ を1つ程度)。
                ただし冗長にならず、要点は簡潔・明瞭に。安全で丁寧な表現を心掛けてください。
                依頼があれば詳しく、無ければ簡潔に答えます。
                返答はマークダウン形式で行ってください。$webSearchInstruction$contextInfo
            """.trimIndent()
            //客観的
            "objective" -> """
                あなたは${agentName}。${userName}と日本語で会話するAIアシスタントです。
                ・自分を指すときは一人称「私」を使います。必要に応じて冒頭で「${agentName}です」と名乗っても構いません。
                ・${userName}の名前を必要に応じて用い、丁寧に対応します。
                スタイル: 客観的・中立・簡潔。事実ベースで不要な感情表現は避けます。
                根拠が曖昧な場合は推測と明示し、不確実性を伝えます。必要に応じて箇条書きや手順で整理します。
                返答はマークダウン形式で行ってください。$webSearchInstruction$contextInfo
            """.trimIndent()
            //優しく
            else -> """
                あなたは${agentName}。${userName}と日本語で会話するAIアシスタントです。
                ・自分を指すときは一人称「私」を使います。必要に応じて冒頭で「${agentName}です」と名乗っても構いません。
                ・${userName}の気持ちに配慮し、名前を添えた丁寧な呼びかけを行います。
                スタイル: 優しく丁寧で共感的。相手の意図をくみ取り、安心感のある言い回しを心掛けます。
                長くなり過ぎないように配慮しつつ、役立つ補足がある場合は短い提案を添えます。
                返答はマークダウン形式で行ってください。$webSearchInstruction$contextInfo
            """.trimIndent()
        }
    }

    private fun speakOut(text: String) {
        if (text.isBlank()) return
        
        // SharedPreferencesからTTS設定を読み込み
        val prefs = requireContext().getSharedPreferences("voiceapp_settings", Context.MODE_PRIVATE)
        val isTtsEnabled = prefs.getBoolean("tts_enabled", true) // デフォルトはON
        
        if (!isTtsEnabled) return
        
        // 絵文字読み上げ設定を取得
        val isEmojiReadingEnabled = prefs.getBoolean("emoji_reading_enabled", false)
        
        val processedText = if (isEmojiReadingEnabled) {
            convertEmojisToText(text)
        } else {
            // 絵文字を除去（Unicode絵文字の範囲を除外）
            text.replace(
                Regex("[\\p{So}\\p{Cn}\\p{Sk}\\p{Emoji}]+"),
                ""
            ).trim()
        }
        
        if (processedText.isBlank()) return
        
        // OpenAI TTS APIを使用して音声合成
        lifecycleScope.launch {
            try {
                val customApiKey = prefs.getString("custom_api_key", "")?.trim()
                val apiKey = if (!customApiKey.isNullOrEmpty()) {
                    customApiKey
                } else {
                    BuildConfig.OPENAI_API_KEY
                }
                
                if (apiKey.isEmpty() || apiKey == "your_openai_api_key_here") {
                    Log.w(TAG, "APIキーが設定されていないため、音声読み上げをスキップします")
                    return@launch
                }
                
                Log.d(TAG, "TTS API呼び出し開始: テキスト長=${processedText.length}")
                
                val client = OpenAIClient(apiKey, "https://api.openai.com/v1/")
                val result = client.textToSpeech(
                    text = processedText,
                    voice = "alloy",
                    speed = 1.0
                )
                
                result.fold(
                    onSuccess = { audioData ->
                        Log.d(TAG, "TTS API成功: 音声データサイズ=${audioData.size} bytes")
                        playAudioData(audioData)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "TTS APIエラー: ${error.message}", error)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "音声合成エラー: ${e.message}", e)
            }
        }
    }

    private fun playAudioData(audioData: ByteArray) {
        // メインスレッドで実行
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
            try {
                // 既存のMediaPlayerを停止・解放
                mediaPlayer?.apply {
                    if (isPlaying) {
                        stop()
                    }
                    release()
                }
                mediaPlayer = null
                
                // 一時ファイルに音声データを保存
                val tempFile = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    File.createTempFile("tts_", ".mp3", requireContext().cacheDir).apply {
                        FileOutputStream(this).use { it.write(audioData) }
                    }
                }
                
                Log.d(TAG, "音声ファイル作成: ${tempFile.absolutePath}, サイズ=${tempFile.length()} bytes")
                
                // MediaPlayerで再生
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(tempFile.absolutePath)
                    setOnPreparedListener { mp ->
                        Log.d(TAG, "MediaPlayer準備完了、再生開始")
                        mp.start()
                    }
                    setOnCompletionListener { mp ->
                        Log.d(TAG, "再生完了")
                        mp.release()
                        mediaPlayer = null
                        tempFile.delete()
                    }
                    setOnErrorListener { mp, what, extra ->
                        Log.e(TAG, "MediaPlayer エラー: what=$what, extra=$extra")
                        mp.release()
                        mediaPlayer = null
                        tempFile.delete()
                        true
                    }
                    prepareAsync() // 非同期で準備
                }
            } catch (e: Exception) {
                Log.e(TAG, "音声再生エラー: ${e.message}", e)
                mediaPlayer?.release()
                mediaPlayer = null
            }
        }
    }

    private fun convertEmojisToText(text: String): String {
        // 絵文字を日本語に変換
        val emojiMap = mapOf(
            // 顔文字・感情
            "😀" to "にこにこ", "😁" to "うれしそう", "😂" to "わらい", "🤣" to "おおわらい",
            "😃" to "えがお", "😄" to "えがお", "😅" to "あせ", "😆" to "たのしい",
            "😉" to "ういんく", "😊" to "うれしい", "😋" to "おいしそう", "😎" to "くーる",
            "😍" to "だいすき", "😘" to "きす", "😗" to "きす", "😙" to "きす", "😚" to "きす",
            "🙂" to "ほほえみ", "🤗" to "はぐ", "🤩" to "きらきら", "🤔" to "かんがえちゅう",
            "🤨" to "ぎもん", "😐" to "むひょうじょう", "😑" to "むひょうじょう", "😶" to "むごん",
            "🙄" to "あきれ", "😏" to "にやり", "😣" to "こまった", "😥" to "かなしい",
            "😮" to "おどろき", "🤐" to "くちちゃっく", "😯" to "おどろき", "😪" to "ねむい",
            "😫" to "つかれた", "😴" to "ねる", "😌" to "あんしん", "😛" to "てへぺろ",
            "😜" to "ういんくしただし", "😝" to "しただし", "🤤" to "よだれ", "😒" to "ふまん",
            "😓" to "ひやあせ", "😔" to "おちこみ", "😕" to "こんわく", "🙃" to "さかさま",
            "🤑" to "おかね", "😲" to "びっくり", "☹️" to "かなしい", "🙁" to "かなしい",
            "😖" to "こまった", "😞" to "しつぼう", "😟" to "しんぱい", "😤" to "むっ",
            "😢" to "なく", "😭" to "ごうきゅう", "😦" to "おどろき", "😧" to "しんぱい",
            "😨" to "こわい", "😩" to "つかれた", "🤯" to "しょうげき", "😬" to "きまずい",
            "😰" to "あせり", "😱" to "きょうふ", "😳" to "てれ", "🤪" to "へんがお",
            "😵" to "めがまわる", "😡" to "いかり", "😠" to "おこり", "🤬" to "げきど",
            "😷" to "ますく", "🤒" to "ねつ", "🤕" to "けが", "🤢" to "きもちわるい",
            "🤮" to "はきけ", "🤧" to "くしゃみ", "😇" to "てんし", "🤠" to "かうぼーい",
            "🤡" to "ぴえろ", "🤥" to "うそ", "🤫" to "しずかに", "🤭" to "くちをかくす",
            "🧐" to "じっくり", "🤓" to "めがね", "😈" to "あくま", "👿" to "あくま",
            "👹" to "おに", "👺" to "てんぐ", "💀" to "がいこつ", "☠️" to "がいこつ",
            "👻" to "ゆうれい", "👽" to "うちゅうじん", "👾" to "げーむ", "🤖" to "ろぼっと",
            "💩" to "うんち", "😺" to "ねこえがお", "😸" to "ねこうれしい", "😹" to "ねこわらい",
            "😻" to "ねこはーと", "😼" to "ねこにやり", "😽" to "ねこきす", "🙀" to "ねこおどろき",
            "😿" to "ねこなく", "😾" to "ねこおこり",
            
            // ハート・記号
            "❤️" to "はーと", "💛" to "きいろいはーと", "💚" to "みどりのはーと", "💙" to "あおいはーと",
            "💜" to "むらさきのはーと", "🖤" to "くろいはーと", "💔" to "しつれん", "❣️" to "はーと",
            "💕" to "ふたつのはーと", "💞" to "かいてんはーと", "💓" to "どきどき", "💗" to "せいちょうはーと",
            "💖" to "きらきらはーと", "💘" to "やはーと", "💝" to "ぷれぜんと",
            
            // ハンドサイン
            "👍" to "いいね", "👎" to "よくない", "👌" to "おっけー", "✌️" to "ぴーす",
            "🤞" to "ねがい", "🤟" to "あい", "🤘" to "ろっく", "🤙" to "でんわ",
            "👈" to "ひだり", "👉" to "みぎ", "👆" to "うえ", "👇" to "した",
            "☝️" to "ひとさしゆび", "✋" to "て", "🤚" to "てのこう", "🖐️" to "ひらいたて",
            "🖖" to "ばるかん", "👋" to "てをふる", "💪" to "ちからこぶ",
            "🙏" to "おねがい", "✍️" to "かく", "💅" to "ねいる", "🤳" to "じどり",
            
            // 人物・動作
            "💃" to "だんす", "🕺" to "だんす", "👯" to "だんす", "🚶" to "あるく",
            "🏃" to "はしる", "👫" to "かっぷる", "👬" to "だんせいふたり", "👭" to "じょせいふたり",
            "💏" to "きす", "💑" to "かっぷる", "👨‍👩‍👧" to "かぞく", "👪" to "かぞく",
            
            // イベント・お祝い
            "🎉" to "くらっかー", "🎊" to "くすだま", "🎈" to "ふうせん", "🎁" to "ぷれぜんと",
            "🏆" to "とろふぃー", "🥇" to "きんめだる", "🥈" to "ぎんめだる", "🥉" to "どうめだる",
            
            // スポーツ
            "⚽" to "さっかー", "🏀" to "ばすけっとぼーる", "🏈" to "あめふと", "⚾" to "やきゅう",
            "🎾" to "てにす", "🏐" to "ばれー", "🏉" to "らぐびー", "🎱" to "びりやーど",
            
            // 効果・記号
            "🔥" to "ほのお", "✨" to "きらきら", "⭐" to "ほし", "🌟" to "かがやくほし",
            "💫" to "めがまわる", "💥" to "しょうとつ", "💢" to "いかり", "💦" to "あせ",
            "💧" to "みずてき", "💤" to "すいみん", "💨" to "かぜ", "👏" to "はくしゅ",
            "🙌" to "ばんざい", "👐" to "りょうてをひらく", "🤲" to "りょうてをそえる", "🤝" to "あくしゅ",
            
            // 音楽
            "🎵" to "おんぷ", "🎶" to "おんがく", "🎤" to "まいく", "🎧" to "へっどふぉん",
            
            // デバイス
            "📱" to "すまほ", "💻" to "ぱそこん", "⌨️" to "きーぼーど", "🖥️" to "ですくとっぷ",
            "🖨️" to "ぷりんたー", "🖱️" to "まうす", "💾" to "ふろっぴー", "💿" to "しーでぃー",
            "📀" to "でぃーぶいでぃー", "🎥" to "かめら", "📷" to "かめら", "📹" to "びでおかめら",
            
            // 天気
            "☀️" to "はれ", "🌤️" to "はれときどきくもり", "⛅" to "くもり", "🌥️" to "くもり",
            "☁️" to "くもり", "🌦️" to "あめ", "🌧️" to "あめ", "⛈️" to "らいう",
            "🌩️" to "かみなり", "🌨️" to "ゆき", "❄️" to "ゆきのけっしょう", "☃️" to "ゆきだるま",
            "⛄" to "ゆきだるま", "🌬️" to "かぜ", "🌪️" to "たつまき",
            "🌫️" to "きり", "🌈" to "にじ", "☂️" to "かさ", "☔" to "あめがさ",
            "⚡" to "いなずま", "🔆" to "あかるい", "🔅" to "くらい", "💡" to "でんきゅう",
            
            // 食べ物
            "🍕" to "ぴざ", "🍔" to "はんばーがー", "🍟" to "ふらいどぽてと", "🌭" to "ほっとどっぐ",
            "🍿" to "ぽっぷこーん", "🧀" to "ちーず", "🥚" to "たまご", "🍳" to "めだまやき",
            "🥓" to "べーこん", "🥞" to "ぱんけーき", "🍞" to "ぱん", "🥐" to "くろわっさん",
            "🥖" to "ばげっと", "🥨" to "ぷれっつぇる", "🥯" to "べーぐる", "🧇" to "わっふる",
            "🍖" to "にく", "🍗" to "とりにく", "🥩" to "すてーき", "🍤" to "えびふらい",
            "🍱" to "べんとう", "🍜" to "らーめん", "🍲" to "なべ", "🍛" to "かれー",
            "🍣" to "すし", "🍙" to "おにぎり", "🍚" to "ごはん", "🍘" to "せんべい",
            "🍥" to "なると", "🍢" to "おでん", "🍡" to "だんご", "🍧" to "かきごおり",
            "🍨" to "あいす", "🍦" to "そふとくりーむ", "🥧" to "ぱい", "🍰" to "けーき",
            "🎂" to "たんじょうびけーき", "🧁" to "かっぷけーき", "🍮" to "ぷりん", "🍭" to "きゃんでぃー",
            "🍬" to "きゃんでぃー", "🍫" to "ちょこれーと", "🍩" to "どーなつ", "🍪" to "くっきー",
            "🌰" to "くり", "🥜" to "ぴーなっつ", "🍯" to "はちみつ", "🥛" to "ぎゅうにゅう",
            "🍼" to "ほにゅうびん", "☕" to "こーひー", "🍵" to "おちゃ", "🧃" to "じゅーすぼっくす",
            "🥤" to "どりんく", "🍶" to "にほんしゅ", "🍺" to "びーる", "🍻" to "かんぱい",
            "🥂" to "かんぱい", "🍷" to "わいん", "🥃" to "ういすきー", "🍸" to "かくてる",
            "🍹" to "とろぴかるどりんく", "🍾" to "しゃんぱん", "🧊" to "こおり", "🥄" to "すぷーん",
            "🍴" to "ふぉーくとないふ", "🍽️" to "さらとないふふぉーく", "🥣" to "ぼうる", "🥡" to "ていくあうと",
            "🥢" to "はし", "🧂" to "しお"
        )
        
        var result = text
        emojiMap.forEach { (emoji, reading) ->
            result = result.replace(emoji, reading)
        }
        
        // マップにない絵文字は除去
        result = result.replace(
            Regex("[\\p{So}\\p{Cn}\\p{Sk}\\p{Emoji}]+"),
            ""
        ).trim()
        
        return result
    }

    private fun handleAssistantSpeechComplete(messages: List<ChatMessage>) {
        // TTS が有効かチェック
        if (!SettingsFragment.isTTSEnabled(requireContext())) return
        
        val assistantMessages = messages.filter { !it.isUser }
        val activeTimestamps = mutableSetOf<Long>()

        assistantMessages.forEach { message ->
            val timestamp = message.timestamp
            activeTimestamps.add(timestamp)
            val content = message.content.trim()
            
            // 空のメッセージはスキップ
            if (content.isBlank()) return@forEach

            val lastSpoken = spokenAssistantMessages[timestamp]
            
            // まだ読み上げていない、または内容が変わった場合のみ読み上げ
            if (lastSpoken != content) {
                spokenAssistantMessages[timestamp] = content
                Log.d(TAG, "音声読み上げ開始（ストリーミング完了時）: timestamp=$timestamp, length=${content.length}")
                speakOut(content)
            }
        }

        // 古いメッセージの記録を削除
        spokenAssistantMessages.keys.retainAll(activeTimestamps)
    }

    /**
     * 現在時刻を日本語形式で取得
     */
    private fun getCurrentDateTime(): String {
        val sdf = SimpleDateFormat("yyyy年MM月dd日(E) HH:mm:ss", Locale.JAPANESE)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Tokyo")
        return sdf.format(Date())
    }

    /**
     * 現在位置情報を取得（非同期、最後の既知位置を使用）
     */
    private fun getCurrentLocationInfo(): String {
        // 位置情報の権限チェック
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            cachedLocationInfo = "位置情報: 権限が必要です"
            return cachedLocationInfo
        }

        // 非同期で位置情報を取得し、キャッシュを更新
        lifecycleScope.launch {
            try {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    null
                ).addOnSuccessListener { location ->
                    if (location != null) {
                        val lat = String.format("%.4f", location.latitude)
                        val lon = String.format("%.4f", location.longitude)
                        cachedLocationInfo = "位置情報: 緯度 $lat, 経度 $lon"
                        Log.d(TAG, "位置情報取得成功: $cachedLocationInfo")
                    } else {
                        cachedLocationInfo = "位置情報: 取得できませんでした"
                        Log.d(TAG, "位置情報がnullでした")
                    }
                }.addOnFailureListener { e ->
                    cachedLocationInfo = "位置情報: 取得エラー"
                    Log.e(TAG, "位置情報取得エラー", e)
                }
            } catch (e: Exception) {
                cachedLocationInfo = "位置情報: 取得エラー"
                Log.e(TAG, "位置情報取得例外", e)
            }
        }
        
        // 現在のキャッシュ値を返す（初回は「取得中...」、以降は前回の値）
        return cachedLocationInfo
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        speechRecognizer?.destroy()
        speechRecognizer = null
        spokenAssistantMessages.clear()
        clearSelectedImage()
        _binding = null
    }

    private fun hideKeyboard() {
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        val windowToken = binding.etMessage.windowToken
        if (windowToken != null) {
            imm?.hideSoftInputFromWindow(windowToken, 0)
        }
        binding.etMessage.clearFocus()
    }
}
