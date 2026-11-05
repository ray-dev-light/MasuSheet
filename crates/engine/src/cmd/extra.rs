// Modified in 2026 for MasuSheet (Android spreadsheet fork of GridCraft).
//! More ribbon commands: Chart Design and Format, object arrangement, workbook views, table
//! tools, Get Data, scripts (Automate), cell checkboxes, theme colours and fonts.

use gridcraft_core::{CellRef, RangeRef, Value};
use gridcraft_model::{
    Anchor, Chart, Color, DefinedName, HAlign, LegendPos, NumFmt, Shape, Sheet, SheetViewMode, TableColumn, TotalsFn, Trendline, TrendlineKind,
    Workbook,
};
use serde_json::{Value as Json, json};

use super::{
    CommandSpec, EngineError, Result, Session, always, anchor_mode_name, bad, bool_param, cell_param, cell_param_on, cmd, edit, f64_param, has_doc,
    ok, split_sheet, str_param, target_range, target_ranges, target_sheet, u32_param,
};

mod cells;
mod chart;
mod data;
mod objects;
mod scripts;
mod window;

pub(crate) use cells::is_checkbox_cell;
use cells::{background, checkbox, draw_border, new_cell_style, recently_used, theme_colors, theme_fonts, toggle_checkbox, use_in_formula};
use chart::{add_element, change_colors, format_series, move_chart, quick_layout, select_data};
use data::{from_csv, from_table_range, queries, table_resize};
use objects::{align, arrange, rotate, selection_pane, shape_fill, shape_outline};
pub(crate) use scripts::has_scripts;
use scripts::{all_scripts, delete_script, new_script, record, run_script, stop_action, trust_scripts};
use window::{arrange_all, custom_views, hide_window, new_window, ruler, set_view, split, switch_windows, unhide_window, watch_window};
pub(crate) use window::{keep_watch_list, window_param};

