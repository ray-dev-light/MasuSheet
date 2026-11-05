// Modified in 2026 for MasuSheet (Android spreadsheet fork of GridCraft).
//! Information, logical, web and cube functions.

use std::cmp::Ordering;

use gridcraft_core::{Array, CellError, Value, compare_text};

use crate::util::{A, MAX_CELLS, R, S, has, scalar, text};
use crate::{Arg, Ctx, FnSpec, VAR};

// ---------------------------------------------------------------------------------------------
// Information

fn first(a: &[Arg]) -> Value {
    a.first().map(scalar).unwrap_or(Value::Empty)
}

fn is_blank(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    Ok(Value::Bool(matches!(first(a), Value::Empty)))
}
fn is_err(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    Ok(Value::Bool(matches!(first(a), Value::Error(e) if e != CellError::NA)))
}
fn is_error(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    Ok(Value::Bool(matches!(first(a), Value::Error(_))))
}
fn is_na(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    Ok(Value::Bool(matches!(first(a), Value::Error(CellError::NA))))
}
fn is_number(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    Ok(Value::Bool(matches!(first(a), Value::Number(_))))
}
fn is_text(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    Ok(Value::Bool(matches!(first(a), Value::Text(_))))
}
fn is_nontext(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    Ok(Value::Bool(!matches!(first(a), Value::Text(_))))
}
fn is_logical(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    Ok(Value::Bool(matches!(first(a), Value::Bool(_))))
}

fn parity(a: &[Arg]) -> R<f64> {
    match first(a) {
        Value::Bool(_) => Err(CellError::Value),
        v => {
            let n = v.to_number()?;
            if n.abs() > 9.007_199_254_740_992e15 {
                return Err(CellError::Num);
            }
            Ok(n.trunc().abs() % 2.0)
        }
    }
}
fn is_even(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    Ok(Value::Bool(parity(a)? == 0.0))
}
fn is_odd(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    Ok(Value::Bool(parity(a)? == 1.0))
}

fn type_of(v: &Value) -> f64 {
    match v {
        Value::Empty | Value::Number(_) => 1.0,
        Value::Text(_) => 2.0,
        Value::Bool(_) => 4.0,
        Value::Error(_) => 16.0,
        Value::Array(_) => 64.0,
    }
}
fn type_fn(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    let Some(arg) = a.first() else { return Err(CellError::Value) };
    let t = match &arg.value {
        // A single-cell reference reports the type of its content.
        Value::Array(arr) if arg.from_ref && arr.rows == 1 && arr.cols == 1 => arr.data.first().map_or(1.0, type_of),
        v => type_of(v),
    };
    Ok(Value::Number(t))
}

fn error_type(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    match first(a) {
        Value::Error(e) => Ok(Value::Number(e.code() as f64)),
        _ => Err(CellError::NA),
    }
}

fn na(_a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    Err(CellError::NA)
}

fn n_fn(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    // N(range) reads the range's first cell, but a computed array (N(A1:A3>0), N({...})) is
    // converted element by element, so N can stand in for `--` inside SUMPRODUCT.
    if let Some(Arg { value: Value::Array(arr), from_ref: false }) = a.first() {
        let data = arr.data.iter().map(|v| n_of(v.clone()).unwrap_or_else(Value::Error)).collect();
        return Array::new(arr.rows, arr.cols, data).map(Value::from).ok_or(CellError::Calc);
    }
    n_of(first(a))
}

fn n_of(v: Value) -> R<Value> {
    match v {
        Value::Number(n) => Ok(Value::Number(n)),
        Value::Bool(b) => Ok(Value::Number(if b { 1.0 } else { 0.0 })),
        Value::Error(e) => Err(e),
        _ => Ok(Value::Number(0.0)),
    }
}

fn info(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    let t = text(a, 0)?.to_ascii_lowercase();
    let v: Value = match t.as_str() {
        "directory" => "/".into(),
        "numfile" => Value::Number(1.0),
        "origin" => "$A:$A$1".into(),
        "osversion" => "GridCraft".into(),
        "recalc" => "Automatic".into(),
        "release" => "16.0".into(),
        "system" => "pcdos".into(),
        _ => return Err(CellError::Value),
    };
    Ok(v)
}

// ---------------------------------------------------------------------------------------------
// Logical

/// Collects booleans for AND/OR/XOR: references and arrays contribute numbers and booleans
/// (text and blanks skipped); direct scalars coerce (empty literal = FALSE).
fn logicals(a: &[Arg]) -> R<Vec<bool>> {
    let mut out = Vec::new();
    for arg in a {
        match &arg.value {
            Value::Array(arr) => {
                for v in arr.iter() {
                    match v {
                        Value::Number(n) => out.push(*n != 0.0),
                        Value::Bool(b) => out.push(*b),
                        Value::Error(e) => return Err(*e),
                        _ => {}
                    }
                }
            }
            Value::Error(e) => return Err(*e),
            Value::Number(n) => out.push(*n != 0.0),
            Value::Bool(b) => out.push(*b),
            _ if arg.from_ref => {}
            v => out.push(v.to_bool()?),
        }
    }
    if out.is_empty() {
        return Err(CellError::Value);
    }
    Ok(out)
}

fn and(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    Ok(Value::Bool(logicals(a)?.iter().all(|b| *b)))
}
fn or(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    Ok(Value::Bool(logicals(a)?.iter().any(|b| *b)))
}
fn xor(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    Ok(Value::Bool(logicals(a)?.iter().filter(|b| **b).count() % 2 == 1))
}
fn not(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    Ok(Value::Bool(!first(a).to_bool()?))
}

