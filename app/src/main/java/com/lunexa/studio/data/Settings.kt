package com.lunexa.studio.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException

@Serializable
data class AppSettings(
    val workspaceName: String = "LUNEXA Studio",
    val channelName: String = "",
    val channelAvatarUri: String? = null,
    val timezone: String = "Asia/Novosibirsk",
    val language: String = "ru",
    val notificationsEnabled: Boolean = true,
    val notifyPublications: Boolean = true,
    val notifyTasks: Boolean = true,
    val notifyWeekly: Boolean = true,
    val defaultReminderMin: Int = 30,
    /** Время Shorts по умолчанию, HH:mm. */
    val shortsTime: String = "13:25",
    val longTime: String = "18:00",
    val tiktokTime: String = "19:00",
    val subscriberGoal: Long = 100,
    val accent: Int = 0,
    val youtubeConnected: Boolean = false,
    /** Ссылка или @имя канала для режима открытых данных. */
    val youtubeChannelInput: String = "",
    // ----- Этап 3 -----
    val notifyGoals: Boolean = true,
    val notifyChanges: Boolean = true,
    /** ИИ: адрес OpenAI-совместимого API и модель. Ключ хранится отдельно, в зашифрованном виде. */
    val aiBaseUrl: String = "",
    val aiModel: String = "",
    /** Цели заработка в месяц. */
    val incomeGoalRub: Long = 0,
    val incomeGoalUsd: Long = 0,
    /** Показатели для YPP, которые без входа Google вводятся вручную из YouTube Studio. */
    val yppWatchHours: Long = 0,
    val yppShortsViews90: Long = 0,
    val checklist: List<String> = DEFAULT_CHECKLIST,
    val checklistDone: List<Int> = emptyList(),
)

val DEFAULT_CHECKLIST = listOf(
    "Название понятно и цепляет (EN)",
    "Описание: о чём видео + название игры",
    "3–5 хештегов, включая #shorts для Shorts",
    "Ключевые слова / теги добавлены",
    "Обложка выбрана (для длинных видео)",
    "Плейлист выбран",
    "Ограничение по возрасту и «Для детей» указаны",
    "Конечная заставка / ссылка на следующую часть",
    "Время публикации совпадает с планом",
    "Публикация отмечена в календаре",
)

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    explicitNulls = false
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val key = stringPreferencesKey("app_settings_json")

    val flow: Flow<AppSettings> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> decode(prefs[key]) }

    suspend fun current(): AppSettings = flow.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val updated = transform(decode(prefs[key]))
            prefs[key] = AppJson.encodeToString(AppSettings.serializer(), updated)
        }
    }

    private fun decode(raw: String?): AppSettings =
        if (raw.isNullOrBlank()) AppSettings()
        else runCatching { AppJson.decodeFromString(AppSettings.serializer(), raw) }.getOrDefault(AppSettings())
}