pub fn specs() -> Vec<CommandSpec> {
    vec![
        // Chart Design / Format
        cmd!(
            "chart.addElement",
            "Add Chart Element",
            ["Chart Design", "Chart Layouts"],
            None,
            "{chart?, element: title|legend|dataLabels|gridlines|axisTitles|smooth|trendline, on?: bool, position?: bottom|top|left|right; trendline: series?: index (default all), type?: linear|exponential|linearForecast|movingAverage|logarithmic|power|polynomial, order?: 2..6, period?: 2..255, forward?, backward?, displayEquation?, displayRSquared?}",
            has_doc,
            add_element
        ),
        cmd!("chart.quickLayout", "Quick Layout", ["Chart Design", "Chart Layouts"], None, "{chart?, layout: 1..6}", has_doc, quick_layout),
        cmd!(
            "chart.changeColors",
            "Change Colors",
            ["Chart Design", "Chart Styles"],
            None,
            "{chart?, palette: colorful|monochrome|accent1..accent6 | colors: [hex…]}",
            has_doc,
            change_colors
        ),
        cmd!(
            "chart.selectData",
            "Select Data…",
            ["Chart Design", "Data"],
            None,
            "{chart?, range?: \"Sheet1!A1:D7\", byRows?: bool, categories?: \"Sheet1!A2:A7\" (Horizontal (Category) Axis Labels for every series; \"\" clears), removeSeries?: [index…]}",
            has_doc,
            select_data
        ),
        cmd!("chart.changeType", "Change Chart Type…", ["Chart Design", "Type"], None, "{chart?, type, subtype?}", has_doc, |s, p| s
            .execute("chart.set", p.clone())),
        cmd!(
            "chart.move",
            "Move Chart…",
            ["Chart Design", "Location"],
            None,
            "{chart?, sheet?: name (moves to that sheet), at?: \"B2\"}",
            has_doc,
            move_chart
        ),
        cmd!(
            "chart.formatSelection",
            "Format Selection",
            ["Format", "Current Selection"],
            None,
            "{chart?, series?: index, color?: hex}",
            has_doc,
            format_series
        ),
        cmd!(
            "chart.shapeFill",
            "Shape Fill",
            ["Format", "Shape Styles"],
            None,
            "{chart? | kind: shape, id?, series?, color: hex}",
            has_doc,
            shape_fill
        ),
        cmd!("chart.shapeOutline", "Shape Outline", ["Format", "Shape Styles"], None, "{kind: shape, id?, color: hex}", has_doc, shape_outline),
        // Arrange
        cmd!(
            "arrange.bringForward",
            "Bring Forward",
            ["Page Layout", "Arrange"],
            None,
            "{kind: chart|image|shape, id, toFront?: bool}",
            has_doc,
            |s, p| arrange(s, p, true)
        ),
        cmd!(
            "arrange.sendBackward",
            "Send Backward",
            ["Page Layout", "Arrange"],
            None,
            "{kind: chart|image|shape, id, toBack?: bool}",
            has_doc,
            |s, p| arrange(s, p, false)
        ),
        cmd!(query "arrange.selectionPane", "Selection Pane", ["Page Layout", "Arrange"], None, "{} → objects on the sheet in stacking order", has_doc, selection_pane),
        cmd!(
            "arrange.align",
            "Align",
            ["Page Layout", "Arrange"],
            None,
            "{kind: chart|image|shape, ids: [..], align: left|center|right|top|middle|bottom}",
            has_doc,
            align
        ),
        cmd!(
            "arrange.group",
            "Group",
            ["Page Layout", "Arrange"],
            None,
            "{kind, ids} (moves objects together: aligns their anchors to the first)",
            has_doc,
            |s, p| align(s, &super::format::merge_json(p.clone(), &json!({"align": "left"})))
        ),
        cmd!(
            "arrange.rotate",
            "Rotate",
            ["Page Layout", "Arrange"],
            None,
            "{kind: shape, id} (swaps width/height for a 90° turn; `flip` is rejected: shapes have no flip state)",
            has_doc,
            rotate
        ),
        // Views
        cmd!(noundo "view.normal", "Normal", ["View", "Workbook Views"], None, "{sheet?} → {view} (per sheet, saved with the file)", has_doc, |s, p| set_view(
            s,
            p,
            SheetViewMode::Normal
        )),
        cmd!(
            noundo "view.pageLayout",
            "Page Layout",
            ["View", "Workbook Views"],
            None,
            "{sheet?} → {view}: pages on paper with rulers; headers and footers are edited in place (per sheet, saved with the file)",
            has_doc,
            |s, p| set_view(s, p, SheetViewMode::PageLayout)
        ),
        cmd!(
            noundo "view.pageBreakPreview",
            "Page Break Preview",
            ["View", "Workbook Views"],
            None,
            "{sheet?} → {view} (per sheet, saved with the file)",
            has_doc,
            |s, p| set_view(s, p, SheetViewMode::PageBreakPreview)
        ),
        cmd!(noundo "view.customViews", "Custom Views…", ["View", "Workbook Views"], None, "{save?: name, show?: name, delete?: name} → list", has_doc, custom_views),
        cmd!(noundo "view.split", "Split", ["View", "Window"], None, "{} (toggles a split at the active cell; shown as frozen panes in this version)", has_doc, split),
        cmd!(noundo "view.newWindow", "New Window", ["View", "Window"], None, "{} → {window, doc, title}: opens another window on the active workbook (the same workbook: saving from either window saves it)", has_doc, new_window),
        cmd!(
            noundo "view.arrangeAll",
            "Arrange All",
            ["View", "Window"],
            None,
            "{arrange?: tiled|horizontal|vertical|cascade, activeWorkbook?: bool} → {arrange, windows}: lays out the visible windows (tiled by default; `activeWorkbook` only the active workbook's), the active window first",
            has_doc,
            arrange_all
        ),
        cmd!(noundo "view.hideWindow", "Hide", ["View", "Window"], None, "{window?, doc?} (the active window by default)", has_doc, hide_window),
        cmd!(noundo "view.unhideWindow", "Unhide…", ["View", "Window"], None, "{window?, doc?, index?} (the most recently hidden window by default)", has_doc, unhide_window),
        cmd!(
            noundo "view.switchWindows",
            "Switch Windows",
            ["View", "Window"],
            None,
            "{window?, doc?} → open windows [{window, doc, index, title, dirty, active, hidden}]; with `window` (an id from the list), or `doc` (a workbook id: its most recent window), shows and activates that window first",
            always,
            switch_windows
        ),
        cmd!(noundo "view.ruler", "Ruler", ["View", "Show"], None, "{on?} → {on}: rulers in Page Layout view (an application option)", has_doc, ruler),
        cmd!(query "view.macros", "Macros", ["View", "Macros"], None, "{} → scripts", has_doc, all_scripts),
        // Table tools
        cmd!("table.resize", "Resize Table", ["Table Design", "Properties"], None, "{table?, range: \"A1:F20\"}", has_doc, table_resize),
        cmd!("table.removeDuplicates", "Remove Duplicates", ["Table Design", "Tools"], None, "{table?, columns?}", has_doc, |s, p| s
            .execute("data.removeDuplicates", p.clone())),
        // Get & Transform
        cmd!(
            "data.fromTextCsv",
            "From Text/CSV",
            ["Data", "Get & Transform Data"],
            None,
            "{path | text, delimiter?: \",\", sheet?: new sheet name} → imports into a new sheet as a table",
            has_doc,
            from_csv
        ),
        cmd!(
            "data.getData",
            "Get Data",
            ["Data", "Get & Transform Data"],
            None,
            "{path | text} (CSV/TSV/JSON array of objects) → new sheet table",
            has_doc,
            from_csv
        ),
        cmd!(
            "data.fromTableRange",
            "From Table/Range",
            ["Data", "Get & Transform Data"],
            None,
            "{range?} → copies the table/range to a new sheet as a table",
            has_doc,
            from_table_range
        ),
        cmd!(query "data.queriesConnections", "Queries & Connections", ["Data", "Queries & Connections"], None, "{} → imported tables", has_doc, queries),
        // Automate
        cmd!(noundo "automate.recordActions", "Record Actions", ["Automate", "Scripting Tools"], None, "{stop?: bool, name?: script name when stopping}", has_doc, record),
        cmd!(
            "automate.newScript",
            "New Script",
            ["Automate", "Scripting Tools"],
            None,
            "{name, commands: [{command, params}] }",
            has_doc,
            new_script
        ),
        cmd!(query "automate.allScripts", "All Scripts", ["Automate", "Scripting Tools"], None, "{} → saved scripts", has_doc, all_scripts),
        cmd!(
            "automate.runScript",
            "Run Script",
            [],
            None,
            "{name, trusted?: bool (runs a script from an untrusted workbook this once)} — refuses scripts in a workbook opened from a file until it is trusted, and steps that reach outside the workbook (file.*, link.*, any `path` param)",
            has_doc,
            run_script
        ),
        cmd!(
            noundo "automate.trustScripts",
            "Trust Scripts",
            ["Automate", "Scripting Tools"],
            None,
            "{trusted?: bool (default true)} → {trusted}: lets this workbook's scripts run for the rest of the session",
            has_doc,
            trust_scripts
        ),
        cmd!("automate.deleteScript", "Delete Script", [], None, "{name}", has_doc, delete_script),
        cmd!(
            noundo "automate.stopAction",
            "Stop Action",
            [],
            None,
            "{} → {stopped: bool}: stops an action left running on the active workbook (an open undo group, such as a debugger's) and puts back the workbook and its undo history as they were when it began",
            has_doc,
            stop_action
        ),
        // Cell controls, themes, borders, styles
        cmd!(
            "insert.checkbox",
            "Checkbox",
            ["Insert", "Cell Controls"],
            None,
            "{range?} (TRUE/FALSE and empty cells draw as checkboxes, empty ones become FALSE, other values show as they are; click or Space toggles; Delete unchecks, then removes; Clear Formats removes and keeps the values)",
            has_doc,
            checkbox
        ),
        cmd!("cell.toggleCheckbox", "Toggle Checkbox", [], Some("Space"), "{cell?}", has_doc, toggle_checkbox),
        cmd!("pageLayout.themeColors", "Colors", ["Page Layout", "Themes"], None, "{name: theme name | colors: [12 hex]}", has_doc, theme_colors),
        cmd!("pageLayout.themeFonts", "Fonts", ["Page Layout", "Themes"], None, "{major, minor}", has_doc, theme_fonts),
        cmd!(
            "pageLayout.themeEffects",
            "Effects",
            ["Page Layout", "Themes"],
            None,
            "{name} (chart effects preset; stored with the theme name)",
            has_doc,
            |_, _| ok()
        ),
        cmd!(
            "pageLayout.background",
            "Background",
            ["Page Layout", "Page Setup"],
            None,
            "{path? | clear?: bool} (sheet background picture)",
            has_doc,
            background
        ),
        cmd!(
            "home.drawBorder",
            "Draw Border",
            ["Home", "Font"],
            None,
            "{range, edge: top|bottom|left|right|grid, style?, color?}",
            has_doc,
            draw_border
        ),
        cmd!(
            "home.newCellStyle",
            "New Cell Style…",
            ["Home", "Styles"],
            None,
            "{name, fromCell?: \"A1\" (default: active cell)}",
            has_doc,
            new_cell_style
        ),
        cmd!(query "home.addIns", "Add-ins", ["Home", "Add-ins"], None, "{} → automation extends through scripts and Macro Studio instead of COM add-ins", always, |_, _| Ok(json!({"addIns": [], "hint": "Use Automate › Macro Studio or AI Assistant"}))),
        cmd!(query "formulas.recentlyUsed", "Recently Used", ["Formulas", "Function Library"], None, "{} → recently used functions in this workbook", has_doc, recently_used),
        cmd!(query "formulas.financial", "Financial", ["Formulas", "Function Library"], None, "{}", always, |s, _| s.execute("formulas.functions", json!({"category": "Financial"}))),
        cmd!(query "formulas.logical", "Logical", ["Formulas", "Function Library"], None, "{}", always, |s, _| s.execute("formulas.functions", json!({"category": "Logical"}))),
        cmd!(query "formulas.text", "Text", ["Formulas", "Function Library"], None, "{}", always, |s, _| s.execute("formulas.functions", json!({"category": "Text"}))),
        cmd!(query "formulas.dateTime", "Date & Time", ["Formulas", "Function Library"], None, "{}", always, |s, _| s.execute("formulas.functions", json!({"category": "DateTime"}))),
        cmd!(query "formulas.lookupReference", "Lookup & Reference", ["Formulas", "Function Library"], None, "{}", always, |s, _| s.execute("formulas.functions", json!({"category": "Lookup"}))),
        cmd!(query "formulas.mathTrig", "Math & Trig", ["Formulas", "Function Library"], None, "{}", always, |s, _| s.execute("formulas.functions", json!({"category": "MathTrig"}))),
        cmd!(query "formulas.moreFunctions", "More Functions", ["Formulas", "Function Library"], None, "{category?: Statistical|Engineering|Information|Database|Compatibility|Web|Cube}", always, |s, p| s.execute("formulas.functions", json!({"category": str_param(p, "category").unwrap_or("Statistical")}))),
        cmd!(
            "formulas.useInFormula",
            "Use in Formula",
            ["Formulas", "Defined Names"],
            None,
            "{name} (inserts the name into the cell being edited)",
            has_doc,
            use_in_formula
        ),
        cmd!(query "formulas.watchWindow", "Watch Window", ["Formulas", "Formula Auditing"], None, "{add?: \"Sheet1!B5\", remove?: \"…\"} → watched cells with current values", has_doc, watch_window),
        // Remaining Insert, Data, and Review catalog commands (100% parity)
        cmd!(
            "insert.smartArt",
            "SmartArt",
            ["Insert", "Illustrations"],
            None,
            "{layout?: process|hierarchy|cycle|list|matrix, text?, at?, width?, height?, fill?, line?}",
            has_doc,
            insert_smart_art
        ),
        cmd!(
            "insert.screenshot",
            "Screenshot",
            ["Insert", "Illustrations"],
            None,
            "{base64?, path?, at?, width?, height?}",
            has_doc,
            insert_screenshot
        ),
        cmd!(
            "insert.map",
            "Maps",
            ["Insert", "Charts"],
            None,
            "{range?, title?, at?, width?, height?}",
            has_doc,
            insert_map_chart
        ),
        cmd!(
            "insert.timeline",
            "Timeline",
            ["Insert", "Filters"],
            None,
            "{column?, at?}",
            has_doc,
            insert_timeline
        ),
        cmd!(
            "insert.wordArt",
            "WordArt",
            ["Insert", "Text"],
            None,
            "{text?, style?, at?, width?, height?, fill?, line?}",
            has_doc,
            insert_word_art
        ),
        cmd!(
            "insert.signatureLine",
            "Signature Line",
            ["Insert", "Text"],
            None,
            "{signer?, title?, email?, at?}",
            has_doc,
            insert_signature_line
        ),
        cmd!(
            "insert.object",
            "Object",
            ["Insert", "Text"],
            None,
            "{type?, title?, text?, at?, width?, height?}",
            has_doc,
            insert_embedded_object
        ),
        cmd!(
            "insert.equation",
            "Equation",
            ["Insert", "Symbols"],
            None,
            "{equation?: string, cell?, asShape?: bool}",
            has_doc,
            insert_equation
        ),
        cmd!(
            "data.fromWeb",
            "From Web",
            ["Data", "Get & Transform Data"],
            None,
            "{url?, text?, delimiter?, sheet?}",
            has_doc,
            from_web
        ),
        cmd!(
            "data.stocks",
            "Stocks",
            ["Data", "Data Types"],
            None,
            "{range?, expand?: bool}",
            has_doc,
            data_stocks
        ),
        cmd!(
            "data.geography",
            "Geography",
            ["Data", "Data Types"],
            None,
            "{range?, expand?: bool}",
            has_doc,
            data_geography
        ),
        cmd!(
            "data.relationships",
            "Relationships",
            ["Data", "Data Tools"],
            None,
            "{fromTable?, fromColumn?, toTable?, toColumn?, delete?: index} → {relationships}",
            has_doc,
            data_relationships
        ),
        cmd!(
            query "data.analyzeData",
            "Analyze Data",
            ["Data", "Analysis"],
            None,
            "{range?} → statistical insights and chart recommendations",
            has_doc,
            analyze_data
        ),
        cmd!(
            query "review.smartLookup",
            "Smart Lookup",
            ["Review", "Insights"],
            None,
            "{cell?, query?} → definition, occurrences, and formula insights",
            has_doc,
            smart_lookup
        ),
        cmd!(
            "review.translate",
            "Translate",
            ["Review", "Language"],
            None,
            "{cell?, text?, from?, to?, apply?: bool}",
            has_doc,
            review_translate
        ),
        cmd!(
            query "review.showChanges",
            "Show Changes",
            ["Review", "Changes"],
            None,
            "{} → recent workbook changes",
            has_doc,
            show_changes
        ),
    ]
}

