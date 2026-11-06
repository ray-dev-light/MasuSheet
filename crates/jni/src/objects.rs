// Copyright 2026 MasuSheet contributors. Dual-licensed under MIT OR Apache-2.0.
//! # Floating Sheet Object Synchronization (`CHART`, `IMAGE`, `SHAPE`, `TEXT_BOX`)
//!
//! **[EN]** Bridges MasuSheet's interactive floating objects to and from the Rust `Workbook`
//! (`sh.charts`, `sh.images`, `sh.shapes`) so `.xlsx` save and PDF export emit genuine ECMA-376
//! OOXML DrawingML/ChartML, and external `.xlsx` files opened in MasuSheet display their charts,
//! images, and shapes cleanly. Enforces strict count, dimension, and byte-budget limits.
//!
//! **[JA]** MasuSheet のフローティングオブジェクト（グラフ・画像・図形・テキストボックス）と Rust 側
//! `Workbook` の標準 OOXML 要素を双方向変換します。オブジェクト数・座標範囲・デコード画像サイズの厳格な上限を備えます。

use std::sync::Arc;

use gridcraft_core::{CellRef, Value};
use gridcraft_engine::Session;
use gridcraft_model::{
    Anchor, AnchorMode, Chart, ChartKind, Color, Image, LegendPos, Series, Shape, ShapeFormat,
    ShapeKind, Sheet, Workbook,
};
use serde_json::{Value as Json, json};

use crate::dto::resolve_argb;

/// [EN] Maximum floating objects accepted in a single `jni.syncWorkbookObjects` payload.
/// [JA] 1回の同期処理で受け付ける最大フローティングオブジェクト数です。
const MAX_SYNC_OBJECTS: usize = 500;

/// [EN] Maximum decoded byte size (`16 MiB`) for a single embedded worksheet image.
/// [JA] 単一の埋め込み画像の最大デコードサイズ上限（`16 MiB`）です。
const MAX_SINGLE_IMAGE_BYTES: usize = 16 * 1024 * 1024;

/// [EN] Maximum cumulative decoded image bytes (`64 MiB`) across a workbook sync operation.
/// [JA] ブック全体での埋め込み画像合計デコードサイズ上限（`64 MiB`）です。
const MAX_TOTAL_IMAGE_BYTES: usize = 64 * 1024 * 1024;

/// [EN] Maximum coordinate (`pt`) allowed when anchoring floating objects onto a sheet.
/// [JA] オブジェクト配置座標（`pt`）の安全上限です。
const MAX_COORD_PT: f64 = 1_000_000.0;

/// [EN] Maximum width/height (`pt`) allowed for a floating object (`f32`-safe finite bound).
/// [JA] オブジェクトの幅・高さ（`pt`）の安全上限です。
const MAX_DIMENSION_PT: f64 = 100_000.0;

/// [EN] Maps a MasuSheet chart `subType` string to `(ChartKind, smooth_lines)`.
/// [JA] MasuSheet のグラフ `subType` 識別子を `(ChartKind, 平滑線フラグ)` へ対応付けます。
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

/// [EN] Maps a Rust [`ChartKind`] + smooth flag back to MasuSheet's canonical `subType` identifier.
/// [JA] Rust 側の [`ChartKind`] と平滑線フラグを MasuSheet の `subType` 識別子へ逆変換します。
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

/// [EN] Formats string labels as an OOXML formula array literal (`{"Jan","Feb"}`).
/// [JA] 文字列配列を OOXML 互換の配列リテラル表記（`{"Jan","Feb"}`）へ整形します。
fn format_str_array_literal(items: &[String]) -> String {
    let quoted: Vec<String> = items
        .iter()
        .map(|s| format!("\"{}\"", s.replace('"', "\"\"")))
        .collect();
    format!("{{{}}}", quoted.join(","))
}

/// [EN] Formats numeric values as an OOXML formula array literal (`{120,240}`), replacing non-finite
///      floats with `0`.
/// [JA] 数値配列を OOXML 互換の配列リテラル表記（`{120,240}`）へ整形します（非有限値は `0` に置換）。
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

