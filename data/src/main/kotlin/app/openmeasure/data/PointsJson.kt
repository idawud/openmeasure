package app.openmeasure.data

/** A world-space point in metres. */
data class WorldPoint(val x: Float, val y: Float, val z: Float)

/** Hand-rolled `[[x,y,z],...]` codec (org.json is stubbed in JVM unit tests and we add no dependency). */
object PointsJson {
    fun encode(points: List<WorldPoint>): String =
        points.joinToString(prefix = "[", postfix = "]", separator = ",") { "[${it.x},${it.y},${it.z}]" }

    /** Lenient: malformed input yields an empty list rather than crashing the History screen. */
    fun decode(json: String): List<WorldPoint> {
        val out = ArrayList<WorldPoint>()
        val body = json.trim()
        if (!body.startsWith("[") || !body.endsWith("]")) return emptyList()
        val inner = body.substring(1, body.length - 1)
        var i = 0
        while (i < inner.length) {
            val open = inner.indexOf('[', i)
            if (open < 0) break
            val close = inner.indexOf(']', open)
            if (close < 0) return emptyList()
            val nums = inner.substring(open + 1, close).split(',').map { it.trim().toFloatOrNull() ?: return emptyList() }
            if (nums.size != 3 || nums.any { !it.isFinite() }) return emptyList()
            out += WorldPoint(nums[0], nums[1], nums[2])
            i = close + 1
        }
        return out
    }
}
