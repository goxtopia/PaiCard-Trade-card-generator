package com.cardgen.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Random
import android.graphics.Color
import android.view.Choreographer
import kotlin.math.cos
import kotlin.math.sin

class ParticleView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val particles = ArrayList<Particle>()
    private val paint = Paint()
    private val random = Random()
    private var isRunning = false
    private var density = 1f

    private var particleColor = Color.WHITE
    private var particleCount = 50
    private var speedMultiplier = 1.0f

    // Configuration for different styles
    enum class ParticleType {
        DUST, GLOW, SPARKLE, LIGHTNING, FLAME, COSMIC
    }

    private var currentType = ParticleType.DUST

    init {
        density = resources.displayMetrics.density
    }

    data class Particle(
        var x: Float,
        var y: Float,
        var radius: Float,
        var speedX: Float,
        var speedY: Float,
        var alpha: Int,
        var life: Float, // 0.0 to 1.0
        var decay: Float,
        var color: Int? = null // Optional individual color
    )

    fun setConfig(type: ParticleType) {
        currentType = type
        particles.clear()
        when (type) {
            ParticleType.DUST -> {
                particleColor = Color.LTGRAY
                particleCount = 20
                speedMultiplier = 0.5f
            }
            ParticleType.GLOW -> {
                particleColor = Color.parseColor("#ffaa00") // Orange
                particleCount = 40
                speedMultiplier = 1.0f
            }
            ParticleType.SPARKLE -> {
                particleColor = Color.parseColor("#ffd700") // Gold
                particleCount = 60
                speedMultiplier = 2.0f
            }
            ParticleType.LIGHTNING -> {
                particleColor = Color.parseColor("#b19cd9") // Violet/Lightning
                particleCount = 80
                speedMultiplier = 3.0f
            }
            ParticleType.FLAME -> {
                particleColor = Color.parseColor("#e74c3c") // Red/Orange
                particleCount = 100
                speedMultiplier = 1.5f
            }
            ParticleType.COSMIC -> {
                particleColor = Color.parseColor("#00BFFF") // Deep Sky Blue
                particleCount = 120
                speedMultiplier = 1.0f
            }
        }
        startAnimation()
    }

    private fun startAnimation() {
        if (!isRunning) {
            isRunning = true
            postFrameCallback()
        }
    }

    private fun postFrameCallback() {
        if (isRunning) {
            Choreographer.getInstance().postFrameCallback {
                updateParticles()
                invalidate()
                postFrameCallback()
            }
        }
    }

    private fun spawnParticle() {
        if (width == 0 || height == 0) return

        var x = random.nextFloat() * width
        var y = random.nextFloat() * height
        var speedX = (random.nextFloat() - 0.5f) * 2 * density * speedMultiplier
        var speedY = (random.nextFloat() - 0.5f) * 2 * density * speedMultiplier
        var radius = (random.nextFloat() * 3 + 1) * density
        var decay = 0.01f + random.nextFloat() * 0.02f
        var color: Int? = null

        when (currentType) {
            ParticleType.FLAME -> {
                // Spawn at bottom, move up
                x = random.nextFloat() * width
                y = height.toFloat() + 20f
                speedY = - (random.nextFloat() * 3 + 1) * density * speedMultiplier // Upward
                speedX = (random.nextFloat() - 0.5f) * 1 * density
                decay = 0.015f + random.nextFloat() * 0.02f
                // Color shift: Red to Yellow
                color = if (random.nextBoolean()) Color.parseColor("#e74c3c") else Color.parseColor("#f1c40f")
            }
            ParticleType.COSMIC -> {
                // Spawn center, spiral out? Or just random vibrant
                // Let's do random but with colors
                val colors = listOf(
                    Color.parseColor("#9b59b6"), // Purple
                    Color.parseColor("#3498db"), // Blue
                    Color.parseColor("#e91e63")  // Pink
                )
                color = colors[random.nextInt(colors.size)]
                speedX *= 0.5f
                speedY *= 0.5f
                radius *= 1.5f // Larger particles
            }
            else -> {} // Default
        }

        particles.add(Particle(x, y, radius, speedX, speedY, 255, 1.0f, decay, color))
    }

    private fun updateParticles() {
        // Spawn new particles if needed
        if (particles.size < particleCount) {
            spawnParticle()
        }

        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.speedX
            p.y += p.speedY
            p.life -= p.decay

            // Special behaviors
            if (currentType == ParticleType.COSMIC) {
                 // Slight gravity/pull or weird motion
                 p.x += sin(p.life * 10) * density // Wobbly
            }

            // Fade out
            p.alpha = (p.life * 255).toInt().coerceIn(0, 255)

            if (p.life <= 0 || p.alpha == 0) {
                iterator.remove()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (p in particles) {
            paint.color = p.color ?: particleColor
            paint.alpha = p.alpha
            canvas.drawCircle(p.x, p.y, p.radius, paint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isRunning = false
    }
}
