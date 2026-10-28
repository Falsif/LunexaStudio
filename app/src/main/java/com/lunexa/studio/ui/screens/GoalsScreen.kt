package com.lunexa.studio.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lunexa.studio.data.ChannelNumbers
import com.lunexa.studio.data.Fmt
import com.lunexa.studio.data.Goal
import com.lunexa.studio.data.GoalType
import com.lunexa.studio.data.Goals
import com.lunexa.studio.data.goalTitle
import com.lunexa.studio.ui.ConfirmDialog
import com.lunexa.studio.ui.DropdownField
import com.lunexa.studio.ui.EmptyState
import com.lunexa.studio.ui.GoalBar
import com.lunexa.studio.ui.LX
import com.lunexa.studio.ui.LocalLang
import com.lunexa.studio.ui.LocalRepo
import com.lunexa.studio.ui.LocalZone
import com.lunexa.studio.ui.LxCard
import com.lunexa.studio.ui.LxScaffold
import com.lunexa.studio.ui.LxTopBar
import com.lunexa.studio.ui.NumberField
import com.lunexa.studio.ui.SectionTitle
import com.lunexa.studio.ui.tr

@Composable
fun rememberChannelNumbers(): ChannelNumbers {
    val repo = LocalRepo.current
    val zone = LocalZone.current
    val snaps by repo.snapshots.collectAsStateWithLifecycle(emptyList())
    val pubs by repo.publications.collectAsStateWithLifecycle(emptyList())
    val yt by repo.youtube.collectAsStateWithLifecycle()
    return remember(snaps, pubs, yt, zone) { Goals.numbers(snaps, yt, pubs, zone) }
}

private fun GoalType.icon(): ImageVector = when (this) {
    GoalType.SUBSCRIBERS -> Icons.Outlined.Group
    GoalType.VIEWS -> Icons.Outlined.Visibility
    GoalType.VIDEOS -> Icons.Outlined.VideoLibrary
    GoalType.STREAK -> Icons.Outlined.CalendarMonth
    GoalType.CUSTOM -> Icons.Outlined.Flag
}

private fun GoalType.label(lang: String) = when (this) {
    GoalType.SUBSCRIBERS -> if (lang == "en") "Subscribers" else "Подписчики"
    GoalType.VIEWS -> if (lang == "en") "Views" else "Просмотры"
    GoalType.VIDEOS -> if (lang == "en") "Videos" else "Видео"
    GoalType.STREAK -> if (lang == "en") "Days in a row with uploads" else "Дней подряд с публикацией"
    GoalType.CUSTOM -> if (lang == "en") "Custom goal" else "Своя цель"
}

