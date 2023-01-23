package org.hydev.wearsync

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ActivityPermissions : AppCompatActivity()
{
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        checkPermissions()
    }

    private fun checkPermissions() {
        val missing = getMissingPermissions()
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), ACCESS_LOCATION_REQUEST)
        }
        else finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        println(grantResults.map { it })

        // Check if all permission were granted
        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) finish()
        else {
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
        private const val ACCESS_LOCATION_REQUEST = 2

        fun Context.hasPermissions() = getMissingPermissions().isEmpty()

        fun Context.requiredPermissions(): Array<String>
        {
            val sdk = applicationInfo.targetSdkVersion
            return if (SDK_INT >= Build.VERSION_CODES.S && sdk >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else if (SDK_INT >= Build.VERSION_CODES.Q && sdk >= Build.VERSION_CODES.Q) {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            } else arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        fun Context.getMissingPermissions(): List<String> {
            return requiredPermissions().filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        }
    }
}