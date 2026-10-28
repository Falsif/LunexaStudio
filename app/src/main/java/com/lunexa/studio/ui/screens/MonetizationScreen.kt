package com.lunexa.studio.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lunexa.studio.data.Currency
import com.lunexa.studio.data.Fmt
import com.lunexa.studio.data.Income
import com.lunexa.studio.data.PubStatus
import com.lunexa.studio.data.toLocalDate
import com.lunexa.studio.ui.BarList
import com.lunexa.studio.ui.ChipsRow
import com.lunexa.studio.ui.ConfirmDialog
import com.lunexa.studio.ui.DateField
import com.lunexa.studio.ui.GoalBar
import com.lunexa.studio.ui.LX
import com.lunexa.studio.ui.LocalLang
import com.lunexa.studio.ui.LocalRepo
import com.lunexa.studio.ui.LocalSettings
import com.lunexa.studio.ui.LocalZone
import com.lunexa.studio.ui.LxCard
import com.lunexa.studio.ui.LxScaffold
import com.lunexa.studio.ui.LxTopBar
import com.lunexa.studio.ui.NumberField
import com.lunexa.studio.ui.Pill
import com.lunexa.studio.ui.SectionTitle
import com.lunexa.studio.ui.tr
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private const val YPP_HELP = "https://support.google.com/youtube/answer/72851"

private fun money(v: Double, c: Currency) = "%,.0f".format(java.util.Locale.US, v).replace(',', ' ') + if (c == Currency.RUB) " ₽" else " $"

