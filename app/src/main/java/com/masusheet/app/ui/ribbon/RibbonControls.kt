package com.masusheet.app

import com.masusheet.app.bridge.MasuSheetNativeBridge
import com.masusheet.app.storage.AndroidKeystoreSecretStore
import com.masusheet.app.storage.InMemorySecretStore
import com.masusheet.app.storage.MasuSheetClipboardImageProvider
import com.masusheet.app.storage.SecretStore
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.Spannable
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputContentInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.OverScroller
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * ============================================================================
 * [EN] Reusable Ribbon Buttons, Dropdown Popups, & Hover-Tooltip Controls
 * Provides [MasuSheetPillButton], [SpreadsheetRibbonDropdownPopup], [SpreadsheetRibbonGroup], [SpreadsheetTallRibbonButton], [SpreadsheetSmallIconBtn], and accessible icon semantics (`L-08`).
 *
 * ---
 * [JA / 日本語解説]
 * リボン全体で共通利用されるピルボタン、ドロップダウンメニュー、ツールチップ付きアイコンボタン、チェックボックス、およびアクセシビリティ対応 (`contentDescription`) コントロール群です。
 * ============================================================================
 */

/**
 * [EN] UI/application helper `MasuSheetPillButton` (masu sheet pill button).
 * [JA] `MasuSheetPillButton` に対応するUI／アプリケーション補助処理を実行します。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MasuSheetPillButton(
    label: String,
    highlight: Boolean = false,
    tag: String = "",
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val isEn = LocalAppLanguage.current == "en"
    val isM3 = LocalMaterial3Ui.current
    val isDark = LocalDarkTheme.current
    val displayLabel = SpreadsheetI18n.t(label, isEn)

    if (isM3) {
        val shape = RoundedCornerShape(10.dp)
        val bg = when {
            highlight && isDark -> Color(0xFF15803D)
            highlight -> Color(0xFFDCFCE7)
            isDark -> Color(0xFF28384E)
            else -> Color(0xFFF1F5F9)
        }
        val border = when {
            highlight && isDark -> Color(0xFF6EE7B7)
            highlight -> Color(0xFF15803D)
            isDark -> Color(0xFF4E6582)
            else -> Color(0xFFCBD5E1)
        }
        val borderW = if (highlight) 1.3.dp else 0.9.dp
        val fg = when {
            highlight && isDark -> Color(0xFFFFFFFF)
            highlight -> Color(0xFF14532D)
            isDark -> Color(0xFFF8FAFC)
            else -> Color(0xFF0F172A)
        }
        Surface(
            modifier = Modifier
                .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)
                .heightIn(min = 26.dp)
                .clip(shape)
                .border(borderW, border, shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            shape = shape,
            color = bg,
            tonalElevation = if (highlight) 3.dp else 1.dp
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = displayLabel,
                    color = fg,
                    fontSize = 11.sp,
                    maxLines = 1,
                    fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    } else {
        val shape = RoundedCornerShape(4.dp)
        val bg = when {
            highlight && isDark -> Color(0xFF164E35)
            highlight -> Color(0xFFE6F4EA)
            isDark -> Color(0xFF28313D)
            else -> Color(0xFFF3F2F1)
        }
        val border = when {
            highlight && isDark -> Color(0xFF34D399)
            highlight -> Color(0xFF107C41)
            else -> Color.Transparent
        }
        val fg = when {
            highlight && isDark -> Color(0xFFFFFFFF)
            highlight -> Color(0xFF107C41)
            isDark -> Color(0xFFF8FAFC)
            else -> Color(0xFF201F1E)
        }
        Box(
            modifier = Modifier
                .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)
                .heightIn(min = 26.dp)
                .clip(shape)
                .background(bg)
                .then(if (highlight) Modifier.border(0.8.dp, border, shape) else Modifier)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayLabel,
                color = fg,
                fontSize = 11.sp,
                maxLines = 1,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

/**
 * [EN] UI/application helper `SpreadsheetDropdownActionItem` (spreadsheet dropdown action item).
 * [JA] `SpreadsheetDropdownActionItem` に対応するUI／アプリケーション補助処理を実行します。
 */