/// [EN] Clamps a JSON floating-point coordinate/size into `[min_v, max_v]`, substituting `default_v`
///      when missing or non-finite.
/// [JA] JSON の数値プロパティを取得し、欠損・非有限時は `default_v` にフォールバックしたうえで `[min_v, max_v]` に制限します。
fn finite_clamped_f64(obj: &Json, key: &str, default_v: f64, min_v: f64, max_v: f64) -> f64 {
    obj.get(key)
        .and_then(Json::as_f64)
        .filter(|v| v.is_finite())
        .unwrap_or(default_v)
        .clamp(min_v, max_v)
}

/// [EN] Computes a clamped [`Anchor`] on `sh` from JSON point coordinates (`xPt`, `yPt`, `widthPt`, `heightPt`).
/// [JA] JSON 上の座標・寸法（`pt` 単位）から安全なシート上の [`Anchor`] を算出します。
fn build_object_anchor(sh: &Sheet, obj: &Json) -> Anchor {
    let x_pt = finite_clamped_f64(obj, "xPt", 80.0, 0.0, MAX_COORD_PT);
    let y_pt = finite_clamped_f64(obj, "yPt", 60.0, 0.0, MAX_COORD_PT);
    let w_pt = finite_clamped_f64(obj, "widthPt", 180.0, 16.0, MAX_DIMENSION_PT) as f32;
    let h_pt = finite_clamped_f64(obj, "heightPt", 110.0, 16.0, MAX_DIMENSION_PT) as f32;
    let col = sh.col_at(x_pt);
    let row = sh.row_at(y_pt);
    Anchor {
        cell: CellRef::new(row, col),
        dx: (x_pt - sh.col_left(col)).clamp(0.0, MAX_DIMENSION_PT) as f32,
        dy: (y_pt - sh.row_top(row)).clamp(0.0, MAX_DIMENSION_PT) as f32,
        width: w_pt,
        height: h_pt,
        mode: AnchorMode::MoveAndSize,
    }
}

/// [EN] Synchronizes MasuSheet floating objects (`CHART`, `IMAGE`, `SHAPE`, `TEXT_BOX`) into the
///      active Rust `Workbook` so `.xlsx` save and PDF export emit genuine ECMA-376 DrawingML/ChartML.
///      Validates that `"objects"` is a JSON array BEFORE clearing existing sheet objects so a
///      malformed request never wipes existing charts/images/shapes.
/// [JA] MasuSheet 側のフローティングオブジェクトを Rust `Workbook` へ同期します。
///      不正な引数で既存オブジェクトが消去されないよう、事前に `"objects"` 配列を検証してから反映します。
pub(crate) fn sync_workbook_objects_json(s: &mut Session, params: &Json) -> Result<Json, String> {
    let arr = params
        .get("objects")
        .and_then(Json::as_array)
        .ok_or_else(|| "Missing or invalid 'objects' array in jni.syncWorkbookObjects".to_string())?;
    if arr.len() > MAX_SYNC_OBJECTS {
        return Err(format!(
            "Too many floating objects in sync payload ({} > {MAX_SYNC_OBJECTS})",
            arr.len()
        ));
    }

    let doc = s.doc_mut().map_err(|e| e.to_string())?;
    let wb = Arc::make_mut(&mut doc.wb);
    for sh_arc in &mut wb.sheets {
        let sh = Arc::make_mut(sh_arc);
        sh.charts.clear();
        sh.images.clear();
        sh.shapes.clear();
    }

    let mut next_id: u32 = 1;
    let mut synced_count: usize = 0;
    let mut total_image_bytes: usize = 0;

    for obj in arr {
        let si = obj
            .get("sheetIndex")
            .and_then(Json::as_u64)
            .map_or(0, |v| usize::try_from(v).unwrap_or(usize::MAX));
        if si >= wb.sheets.len() {
            continue;
        }
        let sh = Arc::make_mut(&mut wb.sheets[si]);
        let anchor = build_object_anchor(sh, obj);
        let kind_str = obj
            .get("kind")
            .and_then(Json::as_str)
            .unwrap_or("SHAPE")
            .trim();

        let added = match kind_str {
            "CHART" => {
                sh.charts.push(build_synced_chart(obj, next_id, anchor));
                true
            }
            "IMAGE" => try_push_synced_image(sh, obj, next_id, anchor, &mut total_image_bytes),
            _ => {
                sh.shapes.push(build_synced_shape(obj, kind_str, next_id, anchor));
                true
            }
        };
        if added {
            next_id = next_id.saturating_add(1);
            synced_count += 1;
        }
    }

    Ok(json!({ "synced": synced_count }))
}

