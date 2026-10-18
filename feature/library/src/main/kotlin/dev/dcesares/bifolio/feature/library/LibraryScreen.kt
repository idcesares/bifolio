package dev.dcesares.bifolio.feature.library

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.dcesares.bifolio.data.RecentDocument
import dev.dcesares.bifolio.designsystem.BifolioButton
import dev.dcesares.bifolio.designsystem.BifolioIcon
import dev.dcesares.bifolio.designsystem.BifolioIconButton
import dev.dcesares.bifolio.designsystem.BifolioIcons
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.designsystem.safeDrawingPadding

/** Something the library has to tell the reader after an action failed. */
public enum class LibraryNotice { Unreadable, }

/** What the library shows. [recents] is null while the first read from storage is pending. */
public data class LibraryUiState(
    val recents: List<RecentDocument>? = null,
    val notice: LibraryNotice? = null,
)

/** UI-01: Open action, recents with title, file name, last read time and progress, removal, empty state. */
@Composable
public fun LibraryScreen(
    state: LibraryUiState,
    nowMillis: Long,
    onOpenDocument: () -> Unit,
    onOpenRecent: (RecentDocument) -> Unit,
    onRemoveRecent: (RecentDocument) -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
    onRecentBounds: (Rect) -> Unit = {},
) {
    val palette = BifolioTheme.palette
    val type = BifolioTheme.typography
    val recents = state.recents
    Box(
        modifier = modifier.fillMaxSize().background(palette.page),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier.widthIn(max = Spacing.MaxMeasure).fillMaxSize(),
            contentPadding = safeDrawingPadding(horizontal = Spacing.PageMarginMin, vertical = 0.dp),
        ) {
            item(key = "title") {
                BasicText(
                    text = stringResource(R.string.library_title),
                    style = type.heading(1),
                    modifier = Modifier.padding(top = Spacing.Xl, bottom = Spacing.L),
                )
            }
            val notice = state.notice
            if (notice != null) {
                item(key = "notice") { NoticeCard(notice, onDismissNotice) }
            }
            if (recents != null && recents.isEmpty()) {
                item(key = "empty") { EmptyState(onOpenDocument) }
            } else if (recents != null) {
                item(key = "open") {
                    BifolioButton(
                        text = stringResource(R.string.library_open_action),
                        onClick = onOpenDocument,
                        icon = BifolioIcons.FolderOpen,
                        modifier = Modifier.padding(bottom = Spacing.L),
                    )
                }
                item(key = "heading") {
                    BasicText(
                        text = stringResource(R.string.library_recents_heading),
                        style = type.caption.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(top = Spacing.S, bottom = Spacing.Xs),
                    )
                }
                items(recents, key = { it.uri }) { recent ->
                    RecentRow(
                        recent,
                        nowMillis,
                        onOpen = { bounds ->
                            onRecentBounds(bounds)
                            onOpenRecent(recent)
                        },
                        onRemove = { onRemoveRecent(recent) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onOpenDocument: () -> Unit) {
    val palette = BifolioTheme.palette
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.Xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.L),
    ) {
        BifolioIcon(BifolioIcons.BookOpen, contentDescription = null, tint = palette.inkMuted)
        BasicText(
            text = stringResource(R.string.library_empty_message),
            style = BifolioTheme.typography.body.copy(textAlign = TextAlign.Center),
        )
        BifolioButton(
            text = stringResource(R.string.library_open_action),
            onClick = onOpenDocument,
            icon = BifolioIcons.FolderOpen,
        )
    }
}

@Composable
private fun NoticeCard(
    notice: LibraryNotice,
    onDismiss: () -> Unit,
) {
    val palette = BifolioTheme.palette
    val message =
        when (notice) {
            LibraryNotice.Unreadable -> stringResource(R.string.library_notice_unreadable)
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.L)
                .clip(RoundedCornerShape(Spacing.CodeRadius))
                .background(palette.codeSurface)
                .clickable(role = Role.Button, onClick = onDismiss)
                .defaultMinSize(minHeight = Spacing.TouchTarget)
                .padding(Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        BifolioIcon(BifolioIcons.Info, contentDescription = null, tint = palette.accent)
        Column(Modifier.weight(1f)) {
            BasicText(message, style = BifolioTheme.typography.caption.copy(color = palette.ink))
            BasicText(
                stringResource(R.string.library_notice_dismiss),
                style = BifolioTheme.typography.caption.copy(color = palette.accent, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun RecentRow(
    recent: RecentDocument,
    nowMillis: Long,
    onOpen: (Rect) -> Unit,
    onRemove: () -> Unit,
) {
    val palette = BifolioTheme.palette
    val type = BifolioTheme.typography
    val whenRead =
        DateUtils
            .getRelativeTimeSpanString(
                recent.lastOpenedMillis,
                nowMillis,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE,
            ).toString()
    var bounds = Rect.Zero
    val unavailable = recent.unavailable
    val status =
        if (unavailable) {
            stringResource(R.string.library_unavailable)
        } else {
            stringResource(R.string.library_progress, (recent.progress * PERCENT).toInt())
        }
    val meta =
        stringResource(
            R.string.library_meta_separator,
            stringResource(R.string.library_last_read, whenRead),
            status,
        )
    // Dimmed by moving ink to the muted tone, which keeps the contrast of the palette (UI-16).
    val titleColor = if (unavailable) palette.inkMuted else palette.ink
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.TouchTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Spacing.CodeRadius))
                    .onGloballyPositioned { bounds = it.boundsInRoot() }
                    .clickable(role = Role.Button) { onOpen(bounds) }
                    .padding(vertical = Spacing.M),
        ) {
            BasicText(
                text = recent.title,
                style = type.body.copy(fontWeight = FontWeight.SemiBold, color = titleColor),
            )
            BasicText(recent.fileName, style = type.caption)
            BasicText(meta, style = type.caption)
            if (recent.preview.isNotEmpty()) {
                BasicText(
                    text = recent.preview,
                    style = type.caption.copy(color = if (unavailable) palette.inkMuted else palette.ink),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .padding(top = Spacing.S)
                    .fillMaxWidth()
                    .height(Spacing.Hairline)
                    .background(palette.rule),
            ) {
                if (!unavailable) {
                    Box(
                        Modifier
                            .fillMaxWidth(recent.progress.coerceIn(0f, 1f))
                            .height(Spacing.Hairline)
                            .background(palette.accent),
                    )
                }
            }
        }
        BifolioIconButton(
            icon = BifolioIcons.Trash,
            description = stringResource(R.string.library_remove_description, recent.title),
            onClick = onRemove,
        )
    }
}

private const val PERCENT = 100
