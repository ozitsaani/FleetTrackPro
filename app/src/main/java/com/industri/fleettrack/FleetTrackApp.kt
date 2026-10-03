package com.industri.fleettrack

import android.app.Application
import android.content.pm.ApplicationInfo
import androidx.appcompat.app.AppCompatDelegate
import timber.log.Timber

class FleetTrackApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Plant Timber DebugTree hanya pada mode debug
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Timber.plant(Timber.DebugTree())
        }

        // Kunci aplikasi ke mode terang saja agar estetika Neubrutalism konsisten
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
    }
}
