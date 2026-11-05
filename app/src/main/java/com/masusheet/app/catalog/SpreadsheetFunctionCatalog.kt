package com.masusheet.app.catalog

import com.masusheet.app.R
import java.net.URL
import java.util.Date

/**
 * ============================================================================
 * [EN] 523 Worksheet Function Catalog, Signatures, Bilingual Docs, & Auto-Complete
 * Provides [SpreadsheetFunctionEntry] and [SpreadsheetFunctionCatalog] covering 523 spreadsheet functions across 14 categories with syntax templates, bilingual descriptions, and live prefix matching.
 *
 * ---
 * [JA / 日本語解説]
 * 全14カテゴリ・523種類のワークシート関数について、構文テンプレート・日英解説・数式入力中のプレフィックス補完候補を提供する [SpreadsheetFunctionCatalog] モジュールです。
 * ============================================================================
 */

// ============================================================================
// 2. ALL 523 SPREADSHEET WORKSHEET FUNCTIONS (100% PARITY CATALOG)
// ============================================================================

/**
 * [EN] Metadata for a single worksheet function including category, syntax signature, and bilingual summary.
 * [JA] ワークシート関数1件の関数名・カテゴリ・構文・日英説明を保持するエントリです。
 */
data class SpreadsheetFunctionEntry(
    val name: String,
    val categoryJa: String,
    val syntax: String,
    val descriptionJa: String,
    val syntaxEn: String = syntax,
    val descriptionEn: String = descriptionJa
)

/**
 * [EN] Complete 523-function catalog supporting formula bar prefix auto-complete and the `fx` Function Wizard.
 * [JA] 全523関数の検索・カテゴリ絞り込み・数式オートコンプリート候補抽出を提供する関数カタログです。
 */
object SpreadsheetFunctionCatalog {
    val CATEGORIES_JA: List<String> = listOf(
        "すべて",
        "よく使う関数",
        "数学/三角",
        "統計",
        "検索/行列",
        "論理",
        "文字列操作",
        "日付/時刻",
        "財務",
        "情報",
        "データベース",
        "エンジニアリング",
        "キューブ",
        "Web",
        "AI・動的配列・互換性"
    )

    val POPULAR_FUNCTION_NAMES: Set<String> = setOf(
        "SUM", "AVERAGE", "COUNT", "COUNTA", "MAX", "MIN", "IF", "IFS", "IFERROR",
        "VLOOKUP", "XLOOKUP", "INDEX", "MATCH", "SUMIF", "SUMIFS", "COUNTIF", "COUNTIFS",
        "AVERAGEIF", "ROUND", "ROUNDUP", "ROUNDDOWN", "SUBTOTAL", "TODAY", "NOW", "DATEDIF",
        "TEXT", "LEFT", "RIGHT", "MID", "LEN", "CONCAT", "TEXTJOIN", "PMT", "FILTER", "SORT", "UNIQUE"
    )

    // Complete 523 Spreadsheet Worksheet Function Names
    val ALL_523_FUNCTION_NAMES: List<String> = listOf(
        // Math & Trig (81)
        "ABS", "ACOS", "ACOSH", "ACOT", "ACOTH", "AGGREGATE", "ARABIC", "ASIN", "ASINH", "ATAN",
        "ATAN2", "ATANH", "BASE", "CEILING", "CEILING.MATH", "CEILING.PRECISE", "COMBIN", "COMBINA",
        "COS", "COSH", "COT", "COTH", "CSC", "CSCH", "DECIMAL", "DEGREES", "EVEN", "EXP", "FACT",
        "FACTDOUBLE", "FLOOR", "FLOOR.MATH", "FLOOR.PRECISE", "GCD", "INT", "ISO.CEILING", "LCM",
        "LN", "LOG", "LOG10", "MDETERM", "MINVERSE", "MMULT", "MOD", "MROUND", "MULTINOMIAL",
        "MUNIT", "ODD", "PI", "POWER", "PRODUCT", "QUOTIENT", "RADIANS", "RAND", "RANDARRAY",
        "RANDBETWEEN", "ROMAN", "ROUND", "ROUNDDOWN", "ROUNDUP", "SEC", "SECH", "SERIESSUM",
        "SEQUENCE", "SIGN", "SIN", "SINH", "SQRT", "SQRTPI", "SUBTOTAL", "SUM", "SUMIF", "SUMIFS",
        "SUMPRODUCT", "SUMSQ", "SUMX2MY2", "SUMX2PY2", "SUMXMY2", "TAN", "TANH", "TRUNC",
        // Statistical & Forecasting (114)
        "AVEDEV", "AVERAGE", "AVERAGEA", "AVERAGEIF", "AVERAGEIFS", "BETA.DIST", "BETA.INV",
        "BINOM.DIST", "BINOM.DIST.RANGE", "BINOM.INV", "CHISQ.DIST", "CHISQ.DIST.RT", "CHISQ.INV",
        "CHISQ.INV.RT", "CHISQ.TEST", "CONFIDENCE.NORM", "CONFIDENCE.T", "CORREL", "COUNT",
        "COUNTA", "COUNTBLANK", "COUNTIF", "COUNTIFS", "COVARIANCE.P", "COVARIANCE.S", "DEVSQ",
        "EXPON.DIST", "F.DIST", "F.DIST.RT", "F.INV", "F.INV.RT", "F.TEST", "FISHER", "FISHERINV",
        "FORECAST", "FORECAST.ETS", "FORECAST.ETS.CONFINT", "FORECAST.ETS.SEASONALITY",
        "FORECAST.ETS.STAT", "FORECAST.LINEAR", "FREQUENCY", "GAMMA", "GAMMA.DIST", "GAMMA.INV",
        "GAMMALN", "GAMMALN.PRECISE", "GAUSS", "GEOMEAN", "GROWTH", "HARMEAN", "HYPGEOM.DIST",
        "INTERCEPT", "KURT", "LARGE", "LINEST", "LOGEST", "LOGNORM.DIST", "LOGNORM.INV", "MAX",
        "MAXA", "MAXIFS", "MEDIAN", "MIN", "MINA", "MINIFS", "MODE.MULT", "MODE.SNGL",
        "NEGBINOM.DIST", "NORM.DIST", "NORM.INV", "NORM.S.DIST", "NORM.S.INV", "PEARSON",
        "PERCENTILE.EXC", "PERCENTILE.INC", "PERCENTRANK.EXC", "PERCENTRANK.INC", "PERMUT",
        "PERMUTATIONA", "PHI", "POISSON.DIST", "PROB", "QUARTILE.EXC", "QUARTILE.INC", "RANK.AVG",
        "RANK.EQ", "RSQ", "SKEW", "SKEW.P", "SLOPE", "SMALL", "STANDARDIZE", "STDEV.P", "STDEV.S",
        "STDEVA", "STDEVPA", "STEYX", "T.DIST", "T.DIST.2T", "T.DIST.RT", "T.INV", "T.INV.2T",
        "T.TEST", "TREND", "TRIMMEAN", "VAR.P", "VAR.S", "VARA", "VARPA", "WEIBULL.DIST", "Z.TEST",
        // Compatibility Statistical (39)
        "BETADIST", "BETAINV", "BINOMDIST", "CHIDIST", "CHIINV", "CHITEST", "CONFIDENCE", "COVAR",
        "CRITBINOM", "EXPONDIST", "FDIST", "FINV", "FTEST", "GAMMADIST", "GAMMAINV", "HYPGEOMDIST",
        "LOGINV", "LOGNORMDIST", "MODE", "NEGBINOMDIST", "NORMDIST", "NORMINV", "NORMSDIST",
        "NORMSINV", "PERCENTILE", "PERCENTRANK", "POISSON", "QUARTILE", "RANK", "STDEV", "STDEVP",
        "TDIST", "TINV", "TTEST", "VAR", "VARP", "WEIBULL", "ZTEST",
        // Lookup, Reference & Dynamic Array (44)
        "ADDRESS", "AREAS", "BYCOL", "BYROW", "CHOOSE", "CHOOSECOLS", "CHOOSEROWS", "COLUMN",
        "COLUMNS", "DROP", "EXPAND", "FILTER", "FORMULATEXT", "GETPIVOTDATA", "GROUPBY", "HLOOKUP",
        "HSTACK", "HYPERLINK", "IMAGE", "INDEX", "INDIRECT", "ISOMITTED", "LAMBDA", "LET",
        "LOOKUP", "MAKEARRAY", "MAP", "MATCH", "OFFSET", "PERCENTOF", "PIVOTBY", "REDUCE", "ROW",
        "ROWS", "RTD", "SCAN", "SINGLE", "SORT", "SORTBY", "TAKE", "TOCOL", "TOROW", "TRANSPOSE",
        "UNIQUE", "VLOOKUP", "VSTACK", "WRAPCOLS", "WRAPROWS", "XLOOKUP", "XMATCH",
        // Logical (11)
        "AND", "FALSE", "IF", "IFERROR", "IFNA", "IFS", "NOT", "OR", "SWITCH", "TRUE", "XOR",
        // Text & Regex (42)
        "ARRAYTOTEXT", "ASC", "BAHTTEXT", "CHAR", "CLEAN", "CODE", "CONCAT", "CONCATENATE", "DBCS",
        "DOLLAR", "EXACT", "FIND", "FINDB", "FIXED", "JIS", "LEFT", "LEFTB", "LEN", "LENB",
        "LOWER", "MID", "MIDB", "NUMBERVALUE", "PHONETIC", "PROPER", "REGEXEXTRACT", "REGEXREPLACE",
        "REGEXTEST", "REPLACE", "REPLACEB", "REPT", "RIGHT", "RIGHTB", "SEARCH", "SEARCHB",
        "SUBSTITUTE", "T", "TEXT", "TEXTAFTER", "TEXTBEFORE", "TEXTJOIN", "TEXTSPLIT", "TRIM",
        "UNICHAR", "UNICODE", "UPPER", "VALUE", "VALUETOTEXT",
        // Date & Time (25)
        "DATE", "DATEDIF", "DATEVALUE", "DAY", "DAYS", "DAYS360", "EDATE", "EOMONTH", "HOUR",
        "ISOWEEKNUM", "MINUTE", "MONTH", "NETWORKDAYS", "NETWORKDAYS.INTL", "NOW", "SECOND",
        "TIME", "TIMEVALUE", "TODAY", "WEEKDAY", "WEEKNUM", "WORKDAY", "WORKDAY.INTL", "YEAR",
        "YEARFRAC",
        // Financial (56)
        "ACCRINT", "ACCRINTM", "AMORDEGRC", "AMORLINC", "COUPDAYBS", "COUPDAYS", "COUPDAYSNC",
        "COUPNCD", "COUPNUM", "COUPPCD", "CUMIPMT", "CUMPRINC", "DB", "DDB", "DISC", "DOLLARDE",
        "DOLLARFR", "DURATION", "EFFECT", "EUROCONVERT", "FV", "FVSCHEDULE", "INTRATE", "IPMT",
        "IRR", "ISPMT", "MDURATION", "MIRR", "NOMINAL", "NPER", "NPV", "ODDFPRICE", "ODDFYIELD",
        "ODDLPRICE", "ODDLYIELD", "PDURATION", "PMT", "PPMT", "PRICE", "PRICEDISC", "PRICEMAT",
        "PV", "RATE", "RECEIVED", "RRI", "SLN", "SYD", "TBILLEQ", "TBILLPRICE", "TBILLYIELD",
        "VDB", "XIRR", "XNPV", "YIELD", "YIELDDISC", "YIELDMAT", "STOCKHISTORY",
        // Information (20)
        "CELL", "ERROR.TYPE", "INFO", "ISBLANK", "ISERR", "ISERROR", "ISEVEN", "ISFORMULA",
        "ISLOGICAL", "ISNA", "ISNONTEXT", "ISNUMBER", "ISODD", "ISREF", "ISTEXT", "N", "NA",
        "SHEET", "SHEETS", "TYPE",
        // Database (12)
        "DAVERAGE", "DCOUNT", "DCOUNTA", "DGET", "DMAX", "DMIN", "DPRODUCT", "DSTDEV", "DSTDEVP",
        "DSUM", "DVAR", "DVARP",
        // Engineering (54)
        "BESSELI", "BESSELJ", "BESSELK", "BESSELY", "BIN2DEC", "BIN2HEX", "BIN2OCT", "BITAND",
        "BITLSHIFT", "BITOR", "BITRSHIFT", "BITXOR", "COMPLEX", "CONVERT", "DEC2BIN", "DEC2HEX",
        "DEC2OCT", "DELTA", "ERF", "ERF.PRECISE", "ERFC", "ERFC.PRECISE", "GESTEP", "HEX2BIN",
        "HEX2DEC", "HEX2OCT", "IMABS", "IMAGINARY", "IMARGUMENT", "IMCONJUGATE", "IMCOS", "IMCOSH",
        "IMCOT", "IMCSC", "IMCSCH", "IMDIV", "IMEXP", "IMLN", "IMLOG10", "IMLOG2", "IMPOWER",
        "IMPRODUCT", "IMREAL", "IMSEC", "IMSECH", "IMSIN", "IMSINH", "IMSQRT", "IMSUB", "IMSUM",
        "IMTAN", "OCT2BIN", "OCT2DEC", "OCT2HEX",
        // Cube (7)
        "CUBEKPIMEMBER", "CUBEMEMBER", "CUBEMEMBERPROPERTY", "CUBERANKEDMEMBER", "CUBESET",
        "CUBESETCOUNT", "CUBEVALUE",
        // Web, Translation & AI / External (12)
        "ENCODEURL", "FILTERXML", "WEBSERVICE", "DETECTLANGUAGE", "TRANSLATE", "COPILOT", "PY",
        "CALL", "REGISTER.ID"
    ).distinct()

