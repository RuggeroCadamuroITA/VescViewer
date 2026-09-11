package com.ruggerocadamuro.myapplication

import android.app.Application
import com.ruggerocadamuro.myapplication.data.VescRepository
import com.ruggerocadamuro.myapplication.data.settings.SettingsRepository

/**
 * ServiceLocator minimalista (nessuna libreria DI nel template): un solo punto
 * dove si creano i repository, condivisi tra ViewModel e ForegroundService.
 */
object ServiceLocator {
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var vescRepository: VescRepository
        private set

    fun init(context: Application) {
        settingsRepository = SettingsRepository(context)
        vescRepository = VescRepository(context, settingsRepository)
    }
}

/**
 * Application custom: inizializza i repository prima che Activity/Service
 * ne abbiano bisogno.
 */
class VescCompanionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}
