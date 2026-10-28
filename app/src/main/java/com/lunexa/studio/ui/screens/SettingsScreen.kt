package com.lunexa.studio.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.lunexa.studio.data.AppSettings
import com.lunexa.studio.data.hm
import com.lunexa.studio.data.parseHm
import com.lunexa.studio.notify.Notifier
import com.lunexa.studio.notify.Reminders
import com.lunexa.studio.ui.AccentOptions
import com.lunexa.studio.ui.ChipsRow
import com.lunexa.studio.ui.ConfirmDialog
import com.lunexa.studio.ui.DropdownField
import com.lunexa.studio.ui.LX
import com.lunexa.studio.ui.LocalLang
import com.lunexa.studio.ui.LocalRepo
import com.lunexa.studio.ui.LocalSettings
import com.lunexa.studio.ui.LxCard
import com.lunexa.studio.ui.LxScaffold
import com.lunexa.studio.ui.LxTopBar
import com.lunexa.studio.ui.NumberField
import com.lunexa.studio.ui.ReminderOptions
import com.lunexa.studio.ui.SectionTitle
import com.lunexa.studio.ui.TimeField
import com.lunexa.studio.ui.reminderLabel
import com.lunexa.studio.ui.tr
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun SettingsScreen(nav: NavHostController) {
    val repo = LocalRepo.current
    val s = LocalSettings.current
    val lang = LocalLang.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var workspace by remember { mutableStateOf(s.workspaceName) }
    var channel by remember { mutableStateOf(s.channelName) }
    var goal by remember { mutableStateOf(s.subscriberGoal.toString()) }
    var showZones by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    /** Сохраняет настройки и пересчитывает напоминания, если затронуты уведомления/пояс/язык. */
    fun update(reschedule: Boolean = false, t: (AppSettings) -> AppSettings) {
        repo.launch {
            updateSettings(t)
            if (reschedule) rescheduleAll()
        }
    }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) scope.launch {
            snackbar.showSnackbar(trS(lang, "Разрешение не выдано — включите уведомления в настройках Android", "Permission denied — enable notifications in Android settings"))
        }
    }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            update { it.copy(channelAvatarUri = uri.toString()) }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val msg = runCatching { repo.exportTo(uri) }.fold(
                { n -> trS(lang, "Сохранено записей: $n", "Exported records: $n") },
                { e -> trS(lang, "Ошибка экспорта: ", "Export failed: ") + (e.message ?: "") },
            )
            snackbar.showSnackbar(msg)
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingImport = uri }

    LxScaffold(
        topBar = { LxTopBar(tr("Настройки", "Settings"), onBack = { nav.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------- Канал и пространство ----------
            SectionTitle(tr("Канал и рабочее пространство", "Channel & workspace"))
            LxCard {
                OutlinedTextField(workspace, { v -> workspace = v.take(40); val name = workspace.ifBlank { "LUNEXA Studio" }; update { s0 -> s0.copy(workspaceName = name) } },
                    label = { Text(tr("Название рабочего пространства", "Workspace name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(channel, { v -> channel = v.take(60); val name = channel.trim(); update { s0 -> s0.copy(channelName = name) } },
                    label = { Text(tr("Название YouTube-канала", "YouTube channel name")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Аватар канала", "Channel avatar"), modifier = Modifier.weight(1f))
                    TextButton(onClick = { avatarPicker.launch(arrayOf("image/*")) }) { Text(tr("Выбрать", "Choose")) }
                    if (s.channelAvatarUri != null) TextButton(onClick = { update { it.copy(channelAvatarUri = null) } }) { Text(tr("Убрать", "Remove")) }
                }
                Spacer(Modifier.height(6.dp))
                NumberField(
                    tr("Цель по подписчикам", "Subscriber goal"), goal,
                    { goal = it; it.toLongOrNull()?.takeIf { v -> v > 0 }?.let { v -> update { s0 -> s0.copy(subscriberGoal = v) } } },
                    isError = (goal.toLongOrNull() ?: 0) <= 0,
                )
                Spacer(Modifier.height(6.dp))
                ChipsRow(listOf(100L, 500L, 1000L, 10_000L), s.subscriberGoal, { "%,d".format(it).replace(',', ' ') }, { v ->
                    goal = v.toString(); update { s0 -> s0.copy(subscriberGoal = v) }
                })
            }

            // ---------- Язык и время ----------
            SectionTitle(tr("Язык и время", "Language & time"))
            LxCard {
                Text(tr("Язык интерфейса", "Interface language"), style = MaterialTheme.typography.labelLarge, color = LX.Muted)
                Spacer(Modifier.height(6.dp))
                ChipsRow(listOf("ru", "en"), s.language, { if (it == "ru") "Русский" else "English" }, { l -> update(reschedule = true) { it.copy(language = l) } })
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr("Часовой пояс", "Time zone"))
                        Text(s.timezone, style = MaterialTheme.typography.bodySmall, color = LX.Muted)
                    }
                    TextButton(onClick = { showZones = true }) { Text(tr("Изменить", "Change")) }
                }
                Spacer(Modifier.height(8.dp))
                Text(tr("Время по умолчанию", "Default times"), style = MaterialTheme.typography.labelLarge, color = LX.Muted)
                Spacer(Modifier.height(6.dp))
                val (sh, sm) = parseHm(s.shortsTime, 13, 25)
                val (lh, lm) = parseHm(s.longTime, 18, 0)
                val (th, tm) = parseHm(s.tiktokTime, 19, 0)
                TimeField("Shorts", sh, sm, { h, m -> update { it.copy(shortsTime = hm(h, m)) } })
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeField(tr("Длинные", "Long"), lh, lm, { h, m -> update { it.copy(longTime = hm(h, m)) } }, Modifier.weight(1f))
                    TimeField("TikTok", th, tm, { h, m -> update { it.copy(tiktokTime = hm(h, m)) } }, Modifier.weight(1f))
                }
            }

            // ---------- Уведомления ----------
            SectionTitle(tr("Уведомления", "Notifications"))
            LxCard {
                SwitchRow(tr("Все уведомления", "All notifications"), s.notificationsEnabled) { on ->
                    if (on && Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(context)) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                    update(reschedule = true) { it.copy(notificationsEnabled = on) }
                }
                if (s.notificationsEnabled) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = LX.Line)
                    SwitchRow(tr("О приближающихся публикациях", "Upcoming uploads"), s.notifyPublications) { on -> update(true) { it.copy(notifyPublications = on) } }
                    SwitchRow(tr("О сроках задач", "Task deadlines"), s.notifyTasks) { on -> update(true) { it.copy(notifyTasks = on) } }
                    SwitchRow(tr("Еженедельная проверка статистики (пн, 10:00)", "Weekly stats check (Mon, 10:00)"), s.notifyWeekly) { on -> update(true) { it.copy(notifyWeekly = on) } }
                    SwitchRow(tr("О выполнении целей", "Goals reached"), s.notifyGoals) { on -> update { it.copy(notifyGoals = on) } }
                    SwitchRow(tr("О важных изменениях канала", "Important channel changes"), s.notifyChanges) { on -> update { it.copy(notifyChanges = on) } }
                    Spacer(Modifier.height(6.dp))
                    DropdownField(
                        tr("Напоминание о публикации по умолчанию", "Default upload reminder"), ReminderOptions, s.defaultReminderMin,
                        { reminderLabel(it, lang) }, { v -> update { it.copy(defaultReminderMin = v) } },
                    )
                    if (!Notifier.canPost(context)) {
                        Spacer(Modifier.height(6.dp))
                        Text(tr("Система запретила уведомления для приложения.", "Notifications are blocked by the system."), color = LX.Accent, style = MaterialTheme.typography.bodySmall)
                        if (Build.VERSION.SDK_INT >= 33) TextButton(onClick = { notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text(tr("Разрешить", "Allow")) }
                    }
                }
            }

            // ---------- Внешний вид ----------
            SectionTitle(tr("Внешний вид", "Appearance"))
            LxCard {
                Text(tr("Акцентный цвет (тема всегда тёмная)", "Accent color (theme is always dark)"), style = MaterialTheme.typography.labelLarge, color = LX.Muted)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AccentOptions.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(c)
                                .border(3.dp, if (s.accent == i) Color.White else Color.Transparent, CircleShape)
                                .clickable { update { it.copy(accent = i) } },
                        )
                    }
                }
            }

            // ---------- Интеграции ----------
            SectionTitle(tr("Интеграции", "Integrations"))
            LxCard {
                YouTubeConnectBlock()
                Spacer(Modifier.height(10.dp))
                Text("TikTok", style = MaterialTheme.typography.titleSmall)
                Text(tr("Только ручное планирование. Автопубликация и статистика TikTok недоступны без официального доступа к API.",
                    "Manual planning only. TikTok auto-posting and stats require official API access."),
                    style = MaterialTheme.typography.bodySmall, color = LX.Muted)
                Spacer(Modifier.height(10.dp))
                Text(tr("ИИ-помощник", "AI assistant"), style = MaterialTheme.typography.titleSmall)
                Text(tr("Настраивается в «Ещё → ИИ-помощник»: свой ключ хранится зашифрованным, в APK ничего не вшито.", "Set up in More → AI assistant: your key is stored encrypted, nothing is bundled in the APK."),
                    style = MaterialTheme.typography.bodySmall, color = LX.Muted)
            }

            // ---------- Резервная копия ----------
            SectionTitle(tr("Резервное копирование", "Backup"))
            LxCard {
                Text(tr("Все идеи, расписание, задачи, игры, показатели и настройки в одном JSON-файле.",
                    "All ideas, schedule, tasks, games, stats and settings in one JSON file."),
                    style = MaterialTheme.typography.bodySmall, color = LX.Muted)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { exporter.launch("lunexa-studio-backup-${LocalDate.now()}.json") }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.FileUpload, null); Spacer(Modifier.width(6.dp)); Text(tr("Экспорт", "Export"))
                    }
                    OutlinedButton(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(6.dp)); Text(tr("Импорт", "Import"))
                    }
                }
            }

            // ---------- О приложении ----------
            SectionTitle(tr("О приложении", "About"))
            LxCard {
                val version = remember {
                    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
                }
                Text("LUNEXA Studio", style = MaterialTheme.typography.titleMedium)
                Text(tr("Версия $version", "Version $version"), style = MaterialTheme.typography.bodySmall, color = LX.Muted)
                Spacer(Modifier.height(6.dp))
                Text(tr("Личный центр управления игровым YouTube-каналом. Не видеоредактор. Данные хранятся только на устройстве.",
                    "Personal control center for a gaming YouTube channel. Not a video editor. Data stays on your device."),
                    style = MaterialTheme.typography.bodySmall, color = LX.Muted)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showZones) ZonePickerDialog(current = s.timezone, onDismiss = { showZones = false }) { z ->
        update(reschedule = true) { it.copy(timezone = z) }
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            tr("Импортировать данные?", "Import data?"),
            tr("Текущие данные будут полностью заменены содержимым файла. Сначала сделайте экспорт, если не уверены.",
                "Current data will be fully replaced by the file contents. Export first if unsure."),
            tr("Заменить", "Replace"),
            onConfirm = {
                scope.launch {
                    val msg = runCatching { repo.importFrom(uri) }.fold(
                        { n -> trS(lang, "Импортировано записей: $n", "Imported records: $n") },
                        { e -> trS(lang, "Не удалось импортировать: ", "Import failed: ") + (e.message ?: "") },
                    )
                    snackbar.showSnackbar(msg)
                }
            },
            onDismiss = { pendingImport = null },
        )
    }
}

private fun trS(lang: String, ru: String, en: String) = if (lang == "en") en else ru

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ZonePickerDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var q by remember { mutableStateOf("") }
    val all = remember { ZoneId.getAvailableZoneIds().filter { '/' in it && !it.startsWith("Etc") && !it.startsWith("SystemV") }.sorted() }
    val shown = remember(q) { if (q.isBlank()) all else all.filter { it.contains(q.trim(), ignoreCase = true) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LX.Card,
        title = { Text(tr("Часовой пояс", "Time zone")) },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, singleLine = true, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    placeholder = { Text("Novosibirsk, Moscow…") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(shown, key = { it }) { z ->
                        Text(
                            z, color = if (z == current) MaterialTheme.colorScheme.primary else LX.Text,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(z); onDismiss() }.padding(vertical = 10.dp, horizontal = 4.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("Закрыть", "Close")) } },
    )
}
