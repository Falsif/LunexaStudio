package com.lunexa.studio.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable enum class ContentType { SHORTS, LONG, TIKTOK }
@Serializable enum class PubStatus { IDEA, PLANNED, READY, PUBLISHED, CANCELLED }
@Serializable enum class Priority { LOW, MEDIUM, HIGH }
@Serializable enum class GameStatus { WANT, RECORDING, DONE, PAUSED }
@Serializable enum class Platform { ANDROID, PC, CONSOLE }
@Serializable enum class Recurrence { NONE, DAILY, WEEKLY, MONTHLY }

@Serializable
@Entity(tableName = "games")
data class Game(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val coverUri: String? = null,
    val platform: Platform = Platform.PC,
    val genre: String = "",
    val hoursToBeat: Int? = null,
    val status: GameStatus = GameStatus.WANT,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "ideas", indices = [Index("gameId")])
data class Idea(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val gameId: Long? = null,
    val type: ContentType = ContentType.SHORTS,
    val description: String = "",
    val tags: String = "",
    val priority: Priority = Priority.MEDIUM,
    val durationSec: Int? = null,
    /** Начало дня (epoch millis) в часовом поясе пользователя. */
    val plannedDate: Long? = null,
    val status: PubStatus = PubStatus.IDEA,
    val notes: String = "",
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "publications", indices = [Index("gameId"), Index("scheduledAt"), Index("seriesId")])
data class Publication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val gameId: Long? = null,
    val type: ContentType = ContentType.SHORTS,
    val scheduledAt: Long,
    val status: PubStatus = PubStatus.PLANNED,
    /** За сколько минут напомнить; -1 — без напоминания. */
    val remindBeforeMin: Int = 30,
    val seriesId: String? = null,
    val ideaId: Long? = null,
    /** Просмотры, введённые вручную (до подключения YouTube API). */
    val views: Long? = null,
    /** id ролика на YouTube, если публикация связана с видео на канале. */
    val videoId: String? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "tasks")
data class TaskItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val priority: Priority = Priority.MEDIUM,
    val dueAt: Long? = null,
    val recurrence: Recurrence = Recurrence.NONE,
    val remind: Boolean = true,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Ручной снимок показателей канала на дату. */
@Serializable
@Entity(tableName = "snapshots", indices = [Index(value = ["dateEpochDay"], unique = true)])
data class ChannelSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val subscribers: Long,
    val totalViews: Long,
    val videoCount: Long,
    val note: String = "",
)

// ---------- Этап 3 ----------

@Serializable enum class GoalType { SUBSCRIBERS, VIEWS, VIDEOS, STREAK, CUSTOM }

@Serializable
@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: GoalType = GoalType.SUBSCRIBERS,
    /** Название — только для собственных целей; для остальных строится автоматически. */
    val title: String = "",
    val target: Long,
    /** Текущее значение собственной цели (вводится вручную). */
    val manualProgress: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val achievedAt: Long? = null,
)

@Serializable enum class Currency { RUB, USD }

@Serializable
@Entity(tableName = "incomes")
data class Income(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val amount: Double,
    val currency: Currency = Currency.RUB,
    val source: String = "",
    /** true — деньги получены (подтверждено), false — оценка/ожидание. */
    val confirmed: Boolean = true,
    val note: String = "",
)

@Serializable enum class SeoKind { TITLE, DESCRIPTION, HASHTAGS, KEYWORDS }

@Serializable
@Entity(tableName = "seo")
data class SeoItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: SeoKind = SeoKind.TITLE,
    val title: String = "",
    val text: String,
    val gameId: Long? = null,
    val lang: String = "en",
    val createdAt: Long = System.currentTimeMillis(),
)
