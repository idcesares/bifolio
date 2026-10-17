import dev.dcesares.bifolio.policy.PolicyChecks
import dev.dcesares.bifolio.policy.Violation
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

// Root-project plugin: registers `checkPolicy` (PRV-01 to PRV-03, QA-04, ADR-0001).

fun Element.childText(tag: String): String? =
    (getElementsByTagName(tag).item(0) as? Element)?.textContent?.trim()

fun parsePom(file: File): Element {
    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    return factory.newDocumentBuilder().parse(file).documentElement
}

fun Project.pomFile(group: String, name: String, version: String): File? = try {
    val detached = configurations.detachedConfiguration(dependencies.create("$group:$name:$version@pom"))
    detached.isTransitive = false
    detached.singleFile
} catch (_: Exception) {
    null
}

/** License entries for a coordinate, following parent POMs when the child declares none. */
fun Project.licensesOf(group: String, name: String, version: String, depth: Int = 0): List<Pair<String?, String?>> {
    val pom = pomFile(group, name, version) ?: return emptyList()
    val root = parsePom(pom)
    val declared = root.getElementsByTagName("license")
    val own = (0 until declared.length).map { i ->
        val e = declared.item(i) as Element
        e.childText("name") to e.childText("url")
    }
    if (own.isNotEmpty() || depth >= 4) return own
    val parent = root.getElementsByTagName("parent").item(0) as? Element ?: return own
    val pg = parent.childText("groupId") ?: return own
    val pa = parent.childText("artifactId") ?: return own
    val pv = parent.childText("version") ?: return own
    return licensesOf(pg, pa, pv, depth + 1)
}

val shippedClasspaths = listOf("debugRuntimeClasspath", "runtimeClasspath")

// Each project resolves its own shipped classpaths: Gradle forbids resolving another project's
// configurations without its lock.
allprojects {
    val dumpFile = layout.buildDirectory.file("policy/coordinates.txt")
    tasks.register("policyCoordinates") {
        outputs.file(dumpFile)
        doLast {
            val found = sortedSetOf<String>()
            shippedClasspaths.mapNotNull { configurations.findByName(it) }
                .filter { it.isCanBeResolved }
                .forEach { conf ->
                    conf.incoming.resolutionResult.allComponents.forEach { c ->
                        val id = c.moduleVersion
                        if (id != null && c.id !is ProjectComponentIdentifier) {
                            found += "${id.group}:${id.name}:${id.version}"
                        }
                    }
                }
            dumpFile.get().asFile.apply {
                parentFile.mkdirs()
                writeText(found.joinToString("\n"))
            }
        }
    }
}

tasks.register("checkPolicy") {
    group = "verification"
    description = "Fails on network permissions, WebView use, banned SDKs and licenses outside the ADR-0001 allow list."

    // Merged manifest of the app catches permissions injected by dependencies.
    dependsOn(":app:processDebugMainManifest")
    dependsOn(allprojects.map { it.tasks.named("policyCoordinates") })

    doLast {
        val violations = mutableListOf<Violation>()

        rootProject.allprojects.forEach { p ->
            p.fileTree(p.projectDir) {
                include("src/**/*.kt", "src/**/*.java", "src/**/*.xml", "src/**/*.kts")
            }.forEach { f ->
                val rel = f.relativeTo(rootProject.projectDir).path
                val text = f.readText()
                if (f.name == "AndroidManifest.xml") {
                    violations += PolicyChecks.manifestViolations(rel, text)
                } else if (f.extension == "kt" || f.extension == "java") {
                    violations += PolicyChecks.sourceViolations(rel, text)
                }
            }
        }

        rootProject.fileTree(rootProject.project(":app").layout.buildDirectory) {
            include("intermediates/merged_manifest*/**/AndroidManifest.xml")
        }.forEach { f ->
            violations += PolicyChecks.manifestViolations(f.relativeTo(rootProject.projectDir).path, f.readText())
        }

        val exceptionsFile = rootProject.file("config/policy/license-exceptions.txt")
        val exceptions = if (exceptionsFile.exists()) {
            exceptionsFile.readLines().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toSet()
        } else {
            emptySet()
        }

        val coordinates = sortedSetOf<String>()
        rootProject.allprojects.forEach { p ->
            val dump = p.layout.buildDirectory.file("policy/coordinates.txt").get().asFile
            if (dump.exists()) coordinates += dump.readLines().filter { it.isNotBlank() }
        }

        violations += PolicyChecks.dependencyGroupViolations(coordinates)
        coordinates.forEach { coord ->
            val (g, n, v) = coord.split(":")
            violations += PolicyChecks.licenseViolations(coord, rootProject.licensesOf(g, n, v), exceptions)
        }

        logger.lifecycle("checkPolicy: scanned ${coordinates.size} shipped dependencies")
        if (violations.isNotEmpty()) {
            throw GradleException(
                "checkPolicy found ${violations.size} violation(s):\n" + violations.joinToString("\n") { "  - $it" },
            )
        }
    }
}
