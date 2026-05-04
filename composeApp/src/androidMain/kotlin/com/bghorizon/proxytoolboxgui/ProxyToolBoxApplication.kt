package com.bghorizon.proxytoolboxgui

import android.app.Application
import android.content.Context

class ProxyToolBoxApplication : Application() {
    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }
}