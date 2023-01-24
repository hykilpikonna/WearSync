package org.hydev.wearsync.bles

import android.content.Context
import com.welie.blessed.*
import kotlinx.coroutines.*
import org.hydev.wearsync.BluePeri
import org.hydev.wearsync.bles.decoders.*
import timber.log.Timber
import timber.log.Timber.DebugTree
import kotlin.reflect.KClass

internal class BluetoothHandler private constructor(context: Context) {
    var central: BluetoothCentralManager
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun BluePeri.handle() {
        scope.launch {
            try {
                Timber.i("MTU is ${requestMtu(185)}")
                requestConnectionPriority(ConnectionPriority.HIGH)
                Timber.i("RSSI is ${readRemoteRssi()}")
                Timber.i("Received: ${read(ManufacturerDecoder())}")
                Timber.i("Received: ${read(ModelDecoder())}")
                Timber.i("Battery level: ${read(BatteryDecoder())}")

                decoders.forEach { observe(it) }
            }
            catch (e: IllegalArgumentException) { Timber.e(e) }
            catch (b: GattException) { Timber.e(b) }
        }
    }

    val listeners = HashMap<KClass<*>, MutableList<(Any) -> Unit>>()

    @Suppress("UNCHECKED_CAST")
    inline fun <M : Any, reified D : IDecoder<out M>> observe(crossinline cb: (M) -> Unit) {
        (listeners[D::class] ?: error("Cannot observe unknown decoder class ${D::class}"))
            .add { cb(it as M) }
    }

    inline fun <reified D : IDecoder<out Any>> observeAny(noinline cb: (Any) -> Unit) {
        (listeners[D::class] ?: error("Cannot observe unknown decoder class ${D::class}")).add(cb)
    }

    private suspend fun <T : Any> BluePeri.observe(dec: IDecoder<T>) {
        val cls = dec::class
        listeners[cls] = ArrayList()

        observe(dec) {
            listeners[cls]?.forEach { cb -> cb(it) }
        }
    }

    /**
     * Scan and connect to a peripheral at a specific address
     */
    fun connectAddress(address: String, callback: (BluePeri) -> Unit = {}) {
        Timber.d("Scanning for device with address $address")
        central.stopScan()
        central.scanForPeripheralsWithAddresses(arrayOf(address), { peri, _ ->
            if (peri.address != address) return@scanForPeripheralsWithAddresses
            Timber.d("Device found, connecting...")

            central.stopScan()
            connectPeripheral(peri) { callback(peri) }
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
        val decoders = listOf(BatteryDecoder(), BloodPressureDecoder(), GlucoseDecoder(), HeartRateDecoder(),
            PLXSpotDecoder(), PLXContinuousDecoder(), TemperatureDecoder(), WeightDecoder())

        private var instance: BluetoothHandler? = null
        val Context.ble get(): BluetoothHandler {
            if (instance == null) {
                instance = BluetoothHandler(this)
            }
            return instance!!
        }

        suspend fun <T> BluePeri.read(decoder: IDecoder<T>) =
            getCharacteristic(decoder.sid, decoder.cid)?.let { decoder.decode(readCharacteristic(it)) }

        /**
         * Observe a specific measurement
         */
        private suspend fun <T> BluePeri.observe(dec: IDecoder<T>, callback: (T) -> Unit) {
            getCharacteristic(dec.sid, dec.cid)?.let {
                observe(it) { value ->
                    val measurement = dec.decode(value)
                    callback(measurement)
                    Timber.d(measurement.toString())
                }
            }
            dec.additionalSetup(this)
        }
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