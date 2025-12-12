package com.cardgen.app

import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

class VLMService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    data class CardData(
        val rarity: String,
        val name: String,
        val description: String,
        val atk: String,
        val def: String
    )

    fun analyzeImage(bitmap: Bitmap, apiKey: String, apiUrl: String, model: String, customInstructions: String): CardData {
        // Resize image if too big
        val resizedBitmap = resizeBitmap(bitmap, 576)
        val base64Image = bitmapToBase64(resizedBitmap)

        // Combine user's custom instructions with the mandatory JSON format instructions
        val prompt = """
            $customInstructions

            MANDATORY OUTPUT FORMAT:
            You must analyze the image and return a JSON object with the following keys:
            - "rarity": Choose one from [N, R, SR, SSR, UR] based on how epic the image looks.
            - "name": The name of the card.
            - "description": The ability text.
            - "atk": Number 0-5000.
            - "def": Number 0-5000.

            Return ONLY the raw JSON string. Do not include markdown formatting like ```json.
        """.trimIndent()

        val jsonBody = JSONObject()
        jsonBody.put("model", model)
        jsonBody.put("max_tokens", 1000)

        val contentArray = JSONArray()

        val textPart = JSONObject()
        textPart.put("type", "text")
        textPart.put("text", prompt)
        contentArray.put(textPart)

        val imagePart = JSONObject()
        imagePart.put("type", "image_url")
        val imageUrlObj = JSONObject()
        imageUrlObj.put("url", "data:image/jpeg;base64,$base64Image")
        imagePart.put("image_url", imageUrlObj)
        contentArray.put(imagePart)

        val messageObj = JSONObject()
        messageObj.put("role", "user")
        messageObj.put("content", contentArray)

        val messagesArray = JSONArray()
        messagesArray.put(messageObj)

        jsonBody.put("messages", messagesArray)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonBody.toString().toRequestBody(mediaType)

        var finalUrl = apiUrl
        if (!finalUrl.endsWith("/v1/chat/completions")) {
            finalUrl = if (finalUrl.endsWith("/")) finalUrl + "v1/chat/completions" else finalUrl + "/v1/chat/completions"
        }

        // Handle "openai.com" base URL inputs slightly gracefully
        if (apiUrl.contains("api.openai.com") && !apiUrl.contains("v1")) {
             finalUrl = "https://api.openai.com/v1/chat/completions"
        }

        val request = Request.Builder()
            .url(finalUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()

        if (!response.isSuccessful) {
            throw Exception("API Error: ${response.code} - $responseBody")
        }

        try {
            val responseJson = JSONObject(responseBody)
            val content = responseJson.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")

            // Clean markdown code blocks if present
            var cleanContent = content.trim()
            if (cleanContent.startsWith("```json")) {
                cleanContent = cleanContent.substring(7)
            }
            if (cleanContent.startsWith("```")) {
                cleanContent = cleanContent.substring(3)
            }
            if (cleanContent.endsWith("```")) {
                cleanContent = cleanContent.substring(0, cleanContent.length - 3)
            }

            val cardJson = JSONObject(cleanContent.trim())

            return CardData(
                rarity = cardJson.optString("rarity", "N"),
                name = cardJson.optString("name", "Unknown"),
                description = cardJson.optString("description", "No Data"),
                atk = cardJson.optString("atk", "0"),
                def = cardJson.optString("def", "0")
            )

        } catch (e: Exception) {
            Log.e("VLMService", "Parse Error", e)
            throw Exception("Failed to parse AI response: $responseBody")
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        var width = bitmap.width
        var height = bitmap.height

        if (width <= maxSize && height <= maxSize) return bitmap

        val ratio = width.toFloat() / height.toFloat()
        if (width > height) {
            width = maxSize
            height = (width / ratio).toInt()
        } else {
            height = maxSize
            width = (height * ratio).toInt()
        }
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
