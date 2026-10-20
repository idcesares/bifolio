package dev.dcesares.bifolio.highlight

internal val PYTHON =
    LanguageSpec(
        keywords =
            words(
                "False None True and as assert async await break class continue def del elif else except finally " +
                    "for from global if import in is lambda nonlocal not or pass raise return try while with yield " +
                    "match case self",
            ),
        types = words("int float str bool list dict set tuple bytes object type"),
        lineComments = listOf("#"),
        tripleQuotes = true,
    )

internal val BASH =
    LanguageSpec(
        keywords =
            words(
                "if then else elif fi case esac for select while until do done in function time return exit " +
                    "break continue local export readonly declare unset source alias set shift trap eval exec " +
                    "echo cd true false",
            ),
        lineComments = listOf("#"),
        quotes = "\"'`",
        multilineQuotes = "\"'`",
        identPartExtra = "-",
        capitalTypes = false,
        dollarVars = true,
    )

internal val SQL =
    LanguageSpec(
        keywords =
            words(
                "select from where insert into values update set delete create alter drop table index view " +
                    "database join inner left right outer full cross on as and or not null is in between like " +
                    "group by order having limit offset distinct union all case when then else end primary key " +
                    "foreign references default constraint unique with begin commit rollback exists asc desc " +
                    "true false",
            ),
        types = words("int integer bigint smallint text varchar char boolean date timestamp float double decimal"),
        caseInsensitive = true,
        lineComments = listOf("--"),
        blockOpen = C_OPEN,
        blockClose = C_CLOSE,
        capitalTypes = false,
    )

internal val JSON =
    LanguageSpec(
        keywords = words("true false null"),
        quotes = "\"",
        capitalTypes = false,
        keyMarker = ':',
    )

internal val YAML =
    LanguageSpec(
        keywords = words("true false null yes no on off True False Null"),
        lineComments = listOf("#"),
        identPartExtra = "-.",
        capitalTypes = false,
        keyMarker = ':',
    )

internal val TOML =
    LanguageSpec(
        keywords = words("true false"),
        lineComments = listOf("#"),
        quotes = "\"'",
        identPartExtra = "-.",
        capitalTypes = false,
        keyMarker = '=',
        tripleQuotes = true,
    )

internal val CSS =
    LanguageSpec(
        keywords =
            words(
                "@media @import @font-face @keyframes @supports @charset @layer @container @page @namespace " +
                    "!important",
            ),
        blockOpen = C_OPEN,
        blockClose = C_CLOSE,
        identStartExtra = "@-",
        identPartExtra = "-",
        capitalTypes = false,
        keyMarker = ':',
    )
