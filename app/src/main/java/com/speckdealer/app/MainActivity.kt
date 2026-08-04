package com.speckdealer.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
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
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

	private lateinit var appUpdateManager: AppUpdateManager
	private var cachedUpdateInfo: AppUpdateInfo? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		try {
			setContentView(R.layout.activity_main)
		} catch (e: Exception) {
			e.printStackTrace()
			return
		}

		try {
			appUpdateManager = AppUpdateManagerFactory.create(this)
		} catch (e: Exception) {
			e.printStackTrace()
		}

		try {
			setupMenuTiles()
			showChangelogIfUpdated()
			startIntroTransition()
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	override fun onResume() {
		super.onResume()
		if (isInstalledFromPlayStore()) {
			checkForImmediateUpdate()
		}
	}

	private fun setupMenuTiles() {
		try {
			findViewById<View>(R.id.salesTile).setOnClickListener {
				try {
					startActivity(Intent(this, SalesActivity::class.java))
				} catch (e: Exception) {
					e.printStackTrace()
					Snackbar.make(findViewById(android.R.id.content), "Fehler beim Öffnen: ${e.message}", Snackbar.LENGTH_LONG).show()
				}
			}

			findViewById<View>(R.id.articleManagementTile).setOnClickListener {
				try {
					startActivity(Intent(this, ArticleManagementActivity::class.java))
				} catch (e: Exception) {
					e.printStackTrace()
					Snackbar.make(findViewById(android.R.id.content), "Fehler beim Öffnen: ${e.message}", Snackbar.LENGTH_LONG).show()
				}
			}

			findViewById<View>(R.id.updateTile).setOnClickListener {
				try {
					if (isInstalledFromPlayStore()) {
						startImmediateUpdateIfAvailable()
					} else {
						downloadAndInstallLatestRelease()
					}
				} catch (e: Exception) {
					e.printStackTrace()
					Snackbar.make(findViewById(android.R.id.content), "Fehler beim Update: ${e.message}", Snackbar.LENGTH_LONG).show()
				}
			}
		} catch (e: Exception) {
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
		appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
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

		appUpdateManager.appUpdateInfo.addOnSuccessListener { freshInfo ->
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
			appUpdateManager.startUpdateFlowForResult(
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

		Snackbar.make(findViewById(android.R.id.content), getString(R.string.update_downloading), Snackbar.LENGTH_SHORT).show()
		thread {
			try {
				val outputDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
				val outputFile = File(outputDir, "speckdealer-update.apk")
				downloadFile(LATEST_APK_URL, outputFile)
				runOnUiThread { installDownloadedApk(outputFile) }
			} catch (_: Exception) {
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

	private fun downloadFile(url: String, targetFile: File) {
		val connection = URL(url).openConnection() as HttpURLConnection
		connection.instanceFollowRedirects = true
		connection.connectTimeout = 15000
		connection.readTimeout = 30000
		connection.requestMethod = "GET"
		connection.connect()

		connection.inputStream.use { input ->
			targetFile.outputStream().use { output ->
				input.copyTo(output)
			}
		}
		connection.disconnect()
	}

	private fun installDownloadedApk(apkFile: File) {
		val apkUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", apkFile)
		val intent = Intent(Intent.ACTION_VIEW).apply {
			setDataAndType(apkUri, "application/vnd.android.package-archive")
			addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		startActivity(intent)
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

	companion object {
		private const val UPDATE_REQUEST_CODE = 1001
		private const val PREFERENCES_NAME = "speckdealer_prefs"
		private const val KEY_LAST_VERSION_CODE = "last_version_code"
		private const val KEY_LAST_VERSION_NAME = "last_version_name"
		private const val LATEST_APK_URL = "https://github.com/FightofDestinyHD/Speckdealer_mobil/releases/latest/download/app-release.apk"
	}
}
