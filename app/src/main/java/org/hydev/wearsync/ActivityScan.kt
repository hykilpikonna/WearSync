package org.hydev.wearsync

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.welie.blessed.BluetoothPeripheral
import kotlinx.coroutines.*
import org.hydev.wearsync.bles.BluetoothHandler.Companion.ble
import org.hydev.wearsync.databinding.ActivityScanBinding
import java.util.*

class ActivityScan : AppCompatActivity() {
    lateinit var binding: ActivityScanBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initBluetoothHandler()
    }

    @SuppressLint("MissingPermission")
    private fun initBluetoothHandler() {
        println("OnCreate called, Initializing...")

        // List bonded device addresses
        val pairedDevices = blueMan().adapter.bondedDevices.toList()
        val pairedAddresses = pairedDevices.map { it.address }.toSet()

        // Scan devices
        val scannedDevices = ArrayList<BluetoothPeripheral>()
        ble.central.scanForPeripherals({ peripheral, scanResult ->
            if (peripheral.name.isBlank() || scannedDevices.contains(peripheral)) return@scanForPeripherals

            // Add to scanned devices
            scannedDevices.add(peripheral)
            Handler(Looper.getMainLooper()).post {
                binding.lvScanned.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
                    scannedDevices.map {
                        "${it.name + if (it.address in pairedAddresses) " (Paired)" else ""}\n" +
                                "MAC Address: ${it.address}"
                    })
            }
        }, {})

        // Click scanned device
        binding.lvScanned.setOnItemClickListener { parent, view, position, id ->
            ble.central.stopScan()
            ble.connectPeripheral(scannedDevices[position]) {
                view.snack("✅ Connected.")
            }
        }

        // Format and show bounded device list
        binding.lvPaired.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            pairedDevices.map { "Name: ${it.name}\nMAC Address: ${it.address}" })

        // On click handler
        binding.lvPaired.setOnItemClickListener { parent, view, position, id ->
            // Extract MAC address
            val dev = pairedDevices[position]
            println("Clicked: ${dev.address}")

            view.snack("Connecting...")

            // Scan for the device with the MAC address
            ble.connectAddress(dev.address) {
                view.snack("✅ Connected.")
            }
        }
    }
}