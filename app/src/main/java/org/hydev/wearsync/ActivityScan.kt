package org.hydev.wearsync

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.welie.blessed.BluetoothPeripheral
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.consumeAsFlow
import org.hydev.wearsync.bles.BluetoothHandler
import org.hydev.wearsync.bles.ObservationUnit
import org.hydev.wearsync.databinding.ActivityScanBinding
import timber.log.Timber
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class ActivityScan : AppCompatActivity() {
    lateinit var binding: ActivityScanBinding

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dateFormat: DateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH)
    private val enableBluetoothRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                // Bluetooth has been enabled
                checkPermissions()
            } else {
                // Bluetooth has not been enabled, try again
                askToEnableBluetooth()
            }
        }

    private val bluetoothManager by lazy {
        applicationContext.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
    }

    private lateinit var bluetoothHandler: BluetoothHandler


    private fun askToEnableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        enableBluetoothRequest.launch(enableBtIntent)
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        registerReceiver(
            locationServiceStateReceiver,
            IntentFilter(LocationManager.MODE_CHANGED_ACTION)
        )
    }

    override fun onResume() {
        super.onResume()
        if (bluetoothManager.adapter != null) {
            if (!isBluetoothEnabled) askToEnableBluetooth() else checkPermissions()
        } else {
            Timber.e("This device has no Bluetooth hardware")
        }
    }

    private val isBluetoothEnabled: Boolean
        get() = bluetoothManager.adapter?.isEnabled ?: false

    private val central get() = bluetoothHandler.central

    @SuppressLint("MissingPermission")
    private fun initBluetoothHandler() {
        if (this::bluetoothHandler.isInitialized) return
        bluetoothHandler = BluetoothHandler.getInstance(applicationContext)

        println("OnCreate called, Initializing...")

        // List bonded device addresses
        val pairedDevices = bluetoothManager.adapter.bondedDevices.toList()
        val pairedAddresses = pairedDevices.map { it.address }.toSet()

        // Scan devices
        val scannedDevices = ArrayList<BluetoothPeripheral>()
        central.scanForPeripherals({ peripheral, scanResult ->
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
            central.stopScan()
            bluetoothHandler.connectPeripheral(scannedDevices[position]) {
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
            bluetoothHandler.connectAddress(dev.address) {
                view.snack("✅ Connected.")
            }
        }

        collectHeartRate(bluetoothHandler)
        collectPulseOxContinuous(bluetoothHandler)
        collectPulseOxSpot(bluetoothHandler)
        collectTemperature(bluetoothHandler)
        collectWeight(bluetoothHandler)
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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(locationServiceStateReceiver)
    }

    private val locationServiceStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action != null && action == LocationManager.MODE_CHANGED_ACTION) {
                val isEnabled = areLocationServicesEnabled()
                Timber.i("Location service state changed to: %s", if (isEnabled) "on" else "off")
                checkPermissions()
            }
        }
    }

    private fun getPeripheral(peripheralAddress: String): BluetoothPeripheral {
        return bluetoothHandler.central.getPeripheral(peripheralAddress)
    }

    private fun checkPermissions() {
        val missingPermissions = getMissingPermissions(requiredPermissions)
        if (missingPermissions.isNotEmpty()) {
            requestPermissions(missingPermissions, ACCESS_LOCATION_REQUEST)
        } else {
            checkIfLocationIsNeeded()
        }
    }

    private fun getMissingPermissions(requiredPermissions: Array<String>): Array<String> {
        val missingPermissions: MutableList<String> = ArrayList()
        for (requiredPermission in requiredPermissions) {
            if (applicationContext.checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(requiredPermission)
            }
        }
        return missingPermissions.toTypedArray()
    }

    private val requiredPermissions: Array<String>
        get() {
            val targetSdkVersion = applicationInfo.targetSdkVersion
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetSdkVersion >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetSdkVersion >= Build.VERSION_CODES.Q) {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            } else arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

    private fun checkIfLocationIsNeeded() {
        val targetSdkVersion = applicationInfo.targetSdkVersion
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && targetSdkVersion < Build.VERSION_CODES.S) {
            // Check if Location services are on because they are required to make scanning work for SDK < 31
            if (checkLocationServices()) {
                initBluetoothHandler()
            }
        } else {
            initBluetoothHandler()
        }
    }

    private fun areLocationServicesEnabled(): Boolean {
        val locationManager =
            applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled =
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            isGpsEnabled || isNetworkEnabled
        }
    }

    private fun checkLocationServices(): Boolean {
        return if (!areLocationServicesEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Location services are not enabled")
                .setMessage("Scanning for Bluetooth peripherals requires locations services to be enabled.") // Want to enable?
                .setPositiveButton("Enable") { dialogInterface, _ ->
                    dialogInterface.cancel()
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    // if this button is clicked, just close
                    // the dialog box and do nothing
                    dialog.cancel()
                }
                .create()
                .show()
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Check if all permission were granted
        var allGranted = true
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false
                break
            }
        }
        if (allGranted) {
            checkIfLocationIsNeeded()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Location permission is required for scanning Bluetooth peripherals")
                .setMessage("Please grant permissions")
                .setPositiveButton("Retry") { dialogInterface, _ ->
                    dialogInterface.cancel()
                    checkPermissions()
                }
                .create()
                .show()
        }
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val ACCESS_LOCATION_REQUEST = 2
    }
}