    // Specific (Syntax, DescriptionJa) for all 523 Spreadsheet Worksheet Functions
    private val functionSpecs: Map<String, Pair<String, String>> = mapOf(
        // --- Math & Trig (81) ---
        "ABS" to ("ABS(数値)" to "数値の絶対値（符号を除いた正の数）を返します。"),
        "ACOS" to ("ACOS(数値)" to "数値のアークコサイン（逆余弦）をラジアン（0〜π）で返します。"),
        "ACOSH" to ("ACOSH(数値)" to "数値の双曲線逆余弦（ハイパボリック アークコサイン）を返します。"),
        "ACOT" to ("ACOT(数値)" to "数値のアークコタンジェント（逆余接）の主値をラジアンで返します。"),
        "ACOTH" to ("ACOTH(数値)" to "数値の双曲線逆余接（ハイパボリック アークコタンジェント）を返します。"),
        "AGGREGATE" to ("AGGREGATE(集計方法, オプション, 参照1, ...)" to "非表示行やエラー値を無視するオプション付きでリストやデータベースを集計します。"),
        "ARABIC" to ("ARABIC(ローマ数字文字列)" to "ローマ数字の文字列をアラビア数字（通常の数値）に変換します。"),
        "ASIN" to ("ASIN(数値)" to "数値のアークサイン（逆正弦）をラジアン（-π/2〜π/2）で返します。"),
        "ASINH" to ("ASINH(数値)" to "数値の双曲線逆正弦（ハイパボリック アークサイン）を返します。"),
        "ATAN" to ("ATAN(数値)" to "数値のアークタンジェント（逆正接）をラジアン（-π/2〜π/2）で返します。"),
        "ATAN2" to ("ATAN2(x座標, y座標)" to "指定された x 座標と y 座標からアークタンジェント（角度）をラジアンで返します。"),
        "ATANH" to ("ATANH(数値)" to "数値の双曲線逆正接（ハイパボリック アークタンジェント）を返します。"),
        "BASE" to ("BASE(数値, 基数, [最小長])" to "10進数の数値を指定した基数（2〜36進数）の文字列に変換します。"),
        "CEILING" to ("CEILING(数値, 基準値)" to "数値を0から遠い方向へ、指定した基準値の最も近い倍数に切り上げます。"),
        "CEILING.MATH" to ("CEILING.MATH(数値, [基準値], [モード])" to "数値を最も近い整数、または指定した基準値の倍数に切り上げます。"),
        "CEILING.PRECISE" to ("CEILING.PRECISE(数値, [基準値])" to "数値の符号にかかわらず、最も近い整数または基準値の倍数に切り上げます。"),
        "COMBIN" to ("COMBIN(総数, 抜き取り数)" to "指定された個数の対象から抜き取る組み合わせの総数（nCr）を返します。"),
        "COMBINA" to ("COMBINA(総数, 抜き取り数)" to "重複を許して抜き取る組み合わせの総数（重複組合せ）を返します。"),
        "COS" to ("COS(角度)" to "指定された角度（ラジアン）のコサイン（余弦）を返します。"),
        "COSH" to ("COSH(数値)" to "数値の双曲線余弦（ハイパボリック コサイン）を返します。"),
        "COT" to ("COT(角度)" to "指定された角度（ラジアン）のコタンジェント（余接）を返します。"),
        "COTH" to ("COTH(数値)" to "数値の双曲線余接（ハイパボリック コタンジェント）を返します。"),
        "CSC" to ("CSC(角度)" to "指定された角度（ラジアン）のコセカント（余割）を返します。"),
        "CSCH" to ("CSCH(数値)" to "数値の双曲線余割（ハイパボリック コセカント）を返します。"),
        "DECIMAL" to ("DECIMAL(文字列, 基数)" to "指定した基数（進数）で表現された数値テキストを10進数に変換します。"),
        "DEGREES" to ("DEGREES(角度)" to "ラジアン単位の角度を度（°）に変換します。"),
        "EVEN" to ("EVEN(数値)" to "数値を0から遠い方向へ切り上げて、最も近い偶数の整数を返します。"),
        "EXP" to ("EXP(数値)" to "自然対数の底 e を指定した数値でべき乗した値（e^x）を返します。"),
        "FACT" to ("FACT(数値)" to "数値の階乗（1×2×...×n）を返します。"),
        "FACTDOUBLE" to ("FACTDOUBLE(数値)" to "数値の二重階乗（n!!）を返します。"),
        "FLOOR" to ("FLOOR(数値, 基準値)" to "数値を0に近い方向へ、指定した基準値の最も近い倍数に切り捨てます。"),
        "FLOOR.MATH" to ("FLOOR.MATH(数値, [基準値], [モード])" to "数値を最も近い整数、または指定した基準値の倍数に切り捨てます。"),
        "FLOOR.PRECISE" to ("FLOOR.PRECISE(数値, [基準値])" to "数値の符号にかかわらず、最も近い整数または基準値の倍数に切り捨てます。"),
        "GCD" to ("GCD(数値1, [数値2], ...)" to "2つ以上の整数の最大公約数を返します。"),
        "INT" to ("INT(数値)" to "数値を元の値以下の最も近い整数に切り捨てます。"),
        "ISO.CEILING" to ("ISO.CEILING(数値, [基準値])" to "数値の符号にかかわらず、切り上げ方向の最も近い整数または基準値の倍数を返します。"),
        "LCM" to ("LCM(数値1, [数値2], ...)" to "整数の最小公倍数を返します。"),
        "LN" to ("LN(数値)" to "数値の自然対数（底 e）を返します。"),
        "LOG" to ("LOG(数値, [底])" to "指定された底に対する数値の対数を返します（底を省略すると 10）。"),
        "LOG10" to ("LOG10(数値)" to "数値の常用対数（底 10）を返します。"),
        "MDETERM" to ("MDETERM(配列)" to "正方行列の行列式（ディターミナント）を返します。"),
        "MINVERSE" to ("MINVERSE(配列)" to "正方行列の逆行列を返します。"),
        "MMULT" to ("MMULT(配列1, 配列2)" to "2つの配列（行列）の行列積を返します。"),
        "MOD" to ("MOD(数値, 除数)" to "数値を除数で割ったときの剰余（余り）を返します。"),
        "MROUND" to ("MROUND(数値, 倍数)" to "指定された倍数のうち最も近い値に数値を丸めます。"),
        "MULTINOMIAL" to ("MULTINOMIAL(数値1, [数値2], ...)" to "指定された数値の和の階乗を各数値の階乗の積で割った多項係数を返します。"),
        "MUNIT" to ("MUNIT(次元)" to "指定された次元（サイズ）の単位行列を返します。"),
        "ODD" to ("ODD(数値)" to "数値を0から遠い方向へ切り上げて、最も近い奇数の整数を返します。"),
        "PI" to ("PI()" to "円周率 π（約 3.14159265358979）を返します。"),
        "POWER" to ("POWER(数値, 指数)" to "数値を指定した指数でべき乗した結果（数値^指数）を返します。"),
        "PRODUCT" to ("PRODUCT(数値1, [数値2], ...)" to "引数として指定されたすべての数値を掛け合わせた積を返します。"),
        "QUOTIENT" to ("QUOTIENT(分子, 分母)" to "除算の商の整数部のみを返します（余りは切り捨て）。"),
        "RADIANS" to ("RADIANS(角度)" to "度（°）単位の角度をラジアンに変換します。"),
        "RAND" to ("RAND()" to "0 以上 1 未満の一様分布乱数（実数）を返します。"),
        "RANDARRAY" to ("RANDARRAY([行], [列], [最小], [最大], [整数])" to "指定した行数・列数の乱数配列を動的に生成します。"),
        "RANDBETWEEN" to ("RANDBETWEEN(最小値, 最大値)" to "指定した最小値から最大値までの範囲で整数の乱数を返します。"),
        "ROMAN" to ("ROMAN(数値, [書式])" to "アラビア数字を文字列としてのローマ数字に変換します。"),
        "ROUND" to ("ROUND(数値, 桁数)" to "数値を指定した桁数に四捨五入します。"),
        "ROUNDDOWN" to ("ROUNDDOWN(数値, 桁数)" to "数値を0に近い方向に指定した桁数で切り捨てます。"),
        "ROUNDUP" to ("ROUNDUP(数値, 桁数)" to "数値を0から遠い方向に指定した桁数で切り上げます。"),
        "SEC" to ("SEC(角度)" to "指定された角度（ラジアン）のセカント（正割）を返します。"),
        "SECH" to ("SECH(数値)" to "数値の双曲線正割（ハイパボリック セカント）を返します。"),
        "SERIESSUM" to ("SERIESSUM(x, n, m, 係数)" to "べき級数の和を計算して返します。"),
        "SEQUENCE" to ("SEQUENCE(行, [列], [開始], [増分])" to "1、2、3、4 などの連続した数値の配列を生成します。"),
        "SIGN" to ("SIGN(数値)" to "数値の正負の符号を返します（正なら 1、0 なら 0、負なら -1）。"),
        "SIN" to ("SIN(角度)" to "指定された角度（ラジアン）のサイン（正弦）を返します。"),
        "SINH" to ("SINH(数値)" to "数値の双曲線正弦（ハイパボリック サイン）を返します。"),
        "SQRT" to ("SQRT(数値)" to "数値の正の平方根（ルート）を返します。"),
        "SQRTPI" to ("SQRTPI(数値)" to "(数値 × π) の正の平方根を返します。"),
        "SUBTOTAL" to ("SUBTOTAL(集計方法, 参照1, ...)" to "リストまたはデータベースの集計値（フィルター結果対応）を返します。"),
        "SUM" to ("SUM(数値1, [数値2], ...)" to "指定したセル範囲内のすべての数値を合計します。"),
        "SUMIF" to ("SUMIF(範囲, 検索条件, [合計範囲])" to "指定した検索条件に一致するセルの値を合計します。"),
        "SUMIFS" to ("SUMIFS(合計対象範囲, 条件範囲1, 条件1, ...)" to "複数の検索条件に一致するすべてのセルの値を合計します。"),
        "SUMPRODUCT" to ("SUMPRODUCT(配列1, [配列2], ...)" to "対応する配列要素の積を合計します。"),
        "SUMSQ" to ("SUMSQ(数値1, [数値2], ...)" to "引数の2乗（平方）の合計を返します。"),
        "SUMX2MY2" to ("SUMX2MY2(配列X, 配列Y)" to "2つの配列で対応する値の平方差（x^2 - y^2）の合計を返します。"),
        "SUMX2PY2" to ("SUMX2PY2(配列X, 配列Y)" to "2つの配列で対応する値の平方和（x^2 + y^2）の合計を返します。"),
        "SUMXMY2" to ("SUMXMY2(配列X, 配列Y)" to "2つの配列で対応する値の差の平方（(x - y)^2）の合計を返します。"),
        "TAN" to ("TAN(角度)" to "指定された角度（ラジアン）のタンジェント（正接）を返します。"),
        "TANH" to ("TANH(数値)" to "数値の双曲線正接（ハイパボリック タンジェント）を返します。"),
        "TRUNC" to ("TRUNC(数値, [桁数])" to "数値の小数部を指定した桁数で切り捨てて整数または指定桁にします。"),

        // --- Statistical & Forecasting (114) ---
        "AVEDEV" to ("AVEDEV(数値1, [数値2], ...)" to "データとその平均値との絶対偏差の平均を返します。"),
        "AVERAGE" to ("AVERAGE(数値1, [数値2], ...)" to "引数の算術平均（平均値）を返します。"),
        "AVERAGEA" to ("AVERAGEA(値1, [値2], ...)" to "文字列や論理値（TRUE=1, FALSE=0）も含めて引数の平均値を計算します。"),
        "AVERAGEIF" to ("AVERAGEIF(範囲, 検索条件, [平均対象範囲])" to "指定した条件に一致するセルの平均値を返します。"),
        "AVERAGEIFS" to ("AVERAGEIFS(平均対象範囲, 条件範囲1, 条件1, ...)" to "複数の条件すべてに一致するセルの平均値を返します。"),
        "BETA.DIST" to ("BETA.DIST(x, α, β, 関数形式, [A], [B])" to "ベータ分布の確率密度関数または累積分布関数の値を返します。"),
        "BETA.INV" to ("BETA.INV(確率, α, β, [A], [B])" to "指定されたベータ分布の累積分布関数の逆関数値を返します。"),
        "BINOM.DIST" to ("BINOM.DIST(成功数, 試行回数, 成功率, 関数形式)" to "二項分布の確率または累積確率を返します。"),
        "BINOM.DIST.RANGE" to ("BINOM.DIST.RANGE(試行回数, 成功率, 最小成功数, [最大成功数])" to "二項分布を用いて試行結果が指定範囲内に入る確率を返します。"),
        "BINOM.INV" to ("BINOM.INV(試行回数, 成功率, 基準α)" to "累積二項分布が基準値以上になる最小の整数を返します。"),
        "CHISQ.DIST" to ("CHISQ.DIST(x, 自由度, 関数形式)" to "カイ2乗分布の左側確率（確率密度または累積分布）を返します。"),
        "CHISQ.DIST.RT" to ("CHISQ.DIST.RT(x, 自由度)" to "カイ2乗分布の右側確率（上側確率）を返します。"),
        "CHISQ.INV" to ("CHISQ.INV(確率, 自由度)" to "カイ2乗分布の左側確率の逆関数値を返します。"),
        "CHISQ.INV.RT" to ("CHISQ.INV.RT(確率, 自由度)" to "カイ2乗分布の右側確率の逆関数値を返します。"),
        "CHISQ.TEST" to ("CHISQ.TEST(実測値範囲, 期待値範囲)" to "カイ2乗検定を行い、独立性の検定結果（p値）を返します。"),
        "CONFIDENCE.NORM" to ("CONFIDENCE.NORM(α, 標準偏差, 標本数)" to "正規分布を使用して母集団の平均に対する信頼区間を返します。"),
        "CONFIDENCE.T" to ("CONFIDENCE.T(α, 標準偏差, 標本数)" to "スチューデントの t 分布を使用して母集団の平均に対する信頼区間を返します。"),
        "CORREL" to ("CORREL(配列1, 配列2)" to "2つのデータ範囲間の相関係数（-1〜1）を返します。"),
        "COUNT" to ("COUNT(値1, [値2], ...)" to "数値が含まれるセルの個数を数えます。"),
        "COUNTA" to ("COUNTA(値1, [値2], ...)" to "空白でないセルの個数を数えます。"),
        "COUNTBLANK" to ("COUNTBLANK(範囲)" to "指定された範囲内にある空白セルの個数を数えます。"),
        "COUNTIF" to ("COUNTIF(範囲, 検索条件)" to "指定した検索条件に一致するセルの個数を数えます。"),
        "COUNTIFS" to ("COUNTIFS(条件範囲1, 条件1, ...)" to "複数の検索条件に一致するセルの個数を数えます。"),
        "COVARIANCE.P" to ("COVARIANCE.P(配列1, 配列2)" to "母集団全体の共分散（偏差の積の平均）を返します。"),
        "COVARIANCE.S" to ("COVARIANCE.S(配列1, 配列2)" to "標本共分散（2つのデータ組の偏差の積の標本平均）を返します。"),
        "DEVSQ" to ("DEVSQ(数値1, [数値2], ...)" to "データの平均値からの偏差の2乗和（平方和）を返します。"),
        "EXPON.DIST" to ("EXPON.DIST(x, λ, 関数形式)" to "指数分布の確率密度関数または累積分布関数の値を返します。"),
        "F.DIST" to ("F.DIST(x, 自由度1, 自由度2, 関数形式)" to "F 確率分布（左側）の確率密度または累積分布を返します。"),
        "F.DIST.RT" to ("F.DIST.RT(x, 自由度1, 自由度2)" to "F 確率分布の右側確率（上側確率）を返します。"),
        "F.INV" to ("F.INV(確率, 自由度1, 自由度2)" to "F 確率分布（左側）の逆関数値を返します。"),
        "F.INV.RT" to ("F.INV.RT(確率, 自由度1, 自由度2)" to "F 確率分布（右側）の逆関数値を返します。"),
        "F.TEST" to ("F.TEST(配列1, 配列2)" to "2つの配列の分散に有意差がないかどうかを調べる F 検定の両側確率を返します。"),
        "FISHER" to ("FISHER(x)" to "点 x におけるフィッシャー変換の値を返します。"),
        "FISHERINV" to ("FISHERINV(y)" to "フィッシャー変換の逆関数値を返します。"),
        "FORECAST" to ("FORECAST(x, 既知のy, 既知のx)" to "既存の値から線形回帰を使って将来の値を予測します。"),
        "FORECAST.ETS" to ("FORECAST.ETS(目標日, 値, タイムライン, [季節性], [補完])" to "指数平滑法（ETS）アルゴリズムを使用して時系列データの将来値を予測します。"),
        "FORECAST.ETS.CONFINT" to ("FORECAST.ETS.CONFINT(目標日, 値, タイムライン, [信頼水準])" to "指数平滑法による予測値の信頼区間を返します。"),
        "FORECAST.ETS.SEASONALITY" to ("FORECAST.ETS.SEASONALITY(値, タイムライン, [補完])" to "時系列データに対して検出された季節サイクル（周期）の長さを返します。"),
        "FORECAST.ETS.STAT" to ("FORECAST.ETS.STAT(値, タイムライン, 統計種別)" to "時系列予測の統計値（α、β、γ、MASE、SMAPE、MAE、RMSE）を返します。"),
        "FORECAST.LINEAR" to ("FORECAST.LINEAR(x, 既知のy, 既知のx)" to "線形回帰を使用して既存の値から将来の値を予測します。"),
        "FREQUENCY" to ("FREQUENCY(データ配列, 区間配列)" to "値の範囲内にデータがいくつあるかを表す度数分布を縦方向の配列として返します。"),
        "GAMMA" to ("GAMMA(数値)" to "ガンマ関数 Γ(x) の値を返します。"),
        "GAMMA.DIST" to ("GAMMA.DIST(x, α, β, 関数形式)" to "ガンマ分布の確率密度または累積分布関数の値を返します。"),
        "GAMMA.INV" to ("GAMMA.INV(確率, α, β)" to "ガンマ分布の累積分布関数の逆関数値を返します。"),
        "GAMMALN" to ("GAMMALN(x)" to "ガンマ関数 Γ(x) の自然対数を返します。"),
        "GAMMALN.PRECISE" to ("GAMMALN.PRECISE(x)" to "ガンマ関数 Γ(x) の高精度な自然対数を返します。"),
        "GAUSS" to ("GAUSS(z)" to "標準正規分布の累積分布関数から 0.5 を引いた確率（0 から z までの確率）を返します。"),
        "GEOMEAN" to ("GEOMEAN(数値1, [数値2], ...)" to "正の数データの幾何平均（相乗平均）を返します。"),
        "GROWTH" to ("GROWTH(既知のy, [既知のx], [新しいx], [定数])" to "指数曲線（y = b*m^x）に当てはめて予測値を返します。"),
        "HARMEAN" to ("HARMEAN(数値1, [数値2], ...)" to "正の数データの調和平均を返します。"),
        "HYPGEOM.DIST" to ("HYPGEOM.DIST(標本の成功数, 標本数, 母集団の成功数, 母集団数, 関数形式)" to "超幾何分布の確率または累積確率を返します。"),
        "INTERCEPT" to ("INTERCEPT(既知のy, 既知のx)" to "回帰直線が y 軸と交わる点（切片）の座標を返します。"),
        "KURT" to ("KURT(数値1, [数値2], ...)" to "データセットの尖度（分布の鋭さ・裾の重さ）を返します。"),
        "LARGE" to ("LARGE(配列, 順位)" to "データ範囲の中で k 番目に大きい値を返します。"),
        "LINEST" to ("LINEST(既知のy, [既知のx], [定数], [補正])" to "最小二乗法によって回帰直線のパラメータ配列を計算します。"),
        "LOGEST" to ("LOGEST(既知のy, [既知のx], [定数], [補正])" to "指数回帰曲線のパラメータ配列を計算します。"),
        "LOGNORM.DIST" to ("LOGNORM.DIST(x, 平均, 標準偏差, 関数形式)" to "対数正規分布の確率密度または累積分布関数の値を返します。"),
        "LOGNORM.INV" to ("LOGNORM.INV(確率, 平均, 標準偏差)" to "対数正規分布の累積分布関数の逆関数値を返します。"),
        "MAX" to ("MAX(数値1, [数値2], ...)" to "引数リストに含まれる最大値を返します。"),
        "MAXA" to ("MAXA(値1, [値2], ...)" to "論理値や文字列も含めて引数リスト内の最大値を返します。"),
        "MAXIFS" to ("MAXIFS(最大対象範囲, 条件範囲1, 条件1, ...)" to "指定した複数の条件に一致するセルの中の最大値を返します。"),
        "MEDIAN" to ("MEDIAN(数値1, [数値2], ...)" to "引数に含まれる数値の中央値（メジアン）を返します。"),
        "MIN" to ("MIN(数値1, [数値2], ...)" to "引数リストに含まれる最小値を返します。"),
        "MINA" to ("MINA(値1, [値2], ...)" to "論理値や文字列も含めて引数リスト内の最小値を返します。"),
        "MINIFS" to ("MINIFS(最小対象範囲, 条件範囲1, 条件1, ...)" to "指定した複数の条件に一致するセルの中の最小値を返します。"),
        "MODE.MULT" to ("MODE.MULT(数値1, [数値2], ...)" to "データ内で最も頻繁に出現する値（最頻値）の縦方向配列を返します。"),
        "MODE.SNGL" to ("MODE.SNGL(数値1, [数値2], ...)" to "データ範囲内で最も頻繁に出現する値（最頻値）を1つ返します。"),
        "NEGBINOM.DIST" to ("NEGBINOM.DIST(失敗数, 成功数, 成功率, 関数形式)" to "負の二項分布の確率または累積確率を返します。"),
        "NORM.DIST" to ("NORM.DIST(x, 平均, 標準偏差, 関数形式)" to "指定した平均と標準偏差に対する正規分布の値を返します。"),
        "NORM.INV" to ("NORM.INV(確率, 平均, 標準偏差)" to "正規分布の累積分布関数の逆関数値を返します。"),
        "NORM.S.DIST" to ("NORM.S.DIST(z, 関数形式)" to "標準正規分布（平均 0、標準偏差 1）の確率密度または累積分布を返します。"),
        "NORM.S.INV" to ("NORM.S.INV(確率)" to "標準正規分布の累積分布関数の逆関数値（z値）を返します。"),
        "PEARSON" to ("PEARSON(配列1, 配列2)" to "ピアソンの積率相関係数 r を返します。"),
        "PERCENTILE.EXC" to ("PERCENTILE.EXC(配列, 率)" to "範囲内のデータの第 k 百分位数（0 と 1 を除く排他的範囲）を返します。"),
        "PERCENTILE.INC" to ("PERCENTILE.INC(配列, 率)" to "範囲内のデータの第 k 百分位数（0〜1 の包括的範囲）を返します。"),
        "PERCENTRANK.EXC" to ("PERCENTRANK.EXC(配列, x, [有効桁数])" to "データ範囲内での値の順位を百分率（0 と 1 を除く）で返します。"),
        "PERCENTRANK.INC" to ("PERCENTRANK.INC(配列, x, [有効桁数])" to "データ範囲内での値の順位を百分率（0〜1）で返します。"),
        "PERMUT" to ("PERMUT(総数, 抜き取り数)" to "指定した個数の対象から抜き取る順列の総数（nPr）を返します。"),
        "PERMUTATIONA" to ("PERMUTATIONA(総数, 抜き取り数)" to "重複を許して抜き取る順列の総数（重複順列）を返します。"),
        "PHI" to ("PHI(x)" to "標準正規分布の確率密度関数 φ(x) の値を返します。"),
        "POISSON.DIST" to ("POISSON.DIST(x, 平均, 関数形式)" to "ポアソン分布の確率または累積確率を返します。"),
        "PROB" to ("PROB(x範囲, 確率範囲, 下限, [上限])" to "範囲内の値が指定した下限と上限の間に入る確率を返します。"),
        "QUARTILE.EXC" to ("QUARTILE.EXC(配列, 戻り値)" to "データ配列の四分位数（0% と 100% を除く 1〜3）を返します。"),
        "QUARTILE.INC" to ("QUARTILE.INC(配列, 戻り値)" to "データ配列の四分位数（0:最小、1:第1四分位、2:中央値、3:第3四分位、4:最大）を返します。"),
        "RANK.AVG" to ("RANK.AVG(数値, 参照, [順序])" to "数値のリスト内での順位を返します（同順位がある場合は平均順位を返します）。"),
        "RANK.EQ" to ("RANK.EQ(数値, 参照, [順序])" to "数値のリスト内での順位を返します（同順位がある場合は最上位の順位を返します）。"),
        "RSQ" to ("RSQ(既知のy, 既知のx)" to "ピアソンの積率相関係数の2乗（決定係数 R^2）を返します。"),
        "SKEW" to ("SKEW(数値1, [数値2], ...)" to "分布の非対称度を表す歪度（標本歪度）を返します。"),
        "SKEW.P" to ("SKEW.P(数値1, [数値2], ...)" to "母集団全体に基づく分布の歪度を返します。"),
        "SLOPE" to ("SLOPE(既知のy, 既知のx)" to "線形回帰直線の傾きを返します。"),
        "SMALL" to ("SMALL(配列, 順位)" to "データ範囲の中で k 番目に小さい値を返します。"),
        "STANDARDIZE" to ("STANDARDIZE(x, 平均, 標準偏差)" to "平均と標準偏差に基づく正規化値（標準化変量 z スコア）を返します。"),
        "STDEV.P" to ("STDEV.P(数値1, [数値2], ...)" to "引数を母集団全体とみなして標準偏差を計算します。"),
        "STDEV.S" to ("STDEV.S(数値1, [数値2], ...)" to "引数を標本とみなして不偏標準偏差を計算します。"),
        "STDEVA" to ("STDEVA(値1, [値2], ...)" to "文字列や論理値も含めて標本の標準偏差を計算します。"),
        "STDEVPA" to ("STDEVPA(値1, [値2], ...)" to "文字列や論理値も含めて母集団全体の標準偏差を計算します。"),
        "STEYX" to ("STEYX(既知のy, 既知のx)" to "回帰直線による予測値 y の標準誤差を返します。"),
        "T.DIST" to ("T.DIST(x, 自由度, 関数形式)" to "スチューデントの左側 t 分布を返します。"),
        "T.DIST.2T" to ("T.DIST.2T(x, 自由度)" to "スチューデントの両側 t 分布の確率を返します。"),
        "T.DIST.RT" to ("T.DIST.RT(x, 自由度)" to "スチューデントの右側 t 分布の確率を返します。"),
        "T.INV" to ("T.INV(確率, 自由度)" to "スチューデントの左側 t 分布の逆関数値を返します。"),
        "T.INV.2T" to ("T.INV.2T(確率, 自由度)" to "スチューデントの両側 t 分布の逆関数値を返します。"),
        "T.TEST" to ("T.TEST(配列1, 配列2, 尾数, 検定種別)" to "スチューデントの t 検定に関連する確率（p値）を返します。"),
        "TREND" to ("TREND(既知のy, [既知のx], [新しいx], [定数])" to "最小二乗法による回帰直線上の予測値配列を返します。"),
        "TRIMMEAN" to ("TRIMMEAN(配列, 割合)" to "データの上限と下限から一定割合を除外した内部平均（調整平均）を返します。"),
        "VAR.P" to ("VAR.P(数値1, [数値2], ...)" to "引数を母集団全体とみなして分散（母分散）を計算します。"),
        "VAR.S" to ("VAR.S(数値1, [数値2], ...)" to "引数を標本とみなして不偏分散（標本分散）を計算します。"),
        "VARA" to ("VARA(値1, [値2], ...)" to "文字列や論理値も含めて標本の分散を計算します。"),
        "VARPA" to ("VARPA(値1, [値2], ...)" to "文字列や論理値も含めて母集団全体の分散を計算します。"),
        "WEIBULL.DIST" to ("WEIBULL.DIST(x, α, β, 関数形式)" to "ワイブル分布の確率密度または累積分布を返します。"),
        "Z.TEST" to ("Z.TEST(配列, x, [σ])" to "z 検定の片側確率値を返します。"),

        // --- Compatibility Statistical (39) ---
        "BETADIST" to ("BETADIST(x, α, β, [A], [B])" to "ベータ累積分布関数の値を返します（互換性関数）。"),
        "BETAINV" to ("BETAINV(確率, α, β, [A], [B])" to "ベータ累積分布関数の逆関数値を返します（互換性関数）。"),
        "BINOMDIST" to ("BINOMDIST(成功数, 試行回数, 成功率, 関数形式)" to "二項分布の確率を返します（互換性関数）。"),
        "CHIDIST" to ("CHIDIST(x, 自由度)" to "カイ2乗分布の片側確率を返します（互換性関数）。"),
        "CHIINV" to ("CHIINV(確率, 自由度)" to "カイ2乗分布の片側確率の逆関数値を返します（互換性関数）。"),
        "CHITEST" to ("CHITEST(実測値範囲, 期待値範囲)" to "カイ2乗独立性検定の確率値を返します（互換性関数）。"),
        "CONFIDENCE" to ("CONFIDENCE(α, 標準偏差, 標本数)" to "母集団の平均に対する信頼区間を返します（互換性関数）。"),
        "COVAR" to ("COVAR(配列1, 配列2)" to "2つのデータ組の母共分散を返します（互換性関数）。"),
        "CRITBINOM" to ("CRITBINOM(試行回数, 成功率, 基準α)" to "累積二項分布が基準値以上になる最小の整数を返します（互換性関数）。"),
        "EXPONDIST" to ("EXPONDIST(x, λ, 関数形式)" to "指数分布の値を返します（互換性関数）。"),
        "FDIST" to ("FDIST(x, 自由度1, 自由度2)" to "F 確率分布（右側）の値を返します（互換性関数）。"),
        "FINV" to ("FINV(確率, 自由度1, 自由度2)" to "F 確率分布（右側）の逆関数値を返します（互換性関数）。"),
        "FTEST" to ("FTEST(配列1, 配列2)" to "F 検定の結果（両側確率）を返します（互換性関数）。"),
        "GAMMADIST" to ("GAMMADIST(x, α, β, 関数形式)" to "ガンマ分布の値を返します（互換性関数）。"),
        "GAMMAINV" to ("GAMMAINV(確率, α, β)" to "ガンマ累積分布関数の逆関数値を返します（互換性関数）。"),
        "HYPGEOMDIST" to ("HYPGEOMDIST(標本の成功数, 標本数, 母集団の成功数, 母集団数)" to "超幾何分布の確率を返します（互換性関数）。"),
        "LOGINV" to ("LOGINV(確率, 平均, 標準偏差)" to "対数正規累積分布の逆関数値を返します（互換性関数）。"),
        "LOGNORMDIST" to ("LOGNORMDIST(x, 平均, 標準偏差)" to "対数正規累積分布関数の値を返します（互換性関数）。"),
        "MODE" to ("MODE(数値1, [数値2], ...)" to "データ内で最も頻繁に出現する最頻値を返します（互換性関数）。"),
        "NEGBINOMDIST" to ("NEGBINOMDIST(失敗数, 成功数, 成功率)" to "負の二項分布の確率を返します（互換性関数）。"),
        "NORMDIST" to ("NORMDIST(x, 平均, 標準偏差, 関数形式)" to "正規分布の確率密度または累積分布を返します（互換性関数）。"),
        "NORMINV" to ("NORMINV(確率, 平均, 標準偏差)" to "正規累積分布の逆関数値を返します（互換性関数）。"),
        "NORMSDIST" to ("NORMSDIST(z)" to "標準正規累積分布関数の値を返します（互換性関数）。"),
        "NORMSINV" to ("NORMSINV(確率)" to "標準正規累積分布の逆関数値を返します（互換性関数）。"),
        "PERCENTILE" to ("PERCENTILE(配列, 率)" to "範囲内のデータの第 k 百分位数を返します（互換性関数）。"),
        "PERCENTRANK" to ("PERCENTRANK(配列, x, [有効桁数])" to "データ範囲内での値の順位を百分率で返します（互換性関数）。"),
        "POISSON" to ("POISSON(x, 平均, 関数形式)" to "ポアソン分布の確率を返します（互換性関数）。"),
        "QUARTILE" to ("QUARTILE(配列, 戻り値)" to "データ範囲の四分位数を返します（互換性関数）。"),
        "RANK" to ("RANK(数値, 参照, [順序])" to "数値のリスト内での順位を返します（互換性関数）。"),
        "STDEV" to ("STDEV(数値1, [数値2], ...)" to "標本に基づく標準偏差を計算します（互換性関数）。"),
        "STDEVP" to ("STDEVP(数値1, [数値2], ...)" to "母集団全体に基づく標準偏差を計算します（互換性関数）。"),
        "TDIST" to ("TDIST(x, 自由度, 尾数)" to "スチューデントの t 分布の確率を返します（互換性関数）。"),
        "TINV" to ("TINV(確率, 自由度)" to "スチューデントの両側 t 分布の逆関数値を返します（互換性関数）。"),
        "TTEST" to ("TTEST(配列1, 配列2, 尾数, 検定種別)" to "スチューデントの t 検定の確率を返します（互換性関数）。"),
        "VAR" to ("VAR(数値1, [数値2], ...)" to "標本に基づく不偏分散を計算します（互換性関数）。"),
        "VARP" to ("VARP(数値1, [数値2], ...)" to "母集団全体に基づく母分散を計算します（互換性関数）。"),
        "WEIBULL" to ("WEIBULL(x, α, β, 関数形式)" to "ワイブル分布の値を返します（互換性関数）。"),
        "ZTEST" to ("ZTEST(配列, x, [σ])" to "z 検定の片側確率値を返します（互換性関数）。"),

        // --- Lookup, Reference & Dynamic Array (50) ---
        "ADDRESS" to ("ADDRESS(行番号, 列番号, [参照の型], [A1形式], [シート名])" to "指定した行番号と列番号からセル参照文字列（\"\$A\$1\" など）を作成します。"),
        "AREAS" to ("AREAS(参照)" to "参照内の領域（連続したセル範囲または単一セル）の個数を返します。"),
        "BYCOL" to ("BYCOL(配列, LAMBDA関数)" to "配列の各列に LAMBDA 関数を適用し、結果の1行配列を返します。"),
        "BYROW" to ("BYROW(配列, LAMBDA関数)" to "配列の各行に LAMBDA 関数を適用し、結果の1列配列を返します。"),
        "CHOOSE" to ("CHOOSE(インデックス, 値1, [値2], ...)" to "インデックス番号に基づいて引数リストから対応する値を選択して返します。"),
        "CHOOSECOLS" to ("CHOOSECOLS(配列, 列番号1, [列番号2], ...)" to "配列または範囲から指定した列番号の列だけを抽出して新しい配列を返します。"),
        "CHOOSEROWS" to ("CHOOSEROWS(配列, 行番号1, [行番号2], ...)" to "配列または範囲から指定した行番号の行だけを抽出して新しい配列を返します。"),
        "COLUMN" to ("COLUMN([参照])" to "指定したセル参照の列番号（A列=1, B列=2...）を返します。"),
        "COLUMNS" to ("COLUMNS(配列)" to "セル範囲または配列に含まれる列数を返します。"),
        "DROP" to ("DROP(配列, 行数, [列数])" to "配列の先頭または末尾から指定した行数・列数を除外した残りの配列を返します。"),
        "EXPAND" to ("EXPAND(配列, 行数, [列数], [パディング値])" to "配列を指定した行数と列数のサイズに拡張し、余白を指定値で埋めます。"),
        "FILTER" to ("FILTER(配列, 含む, [空の場合])" to "定義した条件に基づいてデータ範囲をフィルター処理します。"),
        "FORMULATEXT" to ("FORMULATEXT(参照)" to "指定したセルに入力されている数式をそのまま文字列として返します。"),
        "GETPIVOTDATA" to ("GETPIVOTDATA(データフィールド, ピボットテーブル, [フィールド1, アイテム1], ...)" to "ピボットテーブルに格納されている集計データを抽出します。"),
        "GROUPBY" to ("GROUPBY(行フィールド, 値, 集計関数)" to "指定した行フィールドでデータをグループ化して集計します。"),
        "HLOOKUP" to ("HLOOKUP(検索値, 範囲, 行番号, [検索方法])" to "表の上端行で特定の値を検索し、同じ列の指定した行にある値を返します。"),
        "HSTACK" to ("HSTACK(配列1, [配列2], ...)" to "複数の配列を横方向（列方向）に連結して1つの配列にします。"),
        "HYPERLINK" to ("HYPERLINK(リンク先, [別名])" to "ネットワークやWebページ、別シートへジャンプするハイパーリンクを作成します。"),
        "IMAGE" to ("IMAGE(ソース, [代替テキスト], [サイズ])" to "指定したソースURLまたはパスからセル内に画像を挿入します。"),
        "INDEX" to ("INDEX(配列, 行番号, [列番号])" to "テーブルまたは範囲内の指定した行と列が交差する位置にある値を返します。"),
        "INDIRECT" to ("INDIRECT(参照文字列, [A1形式])" to "文字列で指定されたセル参照を評価し、その参照先の値を返します。"),
        "ISOMITTED" to ("ISOMITTED(引数)" to "LAMBDA 関数内の省略可能な引数が省略されているかどうかを判定し TRUE/FALSE を返します。"),
        "LAMBDA" to ("LAMBDA([引数1, ...], 計算式)" to "再利用可能なカスタム関数（無名関数）を作成します。"),
        "LET" to ("LET(名前1, 値1, [名前2, 値2, ...], 計算式)" to "計算結果に変数名を割り当て、数式内で中間値を再利用できるようにします。"),
        "LOOKUP" to ("LOOKUP(検索値, 検査範囲, [対応範囲])" to "1行または1列の範囲、あるいは配列から値を検索します。"),
        "MAKEARRAY" to ("MAKEARRAY(行数, 列数, LAMBDA関数)" to "LAMBDA 関数を適用して、指定された行数と列数の計算配列を生成します。"),
        "MAP" to ("MAP(配列1, ..., LAMBDA関数)" to "配列の各要素に LAMBDA 関数を適用してマッピングした新しい配列を返します。"),
        "MATCH" to ("MATCH(検索値, 検査範囲, [照合の型])" to "セル範囲内で指定した項目を検索し、その相対的な位置（何番目か）を返します。"),
        "OFFSET" to ("OFFSET(参照, 行数, 列数, [高さ], [幅])" to "基準のセルまたは範囲から指定した行数・列数だけシフトした範囲への参照を返します。"),
        "PERCENTOF" to ("PERCENTOF(データ部分集合, データ全体)" to "データ全体に対するサブセットの合計の割合（構成比）を計算します。"),
        "PIVOTBY" to ("PIVOTBY(行フィールド, 列フィールド, 値, 集計関数)" to "行と列の両方でクロス集計（動的ピボット）を行います。"),
        "REDUCE" to ("REDUCE([初期値], 配列, LAMBDA関数)" to "配列の各要素に LAMBDA 関数を累積適用し、単一の集計結果に畳み込みます。"),
        "ROW" to ("ROW([参照])" to "指定したセル参照の行番号を返します。"),
        "ROWS" to ("ROWS(配列)" to "セル範囲または配列に含まれる行数を返します。"),
        "RTD" to ("RTD(ProgID, サーバー, トピック1, ...)" to "COM オートメーション対応プログラムからリアルタイムデータを取得します。"),
        "SCAN" to ("SCAN([初期値], 配列, LAMBDA関数)" to "配列の各要素に LAMBDA 関数を適用し、すべての中間累積値の配列を返します。"),
        "SINGLE" to ("SINGLE(値)" to "共通部分参照（暗黙的な交差 @）を使用して単一の値を返します。"),
        "SORT" to ("SORT(配列, [並べ替えインデックス], [並べ替え順序], [列方向])" to "範囲または配列の内容を昇順・降順に並べ替えます。"),
        "SORTBY" to ("SORTBY(配列, 基準配列1, [順序1], ...)" to "対応する別の範囲または配列の値に基づいて内容を並べ替えます。"),
        "TAKE" to ("TAKE(配列, 行数, [列数])" to "配列の先頭または末尾から指定した行数・列数の連続要素を取得します。"),
        "TOCOL" to ("TOCOL(配列, [無視オプション], [列方向スキャン])" to "2次元の配列やセル範囲を単一の1列（縦方向ベクトル）に変換します。"),
        "TOROW" to ("TOROW(配列, [無視オプション], [列方向スキャン])" to "2次元の配列やセル範囲を単一の1行（横方向ベクトル）に変換します。"),
        "TRANSPOSE" to ("TRANSPOSE(配列)" to "セル範囲または配列の行と列を入れ替えます（転置）。"),
        "UNIQUE" to ("UNIQUE(配列, [列方向], [回数指定])" to "リストまたは範囲から重複を除いた一意の値の一覧を返します。"),
        "VLOOKUP" to ("VLOOKUP(検索値, 範囲, 列番号, [検索方法])" to "範囲の左端列で値を検索し、同じ行の指定した列の値を返します。"),
        "VSTACK" to ("VSTACK(配列1, [配列2], ...)" to "複数の配列を縦方向（行方向）に連結して1つの配列にします。"),
        "WRAPCOLS" to ("WRAPCOLS(ベクトル, 折り返し数, [パディング値])" to "1次元の行または列ベクトルを指定した要素数ごとに列方向へ折り返して2次元配列にします。"),
        "WRAPROWS" to ("WRAPROWS(ベクトル, 折り返し数, [パディング値])" to "1次元の行または列ベクトルを指定した要素数ごとに行方向へ折り返して2次元配列にします。"),
        "XLOOKUP" to ("XLOOKUP(検索値, 検索範囲, 戻り配列, [見つからない場合], [一致モード], [検索モード])" to "範囲または配列を検索し、最初に見つかった一致に対応する項目を返します。"),
        "XMATCH" to ("XMATCH(検索値, 検索範囲, [一致モード], [検索モード])" to "配列またはセル範囲内で指定した項目を検索し、その相対位置を返します。"),

        // --- Logical (11) ---
        "AND" to ("AND(論理式1, [論理式2], ...)" to "すべての引数が TRUE のときに TRUE を返し、1つでも FALSE があれば FALSE を返します。"),
        "FALSE" to ("FALSE()" to "論理値 FALSE（偽）を返します。"),
        "IF" to ("IF(論理式, 値が真の場合, [値が偽の場合])" to "条件が真か偽かを判定し、それぞれに対応する値を返します。"),
        "IFERROR" to ("IFERROR(値, エラーの場合の値)" to "数式がエラーと評価された場合に指定した代替値を返し、それ以外は数式の結果を返します。"),
        "IFNA" to ("IFNA(値, NAの場合の値)" to "数式が #N/A エラーになった場合に指定した代替値を返し、それ以外は結果を返します。"),
        "IFS" to ("IFS(論理式1, 値1, [論理式2, 値2], ...)" to "複数の条件を順番に評価し、最初に真となった条件に対応する値を返します。"),
        "NOT" to ("NOT(論理式)" to "引数の論理値を反転します（TRUE なら FALSE、FALSE なら TRUE）。"),
        "OR" to ("OR(論理式1, [論理式2], ...)" to "いずれか1つ以上の引数が TRUE であれば TRUE を返します。"),
        "SWITCH" to ("SWITCH(式, 値1, 結果1, [デフォルトまたは値2, 結果2], ...)" to "式の値を値のリストと比較し、最初に一致した値に対応する結果を返します。"),
        "TRUE" to ("TRUE()" to "論理値 TRUE（真）を返します。"),
        "XOR" to ("XOR(論理式1, [論理式2], ...)" to "すべての引数の排他的論理和（TRUE の個数が奇数のとき TRUE）を返します。"),

        // --- Text & Regex (48) ---
        "ARRAYTOTEXT" to ("ARRAYTOTEXT(配列, [書式])" to "指定した範囲内の配列値をテキスト文字列に変換して返します。"),
        "ASC" to ("ASC(文字列)" to "全角（2バイト）の英数カナ文字を半角（1バイト）の文字に変換します。"),
        "BAHTTEXT" to ("BAHTTEXT(数値)" to "数値をタイ語（バーツ通貨表記）の文字列に変換します。"),
        "CHAR" to ("CHAR(数値)" to "文字コード番号（1〜255）に対応する文字を返します。"),
        "CLEAN" to ("CLEAN(文字列)" to "文字列から印刷できない制御文字をすべて削除します。"),
        "CODE" to ("CODE(文字列)" to "文字列の先頭文字の数値文字コード（ASCII / ANSI）を返します。"),
        "CONCAT" to ("CONCAT(文字列1, [文字列2], ...)" to "複数の範囲および文字列のテキストを結合します。"),
        "CONCATENATE" to ("CONCATENATE(文字列1, [文字列2], ...)" to "複数のテキスト文字列を1つの文字列に結合します。"),
        "DBCS" to ("DBCS(文字列)" to "半角（1バイト）の英数カナ文字を全角（2バイト）の文字に変換します。"),
        "DOLLAR" to ("DOLLAR(数値, [桁数])" to "数値を四捨五入し、通貨書式（\$ 記号と桁区切り付き）の文字列に変換します。"),
        "EXACT" to ("EXACT(文字列1, 文字列2)" to "2つの文字列が大文字・小文字も含めて完全に一致するかどうかを比較し TRUE/FALSE を返します。"),
        "FIND" to ("FIND(検索文字列, 対象, [開始位置])" to "ある文字列が別の文字列内で最初に現れる位置を返します（大文字・小文字を区別）。"),
        "FINDB" to ("FINDB(検索文字列, 対象, [開始位置])" to "バイト数単位で検索文字列が最初に現れる位置を返します。"),
        "FIXED" to ("FIXED(数値, [桁数], [カンマなし])" to "数値を指定された小数点以下の桁数に丸め、ピリオドとカンマを使った文字列に変換します。"),
        "JIS" to ("JIS(文字列)" to "半角（1バイト）の英数カタカナを全角（2バイト）の文字に変換します。"),
        "LEFT" to ("LEFT(文字列, [文字数])" to "文字列の先頭（左端）から指定した文字数の文字を抽出します。"),
        "LEFTB" to ("LEFTB(文字列, [バイト数])" to "文字列の先頭から指定したバイト数の文字を抽出します。"),
        "LEN" to ("LEN(文字列)" to "文字列に含まれる文字数を返します。"),
        "LENB" to ("LENB(文字列)" to "文字列を表すために使用されるバイト数を返します。"),
        "LOWER" to ("LOWER(文字列)" to "文字列に含まれる英字をすべて小文字に変換します。"),
        "MID" to ("MID(文字列, 開始位置, 文字数)" to "文字列の指定した開始位置から指定した文字数の文字を抽出します。"),
        "MIDB" to ("MIDB(文字列, 開始位置, バイト数)" to "文字列の指定した開始位置から指定したバイト数の文字を抽出します。"),
        "NUMBERVALUE" to ("NUMBERVALUE(文字列, [小数点記号], [桁区切り記号])" to "ロケールに依存しない方法で小数点・桁区切りを指定して文字列を数値に変換します。"),
        "PHONETIC" to ("PHONETIC(参照)" to "文字列からふりがな（フリガナ情報）を抽出します。"),
        "PROPER" to ("PROPER(文字列)" to "文字列内の各英単語の先頭文字を大文字に、それ以降を小文字に変換します。"),
        "REGEXEXTRACT" to ("REGEXEXTRACT(文字列, 正規表現, [戻りモード])" to "正規表現パターンに一致する部分文字列を抽出します。"),
        "REGEXREPLACE" to ("REGEXREPLACE(文字列, 正規表現, 置換文字列)" to "正規表現パターンに一致する部分を新しい文字列に置換します。"),
        "REGEXTEST" to ("REGEXTEST(文字列, 正規表現)" to "文字列の一部が正規表現パターンに一致するかどうかを判定します。"),
        "REPLACE" to ("REPLACE(文字列, 開始位置, 文字数, 置換文字列)" to "文字列の指定した位置から指定文字数分を新しい文字列に置き換えます。"),
        "REPLACEB" to ("REPLACEB(文字列, 開始位置, バイト数, 置換文字列)" to "バイト数に基づいて文字列の一部を別の文字列に置き換えます。"),
        "REPT" to ("REPT(文字列, 繰り返し回数)" to "文字列を指定した回数だけ繰り返して表示します。"),
        "RIGHT" to ("RIGHT(文字列, [文字数])" to "文字列の末尾（右端）から指定した文字数の文字を抽出します。"),
        "RIGHTB" to ("RIGHTB(文字列, [バイト数])" to "文字列の末尾から指定したバイト数の文字を抽出します。"),
        "SEARCH" to ("SEARCH(検索文字列, 対象, [開始位置])" to "指定した文字列が最初に現れる位置を返します（大文字・小文字を区別せずワイルドカード使用可）。"),
        "SEARCHB" to ("SEARCHB(検索文字列, 対象, [開始位置])" to "バイト数単位で検索文字列が現れる位置を返します（大文字・小文字を区別しない）。"),
        "SUBSTITUTE" to ("SUBSTITUTE(文字列, 検索文字列, 置換文字列, [置換対象])" to "文字列中の指定した文字を新しい文字に置き換えます。"),
        "T" to ("T(値)" to "引数が文字列であればその文字列を返し、それ以外は空白文字列（\"\"）を返します。"),
        "TEXT" to ("TEXT(値, 表示形式)" to "数値を指定した表示形式（\"#,##0\" や \"yyyy/mm/dd\" など）の文字列に変換します。"),
        "TEXTAFTER" to ("TEXTAFTER(文字列, 区切り記号, [インスタンス番号])" to "指定した区切り文字または部分文字列の後ろにあるテキストを抽出します。"),
        "TEXTBEFORE" to ("TEXTBEFORE(文字列, 区切り記号, [インスタンス番号])" to "指定した区切り文字または部分文字列の前にあるテキストを抽出します。"),
        "TEXTJOIN" to ("TEXTJOIN(区切り記号, 空のセルを無視, 文字列1, ...)" to "複数の範囲や文字列を指定した区切り記号で結合します。"),
        "TEXTSPLIT" to ("TEXTSPLIT(文字列, 列区切り記号, [行区切り記号])" to "区切り記号を使用してテキスト文字列を行と列に分割します。"),
        "TRIM" to ("TRIM(文字列)" to "文字列の先頭・末尾の空白を削除し、単語間の連続する空白を1つにまとめます。"),
        "UNICHAR" to ("UNICHAR(数値)" to "指定された Unicode コードポイント番号に対応する文字を返します。"),
        "UNICODE" to ("UNICODE(文字列)" to "文字列の先頭文字に対応する Unicode コードポイント番号を返します。"),
        "UPPER" to ("UPPER(文字列)" to "文字列に含まれる英字をすべて大文字に変換します。"),
        "VALUE" to ("VALUE(文字列)" to "数値を表す文字列を実際の数値データに変換します。"),
        "VALUETOTEXT" to ("VALUETOTEXT(値, [書式])" to "任意の指定した値をテキスト文字列に変換して返します。"),

        // --- Date & Time (25) ---
        "DATE" to ("DATE(年, 月, 日)" to "指定した年・月・日を表す日付シリアル値を返します。"),
        "DATEDIF" to ("DATEDIF(開始日, 終了日, 単位)" to "2つの日付間の満年数（\"Y\"）、満月数（\"M\"）、または日数（\"D\"）を計算します。"),
        "DATEVALUE" to ("DATEVALUE(日付文字列)" to "文字列として表された日付をスプレッドシートの日付シリアル値に変換します。"),
        "DAY" to ("DAY(シリアル値)" to "日付シリアル値から「日」（1〜31 の整数）を取り出します。"),
        "DAYS" to ("DAYS(終了日, 開始日)" to "2つの日付間の日数を返します。"),
        "DAYS360" to ("DAYS360(開始日, 終了日, [方式])" to "1年を360日（30日×12か月）とする会計基準に基づいて2つの日付間の日数を計算します。"),
        "EDATE" to ("EDATE(開始日, 月数)" to "開始日から指定した月数だけ前または後の同じ日の日付シリアル値を返します。"),
        "EOMONTH" to ("EOMONTH(開始日, 月数)" to "開始日から指定した月数だけ前または後の月の最終日（月末日）を返します。"),
        "HOUR" to ("HOUR(シリアル値)" to "時刻シリアル値から「時」（0〜23 の整数）を取り出します。"),
        "ISOWEEKNUM" to ("ISOWEEKNUM(日付)" to "指定された日付がその年の第何週目にあたるかを ISO 週番号（1〜53）で返します。"),
        "MINUTE" to ("MINUTE(シリアル値)" to "時刻シリアル値から「分」（0〜59 の整数）を取り出します。"),
        "MONTH" to ("MONTH(シリアル値)" to "日付シリアル値から「月」（1〜12 の整数）を取り出します。"),
        "NETWORKDAYS" to ("NETWORKDAYS(開始日, 終了日, [祭日])" to "土日および指定した祭日を除いた2つの日付間の稼働日数を返します。"),
        "NETWORKDAYS.INTL" to ("NETWORKDAYS.INTL(開始日, 終了日, [週末], [祭日])" to "カスタムの週末曜日と祭日を指定して、2つの日付間の稼働日数を返します。"),
        "NOW" to ("NOW()" to "現在の日付と時刻のシリアル値を返します。"),
        "SECOND" to ("SECOND(シリアル値)" to "時刻シリアル値から「秒」（0〜59 の整数）を取り出します。"),
        "TIME" to ("TIME(時, 分, 秒)" to "指定した時・分・秒を表す時刻シリアル値（0〜0.999988426）を返します。"),
        "TIMEVALUE" to ("TIMEVALUE(時刻文字列)" to "文字列として表された時刻を時刻シリアル値に変換します。"),
        "TODAY" to ("TODAY()" to "現在の日付のシリアル値を返します。"),
        "WEEKDAY" to ("WEEKDAY(シリアル値, [種類])" to "日付に対応する曜日番号（1〜7）を返します。"),
        "WEEKNUM" to ("WEEKNUM(シリアル値, [週の基準])" to "その年の第何週目にあたるかを表す週番号を返します。"),
        "WORKDAY" to ("WORKDAY(開始日, 日数, [祭日])" to "土日と指定した祭日を除いて、開始日から指定した稼働日数だけ前または後の日付を返します。"),
        "WORKDAY.INTL" to ("WORKDAY.INTL(開始日, 日数, [週末], [祭日])" to "カスタムの週末曜日と祭日を除いて、指定した稼働日数だけ前または後の日付を返します。"),
        "YEAR" to ("YEAR(シリアル値)" to "日付シリアル値から「年」（1900〜9999 の整数）を取り出します。"),
        "YEARFRAC" to ("YEARFRAC(開始日, 終了日, [基準])" to "開始日から終了日までの期間が1年に対して占める割合（年換算の端数）を返します。"),

        // --- Financial (57) ---
        "ACCRINT" to ("ACCRINT(発行日, 初回利払日, 受渡日, 利率, 額面, 頻度, [基準])" to "定期的に利子が支払われる証券の経過利子（未収利息）を返します。"),
        "ACCRINTM" to ("ACCRINTM(発行日, 受渡日, 利率, 額面, [基準])" to "満期日に一括して利子が支払われる証券の経過利子を返します。"),
        "AMORDEGRC" to ("AMORDEGRC(取得価額, 購入日, 初回期末, 残存価額, 期間, 率, [基準])" to "フランス会計基準に基づく各会計期間の減価償却費（減価償却係数適用）を返します。"),
        "AMORLINC" to ("AMORLINC(取得価額, 購入日, 初回期末, 残存価額, 期間, 率, [基準])" to "フランス会計基準に基づく各会計期間の定額減価償却費を返します。"),
        "COUPDAYBS" to ("COUPDAYBS(受渡日, 満期日, 頻度, [基準])" to "利払期間の第1日目から受渡日までの日数を返します。"),
        "COUPDAYS" to ("COUPDAYS(受渡日, 満期日, 頻度, [基準])" to "受渡日を含む利払期間の日数を返します。"),
        "COUPDAYSNC" to ("COUPDAYSNC(受渡日, 満期日, 頻度, [基準])" to "受渡日から次の利払日までの日数を返します。"),
        "COUPNCD" to ("COUPNCD(受渡日, 満期日, 頻度, [基準])" to "受渡日後の次の利払日を表す日付を返します。"),
        "COUPNUM" to ("COUPNUM(受渡日, 満期日, 頻度, [基準])" to "受渡日と満期日の間に支払われる利子の支払回数を返します。"),
        "COUPPCD" to ("COUPPCD(受渡日, 満期日, 頻度, [基準])" to "受渡日前の直前の利払日を表す日付を返します。"),
        "CUMIPMT" to ("CUMIPMT(利率, 期間, 現在価値, 開始期, 終了期, 支払期日)" to "開始期から終了期までの間に支払われるローンの累計利息額を返します。"),
        "CUMPRINC" to ("CUMPRINC(利率, 期間, 現在価値, 開始期, 終了期, 支払期日)" to "開始期から終了期までの間に返済されるローンの累計元金額を返します。"),
        "DB" to ("DB(取得価額, 残存価額, 耐用年数, 期間, [月数])" to "定率法（固定率法）を使用して指定期間における資産の減価償却費を返します。"),
        "DDB" to ("DDB(取得価額, 残存価額, 耐用年数, 期間, [倍率])" to "倍額定率法または指定した償却率を使用して資産の減価償却費を返します。"),
        "DISC" to ("DISC(受渡日, 満期日, 価格, 償還価額, [基準])" to "証券の割引率を計算して返します。"),
        "DOLLARDE" to ("DOLLARDE(分数価格, 単位)" to "分数で表された価格（例: 1.02 = 1と2/16）を小数で表された価格に変換します。"),
        "DOLLARFR" to ("DOLLARFR(小数価格, 単位)" to "小数で表された価格を分数で表された証券価格表記に変換します。"),
        "DURATION" to ("DURATION(受渡日, 満期日, クーポンレート, 利回り, 頻度, [基準])" to "定期的に利子を支払う証券のマコーレー・デュレーション（加重平均残存期間）を返します。"),
        "EFFECT" to ("EFFECT(名目利率, 複利計算期間)" to "名目年利率と年間複利計算回数から実効年利率を計算します。"),
        "EUROCONVERT" to ("EUROCONVERT(数値, 変換元通貨, 変換先通貨, [完全精度], [有効桁数])" to "ユーロ通貨とEU加盟国旧通貨の間で固定換算レートによる通貨変換を行います。"),
        "FV" to ("FV(利率, 期間, 定期支払額, [現在価値], [支払期日])" to "一定の利率と定期支払額に基づいて投資の将来価値（満期受取額）を計算します。"),
        "FVSCHEDULE" to ("FVSCHEDULE(元金, 利率配列)" to "一連の変動金利（複利）を適用した後の初期元金の将来価値を計算します。"),
        "INTRATE" to ("INTRATE(受渡日, 満期日, 投資額, 償還価額, [基準])" to "全額投資された割引証券の利率を計算します。"),
        "IPMT" to ("IPMT(利率, 期, 期間, 現在価値, [将来価値], [支払期日])" to "定額返済ローンや投資において、指定した期に支払われる利息分の金額を返します。"),
        "IRR" to ("IRR(範囲, [推定値])" to "一連の定期的なキャッシュフローに対する内部利益率（IRR）を返します。"),
        "ISPMT" to ("ISPMT(利率, 期, 期間, 現在価値)" to "元金均等返済の投資・ローンにおいて、指定した期に支払われる利息額を計算します。"),
        "MDURATION" to ("MDURATION(受渡日, 満期日, クーポンレート, 利回り, 頻度, [基準])" to "額面 \$100 の証券に対する修正マコーレー・デュレーションを返します。"),
        "MIRR" to ("MIRR(範囲, 安全利率, 再投資利率)" to "資金調達コストと再投資利回りを考慮した修正内部利益率（MIRR）を返します。"),
        "NOMINAL" to ("NOMINAL(実効利率, 複利計算期間)" to "実効年利率と年間複利計算回数から名目年利率を計算します。"),
        "NPER" to ("NPER(利率, 定期支払額, 現在価値, [将来価値], [支払期日])" to "定額の支払と一定の利率に基づき、ローン返済や投資に必要な期間（総支払回数）を返します。"),
        "NPV" to ("NPV(割引率, 値1, [値2], ...)" to "割引率と一連の将来の支払および収益から投資の正味現在価値（NPV）を算出します。"),
        "ODDFPRICE" to ("ODDFPRICE(受渡日, 満期日, 発行日, 初回利払日, 利率, 利回り, 償還価額, 頻度, [基準])" to "最初の利払期間が半端（長短）な証券の額面 \$100 あたりの価格を返します。"),
        "ODDFYIELD" to ("ODDFYIELD(受渡日, 満期日, 発行日, 初回利払日, 利率, 価格, 償還価額, 頻度, [基準])" to "最初の利払期間が半端な証券の利回りを返します。"),
        "ODDLPRICE" to ("ODDLPRICE(受渡日, 満期日, 最終利払日, 利率, 利回り, 償還価額, 頻度, [基準])" to "最後の利払期間が半端な証券の額面 \$100 あたりの価格を返します。"),
        "ODDLYIELD" to ("ODDLYIELD(受渡日, 満期日, 最終利払日, 利率, 価格, 償還価額, 頻度, [基準])" to "最後の利払期間が半端な証券の利回りを返します。"),
        "PDURATION" to ("PDURATION(利率, 現在価値, 将来価値)" to "投資が指定した将来価値に達するまでに必要な期間を返します。"),
        "PMT" to ("PMT(利率, 期間, 現在価値, [将来価値], [支払期日])" to "定額の支払と一定の利率に基づいてローンの定期支払額（元利均等返済額）を計算します。"),
        "PPMT" to ("PPMT(利率, 期, 期間, 現在価値, [将来価値], [支払期日])" to "定額返済ローンにおいて、指定した期の支払額のうち元金返済分の金額を返します。"),
        "PRICE" to ("PRICE(受渡日, 満期日, 利率, 利回り, 償還価額, 頻度, [基準])" to "定期的に利子を支払う証券の額面 \$100 あたりの価格を返します。"),
        "PRICEDISC" to ("PRICEDISC(受渡日, 満期日, 割引率, 償還価額, [基準])" to "割引証券の額面 \$100 あたりの価格を返します。"),
        "PRICEMAT" to ("PRICEMAT(受渡日, 満期日, 発行日, 利率, 利回り, [基準])" to "満期日に利子が支払われる証券の額面 \$100 あたりの価格を返します。"),
        "PV" to ("PV(利率, 期間, 定期支払額, [将来価値], [支払期日])" to "ローンまたは投資の現在価値（将来の支払額の現在の価値合計）を計算します。"),
        "RATE" to ("RATE(期間, 定期支払額, 現在価値, [将来価値], [支払期日], [推定値])" to "ローンまたは投資の1期間あたりの利率を計算します。"),
        "RECEIVED" to ("RECEIVED(受渡日, 満期日, 投資額, 割引率, [基準])" to "全額投資された割引証券の満期日に受け取る金額（償還額）を返します。"),
        "RRI" to ("RRI(期間, 現在価値, 将来価値)" to "投資の成長に対する等価利率（複利成長率 CAGR）を返します。"),
        "SLN" to ("SLN(取得価額, 残存価額, 耐用年数)" to "定額法による1期あたりの資産の減価償却費を返します。"),
        "STOCKHISTORY" to ("STOCKHISTORY(銘柄, 開始日, [終了日], [間隔])" to "指定した銘柄や為替ペアの過去の価格履歴データを配列として取得します。"),
        "SYD" to ("SYD(取得価額, 残存価額, 耐用年数, 期間)" to "級数法（算術級数法）による指定期間の資産の減価償却費を返します。"),
        "TBILLEQ" to ("TBILLEQ(受渡日, 満期日, 割引率)" to "米国財務省短期証券（TB）の債券換算利回りを返します。"),
        "TBILLPRICE" to ("TBILLPRICE(受渡日, 満期日, 割引率)" to "米国財務省短期証券（TB）の額面 \$100 あたりの価格を返します。"),
        "TBILLYIELD" to ("TBILLYIELD(受渡日, 満期日, 価格)" to "米国財務省短期証券（TB）の利回りを返します。"),
        "VDB" to ("VDB(取得価額, 残存価額, 耐用年数, 開始期, 終了期, [倍率], [切替なし])" to "倍額定率法または指定償却率（定額法への自動切替対応）による指定期間の減価償却費を返します。"),
        "XIRR" to ("XIRR(値, 日付, [推定値])" to "定期的でない日付のキャッシュフローに対する内部利益率を返します。"),
        "XNPV" to ("XNPV(割引率, 値, 日付)" to "定期的でない日付のキャッシュフローに対する正味現在価値を返します。"),
        "YIELD" to ("YIELD(受渡日, 満期日, 利率, 価格, 償還価額, 頻度, [基準])" to "定期的に利子を支払う証券の利回りを返します。"),
        "YIELDDISC" to ("YIELDDISC(受渡日, 満期日, 価格, 償還価額, [基準])" to "割引証券（短期国債など）の年換算利回りを返します。"),
        "YIELDMAT" to ("YIELDMAT(受渡日, 満期日, 発行日, 利率, 価格, [基準])" to "満期日に利子が支払われる証券の年換算利回りを返します。"),

        // --- Information (20) ---
        "CELL" to ("CELL(検査の種類, [参照])" to "セルの書式、位置、または内容に関する情報（\"address\", \"filename\", \"format\" など）を返します。"),
        "ERROR.TYPE" to ("ERROR.TYPE(エラー値)" to "エラー値に対応する整数番号（#NULL!=1, #DIV/0!=2, #VALUE!=3, #REF!=4, #NAME?=5, #NUM!=6, #N/A=7）を返します。"),
        "INFO" to ("INFO(分類文字列)" to "現在の操作環境に関する情報（OSバージョン、ディレクトリ、再計算モードなど）を返します。"),
        "ISBLANK" to ("ISBLANK(値)" to "参照先のセルが空白である場合に TRUE を返します。"),
        "ISERR" to ("ISERR(値)" to "値が #N/A 以外の任意のエラー値である場合に TRUE を返します。"),
        "ISERROR" to ("ISERROR(値)" to "値が任意のエラー値（#N/A, #VALUE!, #REF!, #DIV/0!, #NUM!, #NAME?, #NULL!）である場合に TRUE を返します。"),
        "ISEVEN" to ("ISEVEN(数値)" to "数値が偶数の場合に TRUE を返し、奇数の場合に FALSE を返します。"),
        "ISFORMULA" to ("ISFORMULA(参照)" to "参照先のセルに数式が含まれている場合に TRUE を返します。"),
        "ISLOGICAL" to ("ISLOGICAL(値)" to "値が論理値（TRUE または FALSE）である場合に TRUE を返します。"),
        "ISNA" to ("ISNA(値)" to "値がエラー値 #N/A（利用可能な値なし）である場合に TRUE を返します。"),
        "ISNONTEXT" to ("ISNONTEXT(値)" to "値が文字列以外の項目（数値、日付、論理値、空白セルなど）である場合に TRUE を返します。"),
        "ISNUMBER" to ("ISNUMBER(値)" to "値が数値である場合に TRUE を返します。"),
        "ISODD" to ("ISODD(数値)" to "数値が奇数の場合に TRUE を返し、偶数の場合に FALSE を返します。"),
        "ISREF" to ("ISREF(値)" to "値が有効なセル参照である場合に TRUE を返します。"),
        "ISTEXT" to ("ISTEXT(値)" to "値が文字列（テキスト）である場合に TRUE を返します。"),
        "N" to ("N(値)" to "値を数値に変換します（数値はそのまま、日付はシリアル値、TRUE は 1、それ以外は 0）。"),
        "NA" to ("NA()" to "エラー値 #N/A（値なし）を返します。"),
        "SHEET" to ("SHEET([値])" to "参照先のシートのシート番号（1から始まるインデックス）を返します。"),
        "SHEETS" to ("SHEETS([参照])" to "参照またはブック全体に含まれるシートの枚数を返します。"),
        "TYPE" to ("TYPE(値)" to "値のデータ型を表す整数（数値=1、文字列=2、論理値=4、エラー値=16、配列=64）を返します。"),

        // --- Database (12) ---
        "DAVERAGE" to ("DAVERAGE(データベース, フィールド, 条件)" to "データベースまたはリストの指定した列で、検索条件に一致するレコードの平均値を返します。"),
        "DCOUNT" to ("DCOUNT(データベース, フィールド, 条件)" to "データベース内で検索条件に一致し、かつ数値が含まれるセルの個数を数えます。"),
        "DCOUNTA" to ("DCOUNTA(データベース, フィールド, 条件)" to "データベース内で検索条件に一致し、かつ空白でないセルの個数を数えます。"),
        "DGET" to ("DGET(データベース, フィールド, 条件)" to "データベースから検索条件に一致する単一のレコードの値を抽出します。"),
        "DMAX" to ("DMAX(データベース, フィールド, 条件)" to "データベース内で検索条件に一致するレコードの最大値を返します。"),
        "DMIN" to ("DMIN(データベース, フィールド, 条件)" to "データベース内で検索条件に一致するレコードの最小値を返します。"),
        "DPRODUCT" to ("DPRODUCT(データベース, フィールド, 条件)" to "データベース内で検索条件に一致するレコードの値の積を返します。"),
        "DSTDEV" to ("DSTDEV(データベース, フィールド, 条件)" to "データベース内で検索条件に一致するレコードを標本とみなして標準偏差を計算します。"),
        "DSTDEVP" to ("DSTDEVP(データベース, フィールド, 条件)" to "データベース内で検索条件に一致するレコードを母集団全体とみなして標準偏差を計算します。"),
        "DSUM" to ("DSUM(データベース, フィールド, 条件)" to "データベース内で検索条件に一致するレコードの指定フィールドの数値を合計します。"),
        "DVAR" to ("DVAR(データベース, フィールド, 条件)" to "データベース内で検索条件に一致するレコードを標本とみなして分散を計算します。"),
        "DVARP" to ("DVARP(データベース, フィールド, 条件)" to "データベース内で検索条件に一致するレコードを母集団全体とみなして母分散を計算します。"),

        // --- Engineering (54) ---
        "BESSELI" to ("BESSELI(x, n)" to "修正ベッセル関数 In(x) の値を返します。"),
        "BESSELJ" to ("BESSELJ(x, n)" to "ベッセル関数 Jn(x) の値を返します。"),
        "BESSELK" to ("BESSELK(x, n)" to "修正ベッセル関数 Kn(x) の値を返します。"),
        "BESSELY" to ("BESSELY(x, n)" to "ベッセル関数 Yn(x)（ノイマン関数）の値を返します。"),
        "BIN2DEC" to ("BIN2DEC(2進数)" to "2進数の文字列を10進数の数値に変換します。"),
        "BIN2HEX" to ("BIN2HEX(2進数, [桁数])" to "2進数の文字列を16進数の文字列に変換します。"),
        "BIN2OCT" to ("BIN2OCT(2進数, [桁数])" to "2進数の文字列を8進数の文字列に変換します。"),
        "BITAND" to ("BITAND(数値1, 数値2)" to "2つの数値のビット単位の論理積（AND）を返します。"),
        "BITLSHIFT" to ("BITLSHIFT(数値, シフト数)" to "数値を指定したビット数だけ左にシフトした値を返します。"),
        "BITOR" to ("BITOR(数値1, 数値2)" to "2つの数値のビット単位の論理和（OR）を返します。"),
        "BITRSHIFT" to ("BITRSHIFT(数値, シフト数)" to "数値を指定したビット数だけ右にシフトした値を返します。"),
        "BITXOR" to ("BITXOR(数値1, 数値2)" to "2つの数値のビット単位の排他的論理和（XOR）を返します。"),
        "COMPLEX" to ("COMPLEX(実数係数, 虚数係数, [虚数単位])" to "実数係数と虚数係数を \"x+yi\" または \"x+yj\" 形式の複素数文字列に変換します。"),
        "CONVERT" to ("CONVERT(数値, 変換前単位, 変換後単位)" to "数値をある計測単位系から別の計測単位系（例: マイルからキロメートル、華氏から摂氏）に変換します。"),
        "DEC2BIN" to ("DEC2BIN(10進数, [桁数])" to "10進数の数値を2進数の文字列に変換します。"),
        "DEC2HEX" to ("DEC2HEX(10進数, [桁数])" to "10進数の数値を16進数の文字列に変換します。"),
        "DEC2OCT" to ("DEC2OCT(10進数, [桁数])" to "10進数の数値を8進数の文字列に変換します。"),
        "DELTA" to ("DELTA(数値1, [数値2])" to "2つの数値が等しいかどうかを検査します（クロネッカーのデルタ: 等しければ 1、異なれば 0）。"),
        "ERF" to ("ERF(下限, [上限])" to "下限から上限までの間で積分した誤差関数（エラー関数）の値を返します。"),
        "ERF.PRECISE" to ("ERF.PRECISE(x)" to "0 から x までの間で積分した高精度な誤差関数の値を返します。"),
        "ERFC" to ("ERFC(x)" to "x から無限大までの間で積分した相補誤差関数の値を返します。"),
        "ERFC.PRECISE" to ("ERFC.PRECISE(x)" to "x から無限大までの間で積分した高精度な相補誤差関数の値を返します。"),
        "GESTEP" to ("GESTEP(数値, [しきい値])" to "数値がしきい値以上であるかどうかを検査します（以上なら 1、未満なら 0）。"),
        "HEX2BIN" to ("HEX2BIN(16進数, [桁数])" to "16進数の文字列を2進数の文字列に変換します。"),
        "HEX2DEC" to ("HEX2DEC(16進数)" to "16進数の文字列を10進数の数値に変換します。"),
        "HEX2OCT" to ("HEX2OCT(16進数, [桁数])" to "16進数の文字列を8進数の文字列に変換します。"),
        "IMABS" to ("IMABS(複素数)" to "複素数 \"x+yi\" の絶対値（動径 √(x^2 + y^2)）を返します。"),
        "IMAGINARY" to ("IMAGINARY(複素数)" to "複素数 \"x+yi\" の虚数係数（y）を返します。"),
        "IMARGUMENT" to ("IMARGUMENT(複素数)" to "複素数の偏角 θ をラジアンで表した角度として返します。"),
        "IMCONJUGATE" to ("IMCONJUGATE(複素数)" to "複素数 \"x+yi\" の共役複素数 \"x-yi\" を返します。"),
        "IMCOS" to ("IMCOS(複素数)" to "複素数のコサイン（余弦）を返します。"),
        "IMCOSH" to ("IMCOSH(複素数)" to "複素数の双曲線余弦（ハイパボリック コサイン）を返します。"),
        "IMCOT" to ("IMCOT(複素数)" to "複素数のコタンジェント（余接）を返します。"),
        "IMCSC" to ("IMCSC(複素数)" to "複素数のコセカント（余割）を返します。"),
        "IMCSCH" to ("IMCSCH(複素数)" to "複素数の双曲線余割（ハイパボリック コセカント）を返します。"),
        "IMDIV" to ("IMDIV(複素数1, 複素数2)" to "2つの複素数の商（複素数1 ÷ 複素数2）を返します。"),
        "IMEXP" to ("IMEXP(複素数)" to "複素数の指数（e^z）を返します。"),
        "IMLN" to ("IMLN(複素数)" to "複素数の自然対数を返します。"),
        "IMLOG10" to ("IMLOG10(複素数)" to "複素数の常用対数（底 10）を返します。"),
        "IMLOG2" to ("IMLOG2(複素数)" to "複素数の2を底とする対数を返します。"),
        "IMPOWER" to ("IMPOWER(複素数, 指数)" to "複素数を整数または実数でべき乗した結果を返します。"),
        "IMPRODUCT" to ("IMPRODUCT(複素数1, [複素数2], ...)" to "1〜255個の複素数の積を返します。"),
        "IMREAL" to ("IMREAL(複素数)" to "複素数 \"x+yi\" の実数係数（x）を返します。"),
        "IMSEC" to ("IMSEC(複素数)" to "複素数のセカント（正割）を返します。"),
        "IMSECH" to ("IMSECH(複素数)" to "複素数の双曲線正割（ハイパボリック セカント）を返します。"),
        "IMSIN" to ("IMSIN(複素数)" to "複素数のサイン（正弦）を返します。"),
        "IMSINH" to ("IMSINH(複素数)" to "複素数の双曲線正弦（ハイパボリック サイン）を返します。"),
        "IMSQRT" to ("IMSQRT(複素数)" to "複素数の平方根を返します。"),
        "IMSUB" to ("IMSUB(複素数1, 複素数2)" to "2つの複素数の差（複素数1 - 複素数2）を返します。"),
        "IMSUM" to ("IMSUM(複素数1, [複素数2], ...)" to "複数の複素数の和（合計）を返します。"),
        "IMTAN" to ("IMTAN(複素数)" to "複素数のタンジェント（正接）を返します。"),
        "OCT2BIN" to ("OCT2BIN(8進数, [桁数])" to "8進数の文字列を2進数の文字列に変換します。"),
        "OCT2DEC" to ("OCT2DEC(8進数)" to "8進数の文字列を10進数の数値に変換します。"),
        "OCT2HEX" to ("OCT2HEX(8進数, [桁数])" to "8進数の文字列を16進数の文字列に変換します。"),

        // --- Cube (7) ---
        "CUBEKPIMEMBER" to ("CUBEKPIMEMBER(接続, KPI名, KPIプロパティ, [見出し])" to "OLAP キューブの重要業績評価指標（KPI）プロパティを返し、KPI名を表示します。"),
        "CUBEMEMBER" to ("CUBEMEMBER(接続, メンバー式, [見出し])" to "OLAP キューブからメンバーまたは組（タプル）を取得して検証します。"),
        "CUBEMEMBERPROPERTY" to ("CUBEMEMBERPROPERTY(接続, メンバー式, プロパティ)" to "OLAP キューブ内のメンバープロパティの値を返します。"),
        "CUBERANKEDMEMBER" to ("CUBERANKEDMEMBER(接続, セット式, 順位, [見出し])" to "OLAP キューブのセット内で n 番目にランク付けされたメンバーを返します。"),
        "CUBESET" to ("CUBESET(接続, セット式, [見出し], [並べ替え順序], [並べ替え基準])" to "OLAP キューブにセット式を送信してメンバーや組の定義済みセットを作成します。"),
        "CUBESETCOUNT" to ("CUBESETCOUNT(セット)" to "CUBESET で定義されたセット内のアイテム（メンバー）の個数を返します。"),
        "CUBEVALUE" to ("CUBEVALUE(接続, [メンバー式1], ...)" to "OLAP キューブから集計値（メジャー値）を取得して返します。"),

        // --- Web, Translation & AI / External (9) ---
        "ENCODEURL" to ("ENCODEURL(文字列)" to "文字列を URL エンコード（パーセントエンコーディング）された文字列に変換します。"),
        "FILTERXML" to ("FILTERXML(XML文字列, XPath)" to "指定した XPath 式を使用して XML コンテンツから特定のデータを抽出します。"),
        "WEBSERVICE" to ("WEBSERVICE(URL)" to "インターネットまたはイントラネット上の Web サービスからデータを取得します。"),
        "DETECTLANGUAGE" to ("DETECTLANGUAGE(テキスト)" to "指定したテキストの言語コード（\"ja\", \"en\" 等）を自動判定して返します。"),
        "TRANSLATE" to ("TRANSLATE(テキスト, [元の言語], [翻訳先の言語])" to "テキストを指定した翻訳先の言語に翻訳します。"),
        "COPILOT" to ("COPILOT(プロンプト, [コンテキスト範囲])" to "AIアシスタント（Copilot / Gemini）を呼び出してテキスト生成やデータ分析結果をセルに返します。"),
        "PY" to ("PY(Pythonコード)" to "セル内で Python 式・データフレーム計算を評価して結果を返します。"),
        "CALL" to ("CALL(ライブラリ名, プロシージャ名, 型文字列, [引数1], ...)" to "ダイナミックリンクライブラリ（DLL）またはコードリソースのプロシージャを呼び出します。"),
        "REGISTER.ID" to ("REGISTER.ID(ライブラリ名, プロシージャ名, [型文字列])" to "指定したダイナミックリンクライブラリ（DLL）またはコードリソースの登録 ID を返します。")
    )

