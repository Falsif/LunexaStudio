package com.lunexa.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lunexa.studio.data.ContentType
import com.lunexa.studio.data.Fmt
import com.lunexa.studio.data.Game
import com.lunexa.studio.data.PubStatus
import com.lunexa.studio.data.Publication
import com.lunexa.studio.data.Recurrence
import com.lunexa.studio.data.parseHm
import com.lunexa.studio.data.toLocalDate
import com.lunexa.studio.data.toZdt
import com.lunexa.studio.ui.ChipsRow
import com.lunexa.studio.ui.DateField
import com.lunexa.studio.ui.Dot
import com.lunexa.studio.ui.DropdownField
import com.lunexa.studio.ui.EmptyState
import com.lunexa.studio.ui.LX
import com.lunexa.studio.ui.LocalLang
import com.lunexa.studio.ui.LocalRepo
import com.lunexa.studio.ui.LocalSettings
import com.lunexa.studio.ui.LocalZone
import com.lunexa.studio.ui.LxCard
import com.lunexa.studio.ui.LxScaffold
import com.lunexa.studio.ui.LxTopBar
import com.lunexa.studio.ui.NumberField
import com.lunexa.studio.ui.Pill
import com.lunexa.studio.ui.Routes
import com.lunexa.studio.ui.SectionTitle
import com.lunexa.studio.ui.TimeField
import com.lunexa.studio.ui.color
import com.lunexa.studio.ui.label
import com.lunexa.studio.ui.tr
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

private enum class CalMode { MONTH, WEEK, DAY, LIST }

