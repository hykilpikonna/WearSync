package org.hydev.wearsync

import android.app.Activity
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import kotlin.reflect.KProperty
import kotlin.reflect.full.isSubclassOf


fun View.snack(msg: String) = Snackbar.make(this, msg, Snackbar.LENGTH_LONG)
    .setAction("Action", null).show()

inline fun <reified T> Context.getSysServ() = getSystemService(T::class.java) as T
fun Context.blueMan() = getSysServ<BluetoothManager>()



interface Prefs {
    var chosenDevice: String?

    var infUrl: String?
    var infOrg: String?
    var infBucket: String?
    var infToken: String?

    fun createInflux(): InfluxDBClientKotlin
    suspend fun influxPing()
}

val Context.pref get() = PreferenceManager.getDefaultSharedPreferences(this)
val Context.prefs get() = object : Prefs {
    inner class PrefDelegate {
        operator fun getValue(thisRef: Any?, property: KProperty<*>) = pref.getString(property.name, null)
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
            pref.edit { putString(property.name, value) }
        }
    }

    override var chosenDevice by PrefDelegate()
    override var infUrl by PrefDelegate()
    override var infOrg by PrefDelegate()
    override var infBucket by PrefDelegate()
    override var infToken by PrefDelegate()

    override fun createInflux() = InfluxDBClientKotlinFactory
        .create(infUrl ?: "", (infToken ?: "").toCharArray(), infOrg ?: "", infBucket ?: "")

    override suspend fun influxPing() = with(createInflux()) {
        getWriteKotlinApi().writeRecord("ping host=\"${Build.MODEL}\"", WritePrecision.MS)
    }
}

inline fun <reified T> Context.intent() = Intent(this, T::class.java)
inline fun <reified T> Context.act()
{
    if (T::class.isSubclassOf(Activity::class))
        startActivity(intent<T>())
    else if (T::class.isSubclassOf(Service::class))
        startService(intent<T>())
    else TODO("Unimplemented: ${T::class}")
}
fun ComponentActivity.actCallback(fn: ActivityResultCallback<ActivityResult>) =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult(), fn)

suspend infix fun <T> InfluxDBClientKotlin.add(meas: T) = getWriteKotlinApi().writeMeasurement(meas, WritePrecision.MS)