    private fun inferCategoryJa(name: String): String {
        return when {
            name.startsWith("CUBE") -> "キューブ"
            name in setOf("ENCODEURL", "FILTERXML", "WEBSERVICE", "DETECTLANGUAGE", "TRANSLATE") -> "Web"
            name in setOf(
                "COPILOT", "PY", "CALL", "REGISTER.ID", "FILTER", "SORT", "SORTBY", "UNIQUE", "SEQUENCE",
                "RANDARRAY", "LET", "LAMBDA", "ISOMITTED", "MAP", "REDUCE", "SCAN", "BYROW", "BYCOL",
                "MAKEARRAY", "VSTACK", "HSTACK", "TOROW", "TOCOL", "WRAPROWS", "WRAPCOLS", "TAKE", "DROP",
                "CHOOSEROWS", "CHOOSECOLS", "EXPAND", "GROUPBY", "PIVOTBY", "PERCENTOF", "SINGLE"
            ) -> "AI・動的配列・互換性"
            name in setOf("DAVERAGE", "DCOUNT", "DCOUNTA", "DGET", "DMAX", "DMIN", "DPRODUCT", "DSTDEV", "DSTDEVP", "DSUM", "DVAR", "DVARP") -> "データベース"
            name.startsWith("IM") || name.startsWith("BESSEL") || name.startsWith("BIT") || name.startsWith("ERF") ||
                name.contains("2BIN") || name.contains("2DEC") || name.contains("2HEX") || name.contains("2OCT") ||
                name in setOf("COMPLEX", "CONVERT", "DELTA", "GESTEP") -> "エンジニアリング"
            name in setOf(
                "CELL", "ERROR.TYPE", "INFO", "ISBLANK", "ISERR", "ISERROR", "ISEVEN", "ISFORMULA",
                "ISLOGICAL", "ISNA", "ISNONTEXT", "ISNUMBER", "ISODD", "ISREF", "ISTEXT", "N", "NA",
                "SHEET", "SHEETS", "TYPE"
            ) -> "情報"
            name in setOf("AND", "FALSE", "IF", "IFERROR", "IFNA", "IFS", "NOT", "OR", "SWITCH", "TRUE", "XOR") -> "論理"
            name in setOf(
                "DATE", "DATEDIF", "DATEVALUE", "DAY", "DAYS", "DAYS360", "EDATE", "EOMONTH", "HOUR",
                "ISOWEEKNUM", "MINUTE", "MONTH", "NETWORKDAYS", "NETWORKDAYS.INTL", "NOW", "SECOND",
                "TIME", "TIMEVALUE", "TODAY", "WEEKDAY", "WEEKNUM", "WORKDAY", "WORKDAY.INTL", "YEAR", "YEARFRAC"
            ) -> "日付/時刻"
            name in setOf(
                "ADDRESS", "AREAS", "CHOOSE", "COLUMN", "COLUMNS", "FORMULATEXT", "GETPIVOTDATA",
                "HLOOKUP", "HYPERLINK", "IMAGE", "INDEX", "INDIRECT", "LOOKUP", "MATCH", "OFFSET",
                "ROW", "ROWS", "RTD", "TRANSPOSE", "VLOOKUP", "XLOOKUP", "XMATCH"
            ) -> "検索/行列"
            name in setOf(
                "ARRAYTOTEXT", "ASC", "BAHTTEXT", "CHAR", "CLEAN", "CODE", "CONCAT", "CONCATENATE",
                "DBCS", "DOLLAR", "EXACT", "FIND", "FINDB", "FIXED", "JIS", "LEFT", "LEFTB", "LEN",
                "LENB", "LOWER", "MID", "MIDB", "NUMBERVALUE", "PHONETIC", "PROPER", "REGEXEXTRACT",
                "REGEXREPLACE", "REGEXTEST", "REPLACE", "REPLACEB", "REPT", "RIGHT", "RIGHTB",
                "SEARCH", "SEARCHB", "SUBSTITUTE", "T", "TEXT", "TEXTAFTER", "TEXTBEFORE", "TEXTJOIN",
                "TEXTSPLIT", "TRIM", "UNICHAR", "UNICODE", "UPPER", "VALUE", "VALUETOTEXT"
            ) -> "文字列操作"
            name in setOf(
                "ACCRINT", "ACCRINTM", "AMORDEGRC", "AMORLINC", "COUPDAYBS", "COUPDAYS", "COUPDAYSNC",
                "COUPNCD", "COUPNUM", "COUPPCD", "CUMIPMT", "CUMPRINC", "DB", "DDB", "DISC", "DOLLARDE",
                "DOLLARFR", "DURATION", "EFFECT", "EUROCONVERT", "FV", "FVSCHEDULE", "INTRATE", "IPMT",
                "IRR", "ISPMT", "MDURATION", "MIRR", "NOMINAL", "NPER", "NPV", "ODDFPRICE", "ODDFYIELD",
                "ODDLPRICE", "ODDLYIELD", "PDURATION", "PMT", "PPMT", "PRICE", "PRICEDISC", "PRICEMAT",
                "PV", "RATE", "RECEIVED", "RRI", "SLN", "SYD", "TBILLEQ", "TBILLPRICE", "TBILLYIELD",
                "VDB", "XIRR", "XNPV", "YIELD", "YIELDDISC", "YIELDMAT", "STOCKHISTORY"
            ) -> "財務"
            name in setOf(
                "ABS", "ACOS", "ACOSH", "ACOT", "ACOTH", "AGGREGATE", "ARABIC", "ASIN", "ASINH", "ATAN",
                "ATAN2", "ATANH", "BASE", "CEILING", "CEILING.MATH", "CEILING.PRECISE", "COMBIN", "COMBINA",
                "COS", "COSH", "COT", "COTH", "CSC", "CSCH", "DECIMAL", "DEGREES", "EVEN", "EXP", "FACT",
                "FACTDOUBLE", "FLOOR", "FLOOR.MATH", "FLOOR.PRECISE", "GCD", "INT", "ISO.CEILING", "LCM",
                "LN", "LOG", "LOG10", "MDETERM", "MINVERSE", "MMULT", "MOD", "MROUND", "MULTINOMIAL",
                "MUNIT", "ODD", "PI", "POWER", "PRODUCT", "QUOTIENT", "RADIANS", "RAND", "RANDBETWEEN",
                "ROMAN", "ROUND", "ROUNDDOWN", "ROUNDUP", "SEC", "SECH", "SERIESSUM", "SIGN", "SIN",
                "SINH", "SQRT", "SQRTPI", "SUBTOTAL", "SUM", "SUMIF", "SUMIFS", "SUMPRODUCT", "SUMSQ",
                "SUMX2MY2", "SUMX2PY2", "SUMXMY2", "TAN", "TANH", "TRUNC"
            ) -> "数学/三角"
            else -> "統計"
        }
    }

