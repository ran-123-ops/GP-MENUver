# VoiceApp

音声入力とAI応答にフォーカスした Android (Kotlin) 向けサンプルアプリです。ナビゲーションドロワーとチャットビューを中心に、音声認識・テキスト読み上げ・画像添付・テーマ切り替えなどの体験をまとめて検証できます。

## 🧭 概要
- **対象OS**: Android 11 (API 30) 以降
- **開発言語**: Kotlin
- **ビルドツール**: Gradle (Android Gradle Plugin + Kotlin DSL)
- **メインパッケージ**: `com.example.voiceapp`

## ✨ 主な機能
- **チャットUI**: Markdown対応の会話ビューとメッセージ一覧表示。
- **音声入力**: `SpeechRecognizer` を使ったストリーム音声認識とマイク制御。
- **テキスト読み上げ**: `TextToSpeech` によるAI返信内容の音声再生。
- **画像添付**: ギャラリーピッカーから画像を選択し、Base64エンコードしてチャットに添付。
- **AIパーソナリティ切り替え**: 設定画面からキャラクター性 (優しい/おちゃめ/客観的) を変更し、会話開始時に反映。
- **ユーザー情報カスタマイズ**: ユーザー名・AI名・アイコンを保存し、ナビゲーションヘッダーに反映。
- **ドロワー操作性向上**: ハンバーガーボタンと右スワイプでドロワーを開閉、遷移先に応じたタイトル更新。
- **ダーク/ライトテーマ両対応**: Material 3 カラーパレットをベースに、システム設定へ追従。
- **エッジ・トゥ・エッジレイアウト**: ステータスバー/ナビゲーションバーとの干渉を避ける余白調整。

## 🛠 セットアップ
1. **開発環境**
   - Android Studio Iguana (2023.2.1) 以降推奨
   - JDK 11
   - Android SDK 35 & Build Tools 35.x

2. **リポジトリ取得**
   ```bash
   git clone <this-repo-url>
   cd voiceapp
   ```

3. **APIキーの設定**
   `local.properties` に以下のキーを追記します。既存の SDK パス設定は残してください。
   ```properties
   OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx
   OPENAI_BASE_URL=https://api.openai.com/
   ```
   - プロキシ経由など独自エンドポイントを使う場合は `OPENAI_BASE_URL` を上書きしてください。
   - キーを含むファイルは `.gitignore` 済みです。

4. **依存関係の同期**
   - Android Studio でプロジェクトを開き、Gradle 同期を実行します。

5. **ビルド**
   - IDE から `app` 構成を選んで *Run*。
   - もしくはターミナルで `gradlew.bat assembleDebug` を実行すると `app/build/outputs/apk/debug/` に APK が生成されます。

## 🚀 使い方
1. アプリ起動後、ホーム画面からチャット画面へ移動します。
2. メッセージ入力欄にテキストを入力、またはマイクボタンで音声入力を開始します。
3. 必要に応じてクリップボタンから画像を添付できます。
4. 送信すると、設定済みのAIパーソナリティで OpenAI API に問い合わせた結果を表示し、読み上げます。
5. ナビゲーションドロワーから設定画面を開くと、ユーザー/エージェント情報および性格を更新できます。

## 🎨 UI/テーマのポイント
- Material 3 の `colorSurface` / `colorPrimary` を基準にライト・ダーク両テーマを調整。
- Edge-to-edge 対応のため、`WindowInsets` に応じて AppBar / NavigationView の余白を動的に調節。
- 端末テーマの変化に追従しつつ、ステータスバー・ナビゲーションバーの色も自動で更新。

## 📦 主なライブラリ
- [AndroidX Navigation Component](https://developer.android.com/guide/navigation) – 画面遷移管理
- [Markwon](https://github.com/noties/Markwon) – Markdown レンダリング
- [Retrofit2 & OkHttp3](https://square.github.io/retrofit/) – OpenAI API 連携
- [Kotlin Coroutines](https://github.com/Kotlin/kotlinx.coroutines) – 非同期処理

## 🧪 テスト
- 単体テスト: `gradlew.bat test`
- Android Instrumented Test: `gradlew.bat connectedAndroidTest`

## 📂 プロジェクト構成 (抜粋)
```
app/
  src/
    main/
      java/com/example/voiceapp/
        MainActivity.kt
        ui/
          chat/...
          home/...
          settings/...
      res/
        layout/
        navigation/
        values/
```

## ⚠️ 注意事項
- OpenAI API を利用するため、課金設定や利用制限にご留意ください。
- 音声認識は端末に依存します。非対応端末ではマイクボタンが無効化されます。
- 最低SDKが30のため、Android 10 以下では動作しません。

## 📄 ライセンス
このプロジェクトのライセンスは未設定です。公開・配布を行う場合は適切なライセンスを追加してください。
