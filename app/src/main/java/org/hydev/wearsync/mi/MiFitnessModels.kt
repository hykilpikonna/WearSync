@file:Suppress("PropertyName")

package org.hydev.wearsync.mi

import com.influxdb.annotations.Column
import com.influxdb.annotations.Measurement
import org.hydev.wearsync.reflectToString
import org.jetbrains.exposed.sql.Table
import java.time.Instant
import java.util.*

object SleepSegment : Table("sleep_segment") {
    val key = text("key")
    val sid = text("sid")
    val time = integer("time")
    val isComplete = integer("isComplete")
    val value = text("value")
}

@Measurement(name = "mi-sleep-day")
class SleepNight : SleepDaytime()
{
    @Column val awake_count: Long = 0
    @Column val sleep_awake_duration: Long = 0
    @Column val sleep_deep_duration: Long = 0
    @Column val sleep_light_duration: Long = 0
    @Column val sleep_rem_duration: Long = 0

    override fun toString() = reflectToString()
}

@Measurement(name = "mi-sleep-day")
open class SleepDaytime {
    @Column lateinit var bedtime: Date
    @Column lateinit var wake_up_time: Date
    @Column lateinit var timezone: Date
    @Column var duration: Long = 0

    var items: List<SleepState> = listOf()

    @Column(timestamp = true)
    lateinit var date_time: Instant

    override fun toString() = reflectToString()
}

@Measurement(name = "mi-sleep-state")
data class SleepState(
    @Column(timestamp = true)
    val start_time: Instant,

    val end_time: Instant,

    @Column
    var state: Int,
)