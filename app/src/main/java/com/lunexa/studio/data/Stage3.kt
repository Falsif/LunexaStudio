package com.lunexa.studio.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// =====================================================================
// Цели
// =====================================================================

/** Текущие показатели канала для расчёта целей. */
data class ChannelNumbers(val subs: Long?, val views: Long?, val videos: Long?, val streak: Long)

object Goals {
    fun current(g: Goal, n: ChannelNumbers): Long? = when (g.type) {
        GoalType.SUBSCRIBERS -> n.subs
        GoalType.VIEWS -> n.views
        GoalType.VIDEOS -> n.videos
        GoalType.STREAK -> n.streak
        GoalType.CUSTOM -> g.manualProgress
    }

    fun fraction(g: Goal, n: ChannelNumbers): Float {
        val c = current(g, n) ?: return 0f
        return if (g.target <= 0) 0f else (c.toFloat() / g.target).coerceIn(0f, 1f)
    }

    val presets = listOf(
        GoalType.SUBSCRIBERS to 100L, GoalType.SUBSCRIBERS to 500L, GoalType.SUBSCRIBERS to 1_000L,
        GoalType.SUBSCRIBERS to 10_000L, GoalType.VIEWS to 100_000L, GoalType.VIEWS to 1_000_000L,
        GoalType.STREAK to 30L,
    )

    /** Дни подряд с публикацией, заканчивая сегодня или вчера. */
    fun streak(days: Set<LocalDate>, today: LocalDate): Long {
        var d = if (today in days) today else today.minusDays(1)
        var n = 0L
        while (d in days) { n++; d = d.minusDays(1) }
        return n
    }

    fun publishDays(pubs: List<Publication>, yt: YtCache?, zone: ZoneId): Set<LocalDate> {
        val set = pubs.filter { it.status == PubStatus.PUBLISHED }.map { it.scheduledAt.toLocalDate(zone) }.toMutableSet()
        yt?.videos?.forEach { v -> runCatching { set += Instant.parse(v.publishedAt).atZone(zone).toLocalDate() } }
        return set
    }

    fun numbers(snaps: List<ChannelSnapshot>, yt: YtCache?, pubs: List<Publication>, zone: ZoneId): ChannelNumbers {
        val last = Stats.latest(snaps)
        val subs = yt?.takeIf { !it.hiddenSubscribers }?.subscribers ?: last?.subscribers
        // В режиме открытых данных yt.views — сумма лишь последних роликов, поэтому для цели берём полный счётчик.
        val views = if (yt != null && yt.source == "api") yt.views else last?.totalViews ?: yt?.views
        val videos = yt?.videoCount ?: last?.videoCount
        return ChannelNumbers(subs, views, videos, streak(publishDays(pubs, yt, zone), LocalDate.now(zone)))
    }
}

// =====================================================================
// Анализ «Что работает лучше» — только по реальным данным
// =====================================================================

data class ContentItem(
    val title: String, val views: Long, val likes: Long?, val isShort: Boolean,
    val date: LocalDate?, val gameId: Long?, val videoId: String?,
)

data class Insight(val good: Boolean?, val text: String)

object Analysis {
    fun items(yt: YtCache?, pubs: List<Publication>, games: List<Game>, zone: ZoneId): List<ContentItem> {
        val byVideo = pubs.filter { it.videoId != null }.associateBy { it.videoId }
        val fromYt = yt?.videos.orEmpty().map { v ->
            val linked = byVideo[v.id]
            val game = linked?.gameId ?: games.firstOrNull { g -> g.title.length >= 3 && v.title.contains(g.title, ignoreCase = true) }?.id
            ContentItem(
                v.title, v.views, v.likes, v.isShort,
                runCatching { Instant.parse(v.publishedAt).atZone(zone).toLocalDate() }.getOrNull(), game, v.id,
            )
        }
        val ytIds = fromYt.mapNotNull { it.videoId }.toSet()
        val manual = pubs.filter { it.status == PubStatus.PUBLISHED && it.views != null && (it.videoId == null || it.videoId !in ytIds) }
            .map { ContentItem(it.title, it.views ?: 0, null, it.type == ContentType.SHORTS, it.scheduledAt.toLocalDate(zone), it.gameId, it.videoId) }
        return fromYt + manual
    }

