package org.hydev.wearsync.bles.decoders

import com.welie.blessed.BluetoothBytesParser
import com.welie.blessed.BluetoothBytesParser.Companion.FORMAT_UINT16
import com.welie.blessed.BluetoothBytesParser.Companion.FORMAT_UINT8
import org.hydev.wearsync.bles.SensorContactFeature
import java.nio.ByteOrder
import java.util.*

data class HeartRateMeasurement(
    val pulse: Int,
    val energyExpended: Int?,
    val rrIntervals: IntArray,
    val sensorContactStatus: SensorContactFeature,
    val createdAt: Date = Calendar.getInstance().time
)

class HeartRateDecoder : IDecoder<HeartRateMeasurement>
{
    override val sid = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb")
    override val cid = UUID.fromString("00002A37-0000-1000-8000-00805f9b34fb")

    override fun decode(value: ByteArray): HeartRateMeasurement
    {
        val parser = BluetoothBytesParser(value, ByteOrder.LITTLE_ENDIAN)
        val flags = parser.getIntValue(FORMAT_UINT8)
        val pulse = if (flags and 0x01 == 0) parser.getIntValue(FORMAT_UINT8) else parser.getIntValue(FORMAT_UINT16)
        val sensorContactStatusFlag = flags and 0x06 shr 1
        val energyExpenditurePresent = flags and 0x08 > 0
        val rrIntervalPresent = flags and 0x10 > 0

        val sensorContactStatus = when (sensorContactStatusFlag) {
            0, 1 -> SensorContactFeature.NotSupported
            2 -> SensorContactFeature.SupportedNoContact
            3 -> SensorContactFeature.SupportedAndContact
            else -> SensorContactFeature.NotSupported
        }

        val energyExpended = if (energyExpenditurePresent) parser.getIntValue(FORMAT_UINT16) else null

        val rrArray = ArrayList<Int>()
        if (rrIntervalPresent) {
            while (parser.offset < value.size) {
                val rrInterval = parser.getIntValue(FORMAT_UINT16)
                rrArray.add((rrInterval.toDouble() / 1024.0 * 1000.0).toInt())
            }
        }

        return HeartRateMeasurement(
            pulse = pulse,
            energyExpended = energyExpended,
            sensorContactStatus = sensorContactStatus,
            rrIntervals = rrArray.toIntArray()
        )
    }
}