/// Applies `f` to scalar argument values. When any argument at a `drivers` position is an
/// array, `f` runs per element over the broadcast shape of all arguments; otherwise it receives
/// the arguments whole (so the chosen value may itself be an array).
fn drive(a: &[Arg], drivers: &dyn Fn(usize) -> bool, f: &dyn Fn(&[Value]) -> R<Value>) -> R<Value> {
    let any = a.iter().enumerate().any(|(i, x)| drivers(i) && matches!(x.value, Value::Array(_)));
    if !any {
        let vals: Vec<Value> = a.iter().map(|x| x.value.clone()).collect();
        return f(&vals).map(empty_to_zero);
    }
    let (mut rows, mut cols) = (1usize, 1usize);
    for x in a {
        if let Value::Array(arr) = &x.value {
            rows = rows.max(arr.rows);
            cols = cols.max(arr.cols);
        }
    }
    if rows.saturating_mul(cols) > MAX_CELLS {
        return Err(CellError::Num);
    }
    let mut data = Vec::with_capacity(rows * cols);
    let mut vals: Vec<Value> = Vec::with_capacity(a.len());
    for r in 0..rows {
        for c in 0..cols {
            vals.clear();
            vals.extend(a.iter().map(|x| match &x.value {
                Value::Array(arr) => arr.get_broadcast(r, c),
                v => v.clone(),
            }));
            let out = match f(&vals) {
                Ok(Value::Array(arr)) => arr.data.first().cloned().unwrap_or(Value::Empty),
                Ok(v) => v,
                Err(e) => Value::Error(e),
            };
            data.push(out);
        }
    }
    Array::new(rows, cols, data).map(Value::from).ok_or(CellError::Calc)
}

fn empty_to_zero(v: Value) -> Value {
    if matches!(v, Value::Empty) { Value::Number(0.0) } else { v }
}

fn if_fn(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    drive(a, &|i| i == 0, &|v| {
        let cond = v.first().cloned().unwrap_or(Value::Empty).to_bool()?;
        if cond { Ok(v.get(1).cloned().unwrap_or(Value::Bool(true))) } else { Ok(v.get(2).cloned().unwrap_or(Value::Bool(false))) }
    })
}

fn ifs(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    if !a.len().is_multiple_of(2) {
        return Err(CellError::NA);
    }
    drive(a, &|i| i % 2 == 0, &|v| {
        for pair in v.chunks(2) {
            if let [c, r] = pair
                && c.to_bool()?
            {
                return Ok(r.clone());
            }
        }
        Err(CellError::NA)
    })
}

fn iferror(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    drive(a, &|i| i == 0, &|v| match v.first() {
        Some(Value::Error(_)) => Ok(v.get(1).cloned().unwrap_or(Value::Empty)),
        Some(x) => Ok(x.clone()),
        None => Err(CellError::Value),
    })
}

fn ifna(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    drive(a, &|i| i == 0, &|v| match v.first() {
        Some(Value::Error(CellError::NA)) => Ok(v.get(1).cloned().unwrap_or(Value::Empty)),
        Some(x) => Ok(x.clone()),
        None => Err(CellError::Value),
    })
}

fn switch_equal(a: &Value, b: &Value) -> bool {
    match (a, b) {
        (Value::Text(x), Value::Text(y)) => compare_text(x, y) == Ordering::Equal,
        (Value::Number(x), Value::Number(y)) => x == y,
        (Value::Bool(x), Value::Bool(y)) => x == y,
        (Value::Empty, Value::Empty) => true,
        (Value::Empty, Value::Number(x)) | (Value::Number(x), Value::Empty) => *x == 0.0,
        (Value::Empty, Value::Text(t)) | (Value::Text(t), Value::Empty) => t.is_empty(),
        _ => false,
    }
}

fn switch(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    drive(a, &|i| i == 0, &|v| {
        let Some((expr, rest)) = v.split_first() else { return Err(CellError::Value) };
        if let Value::Error(e) = expr {
            return Err(*e);
        }
        let mut it = rest.chunks(2);
        for pair in it.by_ref() {
            match pair {
                [case, result] => {
                    if let Value::Error(e) = case {
                        return Err(*e);
                    }
                    if switch_equal(expr, case) {
                        return Ok(result.clone());
                    }
                }
                [default] => return Ok(default.clone()),
                _ => {}
            }
        }
        Err(CellError::NA)
    })
}

fn choose(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    drive(a, &|i| i == 0, &|v| {
        let idx = v.first().cloned().unwrap_or(Value::Empty).to_number()?.trunc();
        if idx < 1.0 || idx >= v.len() as f64 {
            return Err(CellError::Value);
        }
        v.get(idx as usize).cloned().ok_or(CellError::Value)
    })
}

fn true_fn(_a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    Ok(Value::Bool(true))
}
fn false_fn(_a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    Ok(Value::Bool(false))
}

// ---------------------------------------------------------------------------------------------
// Web and cube

fn hyperlink(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    if has(a, 1) {
        let v = a.get(1).map(scalar).unwrap_or(Value::Empty);
        return Ok(empty_to_zero(v));
    }
    // Excel returns the link as text, so HYPERLINK(1) is "1" and ISTEXT is TRUE.
    Ok(Value::Text(text(a, 0)?.into()))
}

fn encodeurl(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    let s = text(a, 0)?;
    let mut out = String::with_capacity(s.len());
    for b in s.bytes() {
        if b.is_ascii_alphanumeric() || matches!(b, b'-' | b'_' | b'.' | b'~') {
            out.push(char::from(b));
        } else {
            out.push_str(&format!("%{b:02X}"));
        }
    }
    Ok(Value::from(out))
}

fn value_err(_a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    Err(CellError::Value)
}

/// [EN] Unescapes the 5 standard XML character entities (`&lt;`, `&gt;`, `&quot;`, `&apos;`, `&amp;`)
///      and converts finite numeric strings to [`Value::Number`].
/// [JA] 標準 XML 実体参照5種（`&lt;`, `&gt;`, `&quot;`, `&apos;`, `&amp;`）を展開し、
///      有限な数値文字列であれば [`Value::Number`] に変換します。
fn parse_xml_scalar(raw: &str) -> Value {
    let unescaped = if raw.contains('&') {
        raw.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    } else {
        raw.to_string()
    };
    unescaped
        .parse::<f64>()
        .ok()
        .filter(|n| n.is_finite())
        .map_or_else(|| Value::from(unescaped), Value::Number)
}

