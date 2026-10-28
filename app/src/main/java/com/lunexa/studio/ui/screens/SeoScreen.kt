package com.lunexa.studio.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lunexa.studio.data.DEFAULT_CHECKLIST
import com.lunexa.studio.data.Game
import com.lunexa.studio.data.SeoItem
import com.lunexa.studio.data.SeoKind
import com.lunexa.studio.ui.ChipsRow
import com.lunexa.studio.ui.ConfirmDialog
import com.lunexa.studio.ui.DropdownField
import com.lunexa.studio.ui.EmptyState
import com.lunexa.studio.ui.LX
import com.lunexa.studio.ui.LocalLang
import com.lunexa.studio.ui.LocalRepo
import com.lunexa.studio.ui.LocalSettings
import com.lunexa.studio.ui.LxCard
import com.lunexa.studio.ui.LxScaffold
import com.lunexa.studio.ui.LxTopBar
import com.lunexa.studio.ui.Pill
import com.lunexa.studio.ui.tr

private fun SeoKind.label(lang: String) = when (this) {
    SeoKind.TITLE -> if (lang == "en") "Titles" else "Названия"
    SeoKind.DESCRIPTION -> if (lang == "en") "Descriptions" else "Описания"
    SeoKind.HASHTAGS -> if (lang == "en") "Hashtags" else "Хештеги"
    SeoKind.KEYWORDS -> if (lang == "en") "Keywords" else "Ключевые слова"
}

/** Примеры шаблонов: {game} и {part} заменяются вручную при копировании. */
private fun example(kind: SeoKind): SeoItem = when (kind) {
    SeoKind.TITLE -> SeoItem(kind = kind, title = "Gameplay series", text = "{game} - Part {part} | Gameplay | No Commentary")
    SeoKind.DESCRIPTION -> SeoItem(
        kind = kind, title = "Long gameplay",
        text = "{game} — Part {part}. Full gameplay walkthrough with no commentary.\n\n" +
            "🎮 Game: {game}\n📺 Previous part: \n🔔 Subscribe for the next parts!\n\n#{game} #gameplay #nocommentary",
    )
    SeoKind.HASHTAGS -> SeoItem(kind = kind, title = "Shorts", text = "#shorts #gaming #gameplay #{game}")
    SeoKind.KEYWORDS -> SeoItem(kind = kind, title = "Base", text = "gameplay, walkthrough, no commentary, {game}, {game} gameplay, {game} part {part}")
}

@Composable
fun SeoScreen(nav: NavHostController) {
    val repo = LocalRepo.current
    val lang = LocalLang.current
    val items by repo.seo.collectAsStateWithLifecycle(emptyList())
    val games by repo.games.collectAsStateWithLifecycle(emptyList())
    var tab by rememberSaveable { mutableStateOf(0) }
    var gameFilter by rememberSaveable { mutableStateOf(-1L) }
    var editing by remember { mutableStateOf<SeoItem?>(null) }
    var deleting by remember { mutableStateOf<SeoItem?>(null) }
    val kinds = SeoKind.entries
    val isChecklist = tab == kinds.size

    LxScaffold(
        topBar = { LxTopBar(tr("SEO и тексты", "SEO & copy"), onBack = { nav.popBackStack() }) },
        fab = {
            if (!isChecklist) FloatingActionButton(
                onClick = { editing = SeoItem(kind = kinds[tab], text = "", gameId = gameFilter.takeIf { it > 0 }) },
                containerColor = MaterialTheme.colorScheme.primary,
            ) { Icon(Icons.Outlined.Add, tr("Добавить", "Add")) }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            ScrollableTabRow(selectedTabIndex = tab, containerColor = LX.Bg, contentColor = LX.Text, edgePadding = 12.dp) {
                kinds.forEachIndexed { i, k -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(k.label(lang)) }) }
                Tab(selected = isChecklist, onClick = { tab = kinds.size }, text = { Text(tr("Чек-лист", "Checklist")) })
            }
            if (isChecklist) {
                ChecklistTab()
            } else {
                val kind = kinds[tab]
                val shown = items.filter { it.kind == kind && (gameFilter < 0 || it.gameId == gameFilter || (gameFilter == 0L && it.gameId == null)) }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (games.isNotEmpty()) item {
                        ChipsRow(listOf(-1L, 0L) + games.map { it.id }, gameFilter, { id ->
                            when (id) { -1L -> trS3(lang, "Все", "All"); 0L -> trS3(lang, "Общие", "General"); else -> games.firstOrNull { it.id == id }?.title ?: "?" }
                        }, { gameFilter = it })
                    }
                    if (shown.isEmpty()) item {
                        EmptyState(
                            Icons.Outlined.Tag, tr("Здесь пока пусто", "Nothing here yet"),
                            tr("Храните готовые тексты и копируйте их одним нажатием. {game} и {part} — места для названия игры и номера части.",
                                "Keep ready-made texts and copy them in one tap. {game} and {part} are placeholders for the game name and part number."),
                            action = tr("Добавить пример", "Add an example"), onAction = { repo.launch { saveSeo(example(kind)) } },
                        )
                    }
                    items(shown, key = { it.id }) { item -> SeoCard(item, games, onEdit = { editing = item }, onDelete = { deleting = item }) }
                }
            }
        }
    }

    editing?.let { e -> SeoDialog(e, games, onDismiss = { editing = null }) { repo.launch { saveSeo(it) } } }
    deleting?.let { d ->
        ConfirmDialog(tr("Удалить?", "Delete?"), d.title.ifBlank { d.text.take(60) }, tr("Удалить", "Delete"),
            onConfirm = { repo.launch { deleteSeo(d) } }, onDismiss = { deleting = null })
    }
}

