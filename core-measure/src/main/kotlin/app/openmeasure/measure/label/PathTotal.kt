package app.openmeasure.measure.label

/** Combines per-segment labels into the path total. */
object PathTotal {
    const val MAX_POINTS = 50

    /**
     * Total length in metres, only when there are >= 2 segments and EVERY one is a displayable
     * [LabelContent.Length]; otherwise null (never a number the scorer refused to show).
     */
    fun of(segments: List<LabelContent>): Double? {
        if (segments.size < 2) return null
        var sum = 0.0
        for (s in segments) {
            if (s !is LabelContent.Length) return null
            sum += s.meters
        }
        return sum.takeIf { it.isFinite() }
    }
}
