package org.hydev.wearsync

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import org.hydev.wearsync.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity()
{
    lateinit var binding: ActivityMainBinding

    val pref get() = getSharedPreferences("settings", Context.MODE_PRIVATE)
    val mac get() = pref.getString("device", "None")

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

                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}