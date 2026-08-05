package com.speckdealer.app

import android.app.Application

class SpeckdealerApp : Application() {
	override fun onCreate() {
		super.onCreate()
		StartupCrashLogger.install(this)
		StartupCrashLogger.logEvent(this, "Application.onCreate")
	}
}
