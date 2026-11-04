package app.openpulse.data.repo

import app.openpulse.core.model.Measurement
import app.openpulse.data.db.MeasurementDao
import app.openpulse.data.db.toEntity
import app.openpulse.data.db.toModel
import app.openpulse.data.prefs.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for measurements. In demo mode only sample data is visible; in normal
 * mode only real data is visible - the two are never mixed.
 */
class MeasurementRepository(
    private val dao: MeasurementDao,
    private val settings: SettingsRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val measurements: Flow<List<Measurement>> = settings.settings
        .map { it.demoMode }
        .distinctUntilChanged()
        .flatMapLatest { demo -> dao.observeAll(demo) }
        .map { list -> list.mapNotNull { it.toModel() } }

    suspend fun get(id: Long): Measurement? = dao.getById(id)?.toModel()

    /** Always inserts a new row; never replaces an entry that happens to share the same date. */
    suspend fun add(m: Measurement): Long {
        val now = System.currentTimeMillis()
        val demo = settings.current().demoMode
        return dao.insert(m.copy(id = 0, isDemo = demo, createdAt = now, updatedAt = now).toEntity())
    }

    suspend fun update(m: Measurement) {
        require(m.id != 0L) { "Cannot update an unsaved measurement" }
        val existing = dao.getById(m.id) ?: error("Measurement ${m.id} no longer exists")
        dao.update(m.copy(isDemo = existing.isDemo, createdAt = existing.createdAt, updatedAt = System.currentTimeMillis()).toEntity())
    }

    suspend fun delete(id: Long) { dao.deleteById(id) }

    suspend fun allReal(): List<Measurement> = dao.getAll(demo = false).mapNotNull { it.toModel() }
    suspend fun allDemo(): List<Measurement> = dao.getAll(demo = true).mapNotNull { it.toModel() }

    suspend fun insertImported(list: List<Measurement>): Int =
        dao.insertAll(list.map { it.copy(id = 0, isDemo = false).toEntity() }).size

    suspend fun replaceAllReal(list: List<Measurement>) =
        dao.replaceAllReal(list.map { it.copy(id = 0, isDemo = false).toEntity() })

    suspend fun insertDemo(list: List<Measurement>) {
        dao.deleteAllDemo()
        dao.insertAll(list.map { it.copy(id = 0, isDemo = true).toEntity() })
    }

    suspend fun deleteAllReal() = dao.deleteAllReal()
    suspend fun deleteAllDemo() = dao.deleteAllDemo()
    suspend fun countReal() = dao.count(false)
}
