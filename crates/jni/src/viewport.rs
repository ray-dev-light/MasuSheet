// Copyright 2026 MasuSheet contributors. Dual-licensed under MIT OR Apache-2.0.
//! # Viewport Snapshot Builder & Cross-Sheet Cell Batch Reader
//!
//! **[EN]** Builds bounded [`ViewportDto`] snapshots for the active worksheet and implements
//! `jni.readSheetCells` (`read_sheet_cells_json`) for reading cells from any sheet without
//! mutating the active sheet tab (used by cross-sheet PivotTable refresh and full-sheet export).
//!
//! **[JA]** アクティブシートの [`ViewportDto`] スナップショット構築および、シート切替を行わずに任意シートの
//! セル群を一括取得する `jni.readSheetCells`（ピボットテーブル更新・全シート出力用）を実装します。

use std::collections::BTreeSet;

use gridcraft_core::{CellRef, RangeRef, Value};
use gridcraft_engine::Session;
use gridcraft_model::{Sheet, SparklineKind, ValidationKind, Workbook};
use serde_json::{Value as Json, json};

use crate::dto::{
    CellBuildContext, CellDto, CommentDto, DefinedNameDto, HyperlinkDto, SparklineDto,
    ValidationDto, ViewportDto, build_cell_dto, collect_sheet_tabs, empty_viewport_dto,
    resolve_argb,
};

/// [EN] Hard ceiling on serialized `CellDto` entries in a single viewport JSON payload to prevent
///      excessive JNI string allocations on sheets with millions of formatted cells.
/// [JA] 単一ビューポート JSON に含める最大セル数上限です（超高密度シートでの JNI 文字列 OOM を防止）。
const MAX_VIEWPORT_CELLS: usize = 120_000;

/// [EN] Hard ceiling on serialized cells returned by `jni.readSheetCells`.
/// [JA] `jni.readSheetCells` で一括返却するセル数の安全上限です。
const MAX_READ_SHEET_CELLS: usize = 200_000;

/// [EN] Request parameters for [`build_viewport_dto`].
/// [JA] [`build_viewport_dto`] への要求パラメータ構造体です。
pub(crate) struct ViewportRequest {
    pub(crate) session_id: i64,
    pub(crate) max_row: u32,
    pub(crate) max_col: u32,
    pub(crate) error: Option<String>,
    pub(crate) result: Option<Json>,
}