@Composable
fun MonetizationScreen(nav: NavHostController) {
    val repo = LocalRepo.current
    val s = LocalSettings.current
    val lang = LocalLang.current
    val zone = LocalZone.current
    val ctx = LocalContext.current
    val yt by repo.youtube.collectAsStateWithLifecycle()
    val pubs by repo.publications.collectAsStateWithLifecycle(emptyList())
    val incomes by repo.incomes.collectAsStateWithLifecycle(emptyList())
    val n = rememberChannelNumbers()
    var editing by remember { mutableStateOf<Income?>(null) }
    var deleting by remember { mutableStateOf<Income?>(null) }
    var curName by rememberSaveable { mutableStateOf(Currency.RUB.name) }
    val cur = Currency.valueOf(curName)

    // Публикации за 90 дней: отмеченные в календаре + ролики канала
    val today = LocalDate.now(zone)
    val from90 = today.minusDays(90)
    val days90 = buildSet {
        pubs.filter { it.status == PubStatus.PUBLISHED }.forEach { add("p${it.id}" to it.scheduledAt.toLocalDate(zone)) }
        yt?.videos?.forEach { v -> runCatching { add(v.id to Instant.parse(v.publishedAt).atZone(zone).toLocalDate()) } }
    }.filter { it.second >= from90 }
    val uploads90 = days90.size.toLong()
    // Часы просмотра: из YouTube Analytics (вход Google) или вручную из Studio
    val apiHours = yt?.takeIf { it.daily.isNotEmpty() }?.daily?.sumOf { it.minutes }?.div(60)
    val watchHours = apiHours ?: s.yppWatchHours
    val subs = n.subs ?: 0

    LxScaffold(
        topBar = { LxTopBar(tr("Монетизация", "Monetization"), onBack = { nav.popBackStack() }) },
        fab = {
            FloatingActionButton(onClick = { editing = Income(dateEpochDay = today.toEpochDay(), amount = 0.0, currency = cur) }, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Outlined.Add, tr("Добавить доход", "Add income"))
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                LxCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.WarningAmber, null, tint = LX.Warn)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            tr(
                                "Важно: с августа 2024 года Google отключил AdSense-аккаунты с Россией в качестве страны — монетизация YouTube через партнёрскую программу для них недоступна. Трекер показывает прогресс к требованиям, но не гарантирует подключение. Доходы из других источников (донаты, спонсоры, другие площадки) учитываются ниже вручную.",
                                "Important: since August 2024 Google deactivated AdSense accounts with Russia as the country, so YouTube Partner Program payouts aren't available for them. This tracker shows progress toward requirements but doesn't guarantee approval. Income from other sources (donations, sponsors, other platforms) is tracked manually below.",
                            ),
                            style = MaterialTheme.typography.bodySmall, color = LX.Muted,
                        )
                    }
                }
            }

            item { SectionTitle(tr("Требования YouTube Partner Program", "YouTube Partner Program requirements")) }
            item {
                LxCard {
                    Text(tr("Уровень 1 — спонсорство, Super Thanks, магазин", "Tier 1 — memberships, Super Thanks, shopping"), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Req(tr("Подписчики", "Subscribers"), subs, 500)
                    Req(tr("Публикаций за 90 дней", "Uploads in 90 days"), uploads90, 3)
                    Req(tr("Часы просмотра за 12 мес.", "Watch hours, 12 months"), watchHours, 3_000, tr("или", "or"))
                    Req(tr("Просмотры Shorts за 90 дн.", "Shorts views, 90 days"), s.yppShortsViews90, 3_000_000)
                    Spacer(Modifier.height(14.dp))
                    Text(tr("Уровень 2 — доход от рекламы", "Tier 2 — ad revenue"), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Req(tr("Подписчики", "Subscribers"), subs, 1_000)
                    Req(tr("Часы просмотра за 12 мес.", "Watch hours, 12 months"), watchHours, 4_000, tr("или", "or"))
                    Req(tr("Просмотры Shorts за 90 дн.", "Shorts views, 90 days"), s.yppShortsViews90, 10_000_000)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        tr("Часы и просмотры Shorts — разные пути, они не складываются. Учитываются только публичные ролики. По сообщениям сторонних изданий, с 1 февраля 2027 пороги уровня 2 для новых заявок могут вырасти — проверяйте Справку YouTube.",
                            "Watch hours and Shorts views are separate paths and don't add up. Only public videos count. Third-party outlets report the Tier 2 bar for new applicants may rise from 1 Feb 2027 — check YouTube Help."),
                        style = MaterialTheme.typography.labelSmall, color = LX.Muted,
                    )
                    TextButton(onClick = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(YPP_HELP))) } }) {
                        Text(tr("Открыть официальные требования", "Open official requirements"))
                    }
                }
            }
            item {
                LxCard {
                    Text(tr("Данные из YouTube Studio", "Numbers from YouTube Studio"), style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (apiHours != null) tr("Часы просмотра считаются автоматически через вход Google.", "Watch hours are calculated automatically via Google sign-in.")
                        else tr("Без входа Google эти числа YouTube не отдаёт — перепишите их из Studio → «Заработок».", "Without Google sign-in YouTube doesn't share these — copy them from Studio → Earn."),
                        style = MaterialTheme.typography.bodySmall, color = LX.Muted,
                    )
                    Spacer(Modifier.height(8.dp))
                    var h by remember(s.yppWatchHours) { mutableStateOf(s.yppWatchHours.toString()) }
                    var sv by remember(s.yppShortsViews90) { mutableStateOf(s.yppShortsViews90.toString()) }
                    if (apiHours == null) {
                        NumberField(tr("Часы просмотра за 12 мес.", "Watch hours, 12 months"), h, { v -> h = v; v.toLongOrNull()?.let { x -> repo.launch { updateSettings { it.copy(yppWatchHours = x) } } } })
                        Spacer(Modifier.height(8.dp))
                    }
                    NumberField(tr("Просмотры Shorts за 90 дней", "Shorts views, 90 days"), sv, { v -> sv = v; v.toLongOrNull()?.let { x -> repo.launch { updateSettings { it.copy(yppShortsViews90 = x) } } } })
                }
            }

            // ---------- Доходы ----------
            item { SectionTitle(tr("Доходы", "Income")) }
            item { ChipsRow(Currency.entries, cur, { if (it == Currency.RUB) "₽ RUB" else "$ USD" }, { curName = it.name }) }
            val mine = incomes.filter { it.currency == cur }
            val month = YearMonth.from(today)
            val monthItems = mine.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == month }
            val confirmed = monthItems.filter { it.confirmed }.sumOf { it.amount }
            val estimated = monthItems.filter { !it.confirmed }.sumOf { it.amount }
            val goal = if (cur == Currency.RUB) s.incomeGoalRub else s.incomeGoalUsd
            item {
                LxCard {
                    Text(tr("Этот месяц", "This month"), style = MaterialTheme.typography.labelMedium, color = LX.Muted)
                    Text(money(confirmed, cur), style = MaterialTheme.typography.headlineSmall)
                    if (estimated > 0) Text(tr("+ ожидается ", "+ expected ") + money(estimated, cur), style = MaterialTheme.typography.bodySmall, color = LX.Warn)
                    Spacer(Modifier.height(10.dp))
                    var g by remember(goal, curName) { mutableStateOf(if (goal > 0) goal.toString() else "") }
                    NumberField(tr("Цель в месяц", "Monthly goal"), g, { v ->
                        g = v
                        val x = v.toLongOrNull() ?: 0
                        repo.launch { updateSettings { if (cur == Currency.RUB) it.copy(incomeGoalRub = x) else it.copy(incomeGoalUsd = x) } }
                    }, suffix = if (cur == Currency.RUB) "₽" else "$")
                    if (goal > 0) {
                        Spacer(Modifier.height(8.dp))
                        GoalBar((confirmed / goal).toFloat())
                        Text("${((confirmed / goal) * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = LX.Muted)
                    }
                }
            }
            val months = (5 downTo 0).map { month.minusMonths(it.toLong()) }
            if (mine.isNotEmpty()) {
                item { SectionTitle(tr("По месяцам (подтверждённые)", "By month (confirmed)")) }
                item {
                    LxCard {
                        val fmt = DateTimeFormatter.ofPattern("LLL yyyy", com.lunexa.studio.data.localeOf(lang))
                        BarList(months.map { m ->
                            fmt.format(m) to mine.filter { it.confirmed && YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == m }.sumOf { it.amount }.toLong()
                        }) { LX.Ok }
                    }
                }
                item { SectionTitle(tr("История", "History")) }
                items(mine, key = { it.id }) { i ->
                    LxCard(onClick = { editing = i }, padding = PaddingValues(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(i.source.ifBlank { tr("Без источника", "No source") }, style = MaterialTheme.typography.titleSmall)
                                Text(com.lunexa.studio.data.Fmt.shortDay(LocalDate.ofEpochDay(i.dateEpochDay), lang) + (if (i.note.isNotBlank()) " · ${i.note}" else ""),
                                    style = MaterialTheme.typography.bodySmall, color = LX.Muted, maxLines = 1)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(money(i.amount, i.currency), style = MaterialTheme.typography.titleSmall)
                                Pill(if (i.confirmed) tr("получено", "received") else tr("оценка", "estimate"), if (i.confirmed) LX.Ok else LX.Warn)
                            }
                        }
                    }
                }
            } else item {
                Text(tr("Пока нет записей. Нажмите «+», чтобы добавить доход — донаты, спонсоров, продажи и т. д.", "No entries yet. Tap “+” to add income — donations, sponsors, sales, etc."),
                    style = MaterialTheme.typography.bodySmall, color = LX.Muted)
            }
        }
    }

    editing?.let { i ->
        IncomeDialog(i, onDismiss = { editing = null }, onDelete = if (i.id != 0L) ({ deleting = i; editing = null }) else null) { repo.launch { saveIncome(it) } }
    }
    deleting?.let { i ->
        ConfirmDialog(tr("Удалить запись?", "Delete entry?"), money(i.amount, i.currency), tr("Удалить", "Delete"),
            onConfirm = { repo.launch { deleteIncome(i) } }, onDismiss = { deleting = null })
    }
}

