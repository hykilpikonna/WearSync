package org.hydev.wearsync

import android.content.Intent
import android.os.BatteryManager
import com.influxdb.annotations.Column
import com.influxdb.annotations.Measurement

@Measurement(name = "phone-battery")
data class BatteryInfo(
    @Column val percent: Double, // Percent
    @Column val temperature: Double, // Celsius
    @Column val current: Double, // mA
    @Column val status: String?,
    @Column val health: String?,
    @Column val powerSource: String?,
) {
    companion object {
        fun Intent.batteryInfo(bm: BatteryManager): BatteryInfo
        {
            val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val temp = getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val current = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val health = getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            val chargePlug = getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)

            val statusStr = when (status)
            {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                BatteryManager.BATTERY_STATUS_UNKNOWN -> null
                else -> null
            }

            val powerSource = when (chargePlug)
            {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                else -> null
            }

            val healthStr = when (health)
            {
                BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over-Voltage"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                BatteryManager.BATTERY_HEALTH_UNKNOWN -> null
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> null
                else -> null
            }

            return BatteryInfo(
                percent = level.toDouble() / scale,
                temperature = temp / 10.0,
                current = current / 1000.0,
                status = statusStr,
                health = healthStr,
                powerSource = powerSource
            )
        }
    }
}