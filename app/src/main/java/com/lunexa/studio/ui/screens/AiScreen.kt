package com.lunexa.studio.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lunexa.studio.data.AiClient
import com.lunexa.studio.data.ContentType
import com.lunexa.studio.data.Fmt
import com.lunexa.studio.data.Idea
import com.lunexa.studio.data.PubStatus
import com.lunexa.studio.data.SeoItem
import com.lunexa.studio.data.SeoKind
import com.lunexa.studio.ui.DropdownField
import com.lunexa.studio.ui.LX
import com.lunexa.studio.ui.LocalLang
import com.lunexa.studio.ui.LocalRepo
import com.lunexa.studio.ui.LocalSettings
import com.lunexa.studio.ui.LocalZone
import com.lunexa.studio.ui.LxCard
import com.lunexa.studio.ui.LxScaffold
import com.lunexa.studio.ui.LxTopBar
import com.lunexa.studio.ui.tr
import kotlinx.coroutines.launch

private enum class AiTask(val ru: String, val en: String, val hintRu: String, val hintEn: String, val needsData: Boolean = false) {
    IDEAS("Идеи для видео", "Video ideas", "Игра или тема (необязательно)", "Game or topic (optional)"),
    SHORTS("Идеи для Shorts", "Shorts ideas", "Игра или момент из игры", "Game or in-game moment"),
    TITLES("Названия на английском", "English titles", "О чём ролик (можно по-русски)", "What the video is about"),
    DESCRIPTION("Описание ролика", "Video description", "О чём ролик, номер части и т. п.", "What it's about, part number, etc."),
    HASHTAGS("Хештеги", "Hashtags", "Игра и тема ролика", "Game and video topic"),
    POST("Текст для публикации", "Post text", "Для YouTube-сообщества или TikTok: что анонсируем", "For community/TikTok: what to announce"),
    TRANSLATE("Перевод RU → EN", "Translate RU → EN", "Вставьте название или описание на русском", "Paste a Russian title or description"),
    THEME("Анализ тематики канала", "Channel theme analysis", "Вопрос (необязательно)", "Question (optional)", true),
    GROWTH("Рекомендации по развитию", "Growth advice", "Что хочется улучшить (необязательно)", "What to improve (optional)", true),
    EXPLAIN("Объяснить аналитику", "Explain analytics", "Что непонятно (необязательно)", "What's unclear (optional)", true),
    PLAN("Улучшить контент-план", "Improve content plan", "Пожелания (необязательно)", "Wishes (optional)", true),
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiScreen(nav: NavHostController) {
    val repo = LocalRepo.current
    val s = LocalSettings.current
    val lang = LocalLang.current
    val zone = LocalZone.current
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val games by repo.games.collectAsStateWithLifecycle(emptyList())
    val ideas by repo.ideas.collectAsStateWithLifecycle(emptyList())
    val pubs by repo.publications.collectAsStateWithLifecycle(emptyList())
    val yt by repo.youtube.collectAsStateWithLifecycle()

    var taskName by rememberSaveable { mutableStateOf(AiTask.IDEAS.name) }
    val task = AiTask.valueOf(taskName)
    var input by rememberSaveable { mutableStateOf("") }
    var gameId by rememberSaveable { mutableStateOf(0L) }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showSetup by remember { mutableStateOf(false) }
    var keySaved by remember { mutableStateOf(repo.hasAiKey()) }
    val configured = s.aiBaseUrl.isNotBlank() && s.aiModel.isNotBlank() && keySaved

    fun buildPrompt(): Pair<String, String> {
        val en = lang == "en"
        val channel = yt?.title ?: s.channelName.ifBlank { "LUNEXA" }
        val system = buildString {
            append("You are an assistant for the gaming YouTube channel \"$channel\". ")
            append("The creator records gameplay with no voice and no face (no commentary), publishes Shorts daily, long videos and TikTok, ")
            append("and targets an international audience. The creator is not fluent in English: give anything meant for publishing (titles, descriptions, hashtags, posts) in natural English, ")
            append(if (en) "and explanations in English. " else "and all explanations and comments in Russian. ")
            append("Be concrete and concise. Never invent statistics; if data is missing, say so.")
        }
        val game = games.firstOrNull { it.id == gameId }?.title
        val data = if (!task.needsData) "" else buildString {
            append("\n\nChannel data (real):\n")
            yt?.let { c ->
                append("Subscribers: ${if (c.hiddenSubscribers) "hidden" else c.subscribers}, videos: ${c.videoCount}.\n")
                append("Recent videos (title — views — type):\n")
                c.videos.take(15).forEach { append("- ${it.title} — ${it.views} — ${if (it.isShort) "Shorts" else "long"}\n") }
                if (c.daily.isNotEmpty()) {
                    val last28 = c.daily.takeLast(28)
                    append("Last 28 days: views ${last28.sumOf { it.views }}, watch minutes ${last28.sumOf { it.minutes }}, subs +${last28.sumOf { it.subsGained }} −${last28.sumOf { it.subsLost }}.\n")
                }
                if (c.traffic.isNotEmpty()) append("Traffic sources (28d): ${c.traffic.take(5).joinToString { "${it.key} ${it.value.toLong()}" }}.\n")
            } ?: append("Channel is not connected — no channel statistics.\n")
            if (games.isNotEmpty()) append("Games in library: ${games.joinToString { "${it.title} (${it.status})" }}.\n")
            val upcoming = pubs.filter { it.status == PubStatus.PLANNED || it.status == PubStatus.READY }.sortedBy { it.scheduledAt }.take(10)
            if (upcoming.isNotEmpty()) append("Upcoming schedule: ${upcoming.joinToString { "${Fmt.dateTime(it.scheduledAt, zone, "en")} ${it.type} \"${it.title}\"" }}.\n")
            if (ideas.isNotEmpty()) append("Saved ideas: ${ideas.take(10).joinToString { it.title }}.\n")
        }
        val what = input.trim().ifBlank { "-" }
        val g = game?.let { "Game: $it. " } ?: ""
        val user = when (task) {
            AiTask.IDEAS -> "${g}Suggest 8 ideas for gameplay videos without commentary that can attract an international audience. Topic/wishes: $what. For each: an English working title and one line why it can work."
            AiTask.SHORTS -> "${g}Suggest 10 YouTube Shorts ideas (under 60 seconds, no voice) from gameplay moments. Context: $what. For each: an English title with hook and what to show in the first 2 seconds."
            AiTask.TITLES -> "${g}Write 10 catchy, honest English YouTube titles (under 70 characters) for this video: $what. Mark the 3 best and explain briefly."
            AiTask.DESCRIPTION -> "${g}Write an English YouTube description for this no-commentary gameplay video: $what. Structure: 2-line hook, what happens, game name, call to subscribe, 3-5 hashtags."
            AiTask.HASHTAGS -> "${g}Suggest 15 relevant hashtags (English) for: $what. Split into: must-use (3), broad, niche. Keep them realistic."
            AiTask.POST -> "${g}Write a short English post for YouTube Community and a TikTok caption announcing: $what."
            AiTask.TRANSLATE -> "Translate into natural, catchy English suitable for YouTube (keep game names unchanged). Give 2 variants:\n$what"
            AiTask.THEME -> "Analyze the channel's theme and positioning from the data. What niche is forming, what is consistent, what is scattered? Question: $what$data"
            AiTask.GROWTH -> "Give 7 concrete recommendations to grow this channel based on the data. Focus: $what$data"
            AiTask.EXPLAIN -> "Explain these channel numbers in simple words for a beginner: what is good, what is worrying, what to watch next. Question: $what$data"
            AiTask.PLAN -> "Review the upcoming content plan and suggest improvements (games, formats, timing, series). Wishes: $what$data"
        }
        return system to user
    }

