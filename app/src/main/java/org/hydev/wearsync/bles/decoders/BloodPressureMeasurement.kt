package org.hydev.wearsync.bles.decoders

import com.welie.blessed.BluetoothBytesParser
import org.hydev.wearsync.bles.ObservationUnit
import java.nio.ByteOrder
import java.util.*

class BloodPressureStatus internal constructor(measurementStatus: Int) {
    /**
     * Body Movement Detected
     */
    val isBodyMovementDetected: Boolean
    /**
     * Cuff is too loose
     */
    val isCuffTooLoose: Boolean
    /**
     * Irregular pulse detected
     */
    val isIrregularPulseDetected: Boolean
    /**
     * Pulse is not in normal range
     */
    val isPulseNotInRange: Boolean
    /**
     * Improper measurement position
     */
    val isImproperMeasurementPosition: Boolean

    init {
        isBodyMovementDetected = measurementStatus and 0x0001 > 0
        isCuffTooLoose = measurementStatus and 0x0002 > 0
        isIrregularPulseDetected = measurementStatus and 0x0004 > 0
        isPulseNotInRange = measurementStatus and 0x0008 > 0
        isImproperMeasurementPosition = measurementStatus and 0x0020 > 0
    }
}

data class BloodPressureMeasurement(
    val systolic: Float,
    val diastolic: Float,
    val meanArterialPressure: Float,
    val unit: ObservationUnit,
    val timestamp: Date?,
    val pulseRate: Float?,
    val userID: Int?,
    val measurementStatus: BloodPressureStatus?,
    val createdAt: Date = Calendar.getInstance().time
)

class BloodPressureDecoder : IDecoder<BloodPressureMeasurement>
{
    override val sid = UUID.fromString("00001810-0000-1000-8000-00805f9b34fb")
    override val cid = UUID.fromString("00002A35-0000-1000-8000-00805f9b34fb")

    override fun decode(value: ByteArray): BloodPressureMeasurement
    {
        val parser = BluetoothBytesParser(value, ByteOrder.LITTLE_ENDIAN)
        val flags = parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8)
        val unit = if (flags and 0x01 > 0) ObservationUnit.MMHG else ObservationUnit.KPA
        val timestampPresent = flags and 0x02 > 0
        val pulseRatePresent = flags and 0x04 > 0
        val userIdPresent = flags and 0x08 > 0
        val measurementStatusPresent = flags and 0x10 > 0

        val systolic = parser.getFloatValue(BluetoothBytesParser.FORMAT_SFLOAT)
        val diastolic = parser.getFloatValue(BluetoothBytesParser.FORMAT_SFLOAT)
        val meanArterialPressure = parser.getFloatValue(BluetoothBytesParser.FORMAT_SFLOAT)
        val timestamp = if (timestampPresent) parser.dateTime else null
        val pulseRate = if (pulseRatePresent) parser.getFloatValue(BluetoothBytesParser.FORMAT_SFLOAT) else null
        val userID = if (userIdPresent) parser.getIntValue(BluetoothBytesParser.FORMAT_UINT8) else null
        val status = if (measurementStatusPresent) BloodPressureStatus(parser.getIntValue(
            BluetoothBytesParser.FORMAT_UINT16
        )) else null

        return BloodPressureMeasurement(
            systolic = systolic,
            diastolic = diastolic,
            meanArterialPressure = meanArterialPressure,
            unit = unit,
            timestamp = timestamp,
            pulseRate = pulseRate,
            userID = userID,
            measurementStatus = status
        )
    }
}