package org.hydev.wearsync.bles

import android.content.Context
import com.welie.blessed.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import org.hydev.wearsync.BluePeri
import org.hydev.wearsync.bles.decoders.*
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.util.*

internal class BluetoothHandler private constructor(context: Context) {
    var central: BluetoothCentralManager
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val batteryChannel = Channel<BatteryMeasurement>(UNLIMITED)
    val heartRateChannel = Channel<HeartRateMeasurement>(UNLIMITED)
    val bloodPressureChannel = Channel<BloodPressureMeasurement>(UNLIMITED)
    val glucoseChannel = Channel<GlucoseMeasurement>(UNLIMITED)
    val pulseOxSpotChannel = Channel<PulseOximeterSpotMeasurement>(UNLIMITED)
    val pulseOxContinuousChannel = Channel<PulseOximeterContinuousMeasurement>(UNLIMITED)
    val temperatureChannel = Channel<TemperatureMeasurement>(UNLIMITED)
    val weightChannel = Channel<WeightMeasurement>(UNLIMITED)

    private fun BluePeri.handle() {
        scope.launch {
            try {
                Timber.i("MTU is ${requestMtu(185)}")
                requestConnectionPriority(ConnectionPriority.HIGH)
                Timber.i("RSSI is ${readRemoteRssi()}")
                Timber.i("Received: ${read(ManufacturerDecoder())}")
                Timber.i("Received: ${read(ModelDecoder())}")
                Timber.i("Battery level: ${read(BatteryDecoder())}")

                observe(batteryChannel, BatteryDecoder())
                observe(heartRateChannel, HeartRateDecoder())
                observe(temperatureChannel, TemperatureDecoder())
                observe(weightChannel, WeightDecoder())
                observe(bloodPressureChannel, BloodPressureDecoder())
                observe(pulseOxSpotChannel, PLXSpotDecoder())
                observe(pulseOxContinuousChannel, PLXContinuousDecoder())
                setupGLXnotifications(this@handle)
            } catch (e: IllegalArgumentException) {
                Timber.e(e)
            } catch (b: GattException) {
                Timber.e(b)
            }
        }
    }

    private suspend fun setupGLXnotifications(peripheral: BluePeri) {
        peripheral.observe(glucoseChannel, GlucoseDecoder())

        peripheral.getCharacteristic(GLUCOSE_SERVICE_UUID, GLUCOSE_RECORD_ACCESS_POINT_CHARACTERISTIC_UUID)?.let {
            val result = peripheral.observe(it) { value ->
                Timber.d("record access response: ${value.asHexString()}")
            }

            if (result) {
                writeGetAllGlucoseMeasurements(peripheral)
            }
        }
    }

    private suspend fun writeGetAllGlucoseMeasurements(peripheral: BluePeri) {
        val OP_CODE_REPORT_STORED_RECORDS: Byte = 1
        val OPERATOR_ALL_RECORDS: Byte = 1
        val command = byteArrayOf(OP_CODE_REPORT_STORED_RECORDS, OPERATOR_ALL_RECORDS)
        peripheral.writeCharacteristic(GLUCOSE_SERVICE_UUID, GLUCOSE_RECORD_ACCESS_POINT_CHARACTERISTIC_UUID, command, WriteType.WITH_RESPONSE)
    }

    /**
     * Observe a specific mesasurement
     */
    suspend fun <T> BluePeri.observe(dec: IDecoder<T>, callback: (T) -> Unit) {
        getCharacteristic(dec.sid, dec.cid)?.let {
            observe(it) { value ->
                val measurement = dec.decode(value)
                callback(measurement)
                Timber.d(measurement.toString())
            }
        }
    }

    suspend fun <T> BluePeri.observe(chan: Channel<T>, dec: IDecoder<T>) =
        observe(dec) { chan.trySend(it) }

    /**
     * Scan and connect to a peripheral at a specific address
     */
    fun connectAddress(address: String, callback: (BluePeri) -> Unit = {}) {
        central.stopScan()
        central.scanForPeripheralsWithAddresses(arrayOf(address), { peripheral, scanResult ->
            if (peripheral.address != address) return@scanForPeripheralsWithAddresses

            central.stopScan()
            connectPeripheral(peripheral) { callback(peripheral) }
        }, {})
    }

    fun connectPeripheral(peripheral: BluePeri, callback: () -> Unit = {}) {
        peripheral.observeBondState { Timber.i("Bond state is $it") }

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
        val GLUCOSE_SERVICE_UUID = UUID.fromString("00001808-0000-1000-8000-00805f9b34fb")
        val GLUCOSE_RECORD_ACCESS_POINT_CHARACTERISTIC_UUID = UUID.fromString("00002A52-0000-1000-8000-00805f9b34fb")

        private var instance: BluetoothHandler? = null
        val Context.ble get(): BluetoothHandler {
            if (instance == null) {
                instance = BluetoothHandler(this)
            }
            return instance!!
        }

        suspend fun <T> BluePeri.read(decoder: IDecoder<T>) =
            getCharacteristic(decoder.sid, decoder.cid)?.let { decoder.decode(readCharacteristic(it)) }
    }

    init {
        Timber.plant(DebugTree())
        central = BluetoothCentralManager(context)
        central.observeConnectionState { peripheral, state ->
            Timber.i("Peripheral '${peripheral.name}' is $state")
            when (state) {
                ConnectionState.CONNECTED -> peripheral.handle()
                ConnectionState.DISCONNECTED -> scope.launch {
                    delay(15000)

                    // Check if this peripheral should still be auto connected
                    if (central.getPeripheral(peripheral.address).getState() == ConnectionState.DISCONNECTED) {
                        central.autoConnectPeripheral(peripheral)
                    }
                }
                else -> {}
            }
        }
    }
}