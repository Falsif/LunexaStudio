package com.lunexa.studio.ui.screens

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lunexa.studio.data.ContentType
import com.lunexa.studio.data.PubStatus
import com.lunexa.studio.data.Publication
import com.lunexa.studio.data.Recurrence
import com.lunexa.studio.data.millisAt
import com.lunexa.studio.data.parseHm
import com.lunexa.studio.data.toZdt
import com.lunexa.studio.ui.ChipsRow
import com.lunexa.studio.ui.ConfirmDialog
import com.lunexa.studio.ui.DateField
import com.lunexa.studio.ui.DropdownField
import com.lunexa.studio.ui.LX
import com.lunexa.studio.ui.LocalLang
import com.lunexa.studio.ui.LocalRepo
import com.lunexa.studio.ui.LocalSettings
import com.lunexa.studio.ui.LocalZone
import com.lunexa.studio.ui.LxScaffold
import com.lunexa.studio.ui.LxTopBar
import com.lunexa.studio.ui.NumberField
import com.lunexa.studio.ui.ReminderOptions
import com.lunexa.studio.ui.TimeField
import com.lunexa.studio.ui.label
import com.lunexa.studio.ui.reminderLabel
import com.lunexa.studio.ui.tr
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun PublicationEditScreen(nav: NavHostController, id: Long, epochDay: Long, ideaId: Long, gameId: Long) {
    val repo = LocalRepo.current
    val s = LocalSettings.current
    val zone = LocalZone.current
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    val games by repo.games.collectAsStateWithLifecycle(emptyList())

    var loaded by rememberSaveable { mutableStateOf(false) }
    var original by remember { mutableStateOf<Publication?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var typeName by rememberSaveable { mutableStateOf(ContentType.SHORTS.name) }
    var gid by rememberSaveable { mutableStateOf(gameId) }
    var day by rememberSaveable {
        mutableStateOf(if (epochDay != Long.MIN_VALUE) epochDay else LocalDate.now(zone).toEpochDay())
    }
    val (dh, dm) = parseHm(s.shortsTime, 13, 25)
    var hour by rememberSaveable { mutableStateOf(dh) }
    var minute by rememberSaveable { mutableStateOf(dm) }
    var statusName by rememberSaveable { mutableStateOf(PubStatus.PLANNED.name) }
    var remind by rememberSaveable { mutableStateOf(s.defaultReminderMin) }
    var repeatName by rememberSaveable { mutableStateOf(Recurrence.NONE.name) }
    var repeatCount by rememberSaveable { mutableStateOf("7") }
    var views by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var linkedIdea by rememberSaveable { mutableStateOf(ideaId) }
    var videoId by rememberSaveable { mutableStateOf("") }
    var linkInput by rememberSaveable { mutableStateOf("") }
    var linkError by remember { mutableStateOf(false) }
    val yt by repo.youtube.collectAsStateWithLifecycle()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmSeries by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf(false) }

    LaunchedEffect(id, ideaId) {
        if (id != 0L) {
            val p = repo.publication(id)
            original = p
            if (p != null && !loaded) {
                val z = p.scheduledAt.toZdt(zone)
                title = p.title; typeName = p.type.name; gid = p.gameId ?: 0L
                day = z.toLocalDate().toEpochDay(); hour = z.hour; minute = z.minute
                statusName = p.status.name; remind = p.remindBeforeMin
                views = p.views?.toString() ?: ""; notes = p.notes; linkedIdea = p.ideaId ?: 0L
                videoId = p.videoId ?: ""
            }
        } else if (ideaId != 0L && !loaded) {
            repo.idea(ideaId)?.let { idea ->
                title = idea.title; typeName = idea.type.name; gid = idea.gameId ?: 0L
                idea.plannedDate?.let { day = it.toZdt(zone).toLocalDate().toEpochDay() }
                notes = idea.description
                val defTime = when (idea.type) { ContentType.SHORTS -> s.shortsTime; ContentType.LONG -> s.longTime; ContentType.TIKTOK -> s.tiktokTime }
                val (h, m) = parseHm(defTime)
                hour = h; minute = m
            }
        }
        loaded = true
    }

    val type = ContentType.valueOf(typeName)
    val isNew = id == 0L

    fun save() {
        if (title.isBlank()) { titleError = true; return }
        val count = repeatCount.toIntOrNull() ?: 1
        val at = LocalDate.ofEpochDay(day).millisAt(hour, minute, zone)
        val base = (original ?: Publication(title = "", scheduledAt = at)).copy(
            title = title.trim(), type = type, gameId = gid.takeIf { it != 0L }, scheduledAt = at,
            status = PubStatus.valueOf(statusName), remindBeforeMin = remind,
            views = views.toLongOrNull(), notes = notes.trim(), ideaId = linkedIdea.takeIf { it != 0L },
            videoId = videoId.takeIf { it.isNotBlank() },
        )
        val rep = if (isNew) Recurrence.valueOf(repeatName) else Recurrence.NONE
        val ideaRef = linkedIdea
        val plannedStart = LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
        repo.launch {
            savePublication(base, rep, count.coerceIn(1, 365))
            if (ideaRef != 0L) idea(ideaRef)?.let { saveIdea(it.copy(status = base.status, plannedDate = plannedStart)) }
        }
        nav.popBackStack()
    }

    LxScaffold(
        topBar = {
            LxTopBar(if (isNew) tr("Новая публикация", "New upload") else tr("Публикация", "Upload"), onBack = { nav.popBackStack() }, actions = {
                if (!isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, tr("Удалить", "Delete")) }
            })
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                title, { title = it.take(150); titleError = false },
                label = { Text(tr("Название ролика *", "Video title *")) },
                isError = titleError,
                supportingText = if (titleError) { { Text(tr("Введите название", "Enter a title")) } } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            ChipsRow(ContentType.entries, type, { it.label(lang) }, { typeName = it.name })
            DropdownField(
                tr("Игра", "Game"), listOf(0L) + games.map { it.id }, gid,
                optionLabel = { gidOpt -> if (gidOpt == 0L) tr2p(lang, "Без игры", "No game") else games.firstOrNull { it.id == gidOpt }?.title ?: "—" },
                onSelect = { gid = it },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField(tr("Дата", "Date"), LocalDate.ofEpochDay(day), { if (it != null) day = it.toEpochDay() }, Modifier.weight(1.3f))
                TimeField(tr("Время", "Time"), hour, minute, { h, m -> hour = h; minute = m }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1L to tr("+1 день", "+1 day"), 7L to tr("+1 неделя", "+1 week")).forEach { (d, t) ->
                    OutlinedButton(onClick = { day += d }) { Text(t) }
                }
            }
            Text(tr("Часовой пояс: ", "Time zone: ") + s.timezone, style = MaterialTheme.typography.bodySmall, color = LX.Muted)
            DropdownField(tr("Статус", "Status"), PubStatus.entries, PubStatus.valueOf(statusName), { it.label(lang) }, { statusName = it.name })
            DropdownField(tr("Напоминание", "Reminder"), ReminderOptions, remind, { reminderLabel(it, lang) }, { remind = it })
            if (isNew) {
                DropdownField(tr("Повтор", "Repeat"), Recurrence.entries, Recurrence.valueOf(repeatName), { it.label(lang) }, { repeatName = it.name })
                if (repeatName != Recurrence.NONE.name) {
                    val c = repeatCount.toIntOrNull()
                    NumberField(tr("Количество публикаций в серии", "Number of uploads in series"), repeatCount, { repeatCount = it },
                        isError = c == null || c !in 1..365)
                }
            }
            // ----- Связь с роликом на канале -----
            Text(tr("Ролик на канале", "Video on channel"), style = MaterialTheme.typography.labelLarge, color = LX.Muted)
            val ytVideos = yt?.videos.orEmpty()
            fun linkTo(id: String) {
                videoId = id
                if (id.isNotBlank()) {
                    statusName = PubStatus.PUBLISHED.name
                    ytVideos.firstOrNull { it.id == id }?.let { v -> views = v.views.toString() }
                }
            }
            if (ytVideos.isNotEmpty()) {
                val ids = listOf("") + ytVideos.map { it.id } + listOfNotNull(videoId.takeIf { it.isNotBlank() && ytVideos.none { v -> v.id == it } })
                DropdownField(
                    tr("Выбрать из роликов канала", "Pick from channel videos"), ids, videoId,
                    optionLabel = { id -> if (id.isEmpty()) tr2p(lang, "Не связано", "Not linked") else ytVideos.firstOrNull { it.id == id }?.title ?: id },
                    onSelect = { linkTo(it) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    linkInput, { linkInput = it.take(200); linkError = false }, singleLine = true, isError = linkError,
                    label = { Text(tr("или ссылка на ролик", "or video link")) }, modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = {
                    val id = com.lunexa.studio.ui.Studio.parseVideoId(linkInput)
                    if (id == null) linkError = true else { linkTo(id); linkInput = "" }
                }) { Text(tr("Связать", "Link")) }
            }
            if (linkError) Text(tr("Не похоже на ссылку YouTube", "Doesn't look like a YouTube link"), color = LX.Accent, style = MaterialTheme.typography.bodySmall)
            if (videoId.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { com.lunexa.studio.ui.Studio.editVideo(ctx, videoId) }, modifier = Modifier.weight(1f)) { Text(tr("Изменить", "Edit"), maxLines = 1) }
                    OutlinedButton(onClick = { com.lunexa.studio.ui.Studio.videoComments(ctx, videoId) }, modifier = Modifier.weight(1f)) { Text(tr("Коммент.", "Comments"), maxLines = 1) }
                    OutlinedButton(onClick = { com.lunexa.studio.ui.Studio.videoAnalytics(ctx, videoId) }, modifier = Modifier.weight(1f)) { Text(tr("Стат.", "Stats"), maxLines = 1) }
                }
                TextButton(onClick = { videoId = "" }) { Text(tr("Отвязать ролик", "Unlink video")) }
            }
            NumberField(
                if (videoId.isNotBlank() && yt != null) tr("Просмотры (обновляются с канала)", "Views (synced from channel)") else tr("Просмотры (вручную)", "Views (manual)"),
                views, { views = it },
            )
            OutlinedTextField(notes, { notes = it.take(2000) }, label = { Text(tr("Заметки", "Notes")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            if (original?.seriesId != null) {
                TextButton(onClick = { confirmSeries = true }) { Text(tr("Удалить всю серию", "Delete whole series"), color = LX.Accent) }
            }
            Spacer(Modifier.height(4.dp))
            val repeatInvalid = isNew && repeatName != Recurrence.NONE.name && (repeatCount.toIntOrNull() ?: 0) !in 1..365
            Button(onClick = { save() }, enabled = !repeatInvalid, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(tr("Сохранить", "Save"))
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            tr("Удалить публикацию?", "Delete upload?"), tr("Действие нельзя отменить.", "This can't be undone."), tr("Удалить", "Delete"),
            onConfirm = {
                original?.let { p -> scope.launch { repo.deletePublication(p); nav.popBackStack() } }
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmSeries) {
        ConfirmDialog(
            tr("Удалить серию?", "Delete series?"),
            tr("Будут удалены все публикации, созданные этим повтором или шаблоном.", "All uploads created by this repeat or template will be deleted."),
            tr("Удалить", "Delete"),
            onConfirm = {
                original?.seriesId?.let { sid -> scope.launch { repo.deleteSeries(sid); nav.popBackStack() } }
            },
            onDismiss = { confirmSeries = false },
        )
    }
}

internal fun tr2p(lang: String, ru: String, en: String) = if (lang == "en") en else ru
