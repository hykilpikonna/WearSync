package org.hydev.wearsync

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.influxdb.client.kotlin.InfluxDBClientKotlin
import kotlinx.coroutines.*
import org.hydev.wearsync.ActivityPermissions.Companion.hasPermissions
import org.hydev.wearsync.databinding.ActivityMainBinding
import java.util.*

class MainActivity : AppCompatActivity()
{
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var binding: ActivityMainBinding
    lateinit var influx: InfluxDBClientKotlin

    var log = true

    val enableBluetoothRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != RESULT_OK) ensureBluetooth()
    }

    fun ensureBluetooth() {
        if (blueMan().adapter?.isEnabled == false)
            enableBluetoothRequest.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        // Bind activity
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        // Create recycler
        binding.content.recycler.let {
            it.adapter = RecordAdapter(records)
            it.layoutManager = LinearLayoutManager(this)
        }

        // Create notification channel
        val chan = NotificationChannel(MyService.NOTIF_CHANNEL_ID, "Keep-alive Notification",
            NotificationManager.IMPORTANCE_MIN)
        getSysServ<NotificationManager>().createNotificationChannel(chan)

        if (!hasPermissions()) permissionCallback.launch(intent<ActivityPermissions>())
        else afterPermissions()
    }

    val permissionCallback = actCallback { afterPermissions() }
    fun afterPermissions() {
        addRecord("Permissions granted")

        scope.launch {
            // Open settings if influx database is inaccessible
            if (runCatching { prefs.influxPing() }.isFailure) settingsCallback.launch(intent<ActivitySettings>())
            else runOnUiThread { afterSettings() }
        }
    }

    val settingsCallback = actCallback { afterSettings() }
    fun afterSettings() {
        addRecord("InfluxDB settings checked")

        // Create client
        influx = prefs.createInflux()

        // Scan for devices
        if (prefs.chosenDevice == null) pairCallback.launch(intent<ActivityScan>())
        else afterPair()
    }

    val pairCallback = actCallback { afterPair() }
    fun afterPair() {
        addRecord("Device configured")

        // Start collection
        binding.content.tvDevice.text = "Configured Device: ${prefs.chosenDevice}"
        binding.content.tvValue.text = "Service started!"

        act<MyService>()
    }

    override fun onResume()
    {
        super.onResume()
        ensureBluetooth()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean
    {
        return when (item.itemId)
        {
            R.id.action_settings -> {
                act<ActivitySettings>()
                true
            }
            R.id.action_scan -> {
                act<ActivityScan>()
                true
            }
            R.id.action_stop_logging -> {
                log = false
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private val records = ArrayList<Any>()

    class RecordAdapter(val records: List<Any>) : RecyclerView.Adapter<RecordAdapter.ViewHolder>() {
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val type = itemView.findViewById<TextView>(R.id.rrType)
            val value = itemView.findViewById<TextView>(R.id.rrValue)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder
        {
            return ViewHolder(LayoutInflater.from(parent.context)
                .inflate(R.layout.recycler_record, parent, false))
        }

        override fun getItemCount() = records.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int)
        {
            val record = records[position]
            val cls = record.javaClass.name
            holder.type.text = cls
            holder.value.text = record.toString().replace(cls, "")

            if (record is Exception) holder.type.setTextColor(Color.parseColor("#FF6C52"))
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun addRecord(t: Any) {
        if (!log) return
        while (records.size > 20) records.remove(0)
        records.add(t)
        runOnUiThread { binding.content.recycler.adapter?.notifyDataSetChanged() }
    }
}