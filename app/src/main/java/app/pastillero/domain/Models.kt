package app.pastillero.domain

import java.time.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class Medication(
    val id: Long = 0,
    val name: String,
    val dose: String = "",
    val colorIndex: Int = 4,
    val archived: Boolean = false,
    val createdAt: Long,
) {
    init {
        require(id >= 0)
        require(name.isNotBlank() && name == name.trim() && name.length <= 40)
        require(dose.length <= 30)
        require(colorIndex in 0..7)
    }
}

@Serializable
data class Schedule(
    val id: Long = 0,
    val medicationId: Long,
    val minuteOfDay: Int,
    val daysMask: Int = 127,
) {
    init {
        require(id >= 0 && medicationId > 0)
        require(minuteOfDay in 0..1439)
        // Zero retires a schedule without cascading deletion of its history.
        require(daysMask in 0..127)
    }
}

@Serializable
enum class DoseStatus { PENDING, TAKEN, SKIPPED }

@Serializable
data class DoseLog(
    val id: Long = 0,
    val medicationId: Long,
    val scheduleId: Long,
    val nameSnapshot: String,
    val doseSnapshot: String,
    val colorIndexSnapshot: Int,
    @Serializable(with = LocalDateSerializer::class)
    val localDate: LocalDate,
    val minuteOfDay: Int,
    val scheduledAt: Long,
    val status: DoseStatus = DoseStatus.PENDING,
    val takenAt: Long? = null,
    val notifyAt: Long? = scheduledAt,
    val repeatCount: Int = 0,
) {
    init {
        require(id >= 0 && medicationId > 0 && scheduleId > 0)
        require(nameSnapshot.isNotBlank() && nameSnapshot.length <= 40)
        require(doseSnapshot.length <= 30 && colorIndexSnapshot in 0..7)
        require(minuteOfDay in 0..1439 && repeatCount in 0..3)
        require((status == DoseStatus.TAKEN) == (takenAt != null))
        require(status == DoseStatus.PENDING || notifyAt == null)
    }
}

@Serializable
enum class AppTheme { SYSTEM, LIGHT, DARK }

@Serializable
data class AppSettings(val repeatMinutes: Int = 10, val theme: AppTheme = AppTheme.SYSTEM) {
    init { require(repeatMinutes in setOf(5, 10, 15, 30)) }
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}
