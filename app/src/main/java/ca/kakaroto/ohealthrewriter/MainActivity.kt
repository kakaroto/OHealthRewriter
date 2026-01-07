package ca.kakaroto.ohealthrewriter

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Html
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.work.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var logView: TextView
    private lateinit var prefs: SharedPreferences

    private val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getWritePermission(StepsRecord::class),
        "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
    )

    private val requestPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(permissions)) {
            schedulePeriodicWorker()
            appendLog("Permissions granted")
        } else {
            appendLog("Permissions missing")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        logView = findViewById(R.id.logView)
        prefs = getSharedPreferences("hc_prefs", Context.MODE_PRIVATE)

        findViewById<Button>(R.id.pollButton).setOnClickListener {
            triggerManualPoll()
        }

        findViewById<Button>(R.id.clearLogButton).setOnClickListener {
            clearLogs()
        }

        requestPermissions.launch(permissions)
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
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        prefs.registerOnSharedPreferenceChangeListener(this)
        refreshLogs()
    }

    override fun onPause() {
        super.onPause()
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "logs") {
            refreshLogs()
        }
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

    private fun triggerManualPoll() {
        val work = OneTimeWorkRequestBuilder<StepRewriteWorker>().build()
        WorkManager.getInstance(this).enqueue(work)
        appendLog("Manual poll triggered")
    }

    private fun appendLog(msg: String) {
        val timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.now())
        val logLine = "<font color='#800000'>[$timestamp]</font> <font color='#808080'>$msg</font>"
        val logs = prefs.getString("logs", "") ?: ""
        prefs.edit().putString("logs", logLine + "<br>" + logs).apply()
    }

    private fun refreshLogs() {
        val logs = prefs.getString("logs", "") ?: ""
        logView.text = HtmlCompat.fromHtml(logs, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    private fun clearLogs() {
        prefs.edit().putString("logs", "").apply()
    }
}
