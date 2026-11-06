// Copyright 2026 MasuSheet contributors. Dual-licensed under MIT OR Apache-2.0.
//! # JNI Wire DTOs & Compact Cell Style Bit-Packing
//!
//! **[EN]** Defines the JSON Data Transfer Objects serialized across JNI to Kotlin
//! (`NativeViewportSnapshot`, `NativeCellModel`, sparklines, validations, comments, hyperlinks,
//! and defined names) together with compact 32-bit ARGB, border, and typography bit-flag encoders.
//!
//! **[JA]** Rust 計算エンジンから Kotlin 側（`NativeViewportSnapshot` / `NativeCellModel`）へ返却する
//! JSON データ転送構造体（DTO）および、32ビット ARGB 色・罫線・文字装飾ビットフラグのエンコーダ群です。

use gridcraft_core::{CellRef, Value};
use gridcraft_model::{
    BorderLine, BorderStyle, Color, HAlign, PatternType, Sheet, Style, Underline, VAlign,
    Visibility, Workbook,
};
use serde::Serialize;
use serde_json::Value as Json;

/// [EN] Compact wire representation of a non-empty or styled worksheet cell.
///      Zero/empty fields are omitted in JSON to keep viewport payloads small and fast to parse.
/// [JA] 値または書式を持つセルの軽量 JSON 転送表現です。既定値（`0` / 空文字列）は省略されます。
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct CellDto {
    /// [EN] 0-based row index. / [JA] 0始まりの行インデックス。
    pub(crate) r: u32,
    /// [EN] 0-based column index. / [JA] 0始まりの列インデックス。
    pub(crate) c: u32,
    /// [EN] Formatted display string. / [JA] 表示形式適用後のセル文字列。
    pub(crate) t: String,
    /// [EN] Formula or raw edit string (omitted when identical to `t`).
    /// [JA] 数式または編集用文字列（`t` と同一の場合は省略）。
    #[serde(skip_serializing_if = "String::is_empty")]
    pub(crate) e: String,
    /// [EN] Optional raw numeric value (populated for `jni.readSheetCells` callers).
    /// [JA] 数値セルの場合の生数値（`jni.readSheetCells` 利用時に付与）。
    #[serde(skip_serializing_if = "Option::is_none")]
    pub(crate) n: Option<f64>,
    /// [EN] 32-bit `0xAARRGGBB` fill color (`0` = transparent/none).
    /// [JA] 32ビット `0xAARRGGBB` 背景塗りつぶし色（`0` は塗りなし）。
    #[serde(skip_serializing_if = "is_zero")]
    pub(crate) bg: u32,
    /// [EN] 32-bit `0xAARRGGBB` font color (`0` = theme default text color).
    /// [JA] 32ビット `0xAARRGGBB` 文字色（`0` はテーマ既定色）。
    #[serde(skip_serializing_if = "is_zero")]
    pub(crate) fg: u32,
    /// [EN] Font size in points (`pt`). / [JA] フォントサイズ（`pt` 単位）。
    pub(crate) fs: f32,
    /// [EN] Bit-packed style & alignment flags (see [`encode_style_flags`]).
    /// [JA] 文字装飾および配置を表すビットフラグ（詳細は [`encode_style_flags`] を参照）。
    #[serde(skip_serializing_if = "is_zero")]
    pub(crate) fl: u32,
    /// [EN] Encoded top border (see [`encode_border`]). / [JA] 上罫線のエンコード値。
    #[serde(skip_serializing_if = "is_zero")]
    pub(crate) bt: u32,
    /// [EN] Encoded right border (see [`encode_border`]). / [JA] 右罫線のエンコード値。
    #[serde(skip_serializing_if = "is_zero")]
    pub(crate) br: u32,
    /// [EN] Encoded bottom border (see [`encode_border`]). / [JA] 下罫線のエンコード値。
    #[serde(skip_serializing_if = "is_zero")]
    pub(crate) bb: u32,
    /// [EN] Encoded left border (see [`encode_border`]). / [JA] 左罫線のエンコード値。
    #[serde(skip_serializing_if = "is_zero")]
    pub(crate) bl: u32,
}

/// [EN] Serde predicate skipping `0` integer fields in JSON output.
/// [JA] 値が `0` のフィールドを JSON 出力から省略するための Serde 判定関数です。
pub(crate) fn is_zero(v: &u32) -> bool {
    *v == 0
}

