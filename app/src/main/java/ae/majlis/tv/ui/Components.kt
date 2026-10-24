@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
package ae.majlis.tv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.*
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Google-TV style pivot: the focused item always lands at the same place ([leadDp] from the start edge),
 * so the row slides under a fixed focus position instead of focus walking off the edge. Layout-direction aware.
 */
class PivotSpec(private val leadPx: Float, private val rtlAxis: Boolean) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val target = if (rtlAxis) containerSize - leadPx - size else leadPx
        return offset - target
    }
}
@Composable fun rememberPivot(lead: Dp, rtlAxis: Boolean): BringIntoViewSpec {
    val px = with(LocalDensity.current) { lead.toPx() }
    return remember(px, rtlAxis) { PivotSpec(px, rtlAxis) }
}

/** One horizontal shelf: title, then a lazy row with a fixed pivot and focus restoration. */
@Composable
fun Shelf(title: String, modifier: Modifier = Modifier, gap: Dp = 20.dp, content: LazyListScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = C.Sand, modifier = Modifier.padding(start = SafeH, bottom = 2.dp))
        CompositionLocalProvider(LocalBringIntoViewSpec provides rememberPivot(SafeH, rtlAxis = true)) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().focusRestorer().focusGroup(),
                contentPadding = PaddingValues(horizontal = SafeH, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
                content = content,
            )
        }
    }
}

val TileShape = RoundedCornerShape(12.dp)

/** Standard TV card (Material 3 TV Card focus scale + ring; no custom bounce). */
@Composable
fun TvCard(
    onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null,
    width: Dp = 196.dp, height: Dp = 110.dp, highlight: Boolean = false, content: @Composable BoxScope.() -> Unit,
) {
    Card(
        onClick = onClick, onLongClick = onLongClick, modifier = modifier.size(width, height),
        shape = CardDefaults.shape(TileShape),
        colors = CardDefaults.colors(containerColor = C.Raised, focusedContainerColor = C.Raised),
        scale = CardDefaults.scale(focusedScale = 1.1f),
        border = CardDefaults.border(
            border = if (highlight) Border(BorderStroke(3.dp, C.Gold), shape = TileShape) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, if (highlight) C.Gold else C.Ring), shape = TileShape),
        ),
        glow = CardDefaults.glow(),
    ) { Box(Modifier.fillMaxSize(), content = content) }
}

/** Card + caption below (StandardCardContainer pattern), caption height fixed so rows never shift. */
@Composable
fun CaptionedCard(
    caption: String, onClick: () -> Unit, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null,
    width: Dp = 196.dp, height: Dp = 110.dp, highlight: Boolean = false, content: @Composable BoxScope.() -> Unit,
) {
    Column(modifier.width(width)) {
        TvCard(onClick, Modifier, onLongClick, width, height, highlight, content)
        Text(caption, style = MaterialTheme.typography.labelMedium, color = C.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp).height(20.dp).fillMaxWidth())
    }
}

/** Coil image sized to its slot (memory + disk cache from the app ImageLoader). Never blurred. */
@Composable
fun Img(model: Any?, w: Dp, h: Dp, modifier: Modifier = Modifier, scale: ContentScale = ContentScale.Crop, onLowRes: ((Boolean) -> Unit)? = null, minPx: Int = 0) {
    val ctx = LocalContext.current; val d = LocalDensity.current
    val wp = with(d) { w.roundToPx() }; val hp = with(d) { h.roundToPx() }
    val req = remember(model, wp, hp) { ImageRequest.Builder(ctx).data(model).size(wp, hp).crossfade(120).build() }
    AsyncImage(req, null, modifier, contentScale = scale,
        onSuccess = { s -> if (onLowRes != null) onLowRes(s.result.drawable.intrinsicWidth < minPx) },
        onError = { onLowRes?.invoke(true) })
}

