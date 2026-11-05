// Copyright 2026 MasuSheet contributors. Dual-licensed under MIT OR Apache-2.0.
//! # MasuSheet Android JNI Bridge (`libmasusheet_jni.so`)
//!
//! **[EN]** Multi-session, panic-safe Rust JNI bridge connecting MasuSheet's Kotlin/Jetpack Compose
//! UI and 2-layer hardware Canvas renderer with the `gridcraft-*` formula & `.xlsx` engine:
//! - Isolates concurrent workbooks and `:window2` processes via bounded `session_id` handles (`SESSIONS`).
//! - Denies `unsafe_code` across all internal modules (`dto`, `viewport`, `objects`), permitting
//!   `#[unsafe(no_mangle)]` exclusively on the 8 `extern "system"` JNI export entry points.
//! - Wraps every JNI boundary in `std::panic::catch_unwind` and clears pending JVM exceptions.
//! - Exports visible vs. hidden sheet index mappings, exact Undo/Redo stack depths, dynamic
//!   scrollable viewport bounds, cross-sheet cell batch reads (`jni.readSheetCells`), ECMA-376
//!   OOXML chart/image/shape persistence, and vector PDF export.
//!
//! **[JA]** MasuSheet の Kotlin / Jetpack Compose UI・2層 Canvas 描画エンジンと Rust 製表計算エンジンを接続する
//! マルチセッション・パニック保護付き JNI ブリッジです：
//! - `session_id` ハンドルにより複数ブックおよび別ウィンドウ（`:window2`）のセッションを独立管理します。
//! - 内部モジュール（`dto`, `viewport`, `objects`）では `unsafe` コードを全面禁止し、8個の JNI エクスポート宣言のみに限定しています。
//! - すべての JNI 境界を `catch_unwind` と JVM 例外クリア処理で保護し、ネイティブ強制終了を防止します。
//! - 非表示シートのインデックス分離、Undo/Redo 深さ、動的ビューポート取得、クロスシート読込、OOXML オブジェクト同期、PDF出力を提供します。

mod dto;
mod objects;
mod viewport;

use std::collections::HashMap;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::{Arc, LazyLock, Mutex};

use gridcraft_engine::cmd::print::{PrintOptions, layouts, render_pdf};
use gridcraft_engine::{DocState, Session, Trust};
use jni::JNIEnv;
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jbyteArray, jint, jlong, jstring};
use serde_json::{Value as Json, json};

use crate::dto::empty_viewport_dto;
use crate::objects::{read_workbook_objects_json, sync_workbook_objects_json};
use crate::viewport::{ViewportRequest, build_viewport_dto, read_sheet_cells_json};

/// [EN] Maximum concurrent workbook sessions retained in `SESSIONS` per OS process to guard against
///      unbounded native memory growth if a caller forgets to close old handles.
/// [JA] プロセス内で同時保持するネイティブセッション数の上限です（ハンドル閉じ忘れによるメモリ枯渇を防止）。
const MAX_CONCURRENT_SESSIONS: usize = 64;

/// [EN] Minimal static fallback JSON returned if serializing or allocating a large JNI string fails.
/// [JA] 大規模 JSON 文字列のシリアライズまたは JNI 確保に失敗した際の最小フォールバック応答です。
const FALLBACK_ERROR_JSON: &str = "{\"ok\":false,\"error\":\"JNI response allocation failed\"}";

/// [EN] Process-global registry of active workbook sessions keyed by positive `session_id` (`0` is
///      lazily allocated only if a legacy single-session wrapper invokes it).
/// [JA] 正の `session_id` をキーとして独立した `Session` を保持するグローバルレジストリです。
static SESSIONS: LazyLock<Mutex<HashMap<i64, Arc<Mutex<Session>>>>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));

/// [EN] Monotonically increasing positive session handle generator (`1, 2, 3, ...`).
/// [JA] 単調増加する正のセッション ID 生成カウンタ（`1` 始まり）です。
static NEXT_SESSION_ID: AtomicI64 = AtomicI64::new(1);

/// [EN] Evicts the lowest positive `session_id` if the session map reaches [`MAX_CONCURRENT_SESSIONS`].
/// [JA] セッション数が上限 [`MAX_CONCURRENT_SESSIONS`] に達した場合、最も古い正のセッション ID を解放します。
fn prune_sessions_if_needed(map: &mut HashMap<i64, Arc<Mutex<Session>>>) {
    while map.len() >= MAX_CONCURRENT_SESSIONS {
        let Some(&oldest_id) = map.keys().filter(|&&k| k > 0).min() else {
            break;
        };
        map.remove(&oldest_id);
    }
}

