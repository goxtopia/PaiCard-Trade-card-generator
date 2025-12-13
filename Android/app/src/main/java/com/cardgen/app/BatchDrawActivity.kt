package com.cardgen.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class BatchDrawActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fabCreatePack: FloatingActionButton
    private lateinit var adapter: PackListAdapter

    private var packs: List<PackRepository.Pack> = ArrayList()
    private val executor = Executors.newSingleThreadExecutor()

    private val packUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == CardGenerationService.ACTION_PACK_UPDATED) {
                refreshPacks()
            }
        }
    }

    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val uris = ArrayList<android.net.Uri>()

            if (data?.clipData != null) {
                val count = data.clipData!!.itemCount
                for (i in 0 until count) {
                    uris.add(data.clipData!!.getItemAt(i).uri)
                }
            } else if (data?.data != null) {
                uris.add(data.data!!)
            }

            if (uris.isNotEmpty()) {
                createNewPack(uris)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_batch_draw)
        supportActionBar?.title = "My Card Packs"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        CardRepository.init(this)
        PackRepository.init(this)

        initViews()
        refreshPacks()
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(packUpdateReceiver, IntentFilter(CardGenerationService.ACTION_PACK_UPDATED), Context.RECEIVER_NOT_EXPORTED)
        refreshPacks() // Ensure fresh state
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(packUpdateReceiver)
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerViewPacks)
        fabCreatePack = findViewById(R.id.fabCreatePack)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = PackListAdapter(packs) { pack ->
            onPackClick(pack)
        }
        recyclerView.adapter = adapter

        fabCreatePack.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            pickImagesLauncher.launch(Intent.createChooser(intent, "Select Pictures for Pack"))
        }
    }

    private fun refreshPacks() {
        packs = PackRepository.getPacks()
            .filter { it.status != PackRepository.PackStatus.OPENED }
            .sortedByDescending { it.createdAt }
        adapter.updateList(packs)
    }

    private fun createNewPack(uris: List<android.net.Uri>) {
        executor.execute {
            val items = ArrayList<PackRepository.PackItem>()
            for (uri in uris) {
                 try {
                     // Copy to internal storage to persist permission
                     val inputStream = contentResolver.openInputStream(uri)
                     val bytes = inputStream?.readBytes()
                     if (bytes != null) {
                         val md5 = CardRepository.calculateMD5(bytes)

                         // Save local copy
                         val filename = "pack_img_${md5}.jpg"
                         val file = java.io.File(filesDir, filename)
                         if (!file.exists()) {
                             java.io.FileOutputStream(file).use { out ->
                                 out.write(bytes)
                             }
                         }

                         // Use local path instead of URI
                         items.add(PackRepository.PackItem(file.absolutePath, md5))
                     }
                 } catch (e: Exception) {
                     e.printStackTrace()
                 }
            }

            if (items.isNotEmpty()) {
                val pack = PackRepository.createPack(this, items)

                // Start Service
                val intent = Intent(this, CardGenerationService::class.java)
                intent.action = CardGenerationService.ACTION_PROCESS_PACK
                intent.putExtra(CardGenerationService.EXTRA_PACK_ID, pack.id)
                startService(intent)

                runOnUiThread {
                    refreshPacks()
                }
            }
        }
    }

    private fun onPackClick(pack: PackRepository.Pack) {
        if (pack.status == PackRepository.PackStatus.READY || pack.status == PackRepository.PackStatus.OPENED) {
             val intent = Intent(this, PackOpeningActivity::class.java)
             intent.putExtra("packId", pack.id)
             startActivity(intent)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // Adapter
    class PackListAdapter(
        private var items: List<PackRepository.Pack>,
        private val onClick: (PackRepository.Pack) -> Unit
    ) : RecyclerView.Adapter<PackListAdapter.ViewHolder>() {

        fun updateList(newItems: List<PackRepository.Pack>) {
            items = newItems
            notifyDataSetChanged()
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
             val tvId: TextView = view.findViewById(R.id.tvPackId)
             val tvStatus: TextView = view.findViewById(R.id.tvPackStatus)
             val btnOpen: Button = view.findViewById(R.id.btnOpenPack)
             val progressBar: ProgressBar = view.findViewById(R.id.progressBarPack)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pack_list, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val pack = items[position]
            val date = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(pack.createdAt))

            holder.tvId.text = "Card Pack (${date})"

            when (pack.status) {
                PackRepository.PackStatus.PROCESSING -> {
                    holder.tvStatus.text = "Processing..."
                    holder.tvStatus.setTextColor(android.graphics.Color.GRAY)
                    holder.btnOpen.visibility = View.GONE
                    holder.progressBar.visibility = View.VISIBLE
                }
                PackRepository.PackStatus.READY -> {
                    holder.tvStatus.text = "Ready to Open"
                    holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#f1c40f")) // Gold
                    holder.btnOpen.visibility = View.VISIBLE
                    holder.btnOpen.text = "OPEN"
                    holder.progressBar.visibility = View.GONE
                }
                PackRepository.PackStatus.OPENED -> {
                    holder.tvStatus.text = "Opened"
                    holder.tvStatus.setTextColor(android.graphics.Color.GREEN)
                    holder.btnOpen.visibility = View.VISIBLE
                    holder.btnOpen.text = "VIEW"
                    holder.progressBar.visibility = View.GONE
                }
            }

            holder.btnOpen.setOnClickListener { onClick(pack) }
            holder.itemView.setOnClickListener { onClick(pack) }
        }

        override fun getItemCount() = items.size
    }
}
