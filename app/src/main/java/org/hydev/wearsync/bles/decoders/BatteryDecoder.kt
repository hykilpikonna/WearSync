package org.hydev.wearsync.bles.decoders

import com.welie.blessed.asUInt8
import java.util.*

class BatteryDecoder : IDecoder<UInt>
{
    override val sid = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
    override val cid = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")

    override fun decode(value: ByteArray): UInt
    {
        return value.asUInt8()!!
    }
}