/// [EN] Looks up an existing session handle (`>= 0`), creating a blank workbook session lazily for
///      valid non-negative handles when needed. Negative handles are rejected (`None`).
/// [JA] 非負の `session_id` に対応するセッションを取得（未作成時は空ブックで遅延初期化）します。
///      負の ID は不正ハンドルとして `None` を返します。
fn get_or_create_session(session_id: i64) -> Option<Arc<Mutex<Session>>> {
    if session_id < 0 {
        return None;
    }
    let mut map = SESSIONS
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    if let Some(existing) = map.get(&session_id) {
        return Some(Arc::clone(existing));
    }
    prune_sessions_if_needed(&mut map);
    let mut s = Session::new();
    let _ = s.new_workbook();
    let arc = Arc::new(Mutex::new(s));
    map.insert(session_id, Arc::clone(&arc));
    Some(arc)
}

/// [EN] Allocates a new isolated workbook session initialized with `sample` (`"blank"` or one of
///      `"sales"`, `"budget"`, `"grades"`, `"repair"`) and returns its positive `session_id`.
/// [JA] 指定サンプル（`"blank"` または組み込みサンプル名）で新しい独立セッションを生成し、正の `session_id` を返します。
pub fn create_session_handle(sample: &str) -> i64 {
    let id = NEXT_SESSION_ID.fetch_add(1, Ordering::Relaxed);
    let mut s = Session::new();
    let clean = sample.trim();
    if clean.is_empty()
        || clean == "blank"
        || s.execute("file.new", json!({"sample": clean})).is_err()
    {
        let _ = s.new_workbook();
    }
    let mut map = SESSIONS
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    prune_sessions_if_needed(&mut map);
    map.insert(id, Arc::new(Mutex::new(s)));
    id
}

/// [EN] Releases a workbook session handle from native memory (`session_id >= 0`).
/// [JA] 指定されたセッションハンドルを解放し、ネイティブメモリから破棄します。
pub fn close_session_handle(session_id: i64) {
    if session_id < 0 {
        return;
    }
    let mut map = SESSIONS
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    map.remove(&session_id);
}

/// [EN] Resets an existing session to a blank or named sample workbook and returns the JSON viewport.
/// [JA] 指定セッションを空白ブックまたは名前付きサンプルで再初期化し、ビューポート JSON を返します。
pub fn init_session(session_id: i64, sample: &str) -> String {
    let Some(arc) = get_or_create_session(session_id) else {
        return serialize_error_viewport(session_id, "Invalid negative session ID");
    };
    let mut s = arc
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    let clean = sample.trim();
    let prev_count = s.documents().len();
    let res = if clean.is_empty() || clean == "blank" {
        let _ = s.new_workbook();
        Ok(())
    } else {
        s.execute("file.new", json!({"sample": clean})).map(|_| ())
    };
    let error = match res {
        Ok(()) => {
            for _ in 0..prev_count {
                if s.documents().len() > 1 {
                    s.close_document(0);
                }
            }
            None
        }
        Err(e) => Some(e.to_string()),
    };
    let dto = build_viewport_dto(
        &s,
        ViewportRequest {
            session_id,
            max_row: 80,
            max_col: 30,
            error,
            result: None,
        },
    );
    serde_json::to_string(&dto).unwrap_or_else(|_| FALLBACK_ERROR_JSON.into())
}

/// [EN] Parses `.xlsx` / `.csv` / `.tsv` / `.ods` / `.html` bytes into `session_id`, preserving the
///      previous document if parsing fails.
/// [JA] バイト配列からワークブックを読み込みます。破損ファイル等で失敗した場合は開いていた既存ブックを維持します。
pub fn open_workbook_bytes_session(session_id: i64, name: &str, raw_bytes: &[u8]) -> String {
    let Some(arc) = get_or_create_session(session_id) else {
        return serialize_error_viewport(session_id, "Invalid negative session ID");
    };
    let safe_name = if name.trim().is_empty() {
        "Book1.xlsx"
    } else {
        name.trim()
    };
    let mut s = arc
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    let error = match gridcraft_engine::io::open_bytes(safe_name, raw_bytes) {
        Ok((wb, _warnings)) => {
            let prev_count = s.documents().len();
            let doc = DocState::from_saved(wb, None, safe_name.to_string(), Trust::FromFile);
            let idx = s.add_document(doc);
            s.set_active(idx);
            for _ in 0..prev_count {
                if s.documents().len() > 1 {
                    s.close_document(0);
                }
            }
            None
        }
        Err(e) => Some(e.to_string()),
    };
    let dto = build_viewport_dto(
        &s,
        ViewportRequest {
            session_id,
            max_row: 120,
            max_col: 45,
            error,
            result: None,
        },
    );
    serde_json::to_string(&dto).unwrap_or_else(|_| FALLBACK_ERROR_JSON.into())
}

