package com.lunexa.studio.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.lunexa.studio.data.Fmt
import com.lunexa.studio.data.Game
import com.lunexa.studio.data.GameStatus
import com.lunexa.studio.data.Platform
import com.lunexa.studio.data.PubStatus
import com.lunexa.studio.data.Publication
import com.lunexa.studio.ui.ChipsRow
import com.lunexa.studio.ui.ConfirmDialog
import com.lunexa.studio.ui.EmptyState
import com.lunexa.studio.ui.LX
import com.lunexa.studio.ui.LocalLang
import com.lunexa.studio.ui.LocalRepo
import com.lunexa.studio.ui.LocalZone
import com.lunexa.studio.ui.LxCard
import com.lunexa.studio.ui.LxScaffold
import com.lunexa.studio.ui.LxTopBar
import com.lunexa.studio.ui.NumberField
import com.lunexa.studio.ui.Pill
import com.lunexa.studio.ui.Routes
import com.lunexa.studio.ui.SectionTitle
import com.lunexa.studio.ui.StatTile
import com.lunexa.studio.ui.color
import com.lunexa.studio.ui.label
import com.lunexa.studio.ui.tr
import kotlinx.coroutines.launch

data class GameStats(val published: Int, val planned: Int, val views: Long)

fun gameStats(gameId: Long, pubs: List<Publication>): GameStats {
    val mine = pubs.filter { it.gameId == gameId }
    return GameStats(
        published = mine.count { it.status == PubStatus.PUBLISHED },
        planned = mine.count { it.status == PubStatus.PLANNED || it.status == PubStatus.READY },
        views = mine.sumOf { it.views ?: 0L },
    )
}