private val ACTIVE = setOf(PubStatus.IDEA, PubStatus.PLANNED, PubStatus.READY)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(nav: NavHostController) {
    val repo = LocalRepo.current
    val zone = LocalZone.current
    val lang = LocalLang.current
    val allPubs by repo.publications.collectAsStateWithLifecycle(emptyList())
    val games by repo.games.collectAsStateWithLifecycle(emptyList())

    var modeName by rememberSaveable { mutableStateOf(CalMode.MONTH.name) }
    val mode = CalMode.valueOf(modeName)
    var selectedDay by rememberSaveable { mutableStateOf(LocalDate.now(zone).toEpochDay()) }
    val selected = LocalDate.ofEpochDay(selectedDay)
    var filterName by rememberSaveable { mutableStateOf("ALL") }
    var showTemplates by remember { mutableStateOf(false) }

    val pubs = remember(allPubs, filterName) {
        if (filterName == "ALL") allPubs else allPubs.filter { it.type.name == filterName }
    }
    val byDay = remember(pubs, zone) { pubs.groupBy { it.scheduledAt.toLocalDate(zone) } }
    val now = System.currentTimeMillis()

    LxScaffold(
        topBar = {
            LxTopBar(tr("Календарь", "Calendar"), actions = {
                IconButton(onClick = { selectedDay = LocalDate.now(zone).toEpochDay() }) { Icon(Icons.Outlined.Today, tr("Сегодня", "Today")) }
                IconButton(onClick = { showTemplates = true }) { Icon(Icons.Outlined.AutoMode, tr("Шаблоны расписаний", "Schedule templates")) }
            })
        },
        fab = {
            FloatingActionButton(
                onClick = { nav.navigate(Routes.pub(0, epochDay = selectedDay)) },
                containerColor = MaterialTheme.colorScheme.primary,
            ) { Icon(Icons.Outlined.Add, tr("Запланировать", "Schedule")) }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val modes = listOf(
                        CalMode.MONTH to tr("Месяц", "Month"), CalMode.WEEK to tr("Неделя", "Week"),
                        CalMode.DAY to tr("День", "Day"), CalMode.LIST to tr("Список", "List"),
                    )
                    modes.forEachIndexed { i, (m, title) ->
                        SegmentedButton(
                            selected = mode == m,
                            onClick = { modeName = m.name },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                inactiveContainerColor = LX.Card,
                            ),
                            label = { Text(title, maxLines = 1) },
                        )
                    }
                }
            }
            item {
                ChipsRow(
                    options = listOf("ALL") + ContentType.entries.map { it.name },
                    selected = filterName,
                    label = { if (it == "ALL") tr2(lang, "Все", "All") else ContentType.valueOf(it).label(lang) },
                    onSelect = { filterName = it },
                )
            }

            if (allPubs.isEmpty()) {
                item {
                    LxCard {
                        Text(tr("Начните с расписания", "Start with a schedule"), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            tr("Шаблон создаст серию Shorts ежедневно в выбранное время (по умолчанию 13:25). Потом каждую публикацию можно переименовать или перенести.",
                                "A template creates a daily Shorts series at your chosen time (13:25 by default). Rename or move any upload afterwards."),
                            color = LX.Muted, style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { showTemplates = true }) { Text(tr("Открыть шаблоны", "Open templates")) }
                    }
                }
            }

            when (mode) {
                CalMode.MONTH -> {
                    item {
                        PeriodHeader(Fmt.monthYear(selected, lang),
                            onPrev = { selectedDay = selected.minusMonths(1).withDayOfMonth(1).toEpochDay() },
                            onNext = { selectedDay = selected.plusMonths(1).withDayOfMonth(1).toEpochDay() })
                    }
                    item { MonthGrid(selected, byDay, zone, lang) { selectedDay = it.toEpochDay() } }
                    dayList(Fmt.day(selected, lang), byDay[selected].orEmpty(), games, now, nav, emptyText = true)
                }
                CalMode.WEEK -> {
                    val start = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    item {
                        PeriodHeader("${Fmt.shortDay(start, lang)} — ${Fmt.shortDay(start.plusDays(6), lang)}",
                            onPrev = { selectedDay = selected.minusWeeks(1).toEpochDay() },
                            onNext = { selectedDay = selected.plusWeeks(1).toEpochDay() })
                    }
                    for (i in 0..6) {
                        val d = start.plusDays(i.toLong())
                        dayList(Fmt.day(d, lang), byDay[d].orEmpty(), games, now, nav, emptyText = false,
                            highlight = d == LocalDate.now(zone))
                    }
                }
                CalMode.DAY -> {
                    item {
                        PeriodHeader(Fmt.day(selected, lang),
                            onPrev = { selectedDay = selected.minusDays(1).toEpochDay() },
                            onNext = { selectedDay = selected.plusDays(1).toEpochDay() })
                    }
                    dayList(null, byDay[selected].orEmpty(), games, now, nav, emptyText = true)
                }
                CalMode.LIST -> {
                    val overdue = pubs.filter { it.scheduledAt < now && it.status in ACTIVE }
                    val upcoming = pubs.filter { it.scheduledAt >= now && it.status in ACTIVE }
                    val published = pubs.filter { it.status == PubStatus.PUBLISHED }.sortedByDescending { it.scheduledAt }.take(30)
                    val cancelled = pubs.filter { it.status == PubStatus.CANCELLED }.sortedByDescending { it.scheduledAt }.take(15)
                    if (overdue.isNotEmpty()) {
                        item { SectionTitle(tr("Просрочено", "Overdue") + " · ${overdue.size}") }
                        items(overdue, key = { "o${it.id}" }) { PubRow(it, games, now, nav, showDate = true) }
                    }
                    item { SectionTitle(tr("Предстоящие", "Upcoming") + " · ${upcoming.size}") }
                    if (upcoming.isEmpty()) item { Text(tr("Нет запланированных публикаций", "No upcoming uploads"), color = LX.Muted) }
                    items(upcoming, key = { "u${it.id}" }) { PubRow(it, games, now, nav, showDate = true) }
                    if (published.isNotEmpty()) {
                        item { SectionTitle(tr("Опубликовано", "Published")) }
                        items(published, key = { "p${it.id}" }) { PubRow(it, games, now, nav, showDate = true) }
                    }
                    if (cancelled.isNotEmpty()) {
                        item { SectionTitle(tr("Отменено", "Cancelled")) }
                        items(cancelled, key = { "c${it.id}" }) { PubRow(it, games, now, nav, showDate = true) }
                    }
                }
            }
        }
    }

    if (showTemplates) TemplatesDialog(selected, games, onDismiss = { showTemplates = false })
}

