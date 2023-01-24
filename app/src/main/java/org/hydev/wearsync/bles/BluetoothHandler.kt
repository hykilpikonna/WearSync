package org.hydev.wearsync.bles

import android.content.Context
import com.welie.blessed.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import org.hydev.wearsync.bles.decoders.*
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.nio.ByteOrder
import java.util.*

internal class BluetoothHandler private constructor(context: Context) {
    private var currentTimeCounter = 0
    val batteryChannel = Channel<BatteryMeasurement>(UNLIMITED)
    val heartRateChannel = Channel<HeartRateMeasurement>(UNLIMITED)
    val bloodPressureChannel = Channel<BloodPressureMeasurement>(UNLIMITED)
    val glucoseChannel = Channel<GlucoseMeasurement>(UNLIMITED)
    val pulseOxSpotChannel = Channel<PulseOximeterSpotMeasurement>(UNLIMITED)
    val pulseOxContinuousChannel = Channel<PulseOximeterContinuousMeasurement>(UNLIMITED)
    val temperatureChannel = Channel<TemperatureMeasurement>(UNLIMITED)
    val weightChannel = Channel<WeightMeasurement>(UNLIMITED)

    private fun handlePeripheral(peripheral: BluetoothPeripheral) {
        scope.launch {
            try {
                Timber.i("MTU is ${peripheral.requestMtu(185)}")
                peripheral.requestConnectionPriority(ConnectionPriority.HIGH)
                Timber.i("RSSI is ${peripheral.readRemoteRssi()}")
                Timber.i("Received: ${peripheral.read(ManufacturerDecoder())}")
                Timber.i("Received: ${peripheral.read(ModelDecoder())}")
                Timber.i("Battery level: ${peripheral.read(BatteryDecoder())}")

                // Write Current Time if possible
                peripheral.getCharacteristic(CTS_SERVICE_UUID, CURRENT_TIME_CHARACTERISTIC_UUID)?.let {
                    // If it has the write property we write the current time
                    if (it.supportsWritingWithResponse()) {
                        // Write the current time unless it is an Omron device
                        if (!peripheral.name.contains("BLEsmart_", true)) {
                            val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
                            parser.setCurrentTime(Calendar.getInstance())
                            peripheral.writeCharacteristic(it, parser.value, WriteType.WITH_RESPONSE)
                        }
                    }
                }

                peripheral.setupNotification(batteryChannel, BatteryDecoder())
                peripheral.setupNotification(heartRateChannel, HeartRateDecoder())
                peripheral.setupNotification(temperatureChannel, TemperatureDecoder())
                peripheral.setupNotification(weightChannel, WeightDecoder())
                peripheral.setupNotification(bloodPressureChannel, BloodPressureDecoder())
                peripheral.setupNotification(pulseOxSpotChannel, PLXSpotDecoder())
                peripheral.setupNotification(pulseOxContinuousChannel, PLXContinuousDecoder())
                setupGLXnotifications(peripheral)
                setupCTSnotifications(peripheral)

                peripheral.getCharacteristic(CONTOUR_SERVICE_UUID, CONTOUR_CLOCK)?.let {
                    writeContourClock(peripheral)
                }
            } catch (e: IllegalArgumentException) {
                Timber.e(e)
            } catch (b: GattException) {
                Timber.e(b)
            }
        }
    }

    private suspend fun writeContourClock(peripheral: BluetoothPeripheral) {
        val calendar = Calendar.getInstance()
        val offsetInMinutes = calendar.timeZone.rawOffset / 60000
        calendar.timeZone = TimeZone.getTimeZone("UTC")
        val parser = BluetoothBytesParser(ByteOrder.LITTLE_ENDIAN)
        parser.setIntValue(1, BluetoothBytesParser.FORMAT_UINT8)
        parser.setIntValue(calendar[Calendar.YEAR], BluetoothBytesParser.FORMAT_UINT16)
        parser.setIntValue(calendar[Calendar.MONTH] + 1, BluetoothBytesParser.FORMAT_UINT8)
        parser.setIntValue(calendar[Calendar.DAY_OF_MONTH], BluetoothBytesParser.FORMAT_UINT8)
        parser.setIntValue(calendar[Calendar.HOUR_OF_DAY], BluetoothBytesParser.FORMAT_UINT8)
        parser.setIntValue(calendar[Calendar.MINUTE], BluetoothBytesParser.FORMAT_UINT8)
        parser.setIntValue(calendar[Calendar.SECOND], BluetoothBytesParser.FORMAT_UINT8)
        parser.setIntValue(offsetInMinutes, BluetoothBytesParser.FORMAT_SINT16)
        peripheral.writeCharacteristic(CONTOUR_SERVICE_UUID, CONTOUR_CLOCK, parser.value, WriteType.WITH_RESPONSE)
    }

