package com.cardgen.app

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File

class CardDetailActivity : AppCompatActivity() {

    private lateinit var cardFront: View
    private lateinit var ivArt: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvRarity: TextView
    private lateinit var tvDesc: TextView
    private lateinit var tvAtk: TextView
    private lateinit var tvDef: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_detail)
        supportActionBar?.title = "Card Detail"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initViews()
        loadData()
    }

    private fun initViews() {
        cardFront = findViewById(R.id.cardFront)
        ivArt = findViewById(R.id.ivCardArt)
        tvName = findViewById(R.id.tvCardName)
        tvRarity = findViewById(R.id.tvCardAttribute)
        tvDesc = findViewById(R.id.tvCardDesc)
        tvAtk = findViewById(R.id.tvCardAtk)
        tvDef = findViewById(R.id.tvCardDef)
    }

    private fun loadData() {
        val name = intent.getStringExtra("name") ?: "Unknown"
        val rarity = intent.getStringExtra("rarity") ?: "N"
        val desc = intent.getStringExtra("desc") ?: ""
        val atk = intent.getStringExtra("atk") ?: "0"
        val def = intent.getStringExtra("def") ?: "0"
        val imagePath = intent.getStringExtra("imagePath") ?: ""

        tvName.text = name
        tvRarity.text = rarity
        tvDesc.text = desc
        tvAtk.text = "ATK/$atk"
        tvDef.text = "DEF/$def"

        if (imagePath.isNotEmpty()) {
            val imgFile = File(imagePath)
            if (imgFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                ivArt.setImageBitmap(bitmap)
            }
        }

        applyVisuals(rarity)

        // Add click listener for 3D flip effect (just for fun/interactivity)
        cardFront.setOnClickListener {
            flipCard()
        }
    }

    private fun applyVisuals(rarity: String) {
        val r = rarity.uppercase()
        val (bgColor, borderColor) = when {
            r.contains("UR") -> Pair(R.color.rarity_ur_bg, R.color.rarity_ur_border)
            r.contains("SSR") -> Pair(R.color.rarity_ssr_bg, R.color.rarity_ssr_border)
            r.contains("SR") -> Pair(R.color.rarity_sr_bg, R.color.rarity_sr_border)
            r.contains("R") -> Pair(R.color.rarity_r_bg, R.color.rarity_r_border)
            else -> Pair(R.color.rarity_n_bg, R.color.rarity_n_border)
        }

        // Access inner constraint layout if needed, or set on main view
        // Assuming activity_card_detail uses the standard card layout structure
        val bgDrawable = ContextCompat.getDrawable(this, R.drawable.bg_card_base) as GradientDrawable
        bgDrawable.setColor(ContextCompat.getColor(this, bgColor))
        bgDrawable.setStroke(8, ContextCompat.getColor(this, borderColor)) // Thicker border for detail view

        // Find the inner layout that needs the background
        val innerLayout = findViewById<View>(R.id.cardInnerLayout)
        innerLayout.background = bgDrawable

        if (r.contains("SSR") || r.contains("UR")) {
            startBreathingAnimation(cardFront)
        }
    }

    private fun startBreathingAnimation(view: View) {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1.0f, 1.02f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1.0f, 1.02f)
        val animator = ObjectAnimator.ofPropertyValuesHolder(view, scaleX, scaleY)
        animator.duration = 2000
        animator.repeatCount = ObjectAnimator.INFINITE
        animator.repeatMode = ObjectAnimator.REVERSE
        animator.start()
    }

    private fun flipCard() {
        val scale = resources.displayMetrics.density
        cardFront.cameraDistance = 8000 * scale
        val animator = ObjectAnimator.ofFloat(cardFront, "rotationY", 0f, 360f)
        animator.duration = 1000
        animator.start()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
