package org.hydev.wearsync

import android.app.Activity
import android.app.Service
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.os.Build
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.edit
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import androidx.preference.PreferenceManager
import com.google.android.material.snackbar.Snackbar
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import com.influxdb.client.InfluxDBClient
import com.influxdb.client.domain.DeletePredicateRequest
import com.influxdb.client.domain.WritePrecision
import com.influxdb.client.kotlin.InfluxDBClientKotlin
import com.influxdb.client.kotlin.InfluxDBClientKotlinFactory
import com.welie.blessed.BluetoothPeripheral
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import java.lang.reflect.Modifier
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.reflect.KProperty
import kotlin.reflect.full.isSubclassOf

fun <T> Result<T>.orTrace() = apply { exceptionOrNull()?.printStackTrace() }

fun View.snack(msg: String) = Snackbar.make(this, msg, Snackbar.LENGTH_LONG)
    .setAction("Action", null).show()

inline fun <reified T> Context.getSysServ() = getSystemService(T::class.java) as T
typealias BluePeri = BluetoothPeripheral
fun Context.blueMan() = getSysServ<BluetoothManager>()


private val gScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
fun async(context: CoroutineContext = EmptyCoroutineContext, start: CoroutineStart = CoroutineStart.DEFAULT, block: suspend CoroutineScope.() -> Unit) =
    gScope.launch(context, start, block)


interface Prefs {
    var chosenDevice: String?

    var infUrl: String?
    var infOrg: String?
    var infBucket: String?
    var infToken: String?

    fun createInflux(): InfluxDBClientKotlin
    suspend fun influxPing()
}

val Context.pref get() = PreferenceManager.getDefaultSharedPreferences(this)
val Context.prefs get() = object : Prefs {
    inner class PrefDelegate {
        operator fun getValue(thisRef: Any?, property: KProperty<*>) = pref.getString(property.name, null)
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: String?) {
            pref.edit { putString(property.name, value) }
        }
    }

    override var chosenDevice by PrefDelegate()
    override var infUrl by PrefDelegate()
    override var infOrg by PrefDelegate()
    override var infBucket by PrefDelegate()
    override var infToken by PrefDelegate()

    override fun createInflux() = InfluxDBClientKotlinFactory
        .create(infUrl ?: "", (infToken ?: "").toCharArray(), infOrg ?: "", infBucket ?: "")

    override suspend fun influxPing() = with(createInflux()) {
        getWriteKotlinApi().writeRecord("ping host=\"${Build.MODEL}\"", WritePrecision.MS)
    }
}

inline fun <reified T> Context.intent() = Intent(this, T::class.java)
inline fun <reified T> Context.act()
{
    if (T::class.isSubclassOf(Activity::class))
        startActivity(intent<T>())
    else if (T::class.isSubclassOf(Service::class))
        startService(intent<T>())
    else TODO("Unimplemented: ${T::class}")
}
fun ComponentActivity.actCallback(fn: ActivityResultCallback<ActivityResult>) =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult(), fn)

suspend infix fun <T> InfluxDBClientKotlin.add(meas: T) = getWriteKotlinApi().writeMeasurement(meas, WritePrecision.MS)
suspend infix fun <T> InfluxDBClientKotlin.add(meas: Iterable<T>) = getWriteKotlinApi().writeMeasurements(meas, WritePrecision.MS)
suspend infix fun <T> InfluxDBClientKotlin.add(meas: Flow<T>) = getWriteKotlinApi().writeMeasurements(meas, WritePrecision.MS)

fun InfluxDBClient.dropAll(bucket: String, org: String) =
    deleteApi.delete(DeletePredicateRequest().start(Date(0).offset).stop(Date(5999999999999).offset), bucket, org)

fun Any.reflectToString(): String {
    val s = ArrayList<String>()
    var clazz: Class<in Any>? = javaClass
    while (clazz != null) {
        s += clazz.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.map {
            it.isAccessible = true
            "${it.name}=${it.get(this)?.toString()?.trim()}"
        }
        clazz = clazz.superclass
    }
    return "{ ${s.joinToString(", ")} }"
}

val Date.offset get() = toInstant().atOffset(ZoneOffset.UTC)
fun Long.ensureMs() = if (this < 99999999999) this * 1000 else this

// GSON
object GsonExtensions {
    fun makeGson(): Gson
    {
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
    val GSON = makeGson()
    inline fun <reified T> Gson.parse(json: String?) = fromJson<T>(json, object : TypeToken<T>() {}.type)
    inline fun <reified T> String.parseJson() = GSON.parse<T>(this)
}

object Database {
    class ColumnNotFound(col: String) : RuntimeException(col)

    fun Cursor.col(name: String) = getColumnIndex(name).also { if (it == -1) throw ColumnNotFound(name) }

    infix fun Cursor.str(name: String) = getStringOrNull(col(name))
    infix fun Cursor.int(name: String) = getIntOrNull(col(name))

    class CursorIterator(val c: Cursor) : Iterator<Cursor>
    {
        override fun hasNext() = !c.isLast && !c.isAfterLast
        override fun next() = c.apply { moveToNext() }
    }

    val Cursor.iterator get() = CursorIterator(this)
    val Cursor.seq get() = iterator.asSequence()
}
