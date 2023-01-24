package org.hydev.wearsync

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.app.NotificationCompat
import com.influxdb.client.kotlin.InfluxDBClientKotlin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
import org.hydev.wearsync.BatteryInfo.Companion.batteryInfo
import org.hydev.wearsync.bles.BluetoothHandler.Companion.ble
import timber.log.Timber


class MyService : Service()
{
    private lateinit var influx: InfluxDBClientKotlin
    private val bm by lazy { getSysServ<BatteryManager>() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
    {
        influx = prefs.createInflux()
        startCollect()

        registerReceiver(mBatInfoReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        startForeground()
        return super.onStartCommand(intent, flags, startId)
    }

    fun startCollect()
    {
        collect(ble.heartRateChannel)
        collect(ble.batteryChannel)
    }

    private fun <T : Any> collect(channel: Channel<T>) {
        scope.launch {
            channel.consumeAsFlow().collect {
                try {
                    println("Adding ${it.javaClass.simpleName} to influxdb")
                    influx add it
                }
                catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private val mBatInfoReceiver: BroadcastReceiver = object : BroadcastReceiver()
    {
        override fun onReceive(ctxt: Context, intent: Intent) {
            val bi = intent.batteryInfo(bm)
            Timber.d("Battery info recorded: $bi")
            scope.launch { influx add bi }
        }
    }

    private fun startForeground()
    {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0,
            notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(NOTIF_ID, NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_watch_24)
                .setContentTitle("🐱 Running!")
                .setContentIntent(pendingIntent)
                .build()
        )
    }

    companion object
    {
        private const val NOTIF_ID = 1
        const val NOTIF_CHANNEL_ID = "Channel_Id"
    }
}