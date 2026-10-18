package dev.dcesares.bifolio.model

/**
 * Reading speed (UI-06): 230 words per minute until [CALIBRATION_TURNS] page turns have been seen, then the
 * reader's own pace, clamped to 120..450. A turn that took longer than [MAX_TURN_MILLIS] was a pause, and one
 * faster than any reader can read was a skim or a jump; neither says anything about reading speed.
 */
public class ReadingPace {
    private var words = 0L
    private var millis = 0L
    private var turns = 0

    public var wordsPerMinute: Int = DEFAULT_WPM
        private set

    /** The reader spent [millis] on a spread holding [words] words before turning. */
    public fun recordTurn(
        words: Int,
        millis: Long,
    ) {
        if (words <= 0 || millis <= 0 || millis > MAX_TURN_MILLIS) return
        if (words * MILLIS_PER_MINUTE / millis > MAX_PLAUSIBLE_WPM) return
        this.words += words
        this.millis += millis
        turns++
        if (turns >= CALIBRATION_TURNS) {
            wordsPerMinute = (this.words * MILLIS_PER_MINUTE / this.millis).toInt().coerceIn(MIN_WPM, MAX_WPM)
        }
    }

    /** Whole minutes to read [words], rounded up. */
    public fun minutesFor(words: Int): Int = (words + wordsPerMinute - 1) / wordsPerMinute

    public companion object {
        public const val DEFAULT_WPM: Int = 230
        public const val MIN_WPM: Int = 120
        public const val MAX_WPM: Int = 450
        public const val CALIBRATION_TURNS: Int = 10
        private const val MAX_PLAUSIBLE_WPM = 1_000
        private const val MAX_TURN_MILLIS = 5 * 60_000L
        private const val MILLIS_PER_MINUTE = 60_000L
    }
}
