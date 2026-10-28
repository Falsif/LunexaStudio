package com.lunexa.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lunexa.studio.data.Analysis
import com.lunexa.studio.data.Fmt
import com.lunexa.studio.data.localeOf
import com.lunexa.studio.ui.BarList
import com.lunexa.studio.ui.EmptyState
import com.lunexa.studio.ui.LX
import com.lunexa.studio.ui.LocalLang
import com.lunexa.studio.ui.LocalRepo
import com.lunexa.studio.ui.LocalZone
import com.lunexa.studio.ui.LxCard
import com.lunexa.studio.ui.LxScaffold
import com.lunexa.studio.ui.LxTopBar
import com.lunexa.studio.ui.SectionTitle
import com.lunexa.studio.ui.switchTab
import com.lunexa.studio.ui.Routes
import com.lunexa.studio.ui.tr
import java.time.format.TextStyle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalysisScreen(nav: NavHostController) {
    val repo = LocalRepo.current
    val lang = LocalLang.current
    val zone = LocalZone.current
    val yt by repo.youtube.collectAsStateWithLifecycle()
    val pubs by repo.publications.collectAsStateWithLifecycle(emptyList())
    val games by repo.games.collectAsStateWithLifecycle(emptyList())
    val items = remember(yt, pubs, games, zone) { Analysis.items(yt, pubs, games, zone) }
    var compare by rememberSaveable { mutableStateOf(listOf<Int>()) }

    LxScaffold(topBar = { LxTopBar(tr("Что работает лучше", "What works best"), onBack = { nav.popBackStack() }) }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (items.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Outlined.Insights, tr("Нет данных для анализа", "Nothing to analyze yet"),
                        tr("Подключите канал в «Аналитике» или отметьте публикации как «Опубликовано» с просмотрами.",
                            "Connect your channel in Analytics or mark uploads as Published with views."),
                        action = tr("Открыть аналитику", "Open analytics"), onAction = { nav.switchTab(Routes.ANALYTICS) },
                    )
                }
                return@LazyColumn
            }
            item {
                Text(
                    tr("Выводы построены только по реальным данным: ${items.size} роликов. Чем больше роликов, тем точнее.",
                        "Conclusions use real data only: ${items.size} videos. More videos — more accurate."),
                    style = MaterialTheme.typography.bodySmall, color = LX.Muted,
                )
            }
            item { SectionTitle(tr("Выводы", "Takeaways")) }
            item {
                LxCard {
                    Analysis.insights(items, games, lang).forEach { ins ->
                        Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                            Icon(
                                when (ins.good) { true -> Icons.Outlined.ThumbUp; false -> Icons.Outlined.ThumbDown; null -> Icons.Outlined.Info },
                                null, tint = when (ins.good) { true -> LX.Ok; false -> LX.Accent; null -> LX.Cyan },
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(ins.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            val shorts = items.filter { it.isShort }
            val longs = items.filter { !it.isShort }
            if (shorts.isNotEmpty() || longs.isNotEmpty()) {
                item { SectionTitle(tr("Shorts vs длинные · среднее", "Shorts vs long · average")) }
                item {
                    LxCard {
                        val rows = listOfNotNull(
                            shorts.takeIf { it.isNotEmpty() }?.let { "Shorts · ${it.size}" to Analysis.avg(it) },
                            longs.takeIf { it.isNotEmpty() }?.let { trA(lang, "Длинные", "Long") + " · ${it.size}" to Analysis.avg(it) },
                        )
                        BarList(rows) { i -> if (rows[i].first.startsWith("Shorts")) LX.Accent else LX.Warn }
                    }
                }
            }

            val byGame = Analysis.byGame(items, games)
            item { SectionTitle(tr("Игры · среднее на ролик", "Games · average per video")) }
            item {
                LxCard {
                    if (byGame.isEmpty()) Text(
                        tr("Свяжите публикации с играми или добавьте игры в библиотеку — названия игр ищутся и в заголовках роликов.",
                            "Link uploads to games or add games to the library — game names are also matched in video titles."),
                        color = LX.Muted, style = MaterialTheme.typography.bodySmall,
                    ) else BarList(byGame.map { "${it.first} · ${it.third}" to it.second }) { LX.Cyan }
                }
            }

            val wd = Analysis.byWeekday(items)
            if (wd.isNotEmpty()) {
                item { SectionTitle(tr("Дни публикации · среднее", "Publish days · average")) }
                item {
                    LxCard { BarList(wd.map { it.first.getDisplayName(TextStyle.FULL_STANDALONE, localeOf(lang)) to it.second }) { LX.Warn } }
                }
            }

            val sorted = items.sortedByDescending { it.views }
            item { SectionTitle(tr("Лучшие названия", "Best titles")) }
            item { LxCard { BarList(sorted.take(5).map { it.title to it.views }) { LX.Ok } } }
            if (sorted.size > 5) {
                item { SectionTitle(tr("Слабее всего", "Weakest")) }
                item { LxCard { BarList(sorted.takeLast(minOf(5, sorted.size - 5)).reversed().map { it.title to it.views }) { LX.Muted } } }
            }

            item { SectionTitle(tr("Сравнить ролики", "Compare videos")) }
            item {
                LxCard {
                    Text(tr("Выберите от 2 до 4 роликов", "Pick 2 to 4 videos"), style = MaterialTheme.typography.bodySmall, color = LX.Muted)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sorted.take(20).forEachIndexed { i, it ->
                            val sel = i in compare
                            FilterChip(
                                selected = sel,
                                onClick = { compare = if (sel) compare - i else if (compare.size < 4) compare + i else compare },
                                label = { Text(it.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(140.dp)) },
                            )
                        }
                    }
                    val chosen = compare.mapNotNull { sorted.getOrNull(it) }
                    if (chosen.size >= 2) {
                        Spacer(Modifier.height(10.dp))
                        BarList(chosen.map { it.title to it.views }) { i -> if (chosen[i].isShort) LX.Accent else LX.Warn }
                        Spacer(Modifier.height(8.dp))
                        chosen.forEach { c ->
                            val rate = if (c.likes != null && c.views > 0) " · " + trA(lang, "лайки", "likes") + " ${c.likes} (" + "%.1f".format(c.likes * 100.0 / c.views) + "%)" else ""
                            Text("• ${c.title}: ${Fmt.number(c.views)}$rate", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun trA(lang: String, ru: String, en: String) = if (lang == "en") en else ru
