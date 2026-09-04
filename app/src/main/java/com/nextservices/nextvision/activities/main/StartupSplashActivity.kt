package com.nextservices.nextvision.activities.main

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.nextservices.nextvision.BuildConfig
import com.nextservices.nextvision.R
import com.nextservices.nextvision.utils.StartupTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class StartupSplashActivity : FragmentActivity() {
    companion object {
        const val EXTRA_HOME_PRELOADED = "home_preloaded"
        private const val REMOTE_CONFIG_URL = "https://nextservices.live/app-config.txt"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTrace.mark("StartupSplashActivity.onCreate.begin")
        super.onCreate(savedInstanceState)
        StartupTrace.mark("StartupSplashActivity.super.onCreate.end")
        lifecycleScope.launch {
            val latestVersion = withContext(Dispatchers.IO) { fetchLatestVersion() }
            when {
                latestVersion == null -> {
                    setContentView(R.layout.activity_version_check_error)
                    StartupTrace.mark("StartupSplashActivity.version_check_failed")
                }

                latestVersion != BuildConfig.VERSION_NAME -> {
                    setContentView(R.layout.activity_outdated_app)
                    StartupTrace.mark("StartupSplashActivity.outdated version=${BuildConfig.VERSION_NAME} latest=$latestVersion")
                }

                else -> {
                    openMainActivity()
                }
            }
        }
    }

    private fun fetchLatestVersion(): String? {
        return runCatching {
            (URL(REMOTE_CONFIG_URL).openConnection() as HttpURLConnection).run {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
                if (responseCode !in 200..299) return@run null
                inputStream.bufferedReader().use { it.readText().trim() }.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    private fun openMainActivity() {
        val target = if (packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            MainTvActivity::class.java
        } else {
            MainMobileActivity::class.java
        }
        StartupTrace.mark("StartupSplashActivity.target_selected target=${target.simpleName}")
        startActivity(Intent(this, target))
        StartupTrace.mark("StartupSplashActivity.startActivity.called")
        finish()
        StartupTrace.mark("StartupSplashActivity.onCreate.end")
    }
}
