package com.cardgen.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.InputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

class CardGenerationService : Service() {

    companion object {
        const val CHANNEL_ID = "CardGenChannel"
        const val ACTION_PROCESS_PACK = "com.cardgen.app.action.PROCESS_PACK"
        const val ACTION_PACK_UPDATED = "com.cardgen.app.action.PACK_UPDATED" // New broadcast for Pack updates

        const val EXTRA_PACK_ID = "extra_pack_id"
    }

    private val queue = ConcurrentLinkedQueue<Task>()
    private val executor = Executors.newSingleThreadExecutor()
    private val vlmService = VLMService()
    private var isRunning = false

    data class Task(
        val packId: String,
        val item: PackRepository.PackItem
    )

    override fun onCreate() {
        super.onCreate()
        CardRepository.init(this)
        PackRepository.init(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PROCESS_PACK) {
            val packId = intent.getStringExtra(EXTRA_PACK_ID)

            if (packId != null) {
                val pack = PackRepository.getPack(packId)
                if (pack != null) {
                    // Queue all items in the pack
                    for (item in pack.items) {
                        // Only queue if not already done
                        if (!CardRepository.hasCard(item.md5)) {
                            queue.add(Task(packId, item))
                        }
                    }
                    // Trigger initial check/start
                    processNext()
                }
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun processNext() {
        if (isRunning) return
        val task = queue.peek() ?: run {
            // Queue empty, stop
            stopForeground(true)
            return
        }

        isRunning = true
        updateNotification("Processing Pack...", "Remaining items: ${queue.size}")

        executor.execute {
            try {
                // 1. Check Cache (Double check)
                var cardData = CardRepository.getCard(task.item.md5)

                if (cardData == null) {
                    // 2. Generate
                    val bitmap = loadBitmap(Uri.parse(task.item.uri))
                    if (bitmap != null) {
                        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                        val apiKey = prefs.getString("api_key", "")
                        val apiUrl = prefs.getString("api_url", "https://api.openai.com")
                        val model = prefs.getString("model", "gpt-4o-mini")
                        val customPrompt = prefs.getString("custom_prompt", "Create a funny and creative name and ability description.")

                        if (apiKey.isNullOrEmpty()) throw Exception("API Key missing")

                        cardData = vlmService.analyzeImage(bitmap, apiKey!!, apiUrl!!, model!!, customPrompt!!)

                        // 3. Save Cache (Idempotency)
                        CardRepository.saveCard(this, task.item.md5, cardData)
                    } else {
                        // If we can't load image, we can't generate.
                        // For now, we just skip it or retry?
                        // Let's assume transient error and skip, but this might leave pack in limbo.
                        // Ideally we mark error in repo.
                    }
                }

                // 4. Check Pack Readiness
                PackRepository.checkPackReadiness(this, task.packId)

                // 5. Broadcast Update
                val intent = Intent(ACTION_PACK_UPDATED)
                intent.putExtra(EXTRA_PACK_ID, task.packId)
                sendBroadcast(intent)

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                queue.poll() // Remove current
                isRunning = false
                if (queue.isNotEmpty()) {
                    processNext()
                } else {
                    stopForeground(true)
                }
            }
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        return try {
            if (uri.scheme == null || uri.scheme == "file") {
                val path = uri.path
                if (path != null) {
                    val file = java.io.File(path)
                    if (file.exists()) {
                         return BitmapFactory.decodeFile(path)
                    }
                }
            }
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Card Generation Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(title: String, content: String) {
        val notificationIntent = Intent(this, BatchDrawActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_rotate)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
