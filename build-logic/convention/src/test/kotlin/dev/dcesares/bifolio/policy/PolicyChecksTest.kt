package dev.dcesares.bifolio.policy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PolicyChecksTest {
    @Test
    fun cleanManifestPasses() {
        assertTrue(PolicyChecks.manifestViolations("m", "<manifest><application/></manifest>").isEmpty())
    }

    @Test
    fun internetPermissionFails() {
        val xml = """<uses-permission android:name="android.permission.INTERNET"/>"""
        assertEquals("PRV-01", PolicyChecks.manifestViolations("m", xml).single().rule)
    }

    @Test
    fun networkStatePermissionFails() {
        val xml = """<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>"""
        assertEquals(1, PolicyChecks.manifestViolations("m", xml).size)
    }

    @Test
    fun webViewImportFails() {
        val v = PolicyChecks.sourceViolations("A.kt", "import android.webkit.WebView")
        assertEquals("PRV-03", v.single().rule)
    }

    @Test
    fun webViewClassNameFails() {
        assertEquals(1, PolicyChecks.sourceViolations("A.kt", "val w = WebView(ctx)").size)
    }

    @Test
    fun plainSourcePasses() {
        assertTrue(PolicyChecks.sourceViolations("A.kt", "val x = 1").isEmpty())
    }

    @Test
    fun playServicesAndFirebaseFail() {
        val v = PolicyChecks.dependencyGroupViolations(
            listOf(
                "com.google.android.gms:play-services-base:1",
                "com.google.firebase:firebase-analytics:2",
                "androidx.core:core:1",
            ),
        )
        assertEquals(2, v.size)
    }

    @Test
    fun materialIsNotBanned() {
        assertTrue(PolicyChecks.dependencyGroupViolations(listOf("com.google.android.material:material:1")).isEmpty())
    }

    @Test
    fun licenseNormalization() {
        assertEquals("Apache-2.0", PolicyChecks.normalizeLicense("The Apache Software License, Version 2.0", null))
        assertEquals("Apache-2.0", PolicyChecks.normalizeLicense(null, "https://www.apache.org/licenses/LICENSE-2.0.txt"))
        assertEquals("MIT", PolicyChecks.normalizeLicense("MIT License", null))
        assertNull(PolicyChecks.normalizeLicense("GNU General Public License v3", null))
    }

    @Test
    fun gplFailsAllowList() {
        val v = PolicyChecks.licenseViolations("g:a:1", listOf("GNU General Public License v3" to null), emptySet())
        assertEquals("ADR-0001", v.single().rule)
    }

    @Test
    fun lgplFailsAllowList() {
        val v = PolicyChecks.licenseViolations("g:a:1", listOf("GNU Lesser General Public License" to null), emptySet())
        assertEquals(1, v.size)
    }

    @Test
    fun missingLicenseFails() {
        assertEquals(1, PolicyChecks.licenseViolations("g:a:1", emptyList(), emptySet()).size)
    }

    @Test
    fun exceptionListedArtifactPasses() {
        val v = PolicyChecks.licenseViolations("g:a:1", listOf("Eclipse Public License 2.0" to null), setOf("g:a"))
        assertTrue(v.isEmpty())
    }

    @Test
    fun eplWithoutExceptionFails() {
        val v = PolicyChecks.licenseViolations("g:a:1", listOf("Eclipse Public License 2.0" to null), emptySet())
        assertEquals(1, v.size)
    }

    @Test
    fun dualLicenseWithAllowedOptionPasses() {
        val v = PolicyChecks.licenseViolations(
            "g:a:1",
            listOf("Eclipse Public License 2.0" to null, "Apache License 2.0" to null),
            emptySet(),
        )
        assertTrue(v.isEmpty())
    }
}