/// [EN] Constructs a Rust [`Chart`] from a single synced JSON object descriptor.
/// [JA] JSON オブジェクト記述子から Rust 側の [`Chart`] モデルを構築します。
fn build_synced_chart(obj: &Json, id: u32, anchor: Anchor) -> Chart {
    let sub_type = obj
        .get("subType")
        .and_then(Json::as_str)
        .unwrap_or("column")
        .trim();
    let title_str = obj
        .get("title")
        .and_then(Json::as_str)
        .unwrap_or("")
        .trim()
        .to_string();
    let fill_raw = obj
        .get("fillColor")
        .and_then(Json::as_i64)
        .map_or(0, |v| v as u32);
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

    let mut series_vec = build_chart_series_list(
        obj,
        sub_type,
        chart_kind,
        smooth_default,
        &top_labels,
        &top_values,
    );
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
            color: (fill_raw != 0).then_some(Color::Rgb(fill_raw & 0x00FF_FFFF)),
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

    Chart {
        id,
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
    }
}

/// [EN] Parses the `"chartSeries"` JSON array into OOXML [`Series`] models.
/// [JA] `"chartSeries"` JSON 配列を解析し、OOXML 互換の [`Series`] リストを生成します。
fn build_chart_series_list(
    obj: &Json,
    sub_type: &str,
    chart_kind: ChartKind,
    smooth_default: bool,
    top_labels: &[String],
    top_values: &[f64],
) -> Vec<Series> {
    let Some(s_arr) = obj.get("chartSeries").and_then(Json::as_array) else {
        return Vec::new();
    };
    let mut series_vec = Vec::with_capacity(s_arr.len());
    for (idx, s_item) in s_arr.iter().enumerate() {
        let s_name = s_item.get("name").and_then(Json::as_str).unwrap_or("").trim();
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
            .map_or(0, |v| v as u32);

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
            Some(format_str_array_literal(top_labels))
        } else {
            None
        };
        let val_str = if !s_y_range.is_empty() {
            s_y_range.to_string()
        } else if !s_vals.is_empty() {
            format_num_array_literal(&s_vals)
        } else if !top_values.is_empty() {
            format_num_array_literal(top_values)
        } else {
            "{0}".to_string()
        };

        series_vec.push(Series {
            name: name_opt,
            categories: cat_opt,
            values: val_str,
            bubble_sizes: None,
            color: (s_col_raw != 0).then_some(Color::Rgb(s_col_raw & 0x00FF_FFFF)),
            secondary: sub_type.eq_ignore_ascii_case("combo_secondary") && idx > 0,
            kind: (matches!(chart_kind, ChartKind::Combo) && idx > 0)
                .then_some(ChartKind::LineMarkers),
            smooth: smooth_default,
            quartiles: Default::default(),
            trendlines: Vec::new(),
        });
    }
    series_vec
}

/// [EN] Decodes and validates a Base64 image object, enforcing [`MAX_SINGLE_IMAGE_BYTES`] and
///      [`MAX_TOTAL_IMAGE_BYTES`] before appending to `sh.images`.
/// [JA] Base64 画像サイズの上限検査とマジックバイトによる MIME 判定を行い、安全な場合のみ `sh.images` へ追加します。
fn try_push_synced_image(
    sh: &mut Sheet,
    obj: &Json,
    id: u32,
    anchor: Anchor,
    total_image_bytes: &mut usize,
) -> bool {
    let Some(b64_raw) = obj.get("imageBase64").and_then(Json::as_str) else {
        return false;
    };
    let b64_trimmed = b64_raw.trim();
    // Pre-check Base64 length (~4/3 of decoded bytes) BEFORE allocating decoded Vec<u8>
    let max_b64_chars = (MAX_SINGLE_IMAGE_BYTES / 3) * 4 + 64;
    if b64_trimmed.is_empty() || b64_trimmed.len() > max_b64_chars {
        return false;
    }
    let Some(bytes) = gridcraft_model::b64::decode(b64_trimmed) else {
        return false;
    };
    if bytes.is_empty()
        || bytes.len() > MAX_SINGLE_IMAGE_BYTES
        || total_image_bytes.saturating_add(bytes.len()) > MAX_TOTAL_IMAGE_BYTES
    {
        return false;
    }
    *total_image_bytes += bytes.len();

    let mime = detect_image_mime(&bytes);
    let title_str = obj.get("title").and_then(Json::as_str).unwrap_or("").trim();
    let text_str = obj.get("text").and_then(Json::as_str).unwrap_or("").trim();
    let alt_txt = if !title_str.is_empty() {
        title_str.to_string()
    } else {
        text_str.to_string()
    };

    sh.images.push(Image {
        id,
        anchor,
        data: Arc::new(bytes),
        mime: mime.to_string(),
        alt: alt_txt,
    });
    true
}

