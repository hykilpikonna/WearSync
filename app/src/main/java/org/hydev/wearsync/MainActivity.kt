package org.hydev.wearsync

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.consumeAsFlow
import org.hydev.wearsync.ActivityPermissions.Companion.hasPermissions
import org.hydev.wearsync.bles.BluetoothHandler
import org.hydev.wearsync.bles.BluetoothHandler.Companion.ble
import org.hydev.wearsync.bles.ObservationUnit
import org.hydev.wearsync.databinding.ActivityMainBinding
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity()
{
    lateinit var binding: ActivityMainBinding

    val enableBluetoothRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                // Bluetooth has been enabled
            } else {
                // Bluetooth has not been enabled, try again
                ensureBluetooth()
            }
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

        if (!hasPermissions()) act<ActivityPermissions>()

        // Scan for devices
        act<ActivityScan>()
        binding.content.tvDevice.text = "Configured Device: $chosenDevice"

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
            R.id.action_settings -> true
            R.id.action_scan -> {
                act<ActivityScan>()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormat: DateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH)

    fun startCollect()
    {
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
                    binding.content.tvValue.text = String.format(Locale.ENGLISH, "%d bpm", it.pulse)
                }
            }
        }
    }

    private fun collectPulseOxContinuous(bluetoothHandler: BluetoothHandler) {
        scope.launch {
            bluetoothHandler.pulseOxContinuousChannel.consumeAsFlow().collect {
                withContext(Dispatchers.Main) {
                    binding.content.tvValue.text = String.format(
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
                    binding.content.tvValue.text = String.format(
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
                    binding.content.tvValue.text = String.format(
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
                    binding.content.tvValue.text = String.format(
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