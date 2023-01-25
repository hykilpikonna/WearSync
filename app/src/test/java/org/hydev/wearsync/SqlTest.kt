package org.hydev.wearsync

import com.google.gson.*
import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.InfluxDBClientOptions
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import org.hydev.wearsync.mi.SleepDaytime
import org.hydev.wearsync.mi.SleepNight
import org.hydev.wearsync.mi.SleepSegment
import org.hydev.wearsync.mi.SleepState
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.StdOutSqlLogger
import org.jetbrains.exposed.sql.addLogger
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
import java.time.Instant
import java.util.*

fun Long.ensureMs() = if (this < 99999999999) this * 1000 else this
fun makeGson(): Gson {
    val sd = JsonSerializer<Date> { src, _, _ -> if (src == null) null else JsonPrimitive(src.time / 1000) }
    val dd = JsonDeserializer<Date> { json, _, _ -> if (json == null) null else Date(json.asLong.ensureMs()) }
    val si = JsonSerializer<Instant> { src, _, _ -> if (src == null) null else JsonPrimitive(src.epochSecond) }
    val di = JsonDeserializer<Instant> { json, _, _ -> if (json == null) null else Date(json.asLong.ensureMs()).toInstant() }

    return GsonBuilder()
        .registerTypeAdapter(Date::class.java, sd)
        .registerTypeAdapter(Date::class.java, dd)
        .registerTypeAdapter(Instant::class.java, si)
        .registerTypeAdapter(Instant::class.java, di)
        .create()
}


suspend fun main(args: Array<String>)
{
    println("Hi")

    val opts = InfluxDBClientOptions.builder()
        .url("https://influx.hydev.org")
        .authenticateToken("meow".toCharArray())
        .bucket("test").org("hydev").build()

    val influx = InfluxDBClientKotlinFactory.create(opts)
    val influxJava = InfluxDBClientFactory.create(opts)

    val gs = makeGson()
    val PATH = "/ws/Android/WearSyncData/com.mi.health/databases/1804898679/cn/fitness_data"
    Database.connect("jdbc:sqlite:${PATH}", "org.sqlite.JDBC")
    TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE

    val days = transaction {
        addLogger(StdOutSqlLogger)

        return@transaction SleepSegment.selectAll().mapNotNull {
            val json = it[SleepSegment.value]
            when (it[SleepSegment.key])
            {
                "watch_night_sleep" -> gs.fromJson(json, SleepNight::class.java)
                "watch_daytime_sleep" -> gs.fromJson(json, SleepDaytime::class.java)
                else -> null
            }
        }
    }

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

    println(days.joinToString("\n"))

    influxJava.dropAll("test", "hydev")

    influx add days
    influx add states
}