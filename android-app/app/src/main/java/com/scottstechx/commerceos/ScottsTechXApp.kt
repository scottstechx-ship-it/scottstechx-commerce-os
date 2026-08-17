package com.scottstechx.commerceos

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Hilt entry point. Plant Timber in debug only — release builds stay quiet
 * unless a crash reporting backend is wired in.
 */
@HiltAndroidApp
class ScottsTechXApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
