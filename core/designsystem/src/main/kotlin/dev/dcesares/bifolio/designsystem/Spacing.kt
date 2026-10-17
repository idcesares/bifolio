package dev.dcesares.bifolio.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 4dp base grid (DESIGN.md section 4). */
public object Spacing {
    public val Xs: Dp = 4.dp
    public val S: Dp = 8.dp
    public val M: Dp = 12.dp
    public val L: Dp = 16.dp
    public val Xl: Dp = 24.dp
    public val Xxl: Dp = 32.dp
    public val Xxxl: Dp = 48.dp

    /** Minimum touch target. */
    public val TouchTarget: Dp = 48.dp

    /** Corner radius of code blocks and callouts. */
    public val CodeRadius: Dp = 8.dp

    /** Page margin floor (LAY-05: `max(20dp, 0.06 W)`). */
    public val PageMarginMin: Dp = 20.dp

    /** Fraction of the usable width taken by each outer margin (LAY-05). */
    public const val PAGE_MARGIN_FRACTION: Float = 0.06f

    /** Longest comfortable line in scroll mode, in dp (about 66 characters of the body face, LAY-04 and LAY-09). */
    public val MaxMeasure: Dp = 640.dp
}
