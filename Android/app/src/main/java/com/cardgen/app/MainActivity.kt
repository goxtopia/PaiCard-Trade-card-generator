package com.cardgen.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnSingleDraw).setOnClickListener {
            startActivity(Intent(this, SingleDrawActivity::class.java))
        }

        findViewById<Button>(R.id.btnGodsDraw).setOnClickListener {
            startActivity(Intent(this, GodsDrawActivity::class.java))
        }

        findViewById<Button>(R.id.btnBatchDraw).setOnClickListener {
            startActivity(Intent(this, BatchDrawActivity::class.java))
        }

        findViewById<Button>(R.id.btnLibrary).setOnClickListener {
            startActivity(Intent(this, LibraryActivity::class.java))
        }

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }
}