/** Pill button (tv-material Button) in brand colours. */
@Composable
fun Pill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false, enabled: Boolean = true) {
    Button(
        onClick = onClick, modifier = modifier, enabled = enabled,
        scale = ButtonDefaults.scale(focusedScale = 1.06f),
        colors = ButtonDefaults.colors(
            containerColor = if (selected) C.Gold.copy(alpha = .22f) else C.Raised2, contentColor = if (selected) C.Gold else C.Text,
            focusedContainerColor = C.Text, focusedContentColor = C.Bg,
        ),
        border = ButtonDefaults.border(border = if (selected) Border(BorderStroke(1.5.dp, C.Gold)) else Border.None),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 9.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1) }
}

/** Focusable read-only block: lets the D-pad scroll long text lists; shows a quiet ring when focused. */
@Composable
fun ReadRow(modifier: Modifier = Modifier, onClick: () -> Unit = {}, content: @Composable () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = C.Raised2, contentColor = C.Text, focusedContentColor = C.Text),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, C.Gold.copy(alpha = .7f)), shape = RoundedCornerShape(10.dp)))) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) { content() }
    }
}

/** Full-screen overlay used for routes: own window → traps focus; Back closes and focus returns to the opener. */
@Composable
fun Overlay(onBack: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        AlcodeTheme { Box(Modifier.fillMaxSize().background(C.Bg)) { content() } }
    }
}

@Composable
fun Panel(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        AlcodeTheme {
            Column(Modifier.widthIn(min = 420.dp, max = 640.dp).clip(RoundedCornerShape(18.dp)).background(C.Raised).padding(28.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = C.Text)
                Sadu(Modifier.padding(top = 8.dp, bottom = 18.dp))
                content()
            }
        }
    }
}

/** List of choices; the current one is marked and receives focus first. */
@Composable
fun ChoicePanel(title: String, options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val fr = remember { FocusRequester() }
    Panel(title, onDismiss) {
        androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(options.size) { i -> val (v, label) = options[i]
                ListItem(selected = v == current, onClick = { onPick(v); onDismiss() }, headlineContent = { Text(label, style = MaterialTheme.typography.titleSmall) },
                    trailingContent = { if (v == current) Text("✓", color = C.Gold) },
                    modifier = if (v == current || (i == 0 && options.none { it.first == current })) Modifier.focusRequester(fr) else Modifier)
            }
        }
    }
    LaunchedEffect(Unit) { try { fr.requestFocus() } catch (_: Exception) {} }
}

/** Text entry: the field takes focus so the TV keyboard opens; Save / Cancel are real buttons. */
@Composable
fun TextPanel(title: String, fields: List<Triple<String, String, Boolean>>, onSave: (List<String>) -> Unit, onDismiss: () -> Unit, note: String = "") {
    val vals = remember { mutableStateListOf(*fields.map { it.second }.toTypedArray()) }
    val first = remember { FocusRequester() }
    Panel(title, onDismiss) {
        fields.forEachIndexed { i, (label, _, secret) ->
            Text(label, style = MaterialTheme.typography.labelLarge, color = C.Dim, modifier = Modifier.padding(bottom = 6.dp))
            var focused by remember { mutableStateOf(false) }
            BasicTextField(
                value = vals[i], onValueChange = { vals[i] = it }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = C.Text, textAlign = TextAlign.Start),
                cursorBrush = SolidColor(C.Gold),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text, imeAction = if (i == fields.lastIndex) ImeAction.Done else ImeAction.Next),
                keyboardActions = KeyboardActions(onDone = { onSave(vals.toList()); onDismiss() }),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (focused) C.Raised2 else C.Bg).then(if (i == 0) Modifier.focusRequester(first) else Modifier)
                    .onFocusChanged { focused = it.isFocused }.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = C.Dim, modifier = Modifier.padding(bottom = 14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Pill("حفظ", { onSave(vals.toList()); onDismiss() })
            Pill("إلغاء", onDismiss)
        }
    }
    LaunchedEffect(Unit) { try { first.requestFocus() } catch (_: Exception) {} }
}

