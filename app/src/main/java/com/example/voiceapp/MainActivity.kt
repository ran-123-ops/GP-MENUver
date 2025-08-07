package com.example.voiceapp

import android.os.Bundle
import android.view.Menu
import android.view.View
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.voiceapp.databinding.ActivityMainBinding
import com.example.voiceapp.ui.settings.SettingsFragment
import android.widget.LinearLayout

class MainActivity : AppCompatActivity(), com.example.voiceapp.ui.settings.SettingsFragment.OnSettingsSavedListener {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

//        binding.appBarMain.fab.setOnClickListener { view ->
//            Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                .setAction("Action", null)
//                .setAnchorView(R.id.fab).show()
//        }
        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home, R.id.nav_chat, R.id.nav_gallery, R.id.nav_slideshow
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // ナビゲーションの遷移リスナーを追加してメニュー選択状態を正しく管理
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // 現在の画面に応じてメニューの選択状態を更新
            when (destination.id) {
                R.id.nav_home -> {
                    navView.setCheckedItem(R.id.nav_home)
                }
                R.id.nav_chat -> {
                    navView.setCheckedItem(R.id.nav_chat)
                }
                R.id.nav_gallery -> {
                    navView.setCheckedItem(R.id.nav_gallery)
                }
                R.id.nav_slideshow -> {
                    navView.setCheckedItem(R.id.nav_slideshow)
                }
                R.id.action_settings -> {
                    // 設定画面では何も選択しない
                    navView.setCheckedItem(View.NO_ID)
                }
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

        // nav_chatメニュータイトルを動的に変更
        val menu = navView.menu
        val chatMenuItem = menu.findItem(R.id.nav_chat)
        chatMenuItem?.title = "$agentName と会話"
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val navController = findNavController(R.id.nav_host_fragment_content_main)
                navController.navigate(R.id.action_settings)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }

    override fun onSettingsSaved() {
        updateNavHeader()
    }
}