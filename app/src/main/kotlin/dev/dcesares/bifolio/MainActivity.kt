package dev.dcesares.bifolio

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.core.content.IntentCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.dcesares.bifolio.data.AppearanceProfile
import dev.dcesares.bifolio.data.ThemeChoice
import dev.dcesares.bifolio.designsystem.BifolioTheme
import dev.dcesares.bifolio.designsystem.LocalReducedMotionOverride
import dev.dcesares.bifolio.designsystem.ReadingThemeId
import dev.dcesares.bifolio.designsystem.defaultThemeId
import dev.dcesares.bifolio.designsystem.rememberDynamicPalette
import dev.dcesares.bifolio.feature.library.LibraryNotice
import dev.dcesares.bifolio.feature.library.LibraryRoute
import dev.dcesares.bifolio.feature.library.LibraryViewModel
import dev.dcesares.bifolio.feature.reader.ContainerTransform
import dev.dcesares.bifolio.feature.reader.ContentResolverDocumentReader
import dev.dcesares.bifolio.feature.reader.FailureReason
import dev.dcesares.bifolio.feature.reader.OpenRequest
import dev.dcesares.bifolio.feature.reader.PerfLog
import dev.dcesares.bifolio.feature.reader.ReaderRoute
import dev.dcesares.bifolio.feature.reader.ReaderUiState
import dev.dcesares.bifolio.feature.reader.ReaderViewModel
import dev.dcesares.bifolio.feature.reader.toReadingFont
import dev.dcesares.bifolio.feature.settings.AboutScreen
import dev.dcesares.bifolio.feature.settings.AppearanceSheet
import dev.dcesares.bifolio.feature.settings.SheetActions
import dev.dcesares.bifolio.feature.settings.SheetState
import kotlinx.coroutines.launch

private const val KEY_URI = "open.uri"
private const val KEY_MIME = "open.mime"
private const val KEY_EXTERNAL = "open.external"

private fun PackageManager.versionNameOf(packageName: String): String =
    getPackageInfo(packageName, 0).versionName.orEmpty()

/** Library and reader behind one Activity. Opens from the picker, recents and VIEW or SEND intents (DOC-01). */
class MainActivity : ComponentActivity() {
    private val app get() = application as BifolioApp

