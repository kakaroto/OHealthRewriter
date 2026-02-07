package ca.kakaroto.ohealthrewriter

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.text.Html
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.work.*
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

const val ACTION_LOG_UPDATED = "ca.kakaroto.ohealthrewriter.LOG_UPDATED"

class MainActivity : AppCompatActivity() {

    private lateinit var logView: TextView
    private lateinit var createFileLauncher: ActivityResultLauncher<Intent>

    private val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    )

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            schedulePeriodicWorker()
            appendLog("Permissions granted")
        } else {
            appendLog("Permissions missing")
        }
    }

    private val logUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_LOG_UPDATED) {
                refreshLogs()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logView = findViewById(R.id.logView)

        migrateLogsFromPrefs()

        findViewById<Button>(R.id.pollButton).setOnClickListener {
            triggerManualPoll()
        }

        findViewById<Button>(R.id.clearLogButton).setOnClickListener {
            clearLogs()
        }

        requestPermissions.launch(permissions.toTypedArray())

        createFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                it.data?.data?.let {
                    writeLogToFile(it)
                }
            }
        }

        ContextCompat.registerReceiver(this, logUpdateReceiver, IntentFilter(ACTION_LOG_UPDATED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun migrateLogsFromPrefs() {
        val prefs = getSharedPreferences("hc_prefs", Context.MODE_PRIVATE)
        val oldLogs = prefs.getString("logs", null)
        val logFile = File(filesDir, LOG_FILE_NAME)

        // Only migrate if there are old logs AND the new file doesn't already exist/is empty.
        if (!oldLogs.isNullOrEmpty() && (!logFile.exists() || logFile.length() == 0L)) {
            try {
                // Old logs were stored with newest first (reverse chronological).
                // We need to reverse them to get chronological order for the new file.
                val oldLogLines = oldLogs.split("<br>").filter { it.isNotEmpty() }.reversed()

                // Write the chronologically-ordered old logs to the new file.
                openFileOutput(LOG_FILE_NAME, Context.MODE_PRIVATE).use { outputStream ->
                    oldLogLines.forEach { line ->
                        outputStream.write((line + "\n").toByteArray())
                    }
                }

                // Once migrated, remove the old value from prefs
                prefs.edit().remove("logs").apply()
                appendLog("Successfully migrated logs from older version.", "#008000")
            } catch (e: Exception) {
                appendLog("Failed to migrate logs: ${e.message}", "#FF0000")
            }
        } else if (!oldLogs.isNullOrEmpty()) {
            // Edge case: Both old logs and a new log file exist. Log this for debugging.
            appendLog("Could not automatically migrate logs. Please export and clear data if needed.", "#FF0000")
            prefs.edit().remove("logs").apply() // Remove old logs to prevent this message on every launch
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(logUpdateReceiver)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_export_log -> {
                exportLog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLogs()
        appendLog("Checking for new steps data...")
        poll()
    }

    private fun schedulePeriodicWorker() {
        val work = PeriodicWorkRequestBuilder<StepRewriteWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "step_rewrite_worker",
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }

    private fun poll() {
        val work = OneTimeWorkRequestBuilder<StepRewriteWorker>().build()
        WorkManager.getInstance(this).enqueue(work)
    }
    private fun triggerManualPoll() {
        appendLog("Manual poll triggered")
        poll()
    }

    private fun appendLog(msg: String, color: String = "#808080") {
        val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.now())
        val logLine = "<font color='#800000'>[$timestamp]</font> <font color='$color'>$msg</font>"
        try {
            openFileOutput(LOG_FILE_NAME, Context.MODE_APPEND).use {
                it.write((logLine + "\n").toByteArray())
            }
            val intent = Intent(ACTION_LOG_UPDATED).apply {
                setPackage(packageName)
            }
            sendBroadcast(intent)
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun refreshLogs() {
        val logFile = File(filesDir, LOG_FILE_NAME)
        if (logFile.exists()) {
            val logLines = logFile.readLines()
            // Reverse the order of lines for display (newest first) and join for HTML
            val reversedContent = logLines.reversed().joinToString("<br>")
            logView.text = HtmlCompat.fromHtml(reversedContent, HtmlCompat.FROM_HTML_MODE_LEGACY)
        } else {
            logView.text = ""
        }
    }

    private fun clearLogs() {
        val logFile = File(filesDir, LOG_FILE_NAME)
        if (logFile.exists()) {
            logFile.delete()
        }
        refreshLogs()
    }

    private fun exportLog() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/html"
            putExtra(Intent.EXTRA_TITLE, "OHealth-log.html")
        }
        createFileLauncher.launch(intent)
    }

    private fun writeLogToFile(uri: android.net.Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                val logFile = File(filesDir, LOG_FILE_NAME)
                if (logFile.exists()) {
                    outputStream.write(logFile.readBytes())
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
