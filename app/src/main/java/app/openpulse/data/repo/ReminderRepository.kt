package app.openpulse.data.repo

import app.openpulse.core.model.Reminder
import app.openpulse.data.db.ReminderDao
import app.openpulse.data.db.toEntity
import app.openpulse.data.db.toModel
import app.openpulse.reminders.ReminderScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReminderRepository(
    private val dao: ReminderDao,
    private val scheduler: ReminderScheduler,
) {
    val reminders: Flow<List<Reminder>> = dao.observeAll().map { l -> l.map { it.toModel() } }

    suspend fun get(id: Long): Reminder? = dao.getById(id)?.toModel()
    suspend fun all(): List<Reminder> = dao.getAll().map { it.toModel() }

    suspend fun save(r: Reminder): Long {
        val id = if (r.id == 0L) dao.insert(r.toEntity()) else { dao.update(r.toEntity()); r.id }
        scheduler.schedule(r.copy(id = id))
        return id
    }

    suspend fun delete(id: Long) {
        dao.deleteById(id)
        scheduler.cancel(id)
    }

    suspend fun replaceAll(list: List<Reminder>) {
        all().forEach { scheduler.cancel(it.id) }
        dao.deleteAll()
        list.forEach { save(it.copy(id = 0)) }
    }

    suspend fun rescheduleAll() = all().forEach { scheduler.schedule(it) }
}
