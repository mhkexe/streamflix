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
import com.nextservices.nextvision.utils.StartupPreloadStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
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
            try {
                AppDatabase.setup(appContext)
                if (UserPreferences.currentProvider == null) {
                    UserPreferences.currentProvider = TmdbProvider("en")
                }
                preloadStartupData(appContext)
            } catch (error: Exception) {
                StartupTrace.mark("background.preload.failed ${error.javaClass.simpleName}")
            } finally {
                StartupTrace.mark("background.database_setup.end")
                preloadReady.complete(Unit)
            }
        }

        applicationScope.launch(Dispatchers.IO) {
            StartupTrace.mark("background.maintenance.begin")
            runCatching { ArtworkRepairScheduler.schedule(appContext, UserPreferences.currentProvider) }
            runCatching { CacheUtils.autoClearIfNeeded(appContext, thresholdMb = threshold) }
            StartupTrace.mark("background.maintenance.end")
        }
        StartupTrace.mark("Application.onCreate.end")
    }

    private suspend fun preloadStartupData(context: Context) {
        val provider = UserPreferences.currentProvider ?: return
        val database = AppDatabase.getInstance(context)

        coroutineScope {
            val home = async { runCatching { provider.getHome() }.getOrNull() }
            val movies = async { runCatching { provider.getMovies() }.getOrNull() }
            val tvShows = async { runCatching { provider.getTvShows() }.getOrNull() }
            val favoriteMovies = async { runCatching { database.movieDao().getFavorites().first() }.getOrNull() }
            val favoriteTvShows = async { runCatching { database.tvShowDao().getFavorites().first() }.getOrNull() }

            val homeData = home.await()
            homeData?.let { HomeCacheStore.write(context, provider, it) }
            StartupPreloadStore.put(
                provider,
                StartupPreloadStore.Data(
                    home = homeData,
                    movies = movies.await(),
                    tvShows = tvShows.await(),
                )
            )
            favoriteMovies.await()
            favoriteTvShows.await()
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            CacheUtils.clearAppCache(this)
        }
    }
}
