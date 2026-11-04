package app.openpulse.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MeasurementEntity::class, ReminderEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class OpenPulseDatabase : RoomDatabase() {
    abstract fun measurementDao(): MeasurementDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        const val NAME = "open_pulse.db"

        fun build(context: Context): OpenPulseDatabase =
            Room.databaseBuilder(context.applicationContext, OpenPulseDatabase::class.java, NAME)
                // Never use destructive migration: losing health records is not acceptable.
                .build()
    }
}