/// [EN] Evaluates `FILTERXML(xml, xpath)` offline for tag-text paths (`/root/item`, `//price`) and
///      attribute paths (`//item/@id`) without external network or DTD entity expansion (XXE-safe).
/// [JA] 外部通信や DTD 展開を行わない安全なオフライン簡易パーサにより、`FILTERXML(xml, xpath)` の要素値および
///      `@属性` 抽出を実行します（単一結果はスカラ、複数一致時は縦スピル配列を返却）。
fn filterxml(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    let xml = text(a, 0)?;
    let xpath = text(a, 1)?;
    let xp = xpath.trim();
    if xml.trim().is_empty() || xp.is_empty() || !xml.contains('<') || !xp.starts_with('/') {
        return Err(CellError::Value);
    }
    let parts: Vec<&str> = xp.split('/').filter(|s| !s.is_empty()).collect();
    let Some(&last) = parts.last() else {
        return Err(CellError::Value);
    };
    let (target_tag, target_attr) = if let Some(attr) = last.strip_prefix('@') {
        let prev = if parts.len() >= 2 { parts[parts.len() - 2] } else { "" };
        (prev, Some(attr))
    } else {
        (last, None)
    };
    if target_tag.is_empty() {
        return Err(CellError::Value);
    }
    let mut results: Vec<Value> = Vec::new();
    let open_pat = format!("<{target_tag}");
    let close_pat = format!("</{target_tag}>");
    let mut rest = xml.as_str();
    while let Some(pos) = rest.find(&open_pat) {
        let after_name = &rest[pos + open_pat.len()..];
        let valid_boundary = after_name
            .chars()
            .next()
            .is_some_and(|ch| ch.is_whitespace() || ch == '>' || ch == '/');
        if !valid_boundary {
            rest = after_name;
            continue;
        }
        let Some(tag_end) = after_name.find('>') else {
            break;
        };
        let header = &after_name[..tag_end];
        let self_closing = header.ends_with('/');
        if let Some(attr_name) = target_attr {
            let attr_key = format!("{attr_name}=");
            if let Some(apos) = header.find(&attr_key) {
                let val_start = &header[apos + attr_key.len()..].trim_start();
                if let Some(quote) = val_start.chars().next()
                    && (quote == '"' || quote == '\'')
                {
                    let inner = &val_start[1..];
                    if let Some(end_q) = inner.find(quote)
                        && results.len() < MAX_CELLS
                    {
                        results.push(parse_xml_scalar(&inner[..end_q]));
                    }
                }
            }
            rest = &after_name[tag_end + 1..];
        } else if self_closing {
            rest = &after_name[tag_end + 1..];
        } else {
            let body_rest = &after_name[tag_end + 1..];
            if let Some(cpos) = body_rest.find(&close_pat) {
                let content = body_rest[..cpos].trim();
                if !content.is_empty() && !content.contains('<') && results.len() < MAX_CELLS {
                    results.push(parse_xml_scalar(content));
                }
                rest = &body_rest[cpos + close_pat.len()..];
            } else {
                break;
            }
        }
    }
    if results.is_empty() {
        return Err(CellError::Value);
    }
    if results.len() == 1 {
        return Ok(results.remove(0));
    }
    Array::new(results.len(), 1, results).map(Value::from).ok_or(CellError::Calc)
}

/// [EN] Heuristic offline script detector for `DETECTLANGUAGE(text)` returning ISO 639-1 codes
///      (`"ja"`, `"ko"`, `"ru"`, `"ar"`, `"el"`, or `"en"`). Note: Han-only strings without Kana
///      default to `"ja"` in this Japanese-first spreadsheet environment.
/// [JA] 文字コードブロックに基づきオフラインで言語コード（`"ja"`, `"ko"`, `"ru"`, `"ar"`, `"el"`, `"en"`）を
///      判定する `DETECTLANGUAGE(text)` 実装です（漢字のみの語句は日本語優先で `"ja"` を返します）。
fn detectlanguage(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    let s = text(a, 0)?;
    let trimmed = s.trim();
    if trimmed.is_empty() {
        return Err(CellError::Value);
    }
    for ch in trimmed.chars() {
        if ('\u{3040}'..='\u{30FF}').contains(&ch) || ('\u{FF66}'..='\u{FF9F}').contains(&ch) {
            return Ok(Value::from("ja"));
        }
        if ('\u{AC00}'..='\u{D7AF}').contains(&ch) || ('\u{1100}'..='\u{11FF}').contains(&ch) {
            return Ok(Value::from("ko"));
        }
        if ('\u{0400}'..='\u{04FF}').contains(&ch) {
            return Ok(Value::from("ru"));
        }
        if ('\u{0600}'..='\u{06FF}').contains(&ch) {
            return Ok(Value::from("ar"));
        }
        if ('\u{0370}'..='\u{03FF}').contains(&ch) {
            return Ok(Value::from("el"));
        }
    }
    if trimmed.chars().any(|ch| ('\u{4E00}'..='\u{9FFF}').contains(&ch)) {
        return Ok(Value::from("ja"));
    }
    Ok(Value::from("en"))
}

/// [EN] Offline dictionary implementation of `TRANSLATE(text, [source_language], [target_language])`
///      for common English ⇄ Japanese spreadsheet terms; returns the original text when unmatched.
/// [JA] 代表的な表計算用語をオフラインで日英相互翻訳する `TRANSLATE` 関数です（辞書外の文字列はそのまま返却）。
fn translate_fn(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    let s = text(a, 0)?;
    let trimmed = s.trim();
    if trimmed.is_empty() {
        return Ok(Value::from(""));
    }
    let has_ja = trimmed.chars().any(|ch| ('\u{3040}'..='\u{30FF}').contains(&ch) || ('\u{4E00}'..='\u{9FFF}').contains(&ch));
    let target = if has(a, 2) {
        text(a, 2)?.trim().to_ascii_lowercase()
    } else if has(a, 1) {
        text(a, 1)?.trim().to_ascii_lowercase()
    } else if has_ja {
        "en".to_string()
    } else {
        "ja".to_string()
    };
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
    if target.starts_with("ja") {
        let lower = trimmed.to_ascii_lowercase();
        for &(en, ja) in dict {
            if lower == en {
                return Ok(Value::from(ja));
            }
        }
        return Ok(Value::from(trimmed));
    }
    if target.starts_with("en") {
        for &(en, ja) in dict {
            if trimmed == ja {
                let mut c = en.chars();
                let cap = match c.next() {
                    Some(first_ch) => format!("{}{}", first_ch.to_ascii_uppercase(), c.as_str()),
                    None => en.to_string(),
                };
                return Ok(Value::from(cap));
            }
        }
        return Ok(Value::from(trimmed));
    }
    Ok(Value::from(trimmed))
}