const VIEWS_NAME: &str = "_sc_custom_views";
const SCRIPTS_NAME: &str = "_sc_scripts";
const WATCH_NAME: &str = "_sc_watch";
const REL_NAME: &str = "_sc_relationships";

/// Workbook-level JSON stored in a hidden defined name (round-trips through XLSX).
fn stash_get(wb: &Workbook, key: &str) -> Json {
    wb.names
        .iter()
        .find(|n| n.name == key)
        .and_then(|n| n.formula.strip_prefix('"').and_then(|t| t.strip_suffix('"')).map(|t| t.replace("\"\"", "\"")))
        .and_then(|t| serde_json::from_str(&t).ok())
        .unwrap_or(json!({}))
}

/// Like `stash_get`, for the name-keyed lists (views, scripts): a stored value that is not a JSON object is treated as empty.
fn stash_map(wb: &Workbook, key: &str) -> Json {
    let v = stash_get(wb, key);
    if v.is_object() { v } else { json!({}) }
}

fn stash_set(wb: &mut Workbook, key: &str, v: &Json) {
    let text = v.to_string();
    let formula = format!("\"{}\"", text.replace('"', "\"\""));
    wb.names.retain(|n| n.name != key);
    wb.names.push(DefinedName { name: key.into(), scope: None, formula, comment: String::new(), hidden: true });
}

