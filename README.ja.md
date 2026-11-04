# MasuSheet（マスシート）

[![CI](https://github.com/ray-dev-light/MasuSheet/actions/workflows/ci.yml/badge.svg)](https://github.com/ray-dev-light/MasuSheet/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0%20OR%20MIT-blue.svg)](./LICENSE-APACHE)

**[English README is available here (README.md)](./README.md)**

**MasuSheet（マスシート）** は、Rust 製スプレッドシート計算エンジン（`libmasusheet_jni.so`）、2層ハードウェアアクセラレーション `Canvas` グリッド描画、および機能別にモジュール分割された Jetpack Compose UI を組み合わせた Android 向けネイティブ表計算アプリケーションです。Android タブレット、Android デスクトップ／ChromeOS のマルチウィンドウ環境、スマートフォンのすべてで軽快に動作するよう設計されています。

![MasuSheet のワークスペース画面（15タブリボン・シート上の範囲参照枠付き散布図グラフ・右クリックメニュー）](./docs/screenshots/masusheet-overview.png)

---

## 主な機能

- **523種類のワークシート関数＆スピル配列数式**: Rust 製の高速計算コアにより、`SUMIFS`、`XLOOKUP`、`INDEX`/`MATCH`、`LET`、`LAMBDA`、`FILTER`、`SORT`、`UNIQUE`、`SEQUENCE`、財務・統計・工学・日付関数など 523 関数をサポート。数式バーでのリアルタイム補完と、セルタップによる色分け参照入力（ポイントモード）に対応しています。
- **2層ハードウェア Canvas グリッド描画（表示倍率 `25%`〜`400%`）**:
  - **Layer 1（静的セルキャッシュ）**: 4象限ウィンドウ枠の固定、画面外起点を含む結合セル、罫線、条件付き書式、スパークライン、折り返し文字列、セル内チェックボックス（`□` / `☑`）をキャッシュビットマップへ描画。
  - **Layer 2（高速オーバーレイ）**: 選択枠、行列の自動調整（AutoFit）・リサイズガイド、数式参照ハイライト、フローティング画像・図形・グラフをミリ秒未満で重ね描き。
  - **タッチ＆デスクトップ操作**: 1本指タッチスクロールと範囲選択モードのワンタップ切替、ピンチ中心維持ズーム、マウスホイール／トラックパッド操作、右クリックメニュー、物理キーボードショートカットに完全対応。
- **15タブリボン UI ＆ Material 3 / クラシック切替**: 基本10タブ（ホーム・挿入・描画・ページレイアウト・数式・データ・校閲・表示・自動化・開発）＋コンテキスト5タブ（テーブル・グラフ・ピボット・図形・図）の全292コマンドを搭載。ライト／ダークテーマおよび Material 3／クラシック UI を即座に切り替えられます。
- **グラフ（全32サブタイプ）・図形・画像ワークフロー**:
  - **10カテゴリ×32種類のグラフ**: 縦棒・折れ線・円／ドーナツ・横棒・面・散布図・株価・レーダー・複合・ウォーターフォールに対応。シート上での X軸／Y軸データ範囲ドラッグ変更、要素別（全体・タイトル・縦軸・横軸・凡例）の文字色設定、軸の最小値・最大値・対数目盛設定を備えています。
  - **画像＆図形編集**: 枠線なしのクリーンな画像貼り付け、`inSampleSize` 縮小デコード＋`LruCache` による省メモリ描画、四隅のアスペクト比維持リサイズ、辺中央の単軸リサイズ、`Shift+クリック` 複数選択と整列、矢印キー `1pt` 微調整、複数オブジェクトの統合 PNG コピーに対応。
- **ピボットテーブル＆テーブルオートフィルタ**: 新規シートまたは既存シートへのピボットテーブル作成、行・列・値（11種の集計関数）・フィルタ配置、小計・総計、デザインプリセット、別シート元表からの再集計に対応。
- **マクロ＆VBAスタジオ**: 操作記録、`For..Next` ループや `&` 文字列連結を含む VBA コード編集、1行ずつのステップ実行デバッガ、統合 Undo スナップショットによる安全なロールバックを搭載。
- **3モード対応 AI スプレッドシート編集パネル**:
  1. **内蔵スマートスクリプト**: API キー不要・オフラインで動く定型集計・表作成・装飾機能。
  2. **外部 Web AI 連携**: シート構造と専用 JSON 仕様をコピーし、普段お使いの Web AI の出力 JSON を貼り付けて一括反映。
  3. **クラウド API ／ ローカル LLM (`127.0.0.1`)**: `Dispatchers.IO` 非同期通信による Gemini・OpenAI・Claude・ローカル LLM 連携（破壊的コマンドを遮断する厳格な許可リスト付き）。
- **複数ブックタブ＆別ウィンドウ分離（`:window2`）**: 複数ファイルのタブ切替に加え、グラフ・画像・ピボットテーブル・カスタム書式を100%保持したまま独立した OS ウィンドウへ分離・再統合できます。
- **日英バイリンガル UI ＆ 多形式入出力**: 日本語・英語のリアルタイム表示切替と、Android Scoped Storage（`MediaStore`）／SAF による `.xlsx`, `.csv`, `.tsv`, `.ods`, `.html`, `.pdf` の入出力に対応。

---

## モジュール構成（全ファイル `[EN]` / `[JA]` コメント完備）

可読性と保守性を高めるため、単一責任のモジュールへ整理し、すべてのファイル・クラス・主要関数に英語（`[EN]`）と日本語（`[JA]`）の解説コメントを記載しています。

```text
MasuSheet/
├── app/src/main/java/com/masusheet/app/
│   ├── MainActivity.kt              # メインActivity ＆ マルチウィンドウ用 SecondaryWorkbookActivity
│   ├── core/                        # 全体設定・厳密A1番地パーサ・60色テーマパレット・数式ポイントモード・日英翻訳辞書
│   ├── storage/                     # Android Keystore AES-256-GCM 暗号化保管・SAF/MediaStore入出力・UUIDハンドオフ
│   ├── model/                       # ビューポート不変スナップショット・シートオブジェクト・ピボット定義・UI状態
│   ├── bridge/                      # マルチセッション対応 Rust JNI ゲートウェイ ＆ 純粋Kotlinフォールバックエンジン
│   ├── catalog/                     # 全15タブ/292コマンド・全523関数・全32種グラフサブタイプのカタログ定義
│   ├── ai/                          # 3モードAIエージェント ＆ マクロ/VBA解析・ステップ実行エンジン
│   ├── viewmodel/                   # 機能分割された MasuSheetViewModel 群（ブック別統合 Undo/Redo 制御）
│   ├── canvas/                      # 2層Canvasグリッド・4象限セル/グラフ描画レンダラ・日本語IME待機コンテナ
│   └── ui/                          # メイン画面・15タブリボン・数式バー/シートタブバー・各種設定ダイアログ群
└── crates/
    ├── core/ formula/ functions/ calc/ model/ xlsx/ chart/ engine/   # Rust 計算エンジンコア
    └── jni/                         # パニック安全（`catch_unwind`）・マルチセッション対応 JNI ブリッジ
```

---

## セキュリティ設計・Google Play プロテクト準拠

- **最小権限の原則**: 宣言権限は任意の AI API 通信用の `android.permission.INTERNET` **1つのみ**です。広範なストレージ権限（`MANAGE_EXTERNAL_STORAGE` 等）、カメラ、マイク、バックグラウンドサービス等は一切要求せず、ファイル入出力はすべてユーザー選択の SAF / `MediaStore` 経由で行います。
- **API キーのハードウェア保護**: AI 用の API キーは **Android Keystore** の AES-256-GCM 鍵で暗号化して保存し、クラウドバックアップ・端末間転送から除外（`data_extraction_rules.xml` / `backup_rules.xml`）したうえで、URL クエリではなく HTTP ヘッダで送信します。
- **通信とプロセス境界の保護**: `usesCleartextTraffic="false"` により外部への平文 HTTP 通信を遮断（ローカル LLM 用の `127.0.0.1` / `localhost` のみ許可）。内部 Activity や `ContentProvider` は `exported="false"` で非公開化し、別ウィンドウ引き継ぎにはワンタイム UUID トークンを使用します。
- **ネイティブ境界の安全性**: Rust ワークスペース全体で `#![forbid(unsafe_code)]` を維持し、JNI 境界（`crates/jni`）の全エントリーポイントを `std::panic::catch_unwind` で保護しています。

---

## ビルドとテスト手順

### 必要環境
- **JDK 17**
- **Android SDK**（`compileSdk = 36`, `minSdk = 26`, `targetSdk = 35`）
- **Rust stable ツールチェーン**（`app/src/main/jniLibs/` に同梱済みの `arm64-v8a` / `x86_64` バイナリをそのまま使う場合は不要、`crates/` を再ビルドする場合に使用）

### Rust 計算コア＆JNI テスト
```bash
cargo test -p gridcraft-core -p gridcraft-numfmt -p gridcraft-formula \
  -p gridcraft-functions -p gridcraft-model -p gridcraft-calc \
  -p gridcraft-xlsx -p gridcraft-chart -p gridcraft-engine -p masusheet-jni
```

### Android 単体テスト・Lint・デバッグ APK ビルド
```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

### 署名付きリリース APK ビルド
秘密鍵（`.jks`）はリポジトリに含めず、環境変数から注入します：
```bash
export MASUSHEET_KEYSTORE_FILE="/path/to/release.jks"
export MASUSHEET_KEYSTORE_PASSWORD="your_store_password"
export MASUSHEET_KEY_ALIAS="masusheet_release"
export MASUSHEET_KEY_PASSWORD="your_key_password"

./gradlew assembleRelease
```

---

## ライセンスと謝辞（OSS Attribution）

MasuSheet は以下のデュアルライセンスのもとで提供されます（いずれかを選択可能）：

- **Apache License, Version 2.0** ([LICENSE-APACHE](./LICENSE-APACHE))
- **MIT License** ([LICENSE-MIT](./LICENSE-MIT))

本プロジェクトの Rust 計算エンジンクレート群（`crates/*`）は、**ArtCraft** によるオープンソースプロジェクト **GridCraft** をベースに構築されています。MasuSheet は独立したオープンソースプロジェクトであり、ArtCraft による公式製品・提携プロジェクトではありません。詳細な著作権表示および改変履歴については [NOTICE](./NOTICE) および [ATTRIBUTION.md](./ATTRIBUTION.md) をご参照ください。
