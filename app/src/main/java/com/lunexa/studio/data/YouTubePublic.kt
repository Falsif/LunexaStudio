package com.lunexa.studio.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Режим без входа: только открытые данные канала, которые видит любой зритель.
 * Источники — публичная страница канала (имя, аватар, примерное число подписчиков и видео)
 * и официальная RSS-лента YouTube (15 последних роликов с просмотрами и лайками).
 * Ключи API и Google Cloud не нужны.
 */
object YouTubePublic {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

    class PublicException(val kind: String, message: String) : Exception(message)

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            c.setRequestProperty("Cookie", "SOCS=CAI; CONSENT=YES+1")
            c.connectTimeout = 20_000
            c.readTimeout = 30_000
            val code = c.responseCode
            if (code == 404) throw PublicException("notFound", "Channel not found")
            if (code !in 200..299) throw PublicException("http", "HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** Превращает ввод пользователя в адрес страницы канала. */
    fun pageUrl(input: String): String {
        // Убираем хвосты ссылки «Поделиться»: ?si=…, #…
        val t = input.trim().substringBefore("?").substringBefore("#").removeSuffix("/")
        val id = Regex("""(UC[\w-]{22})""").find(t)?.value
        return when {
            id != null -> "https://www.youtube.com/channel/$id"
            t.contains("youtube.com", ignoreCase = true) -> {
                val noProto = t.substringAfter("://", t)
                val path = noProto.substringAfter("/", "")
                "https://www.youtube.com/$path"
            }
            t.startsWith("@") -> "https://www.youtube.com/$t"
            else -> "https://www.youtube.com/@${t.replace(" ", "")}"
        }
    }

    private fun unescape(s: String) = s
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        .replace("\\u0026", "&")

    /** «1.23K» → 1230, «12,345» → 12345. */
    fun parseCount(num: String, suffix: String): Long? {
        val n = num.replace(",", "").toDoubleOrNull() ?: return null
        val mult = when (suffix.uppercase(Locale.US)) { "K" -> 1_000.0; "M" -> 1_000_000.0; "B" -> 1_000_000_000.0; else -> 1.0 }
        return Math.round(n * mult)
    }

    /** Фрагмент страницы с шапкой канала (новая и старая разметка YouTube). */
    private fun headerRegion(html: String): String? {
        for (key in listOf("\"pageHeaderViewModel\"", "\"c4TabbedHeaderRenderer\"", "\"pageHeaderRenderer\"")) {
            val i = html.indexOf(key)
            if (i >= 0) return html.substring(i, minOf(html.length, i + 30_000))
        }
        return null
    }

    /** «5 subscribers», «1 subscriber», «1.2K subscribers», «No subscribers». */
    private fun countIn(region: String, word: String): Long? {
        if (Regex(""""(?:content|simpleText)":"No ${word}s"""").containsMatchIn(region)) return 0
        val m = Regex(""""(?:content|simpleText)":"([\d][\d.,]*)\s?([KMB]?)\s+${word}s?"""").find(region)
            ?: Regex("""([\d][\d.,]*)\s?([KMB]?)\s+${word}s?\b""").find(region)
            ?: return null
        return parseCount(m.groupValues[1], m.groupValues[2])
    }

    suspend fun fetch(input: String): YtCache = withContext(Dispatchers.IO) {
        if (input.isBlank()) throw PublicException("empty", "Empty input")
        val html = get(pageUrl(input))
        val id = Regex(""""externalId":"(UC[\w-]{22})"""").find(html)?.groupValues?.get(1)
            ?: Regex("""<link rel="canonical" href="https://www\.youtube\.com/channel/(UC[\w-]{22})"""").find(html)?.groupValues?.get(1)
            ?: Regex(""""(?:channelId|browseId)":"(UC[\w-]{22})"""").find(html)?.groupValues?.get(1)
            ?: throw PublicException("notFound", "Channel id not found")
        val title = Regex("""<meta property="og:title" content="([^"]*)"""").find(html)?.groupValues?.get(1)?.let(::unescape)
        val avatar = Regex("""<meta property="og:image" content="([^"]*)"""").find(html)?.groupValues?.get(1)?.let(::unescape)
        // Числа берём только из шапки этого канала: на странице есть и чужие каналы (рекомендации).
        val header = headerRegion(html)
        val subs = header?.let { countIn(it, "subscriber") }
        val videoCount = header?.let { countIn(it, "video") }

        val (feedTitle, videos) = parseFeed(get("https://www.youtube.com/feeds/videos.xml?channel_id=$id"))

        YtCache(
            channelId = id,
            title = title ?: feedTitle ?: "YouTube",
            thumb = avatar,
            subscribers = subs ?: 0,
            hiddenSubscribers = subs == null,
            views = videos.sumOf { it.views },
            videoCount = videoCount?.takeIf { it >= videos.size } ?: videos.size.toLong(),
            videos = videos,
            source = "public",
        )
    }

    private fun parseFeed(xml: String): Pair<String?, List<YtVideo>> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(xml.reader())
        var feedTitle: String? = null
        val list = mutableListOf<YtVideo>()
        var inEntry = false
        var id = ""; var title = ""; var published = ""; var thumb: String? = null
        var views = 0L; var likes = 0L; var isShort: Boolean? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "entry" -> { inEntry = true; id = ""; title = ""; published = ""; thumb = null; views = 0; likes = 0; isShort = null }
                    "yt:videoId" -> if (inEntry) id = p.nextText()
                    "title" -> if (inEntry) title = p.nextText() else if (feedTitle == null) feedTitle = p.nextText()
                    "published" -> if (inEntry) published = p.nextText()
                    "link" -> if (inEntry) p.getAttributeValue(null, "href")?.let { isShort = it.contains("/shorts/") }
                    "media:thumbnail" -> if (inEntry) thumb = p.getAttributeValue(null, "url")
                    "media:statistics" -> if (inEntry) views = p.getAttributeValue(null, "views")?.toLongOrNull() ?: 0
                    "media:starRating" -> if (inEntry) likes = p.getAttributeValue(null, "count")?.toLongOrNull() ?: 0
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "entry") {
                inEntry = false
                if (id.isNotEmpty()) list += YtVideo(
                    id = id, title = title, publishedAt = published, thumb = thumb,
                    views = views, likes = likes, shortsHint = isShort,
                )
            }
            ev = p.next()
        }
        return feedTitle to list
    }

    fun explain(e: Throwable, lang: String): String {
        val en = lang == "en"
        return when {
            e is PublicException && e.kind == "empty" -> if (en) "Enter a channel link or @handle." else "Введите ссылку на канал или @имя."
            e is PublicException && e.kind == "notFound" ->
                if (en) "Channel not found. Copy the link from YouTube: channel → Share → Copy link." else "Канал не найден. Скопируйте ссылку в YouTube: канал → «Поделиться» → «Копировать ссылку»."
            e is java.net.UnknownHostException || e is java.net.SocketTimeoutException || e is java.io.IOException ->
                if (en) "YouTube is not reachable. Check the internet (or the VPN, if YouTube is slow in your region)." else "YouTube не отвечает. Проверьте интернет (или VPN, если YouTube у вас замедлен)."
            else -> (if (en) "Error: " else "Ошибка: ") + (e.message ?: e.javaClass.simpleName)
        }
    }
}
