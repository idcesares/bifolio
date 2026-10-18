package dev.dcesares.bifolio.layout

/** Reading posture of the window (POS-01 to POS-04). Standard covers POS-01, POS-02, POS-05 and POS-06. */
public enum class Posture { Standard, Book, Tabletop }

/** Pure posture rules. Only the fold that intersects the window counts (LAY-01); window size never does. */
public object PostureClassifier {
    public fun classify(window: WindowSpec): Posture =
        classify(window.fold?.takeIf { it.intersects(window.widthPx, window.heightPx) })

    /** [fold] must already be known to intersect the window, or be null. */
    public fun classify(fold: FoldSpec?): Posture =
        when {
            fold == null -> Posture.Standard
            fold.orientation == FoldOrientation.Vertical && (fold.halfOpened || fold.isSeparating) -> Posture.Book
            fold.orientation == FoldOrientation.Horizontal && fold.halfOpened -> Posture.Tabletop
            else -> Posture.Standard
        }
}
