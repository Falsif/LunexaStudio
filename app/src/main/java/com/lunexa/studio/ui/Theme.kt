package com.lunexa.studio.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lunexa.studio.data.AppSettings
import com.lunexa.studio.data.ContentType
import com.lunexa.studio.data.DEFAULT_ZONE
import com.lunexa.studio.data.GameStatus
import com.lunexa.studio.data.Platform
import com.lunexa.studio.data.Priority
import com.lunexa.studio.data.PubStatus
import com.lunexa.studio.data.Recurrence
import com.lunexa.studio.data.Repository
import java.time.ZoneId

object LX {
    val Bg = Color(0xFF0B0B0F)
    val Card = Color(0xFF19191F)
    val Card2 = Color(0xFF23232B)
    val Line = Color(0xFF2E2E36)
    val Accent = Color(0xFFE11D48)
    val Red2 = Color(0xFF991B1B)
    val Text = Color(0xFFF8FAFC)
    val Muted = Color(0xFFA1A1AA)
    val Ok = Color(0xFF22C55E)
    val Warn = Color(0xFFF59E0B)
    val Cyan = Color(0xFF22D3EE)
}

val AccentOptions = listOf(Color(0xFFE11D48), Color(0xFFDC2626), Color(0xFFF43F5E))

@Composable
fun LunexaTheme(accentIndex: Int, content: @Composable () -> Unit) {
    val accent = AccentOptions.getOrElse(accentIndex) { AccentOptions[0] }
    val scheme = darkColorScheme(
        primary = accent, onPrimary = Color.White,
        primaryContainer = LX.Red2, onPrimaryContainer = LX.Text,
        secondary = LX.Red2, onSecondary = Color.White,
        secondaryContainer = Color(0xFF3B0D16), onSecondaryContainer = LX.Text,
        tertiary = LX.Cyan,
        background = LX.Bg, onBackground = LX.Text,
        surface = LX.Bg, onSurface = LX.Text,
        surfaceVariant = LX.Card, onSurfaceVariant = LX.Muted,
        surfaceContainerLowest = LX.Bg, surfaceContainerLow = LX.Card,
        surfaceContainer = LX.Card, surfaceContainerHigh = LX.Card2, surfaceContainerHighest = LX.Card2,
        outline = Color(0xFF3F3F46), outlineVariant = LX.Line,
        error = Color(0xFFF87171), onError = Color.Black,
    )
    val base = Typography()
    val typography = base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = typography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}

// ---------- Общие значения дерева ----------
val LocalRepo = staticCompositionLocalOf<Repository> { error("Repository not provided") }
val LocalSettings = compositionLocalOf { AppSettings() }
val LocalLang = compositionLocalOf { "ru" }
val LocalZone = compositionLocalOf<ZoneId> { ZoneId.of(DEFAULT_ZONE) }

/** Двуязычная строка: русский по умолчанию, английский при переключении. */
@Composable
@ReadOnlyComposable
fun tr(ru: String, en: String): String = if (LocalLang.current == "en") en else ru

fun trL(lang: String, ru: String, en: String) = if (lang == "en") en else ru

// ---------- Подписи перечислений ----------
fun ContentType.label(lang: String) = when (this) {
    ContentType.SHORTS -> "Shorts"
    ContentType.LONG -> trL(lang, "Длинное видео", "Long video")
    ContentType.TIKTOK -> "TikTok"
}

val ContentType.color: Color
    get() = when (this) {
        ContentType.SHORTS -> LX.Accent
        ContentType.LONG -> LX.Warn
        ContentType.TIKTOK -> LX.Cyan
    }

fun PubStatus.label(lang: String) = when (this) {
    PubStatus.IDEA -> trL(lang, "Идея", "Idea")
    PubStatus.PLANNED -> trL(lang, "Запланировано", "Planned")
    PubStatus.READY -> trL(lang, "Готово", "Ready")
    PubStatus.PUBLISHED -> trL(lang, "Опубликовано", "Published")
    PubStatus.CANCELLED -> trL(lang, "Отменено", "Cancelled")
}

val PubStatus.color: Color
    get() = when (this) {
        PubStatus.IDEA -> LX.Muted
        PubStatus.PLANNED -> LX.Cyan
        PubStatus.READY -> LX.Warn
        PubStatus.PUBLISHED -> LX.Ok
        PubStatus.CANCELLED -> Color(0xFF71717A)
    }

fun Priority.label(lang: String) = when (this) {
    Priority.LOW -> trL(lang, "Низкий", "Low")
    Priority.MEDIUM -> trL(lang, "Средний", "Medium")
    Priority.HIGH -> trL(lang, "Высокий", "High")
}

val Priority.color: Color
    get() = when (this) {
        Priority.LOW -> LX.Muted
        Priority.MEDIUM -> LX.Warn
        Priority.HIGH -> LX.Accent
    }

fun GameStatus.label(lang: String) = when (this) {
    GameStatus.WANT -> trL(lang, "Хочу снять", "Want to record")
    GameStatus.RECORDING -> trL(lang, "Снимаю", "Recording")
    GameStatus.DONE -> trL(lang, "Завершено", "Completed")
    GameStatus.PAUSED -> trL(lang, "Отложено", "On hold")
}

val GameStatus.color: Color
    get() = when (this) {
        GameStatus.WANT -> LX.Cyan
        GameStatus.RECORDING -> LX.Accent
        GameStatus.DONE -> LX.Ok
        GameStatus.PAUSED -> LX.Muted
    }

fun Platform.label(lang: String) = when (this) {
    Platform.ANDROID -> "Android"
    Platform.PC -> trL(lang, "ПК", "PC")
    Platform.CONSOLE -> trL(lang, "Консоль", "Console")
}

fun Recurrence.label(lang: String) = when (this) {
    Recurrence.NONE -> trL(lang, "Не повторять", "Don't repeat")
    Recurrence.DAILY -> trL(lang, "Ежедневно", "Daily")
    Recurrence.WEEKLY -> trL(lang, "Еженедельно", "Weekly")
    Recurrence.MONTHLY -> trL(lang, "Ежемесячно", "Monthly")
}

fun reminderLabel(min: Int, lang: String): String = when {
    min < 0 -> trL(lang, "Без напоминания", "No reminder")
    min == 0 -> trL(lang, "В момент публикации", "At publish time")
    min < 60 -> trL(lang, "За $min мин", "$min min before")
    min < 1440 -> trL(lang, "За ${min / 60} ч", "${min / 60} h before")
    else -> trL(lang, "За ${min / 1440} дн", "${min / 1440} day before")
}

val ReminderOptions = listOf(-1, 0, 10, 30, 60, 120, 1440)
