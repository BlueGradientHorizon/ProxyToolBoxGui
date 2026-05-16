package com.bghorizon.proxytoolboxgui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import java.lang.ref.WeakReference

class ProxyToolBoxApplication : Application() {
    companion object {
        lateinit var appContext: Context
            private set

        private var currentActivityReference = WeakReference<Activity>(null)

        var currentActivity: Activity?
            get() = currentActivityReference.get()
            private set(value) {
                currentActivityReference = WeakReference(value)
            }
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                currentActivity = activity
            }

            override fun onActivityStarted(activity: Activity) {
                currentActivity = activity
            }

            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity
            }

            override fun onActivityPaused(activity: Activity) {
                if (currentActivity === activity) currentActivity = null
            }

            override fun onActivityStopped(activity: Activity) {
                if (currentActivity === activity) currentActivity = null
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity === activity) currentActivity = null
            }
        })
    }
}