private fun trS3(lang: String, ru: String, en: String) = if (lang == "en") en else ru

@Composable
private fun SeoCard(item: SeoItem, games: List<Game>, onEdit: () -> Unit, onDelete: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    val lang = LocalLang.current
    LxCard(onClick = onEdit, padding = PaddingValues(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(item.lang.uppercase(), LX.Cyan)
                    games.firstOrNull { it.id == item.gameId }?.let { Spacer(Modifier.width(6.dp)); Pill(it.title, LX.Warn) }
                    if (item.title.isNotBlank()) { Spacer(Modifier.width(8.dp)); Text(item.title, style = MaterialTheme.typography.labelLarge) }
                }
                Spacer(Modifier.height(6.dp))
                Text(item.text, style = MaterialTheme.typography.bodyMedium, maxLines = 8)
            }
            Column {
                IconButton(onClick = {
                    clipboard.setText(AnnotatedString(item.text))
                    Toast.makeText(ctx, if (lang == "en") "Copied" else "Скопировано", Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Outlined.ContentCopy, tr("Копировать", "Copy"), tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = onDelete) { Icon(Icons.Outlined.Close, tr("Удалить", "Delete"), tint = LX.Muted) }
            }
        }
    }
}

@Composable
private fun SeoDialog(initial: SeoItem, games: List<Game>, onDismiss: () -> Unit, onSave: (SeoItem) -> Unit) {
    val lang = LocalLang.current
    var title by remember { mutableStateOf(initial.title) }
    var text by remember { mutableStateOf(initial.text) }
    var gid by remember { mutableStateOf(initial.gameId ?: 0L) }
    var l by remember { mutableStateOf(initial.lang) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LX.Card,
        title = { Text(initial.kind.label(lang)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it.take(60) }, label = { Text(tr("Короткое имя (необязательно)", "Short name (optional)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(text, { text = it.take(5000) }, label = { Text(tr("Текст", "Text")) }, minLines = 4, modifier = Modifier.fillMaxWidth())
                ChipsRow(listOf("en", "ru"), l, { if (it == "en") "English" else "Русский" }, { l = it })
                DropdownField(tr("Игра", "Game"), listOf(0L) + games.map { it.id }, gid,
                    { id -> if (id == 0L) trS3(lang, "Общий шаблон", "General template") else games.firstOrNull { it.id == id }?.title ?: "?" }, { gid = it })
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                onSave(initial.copy(title = title.trim(), text = text.trim(), gameId = gid.takeIf { it != 0L }, lang = l)); onDismiss()
            }) { Text(tr("Сохранить", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена", "Cancel")) } },
    )
}

@Composable
private fun ChecklistTab() {
    val repo = LocalRepo.current
    val s = LocalSettings.current
    var newItem by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Text(tr("Отмечайте пункты перед каждой публикацией.", "Tick items before each upload."), style = MaterialTheme.typography.bodySmall, color = LX.Muted)
        }
        val list = s.checklist
        items(list.size) { i ->
            val done = i in s.checklistDone
            LxCard(padding = PaddingValues(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(done, { c -> repo.launch { updateSettings { it.copy(checklistDone = if (c) it.checklistDone + i else it.checklistDone - i) } } })
                    Text(list[i], modifier = Modifier.weight(1f), textDecoration = if (done) TextDecoration.LineThrough else null,
                        color = if (done) LX.Muted else LX.Text)
                    IconButton(onClick = {
                        repo.launch {
                            updateSettings { st ->
                                st.copy(checklist = st.checklist.filterIndexed { j, _ -> j != i },
                                    checklistDone = st.checklistDone.filter { it != i }.map { if (it > i) it - 1 else it })
                            }
                        }
                    }) { Icon(Icons.Outlined.Close, null, tint = LX.Muted) }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newItem, { newItem = it.take(100) }, singleLine = true, label = { Text(tr("Новый пункт", "New item")) }, modifier = Modifier.weight(1f))
                TextButton(enabled = newItem.isNotBlank(), onClick = {
                    val t = newItem.trim(); newItem = ""
                    repo.launch { updateSettings { it.copy(checklist = it.checklist + t) } }
                }) { Text(tr("Добавить", "Add")) }
            }
        }
        item {
            Row {
                TextButton(onClick = { repo.launch { updateSettings { it.copy(checklistDone = emptyList()) } } }) { Text(tr("Снять все отметки", "Uncheck all")) }
                TextButton(onClick = { repo.launch { updateSettings { it.copy(checklist = DEFAULT_CHECKLIST, checklistDone = emptyList()) } } }) {
                    Text(tr("Вернуть стандартный", "Reset to default"))
                }
            }
        }
    }
}