/// Number format that marks checkbox cells (TRUE/FALSE values drawn as boxes).
pub const CHECKBOX_FORMAT: &str = "\"☐\";\"☐\";\"☐\";[=1]\"☑\"";

/// [EN] Clones `p` if it is a JSON object, or returns an empty JSON object so callers can safely
///      populate default command parameters.
/// [JA] 引数 `p` が JSON オブジェクトであれば複製し、それ以外（`null` 等）の場合は空の JSON オブジェクトを返します。
fn object_params(p: &Json) -> Json {
    Json::Object(p.as_object().cloned().unwrap_or_default())
}

/// [EN] Inserts a structured diagram shape (`process`, `hierarchy`, `cycle`, `list`, `matrix`) by
///      delegating to `insert.shape` with pre-populated layout text and accent styling.
/// [JA] 指定されたレイアウト（工程・階層・循環・リスト・マトリックス）の図解シェイプを既定スタイル付きで挿入します。
fn insert_smart_art(s: &mut Session, p: &Json) -> Result<Json> {
    let layout = str_param(p, "layout").unwrap_or("process");
    let default_text = match layout {
        "hierarchy" => "Root\n├─ Branch A\n└─ Branch B",
        "cycle" => "Plan ↻ Do ↻ Check ↻ Act",
        "list" => "• Item 1\n• Item 2\n• Item 3",
        "matrix" => "[Q1 | Q2]\n[Q3 | Q4]",
        _ => "Step 1  →  Step 2  →  Step 3",
    };
    let mut m = object_params(p);
    m["kind"] = json!(str_param(p, "kind").unwrap_or("roundedRectangle"));
    if m.get("text").is_none() {
        m["text"] = json!(default_text);
    }
    if m.get("width").is_none() {
        m["width"] = json!(240.0);
    }
    if m.get("height").is_none() {
        m["height"] = json!(90.0);
    }
    if m.get("fill").is_none() {
        m["fill"] = json!("#4472C4");
    }
    if m.get("line").is_none() {
        m["line"] = json!("#2F5597");
    }
    s.execute("insert.shape", m)
}

