package com.example.voiceapp.ui.chat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import android.webkit.MimeTypeMap
import com.example.voiceapp.ui.settings.SettingsFragment
import io.noties.markwon.Markwon
import java.util.Locale

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var chatViewModel: ChatViewModel
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var markwon: Markwon

    private var tts: TextToSpeech? = null
    private var lastMessageCount: Int = 0

    // 追加: 音声認識関連
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false

    private var lastPersonality: String? = null
    private var selectedImage: ImageAttachment? = null

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
        setupRecyclerView()
        setupClickListeners()
        setupTextToSpeech()
        setupSpeechRecognizer() // 追加
        setupImagePreview()
        setupInsetsHandling()
        observeViewModel()
        // 起動時の性格を記録
        lastPersonality = com.example.voiceapp.ui.settings.SettingsFragment.getPersonality(requireContext())
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

    private fun setupTextToSpeech() {
        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.JAPANESE)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(requireContext(), "音声合成で日本語がサポートされていません", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(requireContext(), "TTS初期化に失敗しました", Toast.LENGTH_SHORT).show()
            }
        }
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
                if (messages.size > previousCount) {
                    val newMessages = messages.subList(previousCount, messages.size)
                    newMessages.filter { !it.isUser }.forEach { speakOut(it.content) }
                }
                lastMessageCount = messages.size
            }
        }

        chatViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.tvGenerating.isVisible = isLoading
            binding.btnSend.isEnabled = !isLoading
            binding.btnAttach.isEnabled = !isLoading
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
        return when (SettingsFragment.getPersonality(requireContext())) {
            //おちゃめ
            "playful" -> """
                あなたは${agentName}。${userName}と日本語で会話するAIアシスタントです。
                ・自分を指すときは一人称「私」を使います。必要に応じて冒頭で「${agentName}です」と名乗っても構いません。
                ・${userName}の名前を適切に呼びかけ、親しみやすく対応します。
                スタイル: おちゃめで親しみやすく、軽いユーモアや絵文字を時々交えます(例: 😊, ✨ を1つ程度)。
                ただし冗長にならず、要点は簡潔・明瞭に。安全で丁寧な表現を心掛けてください。
                依頼があれば詳しく、無ければ簡潔に答えます。
                返答はマークダウン形式で行ってください。
            """.trimIndent()
            //客観的
            "objective" -> """
                あなたは${agentName}。${userName}と日本語で会話するAIアシスタントです。
                ・自分を指すときは一人称「私」を使います。必要に応じて冒頭で「${agentName}です」と名乗っても構いません。
                ・${userName}の名前を必要に応じて用い、丁寧に対応します。
                スタイル: 客観的・中立・簡潔。事実ベースで不要な感情表現は避けます。
                根拠が曖昧な場合は推測と明示し、不確実性を伝えます。必要に応じて箇条書きや手順で整理します。
                返答はマークダウン形式で行ってください。
            """.trimIndent()
            //優しく
            else -> """
                あなたは${agentName}。${userName}と日本語で会話するAIアシスタントです。
                ・自分を指すときは一人称「私」を使います。必要に応じて冒頭で「${agentName}です」と名乗っても構いません。
                ・${userName}の気持ちに配慮し、名前を添えた丁寧な呼びかけを行います。
                スタイル: 優しく丁寧で共感的。相手の意図をくみ取り、安心感のある言い回しを心掛けます。
                長くなり過ぎないように配慮しつつ、役立つ補足がある場合は短い提案を添えます。
                返答はマークダウン形式で行ってください。
            """.trimIndent()
        }
    }

    private fun speakOut(text: String) {
        if (text.isBlank()) return
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, System.currentTimeMillis().toString())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        tts?.stop()
        tts?.shutdown()
        tts = null
        speechRecognizer?.destroy()
        speechRecognizer = null
        clearSelectedImage()
        _binding = null
    }
}
