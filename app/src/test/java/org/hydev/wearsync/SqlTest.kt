package org.hydev.wearsync

import com.influxdb.client.InfluxDBClientFactory
import com.influxdb.client.InfluxDBClientOptions
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import org.hydev.wearsync.mi.readMiDatabase


suspend fun main(args: Array<String>)
{
    println("Hi")

    val opts = InfluxDBClientOptions.builder()
        .url("https://influx.hydev.org")
        .authenticateToken("TASCtBVdyrXiATQQrdsqF7r6HFOZThijJeA22v1Zu3MUVsTAd4MTGsu8Sh0Nutf5u2KaQ4ut7CP8zsJ--p6Phg==".toCharArray())
        .bucket("test").org("hydev").build()

    val influx = InfluxDBClientKotlinFactory.create(opts)
    val influxJava = InfluxDBClientFactory.create(opts)

    val (days, states) = readMiDatabase()

    println(days.joinToString("\n"))

    influxJava.dropAll("test", "hydev")

    influx add days
    influx add states
}