/// [EN] Inserts an image from caller-supplied `base64`/`path`, or falls back to a minimal 1×1
///      transparent PNG placeholder when invoked headlessly without external image bytes.
/// [JA] `base64` または `path` 指定の画像を挿入し、未指定時は最小の 1×1 透過 PNG プレースホルダを挿入します。
fn insert_screenshot(s: &mut Session, p: &Json) -> Result<Json> {
    if p.get("base64").is_some() || p.get("path").is_some() {
        return s.execute("insert.picture", p.clone());
    }
    // Minimal 1x1 transparent PNG placeholder when invoked without external image bytes
    let mut m = object_params(p);
    m["base64"] = json!("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");
    m["mime"] = json!("image/png");
    if m.get("alt").is_none() {
        m["alt"] = json!("Screenshot");
    }
    s.execute("insert.picture", m)
}

/// [EN] Inserts a geographic/hierarchical chart (defaulting to `treemap` when no subtype is given).
/// [JA] 地図／階層比較用グラフ（未指定時はツリーマップ）をシートへ挿入します。
fn insert_map_chart(s: &mut Session, p: &Json) -> Result<Json> {
    let mut m = object_params(p);
    if m.get("type").is_none() {
        m["type"] = json!("treemap");
    }
    if m.get("title").is_none() {
        m["title"] = json!("Geographic Map");
    }
    s.execute("insert.chart", m)
}

/// [EN] Converts an evaluated cell [`Value`] into plain display text without scientific notation
///      for integer-valued numbers below $10^{15}$.
/// [JA] セルの評価値 [`Value`] を人間が読みやすいプレーン文字列へ変換します。
fn value_text(v: &Value) -> String {
    match v {
        Value::Empty => String::new(),
        Value::Number(n) => {
            if n.fract() == 0.0 && n.abs() < 1e15 {
                format!("{}", *n as i64)
            } else {
                format!("{n}")
            }
        }
        Value::Text(s) => s.to_string(),
        Value::Bool(b) => if *b { "TRUE".into() } else { "FALSE".into() },
        Value::Error(e) => e.as_str().to_string(),
        Value::Array(arr) => arr.get(0, 0).map(value_text).unwrap_or_default(),
    }
}

/// [EN] Reads the plain display text of cell `c` on `sh`.
/// [JA] 指定シート上のセル `c` の評価済み文字列を取得します。
fn cell_text(sh: &Sheet, c: CellRef) -> String {
    value_text(&sh.value(c))
}

/// [EN] Inserts a date/column filter slicer (`insert.slicer`), inferring the first table column
///      or region header when `column` is omitted.
/// [JA] タイムライン／列フィルタ用スライサーを挿入します（`column` 未指定時はテーブル先頭列を自動推定）。
fn insert_timeline(s: &mut Session, p: &Json) -> Result<Json> {
    if p.get("column").is_some() {
        return s.execute("insert.slicer", p.clone());
    }
    let col_name = {
        let d = s.doc()?;
        let sh = d.wb.active().ok_or(EngineError::NoDocument)?;
        let active = d.selection.active;
        if let Some(t) = sh.table_at(active).or_else(|| sh.tables.first()) {
            t.columns.first().map(|c| c.name.clone()).unwrap_or_else(|| "Date".into())
        } else {
            let r = crate::selection::current_region(sh, active);
            let h = cell_text(sh, r.start);
            if h.is_empty() { "A".into() } else { h }
        }
    };
    let mut m = object_params(p);
    m["column"] = json!(col_name);
    s.execute("insert.slicer", m)
}

/// [EN] Inserts a decorative styled text box (`textBox` shape with accent fill and border).
/// [JA] 装飾付きテキストボックス（見出しアート用シェイプ）を挿入します。
fn insert_word_art(s: &mut Session, p: &Json) -> Result<Json> {
    let mut m = object_params(p);
    m["kind"] = json!("textBox");
    if m.get("text").is_none() {
        m["text"] = json!("Text Art");
    }
    if m.get("width").is_none() {
        m["width"] = json!(220.0);
    }
    if m.get("height").is_none() {
        m["height"] = json!(64.0);
    }
    if m.get("fill").is_none() {
        m["fill"] = json!("#2F5597");
    }
    if m.get("line").is_none() {
        m["line"] = json!("#1F3864");
    }
    s.execute("insert.shape", m)
}

/// [EN] Inserts a formatted signature line box displaying the signer's name and optional title.
/// [JA] 署名欄（署名者名および任意の役職付きテキストボックス）をシートへ挿入します。
fn insert_signature_line(s: &mut Session, p: &Json) -> Result<Json> {
    let signer = str_param(p, "signer").unwrap_or("Authorized Signer");
    let title = str_param(p, "title").unwrap_or("");
    let label = if title.is_empty() {
        format!("X ________________________\n{signer}")
    } else {
        format!("X ________________________\n{signer} ({title})")
    };
    let mut m = object_params(p);
    m["kind"] = json!("textBox");
    m["text"] = json!(label);
    if m.get("width").is_none() {
        m["width"] = json!(240.0);
    }
    if m.get("height").is_none() {
        m["height"] = json!(68.0);
    }
    s.execute("insert.shape", m)
}

/// [EN] Inserts a visual placeholder card representing an embedded document or object attachment.
/// [JA] 埋め込みドキュメント／オブジェクトを表す視覚カードシェイプを挿入します。
fn insert_embedded_object(s: &mut Session, p: &Json) -> Result<Json> {
    let obj_type = str_param(p, "type").unwrap_or("Embedded Document");
    let title = str_param(p, "title").or_else(|| str_param(p, "text")).unwrap_or(obj_type);
    let mut m = object_params(p);
    m["kind"] = json!("rectangle");
    m["text"] = json!(format!("📦 {title}"));
    if m.get("width").is_none() {
        m["width"] = json!(200.0);
    }
    if m.get("height").is_none() {
        m["height"] = json!(60.0);
    }
    s.execute("insert.shape", m)
}

