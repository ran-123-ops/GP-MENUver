# 位置情報・時刻コンテキスト機能

## 概要
スマートフォンから現在時刻と位置情報を取得し、AIのシステムプロンプトに自動的に追加する機能です。

## 実装内容

### 1. 現在時刻の取得
- **関数**: `getCurrentDateTime()`
- **データソース**: スマートフォンのシステム時計
- **タイムゾーン**: 日本標準時（JST / Asia/Tokyo）
- **フォーマット**: `yyyy年MM月dd日(E) HH:mm:ss`
- **例**: `2024年01月15日(月) 14:30:45`

### 2. 位置情報の取得
- **関数**: `getCurrentLocationInfo()`
- **データソース**: Google Play Services Location API (FusedLocationProviderClient)
- **精度**: PRIORITY_BALANCED_POWER_ACCURACY（バランス型）
- **取得方法**:
  - GPS、Wi-Fi、モバイルネットワークから最適な位置を取得
  - 非同期で取得し、結果をキャッシュ
  - 権限がない場合は権限要求メッセージを表示

### 3. 権限設定
**AndroidManifest.xml**に以下の権限を追加済み:
```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

### 4. システムプロンプトへの追加
メッセージ送信時に以下の形式でプロンプトに自動追加:
```
【コンテキスト情報】
現在時刻: 2024年01月15日(月) 14:30:45
位置情報: 緯度 35.6812, 経度 139.7671
```

## 動作フロー

1. **アプリ起動時**:
   - `FusedLocationProviderClient` を初期化
   - 初回位置情報取得を開始（バックグラウンド）

2. **メッセージ送信時**:
   - `getCurrentDateTime()` でリアルタイムの時刻を取得
   - `getCurrentLocationInfo()` でキャッシュされた位置情報を取得
   - システムプロンプトに追加してAPIへ送信

3. **位置情報の状態**:
   - **権限なし**: `位置情報: 権限が必要です`
   - **取得中**: `位置情報: 取得中...`
   - **成功**: `位置情報: 緯度 XX.XXXX, 経度 XX.XXXX`
   - **失敗**: `位置情報: 取得エラー`

## AIの活用例

この機能により、AIは以下のような文脈に応じた回答が可能になります:

- **時刻考慮**:
  - 朝なら「おはようございます」
  - 夜なら「遅い時間ですね」
  - 時間帯に応じたおすすめ（食事、活動など）

- **位置考慮**:
  - 地域に応じた情報提供
  - 近くの施設案内（Web検索と組み合わせ）
  - 天気予報の地域指定

## 実装ファイル

### 主要ファイル
- `app/src/main/java/com/example/voiceapp/ui/chat/ChatFragment.kt`
  - `getCurrentDateTime()`: 時刻取得
  - `getCurrentLocationInfo()`: 位置情報取得
  - `buildSystemPrompt()`: プロンプト構築

### 依存関係
- `build.gradle.kts`:
  ```kotlin
  implementation("com.google.android.gms:play-services-location:21.0.1")
  ```

## セキュリティとプライバシー

- 位置情報は実行時に権限をリクエスト
- ユーザーが拒否した場合は位置情報なしで動作
- 位置情報はデバイス内でのみ処理、OpenAI APIには座標のみ送信
- GPSの精度はバランス型で電力消費を抑制

## テスト方法

1. アプリを起動
2. 位置情報の権限リクエストを許可
3. チャットでメッセージを送信
4. AIの返答に時刻や位置に関連する内容が含まれるか確認

例: 「今何時？」→ AIが正確な時刻を回答
例: 「今どこにいる？」→ AIが座標情報を元に場所を説明
