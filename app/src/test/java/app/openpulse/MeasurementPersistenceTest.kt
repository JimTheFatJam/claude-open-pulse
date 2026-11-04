package app.openpulse

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openpulse.core.model.Measurement
import app.openpulse.core.model.MeasurementType
import app.openpulse.core.model.Reminder
import app.openpulse.data.db.OpenPulseDatabase
import app.openpulse.data.db.toEntity
import app.openpulse.data.db.toModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class MeasurementPersistenceTest {
    private lateinit var context: Context
    private val dbName = "persistence-test.db"

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After fun tearDown() { context.deleteDatabase(dbName) }

    private fun open() = Room.databaseBuilder(context, OpenPulseDatabase::class.java, dbName).allowMainThreadQueries().build()

    private fun bp(ts: Long, sys: Double, dia: Double) = Measurement(
        type = MeasurementType.BLOOD_PRESSURE, value = sys, secondaryValue = dia, pulse = 60.0,
        timestamp = ts, zoneId = "Europe/Berlin", note = "n", tags = listOf("Morning", "Seated"),
    )

    @Test fun sameDayAndSameInstantEntriesAreKeptSeparately() = runBlocking {
        val db = open()
        val dao = db.measurementDao()
        val t = 1_760_000_000_000L
        val id1 = dao.insert(bp(t, 120.0, 80.0).toEntity())
        val id2 = dao.insert(bp(t, 121.0, 81.0).toEntity()) // identical timestamp
        val id3 = dao.insert(bp(t + 3_600_000, 118.0, 79.0).toEntity())
        assertNotEquals(id1, id2)
        val all = dao.getAll(false).mapNotNull { it.toModel() }
        assertEquals(3, all.size)
        assertEquals(setOf(120.0, 121.0, 118.0), all.map { it.value }.toSet())
        assertEquals(listOf("Morning", "Seated"), all.first().tags)
        assertTrue(id3 > id2)
        db.close()
    }

    @Test fun dataSurvivesReopeningTheDatabase() = runBlocking {
        val db = open()
        db.measurementDao().insert(bp(1_760_000_000_000L, 125.0, 82.0).toEntity())
        db.reminderDao().insert(Reminder(title = "BP", types = setOf(MeasurementType.BLOOD_PRESSURE), hour = 7, minute = 30, daysOfWeek = setOf(1, 2)).toEntity())
        db.close()

        val reopened = open()
        val list = reopened.measurementDao().observeAll(false).first().mapNotNull { it.toModel() }
        assertEquals(1, list.size)
        assertEquals(125.0, list[0].value, 0.0)
        assertEquals(82.0, list[0].secondaryValue!!, 0.0)
        val reminders = reopened.reminderDao().getAll().map { it.toModel() }
        assertEquals(setOf(1, 2), reminders.single().daysOfWeek)
        reopened.close()
    }

    @Test fun demoDataIsSeparateFromRealData() = runBlocking {
        val db = open()
        val dao = db.measurementDao()
        dao.insert(bp(1L, 120.0, 80.0).toEntity())
        dao.insert(bp(2L, 130.0, 85.0).copy(isDemo = true).toEntity())
        assertEquals(1, dao.getAll(false).size)
        assertEquals(1, dao.getAll(true).size)
        dao.deleteAllDemo()
        assertEquals(1, dao.getAll(false).size)
        assertEquals(0, dao.getAll(true).size)
        db.close()
    }

    @Test fun updateKeepsIdAndChangesValues() = runBlocking {
        val db = open()
        val dao = db.measurementDao()
        val id = dao.insert(bp(1L, 120.0, 80.0).toEntity())
        val e = dao.getById(id)!!
        dao.update(e.copy(value = 140.0))
        assertEquals(140.0, dao.getById(id)!!.value, 0.0)
        assertEquals(1, dao.count(false))
        db.close()
    }
}
