package com.lunexa.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lunexa.studio.data.ChannelSnapshot
import com.lunexa.studio.data.ContentType
import com.lunexa.studio.data.Fmt
import com.lunexa.studio.data.PubStatus
import com.lunexa.studio.data.toLocalDate
import com.lunexa.studio.ui.BarList
import com.lunexa.studio.ui.ChipsRow
import com.lunexa.studio.ui.ConfirmDialog
import com.lunexa.studio.ui.DateField
import com.lunexa.studio.ui.EmptyState
import com.lunexa.studio.ui.LX
import com.lunexa.studio.ui.LineChart
import com.lunexa.studio.ui.LocalLang
import com.lunexa.studio.ui.LocalRepo
import com.lunexa.studio.ui.LocalZone
import com.lunexa.studio.ui.LxCard
import com.lunexa.studio.ui.LxScaffold
import com.lunexa.studio.ui.LxTopBar
import com.lunexa.studio.ui.NumberField
import com.lunexa.studio.ui.Routes
import com.lunexa.studio.ui.SectionTitle
import com.lunexa.studio.ui.StatTile
import com.lunexa.studio.ui.color
import com.lunexa.studio.ui.label
import com.lunexa.studio.ui.tr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

private val periods = listOf(7, 28, 90, 365, 0) // 0 — всё время

