package com.example.voiceapp.ui.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.voiceapp.R
import com.example.voiceapp.databinding.FragmentHomeBinding
import com.example.voiceapp.ui.settings.SettingsFragment
import java.text.SimpleDateFormat
import java.util.*

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val handler = Handler(Looper.getMainLooper())
    private var timeUpdateRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupClickListeners()
        startTimeUpdates()
        updateSystemStatus()
        loadUserInfo()
        loadUsageStats()
    }

    private fun setupUI() {
        // 初期状態の設定
        updateCurrentTime()
    }

    private fun setupClickListeners() {
        // チャット開始ボタン - fragment_chat.xmlに確実に移動
        binding.btnGoToChat.setOnClickListener {
            try {
                findNavController().navigate(R.id.nav_chat)
            } catch (e: Exception) {
                // ナビゲーションエラーの場合のログ出力
                android.util.Log.e("HomeFragment", "Navigation to chat failed", e)
            }
        }

        // 設定ボタン
        binding.btnGoToSettings.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_settings)
            } catch (e: Exception) {
                android.util.Log.e("HomeFragment", "Navigation to settings failed", e)
            }
        }
    }

    private fun startTimeUpdates() {
        timeUpdateRunnable = object : Runnable {
            override fun run() {
                updateCurrentTime()
                handler.postDelayed(this, 60000) // 1分ごとに更新
            }
        }
        handler.post(timeUpdateRunnable!!)
    }

    private fun updateCurrentTime() {
        val formatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
        binding.tvCurrentTime.text = formatter.format(Date())
    }

    private fun updateSystemStatus() {
        // みずやりステータス
        binding.ivWateringStatus.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
        binding.tvWateringStatus.text = "完了"
        binding.tvWateringStatusBadge.text = "OK"
        binding.tvWateringStatusBadge.setBackgroundResource(R.drawable.badge_success)

        // きぶんステータス
        binding.ivMoodStatus.setColorFilter(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
        binding.tvMoodStatus.text = "よい"
        binding.tvMoodStatusBadge.text = "良好"
        binding.tvMoodStatusBadge.setBackgroundResource(R.drawable.badge_success)


    }

    private fun loadUserInfo() {
        // 設定からユーザー名とエージェント名を読み込み
        val userName = SettingsFragment.getUserName(requireContext())
        val agentName = SettingsFragment.getAgentName(requireContext())

        binding.tvUserName.text = userName
        binding.tvAgentName.text = agentName
    }

    private fun loadUsageStats() {
        // SharedPreferencesから使用統計を読み込み
        val prefs = requireContext().getSharedPreferences("usage_stats", Context.MODE_PRIVATE)

        val todayMessages = prefs.getInt("today_messages", 0)
        val totalMessages = prefs.getInt("total_messages", 0)
        val lastUsedTime = prefs.getLong("last_used_time", 0)

        binding.tvTodayMessages.text = todayMessages.toString()
        binding.tvTotalMessages.text = totalMessages.toString()

        if (lastUsedTime > 0) {
            val formatter = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            binding.tvLastUsed.text = formatter.format(Date(lastUsedTime))
        } else {
            binding.tvLastUsed.text = "-"
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    override fun onResume() {
        super.onResume()
        // 画面が表示されるたびに情報を更新
        updateSystemStatus()
        loadUserInfo()
        loadUsageStats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timeUpdateRunnable?.let { handler.removeCallbacks(it) }
        _binding = null
    }
}