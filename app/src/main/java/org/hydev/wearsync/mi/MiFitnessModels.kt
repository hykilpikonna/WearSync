@file:Suppress("PropertyName")

package org.hydev.wearsync.mi

import org.hydev.wearsync.reflectToString
import org.jetbrains.exposed.sql.Table
import java.util.*

object SleepSegment : Table("sleep_segment") {
    val key = text("key")
    val sid = text("sid")
    val time = integer("time")
    val isComplete = integer("isComplete")
    val value = text("value")
}

class SleepNight : SleepDaytime()
{
    val awake_count: Long = 0
    val sleep_awake_duration: Long = 0
    val sleep_deep_duration: Long = 0
    val sleep_light_duration: Long = 0
    val sleep_rem_duration: Long = 0

    override fun toString() = reflectToString()
}

open class SleepDaytime {
    var bedtime: Long = 0
    var wake_up_time: Long = 0
    var timezone: Long = 0
    var duration: Long = 0

    var items: List<SleepState> = listOf()
    var date_time: Long = 0

    override fun toString() = reflectToString()
}

data class SleepState(
    val end_time: Long,
    val state: Long,
    val start_time: Long
)