/// [EN] Serializes the active workbook of `session_id` to `.xlsx` / `.csv` / `.tsv` / `.ods` / `.html`
///      bytes and marks lossless saves (`Xlsx` / `Json`) clean (`isDirty = false`).
/// [JA] セッション内のワークブックを指定拡張子のバイト列へシリアライズし、`.xlsx` 等の完全保存時は未保存フラグを解除します。
pub fn save_workbook_bytes_session(session_id: i64, name: &str) -> Option<Vec<u8>> {
    let arc = get_or_create_session(session_id)?;
    let clean = if name.trim().is_empty() {
        "Book1.xlsx"
    } else {
        name.trim()
    };
    let safe_name = if gridcraft_engine::io::FileKind::from_path(clean).is_some() {
        clean.to_string()
    } else {
        format!("{clean}.xlsx")
    };
    let kind = gridcraft_engine::io::FileKind::from_path(&safe_name);
    let mut s = arc
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    let doc = s.doc_mut().ok()?;
    let bytes = gridcraft_engine::io::save_bytes(&doc.wb, &safe_name).ok()?;
    if matches!(
        kind,
        Some(gridcraft_engine::io::FileKind::Xlsx | gridcraft_engine::io::FileKind::Json)
    ) {
        doc.saved = doc.wb.clone();
    }
    Some(bytes)
}

/// [EN] Renders the active workbook of `session_id` to vector PDF bytes (`%PDF-...`).
/// [JA] セッション内のワークブックを印刷レイアウト付きベクター PDF バイト列へ出力します。
pub fn export_pdf_bytes_session(session_id: i64) -> Option<Vec<u8>> {
    let arc = get_or_create_session(session_id)?;
    let s = arc
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    let doc = s.active()?;
    let opts = PrintOptions::default();
    let lays = layouts(&doc.wb, &opts);
    let (pdf_bytes, _) = render_pdf(&doc.wb, &lays, &doc.display_title(), "");
    (!pdf_bytes.is_empty()).then_some(pdf_bytes)
}

/// [EN] Executes a command ID (`cell.set`, `edit.undo`, `jni.readSheetCells`,
///      `jni.syncWorkbookObjects`, `jni.readWorkbookObjects`, etc.) on `session_id` and returns
///      the updated JSON viewport snapshot.
/// [JA] 指定セッションに対してエンジンコマンドまたは JNI 専用ブリッジコマンドを実行し、更新後のビューポート JSON を返します。
pub fn execute_command_session(session_id: i64, cmd: &str, params_str: &str) -> String {
    let Some(arc) = get_or_create_session(session_id) else {
        return serialize_error_viewport(session_id, "Invalid negative session ID");
    };
    let mut s = arc
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    let trimmed_params = params_str.trim();
    let params: Json = if trimmed_params.is_empty() {
        json!({})
    } else {
        match serde_json::from_str(trimmed_params) {
            Ok(v) => v,
            Err(e) => {
                let dto = build_viewport_dto(
                    &s,
                    ViewportRequest {
                        session_id,
                        max_row: 120,
                        max_col: 45,
                        error: Some(format!("Invalid JSON parameters for `{cmd}`: {e}")),
                        result: None,
                    },
                );
                return serde_json::to_string(&dto).unwrap_or_else(|_| FALLBACK_ERROR_JSON.into());
            }
        }
    };

    let (error, result) = dispatch_session_command(&mut s, cmd, params);
    let dto = build_viewport_dto(
        &s,
        ViewportRequest {
            session_id,
            max_row: 120,
            max_col: 45,
            error,
            result,
        },
    );
    serde_json::to_string(&dto).unwrap_or_else(|_| FALLBACK_ERROR_JSON.into())
}

