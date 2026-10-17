package dev.dcesares.bifolio.policy

/** One policy breach, rendered as `rule: location: detail` in the build output. */
data class Violation(val rule: String, val location: String, val detail: String) {
    override fun toString() = "$rule: $location: $detail"
}

/** Pure checks behind `checkPolicy`; free of Gradle types so they are unit-testable. */
object PolicyChecks {
    private val networkPermissions = Regex(
        """android\.permission\.(INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE|CHANGE_NETWORK_STATE|CHANGE_WIFI_STATE)""",
    )
    private val webKit = Regex("""\bandroid\.webkit\b|\bWebView\b""")

    /** Dependency groups banned by PRV-02: analytics, crash reporting, ads, Play Services. */
    val bannedGroups: List<String> = listOf(
        "com.google.android.gms",
        "com.google.firebase",
        "com.google.android.play",
        "com.crashlytics.sdk.android",
        "io.sentry",
        "com.facebook.android",
        "com.google.android.ads",
        "com.applovin",
        "com.mixpanel.android",
        "com.amplitude",
        "com.flurry",
        "com.adjust.sdk",
        "com.appsflyer",
        "org.matomo",
    )

    private val allowedLicenses = setOf(
        "Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause", "ISC",
        "Unicode-3.0", "Unicode-DFS", "OFL-1.1",
    )

    fun manifestViolations(path: String, xml: String): List<Violation> =
        networkPermissions.findAll(xml).map {
            Violation("PRV-01", path, "network permission ${it.value}")
        }.distinct().toList()

    fun sourceViolations(path: String, text: String): List<Violation> =
        if (webKit.containsMatchIn(text)) {
            listOf(Violation("PRV-03", path, "references android.webkit / WebView"))
        } else {
            emptyList()
        }

    fun dependencyGroupViolations(coordinates: Collection<String>): List<Violation> =
        coordinates.filter { c -> bannedGroups.any { c.startsWith("$it:") || c.startsWith("$it.") } }
            .map { Violation("PRV-02", it, "banned dependency group") }

    /** Maps a POM license name or URL to an SPDX id, or null when unrecognised. */
    fun normalizeLicense(name: String?, url: String?): String? {
        val n = (name ?: "").lowercase()
        val u = (url ?: "").lowercase()
        return when {
            "apache" in n && "2" in n || "apache.org/licenses/license-2.0" in u -> "Apache-2.0"
            n == "mit" || "mit license" in n || "opensource.org/licenses/mit" in u -> "MIT"
            "bsd" in n && "2" in n && "clause" in n -> "BSD-2-Clause"
            "bsd" in n && ("3" in n || "new" in n || "revised" in n) -> "BSD-3-Clause"
            n == "isc" || "isc license" in n -> "ISC"
            "unicode" in n -> "Unicode-3.0"
            "ofl" in n || "open font license" in n -> "OFL-1.1"
            "eclipse public license" in n && "2" in n -> "EPL-2.0"
            else -> null
        }
    }

    /**
     * ADR-0001 allow list. [exceptions] holds `group:name` coordinates that have a dedicated ADR
     * (EPL-2.0) or a verified metadata gap, listed in `config/policy/license-exceptions.txt`.
     */
    fun licenseViolations(
        coordinate: String,
        licenses: List<Pair<String?, String?>>,
        exceptions: Set<String>,
    ): List<Violation> {
        if (exceptions.any { coordinate == it || coordinate.startsWith("$it:") }) return emptyList()
        if (licenses.isEmpty()) {
            return listOf(Violation("ADR-0001", coordinate, "no license declared in POM"))
        }
        val spdx = licenses.map { (n, u) -> normalizeLicense(n, u) }
        // Multi-licensed artifacts pass when at least one offered license is allowed.
        return if (spdx.any { it in allowedLicenses }) {
            emptyList()
        } else {
            val shown = licenses.joinToString { (n, u) -> n ?: u ?: "unknown" }
            listOf(Violation("ADR-0001", coordinate, "license not on allow list: $shown"))
        }
    }
}
