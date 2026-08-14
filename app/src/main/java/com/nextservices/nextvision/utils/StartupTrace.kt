package com.nextservices.nextvision.utils

import android.os.Process
import android.os.SystemClock
import android.util.Log

object StartupTrace {
    private const val TAG = "StartupTrace"
    private val processStartWallClockMillis = System.currentTimeMillis()
    private val processStartElapsedMillis = SystemClock.elapsedRealtime()

    init {
        mark("process_start")
    }

    fun mark(event: String) {
        val wallClockMillis = System.currentTimeMillis()
        val elapsedMillis = SystemClock.elapsedRealtime() - processStartElapsedMillis
        Log.i(
            TAG,
            "wallClockMillis=$wallClockMillis elapsedSinceProcessStartMs=$elapsedMillis " +
                "pid=${Process.myPid()} thread=${Thread.currentThread().name} event=$event",
        )
    }
}