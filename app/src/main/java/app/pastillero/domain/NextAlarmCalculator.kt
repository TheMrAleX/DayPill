package app.pastillero.domain

import java.time.Clock
import java.time.Duration

data class NextAlarm(val doseId: Long, val triggerAt: Long)
data class AlarmCalculation(val next: NextAlarm?, val expiredDoseIds: List<Long>)

object NextAlarmCalculator {
    fun calculate(doses: List<DoseLog>, clock: Clock): AlarmCalculation {
        val now = clock.millis()
        val cutoff = now - Duration.ofHours(12).toMillis()
        val pending = doses.filter { it.status == DoseStatus.PENDING && it.notifyAt != null }
        val expired = pending.filter { it.notifyAt!! <= cutoff }
        val next = pending.filter { it.notifyAt!! > cutoff }
            .minWithOrNull(compareBy<DoseLog> { it.notifyAt }.thenBy { it.id })
        return AlarmCalculation(
            next = next?.let { NextAlarm(it.id, maxOf(now, it.notifyAt!!)) },
            expiredDoseIds = expired.map { it.id },
        )
    }
}
