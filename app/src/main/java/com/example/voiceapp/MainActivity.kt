package com.example.voiceapp

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.voiceapp.databinding.ActivityMainBinding
import com.example.voiceapp.ui.settings.SettingsFragment
import android.widget.LinearLayout
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import kotlin.math.abs
import com.google.android.material.color.MaterialColors

class MainActivity : AppCompatActivity(), com.example.voiceapp.ui.settings.SettingsFragment.OnSettingsSavedListener {

    private lateinit var binding: ActivityMainBinding
    private var swipeStartX: Float = 0f
    private var swipeStartY: Float = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val surfaceColor = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurface)
        window.statusBarColor = surfaceColor
        window.navigationBarColor = surfaceColor
        WindowCompat.getInsetsController(window, binding.root).isAppearanceLightStatusBars =
            resources.getBoolean(R.bool.isLightTheme)

        val initialAppBarPaddingTop = binding.appBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = initialAppBarPaddingTop + systemBars.top)
            insets
        }

        val initialNavViewPaddingTop = binding.navView.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.navView) { view, insets ->
            view.updatePadding(top = initialNavViewPaddingTop)
            insets
        }

        val navHostFragment = findViewById<View>(R.id.nav_host_fragment_content_main)
        val initialNavHostPaddingBottom = navHostFragment.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(navHostFragment) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = initialNavHostPaddingBottom + systemBars.bottom)
            insets
        }

//        binding.appBarMain.fab.setOnClickListener { view ->
//            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                .setAction("Action", null)
//                .setAnchorView(R.id.fab).show()
//        }
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        navView.setupWithNavController(navController)

        // メニューボタンのクリックリスナー
        binding.menuButton.setOnClickListener {
            binding.drawerLayout.openDrawer(binding.navView)
        }

        // ナビゲーションの遷移リスナーを追加してメニュー選択状態を正しく管理
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // 現在の画面に応じてメニューの選択状態を更新
            when (destination.id) {
                R.id.nav_home -> navView.setCheckedItem(R.id.nav_home)
                R.id.nav_chat -> navView.setCheckedItem(R.id.nav_chat)
                R.id.nav_gallery -> navView.setCheckedItem(R.id.nav_gallery)
                R.id.nav_slideshow -> navView.setCheckedItem(R.id.nav_slideshow)
                R.id.action_settings -> navView.setCheckedItem(View.NO_ID)
                else -> Unit
            }

            if (destination.id == R.id.nav_chat) {
                // チャット画面のときはエージェント名をタイトルに表示
                binding.pageTitle.text = SettingsFragment.getAgentName(this)
            } else {
                val resolvedLabel = destination.label?.takeIf { it.isNotEmpty() }
                    ?: getString(R.string.app_name)
                binding.pageTitle.text = resolvedLabel
            }

            updateNavHeader()
        }

        // ナビゲーションメニューのクリックリスナーを追加
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    // homeへの確実な遷移処理
                    if (navController.currentDestination?.id != R.id.nav_home) {
                        navController.popBackStack(R.id.nav_home, false)
                        if (navController.currentDestination?.id != R.id.nav_home) {
                            navController.navigate(R.id.nav_home)
                        }
                    }
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_chat -> {
                    if (navController.currentDestination?.id != R.id.nav_chat) {
                        navController.navigate(R.id.nav_chat)
                    }
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_gallery -> {
                    if (navController.currentDestination?.id != R.id.nav_gallery) {
                        navController.navigate(R.id.nav_gallery)
                    }
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.nav_slideshow -> {
                    if (navController.currentDestination?.id != R.id.nav_slideshow) {
                        navController.navigate(R.id.nav_slideshow)
                    }
                    binding.drawerLayout.closeDrawers()
                    true
                }
                R.id.action_settings -> {
                    navController.navigate(R.id.action_settings)
                    binding.drawerLayout.closeDrawers()
                    true
                }
                else -> false
            }
        }

        // 初期状態でhomeを選択状態にする
        navView.setCheckedItem(R.id.nav_home)
        updateNavHeader()
    }

    override fun onResume() {
        super.onResume()
        updateNavHeader()
    }

    private fun updateNavHeader() {
        val navView: NavigationView = binding.navView
        val headerView = navView.getHeaderView(0)
        val userName = SettingsFragment.getUserName(this)
        val agentName = SettingsFragment.getAgentName(this)
    val userIconUri = SettingsFragment.getUserIconUri(this)

        // ナビゲーションヘッダーのクリック機能を修正
        val headerContainer = headerView.findViewById<LinearLayout>(R.id.nav_header_container)
        headerContainer?.setOnClickListener {
            val navController = findNavController(R.id.nav_host_fragment_content_main)
            // home画面がBackStack上に無い場合も含めて必ずhomeへ遷移
            if (!navController.popBackStack(R.id.nav_home, false)) {
                navController.navigate(R.id.nav_home)
            }
            binding.drawerLayout.closeDrawers() // ドロワーを閉じる
        }

        // id指定ではなく2番目のTextViewを取得
        val titleTextView = headerView.findViewById<TextView>(R.id.nav_header_title)
            ?: headerView.findViewById<TextView>(android.R.id.text1)
            ?: headerView.findViewById<TextView>(headerView.resources.getIdentifier("nav_header_title", "id", packageName))
            ?: headerView.findViewById<TextView>(headerView.resources.getIdentifier("title", "id", packageName))
            ?: headerView.findViewById<TextView>(headerView.resources.getIdentifier("textView", "id", packageName))
            ?: (headerView as? LinearLayout)?.getChildAt(1) as? TextView
        titleTextView?.text = "$userName さん"

        // アイコン画像更新
        val iconView = headerView.findViewById<ImageView>(R.id.imageView)
        if (userIconUri != null) {
            try {
                iconView?.setImageURI(userIconUri)
            } catch (e: Exception) {
                // 無効なURIの場合は無視
            }
        }

        // nav_chatメニュータイトルを動的に変更
        val menu = navView.menu
        val chatMenuItem = menu.findItem(R.id.nav_chat)
        chatMenuItem?.title = "$agentName と会話"
    }

    override fun onSettingsSaved() {
        updateNavHeader()
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        if (navController.currentDestination?.id == R.id.nav_chat) {
            binding.pageTitle.text = SettingsFragment.getAgentName(this)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = ev.x
                swipeStartY = ev.y
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                if (!binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    val density = resources.displayMetrics.density
                    val distanceThreshold = 72f * density
                    val diffX = ev.x - swipeStartX
                    val diffY = ev.y - swipeStartY

                    if (diffX > distanceThreshold &&
                        abs(diffX) > abs(diffY)
                    ) {
                        binding.drawerLayout.openDrawer(GravityCompat.START)
                        return true
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                swipeStartX = 0f
                swipeStartY = 0f
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}