    private val explicitEnglishDescriptions: Map<String, String> = mapOf(
        "ABS" to "Returns the absolute value of a number (a number without its sign).",
        "ACOS" to "Returns the arccosine (inverse cosine) of a number in radians (0 to pi).",
        "ACOSH" to "Returns the inverse hyperbolic cosine of a number.",
        "ACOT" to "Returns the principal value of the arccotangent of a number in radians.",
        "ACOTH" to "Returns the inverse hyperbolic cotangent of a number.",
        "AGGREGATE" to "Returns an aggregate calculation in a list or database with options to ignore hidden rows and errors.",
        "ARABIC" to "Converts a Roman numeral string to an Arabic number.",
        "ASIN" to "Returns the arcsine (inverse sine) of a number in radians (-pi/2 to pi/2).",
        "ASINH" to "Returns the inverse hyperbolic sine of a number.",
        "ATAN" to "Returns the arctangent (inverse tangent) of a number in radians (-pi/2 to pi/2).",
        "ATAN2" to "Returns the arctangent from specified x- and y-coordinates in radians.",
        "ATANH" to "Returns the inverse hyperbolic tangent of a number.",
        "AVERAGE" to "Returns the arithmetic mean (average) of its arguments.",
        "AVERAGEA" to "Returns the average of its arguments, evaluating text and FALSE as 0 and TRUE as 1.",
        "AVERAGEIF" to "Returns the average of all cells in a range that meet a given criterion.",
        "AVERAGEIFS" to "Returns the average of all cells that meet multiple criteria.",
        "CEILING" to "Rounds a number up, away from zero, to the nearest multiple of significance.",
        "CHOOSE" to "Uses index_num to return a value from the list of value arguments.",
        "CONCAT" to "Combines the text from multiple ranges and/or strings.",
        "CONCATENATE" to "Joins two or more text strings into one string.",
        "COS" to "Returns the cosine of the given angle in radians.",
        "COUNT" to "Counts the number of cells in a range that contain numbers.",
        "COUNTA" to "Counts the number of non-empty cells in a range.",
        "COUNTBLANK" to "Counts the number of empty cells in a specified range.",
        "COUNTIF" to "Counts the number of cells within a range that meet the given criterion.",
        "COUNTIFS" to "Counts the number of cells specified by a given set of conditions or criteria.",
        "DATE" to "Returns the serial number that represents a particular date (year, month, day).",
        "DATEDIF" to "Calculates the number of days, months, or years between two dates.",
        "DAY" to "Returns the day of a date, represented by a serial number (1 to 31).",
        "EDATE" to "Returns the serial number of the date that is the indicated number of months before or after start_date.",
        "EOMONTH" to "Returns the serial number of the last day of the month before or after a specified number of months.",
        "EXACT" to "Checks whether two text strings are exactly the same, and returns TRUE or FALSE.",
        "EXP" to "Returns e raised to the power of a given number.",
        "FACT" to "Returns the factorial of a number (1*2*3*...*n).",
        "FILTER" to "Filters a range of data based on criteria you define.",
        "FIND" to "Locates one text string within a second text string (case-sensitive).",
        "FLOOR" to "Rounds a number down, toward zero, to the nearest multiple of significance.",
        "FV" to "Returns the future value of an investment based on periodic, constant payments and a constant interest rate.",
        "HLOOKUP" to "Looks for a value in the top row of a table or array of values and returns a value in the same column.",
        "HOUR" to "Returns the hour of a time value (0 to 23).",
        "IF" to "Checks whether a condition is met, and returns one value if TRUE and another value if FALSE.",
        "IFERROR" to "Returns a value you specify if a formula evaluates to an error; otherwise returns the result of the formula.",
        "IFNA" to "Returns the value you specify if the formula returns the #N/A error value; otherwise returns the result.",
        "IFS" to "Checks whether one or more conditions are met and returns a value that corresponds to the first TRUE condition.",
        "INDEX" to "Returns a value or the reference to a value from within a table or range.",
        "INDIRECT" to "Returns the reference specified by a text string.",
        "INT" to "Rounds a number down to the nearest integer.",
        "IPMT" to "Returns the interest payment for a given period for an investment based on periodic, constant payments.",
        "IRR" to "Returns the internal rate of return for a series of cash flows.",
        "ISBLANK" to "Returns TRUE if the value refers to an empty cell.",
        "ISERROR" to "Returns TRUE if the value is any error value.",
        "ISNA" to "Returns TRUE if the value is the #N/A error value.",
        "ISNUMBER" to "Returns TRUE if the value is a number.",
        "ISTEXT" to "Returns TRUE if the value is text.",
        "LAMBDA" to "Creates a custom, reusable function and calls it by a friendly name.",
        "LARGE" to "Returns the k-th largest value in a data set.",
        "LEFT" to "Returns the specified number of characters from the start of a text string.",
        "LEN" to "Returns the number of characters in a text string.",
        "LET" to "Assigns names to calculation results to store intermediate calculations and values inside a formula.",
        "LN" to "Returns the natural logarithm of a number.",
        "LOG" to "Returns the logarithm of a number to the base you specify.",
        "LOG10" to "Returns the base-10 logarithm of a number.",
        "LOOKUP" to "Looks up a value either from a one-row or one-column range or from an array.",
        "LOWER" to "Converts all uppercase letters in a text string to lowercase.",
        "MATCH" to "Searches for a specified item in a range of cells, and returns the relative position of that item.",
        "MAX" to "Returns the largest value in a set of values.",
        "MAXIFS" to "Returns the maximum value among cells specified by a given set of conditions or criteria.",
        "MEDIAN" to "Returns the median (middle value) of the given numbers.",
        "MID" to "Returns a specific number of characters from a text string, starting at the position you specify.",
        "MIN" to "Returns the smallest number in a set of values.",
        "MINIFS" to "Returns the minimum value among cells specified by a given set of conditions or criteria.",
        "MINUTE" to "Returns the minute of a time value (0 to 59).",
        "MOD" to "Returns the remainder after number is divided by divisor.",
        "MODE" to "Returns the most frequently occurring, or repetitive, value in an array or range of data.",
        "MONTH" to "Returns the month of a date represented by a serial number (1 to 12).",
        "NETWORKDAYS" to "Returns the number of whole working days between start_date and end_date.",
        "NOT" to "Reverses the logic of its argument (TRUE becomes FALSE, FALSE becomes TRUE).",
        "NOW" to "Returns the serial number of the current date and time.",
        "NPER" to "Returns the number of periods for an investment based on periodic, constant payments and a constant interest rate.",
        "NPV" to "Calculates the net present value of an investment by using a discount rate and a series of future payments and income.",
        "OFFSET" to "Returns a reference to a range that is a specified number of rows and columns from a cell or range of cells.",
        "OR" to "Returns TRUE if any argument is TRUE; returns FALSE if all arguments are FALSE.",
        "PI" to "Returns the number 3.14159265358979, the mathematical constant pi.",
        "PMT" to "Calculates the payment for a loan based on constant payments and a constant interest rate.",
        "POWER" to "Returns the result of a number raised to a power.",
        "PPMT" to "Returns the payment on the principal for a given period for an investment based on periodic, constant payments.",
        "PRODUCT" to "Multiplies all the numbers given as arguments and returns the product.",
        "PROPER" to "Capitalizes the first letter in each word of a text value.",
        "PV" to "Calculates the present value of a loan or an investment, based on a constant interest rate.",
        "QUOTIENT" to "Returns the integer portion of a division.",
        "RADIANS" to "Converts degrees to radians.",
        "RAND" to "Returns an evenly distributed random real number greater than or equal to 0 and less than 1.",
        "RANDBETWEEN" to "Returns a random integer number between the numbers you specify.",
        "RANK" to "Returns the rank of a number in a list of numbers.",
        "RATE" to "Returns the interest rate per period of an annuity.",
        "REPLACE" to "Replaces part of a text string, based on the number of characters you specify, with a different text string.",
        "REPT" to "Repeats text a given number of times.",
        "RIGHT" to "Returns the last character or characters in a text string, based on the number of characters you specify.",
        "ROUND" to "Rounds a number to a specified number of digits.",
        "ROUNDDOWN" to "Rounds a number down, toward zero, to a specified number of digits.",
        "ROUNDUP" to "Rounds a number up, away from zero, to a specified number of digits.",
        "ROW" to "Returns the row number of a reference.",
        "ROWS" to "Returns the number of rows in a reference or array.",
        "SEARCH" to "Locates one text string within a second text string (case-insensitive).",
        "SECOND" to "Returns the second of a time value (0 to 59).",
        "SEQUENCE" to "Generates a list of sequential numbers in an array, such as 1, 2, 3, 4.",
        "SIGN" to "Determines the sign of a number (1 if positive, 0 if zero, -1 if negative).",
        "SIN" to "Returns the sine of the given angle in radians.",
        "SMALL" to "Returns the k-th smallest value in a data set.",
        "SORT" to "Sorts the contents of a range or array.",
        "SORTBY" to "Sorts the contents of a range or array based on the values in a corresponding range or array.",
        "SQRT" to "Returns a positive square root of a number.",
        "STDEV" to "Estimates standard deviation based on a sample.",
        "STDEV.P" to "Calculates standard deviation based on the entire population given as arguments.",
        "STDEV.S" to "Estimates standard deviation based on a sample.",
        "SUBSTITUTE" to "Substitutes new_text for old_text in a text string.",
        "SUBTOTAL" to "Returns a subtotal in a list or database.",
        "SUM" to "Adds all the numbers in a range of cells.",
        "SUMIF" to "Adds the cells specified by a given condition or criteria.",
        "SUMIFS" to "Adds the cells in a range that meet multiple criteria.",
        "SUMPRODUCT" to "Returns the sum of the products of corresponding ranges or arrays.",
        "SWITCH" to "Evaluates an expression against a list of values and returns the result corresponding to the first matching value.",
        "TAN" to "Returns the tangent of the given angle in radians.",
        "TEXT" to "Formats a number and converts it to text using a specified format code.",
        "TEXTAFTER" to "Returns text that occurs after a given character or string.",
        "TEXTBEFORE" to "Returns text that occurs before a given character or string.",
        "TEXTJOIN" to "Combines the text from multiple ranges and/or strings, with a specifiable delimiter.",
        "TEXTSPLIT" to "Splits text strings by using column and row delimiters.",
        "TIME" to "Returns the serial number of a particular time (hour, minute, second).",
        "TODAY" to "Returns the serial number of today's date.",
        "TRANSPOSE" to "Returns a vertical range of cells as a horizontal range, or vice versa.",
        "TRIM" to "Removes all spaces from text except for single spaces between words.",
        "TRUE" to "Returns the logical value TRUE.",
        "TRUNC" to "Truncates a number to an integer by removing the fractional part of the number.",
        "UNIQUE" to "Returns a list of unique values in a list or range.",
        "UPPER" to "Converts text to uppercase.",
        "VALUE" to "Converts a text argument that represents a number to a numeric value.",
        "VAR.P" to "Calculates variance based on the entire population.",
        "VAR.S" to "Estimates variance based on a sample.",
        "VLOOKUP" to "Looks for a value in the leftmost column of a table, and then returns a value in the same row from a column you specify.",
        "WEEKDAY" to "Returns the day of the week corresponding to a date (1 to 7).",
        "WORKDAY" to "Returns the serial number of the date before or after a specified number of workdays.",
        "XLOOKUP" to "Searches a range or an array, and returns an item corresponding to the first match it finds.",
        "XMATCH" to "Returns the relative position of an item in an array or range of cells.",
        "XOR" to "Returns a logical Exclusive Or of all arguments.",
        "YEAR" to "Returns the year corresponding to a date (1900 to 9999)."
    )

