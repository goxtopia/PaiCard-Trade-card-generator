package com.cardgen.app

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.InputStream
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.graphics.Bitmap
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileOutputStream

class BatchDrawActivity : AppCompatActivity() {

    private lateinit var btnPickImages: Button
    private lateinit var statusText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BatchAdapter
    private val processedCards = ArrayList<BatchCardItem>()

    private val vlmService = VLMService()
    private val executor = Executors.newFixedThreadPool(1) // Serial processing
    private val mainHandler = Handler(Looper.getMainLooper())

    data class BatchCardItem(
        val bitmap: Bitmap,
        var cardData: VLMService.CardData? = null,
        var status: String = "Pending" // Pending, Analyzing, Done, Error
    )

    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                for (i in 0 until count) {
                    val uri = data.clipData!!.getItemAt(i).uri
                    loadImage(uri)
                }
            } else if (data?.data != null) {
                val uri = data.data!!
                loadImage(uri)
            }
            processQueue()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_batch_draw)
        supportActionBar?.title = "Batch Draw"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        btnPickImages = findViewById(R.id.btnPickImages)
        statusText = findViewById(R.id.statusText)
        recyclerView = findViewById(R.id.recyclerView)

        adapter = BatchAdapter(processedCards)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        btnPickImages.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            pickImagesLauncher.launch(Intent.createChooser(intent, "Select Pictures"))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun loadImage(uri: android.net.Uri) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            if (bitmap != null) {
                processedCards.add(BatchCardItem(bitmap))
                adapter.notifyItemInserted(processedCards.size - 1)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun processQueue() {
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "")
        val apiUrl = prefs.getString("api_url", "https://api.openai.com")
        val model = prefs.getString("model", "gpt-4o-mini")
        val customPrompt = prefs.getString("custom_prompt", "Create a funny and creative name and ability description for a trading card based on this image.")

        if (apiKey.isNullOrEmpty()) {
            Toast.makeText(this, "Please set API Key in Settings", Toast.LENGTH_LONG).show()
            return
        }

        // Find pending items
        val pendingItems = processedCards.filter { it.status == "Pending" }
        if (pendingItems.isEmpty()) return

        statusText.text = "Processing ${pendingItems.size} images..."
        btnPickImages.isEnabled = false

        executor.execute {
            for ((index, item) in processedCards.withIndex()) {
                if (item.status == "Pending") {
                    mainHandler.post {
                        item.status = "Analyzing..."
                        adapter.notifyItemChanged(index)
                    }

                    try {
                        val data = vlmService.analyzeImage(item.bitmap, apiKey!!, apiUrl!!, model!!, customPrompt!!)
                        saveCardToLibrary(data, item.bitmap)

                        mainHandler.post {
                            item.cardData = data
                            item.status = "Done"
                            adapter.notifyItemChanged(index)
                        }
                    } catch (e: Exception) {
                        mainHandler.post {
                            item.status = "Error: ${e.message}"
                            adapter.notifyItemChanged(index)
                        }
                    }
                }
            }
            mainHandler.post {
                statusText.text = "Batch Complete"
                btnPickImages.isEnabled = true
            }
        }
    }

    private fun saveCardToLibrary(data: VLMService.CardData, bitmap: Bitmap) {
        try {
            val filename = "card_${System.currentTimeMillis()}.png"
            val file = File(filesDir, filename)
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()

            val savedCard = SingleDrawActivity.SavedCard(
                data.name, data.rarity, data.description, data.atk, data.def,
                file.absolutePath, System.currentTimeMillis()
            )

            val prefs = getSharedPreferences("card_library", Context.MODE_PRIVATE)
            val json = prefs.getString("history", "[]")
            val type = object : TypeToken<ArrayList<SingleDrawActivity.SavedCard>>() {}.type
            val list: ArrayList<SingleDrawActivity.SavedCard> = Gson().fromJson(json, type) ?: ArrayList()

            list.add(0, savedCard)
            prefs.edit().putString("history", Gson().toJson(list)).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    inner class BatchAdapter(private val items: List<BatchCardItem>) : RecyclerView.Adapter<BatchAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val img: ImageView = view.findViewById(R.id.itemImg)
            val name: TextView = view.findViewById(R.id.itemName)
            val status: TextView = view.findViewById(R.id.itemStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_batch_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.img.setImageBitmap(item.bitmap)

            if (item.cardData != null) {
                holder.name.text = "${item.cardData!!.name} [${item.cardData!!.rarity}]"
            } else {
                holder.name.text = "Unknown Card"
            }
            holder.status.text = item.status

            if (item.status == "Done") {
                holder.status.setTextColor(android.graphics.Color.GREEN)
            } else if (item.status.startsWith("Error")) {
                holder.status.setTextColor(android.graphics.Color.RED)
            } else {
                holder.status.setTextColor(android.graphics.Color.YELLOW)
            }
        }

        override fun getItemCount() = items.size
    }
}