    /** Debug builds write the PRF-04 timings to logcat under the tag below. Release builds log nothing. */
    private fun perfLog(): PerfLog =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            PerfLog { name, millis -> Log.i("BifolioPerf", "$name=${millis}ms") }
        } else {
            PerfLog.None
        }

    private val reader: ReaderViewModel by viewModels {
        viewModelFactory {
            initializer {
                ReaderViewModel(
                    reader = ContentResolverDocumentReader(contentResolver),
                    repository = app.recents,
                    preferences = app.readingPreferences,
                    perf = perfLog(),
                    folders = app.folderGrants,
                )
            }
        }
    }
    private val library: LibraryViewModel by viewModels {
        viewModelFactory { initializer { LibraryViewModel(app.recents) } }
    }

    private var openUri: String? = null
    private var openMime: String? = null

    /** True when the document came from another app, so a refusal closes this screen instead of showing a card. */
    private var openedExternally = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT),
        )
        if (savedInstanceState != null) {
            openUri = savedInstanceState.getString(KEY_URI)
            openMime = savedInstanceState.getString(KEY_MIME)
            openedExternally = savedInstanceState.getBoolean(KEY_EXTERNAL)
            // After process death the view model is empty: reopen, and the anchor comes back from storage (ANC-07).
            val uri = openUri
            if (uri != null && reader.state.value is ReaderUiState.Idle) {
                reader.open(OpenRequest(uri, openMime, persistGrant = false))
            }
        } else {
            handleIntent(intent)
        }
        setContent { Content() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_URI, openUri)
        outState.putString(KEY_MIME, openMime)
        outState.putBoolean(KEY_EXTERNAL, openedExternally)
    }

    override fun onStop() {
        reader.flush()
        super.onStop()
    }

    @Composable
    private fun Content() {
        val state by reader.state.collectAsStateWithLifecycle()
        val chromeVisible by reader.chromeVisible.collectAsStateWithLifecycle()
        val profile by reader.appearance.collectAsStateWithLifecycle()
        // MOT-04: where the reader grows from; null for the picker and external opens.
        var origin by remember { mutableStateOf<Rect?>(null) }
        // UI-11: About sits over the library; opening a document from elsewhere closes it.
        var showAbout by rememberSaveable { mutableStateOf(false) }
        ProfiledTheme(profile, library = state is ReaderUiState.Idle) {
            // UI-04: immersive reading hides the system bars; they come back with the chrome and in the library.
            LaunchedEffect(state is ReaderUiState.Idle, chromeVisible) {
                val bars = WindowCompat.getInsetsController(window, window.decorView)
                bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (state is ReaderUiState.Idle || chromeVisible) {
                    bars.show(WindowInsetsCompat.Type.systemBars())
                } else {
                    bars.hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            LaunchedEffect(state) {
                val failed = state as? ReaderUiState.Failed ?: return@LaunchedEffect
                when {
                    failed.reason == FailureReason.NotMarkdown && openedExternally -> {
                        Toast
                            .makeText(
                                this@MainActivity,
                                R.string.open_failed_not_markdown,
                                Toast.LENGTH_LONG,
                            ).show()
                        finish()
                    }
                    failed.reason == FailureReason.Unreadable && !openedExternally -> {
                        openUri?.let(library::markUnavailable)
                        library.showNotice(LibraryNotice.Unreadable)
                        closeDocument()
                    }
                }
            }
            ContainerTransform(
                detailVisible = state !is ReaderUiState.Idle,
                origin = origin,
                onBack = ::closeDocument,
                list = {
                    LibraryRoute(
                        viewModel = library,
                        onDocumentPicked = { uri ->
                            origin = null
                            open(uri, mime = null, persist = true, external = false)
                        },
                        onOpenRecent = { recent ->
                            open(Uri.parse(recent.uri), mime = null, persist = false, external = false)
                        },
                        onRecentBounds = { origin = it },
                        onOpenAbout = { showAbout = true },
                    )
                },
                detail = {
                    ReaderRoute(
                        viewModel = reader,
                        onBack = ::closeDocument,
                        appearanceSheet = { onDismiss -> AppearanceSheetHost(onDismiss) },
                    )
                },
            )
            if (showAbout && state is ReaderUiState.Idle) {
                val folders by app.folderGrants.trees.collectAsStateWithLifecycle(emptyList())
                AboutScreen(
                    versionName = packageManager.versionNameOf(packageName),
                    folders = folders,
                    onRevokeFolder = { tree -> lifecycleScope.launch { app.folderGrants.revoke(tree) } },
                    onBack = { showAbout = false },
                )
            }
        }
    }

    @Composable
    private fun AppearanceSheetHost(onDismiss: () -> Unit) {
        val profile by reader.appearance.collectAsStateWithLifecycle()
        val mode by reader.mode.collectAsStateWithLifecycle()
        val volumeKeys by reader.volumeKeysTurn.collectAsStateWithLifecycle()
        val fitting by reader.fittingColumns.collectAsStateWithLifecycle()
        AppearanceSheet(
            state = SheetState(profile, mode, volumeKeys, fitting),
            actions =
                SheetActions(
                    onProfile = reader::updateAppearance,
                    onMode = reader::setMode,
                    onVolumeKeys = reader::setVolumeKeysTurn,
                    onDismiss = onDismiss,
                ),
        )
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW ->
                intent.data?.let { uri ->
                    val persist = intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
                    open(uri, intent.type, persist, external = true)
                }
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                // TODO(spec gap): SEND with only EXTRA_TEXT carries no file. Not handled until the spec decides.
                if (uri != null) {
                    open(uri, intent.type, persist = false, external = true)
                } else {
                    Toast.makeText(this, R.string.open_failed_no_file, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun open(
        uri: Uri,
        mime: String?,
        persist: Boolean,
        external: Boolean,
    ) {
        openUri = uri.toString()
        openMime = mime
        openedExternally = external
        reader.open(OpenRequest(uri.toString(), mime, persist))
    }

    private fun closeDocument() {
        openUri = null
        openMime = null
        openedExternally = false
        reader.close()
    }

    private companion object {
        const val TRANSPARENT = 0x00000000
    }
}

private fun ThemeChoice.toReadingTheme(): ReadingThemeId =
    when (this) {
        ThemeChoice.PAPER -> ReadingThemeId.Paper
        ThemeChoice.SEPIA -> ReadingThemeId.Sepia
        ThemeChoice.DUSK -> ReadingThemeId.Dusk
        ThemeChoice.NIGHT -> ReadingThemeId.Night
    }

/** Applies the user's appearance profile (UI-10) to the theme tokens and the reduce-motion switch. */
@Composable
private fun ProfiledTheme(
    profile: AppearanceProfile,
    library: Boolean,
    content: @Composable () -> Unit,
) {
    val theme = profile.theme?.toReadingTheme() ?: defaultThemeId()
    // TYP-09: dynamic color only for the library, only when the user turned it on.
    val dynamic = if (library && profile.dynamicColor) rememberDynamicPalette() else null
    CompositionLocalProvider(LocalReducedMotionOverride provides profile.reduceMotion.takeIf { it }) {
        BifolioTheme(
            theme,
            profile.bodySp.toFloat(),
            profile.lineHeight,
            profile.font.toReadingFont(),
            dynamic,
            content,
        )
    }
}
