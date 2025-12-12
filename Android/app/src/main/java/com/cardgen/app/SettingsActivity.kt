package com.cardgen.app

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val etApiUrl = findViewById<TextInputEditText>(R.id.etApiUrl)
        val etApiKey = findViewById<TextInputEditText>(R.id.etApiKey)
        val etModel = findViewById<TextInputEditText>(R.id.etModel)
        val btnSave = findViewById<Button>(R.id.btnSaveSettings)

        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // Load existing
        etApiUrl.setText(prefs.getString("api_url", "https://api.openai.com"))
        etApiKey.setText(prefs.getString("api_key", ""))
        etModel.setText(prefs.getString("model", "gpt-4o-mini"))

        btnSave.setOnClickListener {
            val editor = prefs.edit()
            editor.putString("api_url", etApiUrl.text.toString().trim())
            editor.putString("api_key", etApiKey.text.toString().trim())
            editor.putString("model", etModel.text.toString().trim())
            editor.apply()

            Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
