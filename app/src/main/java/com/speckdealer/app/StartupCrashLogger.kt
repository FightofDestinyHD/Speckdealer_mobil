package com.speckdealer.app

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object StartupCrashLogger {

	private const val LOG_DIR = "logs"
	private const val LOG_FILE = "startup-crash.log"
	private const val TAG = "StartupCrashLogger"

	@Volatile
	private var installed = false

	fun install(context: Context) {
		if (installed) return
		val appContext = context.applicationContext
		val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
		Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
			try {
				logEvent(appContext, "UNCAUGHT in thread=${thread.name}", throwable)
			} catch (_: Exception) {
			}
			previousHandler?.uncaughtException(thread, throwable)
		}
		installed = true
		logEvent(appContext, "Crash logger installiert")
	}

	fun logEvent(context: Context, message: String, throwable: Throwable? = null) {
		try {
			val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.GERMANY).format(Date())
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
					append(Log.getStackTraceString(throwable))
					append('\n')
				}
			}
			for (file in getLogFiles(context)) {
				try {
					file.appendText(entry)
				} catch (_: Exception) {
				}
			}
			Log.e(TAG, message, throwable)
		} catch (_: Exception) {
		}
	}

	fun getKnownPaths(context: Context): List<String> {
		return getLogFiles(context).map { it.absolutePath }
	}

	private fun getLogFiles(context: Context): List<File> {
		val files = mutableListOf<File>()
		files.add(createLogFile(File(context.filesDir, LOG_DIR)))
		context.getExternalFilesDir(null)?.let { externalBase ->
			files.add(createLogFile(File(externalBase, LOG_DIR)))
		}
		return files.distinctBy { it.absolutePath }
	}

	private fun createLogFile(dir: File): File {
		if (!dir.exists()) {
			dir.mkdirs()
		}
		return File(dir, LOG_FILE)
	}
}
