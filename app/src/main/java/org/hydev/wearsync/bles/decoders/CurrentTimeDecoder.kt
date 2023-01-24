package org.hydev.wearsync.bles.decoders

import com.welie.blessed.BluetoothBytesParser
import java.util.*

class CurrentTimeDecoder : IDecoder<Date>
{
    override val sid = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb")
    override val cid = UUID.fromString("00002A2B-0000-1000-8000-00805f9b34fb")

    override fun decode(value: ByteArray) = BluetoothBytesParser(value).dateTime
}