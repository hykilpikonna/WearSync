package org.hydev.wearsync

import android.bluetooth.BluetoothManager
import android.content.Context
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar

fun View.snack(msg: String) = Snackbar.make(this, msg, Snackbar.LENGTH_LONG)
    .setAction("Action", null).show()

fun Context.blueMan() = getSystemService(AppCompatActivity.BLUETOOTH_SERVICE) as BluetoothManager
