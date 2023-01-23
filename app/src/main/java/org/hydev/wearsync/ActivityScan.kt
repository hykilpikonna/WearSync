package org.hydev.wearsync

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import com.welie.blessed.BluetoothPeripheral
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.consumeAsFlow
import org.hydev.wearsync.bles.BluetoothHandler
import org.hydev.wearsync.bles.BluetoothHandler.Companion.ble
import org.hydev.wearsync.bles.ObservationUnit
import org.hydev.wearsync.databinding.ActivityScanBinding
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class ActivityScan : AppCompatActivity() {
    lateinit var binding: ActivityScanBinding

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormat: DateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH)

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

        collectHeartRate(ble)
        collectPulseOxContinuous(ble)
        collectPulseOxSpot(ble)
        collectTemperature(ble)
        collectWeight(ble)
    }

    private fun collectHeartRate(bluetoothHandler: BluetoothHandler) {
        scope.launch {
            bluetoothHandler.heartRateChannel.consumeAsFlow().collect {
                withContext(Dispatchers.Main) {
                    binding.mainText.text = String.format(Locale.ENGLISH, "%d bpm", it.pulse)
                }
            }
        }
    }

    private fun collectPulseOxContinuous(bluetoothHandler: BluetoothHandler) {
        scope.launch {
            bluetoothHandler.pulseOxContinuousChannel.consumeAsFlow().collect {
                withContext(Dispatchers.Main) {
                    binding.mainText.text = String.format(
                        Locale.ENGLISH,
                        "SpO2 %d%%,  Pulse %d bpm\n%s\n\nfrom %s",
                        it.spO2,
                        it.pulseRate,
                        dateFormat.format(Calendar.getInstance())
                    )
                }
            }
        }
    }

    private fun collectPulseOxSpot(bluetoothHandler: BluetoothHandler) {
        scope.launch {
            bluetoothHandler.pulseOxSpotChannel.consumeAsFlow().collect {
                withContext(Dispatchers.Main) {
                    binding.mainText.text = String.format(
                        Locale.ENGLISH,
                        "SpO2 %d%%,  Pulse %d bpm\n",
                        it.spO2,
                        it.pulseRate
                    )
                }
            }
        }
    }

    private fun collectTemperature(bluetoothHandler: BluetoothHandler) {
        scope.launch {
            bluetoothHandler.temperatureChannel.consumeAsFlow().collect {
                withContext(Dispatchers.Main) {
                    binding.mainText.text = String.format(
                        Locale.ENGLISH,
                        "%.1f %s (%s)\n%s\n",
                        it.temperatureValue,
                        if (it.unit == ObservationUnit.Celsius) "celsius" else "fahrenheit",
                        it.type,
                        dateFormat.format(it.timestamp ?: Calendar.getInstance())
                    )
                }
            }
        }
    }

    private fun collectWeight(bluetoothHandler: BluetoothHandler) {
        scope.launch {
            bluetoothHandler.weightChannel.consumeAsFlow().collect {
                withContext(Dispatchers.Main) {
                    binding.mainText.text = String.format(
                        Locale.ENGLISH,
                        "%.1f %s\n%s\n",
                        it.weight, it.unit.toString(),
                        dateFormat.format(it.timestamp ?: Calendar.getInstance())
                    )
                }
            }
        }
    }
}