private fun LazyListScope.dayList(
    title: String?, list: List<Publication>, games: List<Game>, now: Long, nav: NavHostController,
    emptyText: Boolean, highlight: Boolean = false,
) {
    if (title != null) item {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (highlight) { Dot(LX.Accent); Spacer(Modifier.width(8.dp)) }
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (highlight) LX.Text else LX.Muted)
        }
    }
    if (list.isEmpty()) {
        if (emptyText) item {
            EmptyState(Icons.Outlined.CalendarMonth, tr("Свободный день", "Free day"), tr("Нажмите «+», чтобы запланировать ролик", "Tap “+” to schedule a video"))
        }
    } else {
        items(list.sortedBy { it.scheduledAt }, key = { "d${it.id}" }) { PubRow(it, games, now, nav, showDate = false) }
    }
}

@Composable
private fun PeriodHeader(title: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, tr("Назад", "Previous")) }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, tr("Вперёд", "Next")) }
    }
}

@Composable
private fun MonthGrid(selected: LocalDate, byDay: Map<LocalDate, List<Publication>>, zone: ZoneId, lang: String, onPick: (LocalDate) -> Unit) {
    val first = selected.withDayOfMonth(1)
    val offset = first.dayOfWeek.value - 1
    val days = selected.lengthOfMonth()
    val rows = (offset + days + 6) / 7
    val today = LocalDate.now(zone)
    val locale = com.lunexa.studio.data.localeOf(lang)
    LxCard(padding = PaddingValues(10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach {
                Text(
                    it.getDisplayName(TextStyle.SHORT, locale), modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = LX.Muted,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0..6) {
                    val dayNum = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..days) {
                            val date = first.withDayOfMonth(dayNum)
                            val isSel = date == selected
                            val isToday = date == today
                            val list = byDay[date].orEmpty()
                            Column(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent)
                                    .then(if (isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else Modifier)
                                    .clickable { onPick(date) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text("$dayNum", style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSel || isToday) FontWeight.Bold else FontWeight.Normal)
                                Spacer(Modifier.height(3.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    list.take(3).forEach { p ->
                                        Dot(if (p.status == PubStatus.CANCELLED) LX.Muted else p.type.color, 5)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PubRow(p: Publication, games: List<Game>, now: Long, nav: NavHostController, showDate: Boolean) {
    val repo = LocalRepo.current
    val zone = LocalZone.current
    val lang = LocalLang.current
    val overdue = p.scheduledAt < now && p.status in ACTIVE
    var menu by remember { mutableStateOf(false) }
    LxCard(onClick = { nav.navigate(Routes.pub(p.id)) }, padding = PaddingValues(start = 0.dp, end = 4.dp, top = 0.dp, bottom = 0.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).height(64.dp).background(p.type.color))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (showDate) Fmt.dateTime(p.scheduledAt, zone, lang) else Fmt.time(p.scheduledAt, zone),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (overdue) LX.Accent else LX.Muted,
                    )
                    Spacer(Modifier.width(8.dp))
                    Pill(p.type.label(lang), p.type.color)
                    if (overdue) { Spacer(Modifier.width(6.dp)); Pill(tr("Просрочено", "Overdue"), LX.Accent) }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    p.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (p.status == PubStatus.CANCELLED) TextDecoration.LineThrough else null,
                )
                val game = games.firstOrNull { it.id == p.gameId }?.title
                Text(
                    listOfNotNull(game, p.status.label(lang)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = p.status.color, maxLines = 1,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, tr("Действия", "Actions"), tint = LX.Muted) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    PubStatus.entries.filter { it != p.status }.forEach { st ->
                        DropdownMenuItem(text = { Text(st.label(lang)) }, onClick = {
                            menu = false
                            repo.launch { savePublication(p.copy(status = st)) }
                        })
                    }
                    DropdownMenuItem(text = { Text(tr("Перенести на +1 день", "Move +1 day")) }, onClick = {
                        menu = false
                        repo.launch { savePublication(p.copy(scheduledAt = p.scheduledAt.toZdt(zone).plusDays(1).toInstant().toEpochMilli())) }
                    })
                    DropdownMenuItem(text = { Text(tr("Удалить", "Delete"), color = LX.Accent) }, onClick = {
                        menu = false
                        repo.launch { deletePublication(p) }
                    })
                }
            }
        }
    }
}

private enum class Tpl { SHORTS_DAILY, LONG_WEEKLY, TIKTOK_DAILY }

@Composable
private fun TemplatesDialog(start: LocalDate, games: List<Game>, onDismiss: () -> Unit) {
    val repo = LocalRepo.current
    val s = LocalSettings.current
    val lang = LocalLang.current
    var tpl by remember { mutableStateOf(Tpl.SHORTS_DAILY) }
    var from by remember { mutableStateOf(start) }
    val (sh, sm) = parseHm(s.shortsTime, 13, 25)
    val (lh, lm) = parseHm(s.longTime, 18, 0)
    val (th, tm) = parseHm(s.tiktokTime, 19, 0)
    var hour by remember { mutableStateOf(sh) }
    var minute by remember { mutableStateOf(sm) }
    var count by remember { mutableStateOf("14") }
    var title by remember { mutableStateOf("Shorts") }
    var game by remember { mutableStateOf<Game?>(null) }
    var weekday by remember { mutableStateOf(DayOfWeek.SATURDAY) }

    fun applyDefaults(t: Tpl) {
        tpl = t
        when (t) {
            Tpl.SHORTS_DAILY -> { hour = sh; minute = sm; title = "Shorts"; count = "14" }
            Tpl.LONG_WEEKLY -> { hour = lh; minute = lm; title = trL2(lang, "Длинное видео", "Long video"); count = "4" }
            Tpl.TIKTOK_DAILY -> { hour = th; minute = tm; title = "TikTok"; count = "7" }
        }
    }

    val n = count.toIntOrNull() ?: 0
    val valid = n in 1..90 && title.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LX.Card,
        title = { Text(tr("Шаблон расписания", "Schedule template")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DropdownField(
                    tr("Шаблон", "Template"), Tpl.entries, tpl,
                    optionLabel = {
                        when (it) {
                            Tpl.SHORTS_DAILY -> tr2(lang, "Shorts — ежедневно", "Shorts — daily")
                            Tpl.LONG_WEEKLY -> tr2(lang, "Длинное видео — еженедельно", "Long video — weekly")
                            Tpl.TIKTOK_DAILY -> tr2(lang, "TikTok — ежедневно", "TikTok — daily")
                        }
                    },
                    onSelect = { applyDefaults(it) },
                )
                OutlinedTextField(title, { title = it.take(100) }, label = { Text(tr("Название публикаций", "Upload title")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                DropdownField(
                    tr("Игра", "Game"), listOf<Game?>(null) + games, game,
                    optionLabel = { it?.title ?: tr2(lang, "Без игры", "No game") }, onSelect = { game = it },
                )
                DateField(tr("Начиная с", "Starting"), from, { if (it != null) from = it })
                if (tpl == Tpl.LONG_WEEKLY) {
                    DropdownField(
                        tr("День недели", "Weekday"), DayOfWeek.entries, weekday,
                        optionLabel = { it.getDisplayName(TextStyle.FULL_STANDALONE, com.lunexa.studio.data.localeOf(lang)) },
                        onSelect = { weekday = it },
                    )
                }
                TimeField(tr("Время", "Time"), hour, minute, { h, m -> hour = h; minute = m })
                NumberField(
                    if (tpl == Tpl.LONG_WEEKLY) tr("Сколько недель (1–90)", "How many weeks (1–90)") else tr("Сколько дней (1–90)", "How many days (1–90)"),
                    count, { count = it }, isError = !valid,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val type = when (tpl) { Tpl.SHORTS_DAILY -> ContentType.SHORTS; Tpl.LONG_WEEKLY -> ContentType.LONG; Tpl.TIKTOK_DAILY -> ContentType.TIKTOK }
                val rec = if (tpl == Tpl.LONG_WEEKLY) Recurrence.WEEKLY else Recurrence.DAILY
                val first = if (tpl == Tpl.LONG_WEEKLY) from.with(TemporalAdjusters.nextOrSame(weekday)) else from
                val g = game
                repo.launch { generateSeries(type, title.trim(), g?.id, first, hour, minute, rec, n) }
                onDismiss()
            }) { Text(tr("Создать", "Create")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена", "Cancel")) } },
    )
}

private fun tr2(lang: String, ru: String, en: String) = if (lang == "en") en else ru
private fun trL2(lang: String, ru: String, en: String) = tr2(lang, ru, en)
