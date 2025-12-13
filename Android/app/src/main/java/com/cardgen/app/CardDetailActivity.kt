package com.cardgen.app

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.File

class CardDetailActivity : AppCompatActivity() {

    private lateinit var cardFront: View
    private lateinit var particleView: ParticleView
    private lateinit var ivArt: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvRarity: TextView
    private lateinit var tvDesc: TextView
    private lateinit var tvAtk: TextView
    private lateinit var tvDef: TextView

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var scaleFactor = 1.0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_detail)
        supportActionBar?.title = "Card Detail"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initViews()
        loadData()
        setupInteractions()
    }

    private fun initViews() {
        cardFront = findViewById(R.id.cardFront)
        particleView = findViewById(R.id.particleView)
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

        val bgDrawable = ContextCompat.getDrawable(this, R.drawable.bg_card_base) as GradientDrawable
        bgDrawable.setColor(ContextCompat.getColor(this, bgColor))
        bgDrawable.setStroke(8, ContextCompat.getColor(this, borderColor))

        findViewById<View>(R.id.cardInnerLayout).background = bgDrawable

        // Configure Particles
        when {
            r.contains("UR") -> particleView.setConfig(ParticleView.ParticleType.LIGHTNING)
            r.contains("SSR") -> particleView.setConfig(ParticleView.ParticleType.SPARKLE)
            r.contains("SR") -> particleView.setConfig(ParticleView.ParticleType.GLOW)
            else -> particleView.setConfig(ParticleView.ParticleType.DUST)
        }

        // Breathing effect for high rarity
        if (r.contains("SSR") || r.contains("UR")) {
             // We disable the constant breathing if we want manual interaction to take precedence,
             // or mix them. Let's keep it but make it subtle so it doesn't fight the user.
             // Actually, for user-controlled 3D tilt, constant scale animation might be annoying.
             // Let's rely on the particle effect for "aliveness" and user input for motion.
        }
    }

    private fun setupInteractions() {
        // Pinch to Zoom
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(0.5f, 3.0f) // Limit zoom range

                cardFront.scaleX = scaleFactor
                cardFront.scaleY = scaleFactor
                return true
            }
        })

        // Touch Listener for Tilt + Zoom
        val rootLayout = findViewById<View>(android.R.id.content)

        rootLayout.setOnTouchListener { _, event ->

            // Pass to scale detector first
            scaleGestureDetector.onTouchEvent(event)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    // Only tilt if not scaling (multitouch)
                    if (event.pointerCount == 1) {
                        handleTilt(event.x, event.y, rootLayout.width, rootLayout.height)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    resetCardState()
                }
            }
            // Return true to indicate we consumed the event for gestures,
            // but for complex layouts we might want more granular control.
            // Since this activity is just for viewing, consuming it is fine.
            true
        }
    }

    private fun handleTilt(x: Float, y: Float, w: Int, h: Int) {
        val centerX = w / 2f
        val centerY = h / 2f

        // Calculate offset from center (-1 to 1)
        val offsetX = (x - centerX) / centerX
        val offsetY = (y - centerY) / centerY

        // Max tilt angles
        val maxTilt = 20f

        // Inverse logic: touching left tilts card to left (rotY negative? No, rotY negative is left side coming out)
        // Standard behavior:
        // Touch Right -> Rotate Y positive (Right side goes away? or comes towards?)
        // Let's visualize: Holding a card. Press right side -> Right side goes down (away).
        // View.setRotationY(positive) rotates around Y axis. + degrees = right edge moves into screen (away).

        // So touching Right (offset > 0) -> RotationY should be positive?
        // Let's try: Touch right -> tilt right edge away -> RotationY positive.
        // Touch Top (offsetY < 0) -> tilt top edge away -> RotationX positive?
        // RotationX + degrees = top edge moves into screen (away).

        cardFront.rotationY = offsetX * maxTilt
        cardFront.rotationX = -offsetY * maxTilt // Invert Y for natural feel
    }

    private fun resetCardState() {
        // Animate back to rest
        cardFront.animate()
            .rotationX(0f)
            .rotationY(0f)
            .scaleX(1.0f)
            .scaleY(1.0f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator())
            .start()

        scaleFactor = 1.0f
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
