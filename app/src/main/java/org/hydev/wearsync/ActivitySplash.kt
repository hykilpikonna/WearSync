package org.hydev.wearsync

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.topjohnwu.superuser.Shell
import org.hydev.wearsync.databinding.ActivitySplashBinding

class ActivitySplash : AppCompatActivity()
{
    private lateinit var binding: ActivitySplashBinding

    companion object
    {
        init
        {
            // Set settings before the main shell can be created
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            Shell.setDefaultBuilder(Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10)
            )
        }
    }

    fun getShell() = Shell.getShell {
        println(Shell.cmd("whoami").exec().out)
        act<MainActivity>()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        getShell()

        binding.btnTestRoot.setOnClickListener { getShell() }
    }
}