package com.bghorizon.proxytoolboxgui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import android.content.res.Configuration
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView

import androidx.compose.runtime.remember
import com.bghorizon.proxytoolboxgui.data.db.createAppDatabase
import com.bghorizon.proxytoolboxgui.data.db.createSubscriptionDatabase
import com.bghorizon.proxytoolboxgui.data.db.getAppDatabaseBuilder
import com.bghorizon.proxytoolboxgui.data.db.getSubscriptionDatabaseBuilder

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val appDb = remember { createAppDatabase(getAppDatabaseBuilder(applicationContext)) }
            val subDb = remember { createSubscriptionDatabase(getSubscriptionDatabaseBuilder(applicationContext)) }
            App(appDb, subDb)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // This ensures Compose picks up the locale change without activity recreation
        val composeView = window.decorView.findViewById<ViewGroup>(android.R.id.content)
            .getChildAt(0) as? ComposeView
        composeView?.dispatchConfigurationChanged(newConfig)
    }
}
