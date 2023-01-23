package org.hydev.wearsync

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import org.hydev.wearsync.ActivityPermissions.Companion.hasPermissions
import org.hydev.wearsync.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity()
{
    lateinit var binding: ActivityMainBinding

    val pref get() = getSharedPreferences("settings", Context.MODE_PRIVATE)
    val mac get() = pref.getString("device", "None")

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

        binding.content.tvDevice.text = "Configured Device: $mac"

        binding.fab.setOnClickListener { view ->
            Snackbar.make(view, "owo", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show()

            println("owo")
        }

        if (!hasPermissions()) startActivity(Intent(this, ActivityPermissions::class.java))
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
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId)
        {
            R.id.action_settings -> true
            R.id.action_scan -> {
                startActivity(Intent(this, ActivityScan::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}