/// [EN] Routes a parsed command to UI no-ops, JNI helper handlers, or [`Session::execute`].
/// [JA] 解析済みコマンドを UI 側処理対象・JNI ブリッジ専用ハンドラ・標準エンジンコマンドへ振り分けます。
fn dispatch_session_command(
    s: &mut Session,
    cmd: &str,
    params: Json,
) -> (Option<String>, Option<Json>) {
    match cmd {
        "app.language.japanese" | "app.language.english" | "view.theme" | "view.interfaceScale" => {
            (None, None)
        }
        "jni.readSheetCells" => match read_sheet_cells_json(s, &params) {
            Ok(v) => (None, Some(v)),
            Err(e) => (Some(e), None),
        },
        "jni.syncWorkbookObjects" => match sync_workbook_objects_json(s, &params) {
            Ok(v) => (None, Some(v)),
            Err(e) => (Some(e), None),
        },
        "jni.readWorkbookObjects" => match read_workbook_objects_json(s) {
            Ok(v) => (None, Some(v)),
            Err(e) => (Some(e), None),
        },
        "file.new" => {
            let prev_count = s.documents().len();
            match s.execute(cmd, params) {
                Ok(v) => {
                    for _ in 0..prev_count {
                        if s.documents().len() > 1 {
                            s.close_document(0);
                        }
                    }
                    (None, (!v.is_null()).then_some(v))
                }
                Err(e) => (Some(e.to_string()), None),
            }
        }
        _ => match s.execute(cmd, params) {
            Ok(v) if !v.is_null() => (None, Some(v)),
            Ok(_) => (None, None),
            Err(e) => (Some(e.to_string()), None),
        },
    }
}

/// [EN] Returns the current viewport JSON for `session_id` expanded up to `(max_row, max_col)`.
/// [JA] 指定セッションのビューポート JSON を `(max_row, max_col)` の要求サイズに合わせて返します。
pub fn get_viewport_json_session(session_id: i64, max_row: i32, max_col: i32) -> String {
    let Some(arc) = get_or_create_session(session_id) else {
        return serialize_error_viewport(session_id, "Invalid negative session ID");
    };
    let s = arc
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    let dto = build_viewport_dto(
        &s,
        ViewportRequest {
            session_id,
            max_row: max_row.max(60) as u32,
            max_col: max_col.max(26) as u32,
            error: None,
            result: None,
        },
    );
    serde_json::to_string(&dto).unwrap_or_else(|_| FALLBACK_ERROR_JSON.into())
}

/// [EN] Serializes an [`empty_viewport_dto`] carrying `msg` as its structured error.
/// [JA] エラーメッセージ付きの空 [`ViewportDto`] を JSON 文字列へシリアライズします。
fn serialize_error_viewport(session_id: i64, msg: impl Into<String>) -> String {
    serde_json::to_string(&empty_viewport_dto(session_id, Some(msg.into()), None))
        .unwrap_or_else(|_| FALLBACK_ERROR_JSON.into())
}

/// [EN] Safely decodes a Java `String` into a Rust `String`, clearing any pending JVM exception.
/// [JA] Java の `JString` を安全に Rust `String` へ変換し、JVM 例外が発生した場合はクリアします。
fn read_jstring_safe(env: &mut JNIEnv, input: &JString) -> Result<String, String> {
    if input.is_null() {
        return Ok(String::new());
    }
    match env.get_string(input) {
        Ok(js) => Ok(js.into()),
        Err(e) => {
            if env.exception_check().unwrap_or(false) {
                let _ = env.exception_clear();
            }
            Err(format!("Failed to decode JNI string: {e}"))
        }
    }
}

/// [EN] Allocates a Java `String` for `payload`; if the JVM rejects a huge string allocation,
///      clears the pending exception and falls back to [`FALLBACK_ERROR_JSON`] before returning null.
/// [JA] `payload` から Java 文字列を生成します。メモリ不足等で失敗した場合は JVM 例外を消去したうえで
///      最小エラー JSON 文字列へのフォールバックを試みます。
fn emit_jstring(env: &mut JNIEnv, payload: &str) -> jstring {
    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_clear();
    }
    if let Ok(s) = env.new_string(payload) {
        return s.into_raw();
    }
    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_clear();
    }
    match env.new_string(FALLBACK_ERROR_JSON) {
        Ok(s) => s.into_raw(),
        Err(_) => {
            if env.exception_check().unwrap_or(false) {
                let _ = env.exception_clear();
            }
            std::ptr::null_mut()
        }
    }
}