/// [EN] Evaluates `GETPIVOTDATA(data_field, pivot_table, [field1, item1], ...)` over a rendered
///      PivotTable range or scalar summary cell, returning `#REF!` when criteria are invalid or unmatched.
/// [JA] 展開済みのピボットテーブル範囲または集計セルから条件に一致する値を取得する `GETPIVOTDATA` 関数です。
fn getpivotdata(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    if a.len() < 2 {
        return Err(CellError::Ref);
    }
    let field = text(a, 0)?;
    if field.trim().is_empty() || !(a.len() - 2).is_multiple_of(2) {
        return Err(CellError::Ref);
    }
    let Some(pt) = a.get(1) else {
        return Err(CellError::Ref);
    };
    match &pt.value {
        Value::Number(n) => Ok(Value::Number(*n)),
        Value::Array(arr) => {
            let field_lower = field.trim().to_lowercase();
            let target_col = (0..arr.cols).find(|&c| {
                matches!(arr.get(0, c), Some(Value::Text(t)) if t.to_lowercase().contains(&field_lower))
            });
            let col = target_col.unwrap_or_else(|| arr.cols.saturating_sub(1));
            if a.len() >= 4 {
                let mut criteria: Vec<(Option<usize>, String)> = Vec::new();
                let mut idx = 2usize;
                while idx + 1 < a.len() {
                    let crit_field = text(a, idx)?.trim().to_lowercase();
                    let crit_item = text(a, idx + 1)?.trim().to_lowercase();
                    let crit_col = (0..arr.cols).find(|&c| {
                        matches!(arr.get(0, c), Some(Value::Text(t)) if t.to_lowercase() == crit_field)
                    });
                    criteria.push((crit_col, crit_item));
                    idx += 2;
                }
                for r in 1..arr.rows {
                    let all_match = criteria.iter().all(|(cc, want)| match cc {
                        Some(c_idx) => matches!(arr.get(r, *c_idx), Some(Value::Text(t)) if t.to_lowercase() == *want),
                        None => (0..arr.cols).any(|c_idx| {
                            matches!(arr.get(r, c_idx), Some(Value::Text(t)) if t.to_lowercase() == *want)
                        }),
                    });
                    if all_match
                        && let Some(v) = arr.get(r, col)
                        && !matches!(v, Value::Empty)
                    {
                        return Ok(v.clone());
                    }
                }
                return Err(CellError::Ref);
            }
            for r in (0..arr.rows).rev() {
                if let Some(v @ Value::Number(_)) = arr.get(r, col) {
                    return Ok(v.clone());
                }
            }
            Err(CellError::Ref)
        }
        Value::Error(e) => Err(*e),
        _ => Err(CellError::Ref),
    }
}

/// [EN] Validates `IMAGE(source, [alt_text], [sizing], [height], [width])` arguments without
///      performing network I/O, returning `alt_text` when provided or `source` as cell text.
/// [JA] 外部通信を行わずに `IMAGE` 関数の引数を検証し、`alt_text`（未指定時は `source`）を表示文字列として返します。
fn image_fn(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    let src = text(a, 0)?;
    if src.trim().is_empty() {
        return Err(CellError::Value);
    }
    if has(a, 2) {
        let sizing = a.get(2).map(scalar).unwrap_or(Value::Empty).to_number()?.trunc();
        if !(0.0..=3.0).contains(&sizing) {
            return Err(CellError::Value);
        }
    }
    if has(a, 3) {
        let h = a.get(3).map(scalar).unwrap_or(Value::Empty).to_number()?;
        if h <= 0.0 || !h.is_finite() {
            return Err(CellError::Value);
        }
    }
    if has(a, 4) {
        let w = a.get(4).map(scalar).unwrap_or(Value::Empty).to_number()?;
        if w <= 0.0 || !w.is_finite() {
            return Err(CellError::Value);
        }
    }
    if has(a, 1) {
        let alt = text(a, 1)?;
        if !alt.trim().is_empty() {
            return Ok(Value::from(alt));
        }
    }
    Ok(Value::from(src))
}

/// [EN] Validates `RTD(prog_id, server, topic1, ...)` arguments and returns `#N/A` because Windows
///      COM real-time servers are unavailable on Android/offline engines.
/// [JA] `RTD` 関数の必須引数を検証し、COM サーバー非対応環境として `#N/A` を返します。
fn rtd_fn(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    let prog = text(a, 0)?;
    let topic = text(a, 2)?;
    if prog.trim().is_empty() || topic.trim().is_empty() {
        return Err(CellError::Value);
    }
    Err(CellError::NA)
}

/// [EN] Validates `STOCKHISTORY` ticker, date range, interval (`0..=2`), and header (`0..=2`)
///      parameters, then returns `#N/A` honestly because no live market feed is bundled offline.
/// [JA] `STOCKHISTORY` の銘柄・日付範囲・間隔・ヘッダ引数を検証し、オフライン計算のため架空の株価は返さず `#N/A` を返します。
fn stockhistory(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    let ticker = text(a, 0)?;
    if ticker.trim().is_empty() {
        return Err(CellError::Value);
    }
    let start = a.get(1).map(scalar).unwrap_or(Value::Empty).to_number()?.trunc();
    let end = if has(a, 2) {
        a.get(2).map(scalar).unwrap_or(Value::Empty).to_number()?.trunc()
    } else {
        start
    };
    if !start.is_finite() || !end.is_finite() || start < 1.0 || end < start {
        return Err(CellError::Value);
    }
    if has(a, 3) {
        let interval = a.get(3).map(scalar).unwrap_or(Value::Empty).to_number()?.trunc() as i32;
        if !(0..=2).contains(&interval) {
            return Err(CellError::Value);
        }
    }
    if has(a, 4) {
        let headers = a.get(4).map(scalar).unwrap_or(Value::Empty).to_number()?.trunc() as i32;
        if !(0..=2).contains(&headers) {
            return Err(CellError::Value);
        }
    }
    // Live market data feed is unavailable in the offline formula engine; never fabricate prices.
    Err(CellError::NA)
}

