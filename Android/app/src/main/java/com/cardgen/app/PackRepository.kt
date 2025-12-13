package com.cardgen.app

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

object PackRepository {

    private const val FILE_NAME = "packs.json"
    private val gson = Gson()
    private var packs: CopyOnWriteArrayList<Pack> = CopyOnWriteArrayList()
    private var isLoaded = false

    enum class PackStatus {
        PROCESSING, READY, OPENED
    }

    data class PackItem(
        val uri: String,
        val md5: String
    )

    data class Pack(
        val id: String = UUID.randomUUID().toString(),
        val createdAt: Long = System.currentTimeMillis(),
        var status: PackStatus = PackStatus.PROCESSING,
        val items: List<PackItem>
    )

    fun init(context: Context) {
        if (!isLoaded) {
            loadPacks(context)
            isLoaded = true
        }
    }

    private fun loadPacks(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            try {
                val json = file.readText()
                val type = object : TypeToken<CopyOnWriteArrayList<Pack>>() {}.type
                val loaded: CopyOnWriteArrayList<Pack>? = gson.fromJson(json, type)
                if (loaded != null) {
                    packs = loaded
                }
            } catch (e: Exception) {
                Log.e("PackRepository", "Failed to load packs", e)
            }
        }
    }

    private fun savePacks(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            val json = gson.toJson(packs)
            FileOutputStream(file).use { it.write(json.toByteArray()) }
        } catch (e: Exception) {
            Log.e("PackRepository", "Failed to save packs", e)
        }
    }

    fun createPack(context: Context, items: List<PackItem>): Pack {
        val pack = Pack(items = items)
        packs.add(0, pack) // Add to top
        savePacks(context)
        return pack
    }

    fun getPacks(): List<Pack> {
        return packs
    }

    fun getPack(id: String): Pack? {
        return packs.find { it.id == id }
    }

    fun updatePackStatus(context: Context, id: String, status: PackStatus) {
        val pack = packs.find { it.id == id }
        if (pack != null) {
            pack.status = status
            savePacks(context)
        }
    }

    // Check if all items in the pack have data in CardRepository
    fun checkPackReadiness(context: Context, id: String) {
        val pack = packs.find { it.id == id } ?: return
        if (pack.status == PackStatus.OPENED) return

        val allReady = pack.items.all { CardRepository.hasCard(it.md5) }
        if (allReady && pack.status != PackStatus.READY) {
            pack.status = PackStatus.READY
            savePacks(context)
        }
    }
}
