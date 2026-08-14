package com.nextservices.nextvision

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import com.nextservices.nextvision.database.AppDatabase
import com.nextservices.nextvision.utils.AppLanguageManager
import com.nextservices.nextvision.utils.ArtworkRepairScheduler
import com.nextservices.nextvision.utils.CacheUtils
import com.nextservices.nextvision.utils.UserPreferences
import com.nextservices.nextvision.utils.StartupTrace
import com.nextservices.nextvision.providers.TmdbProvider
import com.nextservices.nextvision.utils.HomeCacheStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NextVisionApp : Application() {
    init {
        StartupTrace.mark("NextVisionApp.class_initialized")
    }

    companion object {
        lateinit var instance: NextVisionApp
            private set

        @Volatile
        var currentActivity: Activity? = null
            private set

        val preloadReady = CompletableDeferred<Unit>()
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun attachBaseContext(base: Context) {
        StartupTrace.mark("Application.attachBaseContext.begin")
        val wrappedContext = AppLanguageManager.wrap(base)
        super.attachBaseContext(wrappedContext)
        StartupTrace.mark("Application.attachBaseContext.end")
    }

    override fun onCreate() {
        StartupTrace.mark("Application.onCreate.begin")
        super.onCreate()
        StartupTrace.mark("Application.super.onCreate.end")
        instance = this
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

            override fun onActivityStarted(activity: Activity) = Unit

            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity
            }

            override fun onActivityPaused(activity: Activity) {
                if (currentActivity === activity) {
                    currentActivity = null
                }
            }

            override fun onActivityStopped(activity: Activity) = Unit

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

            override fun onActivityDestroyed(activity: Activity) {
                if (currentActivity === activity) {
                    currentActivity = null
                }
            }
        })

        // 2. Inizializzazione preferenze (con applicationContext)
        StartupTrace.mark("UserPreferences.setup.begin")
        UserPreferences.setup(this)
        StartupTrace.mark("UserPreferences.setup.end")

        val appContext = applicationContext
        val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val threshold = if (isTv) 250L else 500L
        StartupTrace.mark("Application.synchronous_setup.end isTv=$isTv thresholdMb=$threshold")

        applicationScope.launch(Dispatchers.IO) {
            StartupTrace.mark("background.database_setup.begin")
            runCatching { AppDatabase.setup(appContext) }
            if (UserPreferences.currentProvider == null) {
                UserPreferences.currentProvider = TmdbProvider("en")
            }
            runCatching {
                val provider = UserPreferences.currentProvider ?: return@runCatching
                val cached = HomeCacheStore.read(appContext, provider)
                if (cached.isNullOrEmpty()) {
                    HomeCacheStore.write(appContext, provider, provider.getHome())
                }
            }
            StartupTrace.mark("background.database_setup.end")
            preloadReady.complete(Unit)
        }

        applicationScope.launch(Dispatchers.IO) {
            StartupTrace.mark("background.maintenance.begin")
            runCatching { ArtworkRepairScheduler.schedule(appContext, UserPreferences.currentProvider) }
            runCatching { CacheUtils.autoClearIfNeeded(appContext, thresholdMb = threshold) }
            StartupTrace.mark("background.maintenance.end")
        }
        StartupTrace.mark("Application.onCreate.end")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            CacheUtils.clearAppCache(this)
        }
    }
}