/// [EN] Panic-safe JNI wrapper for functions returning JSON `jstring`.
/// [JA] JSON 文字列（`jstring`）を返す JNI エントリポイント共通の `catch_unwind` 保護ラッパーです。
fn jni_string_guard<F>(mut env: JNIEnv, f: F) -> jstring
where
    F: FnOnce(&mut JNIEnv) -> String,
{
    let out = match catch_unwind(AssertUnwindSafe(|| f(&mut env))) {
        Ok(json_str) => json_str,
        Err(_) => "{\"ok\":false,\"error\":\"Internal native engine panic\"}".to_string(),
    };
    emit_jstring(&mut env, &out)
}

/// [EN] Panic-safe JNI wrapper for functions returning binary `jbyteArray` (`.xlsx` / `.pdf`).
/// [JA] バイナリ配列（`jbyteArray`）を返す JNI エントリポイント共通の `catch_unwind` 保護ラッパーです。
fn jni_bytes_guard<F>(mut env: JNIEnv, f: F) -> jbyteArray
where
    F: FnOnce(&mut JNIEnv) -> Option<Vec<u8>>,
{
    let maybe_bytes = catch_unwind(AssertUnwindSafe(|| f(&mut env))).ok().flatten();
    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_clear();
    }
    let Some(bytes) = maybe_bytes else {
        return std::ptr::null_mut();
    };
    match env.byte_array_from_slice(&bytes) {
        Ok(arr) => arr.into_raw(),
        Err(_) => {
            if env.exception_check().unwrap_or(false) {
                let _ = env.exception_clear();
            }
            std::ptr::null_mut()
        }
    }
}

// ============================================================================
// MasuSheet JNI Export Symbols (`com.masusheet.app.bridge.MasuSheetNativeBridge`)
// `#[allow(unsafe_code)]` is scoped strictly to these 8 FFI symbol declarations.
// ============================================================================

/// [EN] JNI export for `MasuSheetNativeBridge.nativeCreateSession(sample)`. Returns `-1` on panic.
/// [JA] 新規ネイティブセッションを生成する JNI エントリポイントです（異常時は `0` と衝突しない `-1` を返却）。
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masusheet_app_bridge_MasuSheetNativeBridge_nativeCreateSession(
    mut env: JNIEnv,
    _class: JClass,
    sample: JString,
) -> jlong {
    let id = catch_unwind(AssertUnwindSafe(|| {
        let s = read_jstring_safe(&mut env, &sample).unwrap_or_default();
        create_session_handle(&s)
    }))
    .unwrap_or(-1);
    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_clear();
    }
    id
}

/// [EN] JNI export for `MasuSheetNativeBridge.nativeCloseSession(sessionId)`.
/// [JA] ネイティブセッションを解放する JNI エントリポイントです。
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masusheet_app_bridge_MasuSheetNativeBridge_nativeCloseSession(
    env: JNIEnv,
    _class: JClass,
    session_id: jlong,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        close_session_handle(session_id);
    }));
    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_clear();
    }
}

/// [EN] JNI export for `MasuSheetNativeBridge.nativeInitSession(sessionId, sample)`.
/// [JA] 指定セッションをサンプルまたは空白ブックで初期化する JNI エントリポイントです。
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masusheet_app_bridge_MasuSheetNativeBridge_nativeInitSession(
    env: JNIEnv,
    _class: JClass,
    session_id: jlong,
    sample: JString,
) -> jstring {
    jni_string_guard(env, |env| match read_jstring_safe(env, &sample) {
        Ok(s) => init_session(session_id, &s),
        Err(e) => serialize_error_viewport(session_id, e),
    })
}

/// [EN] JNI export for `MasuSheetNativeBridge.nativeOpenWorkbookBytesSession(sessionId, fileName, bytes)`.
/// [JA] バイト配列からワークブックを開く JNI エントリポイントです。
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masusheet_app_bridge_MasuSheetNativeBridge_nativeOpenWorkbookBytesSession(
    env: JNIEnv,
    _class: JClass,
    session_id: jlong,
    file_name: JString,
    bytes: JByteArray,
) -> jstring {
    jni_string_guard(env, |env| {
        let name = match read_jstring_safe(env, &file_name) {
            Ok(n) => n,
            Err(e) => return serialize_error_viewport(session_id, e),
        };
        if bytes.is_null() {
            return serialize_error_viewport(session_id, "Workbook byte array is null");
        }
        match env.convert_byte_array(&bytes) {
            Ok(raw) => open_workbook_bytes_session(session_id, &name, &raw),
            Err(e) => {
                if env.exception_check().unwrap_or(false) {
                    let _ = env.exception_clear();
                }
                serialize_error_viewport(session_id, format!("Failed to read byte array: {e}"))
            }
        }
    })
}

