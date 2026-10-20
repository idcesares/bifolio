package dev.dcesares.bifolio.highlight

/**
 * In-house tokenizer (ADR-0007). Stateless and thread-safe; rule sets are shared immutable values.
 * Every scanner is a single forward pass, so pathological input cannot cause super-linear time.
 */
public class RuleBasedHighlighter : CodeHighlighter {
    override fun tokenize(
        source: CharSequence,
        language: String?,
    ): List<TokenSpan> {
        if (source.isEmpty()) return emptyList()
        val out = SpanSink()
        when (val lang = canonical(language)) {
            null -> out.add(0, source.length, TokenKind.PLAIN)
            "html" -> HtmlScanner(source, out).run()
            "diff" -> scanDiff(source, out)
            "markdown" -> MarkdownScanner(source, out).run()
            else -> GenericScanner(SPECS.getValue(lang), source, out).run()
        }
        return out.result()
    }

    override fun supports(language: String?): Boolean = canonical(language) != null

    private fun canonical(language: String?): String? {
        if (language == null) return null
        var name = language.trim().lowercase()
        val cut = name.indexOfFirst { it.isWhitespace() || it == '{' || it == ',' }
        if (cut >= 0) name = name.substring(0, cut)
        name = name.removePrefix("language-").removePrefix("lang-")
        val resolved = ALIASES[name] ?: name
        return if (resolved in SPECS || resolved in SPECIAL) resolved else null
    }

    private companion object {
        val SPECIAL = setOf("html", "diff", "markdown")

        val SPECS: Map<String, LanguageSpec> =
            mapOf(
                "kotlin" to KOTLIN,
                "java" to JAVA,
                "python" to PYTHON,
                "javascript" to JAVASCRIPT,
                "typescript" to TYPESCRIPT,
                "json" to JSON,
                "yaml" to YAML,
                "toml" to TOML,
                "bash" to BASH,
                "sql" to SQL,
                "css" to CSS,
                "c" to C_LANG,
                "cpp" to CPP,
                "go" to GO,
                "rust" to RUST,
                "swift" to SWIFT,
            )

        val ALIASES: Map<String, String> =
            mapOf(
                "kt" to "kotlin",
                "kts" to "kotlin",
                "py" to "python",
                "python3" to "python",
                "js" to "javascript",
                "jsx" to "javascript",
                "mjs" to "javascript",
                "node" to "javascript",
                "ts" to "typescript",
                "tsx" to "typescript",
                "jsonc" to "json",
                "yml" to "yaml",
                "sh" to "bash",
                "shell" to "bash",
                "zsh" to "bash",
                "console" to "bash",
                "htm" to "html",
                "xml" to "html",
                "svg" to "html",
                "md" to "markdown",
                "h" to "c",
                "c++" to "cpp",
                "cc" to "cpp",
                "cxx" to "cpp",
                "hpp" to "cpp",
                "golang" to "go",
                "rs" to "rust",
                "patch" to "diff",
                "udiff" to "diff",
                "postgresql" to "sql",
                "mysql" to "sql",
                "sqlite" to "sql",
            )
    }
}
