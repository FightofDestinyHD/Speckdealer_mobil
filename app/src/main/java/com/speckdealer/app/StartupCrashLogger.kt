package com.speckdealer.app

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StartupCrashLogger {

	private const val LOG_DIR = "logs"
	private const val LOG_FILE = "startup-crash.log"

	fun install(context: Context) {
		val appContext = context.applicationContext
		val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
		Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
			try {
				logEvent(appContext, "UNCAUGHT in thread=${thread.name}", throwable)
			} catch (_: Exception) {
			}
			previousHandler?.uncaughtException(thread, throwable)
		}
		logEvent(appContext, "Crash logger installiert")
	}

	fun logEvent(context: Context, message: String, throwable: Throwable? = null) {
		try {
			val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.GERMANY).format(Date())
			val logFile = getLogFile(context)
			val entry = buildString {
				append(now)
				append(" | ")
				append(message)
				append('\n')
				if (throwable != null) {
					append(throwable::class.java.name)
					append(": ")
					append(throwable.message ?: "")
					append('\n')
					append(android.util.Log.getStackTraceString(throwable))
					append('\n')
				}
			}
			logFile.appendText(entry)
		} catch (_: Exception) {
		}
	}

	private fun getLogFile(context: Context): File {
		val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
		val dir = File(baseDir, LOG_DIR)
		if (!dir.exists()) {
			dir.mkdirs()
		}
		return File(dir, LOG_FILE)
	}
}