    fun avg(list: List<ContentItem>): Long = if (list.isEmpty()) 0 else list.sumOf { it.views } / list.size

    fun byGame(items: List<ContentItem>, games: List<Game>): List<Triple<String, Long, Int>> =
        items.filter { it.gameId != null }.groupBy { it.gameId }
            .mapNotNull { (id, l) -> games.firstOrNull { it.id == id }?.let { g -> Triple(g.title, avg(l), l.size) } }
            .sortedByDescending { it.second }

    fun byWeekday(items: List<ContentItem>): List<Pair<DayOfWeek, Long>> =
        items.filter { it.date != null }.groupBy { it.date!!.dayOfWeek }
            .map { (d, l) -> d to avg(l) }.sortedBy { it.first.value }

    fun insights(items: List<ContentItem>, games: List<Game>, lang: String): List<Insight> {
        val en = lang == "en"
        if (items.size < 3) return listOf(Insight(null, if (en) "Too little data yet: at least 3 videos with views are needed." else "Пока мало данных: нужно хотя бы 3 ролика с просмотрами."))
        val out = mutableListOf<Insight>()
        val avgAll = avg(items).coerceAtLeast(1)
        val shorts = items.filter { it.isShort }; val longs = items.filter { !it.isShort }
        if (shorts.isNotEmpty() && longs.isNotEmpty()) {
            val a = avg(shorts); val b = avg(longs)
            val (win, lose) = if (a >= b) "Shorts" to (if (en) "long videos" else "длинные") else (if (en) "Long videos" else "Длинные") to "Shorts"
            val ratio = if (minOf(a, b) > 0) maxOf(a, b).toDouble() / minOf(a, b) else 0.0
            out += Insight(true, if (en) "$win get more views than $lose on average (${Fmt.number(maxOf(a, b))} vs ${Fmt.number(minOf(a, b))}${if (ratio >= 1.5) ", ×%.1f".format(ratio) else ""})."
            else "$win в среднем набирают больше, чем $lose (${Fmt.number(maxOf(a, b))} против ${Fmt.number(minOf(a, b))}${if (ratio >= 1.5) ", в %.1f раза".format(ratio) else ""}).")
        }
        val g = byGame(items, games)
        if (g.isNotEmpty()) {
            val best = g.first()
            out += Insight(true, if (en) "Best game: ${best.first} — ${Fmt.number(best.second)} views per video (${best.third} videos)." else "Лучшая игра: ${best.first} — ${Fmt.number(best.second)} просмотров на ролик (роликов: ${best.third}).")
            g.filter { it.second >= avgAll * 1.2 }.forEach {
                out += Insight(true, if (en) "Worth continuing as a series: ${it.first} (above channel average)." else "Стоит продолжить серией: ${it.first} — выше среднего по каналу.")
            }
            g.filter { it.second < avgAll * 0.5 && it.third >= 2 }.forEach {
                out += Insight(false, if (en) "Weak topic: ${it.first} — under half of the average." else "Слабая тема: ${it.first} — меньше половины среднего.")
            }
        }
        val wd = byWeekday(items)
        if (wd.size >= 2) {
            val best = wd.maxBy { it.second }
            val name = best.first.getDisplayName(java.time.format.TextStyle.FULL, localeOf(lang))
            out += Insight(true, if (en) "Best publishing day so far: $name." else "Лучший день публикации пока: $name.")
        }
        val top = items.maxBy { it.views }
        out += Insight(true, if (en) "Top video: “${top.title}” — ${Fmt.number(top.views)} views." else "Самый успешный ролик: «${top.title}» — ${Fmt.number(top.views)} просмотров.")
        val weak = items.filter { it.views < avgAll * 0.3 }
        if (weak.isNotEmpty() && weak.size < items.size) {
            out += Insight(false, if (en) "${weak.size} videos got under 30% of the average — compare their titles with the top ones." else "Роликов ниже 30% от среднего: ${weak.size}. Сравните их названия с лучшими.")
        }
        return out
    }
}

