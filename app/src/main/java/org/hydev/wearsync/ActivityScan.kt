package org.hydev.wearsync

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.hydev.wearsync.bles.BluetoothHandler.Companion.ble
import org.hydev.wearsync.databinding.ActivityScanBinding
import java.util.*

class ActivityScan : AppCompatActivity() {
    lateinit var binding: ActivityScanBinding
    lateinit var view: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        view = binding.root

        initBluetoothHandler()
    }

    @SuppressLint("MissingPermission")
    private fun initBluetoothHandler() {
        println("OnCreate called, Initializing...")

        // Device exists
        prefs.chosenDevice?.let { addr ->
            ble.connectAddress(addr) { connected(addr) }
            return
        }

        // List bonded device addresses
        val pairedDevices = blueMan().adapter.bondedDevices.toList()
        val pairedAddresses = pairedDevices.map { it.address }.toSet()

        // Scan devices
        val scannedDevices = ArrayList<BluePeri>()
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
                connected(scannedDevices[position].address)
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
                connected(dev.address)
            }
        }
    }

    fun connected(address: String)
    {
        view.snack("✅ Connected.")
        prefs.chosenDevice = address
        finish()
    }
}