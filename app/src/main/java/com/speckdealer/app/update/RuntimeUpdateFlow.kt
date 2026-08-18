package com.speckdealer.app.update

import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import com.speckdealer.app.MainActivity
import com.speckdealer.app.StartupCrashLogger
import java.io.File
import java.net.HttpURLConnection

object RuntimeUpdateFlow {
	private const val GITHUB_API_LATEST = "https://api.github.com/repos/FightofDestinyHD/Speckdealer_mobil/releases/latest"

	fun downloadValidatedRelease(activity: MainActivity): File? {
		val releaseAsset = resolveLatestReleaseAsset() ?: run {
			showSnackbar(activity, "GitHub-Release nicht erreichbar oder kein gültiges APK-Asset vorhanden.")
			return null
		}

		val apkFile = File(activity.cacheDir, "speckdealer-update-${System.currentTimeMillis()}.apk")
		if (apkFile.exists()) runCatching { apkFile.delete() }

		val installed = snapshotInstalledApp(activity.packageManager, activity.packageName)
		if (installed == null) {
			showSnackbar(activity, "Installierte App-Version konnte nicht gelesen werden.")
			return null
		}

		val downloaded = downloadAndInspectApk(activity, releaseAsset, apkFile) ?: return null
		logDeviceUpdateSnapshot(activity, installed, downloaded)

		val result = evaluateUpdateCompatibility(activity.packageName, installed, downloaded)
		when (result.status) {
			UpdateStatus.NO_NEW_VERSION -> {
				showSnackbar(activity, result.message)
				return null
			}
			UpdateStatus.ERROR -> {
				showSnackbar(activity, buildString {
					append(result.message)
					append('\n')
					append("Installiert: ")
					append(result.installedCertificateDigest ?: "(leer)")
					append('\n')
					append("Heruntergeladen: ")
					append(result.downloadedCertificateDigest ?: "(leer)")
					append('\n')
					append(result.diagnosticMessage)
				})
				return null
			}
			UpdateStatus.UPDATE_READY -> {
				showSnackbar(activity, "Update bereit. Installation wartet auf Benutzerbestätigung.")
				return apkFile
			}
			else -> {
				showSnackbar(activity, "Update-Status: ${result.status}")
				return null
			}
		}
	}

	private fun resolveLatestReleaseAsset(): ReleaseAssetInfo? {
		val connection = java.net.URL(GITHUB_API_LATEST).openConnection() as HttpURLConnection
		connection.setRequestProperty("Accept", "application/vnd.github+json")
		connection.setRequestProperty("User-Agent", "SpeckdealerApp")
		connection.connectTimeout = 10000
		connection.readTimeout = 10000
		connection.connect()
		if (connection.responseCode !in 200..299) {
			connection.disconnect()
			return null
		}
		val body = connection.inputStream.bufferedReader().use { it.readText() }
		connection.disconnect()
		return parseLatestReleaseAsset(body)
	}

	private fun downloadAndInspectApk(activity: MainActivity, releaseAsset: ReleaseAssetInfo, targetFile: File): ApkSnapshot? {
		val connection = java.net.URL(releaseAsset.apkUrl).openConnection() as HttpURLConnection
		connection.setRequestProperty("User-Agent", "SpeckdealerApp")
		connection.connectTimeout = 15000
		connection.readTimeout = 60000
		connection.instanceFollowRedirects = true
		connection.connect()
		if (connection.responseCode !in 200..299) {
			connection.disconnect()
			showSnackbar(activity, "Download unterbrochen: HTTP ${connection.responseCode}.")
			return null
		}
		connection.inputStream.use { input ->
			targetFile.outputStream().use { output -> input.copyTo(output) }
		}
		connection.disconnect()
		if (!targetFile.exists() || targetFile.length() <= 0L) {
			showSnackbar(activity, "APK-Datei beschädigt oder leer.")
			return null
		}
		val snapshot = snapshotArchiveApk(activity.packageManager, targetFile, sha256OfFile(targetFile))
		if (snapshot == null) {
			showSnackbar(activity, "APK-Metadaten konnten nicht gelesen werden.")
			return null
		}
		return snapshot
	}

	private fun logDeviceUpdateSnapshot(activity: MainActivity, installed: ApkSnapshot, downloaded: ApkSnapshot) {
		StartupCrashLogger.logEvent(activity, buildString {
			append("Update-Prüfung | Modell=")
			append(Build.MANUFACTURER)
			append(' ')
			append(Build.MODEL)
			append(" | Android=")
			append(Build.VERSION.RELEASE)
			append(" (SDK ")
			append(Build.VERSION.SDK_INT)
			append(") | App-ID=")
			append(activity.packageName)
			append(" | Installiert: versionCode=")
			append(installed.versionCode)
			append(" | versionName=")
			append(installed.versionName)
			append(" | cert=")
			append(installed.certificateDigests.joinToString(",").ifBlank { "(leer)" })
			append(" | Heruntergeladen: versionCode=")
			append(downloaded.versionCode)
			append(" | versionName=")
			append(downloaded.versionName)
			append(" | cert=")
			append(downloaded.certificateDigests.joinToString(",").ifBlank { "(leer)" })
		})
	}

	private fun showSnackbar(activity: MainActivity, message: String) {
		activity.runOnUiThread {
			Snackbar.make(activity.findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
		}
	}
}