data class SpreadsheetDropdownActionItem(
    val label: String,
    val isHeader: Boolean = false,
    val isDivider: Boolean = false,
    val subItems: List<Pair<String, () -> Unit>> = emptyList(),
    val onClick: () -> Unit = {}
)

/**
 * [EN] UI/application helper `SpreadsheetRibbonDropdownPopup` (spreadsheet ribbon dropdown popup).
 * [JA] `SpreadsheetRibbonDropdownPopup` に対応するUI／アプリケーション補助処理を実行します。
 */
@Composable
fun SpreadsheetRibbonDropdownPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<SpreadsheetDropdownActionItem>
) {
    if (!expanded || items.isEmpty()) return
    val isEn = LocalAppLanguage.current == "en"
    val isDark = LocalDarkTheme.current
    val menuBg = if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF)
    val textCol = if (isDark) Color(0xFFF1F5F9) else Color(0xFF201F1E)
    val subBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF3F2F1)
    var expandedSubMenuIndex by remember { mutableStateOf<Int?>(null) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            expandedSubMenuIndex = null
            onDismiss()
        },
        modifier = Modifier
            .background(menuBg)
            .widthIn(min = 240.dp, max = 360.dp)
            .heightIn(max = 460.dp)
    ) {
        items.forEachIndexed { idx, item ->
            when {
                item.isDivider -> {
                    HorizontalDivider(
                        color = if (isDark) Color(0xFF334155) else Color(0xFFE1DFDD),
                        thickness = 0.8.dp
                    )
                }
                item.isHeader -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF3F2F1))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = SpreadsheetI18n.t(item.label, isEn),
                            color = Color(0xFF107C41),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                item.subItems.isNotEmpty() -> {
                    val isSubOpen = expandedSubMenuIndex == idx
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = SpreadsheetI18n.t(item.label, isEn),
                                    color = textCol,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSubOpen) FontWeight.Bold else FontWeight.Medium
                                )
                                Text(
                                    text = if (isSubOpen) "▾" else "▸",
                                    color = Color(0xFF107C41),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        onClick = {
                            expandedSubMenuIndex = if (isSubOpen) null else idx
                        }
                    )
                    if (isSubOpen) {
                        item.subItems.forEach { (subLabel, subAction) ->
                            DropdownMenuItem(
                                modifier = Modifier.background(subBg),
                                text = {
                                    Text(
                                        text = "   • " + SpreadsheetI18n.t(subLabel, isEn),
                                        color = textCol,
                                        fontSize = 11.sp
                                    )
                                },
                                onClick = {
                                    expandedSubMenuIndex = null
                                    onDismiss()
                                    subAction()
                                }
                            )
                        }
                    }
                }
                else -> {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = SpreadsheetI18n.t(item.label, isEn),
                                color = textCol,
                                fontSize = 11.5.sp
                            )
                        },
                        onClick = {
                            expandedSubMenuIndex = null
                            onDismiss()
                            item.onClick()
                        }
                    )
                }
            }
        }
    }
}

/**
 * [EN] UI/application helper `SpreadsheetRibbonGroup` (spreadsheet ribbon group).
 * [JA] `SpreadsheetRibbonGroup` に対応するUI／アプリケーション補助処理を実行します。
 */