/// [EN] Detects standard image MIME types from header magic bytes (JPEG, PNG, GIF, WebP).
/// [JA] 先頭マジックバイトから画像の MIME タイプ（JPEG / PNG / GIF / WebP）を識別します。
fn detect_image_mime(bytes: &[u8]) -> &'static str {
    if bytes.starts_with(&[0xFF, 0xD8, 0xFF]) {
        "image/jpeg"
    } else if bytes.starts_with(b"GIF87a") || bytes.starts_with(b"GIF89a") {
        "image/gif"
    } else if bytes.len() >= 12 && &bytes[0..4] == b"RIFF" && &bytes[8..12] == b"WEBP" {
        "image/webp"
    } else {
        "image/png"
    }
}

/// [EN] Constructs a Rust [`Shape`] (`TextBox`, `RoundedRectangle`, `Ellipse`, `Triangle`, `Arrow`,
///      `Line`, or `Rectangle`) from a synced JSON object descriptor.
/// [JA] JSON オブジェクト記述子から Rust 側の [`Shape`] 図形モデルを構築します。
fn build_synced_shape(obj: &Json, kind_str: &str, id: u32, anchor: Anchor) -> Shape {
    let sub_type = obj
        .get("subType")
        .and_then(Json::as_str)
        .unwrap_or("rectangle")
        .trim();
    let title_str = obj.get("title").and_then(Json::as_str).unwrap_or("").trim();
    let text_str = obj.get("text").and_then(Json::as_str).unwrap_or("").trim();
    let fill_raw = obj
        .get("fillColor")
        .and_then(Json::as_i64)
        .map_or(0, |v| v as u32);
    let stroke_raw = obj
        .get("strokeColor")
        .and_then(Json::as_i64)
        .map_or(0, |v| v as u32);
    let stroke_w = finite_clamped_f64(obj, "strokeWidthPt", 2.0, 0.0, 64.0);

    let shape_kind = if kind_str == "TEXT_BOX" || sub_type.eq_ignore_ascii_case("textbox") {
        ShapeKind::TextBox
    } else {
        match sub_type.to_ascii_lowercase().as_str() {
            "roundrect" | "round_rect" | "rounded_rect" | "roundedrectangle" => {
                ShapeKind::RoundedRectangle
            }
            "oval" | "circle" | "ellipse" => ShapeKind::Ellipse,
            "triangle" | "diamond" => ShapeKind::Triangle,
            "arrow" | "arrow_left" | "arrow_up" | "arrow_down" | "right_arrow" | "left_arrow" => {
                ShapeKind::Arrow
            }
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
        text_str.to_string()
    } else {
        title_str.to_string()
    };

    Shape {
        id,
        kind: shape_kind,
        anchor,
        fill,
        line,
        text: body,
        format: ShapeFormat {
            no_line: stroke_raw == 0 || stroke_w <= 0.0,
            ..Default::default()
        },
    }
}

/// [EN] Evaluates a chart series range or array-literal expression into scalar cell [`Value`]s.
/// [JA] グラフ系列のセル範囲参照または配列リテラル式を評価し、[`Value`] リストとして返します。
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
///      so `.xlsx` files authored in external spreadsheet suites render their objects on MasuSheet's canvas.
/// [JA] 開いた `.xlsx` ワークブック内の標準 OOXML グラフ・画像・図形を読み取り、MasuSheet キャンバス用の JSON 配列へ変換します。
pub(crate) fn read_workbook_objects_json(s: &Session) -> Result<Json, String> {
    let doc = s.active().ok_or_else(|| "No active workbook".to_string())?;
    let wb = &doc.wb;
    let mut out: Vec<Json> = Vec::new();
    let mut total_img_bytes: usize = 0;

    for (si, sh) in wb.sheets.iter().enumerate() {
        for ch in &sh.charts {
            if out.len() >= MAX_SYNC_OBJECTS {
                break;
            }
            out.push(serialize_ooxml_chart(wb, sh, si, ch));
        }
        for img in &sh.images {
            if out.len() >= MAX_SYNC_OBJECTS {
                break;
            }
            if img.data.is_empty()
                || img.data.len() > MAX_SINGLE_IMAGE_BYTES
                || total_img_bytes.saturating_add(img.data.len()) > MAX_TOTAL_IMAGE_BYTES
            {
                continue;
            }
            total_img_bytes += img.data.len();
            out.push(serialize_ooxml_image(sh, si, img));
        }
        for sp in &sh.shapes {
            if out.len() >= MAX_SYNC_OBJECTS {
                break;
            }
            out.push(serialize_ooxml_shape(wb, sh, si, sp));
        }
    }

    Ok(json!({ "objects": out }))
}

/// [EN] Serializes an OOXML [`Chart`] into MasuSheet's JSON object format.
/// [JA] OOXML [`Chart`] モデルを MasuSheet 側のグラフ JSON 形式へシリアライズします。
fn serialize_ooxml_chart(wb: &Workbook, sh: &Sheet, si: usize, ch: &Chart) -> Json {
    const PALETTE: [u32; 6] = [
        0xFF25_63EB,
        0xFF16_A34A,
        0xFFEA_580C,
        0xFF93_33EA,
        0xFFDC_2626,
        0xFF08_91B2,
    ];
    let x_pt = sh.col_left(ch.anchor.cell.col) + f64::from(ch.anchor.dx);
    let y_pt = sh.row_top(ch.anchor.cell.row) + f64::from(ch.anchor.dy);
    let any_smooth = ch.series.iter().any(|se| se.smooth);
    let sub_type = map_chart_kind_to_sub_type(ch.kind, any_smooth);

    let mut series_json: Vec<Json> = Vec::with_capacity(ch.series.len());
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
        let (resolved_name, name_cell_ref) = resolve_series_name(wb, si, s_idx, se.name.as_deref());
        let s_color = se
            .color
            .as_ref()
            .map(|c| resolve_argb(c, wb))
            .filter(|&c| c != 0)
            .unwrap_or(PALETTE[s_idx % PALETTE.len()]);
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
            primary_labels.clone_from(&x_labels);
            primary_values.clone_from(&vals);
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
        primary_labels = (1..=primary_values.len()).map(|i| i.to_string()).collect();
    }

    json!({
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
    })
}

