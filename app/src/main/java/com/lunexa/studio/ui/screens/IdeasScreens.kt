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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lunexa.studio.data.ContentType
import com.lunexa.studio.data.Fmt
import com.lunexa.studio.data.Idea
import com.lunexa.studio.data.Priority
import com.lunexa.studio.data.PubStatus
import com.lunexa.studio.data.startMillis
import com.lunexa.studio.data.toLocalDate
import com.lunexa.studio.ui.ChipsRow
import com.lunexa.studio.ui.ConfirmDialog
import com.lunexa.studio.ui.DateField
import com.lunexa.studio.ui.DropdownField
import com.lunexa.studio.ui.EmptyState
import com.lunexa.studio.ui.LX
import com.lunexa.studio.ui.LocalLang
import com.lunexa.studio.ui.LocalRepo
import com.lunexa.studio.ui.LocalZone
import com.lunexa.studio.ui.LxCard
import com.lunexa.studio.ui.LxScaffold
import com.lunexa.studio.ui.LxTopBar
import com.lunexa.studio.ui.Pill
import com.lunexa.studio.ui.Routes
import com.lunexa.studio.ui.color
import com.lunexa.studio.ui.label
import com.lunexa.studio.ui.tr
import kotlinx.coroutines.launch

private enum class IdeaSort { NEWEST, PRIORITY, PLANNED, TITLE }