/// [EN] Propagates upstream errors and validates `COPILOT(prompt, ...)` while keeping formula
///      recalculation deterministic and offline (`#N/A`; interactive AI lives in the AI Assistant panel).
/// [JA] 数式再計算の決定論性とオフライン安全性を保つため、引数検証後に `#N/A` を返します（対話型AIはAIパネルで提供）。
fn copilot_fn(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    let prompt = text(a, 0)?;
    if prompt.trim().is_empty() {
        return Err(CellError::Value);
    }
    for arg in a.iter().skip(1) {
        match &arg.value {
            Value::Error(e) => return Err(*e),
            Value::Array(arr) => {
                for v in arr.iter() {
                    if let Value::Error(e) = v {
                        return Err(*e);
                    }
                }
            }
            _ => {}
        }
    }
    // Worksheet formula evaluation stays deterministic and offline; return #N/A rather than mock AI output.
    Err(CellError::NA)
}

/// [EN] Validates `PY(code, [return_type])` non-empty code argument and returns `#N/A` because no
///      Python interpreter is embedded inside cell recalculation.
/// [JA] `PY` 関数のコード引数を検証し、セル計算内に Python ランタイムを持たないため `#N/A` を返します。
fn py_fn(a: &[Arg], _c: &mut dyn Ctx) -> R<Value> {
    let code = text(a, 0)?;
    if code.trim().is_empty() {
        return Err(CellError::Value);
    }
    // Python runtime is not embedded inside deterministic cell recalculation; return #N/A honestly.
    Err(CellError::NA)
}

