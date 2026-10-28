package com.lunexa.studio.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lunexa.studio.data.Fmt
import com.lunexa.studio.data.YtCache
import com.lunexa.studio.data.YtDay
import com.lunexa.studio.data.localeOf
import com.lunexa.studio.ui.BarList
import com.lunexa.studio.ui.ConfirmDialog
import com.lunexa.studio.ui.LX
import com.lunexa.studio.ui.LineChart
import com.lunexa.studio.ui.LocalLang
import com.lunexa.studio.ui.LocalRepo
import com.lunexa.studio.ui.LocalZone
import com.lunexa.studio.ui.LxCard
import com.lunexa.studio.ui.Pill
import com.lunexa.studio.ui.SectionTitle
import com.lunexa.studio.ui.StatTile
import com.lunexa.studio.ui.rememberYouTubeConnector
import com.lunexa.studio.ui.tr
import java.time.Instant
import java.util.Locale

/** Блок подключения YouTube для настроек и пустой аналитики. */
@Composable
fun YouTubeConnectBlock() {
    val repo = LocalRepo.current
    val zone = LocalZone.current
    val lang = LocalLang.current
    val cache by repo.youtube.collectAsStateWithLifecycle()
    val connector = rememberYouTubeConnector()
    var confirm by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    Text("YouTube", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(6.dp))
    val c = cache
    if (c == null) {
        Text(
            tr("Вставьте ссылку на свой канал или @имя. Приложение покажет открытые данные: подписчиков, число видео и последние ролики с просмотрами и лайками. Вход и пароль не нужны.",
                "Paste your channel link or @handle. The app shows public data: subscribers, video count and recent videos with views and likes. No sign-in needed."),
            style = MaterialTheme.typography.bodySmall, color = LX.Muted,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            input, { input = it.take(200) }, singleLine = true,
            label = { Text(tr("Ссылка на канал или @имя", "Channel link or @handle")) },
            placeholder = { Text("https://youtube.com/@…") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { connector?.connectPublic(input) },
            enabled = connector != null && !connector.busy && input.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (connector?.busy == true) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
            Text(tr("Подключить канал", "Connect channel"))
        }
        Text(
            tr("Подсказка: в YouTube откройте свой канал → «Поделиться» → «Копировать ссылку».", "Tip: in YouTube open your channel → Share → Copy link."),
            style = MaterialTheme.typography.labelSmall, color = LX.Muted,
        )
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = { connector?.connect() }, enabled = connector != null && !connector.busy) {
            Text(tr("Полная аналитика через вход Google (нужен Google Cloud)", "Full analytics via Google sign-in (needs Google Cloud)"))
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.thumb, c.title, 40)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(c.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (c.source == "public") tr("Открытые данные · ", "Public data · ") else tr("Вход Google · ", "Google sign-in · ")) +
                        Fmt.dateTime(c.syncedAt, zone, lang),
                    style = MaterialTheme.typography.bodySmall, color = LX.Muted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { connector?.refresh() }, enabled = connector != null && !connector.busy, modifier = Modifier.weight(1f)) {
                if (connector?.busy == true) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(tr("Обновить", "Refresh"))
            }
            OutlinedButton(onClick = { confirm = true }, modifier = Modifier.weight(1f)) { Text(tr("Отключить", "Disconnect")) }
        }
    }
    connector?.message?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, color = if (connector?.isError == true) LX.Accent else LX.Ok)
    }
    if (confirm) {
        ConfirmDialog(
            tr("Отключить канал?", "Disconnect channel?"),
            tr("Доступ приложения будет отозван, загруженная статистика удалится. Ваши идеи, расписание и ручные записи останутся.",
                "App access will be revoked and synced stats removed. Your ideas, schedule and manual entries stay."),
            tr("Отключить", "Disconnect"),
            onConfirm = { connector?.disconnect() },
            onDismiss = { confirm = false },
        )
    }
}

