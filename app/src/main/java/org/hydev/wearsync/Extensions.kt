package org.hydev.wearsync

import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.view.View
import androidx.core.content.edit
import com.google.android.material.snackbar.Snackbar

fun View.snack(msg: String) = Snackbar.make(this, msg, Snackbar.LENGTH_LONG)
    .setAction("Action", null).show()

inline fun <reified T> Context.getSysServ() = getSystemService(T::class.java) as T
fun Context.blueMan() = getSysServ<BluetoothManager>()

val Context.pref get() = getSharedPreferences("settings", Context.MODE_PRIVATE)
var Context.chosenDevice: String?
    get() = pref.getString("mac", null)
    set(value) = pref.edit { putString("mac", value) }

inline fun <reified T : Activity> Context.act() = startActivity(Intent(this, T::class.java))