/// [EN] Evaluated in-cell sparkline descriptor (`line`, `column`, or `winLoss`).
/// [JA] セル内スパークライン（折れ線・縦棒・勝敗）の評価済み描画データです。
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct SparklineDto {
    pub(crate) r: u32,
    pub(crate) c: u32,
    pub(crate) source: String,
    pub(crate) kind: &'static str,
    pub(crate) color: u32,
    pub(crate) markers: bool,
    pub(crate) values: Vec<f64>,
}

/// [EN] Worksheet data-validation rule and pre-resolved dropdown items.
/// [JA] データの入力規則およびリスト選択候補の転送データです。
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ValidationDto {
    pub(crate) range: String,
    pub(crate) kind: &'static str,
    pub(crate) f1: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub(crate) f2: String,
    pub(crate) in_cell_dropdown: bool,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub(crate) input_title: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub(crate) input_message: String,
    pub(crate) show_input: bool,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub(crate) error_title: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub(crate) error_message: String,
    pub(crate) show_error: bool,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub(crate) items: Vec<String>,
}

/// [EN] Cell note/comment metadata. / [JA] セルコメント（メモ）の転送データです。
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct CommentDto {
    pub(crate) cell: String,
    pub(crate) author: String,
    pub(crate) text: String,
    pub(crate) resolved: bool,
}

/// [EN] Cell hyperlink target and optional tooltip. / [JA] セルのハイパーリンク情報です。
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct HyperlinkDto {
    pub(crate) cell: String,
    pub(crate) target: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub(crate) tooltip: String,
}

/// [EN] User-visible workbook defined name (`Name` -> `RefersTo` formula).
/// [JA] ユーザー定義の名前付き範囲（名前と参照先数式）です。
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct DefinedNameDto {
    pub(crate) name: String,
    pub(crate) refers_to: String,
}

/// [EN] Complete immutable worksheet viewport snapshot returned after session init, file open,
///      command execution, or scroll viewport expansion.
/// [JA] セッション初期化・ファイル読込・コマンド実行・スクロール拡張時に Kotlin 側へ返される
///      ワークシートのビューポート全体スナップショットです。
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub(crate) struct ViewportDto {
    pub(crate) ok: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub(crate) error: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub(crate) result: Option<Json>,
    pub(crate) session_id: i64,
    pub(crate) uid: u64,
    pub(crate) revision: u64,
    pub(crate) undo_depth: usize,
    pub(crate) redo_depth: usize,
    pub(crate) title: String,
    pub(crate) is_dirty: bool,
    pub(crate) sheet_index: usize,
    pub(crate) total_sheet_count: usize,
    pub(crate) sheet_name: String,
    pub(crate) sheet_names: Vec<String>,
    pub(crate) visible_sheet_indices: Vec<usize>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub(crate) hidden_sheet_names: Vec<String>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub(crate) hidden_sheet_indices: Vec<usize>,
    pub(crate) used_end_row: u32,
    pub(crate) used_end_col: u32,
    pub(crate) can_undo: bool,
    pub(crate) can_redo: bool,
    pub(crate) active_row: u32,
    pub(crate) active_col: u32,
    pub(crate) sel_start_row: u32,
    pub(crate) sel_start_col: u32,
    pub(crate) sel_end_row: u32,
    pub(crate) sel_end_col: u32,
    pub(crate) active_address: String,
    pub(crate) active_edit_text: String,
    pub(crate) active_display_text: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    pub(crate) active_spill_formula: String,
    #[serde(skip_serializing_if = "is_zero")]
    pub(crate) freeze_rows: u32,
    #[serde(skip_serializing_if = "is_zero")]
    pub(crate) freeze_cols: u32,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub(crate) theme_colors: Vec<u32>,
    pub(crate) col_widths: Vec<f32>,
    pub(crate) row_heights: Vec<f32>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub(crate) hidden_rows: Vec<u32>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub(crate) hidden_cols: Vec<u32>,
    /// [EN] Merged cell regions intersecting the viewport as 0-based inclusive `[start_row, start_col, end_row, end_col]`.
    /// [JA] ビューポートと交差する結合セル範囲（0始まりの両端含む `[開始行, 開始列, 終了行, 終了列]`）。
    pub(crate) merges: Vec<[u32; 4]>,
    /// [EN] Dynamic-array spill regions intersecting the viewport as 0-based inclusive `[start_row, start_col, end_row, end_col]`.
    /// [JA] ビューポートと交差するスピル範囲（0始まりの両端含む `[開始行, 開始列, 終了行, 終了列]`）。
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub(crate) spill_ranges: Vec<[u32; 4]>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub(crate) sparklines: Vec<SparklineDto>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub(crate) validations: Vec<ValidationDto>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub(crate) comments: Vec<CommentDto>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub(crate) hyperlinks: Vec<HyperlinkDto>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    pub(crate) defined_names: Vec<DefinedNameDto>,
    pub(crate) cells: Vec<CellDto>,
}

