package com.lunexa.studio.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Переходы в официальные приложения YouTube Studio и YouTube.
 * Вход в них у пользователя уже выполнен, поэтому LUNEXA Studio не нужны пароли и ключи.
 * Если приложения нет — ссылка открывается в браузере.
 */
object Studio {
    const val STUDIO_PKG = "com.google.android.apps.youtube.creator"
    const val YOUTUBE_PKG = "com.google.android.youtube"

    private fun open(ctx: Context, url: String, pkg: String?) {
        val base = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pkg != null) {
            try {
                ctx.startActivity(Intent(base).setPackage(pkg)); return
            } catch (_: ActivityNotFoundException) { /* пробуем дальше */ }
        }
        try {
            ctx.startActivity(base)
        } catch (_: ActivityNotFoundException) { }
    }

    fun watch(ctx: Context, videoId: String) = open(ctx, "https://youtu.be/$videoId", YOUTUBE_PKG)
    fun editVideo(ctx: Context, videoId: String) = open(ctx, "https://studio.youtube.com/video/$videoId/edit", STUDIO_PKG)
    fun videoComments(ctx: Context, videoId: String) = open(ctx, "https://studio.youtube.com/video/$videoId/comments", STUDIO_PKG)
    fun videoAnalytics(ctx: Context, videoId: String) = open(ctx, "https://studio.youtube.com/video/$videoId/analytics", STUDIO_PKG)

    fun channelHome(ctx: Context, channelId: String?) =
        open(ctx, if (channelId != null) "https://studio.youtube.com/channel/$channelId" else "https://studio.youtube.com", STUDIO_PKG)
    fun channelComments(ctx: Context, channelId: String?) =
        open(ctx, if (channelId != null) "https://studio.youtube.com/channel/$channelId/comments" else "https://studio.youtube.com", STUDIO_PKG)
    fun channelAnalytics(ctx: Context, channelId: String?) =
        open(ctx, if (channelId != null) "https://studio.youtube.com/channel/$channelId/analytics" else "https://studio.youtube.com", STUDIO_PKG)

    /** Передаёт выбранное видео в приложение YouTube — откроется его экран загрузки. */
    fun uploadVideo(ctx: Context, video: Uri): Boolean {
        val send = Intent(Intent.ACTION_SEND)
            .setType("video/*")
            .putExtra(Intent.EXTRA_STREAM, video)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(Intent(send).setPackage(YOUTUBE_PKG)); true
        } catch (_: ActivityNotFoundException) {
            try { ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
            catch (_: ActivityNotFoundException) { false }
        }
    }

    /** id ролика из ссылки youtu.be/…, youtube.com/watch?v=…, /shorts/… или из самого id. */
    fun parseVideoId(input: String): String? {
        val t = input.trim()
        if (Regex("""^[\w-]{11}$""").matches(t)) return t
        return Regex("""(?:youtu\.be/|v=|/shorts/|/live/|/video/)([\w-]{11})""").find(t)?.groupValues?.get(1)
    }
}