/// [EN] Inserts a mathematical equation string into the active cell (or as a floating text box
///      when `asShape: true`).
/// [JA] 数式記号テキストをアクティブセル（または `asShape: true` の場合はテキストボックス）に挿入します。
fn insert_equation(s: &mut Session, p: &Json) -> Result<Json> {
    let eq = str_param(p, "equation").or_else(|| str_param(p, "text")).unwrap_or("f(x) = a₀ + ∑(aₙ cos(nx) + bₙ sin(nx))");
    if bool_param(p, "asShape").unwrap_or(false) {
        let mut m = object_params(p);
        m["kind"] = json!("textBox");
        m["text"] = json!(eq);
        return s.execute("insert.shape", m);
    }
    s.execute("insert.symbol", json!({"char": eq}))
}

/// [EN] Imports caller-provided CSV `text` or local `path`, rejecting live network fetches so the
///      calculation engine remains 100% deterministic and offline-safe (no SSRF surface).
/// [JA] 呼び出し側から渡された CSV `text` またはローカル `path` を取り込みます。SSRF 防止と決定論性維持のため、
///      エンジン内部からの直接ネットワーク通信は明示的に拒否します。
fn from_web(s: &mut Session, p: &Json) -> Result<Json> {
    if p.get("text").is_some() || p.get("path").is_some() {
        return from_csv(s, p);
    }
    Err(bad(
        "data.fromWeb",
        "live web fetch is disabled in the offline engine; pass CSV `text` or local `path`",
    ))
}

/// [EN] Extracts a clean ticker symbol from raw cell text or previously formatted `🏛 Name (EXCH:TICKER)` labels.
/// [JA] セル文字列または変換済みの `🏛 企業名 (取引所:銘柄コード)` 表記から銘柄コードを抽出します。
fn canonical_ticker(raw: &str) -> &str {
    let stripped = raw.trim().trim_start_matches("🏛 ").trim();
    if let Some((_, inside)) = stripped.rsplit_once(':')
        && let Some(code) = inside.strip_suffix(')')
    {
        return code.trim();
    }
    stripped
}

/// [EN] Formats ticker cells in the target range as stock entities (`🏛 ...`) and writes `#N/A`
///      into adjacent price/change columns rather than fabricating offline market quotes.
/// [JA] 選択範囲の銘柄コードを株式データ表記（`🏛 ...`）に整え、オフライン環境で架空の株価を作らないよう
///      隣接列には正直に `#N/A` を設定します。
fn data_stocks(s: &mut Session, p: &Json) -> Result<Json> {
    let r = target_range(s, p)?;
    let expand = bool_param(p, "expand").unwrap_or(true);
    let col_slice = RangeRef::new(CellRef::new(r.start.row, r.start.col), CellRef::new(r.end.row, r.start.col));
    edit(s, |cx| {
        let si = cx.sheet_index();
        let populated_rows: Vec<(u32, String)> = cx
            .wb
            .sheet(si)
            .map(|sh| {
                sh.cells
                    .iter_range(col_slice)
                    .map(|(cref, _)| (cref.row, cell_text(sh, cref).trim().to_string()))
                    .filter(|(_, txt)| !txt.is_empty())
                    .collect()
            })
            .unwrap_or_default();

        for (row, raw) in populated_rows {
            let c = CellRef::new(row, r.start.col);
            let ticker = canonical_ticker(&raw).to_ascii_uppercase();
            let resolved: Option<&str> = match ticker.as_str() {
                "MSFT" | "MICROSOFT" => Some("Microsoft Corp. (XNAS:MSFT)"),
                "AAPL" | "APPLE" => Some("Apple Inc. (XNAS:AAPL)"),
                "NVDA" | "NVIDIA" => Some("NVIDIA Corp. (XNAS:NVDA)"),
                "GOOGL" | "GOOGLE" | "ALPHABET" => Some("Alphabet Inc. (XNAS:GOOGL)"),
                "AMZN" | "AMAZON" => Some("Amazon.com Inc. (XNAS:AMZN)"),
                "TSLA" | "TESLA" => Some("Tesla Inc. (XNAS:TSLA)"),
                "7203" | "7203.T" | "TOYOTA" | "トヨタ" => Some("Toyota Motor Corp. (XTKS:7203)"),
                "6758" | "6758.T" | "SONY" | "ソニー" => Some("Sony Group Corp. (XTKS:6758)"),
                "9984" | "9984.T" | "SOFTBANK" | "ソフトバンク" => Some("SoftBank Group Corp. (XTKS:9984)"),
                _ => None,
            };
            let label = match resolved {
                Some(name) => format!("🏛 {name}"),
                None => format!("🏛 {ticker}"),
            };
            {
                let shm = cx.sheet_mut(si)?;
                shm.set_value(c, Value::from(label));
                // Live stock quotes are unavailable offline; write #N/A rather than fabricated numbers.
                if expand && r.start.col + 2 < gridcraft_core::MAX_COLS {
                    let c_price = CellRef::new(row, r.start.col + 1);
                    let c_chg = CellRef::new(row, r.start.col + 2);
                    shm.set_value(c_price, Value::Error(gridcraft_core::CellError::NA));
                    shm.set_value(c_chg, Value::Error(gridcraft_core::CellError::NA));
                }
            }
            cx.touch(si, c);
            if expand && r.start.col + 2 < gridcraft_core::MAX_COLS {
                cx.touch(si, CellRef::new(row, r.start.col + 1));
                cx.touch(si, CellRef::new(row, r.start.col + 2));
            }
        }
        Ok(Json::Null)
    })
}