/// [EN] Constructs a safe fallback [`ViewportDto`] (26 columns × 60 rows) when no active sheet is
///      loaded or when returning a structured JNI parameter validation error.
/// [JA] アクティブなブックが存在しない場合やエラー返却時に使用する安全な空の [`ViewportDto`] を生成します。
pub(crate) fn empty_viewport_dto(
    session_id: i64,
    err: Option<String>,
    result: Option<Json>,
) -> ViewportDto {
    ViewportDto {
        ok: err.is_none(),
        error: err,
        result,
        session_id,
        uid: 0,
        revision: 0,
        undo_depth: 0,
        redo_depth: 0,
        title: "Book1.xlsx".into(),
        is_dirty: false,
        sheet_index: 0,
        total_sheet_count: 1,
        sheet_name: "Sheet1".into(),
        sheet_names: vec!["Sheet1".into()],
        visible_sheet_indices: vec![0],
        hidden_sheet_names: vec![],
        hidden_sheet_indices: vec![],
        used_end_row: 0,
        used_end_col: 0,
        can_undo: false,
        can_redo: false,
        active_row: 0,
        active_col: 0,
        sel_start_row: 0,
        sel_start_col: 0,
        sel_end_row: 0,
        sel_end_col: 0,
        active_address: "A1".into(),
        active_edit_text: String::new(),
        active_display_text: String::new(),
        active_spill_formula: String::new(),
        freeze_rows: 0,
        freeze_cols: 0,
        theme_colors: vec![],
        col_widths: vec![64.0; 26],
        row_heights: vec![20.0; 60],
        hidden_rows: vec![],
        hidden_cols: vec![],
        merges: vec![],
        spill_ranges: vec![],
        sparklines: vec![],
        validations: vec![],
        comments: vec![],
        hyperlinks: vec![],
        defined_names: vec![],
        cells: vec![],
    }
}

/// [EN] Resolves a workbook [`Color`] (RGB, theme index + tint, or indexed color) into an opaque
///      Android ARGB integer (`0xFFRRGGBB`), or `0` if `Color::Auto`.
/// [JA] ワークブックの [`Color`]（RGB・テーマ色＋明暗・インデックス色）を Android 用の不透明 ARGB 整数
///      （`0xFFRRGGBB`）へ解決します（自動色の場合は `0`）。
pub(crate) fn resolve_argb(c: &Color, wb: &Workbook) -> u32 {
    match c.resolve(&wb.theme) {
        Some([r, g, b]) => 0xFF00_0000 | (u32::from(r) << 16) | (u32::from(g) << 8) | u32::from(b),
        None => 0,
    }
}

/// [EN] Packs a cell [`BorderLine`] into a 32-bit integer:
///      - Bits `24..=31`: stroke width in half-pixels (`2` = thin/1px, `4` = medium/2px, `6` = thick/3px)
///      - Bits `0..=23`: 24-bit `0xRRGGBB` border color (default slate `0x64748B` when `Auto`)
///      Returns `0` when `line.style == BorderStyle::None`.
/// [JA] セルの罫線 [`BorderLine`] を32ビット整数へパックします：
///      上位8ビットに線幅（0.5px単位：`2`=細線、`4`=中線、`6`=太線）、下位24ビットに `0xRRGGBB` 色を格納します。
pub(crate) fn encode_border(line: &BorderLine, wb: &Workbook) -> u32 {
    if line.style == BorderStyle::None {
        return 0;
    }
    let width_half_px: u32 = match line.style {
        BorderStyle::None => 0,
        BorderStyle::Hair
        | BorderStyle::Dotted
        | BorderStyle::Dashed
        | BorderStyle::Thin
        | BorderStyle::DashDot
        | BorderStyle::DashDotDot => 2,
        BorderStyle::Medium
        | BorderStyle::MediumDashed
        | BorderStyle::MediumDashDot
        | BorderStyle::MediumDashDotDot
        | BorderStyle::SlantDashDot => 4,
        BorderStyle::Thick | BorderStyle::Double => 6,
    };
    let rgb = match line.color.resolve(&wb.theme) {
        Some([r, g, b]) => (u32::from(r) << 16) | (u32::from(g) << 8) | u32::from(b),
        None => 0x64748B,
    };
    (width_half_px << 24) | rgb
}

