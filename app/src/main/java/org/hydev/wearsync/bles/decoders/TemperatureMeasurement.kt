package org.hydev.wearsync.bles.decoders

import com.welie.blessed.BluetoothBytesParser
import org.hydev.wearsync.bles.ObservationUnit
import java.nio.ByteOrder
import java.util.*

enum class TemperatureType(val value: Int) {
    Unknown(0), Armpit(1), Body(2), Ear(3), Finger(4), GastroIntestinalTract(5), Mouth(6), Rectum(7), Toe(8), Tympanum(9);

    companion object {
        fun fromValue(value: Int): TemperatureType
        {
            for (type in values()) {
                if (type.value == value) return type
            }
            return Unknown
        }
    }
}

data class TemperatureMeasurement(
    val temperatureValue: Float,
    val unit: ObservationUnit,
    val timestamp: Date?,
    val type: TemperatureType,
    val createdAt: Date = Calendar.getInstance().time
)

/**
 * Health Thermometer service
 */
class TemperatureDecoder : IDecoder<TemperatureMeasurement> {

    override val sid = UUID.fromString("00001809-0000-1000-8000-00805f9b34fb")
    override val cid = UUID.fromString("00002A1C-0000-1000-8000-00805f9b34fb")

    override fun decode(value: ByteArray): TemperatureMeasurement
    {
        val parser = BluetoothBytesParser(value, ByteOrder.LITTLE_ENDIAN)
        val flags = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8)
        val unit = if (flags and 0x01 > 0) ObservationUnit.Fahrenheit else ObservationUnit.Celsius
        val timestampPresent = flags and 0x02 > 0
        val typePresent = flags and 0x04 > 0

        val temperatureValue = parser.getFloatValue(BluetoothBytesParser.FORMAT_FLOAT)
        val timestamp = if (timestampPresent) parser.dateTime else null
        val type = if (typePresent) TemperatureType.fromValue(parser.getIntValue(
            BluetoothBytesParser.FORMAT_UINT8
        )) else TemperatureType.Unknown

        return TemperatureMeasurement(
            unit = unit,
            temperatureValue = temperatureValue,
            timestamp = timestamp,
            type = type
        )
    }
}