@Composable
fun IdeasScreen(nav: NavHostController) {
    val repo = LocalRepo.current
    val lang = LocalLang.current
    val zone = LocalZone.current
    val ideas by repo.ideas.collectAsStateWithLifecycle(emptyList())
    val games by repo.games.collectAsStateWithLifecycle(emptyList())

    var query by rememberSaveable { mutableStateOf("") }
    var typeFilter by rememberSaveable { mutableStateOf("ALL") }
    var statusFilter by rememberSaveable { mutableStateOf("ALL") }
    var favOnly by rememberSaveable { mutableStateOf(false) }
    var sortName by rememberSaveable { mutableStateOf(IdeaSort.NEWEST.name) }

    val shown = remember(ideas, games, query, typeFilter, statusFilter, favOnly, sortName) {
        val q = query.trim().lowercase()
        ideas.asSequence()
            .filter { typeFilter == "ALL" || it.type.name == typeFilter }
            .filter { statusFilter == "ALL" || it.status.name == statusFilter }
            .filter { !favOnly || it.favorite }
            .filter {
                q.isEmpty() || it.title.lowercase().contains(q) || it.tags.lowercase().contains(q) ||
                    it.description.lowercase().contains(q) ||
                    (games.firstOrNull { g -> g.id == it.gameId }?.title?.lowercase()?.contains(q) == true)
            }
            .let { seq ->
                when (IdeaSort.valueOf(sortName)) {
                    IdeaSort.NEWEST -> seq.sortedByDescending { it.createdAt }
                    IdeaSort.PRIORITY -> seq.sortedWith(compareByDescending<Idea> { it.priority.ordinal }.thenByDescending { it.createdAt })
                    IdeaSort.PLANNED -> seq.sortedWith(compareBy<Idea> { it.plannedDate ?: Long.MAX_VALUE }.thenByDescending { it.createdAt })
                    IdeaSort.TITLE -> seq.sortedBy { it.title.lowercase() }
                }
            }
            .toList()
    }

    LxScaffold(
        topBar = { LxTopBar(tr("Идеи", "Ideas") + if (ideas.isNotEmpty()) " · ${ideas.size}" else "") },
        fab = {
            FloatingActionButton(onClick = { nav.navigate(Routes.idea(0)) }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Outlined.Add, tr("Новая идея", "New idea"))
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    query, { query = it }, singleLine = true,
                    placeholder = { Text(tr("Поиск по названию, тегам, игре", "Search title, tags, game")) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, null) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                ChipsRow(
                    listOf("ALL") + ContentType.entries.map { it.name }, typeFilter,
                    { if (it == "ALL") tr2p(lang, "Все типы", "All types") else ContentType.valueOf(it).label(lang) },
                    { typeFilter = it },
                )
            }
            item {
                FilterChip(
                    selected = favOnly, onClick = { favOnly = !favOnly },
                    label = { Text(tr("Только избранное", "Favorites only")) },
                    leadingIcon = { Icon(if (favOnly) Icons.Filled.Star else Icons.Outlined.StarBorder, null) },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    DropdownField(
                        tr("Статус", "Status"), listOf("ALL") + PubStatus.entries.map { it.name }, statusFilter,
                        { if (it == "ALL") tr2p(lang, "Все", "All") else PubStatus.valueOf(it).label(lang) },
                        { statusFilter = it }, Modifier.weight(1f),
                    )
                    DropdownField(
                        tr("Сортировка", "Sort"), IdeaSort.entries, IdeaSort.valueOf(sortName),
                        {
                            when (it) {
                                IdeaSort.NEWEST -> tr2p(lang, "Новые", "Newest")
                                IdeaSort.PRIORITY -> tr2p(lang, "Приоритет", "Priority")
                                IdeaSort.PLANNED -> tr2p(lang, "По дате", "By date")
                                IdeaSort.TITLE -> tr2p(lang, "А–Я", "A–Z")
                            }
                        },
                        { sortName = it.name }, Modifier.weight(1f),
                    )
                }
            }
            if (shown.isEmpty()) {
                item {
                    if (ideas.isEmpty()) EmptyState(Icons.Outlined.Lightbulb, tr("Идей пока нет", "No ideas yet"),
                        tr("Записывайте всё, что хочется снять — потом превратите идею в публикацию.", "Capture anything you'd like to record — turn it into an upload later."),
                        action = tr("Добавить идею", "Add idea"), onAction = { nav.navigate(Routes.idea(0)) })
                    else EmptyState(Icons.Outlined.Search, tr("Ничего не найдено", "Nothing found"), tr("Измените поиск или фильтры", "Change search or filters"))
                }
            }
            items(shown, key = { it.id }) { idea ->
                LxCard(onClick = { nav.navigate(Routes.idea(idea.id)) }, padding = PaddingValues(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Pill(idea.type.label(lang), idea.type.color)
                                Pill(idea.priority.label(lang), idea.priority.color)
                                Pill(idea.status.label(lang), idea.status.color)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(idea.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val meta = listOfNotNull(
                                games.firstOrNull { it.id == idea.gameId }?.title,
                                idea.plannedDate?.let { Fmt.shortDay(it.toLocalDate(zone), lang) },
                                idea.durationSec?.let { Fmt.duration(it) },
                            ).joinToString(" · ")
                            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = LX.Muted)
                            if (idea.tags.isNotBlank()) Text(
                                idea.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ") { "#$it" },
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { repo.launch { saveIdea(idea.copy(favorite = !idea.favorite)) } }) {
                            Icon(
                                if (idea.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                tr("Избранное", "Favorite"), tint = if (idea.favorite) LX.Warn else LX.Muted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IdeaEditScreen(nav: NavHostController, id: Long, presetGame: Long) {
    val repo = LocalRepo.current
    val lang = LocalLang.current
    val zone = LocalZone.current
    val scope = rememberCoroutineScope()
    val games by repo.games.collectAsStateWithLifecycle(emptyList())

    var loaded by rememberSaveable { mutableStateOf(false) }
    var original by remember { mutableStateOf<Idea?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var gid by rememberSaveable { mutableStateOf(presetGame) }
    var typeName by rememberSaveable { mutableStateOf(ContentType.SHORTS.name) }
    var description by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    var priorityName by rememberSaveable { mutableStateOf(Priority.MEDIUM.name) }
    var duration by rememberSaveable { mutableStateOf("") }
    var plannedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var statusName by rememberSaveable { mutableStateOf(PubStatus.IDEA.name) }
    var notes by rememberSaveable { mutableStateOf("") }
    var favorite by rememberSaveable { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != 0L) {
            val i = repo.idea(id)
            original = i
            if (i != null && !loaded) {
                title = i.title; gid = i.gameId ?: 0L; typeName = i.type.name; description = i.description
                tags = i.tags; priorityName = i.priority.name; duration = Fmt.duration(i.durationSec)
                plannedDay = i.plannedDate?.toLocalDate(zone)?.toEpochDay(); statusName = i.status.name
                notes = i.notes; favorite = i.favorite
            }
        }
        loaded = true
    }

    val durationSec: Int? = parseDuration(duration)
    val durationError = duration.isNotBlank() && durationSec == null

    fun build(): Idea? {
        if (title.isBlank()) { titleError = true; return null }
        if (durationError) return null
        return (original ?: Idea(title = "")).copy(
            title = title.trim(), gameId = gid.takeIf { it != 0L }, type = ContentType.valueOf(typeName),
            description = description.trim(), tags = tags.trim(), priority = Priority.valueOf(priorityName),
            durationSec = durationSec,
            plannedDate = plannedDay?.let { java.time.LocalDate.ofEpochDay(it).startMillis(zone) },
            status = PubStatus.valueOf(statusName), notes = notes.trim(), favorite = favorite,
        )
    }

    LxScaffold(
        topBar = {
            LxTopBar(if (id == 0L) tr("Новая идея", "New idea") else tr("Идея", "Idea"), onBack = { nav.popBackStack() }, actions = {
                IconButton(onClick = { favorite = !favorite }) {
                    Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder, tr("Избранное", "Favorite"), tint = if (favorite) LX.Warn else LX.Muted)
                }
                if (id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, tr("Удалить", "Delete")) }
            })
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                title, { title = it.take(150); titleError = false }, label = { Text(tr("Название *", "Title *")) },
                isError = titleError, supportingText = if (titleError) { { Text(tr("Введите название", "Enter a title")) } } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            ChipsRow(ContentType.entries, ContentType.valueOf(typeName), { it.label(lang) }, { typeName = it.name })
            DropdownField(
                tr("Игра", "Game"), listOf(0L) + games.map { it.id }, gid,
                { g -> if (g == 0L) tr2p(lang, "Без игры", "No game") else games.firstOrNull { it.id == g }?.title ?: "—" },
                { gid = it },
            )
            OutlinedTextField(description, { description = it.take(3000) }, label = { Text(tr("Описание", "Description")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                tags, { tags = it.take(300) }, label = { Text(tr("Теги через запятую", "Tags, comma-separated")) },
                placeholder = { Text("minecraft, survival, shorts") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Text(tr("Приоритет", "Priority"), style = MaterialTheme.typography.labelLarge, color = LX.Muted)
            ChipsRow(Priority.entries, Priority.valueOf(priorityName), { it.label(lang) }, { priorityName = it.name })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    duration, { duration = it.filter { c -> c.isDigit() || c == ':' }.take(6) },
                    label = { Text(tr("Длительность м:сс", "Length m:ss")) }, placeholder = { Text("0:45") },
                    isError = durationError, singleLine = true, modifier = Modifier.weight(1f),
                )
                DateField(tr("План. дата", "Planned date"), plannedDay?.let { java.time.LocalDate.ofEpochDay(it) },
                    { plannedDay = it?.toEpochDay() }, Modifier.weight(1.2f), clearable = true)
            }
            DropdownField(tr("Статус", "Status"), PubStatus.entries, PubStatus.valueOf(statusName), { it.label(lang) }, { statusName = it.name })
            OutlinedTextField(notes, { notes = it.take(3000) }, label = { Text(tr("Личные заметки", "Personal notes")) }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { build()?.let { i -> repo.launch { saveIdea(i) }; nav.popBackStack() } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(tr("Сохранить", "Save")) }
            OutlinedButton(
                onClick = {
                    build()?.let { i ->
                        scope.launch {
                            val newId = repo.saveIdea(i)
                            nav.popBackStack()
                            nav.navigate(Routes.pub(0, ideaId = newId))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(tr("Превратить в публикацию", "Turn into an upload")) }
            Spacer(Modifier.width(1.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            tr("Удалить идею?", "Delete idea?"), tr("Связанные публикации останутся в календаре.", "Linked uploads stay in the calendar."), tr("Удалить", "Delete"),
            onConfirm = { original?.let { i -> scope.launch { repo.deleteIdea(i); nav.popBackStack() } } },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** «45», «1:30», «12:05» → секунды. Пустая строка — null без ошибки (проверяется отдельно). */
fun parseDuration(s: String): Int? {
    val t = s.trim()
    if (t.isEmpty()) return null
    val parts = t.split(':')
    return when (parts.size) {
        1 -> parts[0].toIntOrNull()?.takeIf { it in 0..86_400 }
        2 -> {
            val m = parts[0].toIntOrNull() ?: return null
            val sec = parts[1].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
            (m * 60 + sec).takeIf { m in 0..1440 }
        }
        else -> null
    }
}
