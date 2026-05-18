package com.asmrhelper

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AsmrApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.setup(this)
    }
}
