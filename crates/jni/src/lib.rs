// Copyright 2026 MasuSheet contributors. Dual-licensed under MIT OR Apache-2.0.
//! # MasuSheet Android JNI Bridge (`libmasusheet_jni.so`)
//!
//! **[EN]** Multi-session, panic-safe Rust JNI bridge connecting MasuSheet's Kotlin/Jetpack Compose
//! UI and 2-layer hardware Canvas renderer with the `gridcraft-*` formula & `.xlsx` engine:
//! - Isolates concurrent workbooks and `:window2` processes via `session_id` handles (`SESSIONS`).
//! - Wraps every `extern "system"` JNI boundary in `std::panic::catch_unwind` to prevent `SIGABRT`.
//! - Exports visible vs. hidden sheet index mappings, exact Undo/Redo stack depths, dynamic
//!   scrollable viewport bounds, full-sheet cell batch reads (`jni.readSheetCells`), and PDF export.
//!
//! **[JA]** MasuSheet の Kotlin / Jetpack Compose UI・2層 Canvas 描画エンジンと Rust 製表計算エンジンを接続する
//! マルチセッション・パニック保護付き JNI ブリッジです：
//! - `session_id` ハンドルにより複数ブックおよび別ウィンドウ（`:window2`）のセッションを独立管理します。
//! - すべての JNI 境界を `catch_unwind` で保護し、破損ファイル入力時などのネイティブ強制終了を防止します。
//! - 非表示シートのインデックス分離、正確な Undo/Redo 深さ、動的な大規模ビューポート取得、PDF出力を提供します。
#![allow(unsafe_code)]

use std::collections::{BTreeSet, HashMap};
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::sync::atomic::{AtomicI64, Ordering};
use std::sync::{Arc, LazyLock, Mutex};

use gridcraft_core::{CellRef, RangeRef, Value};
use gridcraft_engine::cmd::print::{PrintOptions, layouts, render_pdf};
use gridcraft_engine::{DocState, Session, Trust};
use gridcraft_model::{
    Anchor, AnchorMode, BorderLine, BorderStyle, Chart, ChartKind, Color, HAlign, Image,
    LegendPos, PatternType, Series, Shape, ShapeFormat, ShapeKind, SparklineKind, Style, VAlign,
    ValidationKind, Visibility, Workbook,
};
use jni::JNIEnv;
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jbyteArray, jint, jlong, jstring};
use serde::Serialize;
use serde_json::{Value as Json, json};

static SESSIONS: LazyLock<Mutex<HashMap<i64, Arc<Mutex<Session>>>>> = LazyLock::new(|| {
    let mut map = HashMap::new();
    let mut s = Session::new();
    let _ = s.execute("file.new", json!({"sample": "sales"}));
    map.insert(0, Arc::new(Mutex::new(s)));
    Mutex::new(map)
});

static NEXT_SESSION_ID: AtomicI64 = AtomicI64::new(1);

fn get_or_create_session(session_id: i64) -> Arc<Mutex<Session>> {
    let mut map = SESSIONS
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    map.entry(session_id)
        .or_insert_with(|| {
            let mut s = Session::new();
            let _ = s.new_workbook();
            Arc::new(Mutex::new(s))
        })
        .clone()
}

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
    map.insert(id, Arc::new(Mutex::new(s)));
    id
}

