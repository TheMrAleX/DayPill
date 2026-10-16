package app.pastillero.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import app.pastillero.domain.DoseStatus
import java.time.LocalDate

class DatabaseConverters {
    @TypeConverter fun dateToString(value: LocalDate): String = value.toString()
    @TypeConverter fun stringToDate(value: String): LocalDate = LocalDate.parse(value)
    @TypeConverter fun statusToString(value: DoseStatus): String = value.name
    @TypeConverter fun stringToStatus(value: String): DoseStatus = DoseStatus.valueOf(value)
}

@Database(
    entities = [MedicationEntity::class, ScheduleEntity::class, DoseLogEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class PastilleroDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
}
