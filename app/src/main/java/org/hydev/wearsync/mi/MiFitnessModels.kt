@file:Suppress("PropertyName")

package org.hydev.wearsync.mi

import android.annotation.SuppressLint
import android.content.Context
import com.influxdb.annotations.Column
import com.influxdb.annotations.Measurement
import com.topjohnwu.superuser.Shell
import org.hydev.wearsync.GSON
import org.hydev.wearsync.reflectToString
import org.jetbrains.exposed.exceptions.ExposedSQLException
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

data class MiFitness(
    val days: List<SleepDaytime>,
    val states: List<SleepState>,
)

fun readMiFitness(path: String): MiFitness
{
//    Database.registerJdbcDriver("jdbc:sqldroid", "org.sqldroid.SQLDroidDriver", SQLiteDialect.dialectName)
    Database.connect("jdbc:sqlite:${path}", "org.sqlite.JDBC")
    TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE

    val days = try {
        transaction {
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
    }
    catch (e: ExposedSQLException)
    {
        if (e.message?.lowercase()?.contains("no such table") == true) {
            println("Fitness database not found in $path")
            return MiFitness(emptyList(), emptyList())
        }
        else throw e
    }
    if (days.isEmpty()) return MiFitness(emptyList(), emptyList())

    // Calculate average fall asleep time
    println(days.asSequence().filterIsInstance<SleepNight>().take(45)
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

    return MiFitness(days, states.toList())
}

@SuppressLint("SdCardPath")
const val MI_DB_PATH = "/data/data/com.mi.health/databases"

fun Context.readMiFitness(): MiFitness
{
    val days = ArrayList<SleepDaytime>()
    val states = ArrayList<SleepState>()

    // Find fitness database paths through the root shell
    Shell.cmd("find $MI_DB_PATH -name fitness_data").exec().out.forEach {
        println("Reading database $it")

        // Copy file to a readable path
        val path = filesDir.path + "/tmp.db"
        Shell.cmd("cp '$it' $path").exec()

        val (d, s) = readMiFitness(path)
        days += d
        states += s
    }

    return MiFitness(days, states)
}