pub(crate) fn specs() -> Vec<FnSpec> {
    vec![
        f!("ISBLANK", 1, 1, Information, S, "ISBLANK(value)", "TRUE when the value refers to an empty cell.", is_blank),
        f!("ISERR", 1, 1, Information, S, "ISERR(value)", "TRUE when the value is any error other than #N/A.", is_err),
        f!("ISERROR", 1, 1, Information, S, "ISERROR(value)", "TRUE when the value is any error.", is_error),
        f!("ISNA", 1, 1, Information, S, "ISNA(value)", "TRUE when the value is the #N/A error.", is_na),
        f!("ISNUMBER", 1, 1, Information, S, "ISNUMBER(value)", "TRUE when the value is a number.", is_number),
        f!("ISTEXT", 1, 1, Information, S, "ISTEXT(value)", "TRUE when the value is text.", is_text),
        f!("ISNONTEXT", 1, 1, Information, S, "ISNONTEXT(value)", "TRUE when the value is anything other than text.", is_nontext),
        f!("ISLOGICAL", 1, 1, Information, S, "ISLOGICAL(value)", "TRUE when the value is TRUE or FALSE.", is_logical),
        f!("ISEVEN", 1, 1, Information, S, "ISEVEN(number)", "TRUE when the integer part of a number is even.", is_even),
        f!("ISODD", 1, 1, Information, S, "ISODD(number)", "TRUE when the integer part of a number is odd.", is_odd),
        f!(
            "TYPE",
            1,
            1,
            Information,
            A,
            "TYPE(value)",
            "Returns a code for the kind of value: 1 number, 2 text, 4 logical, 16 error, 64 array.",
            type_fn
        ),
        f!(
            "ERROR.TYPE",
            1,
            1,
            Information,
            S,
            "ERROR.TYPE(error_val)",
            "Returns the number identifying an error value, or #N/A if it is not an error.",
            error_type
        ),
        f!("NA", 0, 0, Information, S, "NA()", "Returns the #N/A error value.", na),
        f!("N", 1, 1, Information, A, "N(value)", "Converts a value to a number: numbers stay, TRUE is 1, everything else is 0.", n_fn),
        f!("INFO", 1, 1, Information, S, "INFO(type_text)", "Returns information about the operating environment.", info),
        f!("AND", 1, VAR, Logical, A, "AND(logical1, [logical2], ...)", "TRUE when every argument is TRUE.", and),
        f!("OR", 1, VAR, Logical, A, "OR(logical1, [logical2], ...)", "TRUE when at least one argument is TRUE.", or),
        f!("XOR", 1, VAR, Logical, A, "XOR(logical1, [logical2], ...)", "TRUE when an odd number of arguments are TRUE.", xor),
        f!("NOT", 1, 1, Logical, S, "NOT(logical)", "Reverses a logical value.", not),
        f!("TRUE", 0, 0, Logical, S, "TRUE()", "Returns the logical value TRUE.", true_fn),
        f!("FALSE", 0, 0, Logical, S, "FALSE()", "Returns the logical value FALSE.", false_fn),
        f!(
            "IF",
            2,
            3,
            Logical,
            A,
            "IF(logical_test, [value_if_true], [value_if_false])",
            "Returns one value when a condition is TRUE and another when it is FALSE.",
            if_fn
        ),
        f!(
            "IFS",
            2,
            VAR,
            Logical,
            A,
            "IFS(logical_test1, value_if_true1, ...)",
            "Returns the value paired with the first condition that is TRUE.",
            ifs
        ),
        f!(
            "IFERROR",
            2,
            2,
            Logical,
            A,
            "IFERROR(value, value_if_error)",
            "Returns a fallback when the value is an error, otherwise the value.",
            iferror
        ),
        f!("IFNA", 2, 2, Logical, A, "IFNA(value, value_if_na)", "Returns a fallback when the value is #N/A, otherwise the value.", ifna),
        f!(
            "SWITCH",
            3,
            VAR,
            Logical,
            A,
            "SWITCH(expression, value1, result1, [default_or_value2, result2], ...)",
            "Compares an expression with a list of values and returns the result paired with the first match.",
            switch
        ),
        f!("CHOOSE", 2, VAR, Lookup, A, "CHOOSE(index_num, value1, [value2], ...)", "Picks a value from the list by its position.", choose),
        f!(
            "HYPERLINK",
            1,
            2,
            Information,
            S,
            "HYPERLINK(link_location, [friendly_name])",
            "Creates a link; the cell shows the friendly name, or the link itself.",
            hyperlink
        ),
        f!("ENCODEURL", 1, 1, Web, S, "ENCODEURL(text)", "Percent-encodes text for use in a URL.", encodeurl),
        f!("WEBSERVICE", 1, 1, Web, S, "WEBSERVICE(url)", "Fetches data from a web service (not available; returns #VALUE!).", value_err),
        f!("FILTERXML", 2, 2, Web, S, "FILTERXML(xml, xpath)", "Extracts data from XML content by using the specified XPath expression.", filterxml),
        f!("DETECTLANGUAGE", 1, 1, Text, S, "DETECTLANGUAGE(text)", "Identifies the language of the specified text and returns its ISO 639-1 code.", detectlanguage),
        f!("TRANSLATE", 1, 3, Text, S, "TRANSLATE(text, [source_language], [target_language])", "Translates common spreadsheet terms offline between English and Japanese.", translate_fn),
        f!("GETPIVOTDATA", 2, VAR, Lookup, A, "GETPIVOTDATA(data_field, pivot_table, [field1, item1], ...)", "Returns visible data stored in a PivotTable report.", getpivotdata),
        f!("IMAGE", 1, 5, Lookup, S, "IMAGE(source, [alt_text], [sizing], [height], [width])", "Inserts an image reference or alt text into a cell.", image_fn),
        f!("RTD", 3, VAR, Lookup, S, "RTD(prog_id, server, topic1, [topic2], ...)", "Retrieves real-time data from a COM server (not available; returns #N/A).", rtd_fn),
        f!("STOCKHISTORY", 2, VAR, Financial, A, "STOCKHISTORY(stock, start_date, [end_date], [interval], [headers], [property0], ...)", "Retrieves historical market data (no live market feed; returns #N/A).", stockhistory),
        f!("CALL", 1, VAR, AddIn, S, "CALL(register_id, [argument1], ...)", "Calls a procedure in a dynamic link library or code resource (returns #VALUE!).", value_err),
        f!("REGISTER.ID", 2, 3, AddIn, S, "REGISTER.ID(module_text, procedure, [type_text])", "Returns the register ID of a DLL or code resource (returns #VALUE!).", value_err),
        f!("COPILOT", 1, VAR, Information, A, "COPILOT(prompt, [context1], ...)", "Cloud AI worksheet function (offline formula engine; returns #N/A).", copilot_fn),
        f!("PY", 1, 2, Information, S, "PY(code, [return_type])", "Python-in-cell function (no embedded Python runtime; returns #N/A).", py_fn),
        f!(
            "CUBEKPIMEMBER",
            3,
            4,
            Cube,
            A,
            "CUBEKPIMEMBER(connection, kpi_name, kpi_property, [caption])",
            "Returns a key performance indicator property from a cube (no cube connections; #N/A).",
            na
        ),
        f!(
            "CUBEMEMBER",
            2,
            3,
            Cube,
            A,
            "CUBEMEMBER(connection, member_expression, [caption])",
            "Returns a member or tuple from a cube (no cube connections; #N/A).",
            na
        ),
        f!(
            "CUBEMEMBERPROPERTY",
            3,
            3,
            Cube,
            A,
            "CUBEMEMBERPROPERTY(connection, member_expression, property)",
            "Returns a property of a cube member (no cube connections; #N/A).",
            na
        ),
        f!(
            "CUBERANKEDMEMBER",
            3,
            4,
            Cube,
            A,
            "CUBERANKEDMEMBER(connection, set_expression, rank, [caption])",
            "Returns the nth member of a cube set (no cube connections; #N/A).",
            na
        ),
        f!(
            "CUBESET",
            2,
            5,
            Cube,
            A,
            "CUBESET(connection, set_expression, [caption], [sort_order], [sort_by])",
            "Defines a set of cube members (no cube connections; #N/A).",
            na
        ),
        f!("CUBESETCOUNT", 1, 1, Cube, A, "CUBESETCOUNT(set)", "Counts the items in a cube set (no cube connections; #N/A).", na),
        f!(
            "CUBEVALUE",
            1,
            VAR,
            Cube,
            A,
            "CUBEVALUE(connection, [member_expression1], ...)",
            "Returns an aggregated value from a cube (no cube connections; #N/A).",
            na
        ),
    ]
}

#[cfg(test)]
mod tests {
    use gridcraft_core::{CellError, Value};

    use crate::util::testutil::*;

    #[test]
    fn is_functions() {
        assert_eq!(ev("ISBLANK", vec![rf(Value::Empty)]), Value::Bool(true));
        assert_eq!(ev("ISBLANK", vec![t("")]), Value::Bool(false));
        assert_eq!(ev("ISERR", vec![e(CellError::NA)]), Value::Bool(false));
        assert_eq!(ev("ISERR", vec![e(CellError::Div0)]), Value::Bool(true));
        assert_eq!(ev("ISERROR", vec![e(CellError::NA)]), Value::Bool(true));
        assert_eq!(ev("ISERROR", vec![n(1.0)]), Value::Bool(false));
        assert_eq!(ev("ISNA", vec![e(CellError::NA)]), Value::Bool(true));
        assert_eq!(ev("ISNA", vec![e(CellError::Value)]), Value::Bool(false));
        assert_eq!(ev("ISNUMBER", vec![n(1.0)]), Value::Bool(true));
        assert_eq!(ev("ISNUMBER", vec![t("1")]), Value::Bool(false));
        assert_eq!(ev("ISTEXT", vec![t("a")]), Value::Bool(true));
        assert_eq!(ev("ISNONTEXT", vec![rf(Value::Empty)]), Value::Bool(true));
        assert_eq!(ev("ISNONTEXT", vec![t("a")]), Value::Bool(false));
        assert_eq!(ev("ISLOGICAL", vec![b(false)]), Value::Bool(true));
        assert_eq!(ev("ISLOGICAL", vec![n(0.0)]), Value::Bool(false));
        assert_eq!(ev("ISEVEN", vec![n(-2.5)]), Value::Bool(true));
        assert_eq!(ev("ISODD", vec![n(3.9)]), Value::Bool(true));
        assert_eq!(ev("ISODD", vec![n(-3.0)]), Value::Bool(true));
        is_err(ev("ISEVEN", vec![t("x")]), CellError::Value);
        is_err(ev("ISEVEN", vec![b(true)]), CellError::Value);
        assert_eq!(ev("ISEVEN", vec![rf(Value::Empty)]), Value::Bool(true));
        assert_eq!(rows_of(&ev("ISEVEN", vec![av(row(&[1.0, 2.0]))])), vec![vec![false.into(), true.into()]]);
        let range = arr(vec![vec![nv(1.0), Value::Empty], vec![tv("x"), Value::Empty]]);
        assert_eq!(rows_of(&ev("ISBLANK", vec![rf(range)])), vec![vec![false.into(), true.into()], vec![false.into(), true.into()]]);
    }

