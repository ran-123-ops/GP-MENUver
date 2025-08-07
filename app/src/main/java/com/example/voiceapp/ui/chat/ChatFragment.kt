package com.example.voiceapp.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.voiceapp.R
import com.example.voiceapp.databinding.FragmentChatBinding

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var chatViewModel: ChatViewModel
    private lateinit var chatAdapter: ChatAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        chatViewModel = ViewModelProvider(this)[ChatViewModel::class.java]
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
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
            if (message.isNotEmpty()) {
                chatViewModel.sendMessage(message)
                binding.etMessage.setText("")
            }
        }
    }

    private fun observeViewModel() {
        chatViewModel.messages.observe(viewLifecycleOwner) { messages ->
            chatAdapter.submitList(messages)
            if (messages.isNotEmpty()) {
                binding.rvMessages.scrollToPosition(messages.size - 1)
            }
        }

        chatViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.btnSend.isEnabled = !isLoading
        }

//        chatViewModel.isApiKeyConfigured.observe(viewLifecycleOwner) { isConfigured ->
//            if (isConfigured) {
//                binding.ivApiStatus.setImageResource(android.R.drawable.ic_dialog_info)
//                binding.ivApiStatus.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
//                binding.tvApiStatus.text = "APIキーが設定されています。チャットを開始できます。"
//                binding.btnSend.isEnabled = true
//            } else {
//                binding.ivApiStatus.setImageResource(android.R.drawable.ic_dialog_alert)
//                binding.ivApiStatus.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
//                binding.tvApiStatus.text = "local.propertiesファイルでOPENAI_API_KEYを設定してください"
//                binding.btnSend.isEnabled = false
//            }
//        }

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
