package dev.dcesares.bifolio.feature.reader

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.dcesares.bifolio.designsystem.BifolioButton
import dev.dcesares.bifolio.designsystem.BifolioIcon
import dev.dcesares.bifolio.designsystem.BifolioIcons
import dev.dcesares.bifolio.designsystem.BifolioMotion
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.designsystem.rememberReducedMotion
import dev.dcesares.bifolio.layout.FoldOrientation
import dev.dcesares.bifolio.layout.FoldSpec

internal const val LINK_SHEET_TAG = "external-link-sheet"
internal const val LINK_URL_TAG = "external-link-url"
internal const val LINK_OPEN_TAG = "external-link-open"
internal const val LINK_CANCEL_TAG = "external-link-cancel"
internal const val LINK_NO_APP_TAG = "external-link-no-app"

private const val LINK_MAX_HEIGHT = 0.85f

/** What the confirmation needs to show and do (NAV-08). [noApp] is set after an Open that no app could answer. */
internal class ExternalLinkPrompt(
    val link: ExternalLink,
    val noApp: Boolean,
    val onOpen: () -> Unit,
    val onCancel: () -> Unit,
)

/**
 * Hands [link] to the user's browser or mail app with a plain view intent. Bifolio opens no connection itself: the
 * manifest holds no network permission and there is no web view (PRV-01, PRV-03). The intent is built from an
 * [ExternalLink], which only exists for http, https and mailto, so no other scheme can get here. Returns false when
 * nothing on the device can show the link.
 */
internal fun Context.openExternalLink(link: ExternalLink): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(link.url)).addCategory(Intent.CATEGORY_BROWSABLE)
    if (this !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

/**
 * The link as styled text: the real host in bold `ink`, the rest in `inkMuted`, so it is the part that stands out
 * and nothing is cut (NAV-08). Bold is the cue, not color alone.
 */
internal fun ExternalLink.styled(
    host: SpanStyle,
    rest: SpanStyle,
): AnnotatedString =
    buildAnnotatedString {
        var at = 0
        for (range in hostRanges.sortedBy { it.first }) {
            withStyle(rest) { append(url.substring(at, range.first)) }
            withStyle(host) { append(url.substring(range)) }
            at = range.last + 1
        }
        withStyle(rest) { append(url.substring(at)) }
    }

/**
 * Confirmation before an external link opens (NAV-08). On a window narrower than 840dp, or in tabletop, it is a
 * bottom sheet; from 840dp it is a card in the middle of the window, in the half beside a separating vertical fold
 * so it never straddles the fold (LAY-12). The choice comes from the window width only (LAY-01).
 *
 * It shows the whole address with the host emphasized, wrapped and scrolling when long, never cut. Open and Cancel
 * are 48dp targets; Escape, the scrim and Back cancel. Motion follows the other overlays (MOT-06, MOT-07): chrome
 * springs for the sheet, a plain fade under reduced motion. Toggling [prompt] retargets the transition.
 */
@Composable
internal fun ExternalLinkSheet(
    prompt: ExternalLinkPrompt?,
    modifier: Modifier = Modifier,
    fold: FoldSpec? = null,
) {
    val held = remember { arrayOfNulls<ExternalLinkPrompt>(1) }
    if (prompt != null) held[0] = prompt
    val shown = prompt ?: held[0]
    val visible = prompt != null
    val reduced = rememberReducedMotion()
    val clearance = with(LocalDensity.current) { fold.tocClearance { it.toDp() } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val tabletop = fold != null && fold.isSeparating && fold.orientation == FoldOrientation.Horizontal
        val wide = isSidePane(maxWidth) && !tabletop
        AnimatedVisibility(visible, enter = fadeIn(BifolioMotion.fade()), exit = fadeOut(BifolioMotion.fade())) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = Spacing.SCRIM_ALPHA))
                    .pointerInput(Unit) { detectTapGestures { shown?.onCancel?.invoke() } },
            )
        }
        if (shown == null) return@BoxWithConstraints
        Box(
            Modifier.fillMaxSize().padding(start = clearance.start, top = clearance.top),
            contentAlignment = if (wide) Alignment.Center else Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = if (wide) fadeIn(BifolioMotion.fade()) else panelEnter(false, reduced),
                exit = if (wide) fadeOut(BifolioMotion.fade()) else panelExit(false, reduced),
            ) {
                val available = this@BoxWithConstraints.maxHeight - clearance.top
                LinkCard(shown, wide, available * LINK_MAX_HEIGHT)
            }
        }
    }
}