    #[test]
    fn type_error_n() {
        close(ev("TYPE", vec![n(1.0)]), 1.0);
        close(ev("TYPE", vec![t("a")]), 2.0);
        close(ev("TYPE", vec![b(true)]), 4.0);
        close(ev("TYPE", vec![e(CellError::Ref)]), 16.0);
        close(ev("TYPE", vec![av(row(&[1.0, 2.0]))]), 64.0);
        close(ev("TYPE", vec![rf(arr(vec![vec![tv("x")]]))]), 2.0);
        close(ev("TYPE", vec![rf(Value::Empty)]), 1.0);
        close(ev("ERROR.TYPE", vec![e(CellError::Div0)]), 2.0);
        close(ev("ERROR.TYPE", vec![e(CellError::NA)]), 7.0);
        is_err(ev("ERROR.TYPE", vec![n(1.0)]), CellError::NA);
        is_err(ev("NA", vec![]), CellError::NA);
        close(ev("N", vec![n(7.0)]), 7.0);
        close(ev("N", vec![b(true)]), 1.0);
        close(ev("N", vec![t("7")]), 0.0);
        is_err(ev("N", vec![e(CellError::Num)]), CellError::Num);
        close(ev("N", vec![rf(row(&[3.0, 4.0]))]), 3.0);
        let lifted = ev("N", vec![av(arr(vec![vec![Value::Bool(true), tv("x")], vec![Value::Error(CellError::Div0), nv(4.0)]]))]);
        assert_eq!(rows_of(&lifted), vec![vec![nv(1.0), nv(0.0)], vec![Value::Error(CellError::Div0), nv(4.0)]]);
        is_text(ev("INFO", vec![t("RECALC")]), "Automatic");
        is_err(ev("INFO", vec![t("bogus")]), CellError::Value);
    }

    #[test]
    fn logical() {
        assert_eq!(ev("AND", vec![b(true), n(1.0)]), Value::Bool(true));
        assert_eq!(ev("AND", vec![b(true), n(0.0)]), Value::Bool(false));
        assert_eq!(ev("AND", vec![rf(arr(vec![vec![b(true).value, tv("x"), Value::Empty]]))]), Value::Bool(true));
        is_err(ev("AND", vec![rf(arr(vec![vec![tv("x")]]))]), CellError::Value);
        is_err(ev("AND", vec![t("abc")]), CellError::Value);
        assert_eq!(ev("AND", vec![t("TRUE")]), Value::Bool(true));
        is_err(ev("OR", vec![b(false), e(CellError::Div0)]), CellError::Div0);
        assert_eq!(ev("OR", vec![b(false), n(2.0)]), Value::Bool(true));
        assert_eq!(ev("OR", vec![b(false), b(false)]), Value::Bool(false));
        assert_eq!(ev("XOR", vec![b(true), b(true)]), Value::Bool(false));
        assert_eq!(ev("XOR", vec![b(true), b(true), b(true)]), Value::Bool(true));
        assert_eq!(ev("NOT", vec![b(false)]), Value::Bool(true));
        assert_eq!(ev("NOT", vec![n(5.0)]), Value::Bool(false));
        is_err(ev("NOT", vec![t("x")]), CellError::Value);
        assert_eq!(ev("TRUE", vec![]), Value::Bool(true));
        assert_eq!(ev("FALSE", vec![]), Value::Bool(false));
    }

    #[test]
    fn conditionals() {
        is_text(ev("IF", vec![b(true), t("y"), t("n")]), "y");
        is_text(ev("IF", vec![n(0.0), t("y"), t("n")]), "n");
        assert_eq!(ev("IF", vec![b(false), t("y")]), Value::Bool(false));
        close(ev("IF", vec![b(true), empty()]), 0.0);
        is_err(ev("IF", vec![e(CellError::Ref), n(1.0), n(2.0)]), CellError::Ref);
        let r = ev("IF", vec![av(arr(vec![vec![b(true).value, b(false).value]])), av(row(&[1.0, 2.0])), av(row(&[3.0, 4.0]))]);
        assert_eq!(rows_of(&r), vec![vec![nv(1.0), nv(4.0)]]);
        // Scalar condition returns a whole array value.
        assert_eq!(rows_of(&ev("IF", vec![b(true), av(row(&[1.0, 2.0])), n(0.0)])), vec![vec![nv(1.0), nv(2.0)]]);
        is_text(ev("IFS", vec![b(false), t("a"), b(true), t("b")]), "b");
        is_err(ev("IFS", vec![b(false), t("a")]), CellError::NA);
        close(ev("IFERROR", vec![e(CellError::Div0), n(9.0)]), 9.0);
        close(ev("IFERROR", vec![n(3.0), n(9.0)]), 3.0);
        let r = ev("IFERROR", vec![av(arr(vec![vec![nv(1.0), Value::Error(CellError::Div0)]])), n(0.0)]);
        assert_eq!(rows_of(&r), vec![vec![nv(1.0), nv(0.0)]]);
        close(ev("IFNA", vec![e(CellError::NA), n(1.0)]), 1.0);
        is_err(ev("IFNA", vec![e(CellError::Value), n(1.0)]), CellError::Value);
        is_text(ev("SWITCH", vec![n(2.0), n(1.0), t("one"), n(2.0), t("two")]), "two");
        is_text(ev("SWITCH", vec![n(3.0), n(1.0), t("one"), t("other")]), "other");
        is_err(ev("SWITCH", vec![n(3.0), n(1.0), t("one")]), CellError::NA);
        is_text(ev("SWITCH", vec![t("B"), t("a"), t("x"), t("b"), t("y")]), "y");
        is_text(ev("CHOOSE", vec![n(2.0), t("a"), t("b"), t("c")]), "b");
        is_err(ev("CHOOSE", vec![n(4.0), t("a"), t("b"), t("c")]), CellError::Value);
        is_err(ev("CHOOSE", vec![n(0.0), t("a")]), CellError::Value);
        let r = ev("CHOOSE", vec![av(row(&[1.0, 2.0])), av(row(&[10.0, 20.0])), av(row(&[30.0, 40.0]))]);
        assert_eq!(rows_of(&r), vec![vec![nv(10.0), nv(40.0)]]);
    }