@Composable
fun SpreadsheetRibbonGroup(
    title: String,
    content: @Composable () -> Unit
) {
    val isEn = LocalAppLanguage.current == "en"
    val isDark = LocalDarkTheme.current
    val groupTitleCol = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .heightIn(min = 76.dp)
            .padding(horizontal = 2.dp, vertical = 1.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.heightIn(min = 58.dp)
        ) {
            content()
        }
        Text(
            text = SpreadsheetI18n.t(title, isEn),
            color = groupTitleCol,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * [EN] UI/application helper `SpreadsheetRibbonGroupDivider` (spreadsheet ribbon group divider).
 * [JA] `SpreadsheetRibbonGroupDivider` に対応するUI／アプリケーション補助処理を実行します。
 */
@Composable
fun SpreadsheetRibbonGroupDivider() {
    val isDark = LocalDarkTheme.current
    val isM3 = LocalMaterial3Ui.current
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(0.8.dp)
            .height(64.dp)
            .background(
                when {
                    isDark && isM3 -> Color(0xFF475B73)
                    isDark -> Color(0xFF334155)
                    isM3 -> Color(0xFFCBD5E1)
                    else -> Color(0xFFE1DFDD)
                }
            )
    )
}

/**
 * [EN] UI/application helper `stripLeadingIconWhenSeparateIconShown` (strip leading icon when separate icon shown).
 * [JA] `stripLeadingIconWhenSeparateIconShown` に対応するUI／アプリケーション補助処理を実行します。
 */
internal fun stripLeadingIconWhenSeparateIconShown(label: String, icon: String): String {
    if (icon.isEmpty()) return label
    val trimmed = label.trim()
    val spaceIdx = trimmed.indexOf(' ')
    if (spaceIdx <= 0) return trimmed
    val firstToken = trimmed.substring(0, spaceIdx)
    val rest = trimmed.substring(spaceIdx + 1).trim()
    if (rest.isEmpty()) return trimmed
    val isIconToken = firstToken == icon ||
        firstToken.equals("fx", ignoreCase = true) ||
        firstToken.none { ch ->
            ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' ||
                ch in '\u3040'..'\u30FF' || ch in '\u4E00'..'\u9FFF'
        }
    return if (isIconToken) rest else trimmed
}

/**
 * [EN] UI/application helper `SpreadsheetTallRibbonButton` (spreadsheet tall ribbon button).
 * [JA] `SpreadsheetTallRibbonButton` に対応するUI／アプリケーション補助処理を実行します。
 */
@Composable
fun SpreadsheetTallRibbonButton(
    icon: String,
    label: String,
    hasDropdown: Boolean = false,
    highlight: Boolean = false,
    tag: String = "",
    menuItems: List<SpreadsheetDropdownActionItem> = emptyList(),
    onClick: () -> Unit
) {
    val isEn = LocalAppLanguage.current == "en"
    val isDark = LocalDarkTheme.current
    val isM3 = LocalMaterial3Ui.current
    var menuExpanded by remember { mutableStateOf(false) }
    val baseLabel = stripLeadingIconWhenSeparateIconShown(SpreadsheetI18n.t(label, isEn), icon)
    val showArrow = hasDropdown || menuItems.isNotEmpty()
    val displayLabel = if (showArrow && !baseLabel.endsWith("▾")) "$baseLabel ▾" else baseLabel
    val shape = if (isM3) RoundedCornerShape(8.dp) else RoundedCornerShape(4.dp)
    val bg = when {
        (highlight || menuExpanded) && isDark -> Color(0xFF164E35)
        highlight || menuExpanded -> Color(0xFFDCFCE7)
        isM3 && isDark -> Color(0xFF28374C)
        isM3 -> Color(0xFFF4F7FB)
        else -> Color.Transparent
    }
    val borderCol = when {
        (highlight || menuExpanded) && isDark -> Color(0xFF4ADE80)
        highlight || menuExpanded -> Color(0xFF107C41)
        isM3 && isDark -> Color(0xFF425670)
        isM3 -> Color(0xFFD8E2EE)
        else -> Color.Transparent
    }
    val showBorder = isM3 || highlight || menuExpanded
    val textCol = if (isDark) Color(0xFFF8FAFC) else Color(0xFF1E293B)

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)
                .heightIn(min = 56.dp)
                .widthIn(min = 46.dp, max = 92.dp)
                .clip(shape)
                .background(bg)
                .then(if (showBorder) Modifier.border(0.8.dp, borderCol, shape) else Modifier)
                .clickable {
                    if (menuItems.isNotEmpty()) {
                        menuExpanded = true
                    } else {
                        onClick()
                    }
                }
                .padding(horizontal = 5.dp, vertical = 3.dp)
        ) {
            Text(text = icon, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = displayLabel,
                color = textCol,
                fontSize = 9.5.sp,
                lineHeight = 11.sp,
                textAlign = TextAlign.Center,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        SpreadsheetRibbonDropdownPopup(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            items = menuItems
        )
    }
}

/**
 * [EN] UI/application helper `SpreadsheetSmallIconBtn` (spreadsheet small icon btn).
 * [JA] `SpreadsheetSmallIconBtn` に対応するUI／アプリケーション補助処理を実行します。
 */