/// [EN] Packs font style and cell alignment into a compact 9-bit flag word matching Kotlin's
///      `NativeCellModel.flags` decoder:
///      - Bit 0 (`1`): Bold
///      - Bit 1 (`2`): Italic
///      - Bit 2 (`4`): Underline
///      - Bit 3 (`8`): Strikethrough
///      - Bit 4 (`16`): Wrap text
///      - Bits 5..=6 (`(h_code & 3) << 5`): Horizontal align (`0` = Left, `1` = Center, `2` = Right)
///      - Bits 7..=8 (`(v_code & 3) << 7`): Vertical align (`0` = Top, `1` = Middle, `2` = Bottom)
/// [JA] フォント装飾とセル配置を Kotlin 側 `NativeCellModel.flags` と互換の9ビット整数へエンコードします
///      （`1`=太字、`2`=斜体、`4`=下線、`8`=取消線、`16`=折返し、ビット5..6=水平配置、ビット7..8=垂直配置）。
pub(crate) fn encode_style_flags(style: &Style, val: &Value) -> u32 {
    let mut fl = 0u32;
    if style.font.bold {
        fl |= 1;
    }
    if style.font.italic {
        fl |= 2;
    }
    if style.font.underline != Underline::None {
        fl |= 4;
    }
    if style.font.strike {
        fl |= 8;
    }
    if style.align.wrap {
        fl |= 16;
    }
    let h_code: u32 = match style.align.h {
        HAlign::Center | HAlign::CenterAcross | HAlign::Distributed => 1,
        HAlign::Right => 2,
        HAlign::Left | HAlign::Fill | HAlign::Justify => 0,
        HAlign::General => match val {
            Value::Number(_) => 2,
            Value::Bool(_) | Value::Error(_) => 1,
            _ => 0,
        },
    };
    let v_code: u32 = match style.align.v {
        VAlign::Top => 0,
        VAlign::Center | VAlign::Justify | VAlign::Distributed => 1,
        VAlign::Bottom => 2,
    };
    fl |= (h_code & 3) << 5;
    fl |= (v_code & 3) << 7;
    fl
}

/// [EN] Partitions workbook sheets into `(visible_names, visible_indices, hidden_names, hidden_indices)`
///      so hidden sheets keep their true engine indices without shifting tab selection.
/// [JA] ブック内の全シートを表示シートと非表示シートに分類し、各シート名と実インデックスの組を返します。
pub(crate) fn collect_sheet_tabs(
    wb: &Workbook,
) -> (Vec<String>, Vec<usize>, Vec<String>, Vec<usize>) {
    let mut vis_names = Vec::new();
    let mut vis_indices = Vec::new();
    let mut hid_names = Vec::new();
    let mut hid_indices = Vec::new();
    for (idx, sh) in wb.sheets.iter().enumerate() {
        if sh.visibility == Visibility::Visible {
            vis_names.push(sh.name.clone());
            vis_indices.push(idx);
        } else {
            hid_names.push(sh.name.clone());
            hid_indices.push(idx);
        }
    }
    (vis_names, vis_indices, hid_names, hid_indices)
}

/// [EN] Context bundle passed to [`build_cell_dto`] to avoid long parameter lists.
/// [JA] [`build_cell_dto`] へ渡す描画解決コンテキストです。
pub(crate) struct CellBuildContext<'a> {
    pub(crate) wb: &'a Workbook,
    pub(crate) sh: &'a Sheet,
    pub(crate) sheet_index: usize,
    pub(crate) include_raw_number: bool,
}