pub fn close_session_handle(session_id: i64) {
    if session_id == 0 {
        return;
    }
    let mut map = SESSIONS
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    map.remove(&session_id);
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct CellDto {
    r: u32,
    c: u32,
    t: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    e: String,
    #[serde(skip_serializing_if = "is_zero")]
    bg: u32,
    #[serde(skip_serializing_if = "is_zero")]
    fg: u32,
    fs: f32,
    #[serde(skip_serializing_if = "is_zero")]
    fl: u32,
    #[serde(skip_serializing_if = "is_zero")]
    bt: u32,
    #[serde(skip_serializing_if = "is_zero")]
    br: u32,
    #[serde(skip_serializing_if = "is_zero")]
    bb: u32,
    #[serde(skip_serializing_if = "is_zero")]
    bl: u32,
}

fn is_zero(v: &u32) -> bool {
    *v == 0
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SparklineDto {
    r: u32,
    c: u32,
    source: String,
    kind: &'static str,
    color: u32,
    markers: bool,
    values: Vec<f64>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ValidationDto {
    range: String,
    kind: &'static str,
    f1: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    f2: String,
    in_cell_dropdown: bool,
    #[serde(skip_serializing_if = "String::is_empty")]
    input_title: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    input_message: String,
    show_input: bool,
    #[serde(skip_serializing_if = "String::is_empty")]
    error_title: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    error_message: String,
    show_error: bool,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    items: Vec<String>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct CommentDto {
    cell: String,
    author: String,
    text: String,
    resolved: bool,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct HyperlinkDto {
    cell: String,
    target: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    tooltip: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct DefinedNameDto {
    name: String,
    refers_to: String,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct ViewportDto {
    ok: bool,
    #[serde(skip_serializing_if = "Option::is_none")]
    error: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    result: Option<Json>,
    session_id: i64,
    uid: u64,
    revision: u64,
    undo_depth: usize,
    redo_depth: usize,
    title: String,
    is_dirty: bool,
    sheet_index: usize,
    total_sheet_count: usize,
    sheet_name: String,
    sheet_names: Vec<String>,
    visible_sheet_indices: Vec<usize>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    hidden_sheet_names: Vec<String>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    hidden_sheet_indices: Vec<usize>,
    used_end_row: u32,
    used_end_col: u32,
    can_undo: bool,
    can_redo: bool,
    active_row: u32,
    active_col: u32,
    sel_start_row: u32,
    sel_start_col: u32,
    sel_end_row: u32,
    sel_end_col: u32,
    active_address: String,
    active_edit_text: String,
    active_display_text: String,
    #[serde(skip_serializing_if = "String::is_empty")]
    active_spill_formula: String,
    #[serde(skip_serializing_if = "is_zero")]
    freeze_rows: u32,
    #[serde(skip_serializing_if = "is_zero")]
    freeze_cols: u32,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    theme_colors: Vec<u32>,
    col_widths: Vec<f32>,
    row_heights: Vec<f32>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    hidden_rows: Vec<u32>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    hidden_cols: Vec<u32>,
    merges: Vec<[u32; 4]>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    spill_ranges: Vec<[u32; 4]>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    sparklines: Vec<SparklineDto>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    validations: Vec<ValidationDto>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    comments: Vec<CommentDto>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    hyperlinks: Vec<HyperlinkDto>,
    #[serde(skip_serializing_if = "Vec::is_empty")]
    defined_names: Vec<DefinedNameDto>,
    cells: Vec<CellDto>,
}

fn empty_viewport_dto(session_id: i64, err: Option<String>, result: Option<Json>) -> ViewportDto {
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

fn resolve_argb(c: &Color, wb: &Workbook) -> u32 {
    match c.resolve(&wb.theme) {
        Some([r, g, b]) => 0xFF00_0000 | ((r as u32) << 16) | ((g as u32) << 8) | (b as u32),
        None => 0,
    }
}

fn encode_border(line: &BorderLine, wb: &Workbook) -> u32 {
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
        Some([r, g, b]) => ((r as u32) << 16) | ((g as u32) << 8) | (b as u32),
        None => 0x64748B,
    };
    (width_half_px << 24) | rgb
}

fn encode_style_flags(style: &Style, val: &Value) -> u32 {
    let mut fl = 0u32;
    if style.font.bold {
        fl |= 1;
    }
    if style.font.italic {
        fl |= 2;
    }
    if style.font.underline != gridcraft_model::Underline::None {
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

fn collect_sheet_tabs(wb: &Workbook) -> (Vec<String>, Vec<usize>, Vec<String>, Vec<usize>) {
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

fn build_cell_dto(
    wb: &Workbook,
    sh: &gridcraft_model::Sheet,
    sheet_index: usize,
    cref: CellRef,
    cf_cache: &mut Option<gridcraft_engine::cf::CfCache>,
) -> Option<CellDto> {
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

    if let Some(cache) = cf_cache
        && let Some(cf_look) = cache.look(wb, sheet_index, cref)
    {
        if let Some([r, g, b]) = cf_look.scale_fill {
            bg = 0xFF00_0000 | ((r as u32) << 16) | ((g as u32) << 8) | (b as u32);
        }
        if let Some(cf_st) = &cf_look.style {
            if cf_st.fill.pattern != PatternType::None {
                let cf_bg = resolve_argb(&cf_st.fill.fg, wb);
                if cf_bg != 0 {
                    bg = cf_bg;
                }
            }
            let cf_fg = resolve_argb(&cf_st.font.color, wb);
            if cf_fg != 0 {
                fg = cf_fg;
            }
            if cf_st.font.bold {
                fl |= 1;
            }
            if cf_st.font.italic {
                fl |= 2;
            }
        }
    }

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

    if !disp.is_empty() || bg != 0 || fg != 0 || bt != 0 || br != 0 || bb != 0 || bl != 0 || fl != 0 {
        Some(CellDto {
            r: cref.row,
            c: cref.col,
            t: disp,
            e: edit,
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

fn build_viewport_dto(
    session_id: i64,
    s: &Session,
    max_r_req: u32,
    max_c_req: u32,
    err: Option<String>,
    result: Option<Json>,
) -> ViewportDto {
    let Some(doc) = s.active() else {
        return empty_viewport_dto(session_id, err, result);
    };

    let wb = &doc.wb;
    let sheet_index = wb.active_sheet();
    let total_sheet_count = wb.sheets.len();
    let (sheet_names, visible_sheet_indices, hidden_sheet_names, hidden_sheet_indices) =
        collect_sheet_tabs(wb);
    let theme_colors: Vec<u32> = wb
        .theme
        .colors
        .iter()
        .take(10)
        .map(|&c| 0xFF00_0000 | (c & 0x00FF_FFFF))
        .collect();

    let Some(sh) = wb.sheet(sheet_index) else {
        let mut dto = empty_viewport_dto(session_id, Some("No active sheet".into()), result);
        dto.uid = doc.uid;
        dto.revision = doc.revision;
        dto.undo_depth = doc.undo.len();
        dto.redo_depth = doc.redo.len();
        dto.title = doc.display_title();
        dto.is_dirty = doc.is_dirty();
        dto.sheet_index = sheet_index;
        dto.total_sheet_count = total_sheet_count;
        dto.sheet_names = sheet_names;
        dto.visible_sheet_indices = visible_sheet_indices;
        dto.hidden_sheet_names = hidden_sheet_names;
        dto.hidden_sheet_indices = hidden_sheet_indices;
        dto.can_undo = !doc.undo.is_empty();
        dto.can_redo = !doc.redo.is_empty();
        dto.theme_colors = theme_colors;
        return dto;
    };

    let used_opt = sh.used_range();
    let used_end_row = used_opt.map_or(0, |u| u.end.row);
    let used_end_col = used_opt.map_or(0, |u| u.end.col);

    // Dynamic viewport bounds: automatically cover sheets up to 5,000 rows × 200 cols on load,
    // and expand up to 20,000 rows × 512 cols when requested as the user scrolls.
    let auto_row = used_end_row.saturating_add(30).clamp(60, 5_000);
    let req_row = max_r_req.clamp(60, 20_000);
    let sel_row = doc.selection.active.row.saturating_add(30).min(20_000);
    let max_row = auto_row.max(req_row).max(sel_row);

    let auto_col = used_end_col.saturating_add(10).clamp(26, 200);
    let req_col = max_c_req.clamp(26, 512);
    let sel_col = doc.selection.active.col.saturating_add(10).min(512);
    let max_col = auto_col.max(req_col).max(sel_col);

    let mut col_widths = Vec::with_capacity((max_col + 1) as usize);
    let mut hidden_cols = Vec::new();
    for c in 0..=max_col {
        if sh.is_col_hidden(c) {
            col_widths.push(0.0);
            hidden_cols.push(c);
        } else {
            col_widths.push(sh.col_width(c));
        }
    }

    let mut row_heights = Vec::with_capacity((max_row + 1) as usize);
    let mut hidden_rows = Vec::new();
    for r in 0..=max_row {
        if sh.is_row_hidden(r) {
            row_heights.push(0.0);
            hidden_rows.push(r);
        } else {
            row_heights.push(sh.row_height(r));
        }
    }

    let viewport_range = RangeRef::new(CellRef::new(0, 0), CellRef::new(max_row, max_col));
    let mut merges = Vec::new();
    let mut cell_coords: BTreeSet<CellRef> = sh.cells.iter_range(viewport_range).map(|(c, _)| c).collect();
    for m in &sh.merges {
        if m.intersection(&viewport_range).is_some() {
            merges.push([m.start.row, m.start.col, m.end.row, m.end.col]);
            cell_coords.insert(m.start);
        }
    }

    let mut spill_ranges = Vec::new();
    for r in sh.spill_ranges.values() {
        if r.intersection(&viewport_range).is_some() {
            spill_ranges.push([r.start.row, r.start.col, r.end.row, r.end.col]);
        }
    }
    for &c in sh.spill.keys() {
        if viewport_range.contains(c) {
            cell_coords.insert(c);
        }
    }

    let (freeze_rows, freeze_cols) = sh.freeze.unwrap_or((0, 0));
    let active_cell = sh
        .merge_at(doc.selection.active)
        .map(|m| m.start)
        .unwrap_or(doc.selection.active);
    let sel_cur = doc.selection.current();
    let active_address = active_cell.to_string();
    let active_edit_text = gridcraft_engine::display::edit_text(wb, sh, active_cell);
    let active_display_text = gridcraft_engine::display::cell_text(wb, sh, active_cell);
    let active_spill_formula = sh
        .spill_anchor(active_cell)
        .map(|anchor| gridcraft_engine::display::edit_text(wb, sh, anchor))
        .unwrap_or_default();

    let sparklines = collect_sparklines(wb, sh, sheet_index, viewport_range);
    let validations = collect_validations(wb, sh, sheet_index);
    let comments: Vec<CommentDto> = sh
        .comments
        .iter()
        .map(|(c, cmt)| CommentDto {
            cell: c.a1(),
            author: cmt.author.clone(),
            text: cmt.text.clone(),
            resolved: cmt.resolved,
        })
        .collect();
    let hyperlinks: Vec<HyperlinkDto> = sh
        .hyperlinks
        .iter()
        .map(|(c, h)| HyperlinkDto {
            cell: c.a1(),
            target: h.target.clone(),
            tooltip: h.tooltip.clone().unwrap_or_default(),
        })
        .collect();
    let defined_names: Vec<DefinedNameDto> = wb
        .names
        .iter()
        .filter(|n| !n.hidden && !n.name.starts_with("_gc_") && !n.name.starts_with("_sc_"))
        .map(|n| DefinedNameDto {
            name: n.name.clone(),
            refers_to: n.formula.clone(),
        })
        .collect();

    let mut cf_cache = (!sh.cond_formats.is_empty()).then(gridcraft_engine::cf::CfCache::new);
    let mut cells = Vec::with_capacity(cell_coords.len());
    for cref in cell_coords {
        if let Some(cell_dto) = build_cell_dto(wb, sh, sheet_index, cref, &mut cf_cache) {
            cells.push(cell_dto);
        }
    }

    ViewportDto {
        ok: err.is_none(),
        error: err,
        result,
        session_id,
        uid: doc.uid,
        revision: doc.revision,
        undo_depth: doc.undo.len(),
        redo_depth: doc.redo.len(),
        title: doc.display_title(),
        is_dirty: doc.is_dirty(),
        sheet_index,
        total_sheet_count,
        sheet_name: sh.name.clone(),
        sheet_names,
        visible_sheet_indices,
        hidden_sheet_names,
        hidden_sheet_indices,
        used_end_row,
        used_end_col,
        can_undo: !doc.undo.is_empty(),
        can_redo: !doc.redo.is_empty(),
        active_row: active_cell.row,
        active_col: active_cell.col,
        sel_start_row: sel_cur.start.row,
        sel_start_col: sel_cur.start.col,
        sel_end_row: sel_cur.end.row,
        sel_end_col: sel_cur.end.col,
        active_address,
        active_edit_text,
        active_display_text,
        active_spill_formula,
        freeze_rows,
        freeze_cols,
        theme_colors,
        col_widths,
        row_heights,
        hidden_rows,
        hidden_cols,
        merges,
        spill_ranges,
        sparklines,
        validations,
        comments,
        hyperlinks,
        defined_names,
        cells,
    }
}

fn collect_sparklines(
    wb: &Workbook,
    sh: &gridcraft_model::Sheet,
    sheet_index: usize,
    viewport_range: RangeRef,
) -> Vec<SparklineDto> {
    let mut out = Vec::new();
    for sp in &sh.sparklines {
        if viewport_range.contains(sp.cell) {
            let values: Vec<f64> =
                match gridcraft_engine::calc::evaluate(wb, sheet_index, sp.cell, &sp.source) {
                    Value::Array(a) => a.data.iter().filter_map(Value::as_f64).collect(),
                    v => v.as_f64().into_iter().collect(),
                };
            let kind = match sp.kind {
                SparklineKind::Line => "line",
                SparklineKind::Column => "column",
                SparklineKind::WinLoss => "winLoss",
            };
            let c = resolve_argb(&sp.color, wb);
            out.push(SparklineDto {
                r: sp.cell.row,
                c: sp.cell.col,
                source: sp.source.clone(),
                kind,
                color: if c != 0 { c } else { 0xFF2B_7CD3 },
                markers: sp.markers,
                values,
            });
        }
    }
    out
}

fn collect_validations(
    wb: &Workbook,
    sh: &gridcraft_model::Sheet,
    sheet_index: usize,
) -> Vec<ValidationDto> {
    let mut out = Vec::new();
    for dv in &sh.validations {
        let kind_str = match dv.kind {
            ValidationKind::Any => "any",
            ValidationKind::Whole => "whole",
            ValidationKind::Decimal => "decimal",
            ValidationKind::List => "list",
            ValidationKind::Date => "date",
            ValidationKind::Time => "time",
            ValidationKind::TextLength => "textLength",
            ValidationKind::Custom => "custom",
        };
        let items = if dv.kind == ValidationKind::List {
            gridcraft_engine::cmd::data::list_items(wb, sheet_index, dv)
        } else {
            vec![]
        };
        for r in &dv.ranges {
            out.push(ValidationDto {
                range: r.a1(),
                kind: kind_str,
                f1: dv.f1.clone(),
                f2: dv.f2.clone().unwrap_or_default(),
                in_cell_dropdown: dv.in_cell_dropdown,
                input_title: dv.input_title.clone(),
                input_message: dv.input_message.clone(),
                show_input: dv.show_input,
                error_title: dv.error_title.clone(),
                error_message: dv.error_message.clone(),
                show_error: dv.show_error,
                items: items.clone(),
            });
        }
    }
    out
}

/// Reads sparse cells from any sheet in the workbook without switching the active sheet or
/// truncating to the viewport (used by PivotTable refresh and full-sheet CSV/TSV export).
fn read_sheet_cells_json(s: &Session, params: &Json) -> Result<Json, String> {
    let doc = s.active().ok_or_else(|| "No active workbook".to_string())?;
    let wb = &doc.wb;
    let si = params
        .get("sheetIndex")
        .and_then(Json::as_u64)
        .map(|v| v as usize)
        .or_else(|| {
            params
                .get("sheetName")
                .and_then(Json::as_str)
                .and_then(|n| wb.sheets.iter().position(|sh| sh.name.eq_ignore_ascii_case(n)))
        })
        .unwrap_or_else(|| wb.active_sheet());
    let sh = wb
        .sheet(si)
        .ok_or_else(|| format!("Sheet index {si} out of range"))?;
    let used_opt = sh.used_range();
    let default_end_r = used_opt.map_or(0, |u| u.end.row);
    let default_end_c = used_opt.map_or(0, |u| u.end.col);
    let r1 = params.get("startRow").and_then(Json::as_u64).unwrap_or(0) as u32;
    let c1 = params.get("startCol").and_then(Json::as_u64).unwrap_or(0) as u32;
    let r2 = params
        .get("endRow")
        .and_then(Json::as_u64)
        .map_or(default_end_r, |v| v as u32);
    let c2 = params
        .get("endCol")
        .and_then(Json::as_u64)
        .map_or(default_end_c, |v| v as u32);

    let mut coords: BTreeSet<CellRef> = BTreeSet::new();
    if r2 >= r1 && c2 >= c1 {
        let range = RangeRef::new(CellRef::new(r1, c1), CellRef::new(r2, c2));
        for (cref, _) in sh.cells.iter_range(range) {
            coords.insert(cref);
        }
        for &cref in sh.spill.keys() {
            if range.contains(cref) {
                coords.insert(cref);
            }
        }
    }
    let mut entries = Vec::with_capacity(coords.len());
    for cref in coords {
        let disp = gridcraft_engine::display::cell_text(wb, sh, cref);
        let num = match sh.value(cref) {
            Value::Number(n) if n.is_finite() => Some(n),
            _ => None,
        };
        if !disp.is_empty() || num.is_some() {
            entries.push(json!({
                "r": cref.row,
                "c": cref.col,
                "t": disp,
                "n": num,
            }));
        }
    }
    Ok(json!({
        "sheetIndex": si,
        "sheetName": sh.name,
        "usedEndRow": default_end_r,
        "usedEndCol": default_end_c,
        "cells": entries,
    }))
}

fn map_sub_type_to_chart_kind(sub_type: &str) -> (ChartKind, bool) {
    let lower = sub_type.trim().to_ascii_lowercase();
    match lower.as_str() {
        "column_stacked" | "column_3d_stacked" => (ChartKind::ColumnStacked, false),
        "column_100" | "column_3d_100" => (ChartKind::ColumnStacked100, false),
        "bar" | "bar_3d" => (ChartKind::BarClustered, false),
        "bar_stacked" | "bar_3d_stacked" => (ChartKind::BarStacked, false),
        "bar_100" | "bar_3d_100" => (ChartKind::BarStacked100, false),
        "line" | "line_3d" => (ChartKind::Line, false),
        "line_smooth" => (ChartKind::LineMarkers, true),
        "line_markers" => (ChartKind::LineMarkers, false),
        "line_stacked" | "line_100" | "line_markers_stacked" | "line_markers_100" => {
            (ChartKind::LineStacked, false)
        }
        "pie" | "pie_3d" | "pie_exploded" => (ChartKind::Pie, false),
        "donut" | "doughnut" => (ChartKind::Doughnut, false),
        "area" | "area_3d" => (ChartKind::Area, false),
        "area_stacked" | "area_3d_stacked" => (ChartKind::AreaStacked, false),
        "area_100" | "area_3d_100" => (ChartKind::AreaStacked100, false),
        "scatter" => (ChartKind::Scatter, false),
        "scatter_smooth" | "scatter_smooth_no_markers" => (ChartKind::ScatterLines, true),
        "scatter_lines" | "scatter_lines_no_markers" => (ChartKind::ScatterLines, false),
        "bubble" | "bubble_3d" => (ChartKind::Bubble, false),
        "radar" | "radar_markers" | "radar_filled" => (ChartKind::Radar, false),
        "histogram" | "pareto" => (ChartKind::Histogram, false),
        "waterfall" => (ChartKind::Waterfall, false),
        "funnel" => (ChartKind::Funnel, false),
        "treemap" => (ChartKind::Treemap, false),
        "sunburst" => (ChartKind::Sunburst, false),
        "boxwhisker" | "box_whisker" => (ChartKind::BoxWhisker, false),
        "stock" => (ChartKind::Stock, false),
        "combo" | "combo_secondary" => (ChartKind::Combo, false),
        _ => (ChartKind::ColumnClustered, false),
    }
}

fn map_chart_kind_to_sub_type(kind: ChartKind, smooth: bool) -> &'static str {
    match kind {
        ChartKind::ColumnClustered => "column",
        ChartKind::ColumnStacked => "column_stacked",
        ChartKind::ColumnStacked100 => "column_100",
        ChartKind::BarClustered => "bar",
        ChartKind::BarStacked => "bar_stacked",
        ChartKind::BarStacked100 => "bar_100",
        ChartKind::Line => "line",
        ChartKind::LineMarkers => {
            if smooth {
                "line_smooth"
            } else {
                "line_markers"
            }
        }
        ChartKind::LineStacked => "line_stacked",
        ChartKind::Pie => "pie",
        ChartKind::Doughnut => "donut",
        ChartKind::Area => "area",
        ChartKind::AreaStacked => "area_stacked",
        ChartKind::AreaStacked100 => "area_100",
        ChartKind::Scatter => "scatter",
        ChartKind::ScatterLines => {
            if smooth {
                "scatter_smooth"
            } else {
                "scatter_lines"
            }
        }
        ChartKind::Bubble => "bubble",
        ChartKind::Radar => "radar",
        ChartKind::Histogram => "histogram",
        ChartKind::Waterfall => "waterfall",
        ChartKind::Funnel => "funnel",
        ChartKind::Treemap => "treemap",
        ChartKind::Sunburst => "sunburst",
        ChartKind::BoxWhisker => "box_whisker",
        ChartKind::Stock => "stock",
        ChartKind::Combo => "combo",
    }
}

fn format_str_array_literal(items: &[String]) -> String {
    let quoted: Vec<String> = items
        .iter()
        .map(|s| format!("\"{}\"", s.replace('"', "\"\"")))
        .collect();
    format!("{{{}}}", quoted.join(","))
}

fn format_num_array_literal(items: &[f64]) -> String {
    let nums: Vec<String> = items
        .iter()
        .map(|v| {
            if v.is_finite() {
                format!("{v}")
            } else {
                "0".to_string()
            }
        })
        .collect();
    format!("{{{}}}", nums.join(","))
}

/// [EN] Synchronizes MasuSheet floating objects (`CHART`, `IMAGE`, `SHAPE`, `TEXT_BOX`) into the
///      active Rust `Workbook` so `.xlsx` save and PDF export emit genuine ECMA-376 DrawingML/ChartML.
/// [JA] MasuSheet 側のフローティングオブジェクト（グラフ・画像・図形・テキストボックス）を
///      Rust ワークブックモデルへ同期し、Excel 互換の OOXML 描画・グラフ要素および PDF 出力に反映します。
fn sync_workbook_objects_json(s: &mut Session, params: &Json) -> Result<Json, String> {
    let doc = s.doc_mut().map_err(|e| e.to_string())?;
    let wb = Arc::make_mut(&mut doc.wb);
    for sh_arc in &mut wb.sheets {
        let sh = Arc::make_mut(sh_arc);
        sh.charts.clear();
        sh.images.clear();
        sh.shapes.clear();
    }
    let Some(arr) = params.get("objects").and_then(Json::as_array) else {
        return Ok(json!({ "synced": 0 }));
    };

    let mut next_id: u32 = 1;
    let mut synced_count: usize = 0;

    for obj in arr {
        let si = obj
            .get("sheetIndex")
            .and_then(Json::as_u64)
            .unwrap_or(0) as usize;
        if si >= wb.sheets.len() {
            continue;
        }
        let sh = Arc::make_mut(&mut wb.sheets[si]);
        let x_pt = obj
            .get("xPt")
            .and_then(Json::as_f64)
            .unwrap_or(80.0)
            .max(0.0);
        let y_pt = obj
            .get("yPt")
            .and_then(Json::as_f64)
            .unwrap_or(60.0)
            .max(0.0);
        let w_pt = obj
            .get("widthPt")
            .and_then(Json::as_f64)
            .unwrap_or(180.0)
            .max(16.0) as f32;
        let h_pt = obj
            .get("heightPt")
            .and_then(Json::as_f64)
            .unwrap_or(110.0)
            .max(16.0) as f32;
        let col = sh.col_at(x_pt);
        let row = sh.row_at(y_pt);
        let anchor = Anchor {
            cell: CellRef::new(row, col),
            dx: (x_pt - sh.col_left(col)).max(0.0) as f32,
            dy: (y_pt - sh.row_top(row)).max(0.0) as f32,
            width: w_pt,
            height: h_pt,
            mode: AnchorMode::MoveAndSize,
        };

        let kind_str = obj
            .get("kind")
            .and_then(Json::as_str)
            .unwrap_or("SHAPE")
            .trim();
        let sub_type = obj
            .get("subType")
            .and_then(Json::as_str)
            .unwrap_or("rectangle")
            .trim();
        let title_str = obj
            .get("title")
            .and_then(Json::as_str)
            .unwrap_or("")
            .trim()
            .to_string();
        let text_str = obj
            .get("text")
            .and_then(Json::as_str)
            .unwrap_or("")
            .trim()
            .to_string();
        let fill_raw = obj
            .get("fillColor")
            .and_then(Json::as_i64)
            .map(|v| v as u32)
            .unwrap_or(0);
        let stroke_raw = obj
            .get("strokeColor")
            .and_then(Json::as_i64)
            .map(|v| v as u32)
            .unwrap_or(0);
        let stroke_w = obj
            .get("strokeWidthPt")
            .and_then(Json::as_f64)
            .unwrap_or(2.0);

        match kind_str {
            "CHART" => {
                let (chart_kind, smooth_default) = map_sub_type_to_chart_kind(sub_type);
                let top_labels: Vec<String> = obj
                    .get("chartLabels")
                    .and_then(Json::as_array)
                    .map(|a| {
                        a.iter()
                            .filter_map(Json::as_str)
                            .map(ToString::to_string)
                            .collect()
                    })
                    .unwrap_or_default();
                let top_values: Vec<f64> = obj
                    .get("chartValues")
                    .and_then(Json::as_array)
                    .map(|a| a.iter().filter_map(Json::as_f64).collect())
                    .unwrap_or_default();

                let mut series_vec: Vec<Series> = Vec::new();
                if let Some(s_arr) = obj.get("chartSeries").and_then(Json::as_array) {
                    for (idx, s_item) in s_arr.iter().enumerate() {
                        let s_name = s_item
                            .get("name")
                            .and_then(Json::as_str)
                            .unwrap_or("")
                            .trim();
                        let s_name_ref = s_item
                            .get("nameCellRef")
                            .and_then(Json::as_str)
                            .unwrap_or("")
                            .trim();
                        let s_x_range = s_item
                            .get("xRangeText")
                            .and_then(Json::as_str)
                            .unwrap_or("")
                            .trim();
                        let s_y_range = s_item
                            .get("yRangeText")
                            .and_then(Json::as_str)
                            .unwrap_or("")
                            .trim();
                        let s_x_labels: Vec<String> = s_item
                            .get("xLabels")
                            .and_then(Json::as_array)
                            .map(|a| {
                                a.iter()
                                    .filter_map(Json::as_str)
                                    .map(ToString::to_string)
                                    .collect()
                            })
                            .unwrap_or_default();
                        let s_vals: Vec<f64> = s_item
                            .get("values")
                            .and_then(Json::as_array)
                            .map(|a| a.iter().filter_map(Json::as_f64).collect())
                            .unwrap_or_default();
                        let s_col_raw = s_item
                            .get("color")
                            .and_then(Json::as_i64)
                            .map(|v| v as u32)
                            .unwrap_or(0);

                        let name_opt = if !s_name_ref.is_empty() {
                            Some(s_name_ref.to_string())
                        } else if !s_name.is_empty() {
                            Some(s_name.to_string())
                        } else {
                            None
                        };
                        let cat_opt = if !s_x_range.is_empty() {
                            Some(s_x_range.to_string())
                        } else if !s_x_labels.is_empty() {
                            Some(format_str_array_literal(&s_x_labels))
                        } else if !top_labels.is_empty() {
                            Some(format_str_array_literal(&top_labels))
                        } else {
                            None
                        };
                        let val_str = if !s_y_range.is_empty() {
                            s_y_range.to_string()
                        } else if !s_vals.is_empty() {
                            format_num_array_literal(&s_vals)
                        } else if !top_values.is_empty() {
                            format_num_array_literal(&top_values)
                        } else {
                            "{0}".to_string()
                        };

                        series_vec.push(Series {
                            name: name_opt,
                            categories: cat_opt,
                            values: val_str,
                            bubble_sizes: None,
                            color: (s_col_raw != 0).then(|| Color::Rgb(s_col_raw & 0x00FF_FFFF)),
                            secondary: sub_type.eq_ignore_ascii_case("combo_secondary") && idx > 0,
                            kind: (matches!(chart_kind, ChartKind::Combo) && idx > 0)
                                .then_some(ChartKind::LineMarkers),
                            smooth: smooth_default,
                            quartiles: Default::default(),
                            trendlines: Vec::new(),
                        });
                    }
                }
                if series_vec.is_empty() {
                    let cat_opt = (!top_labels.is_empty()).then(|| format_str_array_literal(&top_labels));
                    let val_str = if !top_values.is_empty() {
                        format_num_array_literal(&top_values)
                    } else {
                        "{0}".to_string()
                    };
                    series_vec.push(Series {
                        name: (!title_str.is_empty()).then(|| title_str.clone()),
                        categories: cat_opt,
                        values: val_str,
                        bubble_sizes: None,
                        color: (fill_raw != 0).then(|| Color::Rgb(fill_raw & 0x00FF_FFFF)),
                        secondary: false,
                        kind: None,
                        smooth: smooth_default,
                        quartiles: Default::default(),
                        trendlines: Vec::new(),
                    });
                }

                let src_opt = obj
                    .get("sourceRangeText")
                    .and_then(Json::as_str)
                    .map(str::trim)
                    .filter(|t| !t.is_empty())
                    .map(ToString::to_string);

                sh.charts.push(Chart {
                    id: next_id,
                    kind: chart_kind,
                    anchor,
                    title: (!title_str.is_empty()).then_some(title_str),
                    series: series_vec,
                    legend: LegendPos::Bottom,
                    data_labels: false,
                    gridlines: true,
                    style: 2,
                    x_title: None,
                    y_title: None,
                    source: src_opt,
                    by_rows: false,
                    pivot: None,
                });
                next_id = next_id.saturating_add(1);
                synced_count += 1;
            }
            "IMAGE" => {
                if let Some(b64_str) = obj.get("imageBase64").and_then(Json::as_str)
                    && !b64_str.trim().is_empty()
                    && let Some(bytes) = gridcraft_model::b64::decode(b64_str.trim())
                    && !bytes.is_empty()
                {
                    let mime = if bytes.starts_with(&[0xFF, 0xD8]) {
                        "image/jpeg"
                    } else {
                        "image/png"
                    };
                    let alt_txt = if !title_str.is_empty() {
                        title_str
                    } else {
                        text_str
                    };
                    sh.images.push(Image {
                        id: next_id,
                        anchor,
                        data: Arc::new(bytes),
                        mime: mime.to_string(),
                        alt: alt_txt,
                    });
                    next_id = next_id.saturating_add(1);
                    synced_count += 1;
                }
            }
            _ => {
                let shape_kind = if kind_str == "TEXT_BOX" || sub_type.eq_ignore_ascii_case("textbox") {
                    ShapeKind::TextBox
                } else {
                    match sub_type.to_ascii_lowercase().as_str() {
                        "roundrect" | "round_rect" | "rounded_rect" | "roundedrectangle" => {
                            ShapeKind::RoundedRectangle
                        }
                        "oval" | "circle" | "ellipse" => ShapeKind::Ellipse,
                        "triangle" | "diamond" => ShapeKind::Triangle,
                        "arrow" | "arrow_left" | "arrow_up" | "arrow_down" | "right_arrow"
                        | "left_arrow" => ShapeKind::Arrow,
                        "line" => ShapeKind::Line,
                        _ => ShapeKind::Rectangle,
                    }
                };
                let fill = if fill_raw != 0 {
                    Color::Rgb(fill_raw & 0x00FF_FFFF)
                } else {
                    Color::Auto
                };
                let line = if stroke_raw != 0 {
                    Color::Rgb(stroke_raw & 0x00FF_FFFF)
                } else {
                    Color::Auto
                };
                let body = if !text_str.is_empty() {
                    text_str
                } else {
                    title_str
                };
                sh.shapes.push(Shape {
                    id: next_id,
                    kind: shape_kind,
                    anchor,
                    fill,
                    line,
                    text: body,
                    format: ShapeFormat {
                        no_line: stroke_raw == 0 || stroke_w <= 0.0,
                        ..Default::default()
                    },
                });
                next_id = next_id.saturating_add(1);
                synced_count += 1;
            }
        }
    }

    Ok(json!({ "synced": synced_count }))
}

fn eval_chart_expr_values(wb: &Workbook, sheet_index: usize, expr: &str) -> Vec<Value> {
    let clean = expr.trim().trim_start_matches('=');
    if clean.is_empty() {
        return Vec::new();
    }
    match gridcraft_engine::calc::evaluate(wb, sheet_index, CellRef::new(0, 0), clean) {
        Value::Array(arr) => arr.data.clone(),
        Value::Empty => Vec::new(),
        other => vec![other],
    }
}

/// [EN] Reads standard ECMA-376 OOXML charts, images, and shapes from the active Rust `Workbook`
///      so Excel-authored `.xlsx` files also display their charts/images/shapes on MasuSheet's canvas.
/// [JA] 開いた `.xlsx` ワークブック内の標準 OOXML グラフ・画像・図形を読み取り、MasuSheet キャンバスへ復元します。
fn read_workbook_objects_json(s: &Session) -> Result<Json, String> {
    let doc = s.active().ok_or_else(|| "No active workbook".to_string())?;
    let wb = &doc.wb;
    let mut out: Vec<Json> = Vec::new();
    let palette: [u32; 6] = [
        0xFF25_63EB,
        0xFF16_A34A,
        0xFFEA_580C,
        0xFF93_33EA,
        0xFFDC_2626,
        0xFF08_91B2,
    ];

    for (si, sh) in wb.sheets.iter().enumerate() {
        for ch in &sh.charts {
            let x_pt = sh.col_left(ch.anchor.cell.col) + ch.anchor.dx as f64;
            let y_pt = sh.row_top(ch.anchor.cell.row) + ch.anchor.dy as f64;
            let any_smooth = ch.series.iter().any(|se| se.smooth);
            let sub_type = map_chart_kind_to_sub_type(ch.kind, any_smooth);

            let mut series_json: Vec<Json> = Vec::new();
            let mut primary_labels: Vec<String> = Vec::new();
            let mut primary_values: Vec<f64> = Vec::new();

            for (s_idx, se) in ch.series.iter().enumerate() {
                let vals: Vec<f64> = eval_chart_expr_values(wb, si, &se.values)
                    .iter()
                    .map(|v| v.as_f64().unwrap_or(0.0))
                    .collect();
                let x_labels: Vec<String> = se
                    .categories
                    .as_deref()
                    .map(|cat| {
                        eval_chart_expr_values(wb, si, cat)
                            .into_iter()
                            .map(|v| v.display())
                            .collect()
                    })
                    .unwrap_or_default();

                let (resolved_name, name_cell_ref) = match se.name.as_deref() {
                    Some(raw_n) if !raw_n.trim().is_empty() => {
                        let trimmed = raw_n.trim();
                        let looks_like_ref = trimmed.contains('!')
                            || trimmed.starts_with('$')
                            || CellRef::parse(trimmed).is_some();
                        if looks_like_ref {
                            let disp = eval_chart_expr_values(wb, si, trimmed)
                                .into_iter()
                                .next()
                                .map(|v| v.display())
                                .filter(|d| !d.is_empty())
                                .unwrap_or_else(|| format!("Series {}", s_idx + 1));
                            (disp, trimmed.to_string())
                        } else {
                            (trimmed.to_string(), String::new())
                        }
                    }
                    _ => (format!("Series {}", s_idx + 1), String::new()),
                };

                let s_color = se
                    .color
                    .as_ref()
                    .map(|c| resolve_argb(c, wb))
                    .filter(|&c| c != 0)
                    .unwrap_or(palette[s_idx % palette.len()]);

                let x_range_txt = se
                    .categories
                    .as_deref()
                    .map(str::trim)
                    .filter(|t| !t.is_empty() && !t.starts_with('{'))
                    .unwrap_or("")
                    .to_string();
                let y_range_txt = {
                    let t = se.values.trim();
                    if !t.is_empty() && !t.starts_with('{') {
                        t.to_string()
                    } else {
                        String::new()
                    }
                };

                if s_idx == 0 {
                    primary_labels = x_labels.clone();
                    primary_values = vals.clone();
                }

                series_json.push(json!({
                    "name": resolved_name,
                    "values": vals,
                    "color": s_color as i32,
                    "nameCellRef": name_cell_ref,
                    "xRangeText": x_range_txt,
                    "yRangeText": y_range_txt,
                    "xLabels": x_labels,
                }));
            }

            if primary_labels.is_empty() && !primary_values.is_empty() {
                primary_labels = (1..=primary_values.len())
                    .map(|i| i.to_string())
                    .collect();
            }

            out.push(json!({
                "id": format!("ooxml_chart_{}_{}", si, ch.id),
                "sheetIndex": si,
                "kind": "CHART",
                "subType": sub_type,
                "xPt": x_pt,
                "yPt": y_pt,
                "widthPt": ch.anchor.width.max(120.0),
                "heightPt": ch.anchor.height.max(90.0),
                "title": ch.title.clone().unwrap_or_default(),
                "text": "",
                "fillColor": 0xFFFF_FFFFu32 as i32,
                "strokeColor": 0xFFCB_D5E1u32 as i32,
                "strokeWidthPt": 1.5,
                "sourceRangeText": ch.source.clone().unwrap_or_default(),
                "chartLabels": primary_labels,
                "chartValues": primary_values,
                "chartSeries": series_json,
            }));
        }

        for img in &sh.images {
            if img.data.is_empty() {
                continue;
            }
            let x_pt = sh.col_left(img.anchor.cell.col) + img.anchor.dx as f64;
            let y_pt = sh.row_top(img.anchor.cell.row) + img.anchor.dy as f64;
            out.push(json!({
                "id": format!("ooxml_img_{}_{}", si, img.id),
                "sheetIndex": si,
                "kind": "IMAGE",
                "subType": "image",
                "xPt": x_pt,
                "yPt": y_pt,
                "widthPt": img.anchor.width.max(40.0),
                "heightPt": img.anchor.height.max(40.0),
                "title": img.alt,
                "text": "",
                "fillColor": 0,
                "strokeColor": 0,
                "strokeWidthPt": 0.0,
                "imageBase64": gridcraft_model::b64::encode(&img.data),
            }));
        }

        for sp in &sh.shapes {
            let x_pt = sh.col_left(sp.anchor.cell.col) + sp.anchor.dx as f64;
            let y_pt = sh.row_top(sp.anchor.cell.row) + sp.anchor.dy as f64;
            let (kind_str, sub_str) = match sp.kind {
                ShapeKind::TextBox => ("TEXT_BOX", "textbox"),
                ShapeKind::RoundedRectangle => ("SHAPE", "roundRect"),
                ShapeKind::Ellipse => ("SHAPE", "ellipse"),
                ShapeKind::Triangle => ("SHAPE", "triangle"),
                ShapeKind::Arrow => ("SHAPE", "arrow"),
                ShapeKind::Line => ("SHAPE", "line"),
                _ => ("SHAPE", "rectangle"),
            };
            let fill_argb = resolve_argb(&sp.fill, wb);
            let stroke_argb = if sp.format.no_line {
                0
            } else {
                let c = resolve_argb(&sp.line, wb);
                if c != 0 { c } else { 0xFF16_3828 }
            };
            out.push(json!({
                "id": format!("ooxml_shape_{}_{}", si, sp.id),
                "sheetIndex": si,
                "kind": kind_str,
                "subType": sub_str,
                "xPt": x_pt,
                "yPt": y_pt,
                "widthPt": sp.anchor.width.max(32.0),
                "heightPt": sp.anchor.height.max(24.0),
                "title": sp.text,
                "text": sp.text,
                "fillColor": fill_argb as i32,
                "strokeColor": stroke_argb as i32,
                "strokeWidthPt": if sp.format.no_line { 0.0 } else { 2.0 },
            }));
        }
    }

    Ok(json!({ "objects": out }))
}

pub fn init_session(session_id: i64, sample: &str) -> String {
    let arc = get_or_create_session(session_id);
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
    let err = match res {
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
    let dto = build_viewport_dto(session_id, &s, 80, 30, err, None);
    serde_json::to_string(&dto).unwrap_or_else(|_| "{\"ok\":false}".into())
}

pub fn open_workbook_bytes_session(session_id: i64, name: &str, raw_bytes: &[u8]) -> String {
    let safe_name = if name.trim().is_empty() {
        "Book1.xlsx"
    } else {
        name.trim()
    };
    let arc = get_or_create_session(session_id);
    let mut s = arc
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    let err = match gridcraft_engine::io::open_bytes(safe_name, raw_bytes) {
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
    let dto = build_viewport_dto(session_id, &s, 120, 45, err, None);
    serde_json::to_string(&dto).unwrap_or_else(|_| "{\"ok\":false}".into())
}

pub fn save_workbook_bytes_session(session_id: i64, name: &str) -> Option<Vec<u8>> {
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
    let arc = get_or_create_session(session_id);
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

pub fn export_pdf_bytes_session(session_id: i64) -> Option<Vec<u8>> {
    let arc = get_or_create_session(session_id);
    let s = arc
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    let doc = s.active()?;
    let opts = PrintOptions::default();
    let lays = layouts(&doc.wb, &opts);
    let (pdf_bytes, _) = render_pdf(&doc.wb, &lays, &doc.display_title(), "");
    if pdf_bytes.is_empty() {
        None
    } else {
        Some(pdf_bytes)
    }
}

pub fn execute_command_session(session_id: i64, cmd: &str, params_str: &str) -> String {
    let arc = get_or_create_session(session_id);
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
                    session_id,
                    &s,
                    120,
                    45,
                    Some(format!("Invalid JSON parameters for `{cmd}`: {e}")),
                    None,
                );
                return serde_json::to_string(&dto).unwrap_or_else(|_| "{\"ok\":false}".into());
            }
        }
    };

    let (err, res_json) = match cmd {
        "app.language.japanese" | "app.language.english" | "view.theme" | "view.interfaceScale" => {
            (None, None)
        }
        "jni.readSheetCells" => match read_sheet_cells_json(&s, &params) {
            Ok(v) => (None, Some(v)),
            Err(e) => (Some(e), None),
        },
        "jni.syncWorkbookObjects" => match sync_workbook_objects_json(&mut s, &params) {
            Ok(v) => (None, Some(v)),
            Err(e) => (Some(e), None),
        },
        "jni.readWorkbookObjects" => match read_workbook_objects_json(&s) {
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
    };
    let dto = build_viewport_dto(session_id, &s, 120, 45, err, res_json);
    serde_json::to_string(&dto).unwrap_or_else(|_| "{\"ok\":false}".into())
}

pub fn get_viewport_json_session(session_id: i64, max_row: i32, max_col: i32) -> String {
    let arc = get_or_create_session(session_id);
    let s = arc
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    let dto = build_viewport_dto(
        session_id,
        &s,
        max_row.max(60) as u32,
        max_col.max(26) as u32,
        None,
        None,
    );
    serde_json::to_string(&dto).unwrap_or_else(|_| "{\"ok\":false}".into())
}

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

fn emit_jstring(env: &mut JNIEnv, payload: &str) -> jstring {
    if env.exception_check().unwrap_or(false) {
        let _ = env.exception_clear();
    }
    match env.new_string(payload) {
        Ok(s) => s.into_raw(),
        Err(_) => {
            if env.exception_check().unwrap_or(false) {
                let _ = env.exception_clear();
            }
            std::ptr::null_mut()
        }
    }
}

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
// MasuSheet clean JNI entry points (`com.masusheet.app.bridge.MasuSheetNativeBridge`)
// ============================================================================

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masusheet_app_bridge_MasuSheetNativeBridge_nativeCreateSession(
    mut env: JNIEnv,
    _class: JClass,
    sample: JString,
) -> jlong {
    catch_unwind(AssertUnwindSafe(|| {
        let s = read_jstring_safe(&mut env, &sample).unwrap_or_default();
        create_session_handle(&s)
    }))
    .unwrap_or(0)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masusheet_app_bridge_MasuSheetNativeBridge_nativeCloseSession(
    _env: JNIEnv,
    _class: JClass,
    session_id: jlong,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        close_session_handle(session_id);
    }));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masusheet_app_bridge_MasuSheetNativeBridge_nativeInitSession(
    env: JNIEnv,
    _class: JClass,
    session_id: jlong,
    sample: JString,
) -> jstring {
    jni_string_guard(env, |env| match read_jstring_safe(env, &sample) {
        Ok(s) => init_session(session_id, &s),
        Err(e) => serde_json::to_string(&empty_viewport_dto(session_id, Some(e), None))
            .unwrap_or_else(|_| "{\"ok\":false}".into()),
    })
}

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
            Err(e) => {
                return serde_json::to_string(&empty_viewport_dto(session_id, Some(e), None))
                    .unwrap_or_else(|_| "{\"ok\":false}".into());
            }
        };
        if bytes.is_null() {
            return serde_json::to_string(&empty_viewport_dto(
                session_id,
                Some("Workbook byte array is null".into()),
                None,
            ))
            .unwrap_or_else(|_| "{\"ok\":false}".into());
        }
        match env.convert_byte_array(&bytes) {
            Ok(raw) => open_workbook_bytes_session(session_id, &name, &raw),
            Err(e) => {
                if env.exception_check().unwrap_or(false) {
                    let _ = env.exception_clear();
                }
                serde_json::to_string(&empty_viewport_dto(
                    session_id,
                    Some(format!("Failed to read byte array: {e}")),
                    None,
                ))
                .unwrap_or_else(|_| "{\"ok\":false}".into())
            }
        }
    })
}

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

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masusheet_app_bridge_MasuSheetNativeBridge_nativeExportPdfBytesSession(
    env: JNIEnv,
    _class: JClass,
    session_id: jlong,
) -> jbyteArray {
    jni_bytes_guard(env, |_| export_pdf_bytes_session(session_id))
}

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
            Err(e) => {
                return serde_json::to_string(&empty_viewport_dto(session_id, Some(e), None))
                    .unwrap_or_else(|_| "{\"ok\":false}".into());
            }
        };
        let params_str = match read_jstring_safe(env, &params_json) {
            Ok(p) => p,
            Err(e) => {
                return serde_json::to_string(&empty_viewport_dto(session_id, Some(e), None))
                    .unwrap_or_else(|_| "{\"ok\":false}".into());
            }
        };
        execute_command_session(session_id, &cmd, &params_str)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_masusheet_app_bridge_MasuSheetNativeBridge_nativeGetViewportJsonSession(
    env: JNIEnv,
    _class: JClass,
    session_id: jlong,
    max_row: jint,
    max_col: jint,
) -> jstring {
    jni_string_guard(env, |_| get_viewport_json_session(session_id, max_row, max_col))
}

#[cfg(test)]
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
    fn sync_and_read_workbook_objects_persists_charts_images_shapes_to_xlsx() {
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

        let sync_res = execute_command_session(sid, "jni.syncWorkbookObjects", &sync_payload.to_string());
        let parsed_sync: Json = serde_json::from_str(&sync_res).unwrap();
        assert_eq!(parsed_sync["ok"], true);
        assert_eq!(parsed_sync["result"]["synced"], 2);

        let xlsx_bytes = save_workbook_bytes_session(sid, "ChartRoundtrip.xlsx").expect("xlsx bytes");
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
        assert!(objs.iter().any(|o| o["kind"] == "CHART" && o["title"] == "Sales Trend"));
        assert!(objs.iter().any(|o| o["kind"] == "SHAPE" && o["subType"] == "roundRect" && o["text"] == "Note Box"));

        close_session_handle(sid);
        close_session_handle(sid2);
    }
}
