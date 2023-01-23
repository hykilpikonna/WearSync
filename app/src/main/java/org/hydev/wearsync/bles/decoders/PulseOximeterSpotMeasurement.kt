package org.hydev.wearsync.bles.decoders

import com.welie.blessed.BluetoothBytesParser
import java.util.*

data class PulseOximeterSpotMeasurement(
    val spO2: Float,
    val pulseRate: Float,
    val pulseAmplitudeIndex: Float?,
    val timestamp: Date?,
    val isDeviceClockSet: Boolean,
    val measurementStatus: Int?,
    val sensorStatus: Int?,
    val createdAt: Date = Calendar.getInstance().time
)

/**
 * Pulse Oximeter Service (PLX)
 */
class PLXSpotDecoder : IDecoder<PulseOximeterSpotMeasurement>
{
    override val sid = UUID.fromString("00001822-0000-1000-8000-00805f9b34fb")
    override val cid = UUID.fromString("00002a5e-0000-1000-8000-00805f9b34fb")

    override fun decode(value: ByteArray): PulseOximeterSpotMeasurement
    {
        val parser = BluetoothBytesParser(value)
        val flags = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8)
        val timestampPresent = flags and 0x01 > 0
        val measurementStatusPresent = flags and 0x02 > 0
        val sensorStatusPresent = flags and 0x04 > 0
        val pulseAmplitudeIndexPresent = flags and 0x08 > 0
        val isDeviceClockSet = flags and 0x10 == 0

        val spO2 = parser.getFloatValue(BluetoothBytesParser.FORMAT_SFLOAT)
        val pulseRate = parser.getFloatValue(BluetoothBytesParser.FORMAT_SFLOAT)
        val timestamp = if (timestampPresent) parser.dateTime else null
        val measurementStatus = if (measurementStatusPresent) parser.getIntValue(
            BluetoothBytesParser.FORMAT_UINT16
        ) else null
        val sensorStatus = if (sensorStatusPresent) parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16) else null
        if (sensorStatusPresent) parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8) // Reserved byte
        val pulseAmplitudeIndex = if (pulseAmplitudeIndexPresent) parser.getFloatValue(
            BluetoothBytesParser.FORMAT_SFLOAT
        ) else null

        return PulseOximeterSpotMeasurement(
            spO2 = spO2,
            pulseRate = pulseRate,
            measurementStatus = measurementStatus,
            sensorStatus = sensorStatus,
            pulseAmplitudeIndex = pulseAmplitudeIndex,
            timestamp = timestamp,
            isDeviceClockSet = isDeviceClockSet
        )
    }
}