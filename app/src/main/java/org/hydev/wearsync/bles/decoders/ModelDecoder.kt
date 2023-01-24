package org.hydev.wearsync.bles.decoders

import com.welie.blessed.asString
import java.util.*

class ModelDecoder : IDecoder<String>
{
    override val sid = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")
    override val cid = UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb")

    override fun decode(value: ByteArray) = value.asString()
}