@Composable
private fun Req(label: String, value: Long, target: Long, prefix: String? = null) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text((prefix?.let { "$it " } ?: "") + label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text("${Fmt.number(value)} / ${Fmt.number(target)}", style = MaterialTheme.typography.bodySmall, color = if (value >= target) LX.Ok else LX.Muted)
        }
        Spacer(Modifier.height(4.dp))
        GoalBar(value.toFloat() / target)
    }
}

@Composable
private fun IncomeDialog(initial: Income, onDismiss: () -> Unit, onDelete: (() -> Unit)?, onSave: (Income) -> Unit) {
    var day by remember { mutableStateOf(LocalDate.ofEpochDay(initial.dateEpochDay)) }
    var amount by remember { mutableStateOf(if (initial.amount > 0) initial.amount.toString().removeSuffix(".0") else "") }
    var cur by remember { mutableStateOf(initial.currency) }
    var source by remember { mutableStateOf(initial.source) }
    var confirmed by remember { mutableStateOf(initial.confirmed) }
    var note by remember { mutableStateOf(initial.note) }
    val a = amount.replace(',', '.').toDoubleOrNull()
    val valid = a != null && a > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LX.Card,
        title = { Text(if (initial.id == 0L) tr("Новый доход", "New income") else tr("Доход", "Income")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField(tr("Дата", "Date"), day, { if (it != null) day = it })
                OutlinedTextField(
                    amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(12) },
                    label = { Text(tr("Сумма", "Amount")) }, singleLine = true, isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
                )
                ChipsRow(Currency.entries, cur, { if (it == Currency.RUB) "₽ RUB" else "$ USD" }, { cur = it })
                OutlinedTextField(source, { source = it.take(60) }, label = { Text(tr("Источник (донаты, спонсор…)", "Source (donations, sponsor…)")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (confirmed) tr("Деньги получены", "Money received") else tr("Оценка / ожидается", "Estimate / pending"))
                        Text(tr("Оценки не входят в подтверждённый доход", "Estimates aren't counted as confirmed"), style = MaterialTheme.typography.labelSmall, color = LX.Muted)
                    }
                    Switch(confirmed, { confirmed = it })
                }
                OutlinedTextField(note, { note = it.take(200) }, label = { Text(tr("Заметка", "Note")) }, modifier = Modifier.fillMaxWidth())
                if (onDelete != null) TextButton(onClick = onDelete) { Text(tr("Удалить", "Delete"), color = LX.Accent) }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                onSave(initial.copy(dateEpochDay = day.toEpochDay(), amount = a ?: 0.0, currency = cur, source = source.trim(), confirmed = confirmed, note = note.trim()))
                onDismiss()
            }) { Text(tr("Сохранить", "Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Отмена", "Cancel")) } },
    )
}