/// [EN] JNI export for `MasuSheetNativeBridge.nativeSaveWorkbookBytesSession(sessionId, fileName)`.
/// [JA] ワークブックを指定形式のバイト配列へ保存する JNI エントリポイントです。
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masusheet_app_bridge_MasuSheetNativeBridge_nativeSaveWorkbookBytesSession(
    env: JNIEnv,
    _class: JClass,
    session_id: jlong,
    file_name: JString,
) -> jbyteArray {
    jni_bytes_guard(env, |env| {
        let name = read_jstring_safe(env, &file_name).ok()?;
        save_workbook_bytes_session(session_id, &name)
    })
}

/// [EN] JNI export for `MasuSheetNativeBridge.nativeExportPdfBytesSession(sessionId)`.
/// [JA] ワークブックを PDF バイト配列へ出力する JNI エントリポイントです。
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masusheet_app_bridge_MasuSheetNativeBridge_nativeExportPdfBytesSession(
    env: JNIEnv,
    _class: JClass,
    session_id: jlong,
) -> jbyteArray {
    jni_bytes_guard(env, |_| export_pdf_bytes_session(session_id))
}

/// [EN] JNI export for `MasuSheetNativeBridge.nativeExecuteCommandSession(sessionId, commandId, paramsJson)`.
/// [JA] 指定セッションでコマンドを実行し最新ビューポート JSON を返す JNI エントリポイントです。
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masusheet_app_bridge_MasuSheetNativeBridge_nativeExecuteCommandSession(
    env: JNIEnv,
    _class: JClass,
    session_id: jlong,
    command_id: JString,
    params_json: JString,
) -> jstring {
    jni_string_guard(env, |env| {
        let cmd = match read_jstring_safe(env, &command_id) {
            Ok(c) => c,
            Err(e) => return serialize_error_viewport(session_id, e),
        };
        let params_str = match read_jstring_safe(env, &params_json) {
            Ok(p) => p,
            Err(e) => return serialize_error_viewport(session_id, e),
        };
        execute_command_session(session_id, &cmd, &params_str)
    })
}

/// [EN] JNI export for `MasuSheetNativeBridge.nativeGetViewportJsonSession(sessionId, maxRow, maxCol)`.
/// [JA] 指定セッションのビューポート JSON を取得する JNI エントリポイントです。
#[allow(unsafe_code)]
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masusheet_app_bridge_MasuSheetNativeBridge_nativeGetViewportJsonSession(
    env: JNIEnv,
    _class: JClass,
    session_id: jlong,
    max_row: jint,
    max_col: jint,
) -> jstring {
    jni_string_guard(env, |_| {
        get_viewport_json_session(session_id, max_row, max_col)
    })
}

#[cfg(test)]
#[allow(clippy::unwrap_used, clippy::expect_used)]
mod tests {
    use super::*;

