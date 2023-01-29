package org.hydev.wearsync

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.hydev.wearsync.BatteryInfo.Companion.batteryInfo
import org.hydev.wearsync.bles.BluetoothHandler.Companion.ble
import org.hydev.wearsync.bles.decoders.BatteryDecoder
import org.hydev.wearsync.bles.decoders.HeartRateDecoder
import org.hydev.wearsync.bles.decoders.IDecoder
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference


class MyService : Service()
{
    private lateinit var influx: Influx
    private val bm by lazy { getSysServ<BatteryManager>() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val count = AtomicLong()

    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int
    {
        init()
        return super.onStartCommand(intent, flags, startId)
    }

    fun init()
    {
        influx = prefs.createInflux()
        ble.connectAddress(prefs.chosenDevice ?: return notif(text = "❌ No bluetooth devices chosen")) {
            startCollect()
            notif(sub = "Bluetooth Connected!")

            registerReceiver(mBatInfoReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
        notif(sub = "Bluetooth Connecting...")
    }

    fun startCollect()
    {
        observe<BatteryDecoder>()
        observe<HeartRateDecoder>()
    }

    private inline fun <reified T : IDecoder<out Any>> observe() = ble.observeAny<T> { add(it) }

    private fun add(it: Any) = scope.launch {
        runCatching {
            val clas = it.javaClass.simpleName
            Timber.d("+$clas")
            influx += it

            notif(text = "Recorded ${count.addAndGet(1)} events!\n+$clas")
        }.orTrace()
    }

    private val mBatInfoReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctxt: Context, intent: Intent) { add(intent.batteryInfo(bm)) }
    }

    private val intent by lazy { PendingIntent.getActivity(this, 0, intent<MainActivity>(),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }

    private val nSub = AtomicReference<String>()
    private val nText = AtomicReference<String>()
    private fun notif(sub: String? = null, text: String? = null) = startForeground(NOTIF_ID,
        NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_watch_24)
            .setContentIntent(intent)
            .setOngoing(true)
            .setContentTitle("🐱 Running!")
            .setContentText(text?.also { nText.set(it) } ?: nText.get())
            .setSubText(sub?.also { nSub.set(it) } ?: nSub.get())
            .build())

    companion object
    {
        private const val NOTIF_ID = 1
        const val NOTIF_CHANNEL_ID = "Channel_Id"
    }
}