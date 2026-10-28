package com.lunexa.studio.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.ZoneId

val YT_SCOPES = listOf(
    "https://www.googleapis.com/auth/youtube.readonly",
    "https://www.googleapis.com/auth/yt-analytics.readonly",
)

/** Ролики до 3 минут считаем Shorts (лимит Shorts с октября 2024). */
const val SHORTS_MAX_SEC = 180

@Serializable data class YtVideo(
    val id: String, val title: String, val publishedAt: String, val thumb: String? = null,
    val durationSec: Int = 0, val views: Long = 0, val likes: Long = 0, val comments: Long = 0,
    /** Из публичной ленты: ссылка вида /shorts/… */
    val shortsHint: Boolean? = null,
) { val isShort: Boolean get() = shortsHint ?: (durationSec in 1..SHORTS_MAX_SEC) }

@Serializable data class YtDay(
    val date: String, val views: Long, val minutes: Long, val avgDurSec: Long, val subsGained: Long, val subsLost: Long,
)

@Serializable data class YtRow(val key: String, val value: Double)

@Serializable data class YtVideoStat(val id: String, val views: Long, val avgDurSec: Long, val subsGained: Long)

@Serializable data class YtCache(
    val channelId: String,
    val title: String,
    val thumb: String? = null,
    val subscribers: Long = 0,
    val hiddenSubscribers: Boolean = false,
    val views: Long = 0,
    val videoCount: Long = 0,
    val syncedAt: Long = System.currentTimeMillis(),
    val videos: List<YtVideo> = emptyList(),
    val daily: List<YtDay> = emptyList(),
    val countries: List<YtRow> = emptyList(),
    val traffic: List<YtRow> = emptyList(),
    val ages: List<YtRow> = emptyList(),
    val videoStats: List<YtVideoStat> = emptyList(),
    /** Если Analytics API недоступен — данные Data API всё равно сохраняются, а здесь причина. */
    val analyticsError: String? = null,
    /** "api" — вход через Google, "public" — открытые данные по ссылке на канал. */
    val source: String = "api",
)

class YtException(val code: Int, val reason: String?, message: String) : Exception(message)

object YouTubeStore {
    private fun file(ctx: Context) = File(ctx.filesDir, "youtube_cache.json")

    fun load(ctx: Context): YtCache? = runCatching {
        val f = file(ctx)
        if (!f.exists()) null else AppJson.decodeFromString(YtCache.serializer(), f.readText())
    }.getOrNull()

    fun save(ctx: Context, c: YtCache) {
        file(ctx).writeText(AppJson.encodeToString(YtCache.serializer(), c))
    }

    fun clear(ctx: Context) { file(ctx).delete() }
}

