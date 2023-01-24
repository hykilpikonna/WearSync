package org.hydev.wearsync.bles.decoders

import com.welie.blessed.BluetoothBytesParser
import com.welie.blessed.BluetoothBytesParser.Companion.FORMAT_SFLOAT
import com.welie.blessed.BluetoothBytesParser.Companion.FORMAT_SINT16
import com.welie.blessed.BluetoothBytesParser.Companion.FORMAT_UINT16
import com.welie.blessed.BluetoothBytesParser.Companion.FORMAT_UINT8
import com.welie.blessed.WriteType
import com.welie.blessed.asHexString
import org.hydev.wearsync.BluePeri
import org.hydev.wearsync.bles.ObservationUnit
import org.hydev.wearsync.bles.ObservationUnit.MiligramPerDeciliter
import org.hydev.wearsync.bles.ObservationUnit.MmolPerLiter
import timber.log.Timber
import java.util.*

data class GlucoseMeasurement(
    val value: Float?,
    val unit: ObservationUnit,
    val timestamp: Date?,
    val sequenceNumber: Int,
    val contextWillFollow: Boolean,
    val createdAt: Date = Calendar.getInstance().time
)

class GlucoseDecoder : IDecoder<GlucoseMeasurement>
{
    companion object {
        val GLUCOSE_RECORD_AP_CID = UUID.fromString("00002A52-0000-1000-8000-00805f9b34fb")
    }

    override val sid = UUID.fromString("00001808-0000-1000-8000-00805f9b34fb")
    override val cid = UUID.fromString("00002A18-0000-1000-8000-00805f9b34fb")

    override fun decode(value: ByteArray): GlucoseMeasurement
    {
        val parser = BluetoothBytesParser(value)
        val flags: Int = parser.getIntValue(FORMAT_UINT8)
        val timeOffsetPresent = flags and 0x01 > 0
        val typeAndLocationPresent = flags and 0x02 > 0
        val unit = if (flags and 0x04 > 0) MmolPerLiter else MiligramPerDeciliter
        val contextWillFollow = flags and 0x10 > 0

        val sequenceNumber = parser.getIntValue(FORMAT_UINT16)
        var timestamp = parser.dateTime
        if (timeOffsetPresent) {
            val timeOffset: Int = parser.getIntValue(FORMAT_SINT16)
            timestamp = Date(timestamp.time + timeOffset * 60000)
        }

        val multiplier = if (unit === MiligramPerDeciliter) 100000 else 1000
        val glucoseValue = if (typeAndLocationPresent) parser.getFloatValue(FORMAT_SFLOAT) * multiplier else null

        return GlucoseMeasurement(
            unit = unit,
            timestamp = timestamp,
            sequenceNumber = sequenceNumber,
            value = glucoseValue,
            contextWillFollow = contextWillFollow
        )
    }

    override suspend fun additionalSetup(peri: BluePeri)
    {
        peri.getCharacteristic(sid, GLUCOSE_RECORD_AP_CID)?.let {
            val result = peri.observe(it) { value ->
                Timber.d("record access response: ${value.asHexString()}")
            }

            if (result) {
                // command = (Op code report stored records = 1, operator all records = 1)
                peri.writeCharacteristic(sid, GLUCOSE_RECORD_AP_CID, byteArrayOf(1, 1), WriteType.WITH_RESPONSE)
            }
        }
    }
}