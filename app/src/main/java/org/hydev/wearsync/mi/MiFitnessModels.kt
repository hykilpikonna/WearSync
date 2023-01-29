@file:Suppress("PropertyName")

package org.hydev.wearsync.mi

import com.influxdb.annotations.Column
import com.influxdb.annotations.Measurement
import org.hydev.wearsync.GSON
import org.hydev.wearsync.reflectToString
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
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

fun readMiDatabase(): Pair<List<SleepDaytime>, List<SleepState>>
{
    val PATH = "/data/data/com.mi.health/databases/1804898679/cn/fitness_data"
    Database.connect("jdbc:sqlite:${PATH}", "org.sqlite.JDBC")
    TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE

    val days = transaction {
        addLogger(StdOutSqlLogger)

        return@transaction SleepSegment.selectAll().mapNotNull {
            val json = it[SleepSegment.value]
            when (it[SleepSegment.key])
            {
                "watch_night_sleep" -> GSON.fromJson(json, SleepNight::class.java)
                "watch_daytime_sleep" -> GSON.fromJson(json, SleepDaytime::class.java)
                else -> null
            }
        }
    }

    println(days.filterIsInstance<SleepNight>().take(45)
        .map { it.bedtime.hours }.map { if (it < 12) it + 24 else it }.average() - 24)

    val rawStates = days.flatMap { it.items }.sortedBy { it.start_time }

    // Awake: 5
    rawStates.filter { it.state == 5 }.forEach { it.state = 0 }

    // Use state 0 to fill between the start-end gaps
    var states = mutableListOf<SleepState>()
    rawStates.forEachIndexed { i, si ->
        states += si
        if (i != 0)
        {
            val last = rawStates[i - 1]
            if (last.end_time < si.start_time) states +=
                SleepState(start_time = last.end_time, end_time = si.start_time, 0)
        }
    }
    states += SleepState(start_time = rawStates.last().end_time, end_time = Instant.now(), 0)

    // Remove duplicates
    states = states.filterIndexed { i, si -> i == 0 || si.state != states[i - 1].state }.toMutableList()

    return days to states.toList()
}