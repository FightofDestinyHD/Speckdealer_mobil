package com.speckdealer.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.speckdealer.app.data.AppGraph
import java.net.HttpURLConnection
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

	private var appUpdateManager: AppUpdateManager? = null
	private var cachedUpdateInfo: AppUpdateInfo? = null
	private var pendingApkFile: java.io.File? = null

	companion object {
		private const val UPDATE_REQUEST_CODE = 1001
		private const val REQUEST_INSTALL_PERMISSION = 1002
		private const val PREFERENCES_NAME = "speckdealer_prefs"
		private const val KEY_LAST_VERSION_CODE = "last_version_code"
		private const val KEY_LAST_VERSION_NAME = "last_version_name"
		private const val GITHUB_API_LATEST = "https://api.github.com/repos/FightofDestinyHD/Speckdealer_mobil/releases/latest"
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		StartupCrashLogger.logEvent(this, "MainActivity.onCreate start")
		try {
			setContentView(R.layout.activity_main)
			StartupCrashLogger.logEvent(this, "setContentView erfolgreich")
		} catch (e: Exception) {
			StartupCrashLogger.logEvent(this, "setContentView Fehler", e)
			e.printStackTrace()
			return
		}

		try {
			appUpdateManager = AppUpdateManagerFactory.create(this)
			StartupCrashLogger.logEvent(this, "AppUpdateManager erstellt")
		} catch (e: Exception) {
			StartupCrashLogger.logEvent(this, "AppUpdateManager Fehler", e)
			e.printStackTrace()
		}

		try {
			setupMenuTiles()
			showChangelogIfUpdated()
			startIntroTransition()
			StartupCrashLogger.logEvent(this, "MainActivity UI init fertig | logs=" + StartupCrashLogger.getKnownPaths(this).joinToString("; "))
		} catch (e: Exception) {
			StartupCrashLogger.logEvent(this, "MainActivity UI init Fehler", e)
			e.printStackTrace()
		}
	}

	override fun onResume() {
		super.onResume()
		StartupCrashLogger.logEvent(this, "MainActivity.onResume")
		try {
			if (isInstalledFromPlayStore()) {
				checkForImmediateUpdate()
			}
		} catch (e: Exception) {
			StartupCrashLogger.logEvent(this, "onResume Fehler", e)
			e.printStackTrace()
		}
	}

	private fun setupMenuTiles() {
		try {
			val salesTile = findViewById<View?>(R.id.salesTile)
			val ordersTile = findViewById<View?>(R.id.ordersTile)
			val articleManagementTile = findViewById<View?>(R.id.articleManagementTile)
			val dailyReportTile = findViewById<View?>(R.id.dailyReportTile)
			val updateTile = findViewById<View?>(R.id.updateTile)

			if (salesTile == null) {
				StartupCrashLogger.logEvent(this, "setupMenuTiles: salesTile fehlt im Layout")
			}
			if (articleManagementTile == null) {
				StartupCrashLogger.logEvent(this, "setupMenuTiles: articleManagementTile fehlt im Layout")
			}
			if (updateTile == null) {
				StartupCrashLogger.logEvent(this, "setupMenuTiles: updateTile fehlt im Layout")
			}

			salesTile?.setOnClickListener {
				try {
					startActivity(Intent(this, SalesActivity::class.java))
				} catch (e: Exception) {
					StartupCrashLogger.logEvent(this, "SalesActivity öffnen Fehler", e)
					e.printStackTrace()
					Snackbar.make(findViewById(android.R.id.content), "Fehler beim Öffnen: ${e.message}", Snackbar.LENGTH_LONG).show()
				}
			}

			ordersTile?.setOnClickListener {
				try {
					startActivity(Intent(this, OrdersActivity::class.java))
				} catch (e: Exception) {
					e.printStackTrace()
					Snackbar.make(findViewById(android.R.id.content), "Fehler beim Öffnen: ${e.message}", Snackbar.LENGTH_LONG).show()
				}
			}

			articleManagementTile?.setOnClickListener {
				try {
					startActivity(Intent(this, ArticleManagementActivity::class.java))
				} catch (e: Exception) {
					StartupCrashLogger.logEvent(this, "ArticleManagementActivity öffnen Fehler", e)
					e.printStackTrace()
					Snackbar.make(findViewById(android.R.id.content), "Fehler beim Öffnen: ${e.message}", Snackbar.LENGTH_LONG).show()
				}
			}

			dailyReportTile?.setOnClickListener {
				try {
					startActivity(Intent(this, DailyReportActivity::class.java))
				} catch (e: Exception) {
					StartupCrashLogger.logEvent(this, "DailyReportActivity öffnen Fehler", e)
					e.printStackTrace()
					Snackbar.make(findViewById(android.R.id.content), "Fehler beim Öffnen: ${e.message}", Snackbar.LENGTH_LONG).show()
				}
			}

			updateTile?.setOnClickListener {
				try {
					if (isInstalledFromPlayStore()) {
						startImmediateUpdateIfAvailable()
					} else {
						downloadAndInstallLatestRelease()
					}
				} catch (e: Exception) {
					StartupCrashLogger.logEvent(this, "Update-Start Fehler", e)
					e.printStackTrace()
					Snackbar.make(findViewById(android.R.id.content), "Fehler beim Update: ${e.message}", Snackbar.LENGTH_LONG).show()
				}
			}
			StartupCrashLogger.logEvent(this, "setupMenuTiles fertig")
		} catch (e: Exception) {
			StartupCrashLogger.logEvent(this, "setupMenuTiles Fehler", e)
			e.printStackTrace()
		}
	}

	private fun showChangelogIfUpdated() {
		try {
			val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
			val currentVersionCode = PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))
			val currentVersionName = BuildConfig.VERSION_NAME
			val lastVersionCode = preferences.getLong(KEY_LAST_VERSION_CODE, 0L)
			val lastVersionName = preferences.getString(KEY_LAST_VERSION_NAME, null)

			val isFirstRun = lastVersionCode == 0L && lastVersionName == null
			val isUpdated = !isFirstRun && (currentVersionCode > lastVersionCode || currentVersionName != lastVersionName)

			if (isUpdated) {
				AlertDialog.Builder(this)
					.setTitle(R.string.changelog_title)
					.setMessage(getString(R.string.changelog_message, BuildConfig.VERSION_NAME))
					.setPositiveButton(android.R.string.ok, null)
					.show()
			}

			preferences.edit()
				.putLong(KEY_LAST_VERSION_CODE, currentVersionCode)
				.putString(KEY_LAST_VERSION_NAME, currentVersionName)
				.apply()
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	private fun startIntroTransition() {
		try {
			val welcomeContainer = findViewById<View>(R.id.welcomeContainer)
			val menuContainer = findViewById<View>(R.id.menuContainer)

			welcomeContainer.animate()
				.alpha(0f)
				.setStartDelay(1300)
				.setDuration(700)
				.withEndAction {
					try {
						welcomeContainer.visibility = View.GONE
						menuContainer.visibility = View.VISIBLE
						menuContainer.alpha = 0f
						menuContainer.animate()
							.alpha(1f)
							.setDuration(450)
							.start()
					} catch (e: Exception) {
						e.printStackTrace()
					}
				}
				.start()
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	private fun checkForImmediateUpdate() {
		val manager = appUpdateManager ?: return
		manager.appUpdateInfo.addOnSuccessListener { info ->
			cachedUpdateInfo = info
			if (isImmediateUpdateAvailable(info)) {
				Snackbar.make(
					findViewById(android.R.id.content),
					getString(R.string.update_available),
					Snackbar.LENGTH_LONG
				).show()
			}
		}
	}

	private fun startImmediateUpdateIfAvailable() {
		val info = cachedUpdateInfo
		if (info != null && isImmediateUpdateAvailable(info)) {
			startImmediateUpdate(info)
			return
		}

		val manager = appUpdateManager
		if (manager == null) {
			downloadAndInstallLatestRelease()
			return
		}

		manager.appUpdateInfo.addOnSuccessListener { freshInfo ->
			cachedUpdateInfo = freshInfo
			if (isImmediateUpdateAvailable(freshInfo)) {
				startImmediateUpdate(freshInfo)
			} else {
				downloadAndInstallLatestRelease()
			}
		}.addOnFailureListener {
			downloadAndInstallLatestRelease()
		}
	}

	private fun startImmediateUpdate(info: AppUpdateInfo) {
		try {
			val manager = appUpdateManager ?: return
			manager.startUpdateFlowForResult(
				info,
				AppUpdateType.IMMEDIATE,
				this,
				UPDATE_REQUEST_CODE
			)
		} catch (_: Exception) {
			Snackbar.make(
				findViewById(android.R.id.content),
				getString(R.string.update_start_failed),
				Snackbar.LENGTH_LONG
			).show()
		}
	}

	private fun downloadAndInstallLatestRelease() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
			val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
				data = Uri.parse("package:$packageName")
			}
			startActivity(intent)
			Snackbar.make(
				findViewById(android.R.id.content),
				getString(R.string.allow_unknown_sources),
				Snackbar.LENGTH_LONG
			).show()
			return
		}

		Snackbar.make(findViewById(android.R.id.content), getString(R.string.update_downloading), Snackbar.LENGTH_LONG).show()

		// GitHub API in Hintergrundthread abfragen
		thread {
			try {
				val apkUrl = resolveLatestApkUrl()
				if (apkUrl == null) {
					runOnUiThread {
						Snackbar.make(
							findViewById(android.R.id.content),
							"Kein APK-Asset im letzten Release gefunden.",
							Snackbar.LENGTH_LONG
						).show()
					}
					return@thread
				}
				runOnUiThread { startDownloadViaDownloadManager(apkUrl) }
			} catch (e: Exception) {
				runOnUiThread {
					Snackbar.make(
						findViewById(android.R.id.content),
						getString(R.string.update_download_failed),
						Snackbar.LENGTH_LONG
					).show()
				}
			}
		}
	}

	private fun startDownloadViaDownloadManager(apkUrl: String) {
		// APK in app-eigenes Cache-Verzeichnis herunterladen (kein FileProvider-Problem)
		val apkFile = java.io.File(cacheDir, "speckdealer-update.apk")

		thread {
			try {
				val url = java.net.URL(apkUrl)
				val conn = url.openConnection() as HttpURLConnection
				conn.setRequestProperty("User-Agent", "SpeckdealerApp")
				conn.connectTimeout = 15000
				conn.readTimeout = 60000
				conn.connect()

				conn.inputStream.use { input ->
					apkFile.outputStream().use { output ->
						input.copyTo(output)
					}
				}
				conn.disconnect()

				runOnUiThread { installApkFile(apkFile) }
			} catch (e: Exception) {
				e.printStackTrace()
				runOnUiThread {
					Snackbar.make(
						findViewById(android.R.id.content),
						getString(R.string.update_download_failed),
						Snackbar.LENGTH_LONG
					).show()
				}
			}
		}
	}

	private fun installApkFile(apkFile: java.io.File) {
		// Ab Android 8: Berechtigung für unbekannte Quellen prüfen
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			if (!packageManager.canRequestPackageInstalls()) {
				pendingApkFile = apkFile
				AlertDialog.Builder(this)
					.setTitle("Installation erlauben")
					.setMessage("Bitte erlaube die Installation aus unbekannten Quellen für diese App, dann wird das Update automatisch installiert.")
					.setPositiveButton("Einstellungen öffnen") { _, _ ->
						val intent = Intent(
							Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
							Uri.parse("package:$packageName")
						)
						startActivityForResult(intent, REQUEST_INSTALL_PERMISSION)
					}
					.setNegativeButton("Abbrechen", null)
					.show()
				return
			}
		}
		doInstallApk(apkFile)
	}

	private fun doInstallApk(apkFile: java.io.File) {
		try {
			val apkUri = FileProvider.getUriForFile(
				this,
				"${packageName}.fileprovider",
				apkFile
			)
			val installIntent = Intent(Intent.ACTION_VIEW).apply {
				setDataAndType(apkUri, "application/vnd.android.package-archive")
				addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
			}
			startActivity(installIntent)
		} catch (e: Exception) {
			e.printStackTrace()
			Snackbar.make(
				findViewById(android.R.id.content),
				getString(R.string.update_download_failed),
				Snackbar.LENGTH_LONG
			).show()
		}
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		super.onActivityResult(requestCode, resultCode, data)
		if (requestCode == REQUEST_INSTALL_PERMISSION) {
			val apk = pendingApkFile
			if (apk != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
				&& packageManager.canRequestPackageInstalls()) {
				pendingApkFile = null
				doInstallApk(apk)
			}
		}
	}

	/** Fragt die GitHub API ab und gibt die browser_download_url des ersten .apk-Assets zurück */
	private fun resolveLatestApkUrl(): String? {
		val connection = java.net.URL(GITHUB_API_LATEST).openConnection() as HttpURLConnection
		connection.setRequestProperty("Accept", "application/vnd.github+json")
		connection.setRequestProperty("User-Agent", "SpeckdealerApp")
		connection.connectTimeout = 10000
		connection.readTimeout = 10000
		connection.connect()
		val json = connection.inputStream.bufferedReader().use { it.readText() }
		connection.disconnect()
		// assets-Array parsen
		val assetsStart = json.indexOf("\"assets\"")
		if (assetsStart < 0) return null
		var idx = json.indexOf('[', assetsStart)
		while (idx >= 0) {
			val urlKey = json.indexOf("\"browser_download_url\"", idx)
			if (urlKey < 0) break
			val colon = json.indexOf(':', urlKey)
			val q1 = json.indexOf('"', colon + 1)
			val q2 = json.indexOf('"', q1 + 1)
			val url = json.substring(q1 + 1, q2)
			if (url.endsWith(".apk")) return url
			idx = q2
		}
		return null
	}

	private fun isImmediateUpdateAvailable(info: AppUpdateInfo): Boolean {
		return info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
			info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
	}

	private fun isInstalledFromPlayStore(): Boolean {
		val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			packageManager.getInstallSourceInfo(packageName).installingPackageName
		} else {
			@Suppress("DEPRECATION")
			packageManager.getInstallerPackageName(packageName)
		}
		return installer == "com.android.vending"
	}
}

