package app.openpulse.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    @Query("SELECT * FROM measurements WHERE isDemo = :demo ORDER BY timestamp DESC, id DESC")
    fun observeAll(demo: Boolean): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurements WHERE isDemo = :demo ORDER BY timestamp ASC, id ASC")
    suspend fun getAll(demo: Boolean): List<MeasurementEntity>

    @Query("SELECT * FROM measurements WHERE id = :id")
    suspend fun getById(id: Long): MeasurementEntity?

    /** ABORT: an insert can never silently replace an existing row. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: MeasurementEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<MeasurementEntity>): List<Long>

    @Update
    suspend fun update(entity: MeasurementEntity): Int

    @Query("DELETE FROM measurements WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM measurements WHERE isDemo = 0")
    suspend fun deleteAllReal(): Int

    @Query("DELETE FROM measurements WHERE isDemo = 1")
    suspend fun deleteAllDemo(): Int

    @Query("SELECT COUNT(*) FROM measurements WHERE isDemo = :demo")
    suspend fun count(demo: Boolean): Int

    @Transaction
    suspend fun replaceAllReal(entities: List<MeasurementEntity>) {
        deleteAllReal()
        insertAll(entities)
    }
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY hour, minute, id")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders ORDER BY hour, minute, id")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ReminderEntity): Long

    @Update
    suspend fun update(entity: ReminderEntity): Int

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM reminders")
    suspend fun deleteAll(): Int
}
