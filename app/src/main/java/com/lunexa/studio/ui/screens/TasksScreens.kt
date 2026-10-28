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
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lunexa.studio.data.Fmt
import com.lunexa.studio.data.Priority
import com.lunexa.studio.data.Recurrence
import com.lunexa.studio.data.TaskItem
import com.lunexa.studio.data.millisAt
import com.lunexa.studio.data.toZdt
import com.lunexa.studio.ui.ChipsRow
import com.lunexa.studio.ui.ConfirmDialog
import com.lunexa.studio.ui.DateField
import com.lunexa.studio.ui.Dot
import com.lunexa.studio.ui.DropdownField
import com.lunexa.studio.ui.EmptyState
import com.lunexa.studio.ui.LX
import com.lunexa.studio.ui.LocalLang
import com.lunexa.studio.ui.LocalRepo
import com.lunexa.studio.ui.LocalZone
import com.lunexa.studio.ui.LxCard
import com.lunexa.studio.ui.LxScaffold
import com.lunexa.studio.ui.LxTopBar
import com.lunexa.studio.ui.Routes
import com.lunexa.studio.ui.TimeField
import com.lunexa.studio.ui.color
import com.lunexa.studio.ui.label
import com.lunexa.studio.ui.tr
import kotlinx.coroutines.launch
import java.time.LocalDate

private val quickTasksRu = listOf("Записать игровой процесс", "Подготовить название", "Написать описание", "Проверить публикацию", "Загрузить Shorts", "Загрузить TikTok", "Проверить аналитику")
private val quickTasksEn = listOf("Record gameplay", "Prepare title", "Write description", "Check the upload", "Upload Shorts", "Upload TikTok", "Check analytics")

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TasksScreen(nav: NavHostController) {
    val repo = LocalRepo.current
    val lang = LocalLang.current
    val zone = LocalZone.current
    val tasks by repo.tasks.collectAsStateWithLifecycle(emptyList())
    var tab by rememberSaveable { mutableStateOf(0) }
    val now = System.currentTimeMillis()

    val active = tasks.filter { !it.done }.sortedWith(
        compareBy<TaskItem> { it.dueAt ?: Long.MAX_VALUE }.thenByDescending { it.priority.ordinal },
    )
    val history = tasks.filter { it.done }.sortedByDescending { it.doneAt ?: 0L }

    LxScaffold(
        topBar = { LxTopBar(tr("Задачи", "Tasks"), onBack = { nav.popBackStack() }) },
        fab = {
            FloatingActionButton(onClick = { nav.navigate(Routes.task(0)) }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Outlined.Add, tr("Новая задача", "New task"))
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(
                selectedTabIndex = tab, containerColor = LX.Bg, contentColor = LX.Text,
                indicator = { pos ->
                    TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), color = MaterialTheme.colorScheme.primary)
                },
            ) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(tr("Активные", "Active") + " · ${active.size}") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(tr("История", "History") + " · ${history.size}") })
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (tab == 0) {
                    item {
                        Text(tr("Быстро добавить на сегодня", "Quick add for today"), style = MaterialTheme.typography.labelLarge, color = LX.Muted)
                        Spacer(Modifier.height(6.dp))
                        val list = if (lang == "en") quickTasksEn else quickTasksRu
                        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            list.forEach { t ->
                                AssistChip(onClick = {
                                    val due = LocalDate.now(zone).millisAt(20, 0, zone)
                                    repo.launch { saveTask(TaskItem(title = t, dueAt = due)) }
                                }, label = { Text(t) })
                            }
                        }
                    }
                    if (active.isEmpty()) item {
                        EmptyState(Icons.Outlined.TaskAlt, tr("Все задачи выполнены", "All done"), tr("Добавьте задачу кнопкой «+»", "Add a task with “+”"))
                    }
                    items(active, key = { it.id }) { t -> TaskRow(t, now, nav) }
                } else {
                    if (history.isEmpty()) item {
                        EmptyState(Icons.Outlined.TaskAlt, tr("История пуста", "No history yet"), tr("Здесь появятся выполненные задачи", "Completed tasks will appear here"))
                    }
                    items(history, key = { it.id }) { t -> TaskRow(t, now, nav) }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(t: TaskItem, now: Long, nav: NavHostController) {
    val repo = LocalRepo.current
    val zone = LocalZone.current
    val lang = LocalLang.current
    val overdue = !t.done && t.dueAt != null && t.dueAt < now
    LxCard(onClick = { nav.navigate(Routes.task(t.id)) }, padding = PaddingValues(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = t.done, onCheckedChange = { repo.launch { setTaskDone(t, it) } },
                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    t.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    color = if (t.done) LX.Muted else LX.Text,
                )
                val sub = when {
                    t.done && t.doneAt != null -> tr("Выполнено ", "Done ") + Fmt.dateTime(t.doneAt, zone, lang)
                    t.dueAt != null -> (if (overdue) tr("Просрочено · ", "Overdue · ") else "") + Fmt.dateTime(t.dueAt, zone, lang)
                    else -> null
                }
                if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = if (overdue) LX.Accent else LX.Muted)
            }
            if (t.recurrence != Recurrence.NONE) {
                Icon(Icons.Outlined.Repeat, t.recurrence.label(lang), tint = LX.Muted, modifier = Modifier.padding(end = 6.dp))
            }
            Dot(t.priority.color, 10)
        }
    }
}