    fun run() {
        val key = repo.aiKey()
        if (key.isNullOrBlank()) { showSetup = true; return }
        busy = true; error = null
        val (sys, user) = buildPrompt()
        scope.launch {
            try {
                result = AiClient.chat(s.aiBaseUrl, s.aiModel, key, sys, user)
            } catch (e: Exception) {
                error = AiClient.explain(e, lang)
            } finally {
                busy = false
            }
        }
    }

    LxScaffold(
        topBar = {
            LxTopBar(tr("ИИ-помощник", "AI assistant"), onBack = { nav.popBackStack() }, actions = {
                IconButton(onClick = { showSetup = true }) { Icon(Icons.Outlined.Settings, tr("Настройки ИИ", "AI settings")) }
            })
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!configured) {
                LxCard {
                    Text(tr("ИИ не настроен", "AI isn't set up"), style = MaterialTheme.typography.titleSmall)
                    Text(
                        tr("Подключите любой сервис с OpenAI-совместимым API: укажите адрес, модель и свой ключ. Без настройки приложение не будет выдавать шаблоны за ответы ИИ.",
                            "Connect any service with an OpenAI-compatible API: set the URL, model and your key. Without it the app won't pass templates off as AI answers."),
                        style = MaterialTheme.typography.bodySmall, color = LX.Muted,
                    )
                    TextButton(onClick = { showSetup = true }) { Text(tr("Настроить", "Set up")) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AiTask.entries.forEach { t ->
                    FilterChip(selected = t == task, onClick = { taskName = t.name }, label = { Text(if (lang == "en") t.en else t.ru) })
                }
            }
            if (games.isNotEmpty() && !task.needsData && task != AiTask.TRANSLATE) {
                DropdownField(tr("Игра", "Game"), listOf(0L) + games.map { it.id }, gameId,
                    { id -> if (id == 0L) (if (lang == "en") "Any" else "Любая") else games.firstOrNull { it.id == id }?.title ?: "?" }, { gameId = it })
            }
            OutlinedTextField(
                input, { input = it.take(3000) }, minLines = if (task == AiTask.TRANSLATE) 4 else 2,
                label = { Text(if (lang == "en") task.hintEn else task.hintRu) }, modifier = Modifier.fillMaxWidth(),
            )
            if (task.needsData) Text(
                tr("В запрос будут отправлены данные канала: названия и просмотры роликов, расписание, идеи.", "Channel data will be sent: video titles and views, schedule, ideas."),
                style = MaterialTheme.typography.labelSmall, color = LX.Muted,
            )
            Button(onClick = { run() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                else { Icon(Icons.Outlined.AutoAwesome, null); Spacer(Modifier.width(8.dp)) }
                Text(if (busy) tr("Думаю…", "Thinking…") else tr("Сгенерировать", "Generate"))
            }
            error?.let { Text(it, color = LX.Accent, style = MaterialTheme.typography.bodySmall) }
            result?.let { r ->
                LxCard {
                    Text(tr("Ответ ИИ · ", "AI answer · ") + s.aiModel, style = MaterialTheme.typography.labelMedium, color = LX.Muted)
                    Spacer(Modifier.height(6.dp))
                    SelectionContainer { Text(r, style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            clipboard.setText(AnnotatedString(r))
                            Toast.makeText(ctx, if (lang == "en") "Copied" else "Скопировано", Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.weight(1f)) { Text(tr("Копировать", "Copy"), maxLines = 1) }
                        OutlinedButton(onClick = {
                            val firstLine = r.lineSequence().map { it.trim().trimStart('-', '*', '•', ' ', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '.', ')') }
                                .firstOrNull { it.isNotBlank() }?.take(120) ?: "AI idea"
                            repo.launch {
                                saveIdea(Idea(title = firstLine, description = r, gameId = gameId.takeIf { it != 0L },
                                    type = if (task == AiTask.SHORTS) ContentType.SHORTS else ContentType.LONG))
                            }
                            Toast.makeText(ctx, if (lang == "en") "Saved to ideas" else "Сохранено в идеи", Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.weight(1f)) { Text(tr("В идеи", "To ideas"), maxLines = 1) }
                        OutlinedButton(onClick = {
                            val kind = when (task) { AiTask.TITLES -> SeoKind.TITLE; AiTask.HASHTAGS -> SeoKind.HASHTAGS; else -> SeoKind.DESCRIPTION }
                            repo.launch { saveSeo(SeoItem(kind = kind, title = "AI", text = r, gameId = gameId.takeIf { it != 0L })) }
                            Toast.makeText(ctx, if (lang == "en") "Saved to SEO" else "Сохранено в SEO", Toast.LENGTH_SHORT).show()
                        }, modifier = Modifier.weight(1f)) { Text("SEO", maxLines = 1) }
                    }
                }
            }
        }
    }

    if (showSetup) AiSetupDialog(onDismiss = { showSetup = false; keySaved = repo.hasAiKey() })
}

@Composable
private fun AiSetupDialog(onDismiss: () -> Unit) {
    val repo = LocalRepo.current
    val s = LocalSettings.current
    var url by remember { mutableStateOf(s.aiBaseUrl) }
    var model by remember { mutableStateOf(s.aiModel) }
    var key by remember { mutableStateOf("") }
    val hasKey = remember { repo.hasAiKey() }
    val urlOk = url.isBlank() || url.trim().startsWith("https://") || url.trim().startsWith("http://")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LX.Card,
        title = { Text(tr("Настройки ИИ", "AI settings")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    tr("Подойдёт любой сервис с OpenAI-совместимым API (Chat Completions). Адрес и модель смотрите в документации сервиса. Проверьте, что сервис доступен в вашей стране. Ключ шифруется в хранилище Android и отправляется только по указанному адресу.",
                        "Any service with an OpenAI-compatible (Chat Completions) API works. Find the URL and model in the service docs. Make sure it's available in your country. The key is encrypted in Android Keystore and sent only to that URL."),
                    style = MaterialTheme.typography.bodySmall, color = LX.Muted,
                )
                OutlinedTextField(url, { url = it.take(300) }, singleLine = true, isError = !urlOk,
                    label = { Text(tr("Адрес API", "API URL")) }, placeholder = { Text("https://…/v1") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it.take(120) }, singleLine = true,
                    label = { Text(tr("Модель", "Model")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(key, { key = it.take(500) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    label = { Text(tr("API-ключ", "API key")) },
                    placeholder = { Text(if (hasKey) tr("сохранён — оставьте пустым", "saved — leave empty") else "sk-…") },
                    modifier = Modifier.fillMaxWidth())
                if (hasKey) TextButton(onClick = { repo.setAiKey(null); onDismiss() }) { Text(tr("Удалить сохранённый ключ", "Delete saved key"), color = LX.Accent) }
            }
        },
        confirmButton = {
            TextButton(enabled = urlOk, onClick = {
                val u = url.trim(); val m = model.trim()
                repo.launch { updateSettings { it.copy(aiBaseUrl = u, aiModel = m) } }
                if (key.isNotBlank()) repo.setAiKey(key.trim())
                onDismiss()
            }) { Text(tr("Сохранить", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена", "Cancel")) } },
    )
}
