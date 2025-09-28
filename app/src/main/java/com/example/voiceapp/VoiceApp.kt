package com.example.voiceapp

import android.app.Application

/**
 * グローバル初期化用の Application クラス。
 * 現状は動的カラーを適用していないため処理は空だが、
 * Manifest から参照されることでアプリ全体のエントリポイントとなる。
 */
class VoiceApp : Application()
