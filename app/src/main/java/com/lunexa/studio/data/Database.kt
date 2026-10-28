package com.lunexa.studio.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<Game>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun get(id: Long): Game?

    @Query("SELECT * FROM games")
    suspend fun all(): List<Game>

    @Insert suspend fun insert(item: Game): Long
    @Update suspend fun update(item: Game)
    @Delete suspend fun delete(item: Game)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Game>)

    @Query("DELETE FROM games") suspend fun clear()
}

@Dao
interface IdeaDao {
    @Query("SELECT * FROM ideas ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Idea>>

    @Query("SELECT * FROM ideas WHERE id = :id")
    suspend fun get(id: Long): Idea?

    @Query("SELECT * FROM ideas")
    suspend fun all(): List<Idea>

    @Insert suspend fun insert(item: Idea): Long
    @Update suspend fun update(item: Idea)
    @Delete suspend fun delete(item: Idea)

    @Query("UPDATE ideas SET gameId = NULL WHERE gameId = :gameId")
    suspend fun detachGame(gameId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Idea>)

    @Query("DELETE FROM ideas") suspend fun clear()
}

@Dao
interface PublicationDao {
    @Query("SELECT * FROM publications ORDER BY scheduledAt ASC")
    fun observeAll(): Flow<List<Publication>>

    @Query("SELECT * FROM publications WHERE id = :id")
    suspend fun get(id: Long): Publication?

    @Query("SELECT * FROM publications")
    suspend fun all(): List<Publication>

    @Query("SELECT * FROM publications WHERE seriesId = :seriesId")
    suspend fun bySeries(seriesId: String): List<Publication>

    @Insert suspend fun insert(item: Publication): Long
    @Update suspend fun update(item: Publication)
    @Delete suspend fun delete(item: Publication)

    @Query("UPDATE publications SET gameId = NULL WHERE gameId = :gameId")
    suspend fun detachGame(gameId: Long)

    @Query("UPDATE publications SET views = :views, status = 'PUBLISHED' WHERE videoId = :videoId")
    suspend fun updateViewsByVideo(videoId: String, views: Long)

    @Query("UPDATE publications SET ideaId = NULL WHERE ideaId = :ideaId")
    suspend fun detachIdea(ideaId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Publication>)

    @Query("DELETE FROM publications") suspend fun clear()
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TaskItem>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskItem?

    @Query("SELECT * FROM tasks")
    suspend fun all(): List<TaskItem>

    @Insert suspend fun insert(item: TaskItem): Long
    @Update suspend fun update(item: TaskItem)
    @Delete suspend fun delete(item: TaskItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TaskItem>)

    @Query("DELETE FROM tasks") suspend fun clear()
}

@Dao
interface SnapshotDao {
    @Query("SELECT * FROM snapshots ORDER BY dateEpochDay ASC")
    fun observeAll(): Flow<List<ChannelSnapshot>>

    @Query("SELECT * FROM snapshots WHERE id = :id")
    suspend fun get(id: Long): ChannelSnapshot?

    @Query("SELECT * FROM snapshots")
    suspend fun all(): List<ChannelSnapshot>

    /** Один снимок на дату: при совпадении даты запись заменяется. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertByDate(item: ChannelSnapshot): Long

    @Delete suspend fun delete(item: ChannelSnapshot)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChannelSnapshot>)

    @Query("DELETE FROM snapshots") suspend fun clear()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY achievedAt IS NOT NULL, createdAt")
    fun observeAll(): Flow<List<Goal>>
    @Query("SELECT * FROM goals") suspend fun all(): List<Goal>
    @Insert suspend fun insert(item: Goal): Long
    @Update suspend fun update(item: Goal)
    @Delete suspend fun delete(item: Goal)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<Goal>)
    @Query("DELETE FROM goals") suspend fun clear()
}

@Dao
interface IncomeDao {
    @Query("SELECT * FROM incomes ORDER BY dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<Income>>
    @Query("SELECT * FROM incomes") suspend fun all(): List<Income>
    @Insert suspend fun insert(item: Income): Long
    @Update suspend fun update(item: Income)
    @Delete suspend fun delete(item: Income)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<Income>)
    @Query("DELETE FROM incomes") suspend fun clear()
}

@Dao
interface SeoDao {
    @Query("SELECT * FROM seo ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SeoItem>>
    @Query("SELECT * FROM seo") suspend fun all(): List<SeoItem>
    @Insert suspend fun insert(item: SeoItem): Long
    @Update suspend fun update(item: SeoItem)
    @Delete suspend fun delete(item: SeoItem)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<SeoItem>)
    @Query("DELETE FROM seo") suspend fun clear()
}

@Database(
    entities = [
        Game::class, Idea::class, Publication::class, TaskItem::class, ChannelSnapshot::class,
        Goal::class, Income::class, SeoItem::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun games(): GameDao
    abstract fun ideas(): IdeaDao
    abstract fun pubs(): PublicationDao
    abstract fun tasks(): TaskDao
    abstract fun snapshots(): SnapshotDao
    abstract fun goals(): GoalDao
    abstract fun incomes(): IncomeDao
    abstract fun seo(): SeoDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "lunexa_studio.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()

        /** v2: связь публикации с роликом на YouTube. Данные пользователя сохраняются. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE publications ADD COLUMN videoId TEXT")
            }
        }

        /** v3: цели, доходы, SEO-шаблоны. */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `goals` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` TEXT NOT NULL, `title` TEXT NOT NULL, `target` INTEGER NOT NULL, `manualProgress` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `achievedAt` INTEGER)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `incomes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dateEpochDay` INTEGER NOT NULL, `amount` REAL NOT NULL, `currency` TEXT NOT NULL, `source` TEXT NOT NULL, `confirmed` INTEGER NOT NULL, `note` TEXT NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `seo` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, `title` TEXT NOT NULL, `text` TEXT NOT NULL, `gameId` INTEGER, `lang` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)")
            }
        }
    }
}