/// [EN] Builds a complete [`ViewportDto`] for the session's active worksheet, dynamically sizing
///      row/column metrics to cover the used range (`<= 5,000 × 200` automatically, expanding up
///      to `20,000 × 512` as the user scrolls).
/// [JA] セッション内のアクティブシートから [`ViewportDto`] を構築します。使用範囲やスクロール位置に応じて
///      行列サイズを動的に拡張します（自動範囲最大 `5,000 × 200`、スクロール要求時最大 `20,000 × 512`）。
pub(crate) fn build_viewport_dto(s: &Session, req: ViewportRequest) -> ViewportDto {
    let Some(doc) = s.active() else {
        return empty_viewport_dto(req.session_id, req.error, req.result);
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
        let mut dto = empty_viewport_dto(req.session_id, Some("No active sheet".into()), req.result);
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

    let max_row = used_end_row
        .saturating_add(30)
        .clamp(60, 5_000)
        .max(req.max_row.clamp(60, 20_000))
        .max(doc.selection.active.row.saturating_add(30).min(20_000));
    let max_col = used_end_col
        .saturating_add(10)
        .clamp(26, 200)
        .max(req.max_col.clamp(26, 512))
        .max(doc.selection.active.col.saturating_add(10).min(512));

    let (col_widths, hidden_cols) = collect_col_metrics(sh, max_col);
    let (row_heights, hidden_rows) = collect_row_metrics(sh, max_row);

    let viewport_range = RangeRef::new(CellRef::new(0, 0), CellRef::new(max_row, max_col));
    let (merges, spill_ranges, cells) =
        collect_viewport_cells(wb, sh, sheet_index, viewport_range);

    let (freeze_rows, freeze_cols) = sh.freeze.unwrap_or((0, 0));
    let active_cell = sh
        .merge_at(doc.selection.active)
        .map_or(doc.selection.active, |m| m.start);
    let sel_cur = doc.selection.current();
    let active_address = active_cell.to_string();
    let active_edit_text = gridcraft_engine::display::edit_text(wb, sh, active_cell);
    let active_display_text = gridcraft_engine::display::cell_text(wb, sh, active_cell);
    let active_spill_formula = sh
        .spill_anchor(active_cell)
        .map(|anchor| gridcraft_engine::display::edit_text(wb, sh, anchor))
        .unwrap_or_default();

    ViewportDto {
        ok: req.error.is_none(),
        error: req.error,
        result: req.result,
        session_id: req.session_id,
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
        sparklines: collect_sparklines(wb, sh, sheet_index, viewport_range),
        validations: collect_validations(wb, sh, sheet_index),
        comments: collect_comments(sh),
        hyperlinks: collect_hyperlinks(sh),
        defined_names: collect_defined_names(wb),
        cells,
    }
}

/// [EN] Collects column widths (`0.0` for hidden columns) and hidden column indices up to `max_col`.
/// [JA] `0..=max_col` の列幅配列（非表示列は `0.0`）および非表示列インデックス一覧を収集します。
fn collect_col_metrics(sh: &Sheet, max_col: u32) -> (Vec<f32>, Vec<u32>) {
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
    (col_widths, hidden_cols)
}

/// [EN] Collects row heights (`0.0` for hidden rows) and hidden row indices up to `max_row`.
/// [JA] `0..=max_row` の行高配列（非表示行は `0.0`）および非表示行インデックス一覧を収集します。
fn collect_row_metrics(sh: &Sheet, max_row: u32) -> (Vec<f32>, Vec<u32>) {
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
    (row_heights, hidden_rows)
}

/// [EN] Collects intersecting merge rectangles, spill rectangles, and up to [`MAX_VIEWPORT_CELLS`]
///      formatted [`CellDto`] entries within `viewport_range`.
/// [JA] 表示範囲と交差する結合セル・スピル範囲・最大 [`MAX_VIEWPORT_CELLS`] 件のセルデータを収集します。
fn collect_viewport_cells(
    wb: &Workbook,
    sh: &Sheet,
    sheet_index: usize,
    viewport_range: RangeRef,
) -> (Vec<[u32; 4]>, Vec<[u32; 4]>, Vec<CellDto>) {
    let mut merges = Vec::new();
    let mut cell_coords: BTreeSet<CellRef> =
        sh.cells.iter_range(viewport_range).map(|(c, _)| c).collect();
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

    let build_ctx = CellBuildContext {
        wb,
        sh,
        sheet_index,
        include_raw_number: false,
    };
    let mut cf_cache = (!sh.cond_formats.is_empty()).then(gridcraft_engine::cf::CfCache::new);
    let mut cells = Vec::with_capacity(cell_coords.len().min(MAX_VIEWPORT_CELLS));
    for cref in cell_coords {
        if cells.len() >= MAX_VIEWPORT_CELLS {
            break;
        }
        if let Some(cell_dto) = build_cell_dto(&build_ctx, cref, &mut cf_cache) {
            cells.push(cell_dto);
        }
    }
    (merges, spill_ranges, cells)
}

/// [EN] Collects comments/notes on `sh`. / [JA] シート上のコメント一覧を収集します。
fn collect_comments(sh: &Sheet) -> Vec<CommentDto> {
    sh.comments
        .iter()
        .map(|(c, cmt)| CommentDto {
            cell: c.a1(),
            author: cmt.author.clone(),
            text: cmt.text.clone(),
            resolved: cmt.resolved,
        })
        .collect()
}

/// [EN] Collects hyperlinks on `sh`. / [JA] シート上のハイパーリンク一覧を収集します。
fn collect_hyperlinks(sh: &Sheet) -> Vec<HyperlinkDto> {
    sh.hyperlinks
        .iter()
        .map(|(c, h)| HyperlinkDto {
            cell: c.a1(),
            target: h.target.clone(),
            tooltip: h.tooltip.clone().unwrap_or_default(),
        })
        .collect()
}

/// [EN] Collects user-visible workbook defined names (filtering internal `_gc_` / `_sc_` stashes).
/// [JA] 内部メタデータ（`_gc_` / `_sc_` 始まり）を除いたユーザー定義の名前一覧を収集します。
fn collect_defined_names(wb: &Workbook) -> Vec<DefinedNameDto> {
    wb.names
        .iter()
        .filter(|n| !n.hidden && !n.name.starts_with("_gc_") && !n.name.starts_with("_sc_"))
        .map(|n| DefinedNameDto {
            name: n.name.clone(),
            refers_to: n.formula.clone(),
        })
        .collect()
}

/// [EN] Evaluates in-cell sparklines within `viewport_range`.
/// [JA] 表示範囲内のスパークラインを評価し描画用データ列へ変換します。
fn collect_sparklines(
    wb: &Workbook,
    sh: &Sheet,
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

/// [EN] Collects worksheet data-validation rules and dropdown candidate items.
/// [JA] シート上の入力規則およびドロップダウン候補リストを収集します。
fn collect_validations(wb: &Workbook, sh: &Sheet, sheet_index: usize) -> Vec<ValidationDto> {
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

/// [EN] Reads sparse cells (with full `CellDto` styling, edit text, borders, and numeric values)
///      from any worksheet in the workbook without switching the active sheet tab.
///      Accepts both Kotlin (`sheet`, `maxRows`, `maxCols`) and engine (`sheetIndex`, `sheetName`,
///      `startRow`, `startCol`, `endRow`, `endCol`) parameter conventions, capped at [`MAX_READ_SHEET_CELLS`].
/// [JA] アクティブシートを切り替えずに指定シートのセル群（書式・数式・罫線・数値を含む [`CellDto`]）を一括取得します。
///      Kotlin 側（`sheet`, `maxRows`, `maxCols`）およびエンジン側（`sheetIndex`, `sheetName`, `endRow`, `endCol`）
///      の両パラメータ形式に対応し、最大 [`MAX_READ_SHEET_CELLS`] 件までに安全制限します。
pub(crate) fn read_sheet_cells_json(s: &Session, params: &Json) -> Result<Json, String> {
    let doc = s.active().ok_or_else(|| "No active workbook".to_string())?;
    let wb = &doc.wb;
    let si = params
        .get("sheetIndex")
        .or_else(|| params.get("sheet"))
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
    let r1 = params
        .get("startRow")
        .and_then(Json::as_u64)
        .map_or(0, |v| (v as u32).min(gridcraft_core::MAX_ROWS - 1));
    let c1 = params
        .get("startCol")
        .and_then(Json::as_u64)
        .map_or(0, |v| (v as u32).min(gridcraft_core::MAX_COLS - 1));
    let r2 = params
        .get("endRow")
        .and_then(Json::as_u64)
        .map(|v| v as u32)
        .or_else(|| {
            params
                .get("maxRows")
                .and_then(Json::as_u64)
                .map(|v| (v as u32).saturating_sub(1))
        })
        .unwrap_or(default_end_r)
        .min(gridcraft_core::MAX_ROWS - 1);
    let c2 = params
        .get("endCol")
        .and_then(Json::as_u64)
        .map(|v| v as u32)
        .or_else(|| {
            params
                .get("maxCols")
                .and_then(Json::as_u64)
                .map(|v| (v as u32).saturating_sub(1))
        })
        .unwrap_or(default_end_c)
        .min(gridcraft_core::MAX_COLS - 1);

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

    let build_ctx = CellBuildContext {
        wb,
        sh,
        sheet_index: si,
        include_raw_number: true,
    };
    let mut cf_cache = (!sh.cond_formats.is_empty()).then(gridcraft_engine::cf::CfCache::new);
    let mut entries: Vec<CellDto> = Vec::with_capacity(coords.len().min(MAX_READ_SHEET_CELLS));
    for cref in coords {
        if entries.len() >= MAX_READ_SHEET_CELLS {
            break;
        }
        if let Some(cell_dto) = build_cell_dto(&build_ctx, cref, &mut cf_cache) {
            entries.push(cell_dto);
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