@Composable
fun GameCover(uri: String?, title: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(LX.Card2), contentAlignment = Alignment.Center) {
        Text(title.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = LX.Muted)
        if (uri != null) AsyncImage(Uri.parse(uri), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun GamesScreen(nav: NavHostController) {
    val repo = LocalRepo.current
    val lang = LocalLang.current
    val games by repo.games.collectAsStateWithLifecycle(emptyList())
    val pubs by repo.publications.collectAsStateWithLifecycle(emptyList())
    var filter by rememberSaveable { mutableStateOf("ALL") }
    val shown = games.filter { filter == "ALL" || it.status.name == filter }

    LxScaffold(
        topBar = { LxTopBar(tr("Библиотека игр", "Game library"), onBack = { nav.popBackStack() }) },
        fab = {
            FloatingActionButton(onClick = { nav.navigate(Routes.game(0)) }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Outlined.Add, tr("Добавить игру", "Add game"))
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ChipsRow(listOf("ALL") + GameStatus.entries.map { it.name }, filter,
                    { if (it == "ALL") tr2p(lang, "Все", "All") else GameStatus.valueOf(it).label(lang) }, { filter = it })
            }
            if (shown.isEmpty()) item {
                EmptyState(Icons.Outlined.SportsEsports, tr("Игр пока нет", "No games yet"),
                    tr("Добавьте игры, которые снимаете или хотите снять.", "Add games you record or plan to record."),
                    action = tr("Добавить игру", "Add game"), onAction = { nav.navigate(Routes.game(0)) })
            }
            items(shown, key = { it.id }) { g ->
                val st = gameStats(g.id, pubs)
                LxCard(onClick = { nav.navigate(Routes.game(g.id)) }, padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GameCover(g.coverUri, g.title, Modifier.width(56.dp).height(74.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOf(g.platform.label(lang), g.genre).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = LX.Muted, maxLines = 1)
                            Spacer(Modifier.height(4.dp))
                            Pill(g.status.label(lang), g.status.color)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                tr("Опубл. ${st.published} · План ${st.planned} · ${Fmt.number(st.views)} просм.",
                                    "Published ${st.published} · Planned ${st.planned} · ${Fmt.number(st.views)} views"),
                                style = MaterialTheme.typography.labelSmall, color = LX.Muted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameEditScreen(nav: NavHostController, id: Long) {
    val repo = LocalRepo.current
    val lang = LocalLang.current
    val zone = LocalZone.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pubs by repo.publications.collectAsStateWithLifecycle(emptyList())
    val ideas by repo.ideas.collectAsStateWithLifecycle(emptyList())

    var loaded by rememberSaveable { mutableStateOf(false) }
    var original by remember { mutableStateOf<Game?>(null) }
    var title by rememberSaveable { mutableStateOf("") }
    var cover by rememberSaveable { mutableStateOf<String?>(null) }
    var platformName by rememberSaveable { mutableStateOf(Platform.PC.name) }
    var genre by rememberSaveable { mutableStateOf("") }
    var hours by rememberSaveable { mutableStateOf("") }
    var statusName by rememberSaveable { mutableStateOf(GameStatus.WANT.name) }
    var notes by rememberSaveable { mutableStateOf("") }
    var titleError by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != 0L) {
            val g = repo.game(id)
            original = g
            if (g != null && !loaded) {
                title = g.title; cover = g.coverUri; platformName = g.platform.name; genre = g.genre
                hours = g.hoursToBeat?.toString() ?: ""; statusName = g.status.name; notes = g.notes
            }
        }
        loaded = true
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            cover = uri.toString()
        }
    }

    fun build(): Game? {
        if (title.isBlank()) { titleError = true; return null }
        return (original ?: Game(title = "")).copy(
            title = title.trim(), coverUri = cover, platform = Platform.valueOf(platformName), genre = genre.trim(),
            hoursToBeat = hours.toIntOrNull(), status = GameStatus.valueOf(statusName), notes = notes.trim(),
        )
    }

    LxScaffold(
        topBar = {
            LxTopBar(if (id == 0L) tr("Новая игра", "New game") else tr("Игра", "Game"), onBack = { nav.popBackStack() }, actions = {
                if (id != 0L) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, tr("Удалить", "Delete")) }
            })
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(96.dp).height(128.dp).clickable { picker.launch(arrayOf("image/*")) }) {
                    GameCover(cover, title.ifBlank { "+" }, Modifier.fillMaxSize())
                    if (cover == null) Icon(Icons.Outlined.AddPhotoAlternate, null, tint = LX.Muted,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("Обложка", "Cover"), style = MaterialTheme.typography.titleSmall)
                    Text(tr("Выберите изображение из галереи. Оно не изменяется и не копируется.", "Pick an image from your gallery. It isn't edited or copied."),
                        style = MaterialTheme.typography.bodySmall, color = LX.Muted)
                    if (cover != null) TextButton(onClick = { cover = null }) { Text(tr("Убрать обложку", "Remove cover")) }
                }
            }
            OutlinedTextField(
                title, { title = it.take(120); titleError = false }, label = { Text(tr("Название *", "Title *")) },
                isError = titleError, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = if (titleError) { { Text(tr("Введите название", "Enter a title")) } } else null,
            )
            Text(tr("Платформа", "Platform"), style = MaterialTheme.typography.labelLarge, color = LX.Muted)
            ChipsRow(Platform.entries, Platform.valueOf(platformName), { it.label(lang) }, { platformName = it.name })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(genre, { genre = it.take(60) }, label = { Text(tr("Жанр", "Genre")) }, singleLine = true, modifier = Modifier.weight(1.4f))
                NumberField(tr("Часов", "Hours"), hours, { hours = it.take(5) }, Modifier.weight(1f))
            }
            Text(tr("Статус", "Status"), style = MaterialTheme.typography.labelLarge, color = LX.Muted)
            ChipsRow(GameStatus.entries, GameStatus.valueOf(statusName), { it.label(lang) }, { statusName = it.name })
            OutlinedTextField(notes, { notes = it.take(3000) }, label = { Text(tr("Заметки", "Notes")) }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = { build()?.let { g -> repo.launch { saveGame(g) }; nav.popBackStack() } },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(tr("Сохранить", "Save")) }

            if (id != 0L) {
                val st = gameStats(id, pubs)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(tr("Опубликовано", "Published"), "${st.published}", Modifier.weight(1f))
                    StatTile(tr("В плане", "Planned"), "${st.planned}", Modifier.weight(1f))
                    StatTile(tr("Просмотры", "Views"), Fmt.number(st.views), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { nav.navigate(Routes.idea(0, gameId = id)) }, modifier = Modifier.weight(1f)) { Text(tr("+ Идея", "+ Idea")) }
                    OutlinedButton(onClick = { nav.navigate(Routes.pub(0, gameId = id)) }, modifier = Modifier.weight(1f)) { Text(tr("+ Публикация", "+ Upload")) }
                }
                val myIdeas = ideas.filter { it.gameId == id }
                if (myIdeas.isNotEmpty()) {
                    SectionTitle(tr("Идеи", "Ideas") + " · ${myIdeas.size}")
                    myIdeas.forEach { i ->
                        LxCard(onClick = { nav.navigate(Routes.idea(i.id)) }, padding = PaddingValues(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Pill(i.type.label(lang), i.type.color)
                                Spacer(Modifier.width(8.dp))
                                Text(i.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                val myPubs = pubs.filter { it.gameId == id }.sortedByDescending { it.scheduledAt }
                if (myPubs.isNotEmpty()) {
                    SectionTitle(tr("Публикации", "Uploads") + " · ${myPubs.size}")
                    myPubs.take(30).forEach { p ->
                        LxCard(onClick = { nav.navigate(Routes.pub(p.id)) }, padding = PaddingValues(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Pill(p.type.label(lang), p.type.color)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(Fmt.dateTime(p.scheduledAt, zone, lang) + " · " + p.status.label(lang),
                                        style = MaterialTheme.typography.bodySmall, color = p.status.color)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            tr("Удалить игру?", "Delete game?"),
            tr("Идеи и публикации останутся, но потеряют привязку к игре.", "Ideas and uploads stay but lose the link to this game."),
            tr("Удалить", "Delete"),
            onConfirm = { original?.let { g -> scope.launch { repo.deleteGame(g); nav.popBackStack() } } },
            onDismiss = { confirmDelete = false },
        )
    }
}
