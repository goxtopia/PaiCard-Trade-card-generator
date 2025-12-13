package com.cardgen.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.util.Random
import android.graphics.Color
import android.view.Choreographer

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
        DUST, GLOW, SPARKLE, LIGHTNING
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
        var decay: Float
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

        val x = random.nextFloat() * width
        val y = random.nextFloat() * height

        // Spawn from center-ish for some effects, or random for others
        // For now, random distribution is fine as a background aura

        val radius = (random.nextFloat() * 3 + 1) * density
        val speedX = (random.nextFloat() - 0.5f) * 2 * density * speedMultiplier
        val speedY = (random.nextFloat() - 0.5f) * 2 * density * speedMultiplier
        val life = 1.0f
        val decay = 0.01f + random.nextFloat() * 0.02f

        particles.add(Particle(x, y, radius, speedX, speedY, 255, life, decay))
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

            // Fade out
            p.alpha = (p.life * 255).toInt()

            if (p.life <= 0) {
                iterator.remove()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (p in particles) {
            paint.color = particleColor
            paint.alpha = p.alpha
            canvas.drawCircle(p.x, p.y, p.radius, paint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isRunning = false
    }
}