@Composable
fun SpreadsheetSmallIconBtn(
    icon: String = "",
    symbol: String = "",
    highlight: Boolean = false,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
    strike: Boolean = false,
    accentBar: Color? = null,
    colorBar: Color? = null,
    tag: String = "",
    menuItems: List<SpreadsheetDropdownActionItem> = emptyList(),
    onClick: () -> Unit
) {
    val isDark = LocalDarkTheme.current
    val isM3 = LocalMaterial3Ui.current
    var menuExpanded by remember { mutableStateOf(false) }
    val displayIcon = icon.ifEmpty { symbol }
    val barColor = accentBar ?: colorBar
    val shape = if (isM3) RoundedCornerShape(6.dp) else RoundedCornerShape(4.dp)
    val bg = when {
        (highlight || menuExpanded) && isDark -> Color(0xFF164E35)
        highlight || menuExpanded -> Color(0xFFDCFCE7)
        isM3 && isDark -> Color(0xFF28374C)
        isM3 -> Color(0xFFF4F7FB)
        else -> Color.Transparent
    }
    val border = when {
        (highlight || menuExpanded) && isDark -> Color(0xFF4ADE80)
        highlight || menuExpanded -> Color(0xFF107C41)
        isM3 && isDark -> Color(0xFF425670)
        isM3 -> Color(0xFFD8E2EE)
        else -> Color.Transparent
    }
    val showBorder = isM3 || highlight || menuExpanded
    val fg = when {
        (highlight || menuExpanded) && !isDark -> Color(0xFF107C41)
        isDark -> Color(0xFFF8FAFC)
        else -> Color(0xFF1E293B)
    }

    Box {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)
                .height(24.dp)
                .widthIn(min = 24.dp)
                .clip(shape)
                .background(bg)
                .then(if (showBorder) Modifier.border(0.8.dp, border, shape) else Modifier)
                .clickable {
                    if (menuItems.isNotEmpty()) {
                        menuExpanded = true
                    } else {
                        onClick()
                    }
                }
                .padding(horizontal = 3.dp)
        ) {
            Text(
                text = displayIcon,
                color = fg,
                fontSize = 11.sp,
                fontWeight = if (bold || highlight) FontWeight.Bold else FontWeight.Medium,
                style = TextStyle(
                    fontStyle = if (italic) androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    textDecoration = when {
                        underline && strike -> androidx.compose.ui.text.style.TextDecoration.combine(
                            listOf(
                                androidx.compose.ui.text.style.TextDecoration.Underline,
                                androidx.compose.ui.text.style.TextDecoration.LineThrough
                            )
                        )
                        underline -> androidx.compose.ui.text.style.TextDecoration.Underline
                        strike -> androidx.compose.ui.text.style.TextDecoration.LineThrough
                        else -> androidx.compose.ui.text.style.TextDecoration.None
                    }
                )
            )
            if (barColor != null) {
                Box(
                    modifier = Modifier
                        .width(13.dp)
                        .height(2.5.dp)
                        .background(barColor)
                )
            }
        }
        SpreadsheetRibbonDropdownPopup(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            items = menuItems
        )
    }
}

/**
 * [EN] UI/application helper `SpreadsheetDropdownBox` (spreadsheet dropdown box).
 * [JA] `SpreadsheetDropdownBox` に対応するUI／アプリケーション補助処理を実行します。
 */
