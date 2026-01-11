package ca.kakaroto.ohealthrewriter

import android.content.Context
import android.content.SharedPreferences
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28]) // Specifying SDK is a good practice for Robolectric
class StepRewriteWorkerTest {

    private lateinit var context: Context
    private lateinit var worker: StepRewriteWorker
    private lateinit var prefs: SharedPreferences

    @Mock
    private lateinit var mockHealthConnectClient: HealthConnectClient

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this) // Initialize mocks
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("hc_prefs", Context.MODE_PRIVATE)

        // Clear SharedPreferences before each test to ensure a clean state
        prefs.edit().clear().commit()

        val workerFactory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker {
                // Return a real worker instance
                return StepRewriteWorker(appContext, workerParameters)
            }
        }

        worker = TestListenableWorkerBuilder<StepRewriteWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
        // Inject the mock client after the worker is built
        worker.client = mockHealthConnectClient
    }

    @Test
    fun `fixPolarQuirk calculates new steps correctly`() = runBlocking {
        // Given
        val recordId = "test_id"
        val recordId2 = "test_id2"
        val initialTime = Instant.now()

        val record1 = createStepsRecord(recordId, 100, 1, initialTime, initialTime.plusSeconds(60))
        val record2 = createStepsRecord(recordId, 250, 2, initialTime, initialTime.plusSeconds(70))
        val record3 = createStepsRecord(recordId, 300, 3, initialTime, initialTime.plusSeconds(120))
        val record4 = createStepsRecord(recordId, 300, 3, initialTime, initialTime.plusSeconds(120)) // Identical to record3
        val record5 = createStepsRecord(recordId2, 100, 1, initialTime.plusSeconds(3600), initialTime.plusSeconds(3660))

        // --- Step 1: Process first record ---
        val newSteps1 = worker.fixPolarQuirk(record1)
        assert(newSteps1 == 100L)
        assert(prefs.getString("polar_last_id", "") == recordId)
        assert(prefs.getLong("polar_last_version", 0) == 1L)
        assert(prefs.getLong("polar_last_steps", 0) == 100L)
        assert(prefs.getLong("polar_last_end_time", 0) == initialTime.plusSeconds(60).toEpochMilli())

        // --- Step 2: Process second, updated record ---
        val newSteps2 = worker.fixPolarQuirk(record2)
        assert(newSteps2 == 150L)
        assert(prefs.getString("polar_last_id", "") == recordId)
        assert(prefs.getLong("polar_last_version", 0) == 2L)
        assert(prefs.getLong("polar_last_steps", 0) == 250L)
        assert(prefs.getLong("polar_last_end_time", 0) == initialTime.plusSeconds(70).toEpochMilli())

        // --- Step 3: Process third, updated record ---
        val newSteps3 = worker.fixPolarQuirk(record3)
        assert(newSteps3 == 50L)
        assert(prefs.getString("polar_last_id", "") == recordId)
        assert(prefs.getLong("polar_last_version", 0) == 3L)
        assert(prefs.getLong("polar_last_steps", 0) == 300L)
        assert(prefs.getLong("polar_last_end_time", 0) == initialTime.plusSeconds(120).toEpochMilli())

        // --- Step 4: Process identical record again (should do nothing) ---
        val newSteps4 = worker.fixPolarQuirk(record4)
        assert(newSteps4 == 0L)
        // Assert that values did NOT change
        assert(prefs.getString("polar_last_id", "") == recordId)
        assert(prefs.getLong("polar_last_version", 0) == 3L)
        assert(prefs.getLong("polar_last_steps", 0) == 300L)
        assert(prefs.getLong("polar_last_end_time", 0) == initialTime.plusSeconds(120).toEpochMilli())

        // --- Step 5: Process a completely new record ---
        val newSteps5 = worker.fixPolarQuirk(record5)
        assert(newSteps5 == 100L)
        assert(prefs.getString("polar_last_id", "") == recordId2)
        assert(prefs.getLong("polar_last_version", 0) == 1L)
        assert(prefs.getLong("polar_last_steps", 0) == 100L)
        assert(prefs.getLong("polar_last_end_time", 0) == initialTime.plusSeconds(3660).toEpochMilli())
    }

    private fun createStepsRecord(id: String, count: Long, version: Long, time: Instant, timeEnd: Instant): StepsRecord {
        return StepsRecord(
            count = count,
            startTime = time,
            endTime = timeEnd,
            startZoneOffset = null,
            endZoneOffset = null,
            metadata = Metadata(
                clientRecordId = id,
                clientRecordVersion = version,
                dataOrigin = DataOrigin("fi.polar.polarflow"),
                device = mock(Device::class.java)
            )
        )
    }
}