@Composable
fun AnalyticsScreen(nav: NavHostController) {
    val repo = LocalRepo.current
    val zone = LocalZone.current
    val lang = LocalLang.current
    val snaps by repo.snapshots.collectAsStateWithLifecycle(emptyList())
    val pubs by repo.publications.collectAsStateWithLifecycle(emptyList())
    val games by repo.games.collectAsStateWithLifecycle(emptyList())
    var period by rememberSaveable { mutableStateOf(28) }

    val today = LocalDate.now(zone)
    val fromDay = if (period == 0) Long.MIN_VALUE else today.minusDays(period.toLong()).toEpochDay()
    val inRange = snaps.filter { it.dateEpochDay >= fromDay }
    val first = inRange.firstOrNull()
    val last = inRange.lastOrNull()
    val published = pubs.filter {
        it.status == PubStatus.PUBLISHED && it.scheduledAt.toLocalDate(zone).toEpochDay() >= fromDay
    }

    LxScaffold(
        topBar = { LxTopBar(tr("Аналитика", "Analytics")) },
        fab = {
            FloatingActionButton(onClick = { nav.navigate(Routes.snapshot(0)) }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Outlined.Add, tr("Добавить показатели", "Add stats"))
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                LxCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Info, null, tint = LX.Cyan)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            tr(
                                "Сейчас аналитика строится по данным, которые вы вводите из YouTube Studio. Автоматическая синхронизация через YouTube Analytics API появится на этапе 2.",
                                "Analytics currently uses data you enter from YouTube Studio. Automatic sync via the YouTube Analytics API arrives in stage 2.",
                            ),
                            style = MaterialTheme.typography.bodySmall, color = LX.Muted,
                        )
                    }
                }
            }
            item {
                ChipsRow(periods, period, {
                    when (it) { 0 -> tr2p(lang, "Всё время", "All time"); 365 -> tr2p(lang, "Год", "Year"); else -> tr2p(lang, "$it дн", "${it}d") }
                }, { period = it })
            }

            if (snaps.isEmpty()) {
                item {
                    EmptyState(Icons.Outlined.Insights, tr("Нет данных канала", "No channel data"),
                        tr("Записывайте показатели хотя бы раз в неделю — появятся графики роста.", "Log your stats at least weekly to unlock growth charts."),
                        action = tr("Добавить", "Add"), onAction = { nav.navigate(Routes.snapshot(0)) })
                }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(tr("Прирост подписчиков", "Subscribers gained"),
                            if (first != null && last != null && first !== last) Fmt.signed(last.subscribers - first.subscribers) else "—",
                            Modifier.weight(1f), sub = last?.let { tr("сейчас ", "now ") + Fmt.number(it.subscribers) })
                        StatTile(tr("Новые просмотры", "New views"),
                            if (first != null && last != null && first !== last) Fmt.signed(last.totalViews - first.totalViews) else "—",
                            Modifier.weight(1f), sub = last?.let { tr("всего ", "total ") + Fmt.number(it.totalViews) })
                    }
                }
                if (first != null && last != null && first !== last) item {
                    Text(
                        tr("По записям с ", "From entries ") + Fmt.shortDayEpoch(first.dateEpochDay, lang) + " — " + Fmt.shortDayEpoch(last.dateEpochDay, lang),
                        style = MaterialTheme.typography.bodySmall, color = LX.Muted,
                    )
                }
                item { SectionTitle(tr("Подписчики", "Subscribers")) }
                item { ChartCard(inRange.map { it.subscribers.toFloat() }, MaterialTheme.colorScheme.primary) }
                item { SectionTitle(tr("Просмотры (всего)", "Views (total)")) }
                item { ChartCard(inRange.map { it.totalViews.toFloat() }, LX.Cyan) }
            }

            // По типам контента — из публикаций с введёнными просмотрами
            item { SectionTitle(tr("Shorts vs длинные vs TikTok", "Shorts vs long vs TikTok")) }
            item {
                LxCard {
                    val withViews = published.filter { it.views != null }
                    if (withViews.isEmpty()) {
                        Text(
                            tr("Отметьте публикации как «Опубликовано» и укажите просмотры — здесь появится сравнение.",
                                "Mark uploads as “Published” and enter their views to see the comparison."),
                            color = LX.Muted, style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        val rows = ContentType.entries.mapNotNull { t ->
                            val list = withViews.filter { it.type == t }
                            if (list.isEmpty()) null else Triple(t, list.size, list.sumOf { it.views ?: 0L })
                        }
                        BarList(rows.map { (t, n, v) -> "${t.label(lang)} · $n" to v }) { i -> rows[i].first.color }
                        Spacer(Modifier.height(10.dp))
                        rows.forEach { (t, n, v) ->
                            Text(
                                "${t.label(lang)}: " + tr("в среднем ${Fmt.number(v / n)} просм. на ролик", "avg ${Fmt.number(v / n)} views per video"),
                                style = MaterialTheme.typography.bodySmall, color = LX.Muted,
                            )
                        }
                    }
                }
            }

            item { SectionTitle(tr("Игры по просмотрам", "Games by views")) }
            item {
                LxCard {
                    val byGame = published.filter { it.views != null && it.gameId != null }
                        .groupBy { it.gameId }
                        .mapNotNull { (gid, list) -> games.firstOrNull { it.id == gid }?.let { g -> g.title to list.sumOf { p -> p.views ?: 0L } } }
                        .sortedByDescending { it.second }
                        .take(8)
                    if (byGame.isEmpty()) Text(tr("Нет опубликованных роликов с играми и просмотрами за период.", "No published videos with games and views in this period."), color = LX.Muted)
                    else BarList(byGame)
                }
            }

            item { SectionTitle(tr("Лучшие ролики периода", "Top videos in period")) }
            item {
                LxCard {
                    val top = published.filter { it.views != null }.sortedByDescending { it.views }.take(5)
                    if (top.isEmpty()) Text(tr("Пока нет данных о просмотрах роликов.", "No video view data yet."), color = LX.Muted)
                    else BarList(top.map { it.title to (it.views ?: 0L) }) { i -> top[i].type.color }
                }
            }

            if (snaps.isNotEmpty()) {
                item { SectionTitle(tr("Записи показателей", "Stat entries")) }
                items(snaps.reversed(), key = { it.id }) { sn ->
                    LxCard(onClick = { nav.navigate(Routes.snapshot(sn.id)) }, padding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Fmt.shortDayEpoch(sn.dateEpochDay, lang), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            Text(
                                "👥 ${Fmt.number(sn.subscribers)}   👁 ${Fmt.number(sn.totalViews)}   ▶ ${Fmt.number(sn.videoCount)}",
                                style = MaterialTheme.typography.bodySmall, color = LX.Muted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(values: List<Float>, color: androidx.compose.ui.graphics.Color) {
    LxCard {
        if (values.size < 2) Text(tr("Для графика нужно минимум 2 записи в выбранном периоде.", "At least 2 entries in the period are needed for a chart."), color = LX.Muted)
        else LineChart(values, color = color)
    }
}

@Composable
fun SnapshotEditScreen(nav: NavHostController, id: Long) {
    val repo = LocalRepo.current
    val zone = LocalZone.current
    val scope = rememberCoroutineScope()
    var loaded by rememberSaveable { mutableStateOf(false) }
    var original by remember { mutableStateOf<ChannelSnapshot?>(null) }
    var day by rememberSaveable { mutableStateOf(LocalDate.now(zone).toEpochDay()) }
    var subs by rememberSaveable { mutableStateOf("") }
    var views by rememberSaveable { mutableStateOf("") }
    var videos by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != 0L) {
            val s = repo.snapshot(id)
            original = s
            if (s != null && !loaded) {
                day = s.dateEpochDay; subs = s.subscribers.toString(); views = s.totalViews.toString()
                videos = s.videoCount.toString(); note = s.note
            }
        } else if (!loaded) {
            // Подставляем последние значения, чтобы обновлять только изменившиеся числа.
            repo.snapshots.collectLatestOnce()?.let { last ->
                subs = last.subscribers.toString(); views = last.totalViews.toString(); videos = last.videoCount.toString()
            }
        }
        loaded = true
    }

    val s = subs.toLongOrNull(); val v = views.toLongOrNull(); val c = videos.toLongOrNull()
    val valid = s != null && v != null && c != null && LocalDate.ofEpochDay(day) <= LocalDate.now(zone)

    LxScaffold(
        topBar = {
            LxTopBar(tr("Показатели канала", "Channel stats"), onBack = { nav.popBackStack() }, actions = {
                if (id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, tr("Удалить", "Delete")) }
            })
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                tr("Возьмите цифры из YouTube Studio → «Аналитика». Одна запись на дату: повторная запись за ту же дату заменит прежнюю.",
                    "Take the numbers from YouTube Studio → Analytics. One entry per date: a new entry for the same date replaces the old one."),
                style = MaterialTheme.typography.bodySmall, color = LX.Muted,
            )
            DateField(tr("Дата", "Date"), LocalDate.ofEpochDay(day), { if (it != null) day = it.toEpochDay() })
            if (LocalDate.ofEpochDay(day) > LocalDate.now(zone)) Text(tr("Дата не может быть в будущем", "Date can't be in the future"), color = LX.Accent, style = MaterialTheme.typography.bodySmall)
            NumberField(tr("Подписчики", "Subscribers"), subs, { subs = it }, isError = tried && s == null)
            NumberField(tr("Всего просмотров", "Total views"), views, { views = it }, isError = tried && v == null)
            NumberField(tr("Опубликовано видео", "Videos published"), videos, { videos = it }, isError = tried && c == null)
            OutlinedTextField(note, { note = it.take(500) }, label = { Text(tr("Заметка", "Note")) }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    tried = true
                    if (valid) {
                        val item = ChannelSnapshot(dateEpochDay = day, subscribers = s!!, totalViews = v!!, videoCount = c!!, note = note.trim())
                        repo.launch { saveSnapshot(item, original?.id) }
                        nav.popBackStack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(tr("Сохранить", "Save")) }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            tr("Удалить запись?", "Delete entry?"), tr("Графики пересчитаются без неё.", "Charts will be recalculated without it."), tr("Удалить", "Delete"),
            onConfirm = { original?.let { sn -> scope.launch { repo.deleteSnapshot(sn); nav.popBackStack() } } },
            onDismiss = { confirmDelete = false },
        )
    }
}

private suspend fun Flow<List<ChannelSnapshot>>.collectLatestOnce(): ChannelSnapshot? =
    first().maxByOrNull { it.dateEpochDay }