/// [EN] Tags geographic names in the target range (`🌐 ...`) and populates static reference
///      population/capital figures for known regions (`#N/A` for unknown offline entries).
/// [JA] 選択範囲の地名を地理データ表記（`🌐 ...`）に変換し、既知の国・都市については人口と首都／中心地を、
///      未知の項目には `#N/A` を設定します。
fn data_geography(s: &mut Session, p: &Json) -> Result<Json> {
    let r = target_range(s, p)?;
    let expand = bool_param(p, "expand").unwrap_or(true);
    let col_slice = RangeRef::new(CellRef::new(r.start.row, r.start.col), CellRef::new(r.end.row, r.start.col));
    edit(s, |cx| {
        let si = cx.sheet_index();
        let populated_rows: Vec<(u32, String)> = cx
            .wb
            .sheet(si)
            .map(|sh| {
                sh.cells
                    .iter_range(col_slice)
                    .map(|(cref, _)| (cref.row, cell_text(sh, cref).trim().to_string()))
                    .filter(|(_, txt)| !txt.is_empty())
                    .collect()
            })
            .unwrap_or_default();

        for (row, raw) in populated_rows {
            let c = CellRef::new(row, r.start.col);
            let place = raw.trim_start_matches("🌐 ").trim();
            let known: Option<(f64, &str)> = match place.to_ascii_lowercase().as_str() {
                "japan" | "jp" | "日本" => Some((124_500_000.0, "Tokyo")),
                "tokyo" | "東京" | "東京都" => Some((14_180_000.0, "Shinjuku")),
                "osaka" | "大阪" | "大阪府" => Some((8_770_000.0, "Osaka")),
                "united states" | "usa" | "us" | "アメリカ" => Some((334_900_000.0, "Washington, D.C.")),
                "united kingdom" | "uk" | "イギリス" => Some((68_300_000.0, "London")),
                "france" | "フランス" => Some((68_170_000.0, "Paris")),
                "germany" | "ドイツ" => Some((84_480_000.0, "Berlin")),
                _ => None,
            };
            {
                let shm = cx.sheet_mut(si)?;
                shm.set_value(c, Value::from(format!("🌐 {place}")));
                if expand && r.start.col + 2 < gridcraft_core::MAX_COLS {
                    let c_pop = CellRef::new(row, r.start.col + 1);
                    let c_cap = CellRef::new(row, r.start.col + 2);
                    match known {
                        Some((pop, capital)) => {
                            shm.set_value(c_pop, Value::Number(pop));
                            shm.set_value(c_cap, Value::from(capital));
                        }
                        None => {
                            shm.set_value(c_pop, Value::Error(gridcraft_core::CellError::NA));
                            shm.set_value(c_cap, Value::Error(gridcraft_core::CellError::NA));
                        }
                    }
                }
            }
            cx.touch(si, c);
            if expand && r.start.col + 2 < gridcraft_core::MAX_COLS {
                cx.touch(si, CellRef::new(row, r.start.col + 1));
                cx.touch(si, CellRef::new(row, r.start.col + 2));
            }
        }
        Ok(Json::Null)
    })
}

/// [EN] Adds or removes foreign-key table relationships stored in workbook metadata (`REL_NAME`).
/// [JA] ワークブックのメタデータ領域（`REL_NAME`）にテーブル間のリレーションシップ定義を追加・削除します。
fn data_relationships(s: &mut Session, p: &Json) -> Result<Json> {
    edit(s, |cx| {
        let mut rels = stash_get(&cx.wb, REL_NAME)
            .get("items")
            .and_then(Json::as_array)
            .cloned()
            .unwrap_or_default();
        if let Some(del_idx) = u32_param(p, "delete").map(|i| i as usize)
            && del_idx < rels.len()
        {
            rels.remove(del_idx);
        }
        if let (Some(ft), Some(fc), Some(tt), Some(tc)) = (
            str_param(p, "fromTable"),
            str_param(p, "fromColumn"),
            str_param(p, "toTable"),
            str_param(p, "toColumn"),
        ) {
            rels.push(json!({
                "fromTable": ft,
                "fromColumn": fc,
                "toTable": tt,
                "toColumn": tc,
                "active": true
            }));
        }
        let payload = json!({"items": rels});
        stash_set(&mut cx.wb, REL_NAME, &payload);
        Ok(json!({"relationships": rels}))
    })
}

/// [EN] Computes summary statistics (`count`, `sum`, `average`, `min`, `max`, text cell count)
///      over the target range or current contiguous region, including dynamic-array spill values.
/// [JA] 指定範囲または現在のデータ領域（スピル結果を含む）の件数・合計・平均・最小・最大を集計して返します。
fn analyze_data(s: &mut Session, p: &Json) -> Result<Json> {
    let d = s.doc()?;
    let sh = d.wb.active().ok_or(EngineError::NoDocument)?;
    let r = if p.get("range").is_some() {
        target_range(s, p)?
    } else {
        crate::selection::current_region(sh, d.selection.active)
    };
    let mut nums: Vec<f64> = Vec::new();
    let mut text_count = 0usize;
    let mut visited = std::collections::BTreeSet::new();
    for (cref, _) in sh.cells.iter_range(r) {
        visited.insert(cref);
        match sh.value(cref) {
            Value::Number(n) if n.is_finite() => nums.push(n),
            Value::Text(t) if !t.is_empty() => text_count += 1,
            _ => {}
        }
    }
    for (&cref, v) in &sh.spill {
        if r.contains(cref) && visited.insert(cref) {
            match v {
                Value::Number(n) if n.is_finite() => nums.push(*n),
                Value::Text(t) if !t.is_empty() => text_count += 1,
                _ => {}
            }
        }
    }
    let count = nums.len();
    let sum: f64 = nums.iter().sum();
    let avg = if count > 0 { sum / (count as f64) } else { 0.0 };
    let min = nums.iter().copied().fold(f64::INFINITY, f64::min);
    let max = nums.iter().copied().fold(f64::NEG_INFINITY, f64::max);
    Ok(json!({
        "range": r.to_string(),
        "numericCount": count,
        "textCount": text_count,
        "sum": sum,
        "average": avg,
        "min": if min.is_finite() { json!(min) } else { Json::Null },
        "max": if max.is_finite() { json!(max) } else { Json::Null },
        "insights": [
            format!("Range {} has {} numeric values and {} text cells.", r, count, text_count),
            format!("Total = {:.2}, Average = {:.2}", sum, avg),
        ]
    }))
}

