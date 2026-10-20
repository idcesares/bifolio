package dev.dcesares.bifolio.highlight

internal val KOTLIN =
    LanguageSpec(
        keywords =
            words(
                "as break class continue do else false for fun if in interface is null object package return super " +
                    "this throw true try typealias typeof val var when while by catch constructor delegate dynamic " +
                    "field file finally get import init param property receiver set setparam value where abstract " +
                    "actual annotation companion const crossinline data enum expect external final infix inline " +
                    "inner internal lateinit noinline open operator out override private protected public reified " +
                    "sealed suspend tailrec vararg",
            ),
        types = words("Int Long Short Byte Float Double Boolean Char String Unit Any Nothing Array List Map Set"),
        lineComments = listOf(C_LINE),
        blockOpen = C_OPEN,
        blockClose = C_CLOSE,
        tripleQuotes = true,
    )

internal val JAVA =
    LanguageSpec(
        keywords =
            words(
                "abstract assert break case catch class const continue default do else enum extends final finally " +
                    "for goto if implements import instanceof interface native new package private protected public " +
                    "return static strictfp super switch synchronized this throw throws transient try volatile while " +
                    "true false null var record sealed permits yield",
            ),
        types = words("int long short byte float double boolean char void String Object"),
        lineComments = listOf(C_LINE),
        blockOpen = C_OPEN,
        blockClose = C_CLOSE,
        tripleQuotes = true,
    )

private const val JS_KEYWORDS =
    "break case catch class const continue debugger default delete do else export extends finally for " +
        "function if import in instanceof let new of return super switch this throw try typeof var void " +
        "while with yield async await static get set true false null undefined"

internal val JAVASCRIPT =
    LanguageSpec(
        keywords = words(JS_KEYWORDS),
        lineComments = listOf(C_LINE),
        blockOpen = C_OPEN,
        blockClose = C_CLOSE,
        quotes = "\"'`",
        multilineQuotes = "`",
        identStartExtra = "$",
        identPartExtra = "$",
    )

internal val TYPESCRIPT =
    LanguageSpec(
        keywords =
            words(
                "$JS_KEYWORDS interface type enum implements namespace declare abstract readonly public private " +
                    "protected as is keyof infer satisfies override module",
            ),
        types = words("string number boolean any unknown never object symbol bigint void"),
        lineComments = listOf(C_LINE),
        blockOpen = C_OPEN,
        blockClose = C_CLOSE,
        quotes = "\"'`",
        multilineQuotes = "`",
        identStartExtra = "$",
        identPartExtra = "$",
    )

private const val C_KEYWORDS =
    "auto break case const continue default do else enum extern for goto if inline register restrict " +
        "return sizeof static struct switch typedef union volatile while #include #define #ifdef #ifndef " +
        "#endif #if #else #elif #pragma #undef #error NULL true false"

private const val C_TYPES = "int long short char float double void signed unsigned size_t bool _Bool uint8_t uint32_t"

internal val C_LANG =
    LanguageSpec(
        keywords = words(C_KEYWORDS),
        types = words(C_TYPES),
        lineComments = listOf(C_LINE),
        blockOpen = C_OPEN,
        blockClose = C_CLOSE,
        identStartExtra = "#",
    )

internal val CPP =
    LanguageSpec(
        keywords =
            words(
                "$C_KEYWORDS class namespace template typename public private protected virtual override final " +
                    "new delete this using try catch throw nullptr constexpr noexcept explicit friend operator " +
                    "static_cast dynamic_cast reinterpret_cast const_cast mutable auto decltype",
            ),
        types = words("$C_TYPES string vector map set wchar_t"),
        lineComments = listOf(C_LINE),
        blockOpen = C_OPEN,
        blockClose = C_CLOSE,
        identStartExtra = "#",
    )

internal val GO =
    LanguageSpec(
        keywords =
            words(
                "break case chan const continue default defer else fallthrough for func go goto if import " +
                    "interface map package range return select struct switch type var true false nil iota",
            ),
        types =
            words(
                "int int8 int16 int32 int64 uint uint8 uint16 uint32 uint64 uintptr float32 float64 " +
                    "complex64 complex128 string bool byte rune error any",
            ),
        lineComments = listOf(C_LINE),
        blockOpen = C_OPEN,
        blockClose = C_CLOSE,
        quotes = "\"'`",
        multilineQuotes = "`",
    )

internal val RUST =
    LanguageSpec(
        keywords =
            words(
                "as async await break const continue crate dyn else enum extern false fn for if impl in let loop " +
                    "match mod move mut pub ref return self Self static struct super trait true type unsafe use " +
                    "where while macro_rules",
            ),
        types = words("i8 i16 i32 i64 i128 isize u8 u16 u32 u64 u128 usize f32 f64 bool char str String"),
        lineComments = listOf(C_LINE),
        blockOpen = C_OPEN,
        blockClose = C_CLOSE,
        quotes = "\"",
        multilineQuotes = "\"",
    )

internal val SWIFT =
    LanguageSpec(
        keywords =
            words(
                "associatedtype class deinit enum extension fileprivate func import init inout internal let open " +
                    "operator private protocol public static struct subscript typealias var break case continue " +
                    "default defer do else fallthrough for guard if in repeat return switch where while as catch " +
                    "false is nil rethrows super self throw throws true try async await actor some any mutating " +
                    "override final lazy weak",
            ),
        types = words("Int Double Float Bool String Character Array Dictionary Set Optional Any Void"),
        lineComments = listOf(C_LINE),
        blockOpen = C_OPEN,
        blockClose = C_CLOSE,
        quotes = "\"",
        tripleQuotes = true,
    )
