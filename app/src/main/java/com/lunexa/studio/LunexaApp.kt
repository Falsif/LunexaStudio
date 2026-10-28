package com.lunexa.studio

import android.app.Application
import com.lunexa.studio.data.AppDatabase
import com.lunexa.studio.data.Repository
import com.lunexa.studio.data.SettingsStore
import com.lunexa.studio.notify.Notifier
import com.lunexa.studio.notify.Reminders

class LunexaApp : Application() {
    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        repo = Repository(this, AppDatabase.build(this), SettingsStore(this))
        repo.launch { Reminders.scheduleWeekly(this@LunexaApp, settingsNow()) }
    }
}