/// [EN] Builds a [`CellDto`] for `cref`, resolving number formatting, formulas, conditional
///      formatting overrides, and merged-range outer borders. Returns `None` for completely blank
///      unstyled cells.
/// [JA] 指定セル `cref` の表示文字列・数式・条件付き書式・結合セル外枠罫線を解決し [`CellDto`] を構築します
///      （値も書式も無い完全な空セルの場合は `None` を返します）。
pub(crate) fn build_cell_dto(
    ctx: &CellBuildContext<'_>,
    cref: CellRef,
    cf_cache: &mut Option<gridcraft_engine::cf::CfCache>,
) -> Option<CellDto> {
    let wb = ctx.wb;
    let sh = ctx.sh;
    let style_id = sh.style_id(cref);
    let style = wb.styles.get(style_id);
    let val = sh.value(cref);
    let disp = if !val.is_empty() || sh.show_formulas {
        gridcraft_engine::display::cell_text(wb, sh, cref)
    } else {
        String::new()
    };
    let edit = if !disp.is_empty() {
        let et = gridcraft_engine::display::edit_text(wb, sh, cref);
        if et != disp { et } else { String::new() }
    } else {
        String::new()
    };
    let raw_num = if ctx.include_raw_number {
        match val {
            Value::Number(num) if num.is_finite() => Some(num),
            _ => None,
        }
    } else {
        None
    };

    let mut bg = if style.fill.pattern != PatternType::None {
        let fg_c = resolve_argb(&style.fill.fg, wb);
        if fg_c != 0 {
            fg_c
        } else {
            resolve_argb(&style.fill.bg, wb)
        }
    } else {
        0
    };
    let mut fg = resolve_argb(&style.font.color, wb);
    let bt = encode_border(&style.borders.top, wb);
    let mut br = encode_border(&style.borders.right, wb);
    let mut bb = encode_border(&style.borders.bottom, wb);
    let bl = encode_border(&style.borders.left, wb);
    let mut fl = encode_style_flags(style, &val);

    apply_conditional_format(wb, ctx.sheet_index, cref, cf_cache, &mut bg, &mut fg, &mut fl);

    if let Some(m) = sh.merge_at(cref)
        && m.start == cref
    {
        if br == 0 {
            let right_style = wb.styles.get(sh.style_id(CellRef::new(m.start.row, m.end.col)));
            br = encode_border(&right_style.borders.right, wb);
        }
        if bb == 0 {
            let bot_style = wb.styles.get(sh.style_id(CellRef::new(m.end.row, m.start.col)));
            bb = encode_border(&bot_style.borders.bottom, wb);
        }
    }

    if !disp.is_empty()
        || raw_num.is_some()
        || bg != 0
        || fg != 0
        || bt != 0
        || br != 0
        || bb != 0
        || bl != 0
        || fl != 0
    {
        Some(CellDto {
            r: cref.row,
            c: cref.col,
            t: disp,
            e: edit,
            n: raw_num,
            bg,
            fg,
            fs: style.font.size,
            fl,
            bt,
            br,
            bb,
            bl,
        })
    } else {
        None
    }
}

/// [EN] Applies matching conditional formatting fill/font overrides from `cf_cache` onto a cell.
/// [JA] 条件付き書式キャッシュ（カラースケール・強調表示ルール）の背景色・文字色・太字／斜体を反映します。
fn apply_conditional_format(
    wb: &Workbook,
    sheet_index: usize,
    cref: CellRef,
    cf_cache: &mut Option<gridcraft_engine::cf::CfCache>,
    bg: &mut u32,
    fg: &mut u32,
    fl: &mut u32,
) {
    let Some(cache) = cf_cache else {
        return;
    };
    let Some(cf_look) = cache.look(wb, sheet_index, cref) else {
        return;
    };
    if let Some([r, g, b]) = cf_look.scale_fill {
        *bg = 0xFF00_0000 | (u32::from(r) << 16) | (u32::from(g) << 8) | u32::from(b);
    }
    if let Some(cf_st) = &cf_look.style {
        if cf_st.fill.pattern != PatternType::None {
            let cf_bg = resolve_argb(&cf_st.fill.fg, wb);
            if cf_bg != 0 {
                *bg = cf_bg;
            }
        }
        let cf_fg = resolve_argb(&cf_st.font.color, wb);
        if cf_fg != 0 {
            *fg = cf_fg;
        }
        if cf_st.font.bold {
            *fl |= 1;
        }
        if cf_st.font.italic {
            *fl |= 2;
        }
    }
}
