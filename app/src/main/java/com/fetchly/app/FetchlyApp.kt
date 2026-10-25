package com.fetchly.app

import android.app.Application

class FetchlyApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.downloads.recoverAfterRestart()
    }
}