object YouTubeApi {
    private val json = Json { ignoreUnknownKeys = true }
    private const val DATA = "https://www.googleapis.com/youtube/v3"
    private const val ANALYTICS = "https://youtubeanalytics.googleapis.com/v2/reports"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String, token: String): JsonObject {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.setRequestProperty("Authorization", "Bearer $token")
            c.setRequestProperty("Accept", "application/json")
            c.connectTimeout = 15_000
            c.readTimeout = 25_000
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val err = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonObject }.getOrNull()
                val msg = err?.get("message")?.jsonPrimitive?.contentOrNull ?: "HTTP $code"
                val reason = runCatching {
                    err?.get("errors")?.jsonArray?.firstOrNull()?.jsonObject?.get("reason")?.jsonPrimitive?.contentOrNull
                        ?: err?.get("status")?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                throw YtException(code, reason, msg)
            }
            return json.parseToJsonElement(body).jsonObject
        } finally {
            c.disconnect()
        }
    }

    private fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.num(k: String) = this[k]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
    private fun JsonObject.obj(k: String) = this[k]?.jsonObject
    private fun JsonObject.arr(k: String): JsonArray = this[k]?.jsonArray ?: JsonArray(emptyList())

    /** ISO 8601 «PT1H2M3S» → секунды. */
    fun parseIsoDuration(s: String?): Int {
        if (s == null) return 0
        val m = Regex("""P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""").matchEntire(s) ?: return 0
        val (d, h, mi, se) = m.destructured
        return (d.toIntOrNull() ?: 0) * 86400 + (h.toIntOrNull() ?: 0) * 3600 + (mi.toIntOrNull() ?: 0) * 60 + (se.toIntOrNull() ?: 0)
    }

    suspend fun fetch(token: String, zone: ZoneId): YtCache = withContext(Dispatchers.IO) {
        // --- Канал ---
        val ch = get("$DATA/channels?part=snippet,statistics,contentDetails&mine=true", token)
            .arr("items").firstOrNull()?.jsonObject
            ?: throw YtException(404, "noChannel", "No YouTube channel on this Google account")
        val sn = ch.obj("snippet")
        val st = ch.obj("statistics")
        val uploads = ch.obj("contentDetails")?.obj("relatedPlaylists")?.str("uploads")
        val thumbs = sn?.obj("thumbnails")
        val thumb = (thumbs?.obj("high") ?: thumbs?.obj("medium") ?: thumbs?.obj("default"))?.str("url")

        // --- Последние ролики (до 50) ---
        val videos = mutableListOf<YtVideo>()
        if (uploads != null) {
            val ids = get("$DATA/playlistItems?part=contentDetails&maxResults=50&playlistId=${enc(uploads)}", token)
                .arr("items").mapNotNull { it.jsonObject.obj("contentDetails")?.str("videoId") }
            if (ids.isNotEmpty()) {
                get("$DATA/videos?part=snippet,statistics,contentDetails&id=${enc(ids.joinToString(","))}", token)
                    .arr("items").forEach { el ->
                        val v = el.jsonObject
                        val vs = v.obj("snippet"); val vst = v.obj("statistics")
                        val t = vs?.obj("thumbnails")
                        videos += YtVideo(
                            id = v.str("id") ?: return@forEach,
                            title = vs?.str("title") ?: "",
                            publishedAt = vs?.str("publishedAt") ?: "",
                            thumb = (t?.obj("medium") ?: t?.obj("default"))?.str("url"),
                            durationSec = parseIsoDuration(v.obj("contentDetails")?.str("duration")),
                            views = vst?.num("viewCount") ?: 0, likes = vst?.num("likeCount") ?: 0,
                            comments = vst?.num("commentCount") ?: 0,
                        )
                    }
            }
        }

        val base = YtCache(
            channelId = ch.str("id") ?: "",
            title = sn?.str("title") ?: "YouTube",
            thumb = thumb,
            subscribers = st?.num("subscriberCount") ?: 0,
            hiddenSubscribers = st?.get("hiddenSubscriberCount")?.jsonPrimitive?.contentOrNull == "true",
            views = st?.num("viewCount") ?: 0,
            videoCount = st?.num("videoCount") ?: 0,
            videos = videos.sortedByDescending { it.publishedAt },
        )

        // --- Analytics API: ошибки здесь не отменяют данные канала ---
        try {
            val end = LocalDate.now(zone)
            val start = end.minusDays(365)
            fun report(params: String) = get("$ANALYTICS?ids=channel%3D%3DMINE&startDate=$start&endDate=$end&$params", token)
            fun rows(o: JsonObject) = o.arr("rows").map { it.jsonArray }
            fun d(a: JsonArray, i: Int) = a.getOrNull(i)?.jsonPrimitive?.doubleOrNull ?: 0.0
            fun l(a: JsonArray, i: Int) = a.getOrNull(i)?.jsonPrimitive?.longOrNull ?: d(a, i).toLong()

            val daily = rows(report("dimensions=day&sort=day&metrics=" + enc("views,estimatedMinutesWatched,averageViewDuration,subscribersGained,subscribersLost")))
                .map { r -> YtDay(r[0].jsonPrimitive.content, l(r, 1), l(r, 2), l(r, 3), l(r, 4), l(r, 5)) }
            val startMonth = end.minusDays(28)
            fun report28(params: String) = get("$ANALYTICS?ids=channel%3D%3DMINE&startDate=$startMonth&endDate=$end&$params", token)
            val countries = rows(report28("dimensions=country&metrics=views&sort=-views&maxResults=10"))
                .map { YtRow(it[0].jsonPrimitive.content, d(it, 1)) }
            val traffic = rows(report28("dimensions=insightTrafficSourceType&metrics=views&sort=-views"))
                .map { YtRow(it[0].jsonPrimitive.content, d(it, 1)) }
            val ages = runCatching {
                rows(report28("dimensions=ageGroup&metrics=viewerPercentage")).map { YtRow(it[0].jsonPrimitive.content, d(it, 1)) }
            }.getOrDefault(emptyList())
            val vStats = rows(report("dimensions=video&sort=-views&maxResults=50&metrics=" + enc("views,averageViewDuration,subscribersGained")))
                .map { YtVideoStat(it[0].jsonPrimitive.content, l(it, 1), l(it, 2), l(it, 3)) }
            base.copy(daily = daily, countries = countries, traffic = traffic, ages = ages, videoStats = vStats)
        } catch (e: YtException) {
            base.copy(analyticsError = e.reason ?: e.message)
        }
    }

    /** Отзыв доступа приложения (кнопка «Отключить канал»). */
    suspend fun revoke(token: String) = withContext(Dispatchers.IO) {
        runCatching {
            val c = URL("https://oauth2.googleapis.com/revoke?token=${enc(token)}").openConnection() as HttpURLConnection
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            c.outputStream.use { }
            c.responseCode
            c.disconnect()
        }
    }

    /** Понятное описание ошибки для пользователя. */
    fun explain(e: Throwable, lang: String): String {
        val en = lang == "en"
        return when {
            e is YtException && e.reason == "noChannel" ->
                if (en) "This Google account has no YouTube channel. Choose the account that owns the channel." else "На этом Google-аккаунте нет YouTube-канала. Выберите аккаунт, которому принадлежит канал."
            e is YtException && (e.reason == "accessNotConfigured" || e.reason == "SERVICE_DISABLED" || e.message?.contains("has not been used", true) == true) ->
                if (en) "The API is not enabled in Google Cloud. Enable YouTube Data API v3 and YouTube Analytics API." else "API не включён в Google Cloud. Включите YouTube Data API v3 и YouTube Analytics API."
            e is YtException && e.code == 401 ->
                if (en) "Session expired — tap “Refresh” to sign in again." else "Сессия истекла — нажмите «Обновить», чтобы войти снова."
            e is YtException && e.reason == "quotaExceeded" ->
                if (en) "Daily API quota exceeded. Try again tomorrow." else "Исчерпан дневной лимит API. Попробуйте завтра."
            e is YtException -> (if (en) "YouTube error: " else "Ошибка YouTube: ") + (e.message ?: e.code.toString())
            e is java.net.UnknownHostException || e is java.net.SocketTimeoutException || e is java.io.IOException ->
                if (en) "No internet connection." else "Нет подключения к интернету."
            else -> (if (en) "Error: " else "Ошибка: ") + (e.message ?: e.javaClass.simpleName)
        }
    }
}
