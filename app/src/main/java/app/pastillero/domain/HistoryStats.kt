package app.pastillero.domain

import java.time.LocalDate
import kotlin.math.roundToInt

enum class HistoryPoint { TAKEN, PENDING, MISSED, SKIPPED }
data class HistoryDay(val date: LocalDate, val points: List<HistoryPoint>, val taken: Int, val total: Int)

object HistoryStats {
    fun sevenDayPercentage(doses: List<DoseLog>, today: LocalDate): Int? {
        val rows = doses.filter { it.localDate in today.minusDays(6)..today }
        if (rows.isEmpty()) return null
        return (100.0 * rows.count { it.status == DoseStatus.TAKEN } / rows.size).roundToInt()
    }

    fun days(doses: List<DoseLog>, today: LocalDate): List<HistoryDay> =
        doses.filter { it.localDate in today.minusDays(29)..today }
            .groupBy { it.localDate }.toSortedMap(reverseOrder())
            .map { (date, rows) ->
                val points = rows.sortedWith(compareBy<DoseLog> { it.minuteOfDay }.thenBy { it.id })
                    .map {
                        when (it.status) {
                            DoseStatus.TAKEN -> HistoryPoint.TAKEN
                            DoseStatus.SKIPPED -> HistoryPoint.SKIPPED
                            DoseStatus.PENDING -> if (date < today) HistoryPoint.MISSED else HistoryPoint.PENDING
                        }
                    }
                HistoryDay(date, points, rows.count { it.status == DoseStatus.TAKEN }, rows.size)
            }
}
