package org.hydev.wearsync.bles.decoders

import org.hydev.wearsync.BluePeri
import java.util.*

interface IDecoder<T>
{
    val sid: UUID
    val cid: UUID

    fun decode(value: ByteArray): T

    /**
     * Optional step to write data after read
     */
    suspend fun additionalSetup(peri: BluePeri) {}
}