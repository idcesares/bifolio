package dev.dcesares.bifolio.highlight

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HighlighterTest {
    private val h: CodeHighlighter = RuleBasedHighlighter()

    private fun kinds(
        src: String,
        lang: String?,
    ): List<Pair<String, TokenKind>> = h.tokenize(src, lang).map { src.substring(it.start, it.end) to it.kind }

    private fun assertHas(
        src: String,
        lang: String?,
        text: String,
        kind: TokenKind,
    ) {
        assertTrue(
            kinds(src, lang).any { it.first.trim() == text && it.second == kind },
            "expected '$text' as $kind in ${kinds(src, lang)}",
        )
    }

    @Test
    fun unknownAndNullLanguageArePlain() {
        assertEquals(listOf(TokenSpan(0, 8, TokenKind.PLAIN)), h.tokenize("fun x(1)", null))
        assertEquals(listOf(TokenSpan(0, 8, TokenKind.PLAIN)), h.tokenize("fun x(1)", "brainfuck"))
    }

    @Test
    fun emptySourceHasNoSpans() {
        assertTrue(h.tokenize("", "kotlin").isEmpty())
    }

    @Test
    fun kotlinBasics() {
        val src = "fun main() { val s = \"hi\" // c\n  println(42) }"
        assertHas(src, "kotlin", "fun", TokenKind.KEYWORD)
        assertHas(src, "kotlin", "main", TokenKind.FUNCTION)
        assertHas(src, "kotlin", "\"hi\"", TokenKind.STRING)
        assertHas(src, "kotlin", "// c", TokenKind.COMMENT)
        assertHas(src, "kotlin", "42", TokenKind.NUMBER)
        assertHas("val x: String", "kotlin", "String", TokenKind.TYPE)
        assertHas("a == b", "kotlin", "==", TokenKind.OPERATOR)
        assertHas("a(b)", "kotlin", "(", TokenKind.PUNCTUATION)
    }

    @Test
    fun blockCommentAndEscapes() {
        assertHas("a /* x \n y */ b", "java", "/* x \n y */", TokenKind.COMMENT)
        assertHas("\"a\\\"b\" c", "java", "\"a\\\"b\"", TokenKind.STRING)
    }

    @Test
    fun pythonTripleQuotesAndHash() {
        assertHas("x = \"\"\"a\nb\"\"\"", "python", "\"\"\"a\nb\"\"\"", TokenKind.STRING)
        assertHas("def f(): # hi", "py", "# hi", TokenKind.COMMENT)
        assertHas("def f(): pass", "python", "def", TokenKind.KEYWORD)
    }

    @Test
    fun jsonKeysAndValues() {
        val src = "{\"a\": [1, true, null, \"x\"]}"
        assertHas(src, "json", "\"a\"", TokenKind.TYPE)
        assertHas(src, "json", "\"x\"", TokenKind.STRING)
        assertHas(src, "json", "true", TokenKind.KEYWORD)
        assertHas(src, "json", "1", TokenKind.NUMBER)
    }

    @Test
    fun yamlAndToml() {
        assertHas("name: bifolio # c", "yml", "name", TokenKind.TYPE)
        assertHas("name: bifolio # c", "yaml", "# c", TokenKind.COMMENT)
        assertTrue(kinds("a: http://x", "yaml").none { it.first == "http" && it.second == TokenKind.TYPE })
        assertHas("[core]\nkey = \"v\"", "toml", "key", TokenKind.TYPE)
        assertHas("key = 5", "toml", "5", TokenKind.NUMBER)
    }

    @Test
    fun bashSqlCss() {
        assertHas("if [ -n \"\$X\" ]; then echo \$HOME; fi", "sh", "if", TokenKind.KEYWORD)
        assertHas("echo \$HOME", "zsh", "\$HOME", TokenKind.TYPE)
        assertHas("SELECT a FROM t -- x", "sql", "SELECT", TokenKind.KEYWORD)
        assertHas("select a from t", "sql", "select", TokenKind.KEYWORD)
        assertHas("SELECT a FROM t -- x", "sql", "-- x", TokenKind.COMMENT)
        assertHas("a { color: red; }", "css", "color", TokenKind.TYPE)
        assertHas("@media (x) {}", "css", "@media", TokenKind.KEYWORD)
        assertHas("a{width:10px}", "css", "10px", TokenKind.NUMBER)
    }

    @Test
    fun htmlTagsAndAttributes() {
        val src = "<!-- c --><a href=\"u\" id=x>t</a>"
        assertHas(src, "htm", "<!-- c -->", TokenKind.COMMENT)
        assertHas(src, "html", "a", TokenKind.KEYWORD)
        assertHas(src, "html", "href", TokenKind.TYPE)
        assertHas(src, "html", "\"u\"", TokenKind.STRING)
        assertHas(src, "html", "t", TokenKind.PLAIN)
    }

    @Test
    fun diffLines() {
        val src = "--- a\n+++ b\n@@ -1 +1 @@\n-old\n+new\n ctx\n"
        assertHas(src, "diff", "--- a\n+++ b", TokenKind.COMMENT)
        assertHas(src, "diff", "@@ -1 +1 @@", TokenKind.TYPE)
        assertHas(src, "diff", "-old", TokenKind.KEYWORD)
        assertHas(src, "diff", "+new", TokenKind.STRING)
        assertHas(src, "diff", "ctx", TokenKind.PLAIN)
    }

    @Test
    fun markdownLines() {
        val src = "# Title\n> q\n- item `code` x\n```\nraw\n```\n"
        assertHas(src, "markdown", "# Title", TokenKind.KEYWORD)
        assertHas(src, "markdown", "> q", TokenKind.COMMENT)
        assertHas(src, "markdown", "`code`", TokenKind.STRING)
        assertHas(src, "markdown", "raw", TokenKind.STRING)
    }

    @Test
    fun cLikeLanguages() {
        assertHas("#include <x.h>\nint main() {}", "c", "#include", TokenKind.KEYWORD)
        assertHas("std::vector<int> v;", "c++", "int", TokenKind.TYPE)
        assertHas("func main() { x := 'a' }", "go", "func", TokenKind.KEYWORD)
        assertHas("fn main() -> &'a str {}", "rs", "fn", TokenKind.KEYWORD)
        assertHas("let x: Option<'a>", "rust", "Option", TokenKind.TYPE)
        assertHas("guard let x = y else { return }", "swift", "guard", TokenKind.KEYWORD)
        assertHas("const x = `a\nb`", "js", "`a\nb`", TokenKind.STRING)
        assertHas("let x: number = 1", "ts", "number", TokenKind.TYPE)
        assertHas("interface A {}", "typescript", "interface", TokenKind.KEYWORD)
    }

    @Test
    fun infoStringAndCaseAreNormalised() {
        assertHas("val x", " Kotlin title=a", "val", TokenKind.KEYWORD)
        assertHas("val x", "kt{1}", "val", TokenKind.KEYWORD)
        assertHas("val x", "language-kotlin", "val", TokenKind.KEYWORD)
    }

    @Test
    fun everySupportedLanguageResolves() {
        val names =
            listOf(
                "kotlin",
                "java",
                "python",
                "javascript",
                "typescript",
                "json",
                "yaml",
                "toml",
                "bash",
                "shell",
                "sql",
                "html",
                "css",
                "markdown",
                "c",
                "cpp",
                "go",
                "rust",
                "swift",
                "diff",
                "kt",
                "js",
                "ts",
                "sh",
                "zsh",
                "yml",
                "c++",
                "py",
                "rs",
                "htm",
                "md",
            )
        for (n in names) assertTrue(h.supports(n), n)
        assertTrue(!h.supports("cobol") && !h.supports(null))
    }
}