@Composable
fun GoalsScreen(nav: NavHostController) {
    val repo = LocalRepo.current
    val lang = LocalLang.current
    val zone = LocalZone.current
    val goals by repo.goals.collectAsStateWithLifecycle(emptyList())
    val n = rememberChannelNumbers()
    var editing by remember { mutableStateOf<Goal?>(null) }
    var deleting by remember { mutableStateOf<Goal?>(null) }

    LaunchedEffect(n, goals.size) { repo.checkGoals(n) }

    val active = goals.filter { it.achievedAt == null }
    val done = goals.filter { it.achievedAt != null }.sortedByDescending { it.achievedAt }

    LxScaffold(
        topBar = { LxTopBar(tr("Цели и достижения", "Goals & achievements"), onBack = { nav.popBackStack() }) },
        fab = {
            FloatingActionButton(onClick = { editing = Goal(type = GoalType.CUSTOM, target = 0) }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Outlined.Add, tr("Новая цель", "New goal"))
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
                    Text(tr("Сейчас", "Now"), style = MaterialTheme.typography.labelMedium, color = LX.Muted)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOf(
                            tr("подписчики", "subscribers") + " " + (n.subs?.let { Fmt.number(it) } ?: "—"),
                            tr("просмотры", "views") + " " + (n.views?.let { Fmt.number(it) } ?: "—"),
                            tr("серия", "streak") + " ${n.streak} " + tr("дн.", "d"),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (goals.isEmpty()) item {
                EmptyState(
                    Icons.Outlined.EmojiEvents, tr("Целей пока нет", "No goals yet"),
                    tr("Добавьте стандартный набор: 100, 500, 1 000 и 10 000 подписчиков, 100 тыс. и 1 млн просмотров, 30 дней публикаций подряд.",
                        "Add the standard set: 100, 500, 1,000 and 10,000 subscribers, 100K and 1M views, 30 days of uploads in a row."),
                    action = tr("Добавить стандартные цели", "Add standard goals"), onAction = { repo.launch { addPresetGoals() } },
                )
            } else if (Goals.presets.any { p -> goals.none { it.type == p.first && it.target == p.second } }) item {
                TextButton(onClick = { repo.launch { addPresetGoals() } }) { Text(tr("Добавить недостающие стандартные цели", "Add missing standard goals")) }
            }

            if (active.isNotEmpty()) item { SectionTitle(tr("В процессе", "In progress")) }
            items(active, key = { it.id }) { g -> GoalCard(g, n, lang, onEdit = { editing = g }, onDelete = { deleting = g }) }

            if (done.isNotEmpty()) {
                item { SectionTitle(tr("Достижения", "Achievements") + " · ${done.size}") }
                items(done, key = { "a${it.id}" }) { g ->
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), LX.Card))),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFF5C542).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Outlined.EmojiEvents, null, tint = Color(0xFFF5C542), modifier = Modifier.size(28.dp)) }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(goalTitle(g, lang), style = MaterialTheme.typography.titleSmall)
                                Text(tr("Выполнено ", "Achieved ") + Fmt.date(g.achievedAt ?: 0, zone, lang), style = MaterialTheme.typography.bodySmall, color = LX.Muted)
                            }
                            IconButton(onClick = { deleting = g }) { Icon(Icons.Outlined.MoreVert, null, tint = LX.Muted) }
                        }
                    }
                }
            }
        }
    }

    editing?.let { g -> GoalDialog(g, onDismiss = { editing = null }) { repo.launch { saveGoal(it) } } }
    deleting?.let { g ->
        ConfirmDialog(
            tr("Удалить цель?", "Delete goal?"), goalTitle(g, lang), tr("Удалить", "Delete"),
            onConfirm = { repo.launch { deleteGoal(g) } }, onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun GoalCard(g: Goal, n: ChannelNumbers, lang: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    val cur = Goals.current(g, n)
    val f = Goals.fraction(g, n)
    var menu by remember { mutableStateOf(false) }
    LxCard(padding = PaddingValues(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(g.type.icon(), null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(goalTitle(g, lang), style = MaterialTheme.typography.titleSmall)
                Text(
                    (cur?.let { Fmt.number(it) } ?: "—") + " / " + Fmt.number(g.target) + " · ${(f * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall, color = LX.Muted,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, null, tint = LX.Muted) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text(tr("Изменить", "Edit")) }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text(tr("Удалить", "Delete"), color = LX.Accent) }, onClick = { menu = false; onDelete() })
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.padding(end = 12.dp)) { GoalBar(f) }
        if (cur == null) {
            Spacer(Modifier.height(6.dp))
            Text(tr("Нет данных: подключите канал или внесите показатели.", "No data: connect the channel or enter stats."),
                style = MaterialTheme.typography.labelSmall, color = LX.Muted)
        }
    }
}

@Composable
private fun GoalDialog(initial: Goal, onDismiss: () -> Unit, onSave: (Goal) -> Unit) {
    val lang = LocalLang.current
    var type by remember { mutableStateOf(initial.type) }
    var title by remember { mutableStateOf(initial.title) }
    var target by remember { mutableStateOf(if (initial.target > 0) initial.target.toString() else "") }
    var progress by remember { mutableStateOf(initial.manualProgress.toString()) }
    val t = target.toLongOrNull()
    val valid = t != null && t > 0 && (type != GoalType.CUSTOM || title.isNotBlank())
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LX.Card,
        title = { Text(if (initial.id == 0L) tr("Новая цель", "New goal") else tr("Цель", "Goal")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DropdownField(tr("Что считаем", "What to track"), GoalType.entries, type, { it.label(lang) }, { type = it })
                if (type == GoalType.CUSTOM) {
                    OutlinedTextField(title, { title = it.take(80) }, label = { Text(tr("Название цели", "Goal name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                NumberField(tr("Целевое значение", "Target"), target, { target = it }, isError = t == null || t <= 0)
                if (type == GoalType.CUSTOM) NumberField(tr("Текущий прогресс", "Current progress"), progress, { progress = it })
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(initial.copy(type = type, title = title.trim(), target = t ?: 0, manualProgress = progress.toLongOrNull() ?: 0,
                    achievedAt = initial.achievedAt?.takeIf { (progress.toLongOrNull() ?: 0) >= (t ?: 0) || type != GoalType.CUSTOM }))
                onDismiss()
            }) { Text(tr("Сохранить", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена", "Cancel")) } },
    )
}
