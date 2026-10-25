package dev.dcesares.bifolio.feature.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.dcesares.bifolio.designsystem.BifolioIconButton
import dev.dcesares.bifolio.designsystem.BifolioIcons
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.Spacing
import dev.dcesares.bifolio.designsystem.safeDrawingPadding

private const val LICENSES_ASSET = "open_source_licenses.txt"

private fun readLicenses(
    context: Context,
    fallback: String,
): String =
    runCatching {
        context.assets
            .open(LICENSES_ASSET)
            .bufferedReader()
            .use { it.readText() }
    }.getOrDefault(fallback)

/** UI-11: version, license, source and contact text, and the open source licenses. */
@Composable
public fun AboutScreen(
    versionName: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val palette = BifolioTheme.palette
    val type = BifolioTheme.typography
    val context = LocalContext.current
    val unavailable = stringResource(R.string.about_licenses_unavailable)
    val licenses = remember { readLicenses(context, unavailable) }
    Box(modifier.fillMaxSize().background(palette.page), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = Spacing.MaxMeasure)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(safeDrawingPadding(horizontal = Spacing.PageMarginMin, vertical = 0.dp)),
            verticalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            Row(Modifier.padding(top = Spacing.M), verticalAlignment = Alignment.CenterVertically) {
                BifolioIconButton(
                    icon = BifolioIcons.ChevronLeft,
                    description = stringResource(R.string.about_back),
                    onClick = onBack,
                )
                BasicText(
                    text = stringResource(R.string.about_title),
                    style = type.heading(1),
                    modifier = Modifier.semantics { heading() },
                )
            }
            BasicText(stringResource(R.string.about_version, versionName), style = type.body)
            BasicText(stringResource(R.string.about_license), style = type.body)
            BasicText(stringResource(R.string.about_source), style = type.body)
            BasicText(stringResource(R.string.about_contact), style = type.body)
            BasicText(
                text = stringResource(R.string.about_licenses_heading),
                style = type.heading(2),
                modifier = Modifier.padding(top = Spacing.L).semantics { heading() },
            )
            BasicText(licenses, style = type.caption, modifier = Modifier.padding(bottom = Spacing.Xl))
        }
    }
}
