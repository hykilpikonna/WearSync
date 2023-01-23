package org.hydev.wearsync.bles.decoders

import com.welie.blessed.BluetoothBytesParser
import java.util.*

data class PulseOximeterContinuousMeasurement(
    val spO2: Float,
    val pulseRate: Float,
    val spO2Fast: Float?,
    val pulseRateFast: Float?,
    val spO2Slow: Float?,
    val pulseRateSlow: Float?,
    val pulseAmplitudeIndex: Float?,
    val measurementStatus: Int?,
    val sensorStatus: Int?,
    val createdAt: Date = Calendar.getInstance().time
)

class PLXContinuousDecoder : IDecoder<PulseOximeterContinuousMeasurement> {

    override val sid = UUID.fromString("00001822-0000-1000-8000-00805f9b34fb")
    override val cid = UUID.fromString("00002a5f-0000-1000-8000-00805f9b34fb")

    override fun decode(value: ByteArray): PulseOximeterContinuousMeasurement
    {
        val parser = BluetoothBytesParser(value)
        val flags = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8)
        val spo2FastPresent = flags and 0x01 > 0
        val spo2SlowPresent = flags and 0x02 > 0
        val measurementStatusPresent = flags and 0x04 > 0
        val sensorStatusPresent = flags and 0x08 > 0
        val pulseAmplitudeIndexPresent = flags and 0x10 > 0

        val spO2 = parser.getFloatValue(BluetoothBytesParser.FORMAT_SFLOAT)
        val pulseRate = parser.getFloatValue(BluetoothBytesParser.FORMAT_SFLOAT)
        val spO2Fast = if (spo2FastPresent) parser.getFloatValue(BluetoothBytesParser.FORMAT_SFLOAT) else null
        val pulseRateFast = if (spo2FastPresent) parser.getFloatValue(BluetoothBytesParser.FORMAT_SFLOAT) else null
        val spO2Slow = if (spo2SlowPresent) parser.getFloatValue(BluetoothBytesParser.FORMAT_SFLOAT) else null
        val pulseRateSlow = if (spo2SlowPresent) parser.getFloatValue(BluetoothBytesParser.FORMAT_SFLOAT) else null
        val measurementStatus = if (measurementStatusPresent) parser.getIntValue(
            BluetoothBytesParser.FORMAT_UINT16
        ) else null
        val sensorStatus = if (sensorStatusPresent) parser.getIntValue(BluetoothBytesParser.FORMAT_UINT16) else null
        if (sensorStatusPresent) parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8) // Reserved byte
        val pulseAmplitudeIndex = if (pulseAmplitudeIndexPresent) parser.getFloatValue(
            BluetoothBytesParser.FORMAT_SFLOAT
        ) else null

        return PulseOximeterContinuousMeasurement(
            spO2 = spO2,
            pulseRate = pulseRate,
            spO2Fast = spO2Fast,
            pulseRateFast = pulseRateFast,
            spO2Slow = spO2Slow,
            pulseRateSlow = pulseRateSlow,
            measurementStatus = measurementStatus,
            sensorStatus = sensorStatus,
            pulseAmplitudeIndex = pulseAmplitudeIndex
        )
    }
}