package com.lunexa.studio.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.withTransaction
import com.lunexa.studio.notify.Reminders
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

@Serializable
data class Backup(
    val format: String = BACKUP_FORMAT,
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val settings: AppSettings,
    val games: List<Game> = emptyList(),
    val ideas: List<Idea> = emptyList(),
    val publications: List<Publication> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val snapshots: List<ChannelSnapshot> = emptyList(),
)

const val BACKUP_FORMAT = "lunexa-studio-backup"

class Repository(
    private val context: Context,
    private val db: AppDatabase,
    private val store: SettingsStore,
) {
    private val handler = CoroutineExceptionHandler { _, e -> Log.e("LunexaRepo", "Background write failed", e) }

    /** Область для записей, которые не должны прерываться при уходе с экрана. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)

    fun launch(block: suspend Repository.() -> Unit): Job = scope.launch { block(this@Repository) }

    // ---------- Потоки данных ----------
    val settings = store.flow
    val games = db.games().observeAll()
    val ideas = db.ideas().observeAll()
    val publications = db.pubs().observeAll()
    val tasks = db.tasks().observeAll()
    val snapshots = db.snapshots().observeAll()

    suspend fun settingsNow(): AppSettings = store.current()

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        store.update(transform)
    }

    // ---------- Игры ----------
    suspend fun game(id: Long) = db.games().get(id)

    suspend fun saveGame(g: Game): Long =
        if (g.id == 0L) db.games().insert(g) else { db.games().update(g); g.id }

    suspend fun deleteGame(g: Game) = db.withTransaction {
        db.ideas().detachGame(g.id)
        db.pubs().detachGame(g.id)
        db.games().delete(g)
    }

    // ---------- Идеи ----------
    suspend fun idea(id: Long) = db.ideas().get(id)

    suspend fun saveIdea(i: Idea): Long =
        if (i.id == 0L) db.ideas().insert(i) else { db.ideas().update(i); i.id }

    suspend fun deleteIdea(i: Idea) = db.withTransaction {
        db.pubs().detachIdea(i.id)
        db.ideas().delete(i)
    }

    // ---------- Публикации ----------
    suspend fun publication(id: Long) = db.pubs().get(id)

    /**
     * Сохраняет публикацию. Для новой записи с повтором создаёт серию из [count] публикаций.
     * Возвращает id первой (или отредактированной) публикации.
     */
    suspend fun savePublication(p: Publication, repeat: Recurrence = Recurrence.NONE, count: Int = 1): Long {
        val s = store.current()
        val zone = zoneOf(s.timezone)
        if (p.id != 0L) {
            db.pubs().update(p)
            Reminders.schedulePublication(context, p, s)
            return p.id
        }
        if (repeat == Recurrence.NONE || count <= 1) {
            val id = db.pubs().insert(p)
            Reminders.schedulePublication(context, p.copy(id = id), s)
            return id
        }
        val series = UUID.randomUUID().toString()
        val start = p.scheduledAt.toZdt(zone)
        var firstId = 0L
        db.withTransaction {
            for (i in 0 until count.coerceAtMost(366)) {
                val at = when (repeat) {
                    Recurrence.DAILY -> start.plusDays(i.toLong())
                    Recurrence.WEEKLY -> start.plusWeeks(i.toLong())
                    Recurrence.MONTHLY -> start.plusMonths(i.toLong())
                    Recurrence.NONE -> start
                }.toInstant().toEpochMilli()
                val item = p.copy(scheduledAt = at, seriesId = series)
                val id = db.pubs().insert(item)
                if (i == 0) firstId = id
                Reminders.schedulePublication(context, item.copy(id = id), s)
            }
        }
        return firstId
    }

    /** Шаблон расписания: серия публикаций одного типа. */
    suspend fun generateSeries(
        type: ContentType, title: String, gameId: Long?, start: LocalDate,
        hour: Int, minute: Int, repeat: Recurrence, count: Int,
    ): Int {
        val s = store.current()
        val zone = zoneOf(s.timezone)
        val base = Publication(
            title = title, type = type, gameId = gameId,
            scheduledAt = start.millisAt(hour, minute, zone),
            status = PubStatus.PLANNED, remindBeforeMin = s.defaultReminderMin,
        )
        savePublication(base, repeat, count)
        return count
    }

    suspend fun deletePublication(p: Publication) {
        Reminders.cancel(context, Reminders.pubCode(p.id))
        db.pubs().delete(p)
    }

    suspend fun deleteSeries(seriesId: String) {
        val items = db.pubs().bySeries(seriesId)
        db.withTransaction { items.forEach { db.pubs().delete(it) } }
        items.forEach { Reminders.cancel(context, Reminders.pubCode(it.id)) }
    }

    // ---------- Задачи ----------
    suspend fun task(id: Long) = db.tasks().get(id)

    suspend fun saveTask(t: TaskItem): Long {
        val s = store.current()
        val id = if (t.id == 0L) db.tasks().insert(t) else { db.tasks().update(t); t.id }
        Reminders.scheduleTask(context, t.copy(id = id), s)
        return id
    }

    suspend fun setTaskDone(t: TaskItem, done: Boolean) {
        val s = store.current()
        val zone = zoneOf(s.timezone)
        val updated = t.copy(done = done, doneAt = if (done) System.currentTimeMillis() else null)
        db.tasks().update(updated)
        Reminders.scheduleTask(context, updated, s)
        // Повторяющаяся задача: при выполнении создаём следующий экземпляр.
        if (done && t.recurrence != Recurrence.NONE && t.dueAt != null) {
            val due = t.dueAt.toZdt(zone)
            var next = when (t.recurrence) {
                Recurrence.DAILY -> due.plusDays(1)
                Recurrence.WEEKLY -> due.plusWeeks(1)
                Recurrence.MONTHLY -> due.plusMonths(1)
                Recurrence.NONE -> due
            }
            val now = System.currentTimeMillis()
            // Пропускаем уже прошедшие периоды, чтобы не плодить просроченные копии.
            var guard = 0
            while (next.toInstant().toEpochMilli() < now && guard < 400) {
                next = when (t.recurrence) {
                    Recurrence.DAILY -> next.plusDays(1)
                    Recurrence.WEEKLY -> next.plusWeeks(1)
                    else -> next.plusMonths(1)
                }
                guard++
            }
            val copy = t.copy(id = 0, done = false, doneAt = null, dueAt = next.toInstant().toEpochMilli(),
                createdAt = System.currentTimeMillis())
            val newId = db.tasks().insert(copy)
            Reminders.scheduleTask(context, copy.copy(id = newId), s)
        }
    }

    suspend fun deleteTask(t: TaskItem) {
        Reminders.cancel(context, Reminders.taskCode(t.id))
        db.tasks().delete(t)
    }

    // ---------- Снимки канала ----------
    suspend fun snapshot(id: Long) = db.snapshots().get(id)

    suspend fun saveSnapshot(s: ChannelSnapshot, replacedId: Long?) = db.withTransaction {
        // Если дату изменили у существующего снимка — удаляем старую запись.
        if (replacedId != null && replacedId != 0L) {
            db.snapshots().get(replacedId)?.let { old ->
                if (old.dateEpochDay != s.dateEpochDay) db.snapshots().delete(old)
            }
        }
        db.snapshots().upsertByDate(s.copy(id = 0))
    }

    suspend fun deleteSnapshot(s: ChannelSnapshot) = db.snapshots().delete(s)

    // ---------- Напоминания ----------
    suspend fun rescheduleAll() {
        val s = store.current()
        db.pubs().all().forEach { Reminders.schedulePublication(context, it, s) }
        db.tasks().all().forEach { Reminders.scheduleTask(context, it, s) }
        Reminders.scheduleWeekly(context, s)
    }

    // ---------- YouTube ----------
    private val _yt = MutableStateFlow(YouTubeStore.load(context))
    val youtube: StateFlow<YtCache?> = _yt

    /** Последний токен — только в памяти, на диск не пишется. */
    @Volatile private var lastToken: String? = null

    suspend fun youtubeSync(token: String): YtCache {
        lastToken = token
        val s = store.current()
        val zone = zoneOf(s.timezone)
        val cache = YouTubeApi.fetch(token, zone)
        withContext(Dispatchers.IO) { YouTubeStore.save(context, cache) }
        _yt.value = cache
        syncLinkedViews(cache)
        store.update { it.copy(youtubeConnected = true, channelName = cache.title) }
        // Снимок на сегодня — чтобы график роста на главной строился из реальных данных.
        if (!cache.hiddenSubscribers) {
            db.snapshots().upsertByDate(
                ChannelSnapshot(
                    dateEpochDay = LocalDate.now(zone).toEpochDay(), subscribers = cache.subscribers,
                    totalViews = cache.views, videoCount = cache.videoCount, note = "YouTube API",
                ),
            )
        }
        return cache
    }

    /** Режим открытых данных: по ссылке на канал, без входа и без Google Cloud. */
    suspend fun youtubePublicSync(input: String): YtCache {
        val cache = YouTubePublic.fetch(input)
        withContext(Dispatchers.IO) { YouTubeStore.save(context, cache) }
        _yt.value = cache
        syncLinkedViews(cache)
        store.update { it.copy(youtubeConnected = true, youtubeChannelInput = input.trim(), channelName = cache.title) }
        return cache
    }

    /** Просмотры связанных публикаций обновляются из данных канала. */
    private suspend fun syncLinkedViews(cache: YtCache) {
        cache.videos.forEach { v -> db.pubs().updateViewsByVideo(v.id, v.views) }
    }

    suspend fun youtubeDisconnect() {
        lastToken?.let { YouTubeApi.revoke(it) }
        lastToken = null
        withContext(Dispatchers.IO) { YouTubeStore.clear(context) }
        _yt.value = null
        store.update { it.copy(youtubeConnected = false, youtubeChannelInput = "") }
    }

    // ---------- Резервное копирование ----------
    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val backup = Backup(
            settings = store.current(),
            games = db.games().all(),
            ideas = db.ideas().all(),
            publications = db.pubs().all(),
            tasks = db.tasks().all(),
            snapshots = db.snapshots().all(),
        )
        val text = AppJson.encodeToString(Backup.serializer(), backup)
        val stream = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(uri, "w")
            ?: throw IllegalStateException("Cannot open file for writing")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        backup.games.size + backup.ideas.size + backup.publications.size + backup.tasks.size + backup.snapshots.size
    }

    /** Полностью заменяет данные содержимым файла. Бросает исключение при неверном формате. */
    suspend fun importFrom(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("Cannot open file")
        val backup = AppJson.decodeFromString(Backup.serializer(), text)
        require(backup.format == BACKUP_FORMAT) { "Not a LUNEXA Studio backup" }

        // Снимаем старые напоминания до замены данных.
        db.pubs().all().forEach { Reminders.cancel(context, Reminders.pubCode(it.id)) }
        db.tasks().all().forEach { Reminders.cancel(context, Reminders.taskCode(it.id)) }

        db.withTransaction {
            db.games().clear(); db.ideas().clear(); db.pubs().clear(); db.tasks().clear(); db.snapshots().clear()
            db.games().insertAll(backup.games)
            db.ideas().insertAll(backup.ideas)
            db.pubs().insertAll(backup.publications)
            db.tasks().insertAll(backup.tasks)
            db.snapshots().insertAll(backup.snapshots)
        }
        store.update { backup.settings }
        rescheduleAll()
        backup.games.size + backup.ideas.size + backup.publications.size + backup.tasks.size + backup.snapshots.size
    }
}
