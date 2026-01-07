package ca.kakaroto.ohealthrewriter

import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.records.metadata.Device

class SettingsActivity : AppCompatActivity() {

    private lateinit var manufacturerEditText: EditText
    private lateinit var modelEditText: EditText
    private lateinit var typeSpinner: Spinner

    private val deviceTypes = arrayOf(
        "Watch" to Device.TYPE_WATCH,
        "Phone" to Device.TYPE_PHONE,
        "Chest Strap" to Device.TYPE_CHEST_STRAP,
        "Scale" to Device.TYPE_SCALE,
        "Head Mounted" to Device.TYPE_HEAD_MOUNTED,
        "Ring" to Device.TYPE_RING,
        "Unknown" to Device.TYPE_UNKNOWN
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        manufacturerEditText = findViewById(R.id.manufacturerEditText)
        modelEditText = findViewById(R.id.modelEditText)
        typeSpinner = findViewById(R.id.typeSpinner)

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, deviceTypes.map { it.first })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = adapter

        loadSettings()

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("hc_prefs", Context.MODE_PRIVATE)
        manufacturerEditText.setText(prefs.getString("device_manufacturer", "OnePlus"))
        modelEditText.setText(prefs.getString("device_model", "OnePlus Watch 3"))
        val type = prefs.getInt("device_type", Device.TYPE_WATCH)
        val typeIndex = deviceTypes.indexOfFirst { it.second == type }
        if (typeIndex != -1) {
            typeSpinner.setSelection(typeIndex)
        }
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("hc_prefs", Context.MODE_PRIVATE).edit()
        prefs.putString("device_manufacturer", manufacturerEditText.text.toString())
        prefs.putString("device_model", modelEditText.text.toString())
        val selectedType = deviceTypes[typeSpinner.selectedItemPosition].second
        prefs.putInt("device_type", selectedType)
        prefs.apply()
        finish()
    }
}