@Composable
fun TaskEditScreen(nav: NavHostController, id: Long) {
    val repo = LocalRepo.current
    val lang = LocalLang.current
    val zone = LocalZone.current
    val scope = rememberCoroutineScope()

    var loaded by rememberSaveable { mutableStateOf(false) }
    var original by remember { mutableStateOf<TaskItem?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var priorityName by rememberSaveable { mutableStateOf(Priority.MEDIUM.name) }
    var dueDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var hour by rememberSaveable { mutableStateOf(20) }
    var minute by rememberSaveable { mutableStateOf(0) }
    var recurrenceName by rememberSaveable { mutableStateOf(Recurrence.NONE.name) }
    var remind by rememberSaveable { mutableStateOf(true) }
    var titleError by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != 0L) {
            val t = repo.task(id)
            original = t
            if (t != null && !loaded) {
                title = t.title; notes = t.notes; priorityName = t.priority.name
                t.dueAt?.toZdt(zone)?.let { z -> dueDay = z.toLocalDate().toEpochDay(); hour = z.hour; minute = z.minute }
                recurrenceName = t.recurrence.name; remind = t.remind
            }
        }
        loaded = true
    }

    LxScaffold(
        topBar = {
            LxTopBar(if (id == 0L) tr("Новая задача", "New task") else tr("Задача", "Task"), onBack = { nav.popBackStack() }, actions = {
                if (id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, tr("Удалить", "Delete")) }
            })
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                title, { title = it.take(150); titleError = false }, label = { Text(tr("Задача *", "Task *")) },
                isError = titleError, modifier = Modifier.fillMaxWidth(),
                supportingText = if (titleError) { { Text(tr("Введите текст задачи", "Enter the task")) } } else null,
            )
            Text(tr("Приоритет", "Priority"), style = MaterialTheme.typography.labelLarge, color = LX.Muted)
            ChipsRow(Priority.entries, Priority.valueOf(priorityName), { it.label(lang) }, { priorityName = it.name })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField(tr("Срок", "Due"), dueDay?.let { LocalDate.ofEpochDay(it) }, { dueDay = it?.toEpochDay() }, Modifier.weight(1.3f), clearable = true)
                if (dueDay != null) TimeField(tr("Время", "Time"), hour, minute, { h, m -> hour = h; minute = m }, Modifier.weight(1f))
            }
            DropdownField(tr("Повтор", "Repeat"), Recurrence.entries, Recurrence.valueOf(recurrenceName), { it.label(lang) }, { recurrenceName = it.name })
            if (recurrenceName != Recurrence.NONE.name && dueDay == null) {
                Text(tr("Для повтора укажите срок — следующая задача создаётся от него.", "Set a due date — the next task is created from it."),
                    style = MaterialTheme.typography.bodySmall, color = LX.Warn)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Напомнить в срок", "Remind when due"))
                    Text(tr("Нужен срок и включённые уведомления", "Requires a due date and notifications on"), style = MaterialTheme.typography.bodySmall, color = LX.Muted)
                }
                Switch(checked = remind, onCheckedChange = { remind = it })
            }
            OutlinedTextField(notes, { notes = it.take(3000) }, label = { Text(tr("Заметки", "Notes")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.width(1.dp))
            Button(
                onClick = {
                    if (title.isBlank()) { titleError = true; return@Button }
                    val t = (original ?: TaskItem(title = "")).copy(
                        title = title.trim(), notes = notes.trim(), priority = Priority.valueOf(priorityName),
                        dueAt = dueDay?.let { LocalDate.ofEpochDay(it).millisAt(hour, minute, zone) },
                        recurrence = Recurrence.valueOf(recurrenceName), remind = remind,
                    )
                    repo.launch { saveTask(t) }
                    nav.popBackStack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(tr("Сохранить", "Save")) }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            tr("Удалить задачу?", "Delete task?"), tr("Действие нельзя отменить.", "This can't be undone."), tr("Удалить", "Delete"),
            onConfirm = { original?.let { t -> scope.launch { repo.deleteTask(t); nav.popBackStack() } } },
            onDismiss = { confirmDelete = false },
        )
    }
}