    private fun buildSyntaxEn(name: String, syntaxJa: String): String {
        val paramTranslation = listOf(
            "集計方法" to "function_num",
            "オプション" to "options",
            "ローマ数字文字列" to "roman_text",
            "x座標" to "x_num",
            "y座標" to "y_num",
            "最小長" to "min_length",
            "基準値" to "significance",
            "モード" to "mode",
            "抜き取り数" to "number_chosen",
            "総数" to "number",
            "検索値" to "lookup_value",
            "検索範囲" to "lookup_array",
            "戻り配列" to "return_array",
            "範囲" to "range",
            "検索条件" to "criteria",
            "合計対象範囲" to "sum_range",
            "平均対象範囲" to "average_range",
            "論理式" to "logical_test",
            "真の場合" to "value_if_true",
            "偽の場合" to "value_if_false",
            "エラーの場合の値" to "value_if_error",
            "配列" to "array",
            "行番号" to "row_num",
            "列番号" to "col_num",
            "照合の型" to "match_type",
            "検索の型" to "range_lookup",
            "開始位置" to "start_num",
            "文字数" to "num_chars",
            "文字列" to "text",
            "テキスト" to "text",
            "表示形式" to "format_text",
            "区切り文字" to "delimiter",
            "利率" to "rate",
            "期間" to "nper",
            "定期支払額" to "pmt",
            "現在価値" to "pv",
            "将来価値" to "fv",
            "開始日" to "start_date",
            "終了日" to "end_date",
            "日付" to "date",
            "時刻" to "time",
            "年" to "year",
            "月" to "month",
            "日" to "day",
            "時" to "hour",
            "分" to "minute",
            "秒" to "second",
            "桁数" to "num_digits",
            "除数" to "divisor",
            "被除数" to "numerator",
            "指数" to "power",
            "底" to "base",
            "基数" to "radix",
            "角度" to "angle",
            "複素数" to "inumber",
            "参照" to "ref",
            "数値" to "number",
            "値" to "value",
            "条件" to "criteria",
            "データ" to "data",
            "確率" to "probability",
            "自由度" to "deg_freedom",
            "平均" to "mean",
            "標準偏差" to "standard_dev"
        )
        var s = syntaxJa
        for ((ja, en) in paramTranslation) {
            s = s.replace(ja, en)
        }
        // Replace any remaining non-ASCII parameter tokens inside parentheses with clean English argument names
        if (s.any { it.code > 127 }) {
            val openIdx = s.indexOf('(')
            val closeIdx = s.lastIndexOf(')')
            if (openIdx > 0 && closeIdx > openIdx) {
                val rawArgs = s.substring(openIdx + 1, closeIdx).split(",")
                val cleanArgs = rawArgs.mapIndexed { idx, raw ->
                    val trimmed = raw.trim()
                    if (trimmed == "...") "..."
                    else if (trimmed.any { it.code > 127 }) {
                        val isOpt = trimmed.startsWith("[") && trimmed.endsWith("]")
                        if (isOpt) "[arg${idx + 1}]" else "arg${idx + 1}"
                    } else trimmed
                }
                s = "${name}(${cleanArgs.joinToString(", ")})"
            } else {
                s = "$name(value)"
            }
        }
        return s
    }

