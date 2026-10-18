package dev.dcesares.bifolio.layout

import kotlin.random.Random

/** Seeded geometry generators for property tests: equal columns, columns of unequal width (LAY-12), or either. */
internal object RandomGeometries {
    private const val MIN_LINES = 12
    private const val MAX_LINES = 30
    private const val MIN_WIDTH = 160
    private const val MAX_WIDTH = 400

    fun equal(random: Random): Geometry =
        testGeometry(
            lines = random.nextInt(MIN_LINES, MAX_LINES + 1),
            columns = random.nextInt(1, 4),
            columnWidth = random.nextInt(MIN_WIDTH, MAX_WIDTH + 1),
            metrics = metrics(random),
        )

    /** Two columns of random widths, or a real fold geometry from [GeometryCalculator] (columns differ by the fold). */
    fun mixed(random: Random): Geometry {
        val metrics = metrics(random)
        val lines = random.nextInt(MIN_LINES, MAX_LINES + 1)
        if (random.nextBoolean()) {
            val first = random.nextInt(MIN_WIDTH, MAX_WIDTH + 1)
            val second = random.nextInt(MIN_WIDTH, MAX_WIDTH + 1)
            return testGeometry(lines = lines, widths = listOf(first, second), metrics = metrics)
        }
        val charW = random.nextInt(6, 13)
        val width = random.nextInt(1000, 2400)
        val foldAt = random.nextInt(width / 3, 2 * width / 3)
        val fold =
            FoldSpec(
                left = foldAt,
                top = 0,
                right = foldAt + random.nextInt(0, 60),
                bottom = 2000,
                isSeparating = true,
                orientation = FoldOrientation.Vertical,
                halfOpened = random.nextBoolean(),
                occlusion = FoldOcclusion.None,
            )
        val spec =
            WindowSpec(
                widthPx = width,
                heightPx = lines * 20 + 2 * 60,
                density = 1f,
                charWidthPx = charW.toFloat(),
                metrics = metrics,
                fold = fold,
            )
        return GeometryCalculator.compute(spec)
    }

    /** Equal columns, unequal columns and fold geometries in equal parts. */
    fun any(random: Random): Geometry = if (random.nextInt(3) == 0) equal(random) else mixed(random)

    private fun metrics(random: Random) =
        TEST_METRICS.copy(blockGap = random.nextInt(0, 31), innerGap = random.nextInt(0, 9))
}