@Composable
fun SpreadsheetDropdownBox(
    label: String = "",
    valueText: String = "",
    widthDp: Int = 80,
    width: androidx.compose.ui.unit.Dp? = null,
    tag: String = "",
    menuItems: List<SpreadsheetDropdownActionItem> = emptyList(),
    onClick: () -> Unit
) {
    val isEn = LocalAppLanguage.current == "en"
    val isDark = LocalDarkTheme.current
    var menuExpanded by remember { mutableStateOf(false) }
    val rawLabel = label.ifEmpty { valueText }
    val displayLabel = SpreadsheetI18n.t(rawLabel, isEn)
    val finalWidth = width ?: widthDp.dp
    val bg = if (isDark) Color(0xFF14181D) else Color(0xFFFFFFFF)
    val border = if (menuExpanded) Color(0xFF107C41) else if (isDark) Color(0xFF334155) else Color(0xFFC8C6C4)
    val fg = if (isDark) Color(0xFFF1F5F9) else Color(0xFF201F1E)

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)
                .width(finalWidth)
                .height(23.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(bg)
                .border(0.8.dp, border, RoundedCornerShape(3.dp))
                .clickable {
                    if (menuItems.isNotEmpty()) {
                        menuExpanded = true
                    } else {
                        onClick()
                    }
                }
                .padding(horizontal = 6.dp)
        ) {
            Text(
                text = displayLabel,
                color = fg,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Text(
                text = "▾",
                color = fg.copy(alpha = 0.7f),
                fontSize = 9.sp
            )
        }
        SpreadsheetRibbonDropdownPopup(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            items = menuItems
        )
    }
}

/**
 * [EN] UI/application helper `SpreadsheetCompactRowButton` (spreadsheet compact row button).
 * [JA] `SpreadsheetCompactRowButton` に対応するUI／アプリケーション補助処理を実行します。
 */
@Composable
fun SpreadsheetCompactRowButton(
    icon: String,
    label: String,
    hasDropdown: Boolean = false,
    highlight: Boolean = false,
    tag: String = "",
    menuItems: List<SpreadsheetDropdownActionItem> = emptyList(),
    onClick: () -> Unit
) {
    val isEn = LocalAppLanguage.current == "en"
    val isDark = LocalDarkTheme.current
    var menuExpanded by remember { mutableStateOf(false) }
    val baseLabel = stripLeadingIconWhenSeparateIconShown(SpreadsheetI18n.t(label, isEn), icon)
    val showArrow = hasDropdown || menuItems.isNotEmpty()
    val displayLabel = if (showArrow && !baseLabel.endsWith("▾")) "$baseLabel ▾" else baseLabel
    val bg = when {
        (highlight || menuExpanded) && isDark -> Color(0xFF163828)
        highlight || menuExpanded -> Color(0xFFE6F4EA)
        else -> Color.Transparent
    }
    val border = if (highlight || menuExpanded) Color(0xFF107C41) else Color.Transparent
    val fg = when {
        (highlight || menuExpanded) && !isDark -> Color(0xFF107C41)
        isDark -> Color(0xFFF1F5F9)
        else -> Color(0xFF201F1E)
    }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)
                .height(22.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(bg)
                .border(0.8.dp, border, RoundedCornerShape(3.dp))
                .clickable {
                    if (menuItems.isNotEmpty()) {
                        menuExpanded = true
                    } else {
                        onClick()
                    }
                }
                .padding(horizontal = 5.dp)
        ) {
            if (icon.isNotEmpty()) {
                Text(text = icon, fontSize = 10.5.sp, color = fg)
            }
            Text(
                text = displayLabel,
                color = fg,
                fontSize = 10.sp,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
        SpreadsheetRibbonDropdownPopup(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            items = menuItems
        )
    }
}

/**
 * [EN] UI/application helper `SpreadsheetRibbonCheckbox` (spreadsheet ribbon checkbox).
 * [JA] `SpreadsheetRibbonCheckbox` に対応するUI／アプリケーション補助処理を実行します。
 */
@Composable
fun SpreadsheetRibbonCheckbox(
    label: String,
    checked: Boolean,
    tag: String = "",
    onClick: () -> Unit
) {
    val isEn = LocalAppLanguage.current == "en"
    val isDark = LocalDarkTheme.current
    val displayLabel = SpreadsheetI18n.t(label, isEn)
    val fg = if (isDark) Color(0xFFF1F5F9) else Color(0xFF201F1E)
    val spreadsheetGreen = Color(0xFF107C41)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier)
            .heightIn(min = 20.dp)
            .clip(RoundedCornerShape(3.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .size(13.dp)
                .clip(RoundedCornerShape(2.5.dp))
                .background(if (checked) spreadsheetGreen else if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF))
                .border(
                    width = 1.dp,
                    color = if (checked) spreadsheetGreen else if (isDark) Color(0xFF64748B) else Color(0xFF8A8886),
                    shape = RoundedCornerShape(2.5.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Text("✓", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            text = displayLabel,
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