@Composable
private fun LinkCard(
    prompt: ExternalLinkPrompt,
    wide: Boolean,
    maxHeight: Dp,
) {
    val palette = BifolioTheme.palette
    val type = BifolioTheme.typography
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val title = stringResource(R.string.reader_link_title)
    val shape =
        if (wide) {
            RoundedCornerShape(Spacing.SheetRadius)
        } else {
            RoundedCornerShape(topStart = Spacing.SheetRadius, topEnd = Spacing.SheetRadius)
        }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val sizing =
        if (wide) {
            Modifier.width(Spacing.SidePaneWidth + Spacing.Xxl * 2)
        } else {
            Modifier.widthIn(max = Spacing.SheetMaxWidth).fillMaxWidth()
        }
    Column(
        sizing
            .heightIn(max = maxHeight)
            .border(BorderStroke(Spacing.Rule, palette.rule), shape)
            .clip(shape)
            .background(palette.page)
            .testTag(LINK_SHEET_TAG)
            .semantics { paneTitle = title }
            .onPreviewKeyEvent { event ->
                (event.readerCommand(false) == ReaderCommand.CLOSE).also { if (it) prompt.onCancel() }
            }.focusRequester(focus)
            .focusable()
            // Taps inside the card must not reach the scrim.
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(bottom = if (wide) 0.dp else insets.calculateBottomPadding()),
    ) {
        BasicText(
            text = title,
            modifier = Modifier.padding(start = Spacing.L, end = Spacing.L, top = Spacing.L),
            style = type.headings[1].copy(color = palette.ink),
        )
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.L, vertical = Spacing.M),
            verticalArrangement = Arrangement.spacedBy(Spacing.M),
        ) {
            val address =
                prompt.link.styled(
                    host = SpanStyle(color = palette.ink, fontWeight = FontWeight.Bold),
                    rest = SpanStyle(color = palette.inkMuted),
                )
            BasicText(text = address, modifier = Modifier.testTag(LINK_URL_TAG), style = type.body)
            Caution(stringResource(R.string.reader_link_note), BifolioIcons.Info)
            if (prompt.link.hasUserInfo) {
                val host =
                    prompt.link.hostTexts
                        .firstOrNull()
                        .orEmpty()
                Caution(stringResource(R.string.reader_link_user_info, host), BifolioIcons.Info)
            }
            prompt.link.unicodeHost?.let {
                Caution(
                    stringResource(R.string.reader_link_international, it),
                    BifolioIcons.Info,
                )
            }
            if (prompt.noApp) {
                Box(Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(LINK_NO_APP_TAG)) {
                    Caution(stringResource(R.string.reader_link_no_app), BifolioIcons.Info, strong = true)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.L, vertical = Spacing.S),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = stringResource(R.string.reader_link_cancel),
                modifier =
                    Modifier
                        .testTag(LINK_CANCEL_TAG)
                        .clip(RoundedCornerShape(Spacing.SheetRadius))
                        .clickable(role = Role.Button, onClick = prompt.onCancel)
                        .defaultMinSize(Spacing.TouchTarget, Spacing.TouchTarget)
                        .padding(horizontal = Spacing.M, vertical = Spacing.M),
                style = type.body.copy(color = palette.inkMuted, fontWeight = FontWeight.SemiBold),
            )
            BifolioButton(
                text = stringResource(R.string.reader_link_open),
                onClick = prompt.onOpen,
                icon = BifolioIcons.ExternalLink,
                modifier = Modifier.testTag(LINK_OPEN_TAG),
            )
        }
    }
}

@Composable
private fun Caution(
    text: String,
    icon: ImageVector,
    strong: Boolean = false,
) {
    val palette = BifolioTheme.palette
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
        BifolioIcon(icon, contentDescription = null, tint = palette.inkMuted)
        BasicText(
            text = text,
            modifier = Modifier.weight(1f),
            style =
                BifolioTheme.typography.caption.copy(
                    color = if (strong) palette.ink else palette.inkMuted,
                    fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
                ),
        )
    }
}