    private fun buildDescriptionEn(name: String, categoryJa: String, syntaxEn: String): String {
        explicitEnglishDescriptions[name]?.let { return it }
        val catEn = when (categoryJa) {
            "数学/三角" -> "Math & Trigonometry"
            "統計" -> "Statistical"
            "検索/行列" -> "Lookup & Reference"
            "論理" -> "Logical"
            "文字列操作" -> "Text"
            "日付/時刻" -> "Date & Time"
            "財務" -> "Financial"
            "情報" -> "Information"
            "データベース" -> "Database"
            "エンジニアリング" -> "Engineering"
            "キューブ" -> "OLAP Cube"
            "Web" -> "Web"
            else -> "Dynamic Array & Worksheet"
        }
        return "Computes the $name ($catEn) spreadsheet function using syntax $syntaxEn."
    }

    val ALL_ENTRIES: List<SpreadsheetFunctionEntry> by lazy {
        ALL_523_FUNCTION_NAMES.map { fnName ->
            val spec = functionSpecs[fnName]
            val cat = inferCategoryJa(fnName)
            val syntax = spec?.first ?: "$fnName(値)"
            val desc = spec?.second ?: "$fnName 関数の値を返します。"
            val synEn = buildSyntaxEn(fnName, syntax)
            val descEn = buildDescriptionEn(fnName, cat, synEn)
            SpreadsheetFunctionEntry(
                name = fnName,
                categoryJa = cat,
                syntax = syntax,
                descriptionJa = desc,
                syntaxEn = synEn,
                descriptionEn = descEn
            )
        }
    }

    fun search(query: String, categoryJa: String = "すべて"): List<SpreadsheetFunctionEntry> {
        val q = query.trim().uppercase()
        return ALL_ENTRIES.filter { entry ->
            val catMatch = when (categoryJa) {
                "すべて", "" -> true
                "よく使う関数" -> entry.name in POPULAR_FUNCTION_NAMES
                else -> entry.categoryJa == categoryJa
            }
            val textMatch = q.isEmpty() ||
                entry.name.contains(q) ||
                entry.descriptionJa.uppercase().contains(q) ||
                entry.descriptionEn.uppercase().contains(q) ||
                entry.syntax.uppercase().contains(q) ||
                entry.syntaxEn.uppercase().contains(q)
            catMatch && textMatch
        }
    }

    fun suggestAutocomplete(formulaText: String): List<SpreadsheetFunctionEntry> {
        if (!formulaText.startsWith("=")) return emptyList()
        val afterEq = formulaText.substring(1)
        val token = afterEq.takeLastWhile { it.isLetterOrDigit() || it == '.' || it == '_' }.uppercase()
        if (token.isEmpty()) return emptyList()
        return ALL_ENTRIES.filter { it.name.startsWith(token) }.take(10)
    }
}
