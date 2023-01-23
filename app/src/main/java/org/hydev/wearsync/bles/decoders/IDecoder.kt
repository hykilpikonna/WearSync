package org.hydev.wearsync.bles.decoders

import java.util.*

interface IDecoder<T>
{
    val sid: UUID
    val cid: UUID

    fun decode(value: ByteArray): T
}