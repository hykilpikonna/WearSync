package org.hydev.wearsync

import android.annotation.SuppressLint
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import org.hydev.wearsync.ActivityPermissions.Companion.hasPermissions
import org.hydev.wearsync.bles.BluetoothHandler.Companion.ble
import org.hydev.wearsync.databinding.ActivityMainBinding
import java.util.*

class MainActivity : AppCompatActivity()
{
    lateinit var binding: ActivityMainBinding
    lateinit var influx: InfluxDBClientKotlin

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

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.content.recycler.let {
            it.adapter = RecordAdapter(records)
            it.layoutManager = LinearLayoutManager(this)
        }

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
        connectCallback.launch(intent<ActivityScan>())
    }

    val connectCallback = actCallback {
        addRecord("Device connected")

        // Start collection
        binding.content.tvDevice.text = "Configured Device: ${prefs.chosenDevice}"
        binding.content.tvValue.text = "Service started!"

        startCollect()
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
            else -> super.onOptionsItemSelected(item)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
        while (records.size > 20) records.remove(0)
        records.add(t)
        runOnUiThread { binding.content.recycler.adapter?.notifyDataSetChanged() }
    }

    fun startCollect()
    {
        collect(ble.heartRateChannel)
        collect(ble.batteryChannel)
    }

    private fun <T : Any> collect(channel: Channel<T>) {
        scope.launch {
            channel.consumeAsFlow().collect {
                try {
                    addRecord(it)
                    influx add it
                }
                catch (e: Exception) {
                    addRecord(e)
                }
            }
        }
    }
}