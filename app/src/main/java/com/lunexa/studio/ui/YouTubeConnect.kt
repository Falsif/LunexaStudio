package com.lunexa.studio.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.lunexa.studio.data.Repository
import com.lunexa.studio.data.YT_SCOPES
import com.lunexa.studio.data.YouTubeApi
import com.lunexa.studio.data.YouTubePublic
import kotlinx.coroutines.launch

/**
 * Официальный вход Google (Authorization API из Google Play services).
 * Запрашиваются только права на чтение канала и аналитики. Пароли приложение не видит и не хранит;
 * токен доступа живёт только в памяти.
 */
class YouTubeConnector(
    private val activity: Activity,
    private val repo: Repository,
    private val langProvider: () -> String,
) {
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
    var isError by mutableStateOf(false)
        private set

    internal var launcher: ActivityResultLauncher<IntentSenderRequest>? = null

    private val en get() = langProvider() == "en"

    /** interactive = true — можно показать окно выбора аккаунта/согласия. */
    fun connect(interactive: Boolean = true) {
        if (busy) return
        busy = true
        message = null
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(YT_SCOPES.map { Scope(it) })
            .build()
        Identity.getAuthorizationClient(activity).authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    val pi = result.pendingIntent
                    if (interactive && pi != null && launcher != null) {
                        launcher?.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                    } else {
                        fail(if (en) "Sign-in required — tap “Connect”." else "Нужен вход — нажмите «Подключить».")
                    }
                } else {
                    onResult(result)
                }
            }
            .addOnFailureListener { e -> fail(describe(e)) }
    }

    internal fun onActivityResult(resultCode: Int, data: android.content.Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            fail(if (en) "Sign-in cancelled." else "Вход отменён.")
            return
        }
        try {
            onResult(Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data))
        } catch (e: ApiException) {
            fail(describe(e))
        }
    }

    private fun onResult(result: AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrEmpty()) { fail(if (en) "Google did not return access." else "Google не выдал доступ."); return }
        repo.scope.launch {
            try {
                val c = repo.youtubeSync(token)
                isError = false
                message = if (en) "Synced: ${c.title}" else "Синхронизировано: ${c.title}"
            } catch (e: Exception) {
                isError = true
                message = YouTubeApi.explain(e, langProvider())
            } finally {
                busy = false
            }
        }
    }

    /** Подключение по ссылке на канал — без входа в Google. */
    fun connectPublic(input: String) {
        if (busy) return
        busy = true
        message = null
        repo.scope.launch {
            try {
                val c = repo.youtubePublicSync(input)
                isError = false
                message = if (en) "Channel connected: ${c.title}" else "Канал подключён: ${c.title}"
            } catch (e: Exception) {
                isError = true
                message = YouTubePublic.explain(e, langProvider())
            } finally {
                busy = false
            }
        }
    }

    /** Обновить данные тем же способом, каким канал был подключён. */
    fun refresh() {
        if (repo.youtube.value?.source == "public") {
            repo.scope.launch {
                val input = repo.settingsNow().youtubeChannelInput
                if (input.isNotBlank()) connectPublic(input) else fail(if (en) "Channel link is missing." else "Нет ссылки на канал.")
            }
        } else {
            connect()
        }
    }

    fun disconnect() {
        repo.scope.launch {
            repo.youtubeDisconnect()
            isError = false
            message = if (en) "Channel disconnected." else "Канал отключён."
        }
    }

    private fun fail(text: String) {
        busy = false
        isError = true
        message = text
    }

    private fun describe(e: Exception): String {
        val code = (e as? ApiException)?.statusCode
        return when (code) {
            CommonStatusCodes.DEVELOPER_ERROR ->
                if (en) "App isn't registered in Google Cloud (package/SHA-1). See the setup guide in README."
                else "Приложение не зарегистрировано в Google Cloud (пакет/SHA-1). См. инструкцию в README."
            CommonStatusCodes.NETWORK_ERROR -> if (en) "No internet connection." else "Нет подключения к интернету."
            CommonStatusCodes.CANCELED -> if (en) "Sign-in cancelled." else "Вход отменён."
            else -> (if (en) "Google sign-in error" else "Ошибка входа Google") + (code?.let { " ($it)" } ?: "") + ": " + (e.message ?: "")
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun rememberYouTubeConnector(): YouTubeConnector? {
    val context = LocalContext.current
    val repo = LocalRepo.current
    val lang = LocalLang.current
    val activity = context.findActivity() ?: return null
    val langHolder = remember { arrayOf(lang) }
    langHolder[0] = lang
    val connector = remember(activity) { YouTubeConnector(activity, repo) { langHolder[0] } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        connector.onActivityResult(res.resultCode, res.data)
    }
    connector.launcher = launcher
    return connector
}
