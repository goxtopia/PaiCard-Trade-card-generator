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
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

class CardGenerationService : Service() {

    companion object {
        const val CHANNEL_ID = "CardGenChannel"
        const val ACTION_PROCESS_IMAGE = "com.cardgen.app.action.PROCESS_IMAGE"
        const val ACTION_CARD_COMPLETE = "com.cardgen.app.action.CARD_COMPLETE"

        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_MD5 = "extra_md5"
        const val EXTRA_REQUEST_ID = "extra_request_id" // To track back to UI item

        // Broadcast Extras
        const val RESULT_DATA_JSON = "result_data_json"
        const val RESULT_MD5 = "result_md5"
        const val RESULT_REQUEST_ID = "result_request_id"
        const val RESULT_ERROR = "result_error"
    }

    private val queue = ConcurrentLinkedQueue<Task>()
    private val executor = Executors.newSingleThreadExecutor()
    private val vlmService = VLMService()
    private var isRunning = false

    data class Task(
        val uri: String,
        val md5: String,
        val requestId: String
    )

    override fun onCreate() {
        super.onCreate()
        CardRepository.init(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PROCESS_IMAGE) {
            val uriStr = intent.getStringExtra(EXTRA_IMAGE_URI)
            val md5 = intent.getStringExtra(EXTRA_MD5)
            val reqId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: "0"

            if (uriStr != null && md5 != null) {
                val task = Task(uriStr, md5, reqId)
                queue.add(task)
                processNext()
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun processNext() {
        if (isRunning) return
        val task = queue.peek() ?: return

        isRunning = true
        updateNotification("Processing Card...", "Queue size: ${queue.size}")

        executor.execute {
            try {
                // 1. Check Cache
                var cardData = CardRepository.getCard(task.md5)

                if (cardData == null) {
                    // 2. Generate
                    val bitmap = loadBitmap(Uri.parse(task.uri))
                    if (bitmap != null) {
                        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                        val apiKey = prefs.getString("api_key", "")
                        val apiUrl = prefs.getString("api_url", "https://api.openai.com")
                        val model = prefs.getString("model", "gpt-4o-mini")
                        val customPrompt = prefs.getString("custom_prompt", "Create a funny and creative name and ability description.")

                        if (apiKey.isNullOrEmpty()) throw Exception("API Key missing")

                        cardData = vlmService.analyzeImage(bitmap, apiKey!!, apiUrl!!, model!!, customPrompt!!)

                        // 3. Save Cache
                        CardRepository.saveCard(this, task.md5, cardData)
                    } else {
                        throw Exception("Failed to load image")
                    }
                }

                // 4. Broadcast Success
                val intent = Intent(ACTION_CARD_COMPLETE)
                intent.putExtra(RESULT_REQUEST_ID, task.requestId)
                intent.putExtra(RESULT_MD5, task.md5)
                // Serialize simple fields manually or use gson if needed
                intent.putExtra(RESULT_DATA_JSON, com.google.gson.Gson().toJson(cardData))
                sendBroadcast(intent)

            } catch (e: Exception) {
                e.printStackTrace()
                val intent = Intent(ACTION_CARD_COMPLETE)
                intent.putExtra(RESULT_REQUEST_ID, task.requestId)
                intent.putExtra(RESULT_MD5, task.md5)
                intent.putExtra(RESULT_ERROR, e.message)
                sendBroadcast(intent)
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
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
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
