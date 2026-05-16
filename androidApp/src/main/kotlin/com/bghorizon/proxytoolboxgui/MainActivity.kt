package com.bghorizon.proxytoolboxgui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

import androidx.compose.runtime.remember
import com.bghorizon.proxytoolboxgui.data.db.createAppDatabase
import com.bghorizon.proxytoolboxgui.data.db.createSubscriptionDatabase
import com.bghorizon.proxytoolboxgui.data.db.getAppDatabaseBuilder
import com.bghorizon.proxytoolboxgui.data.db.getSubscriptionDatabaseBuilder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val appDb = remember { createAppDatabase(getAppDatabaseBuilder(applicationContext)) }
            val subDb = remember { createSubscriptionDatabase(getSubscriptionDatabaseBuilder(applicationContext)) }
            App(appDb, subDb)
        }
    }
}