    #[test]
    fn web_and_cube() {
        is_text(ev("HYPERLINK", vec![t("http://x.com")]), "http://x.com");
        is_text(ev("HYPERLINK", vec![t("http://x.com"), t("Click")]), "Click");
        close(ev("HYPERLINK", vec![t("http://x.com"), n(5.0)]), 5.0);
        // Excel 16: without a friendly name the result is the link as text.
        is_text(ev("HYPERLINK", vec![n(1.0)]), "1");
        is_text(ev("HYPERLINK", vec![n(1.5)]), "1.5");
        is_text(ev("HYPERLINK", vec![b(true)]), "TRUE");
        is_text(ev("HYPERLINK", vec![rf(Value::Empty)]), "");
        is_err(ev("HYPERLINK", vec![e(CellError::Div0)]), CellError::Div0);
        is_text(ev("ENCODEURL", vec![t("a b&c/é")]), "a%20b%26c%2F%C3%A9");
        is_err(ev("WEBSERVICE", vec![t("http://x")]), CellError::Value);
        is_err(ev("FILTERXML", vec![t("<a/>"), t("//a")]), CellError::Value);
        is_text(
            ev("FILTERXML", vec![t("<root><item id=\"A&amp;B\">125</item></root>"), t("//item/@id")]),
            "A&B",
        );
        let xml_attrs = ev(
            "FILTERXML",
            vec![t("<root><item id=\"A&amp;B\">125</item><item id=\"C\">250</item></root>"), t("//item/@id")],
        );
        assert_eq!(rows_of(&xml_attrs), vec![vec![tv("A&B")], vec![tv("C")]]);
        let xml_nums = ev(
            "FILTERXML",
            vec![t("<root><price>120</price><price>240.5</price></root>"), t("/root/price")],
        );
        assert_eq!(rows_of(&xml_nums), vec![vec![nv(120.0)], vec![nv(240.5)]]);
        is_err(ev("CUBEVALUE", vec![t("conn")]), CellError::NA);
        is_err(ev("CUBESETCOUNT", vec![t("set")]), CellError::NA);
    }

    #[test]
    fn masusheet_extended_worksheet_functions() {
        // DETECTLANGUAGE & TRANSLATE
        is_text(ev("DETECTLANGUAGE", vec![t("こんにちは")]), "ja");
        is_text(ev("DETECTLANGUAGE", vec![t("売上合計")]), "ja");
        is_text(ev("DETECTLANGUAGE", vec![t("Quarterly Revenue")]), "en");
        is_err(ev("DETECTLANGUAGE", vec![t("   ")]), CellError::Value);

        is_text(ev("TRANSLATE", vec![t("Sales"), t("en"), t("ja")]), "売上");
        is_text(ev("TRANSLATE", vec![t("合計"), t("ja"), t("en")]), "Total");
        is_text(ev("TRANSLATE", vec![t("CustomTerm"), t("en"), t("ja")]), "CustomTerm");

        // GETPIVOTDATA (scalar & array criteria lookup including Japanese headers)
        close(ev("GETPIVOTDATA", vec![t("売上"), n(4200.0)]), 4200.0);
        let pt = arr(vec![
            vec![tv("地域"), tv("売上")],
            vec![tv("東京"), nv(1500.0)],
            vec![tv("大阪"), nv(980.0)],
        ]);
        close(ev("GETPIVOTDATA", vec![t("売上"), rf(pt.clone()), t("地域"), t("大阪")]), 980.0);
        is_err(ev("GETPIVOTDATA", vec![t("売上"), rf(pt), t("地域"), t("札幌")]), CellError::Ref);

        // IMAGE validation
        is_text(ev("IMAGE", vec![t("https://example.com/chart.png"), t("Sales Chart"), n(0.0)]), "Sales Chart");
        is_err(ev("IMAGE", vec![t(""), t("Alt")]), CellError::Value);
        is_err(ev("IMAGE", vec![t("https://example.com/a.png"), t("Alt"), n(9.0)]), CellError::Value);

        // Offline honest #N/A functions (RTD, STOCKHISTORY, COPILOT, PY)
        is_err(ev("RTD", vec![t("my.rtd"), t(""), t("topic1")]), CellError::NA);
        is_err(ev("RTD", vec![t(""), t(""), t("topic1")]), CellError::Value);
        is_err(ev("STOCKHISTORY", vec![t("MSFT"), n(45000.0), n(45010.0), n(0.0), n(1.0)]), CellError::NA);
        is_err(ev("STOCKHISTORY", vec![t("MSFT"), n(45010.0), n(45000.0)]), CellError::Value);
        is_err(ev("COPILOT", vec![t("Summarize Q1")]), CellError::NA);
        is_err(ev("COPILOT", vec![t("Summarize Q1"), e(CellError::Div0)]), CellError::Div0);
        is_err(ev("PY", vec![t("sum([1, 2, 3])")]), CellError::NA);
        is_err(ev("PY", vec![t("   ")]), CellError::Value);
    }
}
