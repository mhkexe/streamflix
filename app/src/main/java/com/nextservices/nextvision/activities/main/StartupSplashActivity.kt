package com.nextservices.nextvision.activities.main

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.nextservices.nextvision.utils.StartupTrace

class StartupSplashActivity : FragmentActivity() {
    companion object {
        const val EXTRA_HOME_PRELOADED = "home_preloaded"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTrace.mark("StartupSplashActivity.onCreate.begin")
        super.onCreate(savedInstanceState)
        StartupTrace.mark("StartupSplashActivity.super.onCreate.end")
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
