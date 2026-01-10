package ca.kakaroto.ohealthrewriter

import android.content.Context
import android.util.Log
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

    // Made internal and var for testing
    internal var client: HealthConnectClient = HealthConnectClient.getOrCreate(context)
    private val prefs = context.getSharedPreferences("hc_prefs", Context.MODE_PRIVATE)

    override suspend fun doWork(): Result {

        val token = prefs.getString("changes_token", null)
            ?: client.getChangesToken(
                ChangesTokenRequest(setOf(StepsRecord::class))
            )

        val response = client.getChanges(token)
        var rewritten = 0
        var totalSteps: Long = 0

        for (change in response.changes) {
            if (change is UpsertionChange &&
                change.record is StepsRecord
            ) {
                val record = change.record as StepsRecord

                debug("New record: ${record.count} steps from ${record.startTime} to ${record.endTime} (metadata: ${record.metadata})")

                if (record.metadata.dataOrigin.packageName == "fi.polar.polarflow" &&
                    prefs.getBoolean("polar_quirk_fix", false)) {
                    totalSteps += fixPolarQuirk(record)
                    rewritten++
                    continue // Polar records handled, move to the next change
                }

                if (record.metadata.dataOrigin.packageName == "com.heytap.health.international") {
                    if (record.metadata.clientRecordId?.startsWith("rewritten_") == true) continue

                    rewrite(record)
                    rewritten++
                    totalSteps += record.count
                }
            }
        }

        prefs.edit().putString("changes_token", response.nextChangesToken).apply()
        if (rewritten == 0) {
            log("No new records found")
        } else {
            log("Found $rewritten new records. Total new steps: $totalSteps")
        }

        return Result.success()
    }

    // Made internal for testing
    internal suspend fun fixPolarQuirk(record: StepsRecord): Long {
        if (record.metadata.dataOrigin.packageName != "fi.polar.polarflow") return 0

        val lastId = prefs.getString("polar_last_id", "")
        val lastVersion = prefs.getLong("polar_last_version", 0)
        val lastSteps = prefs.getLong("polar_last_steps", 0)
        val lastTS = Instant.ofEpochMilli(prefs.getLong("polar_last_end_time", 0))

        if (lastId == record.metadata.clientRecordId &&
            lastVersion == record.metadata.clientRecordVersion) return 0

        var newSteps = record.count
        var startTime = record.startTime
        var clientId = "polarflow_${record.metadata.id}"
        if (lastId != record.metadata.clientRecordId) {
            log("New Polar Flow record: ${record.count} steps")
        } else {
            newSteps = record.count - lastSteps
            startTime = lastTS
            clientId = "polarflow_${record.metadata.id}-update-${lastSteps}"
            log("Updated Polar Flow record: ${newSteps} new steps (Total changed from $lastSteps to ${record.count})")
        }
        val newRecord = StepsRecord(
            count = newSteps,
            startTime = startTime,
            endTime = record.endTime,
            startZoneOffset = record.startZoneOffset,
            endZoneOffset = record.endZoneOffset,
            metadata = Metadata(
                clientRecordId = clientId,
                clientRecordVersion = 1,
                dataOrigin = record.metadata.dataOrigin,
                device = record.metadata.device,
                recordingMethod = record.metadata.recordingMethod
            )
        )
        debug("Inserting new record: $newRecord")
        client.insertRecords(listOf(newRecord))


        val editor = prefs.edit()
        editor.putString("polar_last_id", record.metadata.clientRecordId)
        editor.putLong("polar_last_version", record.metadata.clientRecordVersion)
        editor.putLong("polar_last_steps", record.count)
        editor.putLong("polar_last_end_time", record.endTime.toEpochMilli())
        editor.apply()
        return newSteps
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

    private fun log(msg: String, color: String = "#808080") {
        val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.now())
        val logLine = "<font color='#800000'>[$timestamp]</font> <font color='$color'>$msg</font>"
        Log.d("StepRewriteWorker", logLine.replace(Regex("<.*?>"), "")) // Log plain text to Logcat
        val prefs = applicationContext.getSharedPreferences("hc_prefs", Context.MODE_PRIVATE)
        val logs = prefs.getString("logs", "") ?: ""
        prefs.edit().putString("logs", logLine + "<br>" + logs).apply()
    }

    private fun debug(msg: String) {
        if (prefs.getBoolean("debugging_enabled", false)) {
            log(msg, color = "#FFA500") // Orange color
        }
    }
}