@Composable
fun Avatar(url: String?, name: String, sizeDp: Int) {
    Box(
        Modifier.size(sizeDp.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
        if (url != null) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

// ---------- Расчёты по реальным данным ----------

fun YtCache.lastDays(n: Int): List<YtDay> = if (n <= 0) daily else daily.takeLast(n)

data class YtTotals(val views: Long, val minutes: Long, val avgDur: Long, val subsNet: Long, val gained: Long, val lost: Long, val days: Int)

fun totals(days: List<YtDay>): YtTotals {
    val views = days.sumOf { it.views }
    val avg = if (views > 0) days.sumOf { it.avgDurSec * it.views } / views else 0
    val g = days.sumOf { it.subsGained }
    val l = days.sumOf { it.subsLost }
    return YtTotals(views, days.sumOf { it.minutes }, avg, g - l, g, l, days.size)
}

private fun trafficName(key: String, lang: String): String {
    val ru = lang != "en"
    return when (key) {
        "YT_SEARCH" -> if (ru) "Поиск YouTube" else "YouTube search"
        "SHORTS" -> if (ru) "Лента Shorts" else "Shorts feed"
        "RELATED_VIDEO" -> if (ru) "Рекомендуемые видео" else "Suggested videos"
        "SUBSCRIBER" -> if (ru) "Главная и подписки" else "Browse & subscriptions"
        "YT_CHANNEL" -> if (ru) "Страница канала" else "Channel page"
        "EXT_URL" -> if (ru) "Внешние сайты" else "External"
        "NO_LINK_OTHER" -> if (ru) "Прямые/неизвестные" else "Direct/unknown"
        "PLAYLIST" -> if (ru) "Плейлисты" else "Playlists"
        "NOTIFICATION" -> if (ru) "Уведомления" else "Notifications"
        "YT_OTHER_PAGE" -> if (ru) "Другие страницы YouTube" else "Other YouTube pages"
        "END_SCREEN" -> if (ru) "Конечные заставки" else "End screens"
        "HASHTAGS" -> if (ru) "Хештеги" else "Hashtags"
        "SOUND_PAGE" -> if (ru) "Страница звука" else "Sound page"
        "ADVERTISING" -> if (ru) "Реклама" else "Advertising"
        else -> key.lowercase().replace('_', ' ')
    }
}

private fun ageName(key: String) = key.removePrefix("age").replace("-", "–")

private fun countryName(code: String, lang: String): String =
    Locale("", code).getDisplayCountry(localeOf(lang)).ifBlank { code }

private fun durText(sec: Long, lang: String) =
    if (sec >= 60) "${sec / 60}:" + "%02d".format(sec % 60) else if (lang == "en") "${sec}s" else "$sec с"

/** Раздел реальной аналитики YouTube внутри LazyColumn. period: 7/28/90/365, 0 — всё (до года). */
fun LazyListScope.youtubeAnalytics(c: YtCache, period: Int, lang: String) {
    val days = c.lastDays(if (period == 0) 0 else period)
    val t = totals(days)

    item { SectionTitle("YouTube · " + c.title) }
    if (c.source == "public") item {
        LxCard {
            Text(
                tr("Режим открытых данных: подписчики (округлённо, как на странице канала) и последние ${c.videos.size} роликов. Время просмотра, источники трафика и страны доступны только через вход Google.",
                    "Public data mode: subscribers (rounded, as on the channel page) and the last ${c.videos.size} videos. Watch time, traffic sources and countries need Google sign-in."),
                style = MaterialTheme.typography.bodySmall, color = LX.Muted,
            )
        }
    }
    if (c.analyticsError != null) item {
        LxCard {
            Text(tr("Статистика канала загружена, но YouTube Analytics недоступна:", "Channel stats loaded, but YouTube Analytics is unavailable:"),
                style = MaterialTheme.typography.bodySmall)
            Text(c.analyticsError, style = MaterialTheme.typography.bodySmall, color = LX.Accent)
            Text(tr("Проверьте, что в Google Cloud включён YouTube Analytics API.", "Make sure YouTube Analytics API is enabled in Google Cloud."),
                style = MaterialTheme.typography.bodySmall, color = LX.Muted)
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val pub = c.source == "public"
            StatTile(tr("Подписчики", "Subscribers"), if (c.hiddenSubscribers) "—" else (if (pub) "≈" else "") + Fmt.number(c.subscribers), Modifier.weight(1f),
                sub = if (days.isNotEmpty()) "${Fmt.signed(t.subsNet)} " + tr("за период", "in period") else null,
                subColor = if (t.subsNet >= 0) LX.Ok else LX.Accent)
            StatTile(
                if (pub) tr("Просмотры ${c.videos.size} видео", "Views, ${c.videos.size} videos") else tr("Просмотры", "Views"),
                Fmt.number(c.views), Modifier.weight(1f),
                sub = if (days.isNotEmpty()) "+${Fmt.number(t.views)} " + tr("за период", "in period") else null)
            if (pub) StatTile(tr("Видео", "Videos"), Fmt.number(c.videoCount), Modifier.weight(0.8f))
        }
    }
    if (days.isNotEmpty()) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(tr("Время просмотра", "Watch time"), Fmt.number(t.minutes / 60) + tr(" ч", " h"), Modifier.weight(1f))
                StatTile(tr("Ср. просмотр", "Avg view"), durText(t.avgDur, lang), Modifier.weight(1f))
                StatTile(tr("Отписки", "Unsubs"), Fmt.number(t.lost), Modifier.weight(0.8f))
            }
        }
        item { SectionTitle(tr("Просмотры по дням", "Daily views")) }
        item {
            LxCard {
                LineChart(days.map { it.views.toFloat() })
                Spacer(Modifier.height(6.dp))
                Row {
                    Text(days.first().date, style = MaterialTheme.typography.labelSmall, color = LX.Muted, modifier = Modifier.weight(1f))
                    Text(days.last().date, style = MaterialTheme.typography.labelSmall, color = LX.Muted)
                }
                Text(tr("YouTube отдаёт аналитику с задержкой 1–3 дня.", "YouTube analytics lags by 1–3 days."),
                    style = MaterialTheme.typography.labelSmall, color = LX.Muted)
            }
        }
        item { SectionTitle(tr("Рост подписчиков (накопительно)", "Subscriber growth (cumulative)")) }
        item {
            LxCard {
                var acc = 0L
                LineChart(days.map { acc += it.subsGained - it.subsLost; acc.toFloat() }, color = LX.Ok)
            }
        }
    }

    // Shorts vs длинные — по последним 50 роликам
    if (c.videos.isNotEmpty()) {
        val shorts = c.videos.filter { it.isShort }
        val longs = c.videos.filter { !it.isShort }
        item { SectionTitle(tr("Shorts vs длинные", "Shorts vs long")) }
        item {
            LxCard {
                BarList(
                    listOfNotNull(
                        shorts.takeIf { it.isNotEmpty() }?.let { "Shorts · ${it.size}" to it.sumOf { v -> v.views } / it.size },
                        longs.takeIf { it.isNotEmpty() }?.let { trL(lang, "Длинные", "Long") + " · ${it.size}" to it.sumOf { v -> v.views } / it.size },
                    ),
                ) { i -> if (i == 0 && shorts.isNotEmpty()) LX.Accent else LX.Warn }
                Spacer(Modifier.height(6.dp))
                Text(tr("Среднее число просмотров на ролик. По последним ${c.videos.size} роликам, Shorts — до 3 минут.",
                    "Average views per video. Based on the last ${c.videos.size} videos; Shorts = up to 3 min."),
                    style = MaterialTheme.typography.labelSmall, color = LX.Muted)
            }
        }
    }

    if (c.videoStats.isNotEmpty()) {
        val titles = c.videos.associateBy { it.id }
        val top = c.videoStats.take(10)
        item { SectionTitle(tr("Лучшие ролики за год", "Top videos this year")) }
        item {
            LxCard {
                BarList(top.map { (titles[it.id]?.title ?: it.id) to it.views }) { i -> if (titles[top[i].id]?.isShort == true) LX.Accent else LX.Warn }
                val subsTop = c.videoStats.filter { it.subsGained > 0 }.sortedByDescending { it.subsGained }.take(3)
                if (subsTop.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(tr("Больше всего подписчиков принесли:", "Most subscribers came from:"), style = MaterialTheme.typography.labelMedium, color = LX.Muted)
                    subsTop.forEach {
                        Text("• ${titles[it.id]?.title ?: it.id} — +${it.subsGained}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    if (c.traffic.isNotEmpty()) {
        item { SectionTitle(tr("Источники трафика · 28 дн", "Traffic sources · 28d")) }
        item { LxCard { BarList(c.traffic.take(8).map { trafficName(it.key, lang) to it.value.toLong() }) } }
    }
    if (c.countries.isNotEmpty()) {
        item { SectionTitle(tr("Страны зрителей · 28 дн", "Viewer countries · 28d")) }
        item { LxCard { BarList(c.countries.map { countryName(it.key, lang) to it.value.toLong() }) { LX.Cyan } } }
    }
    if (c.ages.isNotEmpty()) {
        item { SectionTitle(tr("Возраст зрителей, % · 28 дн", "Viewer age, % · 28d")) }
        item { LxCard { BarList(c.ages.map { ageName(it.key) to Math.round(it.value) }) { LX.Warn } } }
    }

    if (c.videos.isNotEmpty()) {
        item { SectionTitle(tr("Последние ролики", "Recent videos")) }
        items(c.videos.take(20), key = { "yt_${it.id}" }) { v -> VideoRow(v) }
    }
}

private fun trL(lang: String, ru: String, en: String) = if (lang == "en") en else ru

@Composable
private fun VideoRow(v: com.lunexa.studio.data.YtVideo) {
    val ctx = LocalContext.current
    val lang = LocalLang.current
    val zone = LocalZone.current
    LxCard(onClick = {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/${v.id}"))) }
    }, padding = PaddingValues(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(96.dp).height(54.dp).clip(RoundedCornerShape(10.dp)).background(LX.Card2)) {
                if (v.thumb != null) AsyncImage(v.thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(v.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val date = runCatching { Fmt.date(Instant.parse(v.publishedAt).toEpochMilli(), zone, lang) }.getOrDefault("")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (v.isShort) { Pill("Shorts", LX.Accent); Spacer(Modifier.width(6.dp)) }
                    Text(
                        "$date · 👁 ${Fmt.number(v.views)} · 👍 ${Fmt.number(v.likes)} · 💬 ${Fmt.number(v.comments)}",
                        style = MaterialTheme.typography.labelSmall, color = LX.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Данные для главной: реальные показатели за последние n дней. */
fun YtCache.viewsLast(n: Int): Long? = if (daily.isEmpty()) null else daily.takeLast(n).sumOf { it.views }
fun YtCache.subsNetLast(n: Int): Long? = if (daily.isEmpty()) null else daily.takeLast(n).sumOf { it.subsGained - it.subsLost }
