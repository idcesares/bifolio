package dev.dcesares.bifolio.layout

/** What a tap on a [TextTarget] asks for. */
public enum class TargetKind {
    /** A link; [TextTarget.value] is its destination as written. */
    LINK,

    /** A footnote marker (SYN-09); [TextTarget.value] is the footnote label. */
    NOTE,
}

/**
 * A tappable run of a rendered text: `text[start until end]` is a link or a footnote marker (UI-09, NAV-07). The
 * run is a subset of the drawn characters, so a surface can find its rectangles from the text layout without
 * changing any metric.
 */
public class TextTarget(
    public val start: Int,
    public val end: Int,
    public val kind: TargetKind,
    public val value: String,
)