    private suspend fun setupCTSnotifications(peripheral: BluetoothPeripheral) {
        peripheral.getCharacteristic(CTS_SERVICE_UUID, CURRENT_TIME_CHARACTERISTIC_UUID)?.let { currentTimeCharacteristic ->
            peripheral.observe(currentTimeCharacteristic) { value ->
                val parser = BluetoothBytesParser(value)
                val currentTime = parser.dateTime
                Timber.i("Received device time: %s", currentTime)

                // Deal with Omron devices where we can only write currentTime under specific conditions
                val name = peripheral.name
                if (name.contains("BLEsmart_", true)) {
                    peripheral.getCharacteristic(BLP_SERVICE_UUID, BLP_MEASUREMENT_CHARACTERISTIC_UUID)?.let {
                        val isNotifying = peripheral.isNotifying(it)
                        if (isNotifying) currentTimeCounter++

                        // We can set device time for Omron devices only if it is the first notification and currentTime is more than 10 min from now
                        val interval = Math.abs(Calendar.getInstance().timeInMillis - currentTime.time)
                        if (currentTimeCounter == 1 && interval > 10 * 60 * 1000) {
                            parser.setCurrentTime(Calendar.getInstance())
                            scope.launch {
                                peripheral.writeCharacteristic(it, parser.value, WriteType.WITH_RESPONSE)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun setupGLXnotifications(peripheral: BluetoothPeripheral) {
        peripheral.setupNotification(glucoseChannel, GlucoseDecoder())

        peripheral.getCharacteristic(GLUCOSE_SERVICE_UUID, GLUCOSE_RECORD_ACCESS_POINT_CHARACTERISTIC_UUID)?.let {
            val result = peripheral.observe(it) { value ->
                Timber.d("record access response: ${value.asHexString()}")
            }

            if (result) {
                writeGetAllGlucoseMeasurements(peripheral)
            }
        }
    }

    private suspend fun writeGetAllGlucoseMeasurements(peripheral: BluetoothPeripheral) {
        val OP_CODE_REPORT_STORED_RECORDS: Byte = 1
        val OPERATOR_ALL_RECORDS: Byte = 1
        val command = byteArrayOf(OP_CODE_REPORT_STORED_RECORDS, OPERATOR_ALL_RECORDS)
        peripheral.writeCharacteristic(GLUCOSE_SERVICE_UUID, GLUCOSE_RECORD_ACCESS_POINT_CHARACTERISTIC_UUID, command, WriteType.WITH_RESPONSE)
    }

    suspend fun <T> BluetoothPeripheral.setupNotification(channel: Channel<T>, dec: IDecoder<T>)
    {
        getCharacteristic(dec.sid, dec.cid)?.let {
            observe(it) { value ->
                val measurement = dec.decode(value)
                channel.trySend(measurement)
                Timber.d(measurement.toString())
            }
        }
    }

    /**
     * Scan and connect to a peripheral at a specific address
     */
    fun connectAddress(address: String, callback: (BluetoothPeripheral) -> Unit = {}) {
        central.stopScan()
        central.scanForPeripheralsWithAddresses(arrayOf(address), { peripheral, scanResult ->
            if (peripheral.address != address) return@scanForPeripheralsWithAddresses

            central.stopScan()
            connectPeripheral(peripheral) { callback(peripheral) }
        }, {})
    }

    fun connectPeripheral(peripheral: BluetoothPeripheral, callback: () -> Unit = {}) {
        peripheral.observeBondState {
            Timber.i("Bond state is $it")
        }

        scope.launch {
            try {
                central.connectPeripheral(peripheral)
                callback()
            } catch (connectionFailed: ConnectionFailedException) {
                Timber.e("connection failed")
            }
        }
    }

    companion object {
        // UUIDs for the Blood Pressure service (BLP)
        val BLP_SERVICE_UUID = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")
        val BLP_MEASUREMENT_CHARACTERISTIC_UUID = UUID.fromString("00002A35-0000-1000-8000-00805f9b34fb")

        // UUIDs for the Current Time service (CTS)
        val CTS_SERVICE_UUID = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
        val CURRENT_TIME_CHARACTERISTIC_UUID = UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb")

        val GLUCOSE_SERVICE_UUID = UUID.fromString("00001808-0000-1000-8000-00805f9b34fb")
        val GLUCOSE_RECORD_ACCESS_POINT_CHARACTERISTIC_UUID = UUID.fromString("00002A52-0000-1000-8000-00805f9b34fb")

        // Contour Glucose Service
        val CONTOUR_SERVICE_UUID = UUID.fromString("00000000-0002-11E2-9E96-0800200C9A66")
        val CONTOUR_CLOCK = UUID.fromString("00001026-0002-11E2-9E96-0800200C9A66")

        private var instance: BluetoothHandler? = null
        val Context.ble get(): BluetoothHandler {
            if (instance == null) {
                instance = BluetoothHandler(this)
            }
            return instance!!
        }

        suspend fun <T> BluetoothPeripheral.read(decoder: IDecoder<T>) =
            getCharacteristic(decoder.sid, decoder.cid)?.let { decoder.decode(readCharacteristic(it)) }
    }

    @JvmField
    var central: BluetoothCentralManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        Timber.plant(DebugTree())
        central = BluetoothCentralManager(context)

        central.observeConnectionState { peripheral, state ->
            Timber.i("Peripheral '${peripheral.name}' is $state")
            when (state) {
                ConnectionState.CONNECTED -> handlePeripheral(peripheral)
                ConnectionState.DISCONNECTED -> scope.launch {
                    delay(15000)

                    // Check if this peripheral should still be auto connected
                    if (central.getPeripheral(peripheral.address).getState() == ConnectionState.DISCONNECTED) {
                        central.autoConnectPeripheral(peripheral)
                    }
                }
                else -> {
                }
            }
        }
    }
}