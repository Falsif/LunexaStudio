package com.lunexa.studio.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.lunexa.studio.data.Fmt
import com.lunexa.studio.data.PubStatus
import com.lunexa.studio.data.Stats
import com.lunexa.studio.data.toLocalDate
import com.lunexa.studio.ui.GoalBar
import com.lunexa.studio.ui.LX
import com.lunexa.studio.ui.LineChart
import com.lunexa.studio.ui.LocalLang
import com.lunexa.studio.ui.LocalRepo
import com.lunexa.studio.ui.LocalSettings
import com.lunexa.studio.ui.LocalZone
import com.lunexa.studio.ui.LxCard
import com.lunexa.studio.ui.Pill
import com.lunexa.studio.ui.Routes
import com.lunexa.studio.ui.SectionTitle
import com.lunexa.studio.ui.StatTile
import com.lunexa.studio.ui.color
import com.lunexa.studio.ui.label
import com.lunexa.studio.ui.switchTab
import com.lunexa.studio.ui.tr
import androidx.navigation.NavHostController
import java.time.LocalDate

@Composable
fun HomeScreen(nav: NavHostController) {
    val repo = LocalRepo.current
    val s = LocalSettings.current
    val zone = LocalZone.current
    val lang = LocalLang.current
    val snaps by repo.snapshots.collectAsStateWithLifecycle(emptyList())
    val pubs by repo.publications.collectAsStateWithLifecycle(emptyList())
    val tasks by repo.tasks.collectAsStateWithLifecycle(emptyList())
    val games by repo.games.collectAsStateWithLifecycle(emptyList())
    val now = remember(pubs, tasks) { System.currentTimeMillis() }
    val today = LocalDate.now(zone)

    val last = Stats.latest(snaps)
    val views7 = Stats.delta(snaps, 7) { it.totalViews }
    val views28 = Stats.delta(snaps, 28) { it.totalViews }
    val subs28 = Stats.delta(snaps, 28) { it.subscribers }

    val active = setOf(PubStatus.PLANNED, PubStatus.READY)
    val next = pubs.filter { it.scheduledAt >= now && it.status in active }.minByOrNull { it.scheduledAt }
    val lastPublished = pubs.filter { it.status == PubStatus.PUBLISHED }.maxByOrNull { it.scheduledAt }
    val overdue = pubs.count { it.scheduledAt < now && it.status in active }
    val todayPubs = pubs.count { it.scheduledAt.toLocalDate(zone) == today && it.status in active }
    val tasksDue = tasks.count { !it.done && it.dueAt != null && it.dueAt.toLocalDate(zone) <= today }
    val gameName = { id: Long? -> games.firstOrNull { it.id == id }?.title }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ----- Шапка канала -----
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, LX.Red2))),
                    contentAlignment = Alignment.Center,
                ) {
                    val name = s.channelName.ifBlank { s.workspaceName }
                    Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = Color.White)
                    if (s.channelAvatarUri != null) {
                        AsyncImage(
                            model = Uri.parse(s.channelAvatarUri), contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.channelName.ifBlank { s.workspaceName },
                        style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (last != null) tr("Данные вручную · ", "Manual data · ") + Fmt.date(LocalDate.ofEpochDay(last.dateEpochDay).atStartOfDay(zone).toInstant().toEpochMilli(), zone, lang)
                        else tr("Канал не подключён · ручной режим", "Channel not connected · manual mode"),
                        style = MaterialTheme.typography.bodySmall, color = LX.Muted,
                    )
                }
                IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) { Icon(Icons.Outlined.Settings, tr("Настройки", "Settings")) }
            }
        }

        // ----- Показатели -----
        if (last == null) {
            item {
                LxCard(onClick = { nav.navigate(Routes.snapshot(0)) }) {
                    Text(tr("Добавьте показатели канала", "Add your channel stats"), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        tr(
                            "Пока YouTube API не подключён, введите подписчиков, просмотры и число видео из YouTube Studio. Регулярные записи построят график роста.",
                            "Until the YouTube API is connected, enter subscribers, views and video count from YouTube Studio. Regular entries build the growth chart.",
                        ),
                        style = MaterialTheme.typography.bodyMedium, color = LX.Muted,
                    )
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = { nav.navigate(Routes.snapshot(0)) }) { Text(tr("Ввести данные", "Enter data")) }
                }
            }
        } else {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        tr("Подписчики", "Subscribers"), Fmt.number(last.subscribers), Modifier.weight(1f),
                        sub = subs28?.let { "${Fmt.signed(it.value)} " + tr("за ${it.days} дн", "in ${it.days}d") },
                        subColor = if ((subs28?.value ?: 0) >= 0) LX.Ok else LX.Accent, icon = Icons.Outlined.Group,
                    )
                    StatTile(tr("Просмотры", "Views"), Fmt.number(last.totalViews), Modifier.weight(1f), icon = Icons.Outlined.Visibility)
                    StatTile(tr("Видео", "Videos"), Fmt.number(last.videoCount), Modifier.weight(0.8f), icon = Icons.Outlined.VideoLibrary)
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        tr("Просмотры · 7 дн", "Views · 7d"), views7?.let { Fmt.signed(it.value) } ?: "—", Modifier.weight(1f),
                        sub = when {
                            views7 == null -> tr("нужна ещё запись", "need another entry")
                            views7.days != 7L -> tr("фактически ${views7.days} дн", "actual ${views7.days}d")
                            else -> null
                        },
                    )
                    StatTile(
                        tr("Просмотры · 28 дн", "Views · 28d"), views28?.let { Fmt.signed(it.value) } ?: "—", Modifier.weight(1f),
                        sub = when {
                            views28 == null -> tr("нужна ещё запись", "need another entry")
                            views28.days != 28L -> tr("фактически ${views28.days} дн", "actual ${views28.days}d")
                            else -> null
                        },
                    )
                }
            }
        }

        // ----- Цель -----
        item {
            val subs = last?.subscribers ?: 0L
            val goal = s.subscriberGoal.coerceAtLeast(1)
            LxCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Цель по подписчикам", "Subscriber goal"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text("${Fmt.number(subs)} / ${Fmt.number(goal)}", color = LX.Muted, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
                GoalBar(subs.toFloat() / goal)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (subs >= goal) tr("Цель достигнута! Поставьте новую в настройках.", "Goal reached! Set a new one in Settings.")
                    else tr("Осталось ${Fmt.number(goal - subs)} · ${(subs * 100 / goal)}%", "${Fmt.number(goal - subs)} to go · ${(subs * 100 / goal)}%"),
                    style = MaterialTheme.typography.bodySmall, color = if (subs >= goal) LX.Ok else LX.Muted,
                )
            }
        }

        // ----- Быстрые действия -----
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(Icons.Outlined.Lightbulb, tr("Идея", "Idea"), Modifier.weight(1f)) { nav.navigate(Routes.idea(0)) }
                QuickAction(Icons.Outlined.Add, tr("Запланировать", "Schedule"), Modifier.weight(1f)) { nav.navigate(Routes.pub(0)) }
                QuickAction(Icons.Outlined.BarChart, tr("Статистика", "Stats"), Modifier.weight(1f)) { nav.switchTab(Routes.ANALYTICS) }
            }
        }

        // ----- Важное -----
        if (overdue > 0 || todayPubs > 0 || tasksDue > 0 || (last != null && last.subscribers >= s.subscriberGoal)) {
            item { SectionTitle(tr("Важное", "Heads up")) }
            if (overdue > 0) item {
                EventRow(Icons.Outlined.WarningAmber, LX.Accent, tr("Просрочено публикаций: $overdue", "Overdue uploads: $overdue")) { nav.switchTab(Routes.CALENDAR) }
            }
            if (todayPubs > 0) item {
                EventRow(Icons.Outlined.NotificationsActive, LX.Cyan, tr("Публикаций сегодня: $todayPubs", "Uploads today: $todayPubs")) { nav.switchTab(Routes.CALENDAR) }
            }
            if (tasksDue > 0) item {
                EventRow(Icons.Outlined.CheckCircle, LX.Warn, tr("Задач на сегодня и просроченных: $tasksDue", "Tasks due today or overdue: $tasksDue")) { nav.navigate(Routes.TASKS) }
            }
            if (last != null && last.subscribers >= s.subscriberGoal) item {
                EventRow(Icons.Outlined.CheckCircle, LX.Ok, tr("Цель ${Fmt.number(s.subscriberGoal)} подписчиков выполнена", "Goal of ${Fmt.number(s.subscriberGoal)} subscribers reached")) { nav.navigate(Routes.SETTINGS) }
            }
        }

        // ----- Публикации -----
        item { SectionTitle(tr("Публикации", "Uploads"), action = tr("Календарь", "Calendar"), onAction = { nav.switchTab(Routes.CALENDAR) }) }
        item {
            LxCard(onClick = { if (next != null) nav.navigate(Routes.pub(next.id)) else nav.navigate(Routes.pub(0)) }) {
                Text(tr("Следующая", "Next up"), style = MaterialTheme.typography.labelMedium, color = LX.Muted)
                Spacer(Modifier.height(4.dp))
                if (next == null) {
                    Text(tr("Ничего не запланировано — нажмите, чтобы добавить", "Nothing scheduled — tap to add"), color = LX.Muted)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(next.type.label(lang), next.type.color)
                        Spacer(Modifier.width(8.dp))
                        Text(Fmt.dateTime(next.scheduledAt, zone, lang), style = MaterialTheme.typography.bodySmall, color = LX.Muted)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(next.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    gameName(next.gameId)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LX.Muted) }
                }
            }
        }
        item {
            LxCard(onClick = lastPublished?.let { p -> { nav.navigate(Routes.pub(p.id)) } }) {
                Text(tr("Последняя опубликованная", "Last published"), style = MaterialTheme.typography.labelMedium, color = LX.Muted)
                Spacer(Modifier.height(4.dp))
                if (lastPublished == null) {
                    Text(tr("Отметьте публикацию как «Опубликовано» в календаре", "Mark an upload as “Published” in the calendar"), color = LX.Muted)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.PlayCircle, null, tint = lastPublished.type.color)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(lastPublished.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                Fmt.date(lastPublished.scheduledAt, zone, lang) +
                                    (lastPublished.views?.let { " · ${Fmt.number(it)} " + tr("просм.", "views") } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = LX.Muted,
                            )
                        }
                    }
                }
            }
        }

        // ----- График -----
        item { SectionTitle(tr("Рост подписчиков", "Subscriber growth")) }
        item {
            LxCard {
                val pts = snaps.takeLast(60)
                if (pts.size < 2) {
                    Text(
                        tr("Нужно минимум две записи показателей, чтобы построить график.", "At least two stat entries are needed to draw the chart."),
                        color = LX.Muted, style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LineChart(pts.map { it.subscribers.toFloat() })
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Text(Fmt.shortDayEpoch(pts.first().dateEpochDay, lang), style = MaterialTheme.typography.labelSmall, color = LX.Muted, modifier = Modifier.weight(1f))
                        Text(Fmt.shortDayEpoch(pts.last().dateEpochDay, lang), style = MaterialTheme.typography.labelSmall, color = LX.Muted)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

fun Fmt.shortDayEpoch(epochDay: Long, lang: String) = shortDay(LocalDate.ofEpochDay(epochDay), lang)

@Composable
private fun QuickAction(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    LxCard(modifier = modifier, onClick = onClick, padding = PaddingValues(vertical = 14.dp, horizontal = 8.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EventRow(icon: ImageVector, color: Color, text: String, onClick: () -> Unit) {
    LxCard(onClick = onClick, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
