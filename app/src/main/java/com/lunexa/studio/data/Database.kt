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

@Database(
    entities = [Game::class, Idea::class, Publication::class, TaskItem::class, ChannelSnapshot::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun games(): GameDao
    abstract fun ideas(): IdeaDao
    abstract fun pubs(): PublicationDao
    abstract fun tasks(): TaskDao
    abstract fun snapshots(): SnapshotDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "lunexa_studio.db").build()
    }
}