    #[test]
    fn isolated_multi_sessions_do_not_clobber_each_other() {
        let s1 = create_session_handle("blank");
        let s2 = create_session_handle("blank");
        assert_ne!(s1, s2);

        let r1 = execute_command_session(s1, "cell.set", r#"{"cell":"A1","input":"Session1"}"#);
        let r2 = execute_command_session(s2, "cell.set", r#"{"cell":"A1","input":"Session2"}"#);
        assert!(r1.contains("\"ok\":true"));
        assert!(r2.contains("\"ok\":true"));

        let v1: Json = serde_json::from_str(&get_viewport_json_session(s1, 60, 26)).unwrap();
        let v2: Json = serde_json::from_str(&get_viewport_json_session(s2, 60, 26)).unwrap();
        assert_eq!(v1["activeDisplayText"], "Session1");
        assert_eq!(v2["activeDisplayText"], "Session2");

        // Negative session IDs are rejected without creating map entries
        let neg: Json = serde_json::from_str(&get_viewport_json_session(-5, 60, 26)).unwrap();
        assert_eq!(neg["ok"], false);

        close_session_handle(s1);
        close_session_handle(s2);
    }

    #[test]
    fn invalid_json_params_rejected_and_failed_new_preserves_document() {
        let sid = create_session_handle("blank");
        let _ = execute_command_session(sid, "cell.set", r#"{"cell":"B2","input":"KeepMe"}"#);

        let bad_json = execute_command_session(sid, "cell.set", "{not-valid-json");
        let parsed_bad: Json = serde_json::from_str(&bad_json).unwrap();
        assert_eq!(parsed_bad["ok"], false);

        let bad_new = init_session(sid, "nonexistent_sample_xyz");
        let parsed_new: Json = serde_json::from_str(&bad_new).unwrap();
        assert_eq!(parsed_new["ok"], false);

        let read_res = execute_command_session(sid, "jni.readSheetCells", "{}");
        let parsed_read: Json = serde_json::from_str(&read_res).unwrap();
        assert_eq!(parsed_read["ok"], true);
        let cells = parsed_read["result"]["cells"].as_array().unwrap();
        assert!(cells.iter().any(|c| c["t"] == "KeepMe"));

        close_session_handle(sid);
    }

    #[test]
    fn cross_sheet_read_sheet_cells_supports_kotlin_and_engine_params() {
        let sid = create_session_handle("blank");
        let _ = execute_command_session(sid, "cell.set", r#"{"cell":"A1","input":"Region"}"#);
        let _ = execute_command_session(sid, "cell.set", r#"{"cell":"B1","input":"Amount"}"#);
        let _ = execute_command_session(sid, "cell.set", r#"{"cell":"A2","input":"Tokyo"}"#);
        let _ = execute_command_session(sid, "cell.set", r#"{"cell":"B2","input":"=100+250"}"#);
        let _ = execute_command_session(sid, "home.bold", r#"{"range":"A1:B1","on":true}"#);

        // Switch active sheet to a second sheet ("PivotSheet")
        let _ = execute_command_session(sid, "home.insertSheet", r#"{"name":"PivotSheet"}"#);
        let _ = execute_command_session(sid, "sheet.activate", r#"{"sheet":"PivotSheet"}"#);

        // Read sheet 0 using Kotlin's exact parameter keys (`sheet`, `maxRows`, `maxCols`)
        let raw = execute_command_session(
            sid,
            "jni.readSheetCells",
            r#"{"sheet":0,"maxRows":500,"maxCols":50}"#,
        );
        let parsed: Json = serde_json::from_str(&raw).unwrap();
        assert_eq!(parsed["ok"], true);
        // Active sheet remains PivotSheet (index 1)
        assert_eq!(parsed["sheetIndex"], 1);
        // Result payload contains sheet 0's styled & formula-evaluated cells
        assert_eq!(parsed["result"]["sheetIndex"], 0);
        let cells = parsed["result"]["cells"].as_array().unwrap();
        let b2 = cells
            .iter()
            .find(|c| c["r"] == 1 && c["c"] == 1)
            .expect("B2 cell");
        assert_eq!(b2["t"], "350");
        assert_eq!(b2["e"], "=100+250");
        assert_eq!(b2["n"], 350.0);

        let a1 = cells
            .iter()
            .find(|c| c["r"] == 0 && c["c"] == 0)
            .expect("A1 cell");
        assert_eq!(a1["t"], "Region");
        assert!((a1["fl"].as_u64().unwrap_or(0) & 1) == 1);

        close_session_handle(sid);
    }

    #[test]
    fn hidden_sheets_and_save_dirty_flag_behave_accurately() {
        let sid = create_session_handle("blank");
        let _ = execute_command_session(sid, "home.insertSheet", r#"{"name":"Second"}"#);
        let _ = execute_command_session(sid, "home.insertSheet", r#"{"name":"Third"}"#);
        let _ = execute_command_session(sid, "sheet.activate", r#"{"sheet":"Second"}"#);
        let _ = execute_command_session(sid, "sheet.hide", r#"{}"#);

        let vp: Json = serde_json::from_str(&get_viewport_json_session(sid, 60, 26)).unwrap();
        let vis_indices = vp["visibleSheetIndices"].as_array().unwrap();
        let hid_names = vp["hiddenSheetNames"].as_array().unwrap();
        assert_eq!(vis_indices, &[json!(0), json!(2)]);
        assert_eq!(hid_names, &[json!("Second")]);
        assert_eq!(vp["isDirty"], true);

        let bytes = save_workbook_bytes_session(sid, "Check.xlsx").expect("xlsx bytes");
        assert!(!bytes.is_empty());

        let vp_after: Json = serde_json::from_str(&get_viewport_json_session(sid, 60, 26)).unwrap();
        assert_eq!(vp_after["isDirty"], false);

        let pdf = export_pdf_bytes_session(sid).expect("pdf bytes");
        assert!(pdf.starts_with(b"%PDF"));

        close_session_handle(sid);
    }

    #[test]
    fn sync_and_read_workbook_objects_persists_and_guards_malformed_requests() {
        let sid = create_session_handle("blank");
        let _ = execute_command_session(sid, "cell.set", r#"{"cell":"A1","input":"Month"}"#);
        let _ = execute_command_session(sid, "cell.set", r#"{"cell":"B1","input":"Sales"}"#);
        let _ = execute_command_session(sid, "cell.set", r#"{"cell":"A2","input":"Jan"}"#);
        let _ = execute_command_session(sid, "cell.set", r#"{"cell":"B2","input":"120"}"#);
        let _ = execute_command_session(sid, "cell.set", r#"{"cell":"A3","input":"Feb"}"#);
        let _ = execute_command_session(sid, "cell.set", r#"{"cell":"B3","input":"240"}"#);

        let sync_payload = json!({
            "objects": [
                {
                    "id": "chart_1",
                    "sheetIndex": 0,
                    "kind": "CHART",
                    "subType": "scatter_lines",
                    "xPt": 144.0,
                    "yPt": 44.0,
                    "widthPt": 340.0,
                    "heightPt": 210.0,
                    "title": "Sales Trend",
                    "sourceRangeText": "A1:B3",
                    "chartLabels": ["Jan", "Feb"],
                    "chartValues": [120.0, 240.0],
                    "chartSeries": [
                        {
                            "name": "Sales",
                            "nameCellRef": "B1",
                            "xRangeText": "A2:A3",
                            "yRangeText": "B2:B3",
                            "values": [120.0, 240.0],
                            "xLabels": ["Jan", "Feb"],
                            "color": 0xFF25_63EBu32 as i32
                        }
                    ]
                },
                {
                    "id": "shape_1",
                    "sheetIndex": 0,
                    "kind": "SHAPE",
                    "subType": "rounded_rect",
                    "xPt": 60.0,
                    "yPt": 280.0,
                    "widthPt": 160.0,
                    "heightPt": 80.0,
                    "title": "Note Box",
                    "text": "Note Box",
                    "fillColor": 0xFF1F_9D55u32 as i32,
                    "strokeColor": 0xFF16_3828u32 as i32,
                    "strokeWidthPt": 2.0
                }
            ]
        });

        let sync_res =
            execute_command_session(sid, "jni.syncWorkbookObjects", &sync_payload.to_string());
        let parsed_sync: Json = serde_json::from_str(&sync_res).unwrap();
        assert_eq!(parsed_sync["ok"], true);
        assert_eq!(parsed_sync["result"]["synced"], 2);

        // Malformed sync without `objects` array MUST NOT wipe the 2 synced objects!
        let bad_sync = execute_command_session(sid, "jni.syncWorkbookObjects", "{}");
        let parsed_bad_sync: Json = serde_json::from_str(&bad_sync).unwrap();
        assert_eq!(parsed_bad_sync["ok"], false);

        let xlsx_bytes =
            save_workbook_bytes_session(sid, "ChartRoundtrip.xlsx").expect("xlsx bytes");
        assert!(!xlsx_bytes.is_empty());

        let sid2 = create_session_handle("blank");
        let open_res = open_workbook_bytes_session(sid2, "ChartRoundtrip.xlsx", &xlsx_bytes);
        let parsed_open: Json = serde_json::from_str(&open_res).unwrap();
        assert_eq!(parsed_open["ok"], true);

        let read_objs = execute_command_session(sid2, "jni.readWorkbookObjects", "{}");
        let parsed_objs: Json = serde_json::from_str(&read_objs).unwrap();
        assert_eq!(parsed_objs["ok"], true);
        let objs = parsed_objs["result"]["objects"].as_array().unwrap();
        assert_eq!(objs.len(), 2);
        assert!(
            objs.iter()
                .any(|o| o["kind"] == "CHART" && o["title"] == "Sales Trend")
        );
        assert!(
            objs.iter()
                .any(|o| o["kind"] == "SHAPE" && o["subType"] == "roundRect" && o["text"] == "Note Box")
        );

        close_session_handle(sid);
        close_session_handle(sid2);
    }
}