/// [EN] Resolves a chart series name expression (either a cell reference like `Sheet1!$B$1` or a literal title).
/// [JA] グラフ系列名（セル参照またはリテラル文字列）を表示名と参照文字列の組へ解決します。
fn resolve_series_name(
    wb: &Workbook,
    si: usize,
    s_idx: usize,
    raw_name: Option<&str>,
) -> (String, String) {
    let Some(trimmed) = raw_name.map(str::trim).filter(|s| !s.is_empty()) else {
        return (format!("Series {}", s_idx + 1), String::new());
    };
    let looks_like_ref =
        trimmed.contains('!') || trimmed.starts_with('$') || CellRef::parse(trimmed).is_some();
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

/// [EN] Serializes an OOXML [`Image`] into MasuSheet's JSON object format.
/// [JA] OOXML [`Image`] モデルを MasuSheet 側の画像 JSON 形式へシリアライズします。
fn serialize_ooxml_image(sh: &Sheet, si: usize, img: &Image) -> Json {
    let x_pt = sh.col_left(img.anchor.cell.col) + f64::from(img.anchor.dx);
    let y_pt = sh.row_top(img.anchor.cell.row) + f64::from(img.anchor.dy);
    json!({
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
    })
}

/// [EN] Serializes an OOXML [`Shape`] into MasuSheet's JSON object format.
/// [JA] OOXML [`Shape`] モデルを MasuSheet 側の図形／テキストボックス JSON 形式へシリアライズします。
fn serialize_ooxml_shape(wb: &Workbook, sh: &Sheet, si: usize, sp: &Shape) -> Json {
    let x_pt = sh.col_left(sp.anchor.cell.col) + f64::from(sp.anchor.dx);
    let y_pt = sh.row_top(sp.anchor.cell.row) + f64::from(sp.anchor.dy);
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
    json!({
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
    })
}