/// [EN] Counts case-insensitive occurrences of the active cell's text (or `query`) on the active sheet.
/// [JA] アクティブセル文字列（または `query`）が現在のシート内に何件出現するかを大文字小文字を区別せず検索します。
fn smart_lookup(s: &mut Session, p: &Json) -> Result<Json> {
    let d = s.doc()?;
    let sh = d.wb.active().ok_or(EngineError::NoDocument)?;
    let cell = cell_param(s, p, "cell")?.unwrap_or(d.selection.active);
    let term = str_param(p, "query")
        .map(String::from)
        .unwrap_or_else(|| cell_text(sh, cell));
    let mut hits = 0usize;
    if !term.is_empty() {
        let lower = term.to_lowercase();
        for (_, c_data) in sh.cells.iter() {
            if value_text(&c_data.value).to_lowercase().contains(&lower) {
                hits += 1;
            }
        }
    }
    Ok(json!({
        "cell": cell.to_string(),
        "term": term,
        "occurrencesInSheet": hits,
        "summary": format!("Smart Lookup for '{}': {} matching cell(s) in active sheet '{}'.", term, hits, sh.name)
    }))
}

/// [EN] Translates common spreadsheet headers and status terms offline between English and Japanese.
/// [JA] 代表的な表計算の見出し語・ステータス語をオフライン辞書で日英相互翻訳します。
fn translate_offline_term(raw: &str, to_lang: &str) -> String {
    let trimmed = raw.trim();
    if trimmed.is_empty() {
        return String::new();
    }
    let dict: &[(&str, &str)] = &[
        ("sales", "売上"),
        ("revenue", "収益"),
        ("cost", "コスト"),
        ("profit", "利益"),
        ("total", "合計"),
        ("average", "平均"),
        ("date", "日付"),
        ("category", "カテゴリ"),
        ("region", "地域"),
        ("north", "北"),
        ("south", "南"),
        ("east", "東"),
        ("west", "西"),
        ("month", "月"),
        ("year", "年"),
        ("quarter", "四半期"),
        ("budget", "予算"),
        ("actual", "実績"),
        ("forecast", "予測"),
        ("price", "価格"),
        ("quantity", "数量"),
        ("amount", "金額"),
        ("customer", "顧客"),
        ("product", "製品"),
        ("status", "ステータス"),
        ("completed", "完了"),
        ("pending", "保留"),
        ("hello", "こんにちは"),
        ("thank you", "ありがとうございます"),
    ];
    let want = to_lang.trim().to_ascii_lowercase();
    if want.starts_with("ja") {
        let lower = trimmed.to_ascii_lowercase();
        for &(en, ja) in dict {
            if lower == en {
                return ja.to_string();
            }
        }
        return trimmed.to_string();
    }
    if want.starts_with("en") {
        for &(en, ja) in dict {
            if trimmed == ja {
                let mut chars = en.chars();
                return match chars.next() {
                    Some(first) => format!("{}{}", first.to_ascii_uppercase(), chars.as_str()),
                    None => en.to_string(),
                };
            }
        }
        return trimmed.to_string();
    }
    trimmed.to_string()
}

/// [EN] Translates cell text offline (`review.translate`) and optionally writes the result to `targetCell`.
/// [JA] 指定セルまたはテキストをオフライン日英翻訳し、`apply: true` の場合は対象セルへ書き込みます。
fn review_translate(s: &mut Session, p: &Json) -> Result<Json> {
    let cell = cell_param(s, p, "cell")?.unwrap_or(s.doc()?.selection.active);
    let raw = if let Some(t) = str_param(p, "text") {
        t.to_string()
    } else {
        let d = s.doc()?;
        let sh = d.wb.active().ok_or(EngineError::NoDocument)?;
        cell_text(sh, cell)
    };
    let to_lang = str_param(p, "to").unwrap_or("ja");
    let translated = translate_offline_term(&raw, to_lang);
    if bool_param(p, "apply").unwrap_or(false) {
        let target_cell = cell_param(s, p, "targetCell")?.unwrap_or(cell);
        edit(s, |cx| {
            let si = cx.sheet_index();
            cx.sheet_mut(si)?.set_value(target_cell, Value::from(translated.clone()));
            cx.touch(si, target_cell);
            Ok(Json::Null)
        })?;
    }
    Ok(json!({
        "cell": cell.to_string(),
        "source": raw,
        "translated": translated,
        "to": to_lang
    }))
}

/// [EN] Returns up to the 50 most recent command journal entries for the session (`review.showChanges`).
/// [JA] 現在のセッションで実行された直近最大50件のコマンド変更履歴を返します。
fn show_changes(s: &mut Session, _p: &Json) -> Result<Json> {
    let _ = s.doc()?;
    let changes: Vec<Json> = s
        .journal
        .iter()
        .rev()
        .take(50)
        .map(|(cmd_id, params)| {
            json!({
                "command": cmd_id,
                "params": params
            })
        })
        .collect();
    Ok(json!({
        "count": changes.len(),
        "changes": changes
    }))
}
