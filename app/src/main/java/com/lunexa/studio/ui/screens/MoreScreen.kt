package com.lunexa.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.lunexa.studio.ui.LX
import com.lunexa.studio.ui.LocalRepo
import com.lunexa.studio.ui.LxCard
import com.lunexa.studio.ui.LxScaffold
import com.lunexa.studio.ui.LxTopBar
import com.lunexa.studio.ui.Pill
import com.lunexa.studio.ui.Routes
import com.lunexa.studio.ui.SectionTitle
import com.lunexa.studio.ui.tr

@Composable
fun MoreScreen(nav: NavHostController) {
    val repo = LocalRepo.current
    val games by repo.games.collectAsStateWithLifecycle(emptyList())
    val tasks by repo.tasks.collectAsStateWithLifecycle(emptyList())
    val open = tasks.count { !it.done }

    LxScaffold(topBar = { LxTopBar(tr("Ещё", "More")) }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { MoreItem(Icons.Outlined.SportsEsports, tr("Библиотека игр", "Game library"), tr("Игр: ${games.size}", "Games: ${games.size}")) { nav.navigate(Routes.GAMES) } }
            item { MoreItem(Icons.Outlined.TaskAlt, tr("Задачи", "Tasks"), tr("Активных: $open", "Open: $open")) { nav.navigate(Routes.TASKS) } }
            item { MoreItem(Icons.Outlined.Settings, tr("Настройки", "Settings"), tr("Язык, часовой пояс, уведомления, резервная копия", "Language, time zone, notifications, backup")) { nav.navigate(Routes.SETTINGS) } }

            item { SectionTitle(tr("В разработке", "Coming next")) }
            item { MoreItem(Icons.Outlined.AutoAwesome, tr("ИИ-помощник", "AI assistant"), tr("Идеи, названия, описания, перевод", "Ideas, titles, descriptions, translation"), stage = 3) }
            item { MoreItem(Icons.Outlined.Flag, tr("Цели и достижения", "Goals & achievements"), tr("Цель по подписчикам уже есть на главной", "Subscriber goal is already on Home"), stage = 3) }
            item { MoreItem(Icons.Outlined.Payments, tr("Монетизация", "Monetization"), tr("Прогресс к YouTube Partner Program", "Progress to YouTube Partner Program"), stage = 3) }
            item { MoreItem(Icons.Outlined.Tag, tr("SEO и тексты", "SEO & copy"), tr("Шаблоны описаний, хештеги, чек-лист", "Description templates, hashtags, checklist"), stage = 3) }
        }
    }
}

@Composable
private fun MoreItem(icon: ImageVector, title: String, sub: String, stage: Int? = null, onClick: (() -> Unit)? = null) {
    LxCard(
        modifier = if (stage != null) Modifier.alpha(0.55f) else Modifier,
        onClick = onClick, padding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = LX.Muted)
            }
            if (stage != null) Pill(tr("Этап $stage", "Stage $stage"), LX.Muted)
            else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = LX.Muted)
        }
    }
}
