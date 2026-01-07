package ca.kakaroto.ohealthrewriter

import android.content.Context
import android.util.Log
import androidx.core.text.HtmlCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class StepRewriteWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val client = HealthConnectClient.getOrCreate(context)
    private val prefs = context.getSharedPreferences("hc_prefs", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {
        val token = prefs.getString("changes_token", null)
            ?: client.getChangesToken(
                ChangesTokenRequest(setOf(StepsRecord::class))
            )

        val response = client.getChanges(token)
        var rewritten = 0
        var total_steps = 0

        for (change in response.changes) {
            if (change is UpsertionChange &&
                change.record is StepsRecord
            ) {
                val record = change.record as StepsRecord

                if (record.metadata.dataOrigin.packageName != "com.heytap.health.international") continue
                if (record.metadata.clientRecordId?.startsWith("rewritten_") == true) continue
                log("New record: ${record.count} steps at ${record.endTime}")

                rewrite(record)
                rewritten++
                total_steps += record.count.toInt()
            }
        }

        prefs.edit().putString("changes_token", response.nextChangesToken).apply()
        log("Worker completed — rewritten $rewritten records. Total new steps: $total_steps")

        return Result.success()
    }

    private suspend fun rewrite(old: StepsRecord) {
        val manufacturer = prefs.getString("device_manufacturer", "OnePlus")
        val model = prefs.getString("device_model", "OnePlus Watch 3")
        val type = prefs.getInt("device_type", Device.TYPE_WATCH)

        val newRecord = StepsRecord(
            count = old.count,
            startTime = old.startTime,
            endTime = old.endTime,
            startZoneOffset = old.startZoneOffset,
            endZoneOffset = old.endZoneOffset,
            metadata = Metadata(
                clientRecordId = "rewritten_${old.metadata.id}",
                clientRecordVersion = 1,
                dataOrigin = old.metadata.dataOrigin,
                device = Device(
                    manufacturer = manufacturer,
                    model = model,
                    type = type
                ),
                recordingMethod = Metadata.RECORDING_METHOD_AUTOMATICALLY_RECORDED
            )
        )
        client.insertRecords(listOf(newRecord))
    }

    private fun log(msg: String) {
        val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.now())
        val logLine = "<font color='#800000'>[$timestamp]</font> <font color='#808080'>$msg</font>"
        Log.d("StepRewriteWorker", logLine)
        val prefs = applicationContext.getSharedPreferences("hc_prefs", Context.MODE_PRIVATE)
        val logs = prefs.getString("logs", "") ?: ""
        prefs.edit().putString("logs", logLine + "<br>" + logs).apply()
    }
}
