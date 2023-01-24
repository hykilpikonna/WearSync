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
import kotlinx.coroutines.launch
import org.hydev.wearsync.BatteryInfo.Companion.batteryInfo
import org.hydev.wearsync.bles.BluetoothHandler.Companion.ble
import org.hydev.wearsync.bles.decoders.BatteryDecoder
import org.hydev.wearsync.bles.decoders.HeartRateDecoder
import org.hydev.wearsync.bles.decoders.IDecoder


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

        startForeground(NOTIF_ID, "Just started!".toNotif())
        return super.onStartCommand(intent, flags, startId)
    }

    fun startCollect()
    {
        observe<BatteryDecoder>()
        observe<HeartRateDecoder>()
    }

    private inline fun <reified T : IDecoder<out Any>> observe() = ble.observeAny<T> { add(it) }

    private fun add(it: Any) = scope.launch {
        runCatching {
            println("Adding ${it.javaClass.simpleName} to influxdb")
            influx add it
        }.orTrace()
    }

    private val mBatInfoReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctxt: Context, intent: Intent) { add(intent.batteryInfo(bm)) }
    }

    private val intent = PendingIntent.getActivity(this, 0, intent<MainActivity>(),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun String.toNotif() = NotificationCompat.Builder(this@MyService, NOTIF_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_watch_24)
        .setContentIntent(intent)
        .setContentTitle("🐱 Running!")
        .setOngoing(true)
        .setSubText(this)
        .build()

    companion object
    {
        private const val NOTIF_ID = 1
        const val NOTIF_CHANNEL_ID = "Channel_Id"
    }
}