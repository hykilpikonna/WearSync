package org.hydev.wearsync

import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import kotlin.reflect.KProperty


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

inline fun <reified T : Activity> Context.act() = startActivity(Intent(this, T::class.java))
