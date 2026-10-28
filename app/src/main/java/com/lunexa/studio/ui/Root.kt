package com.lunexa.studio.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lunexa.studio.data.AppSettings
import com.lunexa.studio.data.Repository
import com.lunexa.studio.data.zoneOf
import com.lunexa.studio.notify.Notifier
import com.lunexa.studio.ui.screens.AnalyticsScreen
import com.lunexa.studio.ui.screens.CalendarScreen
import com.lunexa.studio.ui.screens.GameEditScreen
import com.lunexa.studio.ui.screens.GamesScreen
import com.lunexa.studio.ui.screens.HomeScreen
import com.lunexa.studio.ui.screens.IdeaEditScreen
import com.lunexa.studio.ui.screens.IdeasScreen
import com.lunexa.studio.ui.screens.MoreScreen
import com.lunexa.studio.ui.screens.PublicationEditScreen
import com.lunexa.studio.ui.screens.SettingsScreen
import com.lunexa.studio.ui.screens.SnapshotEditScreen
import com.lunexa.studio.ui.screens.TaskEditScreen
import com.lunexa.studio.ui.screens.TasksScreen

object Routes {
    const val HOME = "home"
    const val ANALYTICS = "analytics"
    const val CALENDAR = "calendar"
    const val IDEAS = "ideas"
    const val MORE = "more"
    const val GAMES = "games"
    const val TASKS = "tasks"
    const val SETTINGS = "settings"
    const val GOALS = "goals"
    const val ANALYSIS = "analysis"
    const val MONETIZATION = "monetization"
    const val SEO = "seo"
    const val AI = "ai"

    fun game(id: Long) = "game/$id"
    fun idea(id: Long, gameId: Long = 0) = "idea/$id?game=$gameId"
    fun pub(id: Long, epochDay: Long = Long.MIN_VALUE, ideaId: Long = 0, gameId: Long = 0) =
        "pub/$id?day=$epochDay&idea=$ideaId&game=$gameId"
    fun task(id: Long) = "task/$id"
    fun snapshot(id: Long) = "snapshot/$id"
}

private data class Tab(val route: String, val icon: ImageVector, val ru: String, val en: String)

private val tabs = listOf(
    Tab(Routes.HOME, Icons.Outlined.Home, "Главная", "Home"),
    Tab(Routes.ANALYTICS, Icons.Outlined.BarChart, "Аналитика", "Analytics"),
    Tab(Routes.CALENDAR, Icons.Outlined.CalendarMonth, "Календарь", "Calendar"),
    Tab(Routes.IDEAS, Icons.Outlined.Lightbulb, "Идеи", "Ideas"),
    Tab(Routes.MORE, Icons.Outlined.MoreHoriz, "Ещё", "More"),
)

fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun LunexaRoot(repo: Repository) {
    val settings by repo.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val zone = remember(settings.timezone) { zoneOf(settings.timezone) }

    // Однократный запрос разрешения на уведомления (Android 13+).
    val context = LocalContext.current
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // Тихое обновление открытых данных канала при запуске (не чаще раза в 3 часа).
    LaunchedEffect(Unit) {
        repo.launch {
            val input = settingsNow().youtubeChannelInput
            val cache = youtube.value
            if (input.isNotBlank() && cache?.source == "public" && System.currentTimeMillis() - cache.syncedAt > 3 * 3_600_000L) {
                runCatching { youtubePublicSync(input) }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !Notifier.canPost(context)) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    CompositionLocalProvider(
        LocalRepo provides repo,
        LocalSettings provides settings,
        LocalLang provides settings.language,
        LocalZone provides zone,
    ) {
        LunexaTheme(settings.accent) {
            val nav = rememberNavController()
            val entry by nav.currentBackStackEntryAsState()
            val current = entry?.destination?.route
            val showBar = tabs.any { it.route == current }
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (showBar) NavigationBar(containerColor = LX.Card, tonalElevation = 0.dp) {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = current == t.route,
                                onClick = { nav.switchTab(t.route) },
                                icon = { Icon(t.icon, null) },
                                label = { Text(tr(t.ru, t.en), maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = LX.Text,
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                    unselectedIconColor = LX.Muted,
                                    unselectedTextColor = LX.Muted,
                                ),
                            )
                        }
                    }
                },
            ) { inner ->
                NavHost(
                    navController = nav,
                    startDestination = Routes.HOME,
                    modifier = Modifier.padding(inner),
                    enterTransition = { fadeIn() },
                    exitTransition = { fadeOut() },
                ) {
                    composable(Routes.HOME) { HomeScreen(nav) }
                    composable(Routes.ANALYTICS) { AnalyticsScreen(nav) }
                    composable(Routes.CALENDAR) { CalendarScreen(nav) }
                    composable(Routes.IDEAS) { IdeasScreen(nav) }
                    composable(Routes.MORE) { MoreScreen(nav) }
                    composable(Routes.GAMES) { GamesScreen(nav) }
                    composable(Routes.TASKS) { TasksScreen(nav) }
                    composable(Routes.SETTINGS) { SettingsScreen(nav) }
                    composable(Routes.GOALS) { com.lunexa.studio.ui.screens.GoalsScreen(nav) }
                    composable(Routes.ANALYSIS) { com.lunexa.studio.ui.screens.AnalysisScreen(nav) }
                    composable(Routes.MONETIZATION) { com.lunexa.studio.ui.screens.MonetizationScreen(nav) }
                    composable(Routes.SEO) { com.lunexa.studio.ui.screens.SeoScreen(nav) }
                    composable(Routes.AI) { com.lunexa.studio.ui.screens.AiScreen(nav) }
                    composable("game/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                        GameEditScreen(nav, it.arguments?.getLong("id") ?: 0L)
                    }
                    composable(
                        "idea/{id}?game={game}",
                        listOf(
                            navArgument("id") { type = NavType.LongType },
                            navArgument("game") { type = NavType.LongType; defaultValue = 0L },
                        ),
                    ) {
                        IdeaEditScreen(nav, it.arguments?.getLong("id") ?: 0L, it.arguments?.getLong("game") ?: 0L)
                    }
                    composable(
                        "pub/{id}?day={day}&idea={idea}&game={game}",
                        listOf(
                            navArgument("id") { type = NavType.LongType },
                            navArgument("day") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
                            navArgument("idea") { type = NavType.LongType; defaultValue = 0L },
                            navArgument("game") { type = NavType.LongType; defaultValue = 0L },
                        ),
                    ) {
                        val a = it.arguments
                        PublicationEditScreen(
                            nav,
                            id = a?.getLong("id") ?: 0L,
                            epochDay = a?.getLong("day") ?: Long.MIN_VALUE,
                            ideaId = a?.getLong("idea") ?: 0L,
                            gameId = a?.getLong("game") ?: 0L,
                        )
                    }
                    composable("task/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                        TaskEditScreen(nav, it.arguments?.getLong("id") ?: 0L)
                    }
                    composable("snapshot/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                        SnapshotEditScreen(nav, it.arguments?.getLong("id") ?: 0L)
                    }
                }
            }
        }
    }
}

