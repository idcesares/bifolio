package dev.dcesares.bifolio.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import org.junit.Rule
import org.junit.Test

private const val PACKAGE = "dev.dcesares.bifolio"
private const val ITERATIONS = 5

/** PRF cold start of the library screen, with and without the baseline profile. */
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    private fun cold(mode: CompilationMode) =
        rule.measureRepeated(
            packageName = PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = mode,
            iterations = ITERATIONS,
            startupMode = StartupMode.COLD,
        ) {
            pressHome()
            startActivityAndWait()
        }

    @Test
    fun coldStartNoCompilation() = cold(CompilationMode.None())

    @Test
    fun coldStartBaselineProfile() = cold(CompilationMode.Partial())
}
