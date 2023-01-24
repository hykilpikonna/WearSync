package org.hydev.wearsync

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ActivitySettings : AppCompatActivity()
{
    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null)
        {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class SettingsFragment : PreferenceFragmentCompat()
    {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val act by lazy { requireActivity() }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?)
        {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            findPreference<Preference>("infTestButton")!!.setOnPreferenceClickListener {
                scope.launch {
                    try {
                        act.prefs.influxPing()
                        println("Success!")
                        view?.snack("Success!")
                    }
                    catch (e: Exception) {
                        e.printStackTrace()
                        view?.snack("Error: ${e.message}")
                    }
                }
                true
            }
        }
    }
}