// =====================================================================
// Безопасное хранение секретов (ключ ИИ) — Android Keystore, AES-GCM
// =====================================================================

object SecretStore {
    private const val ALIAS = "lunexa_secret_key"
    private const val PREFS = "lunexa_secure"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return gen.generateKey()
    }

    fun put(ctx: Context, name: String, value: String?) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (value.isNullOrBlank()) { prefs.edit().remove(name).apply(); return }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val enc = c.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(name, Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP)).apply()
    }

    fun get(ctx: Context, name: String): String? = runCatching {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(name, null) ?: return null
        val (iv, data) = raw.split(":").let { Base64.decode(it[0], Base64.NO_WRAP) to Base64.decode(it[1], Base64.NO_WRAP) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(c.doFinal(data), Charsets.UTF_8)
    }.getOrNull()

    fun has(ctx: Context, name: String) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(name)
}

// =====================================================================
// ИИ: любой OpenAI-совместимый API (адрес, модель и ключ задаёт пользователь)
// =====================================================================

class AiException(val kind: String, message: String) : Exception(message)

object AiClient {
    const val KEY_NAME = "ai_api_key"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun chat(baseUrl: String, model: String, apiKey: String, system: String, user: String): String = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || model.isBlank() || apiKey.isBlank()) throw AiException("config", "AI is not configured")
        val url = baseUrl.trim().trimEnd('/').let { if (it.endsWith("/chat/completions")) it else "$it/chat/completions" }
        val body = buildJsonObject {
            put("model", model.trim())
            put("temperature", 0.8)
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", system) }
                addJsonObject { put("role", "user"); put("content", user) }
            }
        }.toString()
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 20_000
            c.readTimeout = 120_000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = runCatching {
                    json.parseToJsonElement(text).jsonObject["error"]?.let { e ->
                        runCatching { e.jsonObject["message"]?.jsonPrimitive?.contentOrNull }.getOrNull() ?: e.jsonPrimitive.contentOrNull
                    }
                }.getOrNull() ?: "HTTP $code"
                throw AiException(if (code == 401 || code == 403) "auth" else if (code == 429) "limit" else "http", msg)
            }
            json.parseToJsonElement(text).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw AiException("empty", "Empty response")
        } finally {
            c.disconnect()
        }
    }

    fun explain(e: Throwable, lang: String): String {
        val en = lang == "en"
        return when {
            e is AiException && e.kind == "config" -> if (en) "Set up the AI service first (gear icon)." else "Сначала настройте ИИ-сервис (значок шестерёнки)."
            e is AiException && e.kind == "auth" -> (if (en) "Key rejected: " else "Ключ не принят: ") + e.message
            e is AiException && e.kind == "limit" -> (if (en) "Request limit or balance exhausted: " else "Лимит запросов или баланс исчерпан: ") + e.message
            e is AiException -> (if (en) "AI service error: " else "Ошибка ИИ-сервиса: ") + e.message
            e is java.net.UnknownHostException -> if (en) "Service address not found. Check the URL and internet." else "Адрес сервиса не найден. Проверьте адрес и интернет."
            e is java.net.SocketTimeoutException -> if (en) "The service took too long to respond." else "Сервис слишком долго не отвечает."
            e is java.io.IOException -> (if (en) "Network error: " else "Ошибка сети: ") + (e.message ?: "")
            else -> (if (en) "Error: " else "Ошибка: ") + (e.message ?: e.javaClass.simpleName)
        }
    }
}

fun goalTitle(g: Goal, lang: String): String {
    val en = lang == "en"
    val n = Fmt.number(g.target)
    return when (g.type) {
        GoalType.SUBSCRIBERS -> if (en) "$n subscribers" else "$n подписчиков"
        GoalType.VIEWS -> if (en) "$n views" else "$n просмотров"
        GoalType.VIDEOS -> if (en) "$n videos" else "$n видео"
        GoalType.STREAK -> if (en) "${g.target} days of regular uploads" else "${g.target} дней регулярных публикаций"
        GoalType.CUSTOM -> g.title.ifBlank { n }
    }
}
