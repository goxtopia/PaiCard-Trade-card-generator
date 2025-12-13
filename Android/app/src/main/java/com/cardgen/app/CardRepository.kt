package com.cardgen.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object CardRepository {

    private const val FILE_NAME = "card_cache.json"
    private var cache: ConcurrentHashMap<String, VLMService.CardData> = ConcurrentHashMap()
    private val gson = Gson()
    private var isLoaded = false

    fun init(context: Context) {
        if (!isLoaded) {
            loadCache(context)
            isLoaded = true
        }
    }

    private fun loadCache(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            try {
                val json = file.readText()
                val type = object : TypeToken<ConcurrentHashMap<String, VLMService.CardData>>() {}.type
                val loaded: ConcurrentHashMap<String, VLMService.CardData>? = gson.fromJson(json, type)
                if (loaded != null) {
                    cache = loaded
                }
            } catch (e: Exception) {
                Log.e("CardRepository", "Failed to load cache", e)
            }
        }
    }

    private fun saveCache(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val json = gson.toJson(cache)
            FileOutputStream(file).use { it.write(json.toByteArray()) }
        } catch (e: Exception) {
            Log.e("CardRepository", "Failed to save cache", e)
        }
    }

    fun hasCard(md5: String): Boolean {
        return cache.containsKey(md5)
    }

    fun getCard(md5: String): VLMService.CardData? {
        return cache[md5]
    }

    fun saveCard(context: Context, md5: String, data: VLMService.CardData) {
        cache[md5] = data
        saveCache(context)
    }

    fun calculateMD5(bitmap: Bitmap): String {
        val buffer = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, buffer) // Use consistent compression for hashing
        val bytes = buffer.toByteArray()
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    // Helper to calculate MD5 from byte array directly if available
    fun calculateMD5(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
