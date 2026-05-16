package com.bghorizon.proxytoolboxgui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import java.lang.ref.WeakReference

@SuppressLint("StaticFieldLeak")
object AppContext {
    lateinit var context: Context
    
    private var currentActivityReference = WeakReference<Activity>(null)

    var currentActivity: Activity?
        get() = currentActivityReference.get()
        set(value) {
            currentActivityReference = WeakReference(value)
        }
}
