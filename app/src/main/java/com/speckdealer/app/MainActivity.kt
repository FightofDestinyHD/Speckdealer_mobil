package com.speckdealer.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.EditText
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
import com.speckdealer.app.data.DepositMovement
import com.speckdealer.app.data.DepositMovementStorage
import com.speckdealer.app.data.DepositMovementType
import java.io.File
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

	private var appUpdateManager: AppUpdateManager? = null
	private var cachedUpdateInfo: AppUpdateInfo? = null
	private var pendingApkFile: File? = null
	private var depositReturnInProgress = false

	private data class ReleaseAsset(
		val apkUrl: String,
		val checksumUrl: String?
	)

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
			val settingsTile = findViewById<View?>(R.id.settingsTile)
			val depositReturnTile = findViewById<View>(R.id.depositReturnTile)
			val updateTile = findViewById<View?>(R.id.updateTile)

			if (salesTile == null) {
				StartupCrashLogger.logEvent(this, "setupMenuTiles: salesTile fehlt im Layout")
			}
			if (articleManagementTile == null) {
				StartupCrashLogger.logEvent(this, "setupMenuTiles: articleManagementTile fehlt im Layout")
			}
			if (settingsTile == null) {
				StartupCrashLogger.logEvent(this, "setupMenuTiles: settingsTile fehlt im Layout")
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

			settingsTile?.setOnClickListener {
				try {
					startActivity(Intent(this, ArticleManagementActivity::class.java))
				} catch (e: Exception) {
					StartupCrashLogger.logEvent(this, "SettingsTile öffnen Fehler", e)
					e.printStackTrace()
					Snackbar.make(findViewById(android.R.id.content), "Fehler beim Öffnen: ${e.message}", Snackbar.LENGTH_LONG).show()
				}
			}

			depositReturnTile.setOnClickListener {
				openDepositReturn()
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

	private fun openDepositReturn() {
		if (depositReturnInProgress) return
		val repository = AppGraph.repository(this)
		val depositStorage = DepositMovementStorage(this)
		val depositArticles = repository.getDepositArticles()
		if (depositArticles.isEmpty()) {
			Snackbar.make(findViewById(android.R.id.content), "Keine Pfandkonfiguration vorhanden.", Snackbar.LENGTH_LONG).show()
			return
		}

		val uniqueByType = linkedMapOf<String, com.speckdealer.app.data.ArticleEntity>()
		depositArticles.forEach { article ->
			val type = repository.resolveDepositType(article)
			if (type != "unknown" && !uniqueByType.containsKey(type)) {
				uniqueByType[type] = article
			}
		}
		if (uniqueByType.isEmpty()) {
			Snackbar.make(findViewById(android.R.id.content), "Pfandarten konnten nicht bestimmt werden.", Snackbar.LENGTH_LONG).show()
			return
		}

		val options = uniqueByType.entries.toList()
		AlertDialog.Builder(this)
			.setTitle("Pfandrückgabe")
			.setMessage("Welcher Pfand wird zurückgegeben?")
			.setItems(options.map { (_, article) -> article.name }.toTypedArray()) { _, which ->
				val selected = options[which]
				showDepositQuantityDialog(selected.key, selected.value.name, selected.value.priceCents.toLong(), depositStorage)
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun showDepositQuantityDialog(
		depositType: String,
		displayName: String,
		unitAmountCents: Long,
		depositStorage: DepositMovementStorage
	) {
		if (unitAmountCents <= 0L) {
			Snackbar.make(findViewById(android.R.id.content), "Ungültiger Pfandbetrag konfiguriert.", Snackbar.LENGTH_LONG).show()
			return
		}
		val input = EditText(this).apply {
			inputType = InputType.TYPE_CLASS_NUMBER
			hint = "Anzahl"
		}
		AlertDialog.Builder(this)
			.setTitle("Pfandrückgabe")
			.setMessage("Pfandart: $displayName\nEinzelbetrag: ${MoneyValueService.formatCents(unitAmountCents)}")
			.setView(input)
			.setPositiveButton("Weiter") { _, _ ->
				val quantityText = input.text?.toString()?.trim().orEmpty()
				val quantity = quantityText.toIntOrNull()
				if (quantity == null || quantity <= 0 || quantity > 10_000) {
					Snackbar.make(findViewById(android.R.id.content), "Bitte eine gültige positive Anzahl eingeben.", Snackbar.LENGTH_LONG).show()
					return@setPositiveButton
				}
				val total = unitAmountCents * quantity.toLong()
				showDepositReturnConfirmation(depositType, displayName, quantity, unitAmountCents, total, depositStorage)
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun showDepositReturnConfirmation(
		depositType: String,
		displayName: String,
		quantity: Int,
		unitAmountCents: Long,
		totalAmountCents: Long,
		depositStorage: DepositMovementStorage
	) {
		val summary = buildString {
			appendLine("Pfandart: $displayName")
			appendLine("Anzahl: $quantity")
			appendLine("Einzelbetrag: ${MoneyValueService.formatCents(unitAmountCents)}")
			append("Gesamter Rückgabebetrag: ${MoneyValueService.formatCents(totalAmountCents)}")
		}
		AlertDialog.Builder(this)
			.setTitle("Pfandrückgabe bestätigen")
			.setMessage(summary)
			.setPositiveButton("Bestätigen") { _, _ ->
				if (depositReturnInProgress) return@setPositiveButton
				depositReturnInProgress = true
				runCatching {
					depositStorage.appendMovement(
						DepositMovement(
							transactionId = UUID.randomUUID().toString(),
							depositType = depositType,
							quantity = quantity,
							unitAmountCents = unitAmountCents,
							totalAmountCents = totalAmountCents,
							movementType = DepositMovementType.RETURNED
						)
					)
				}.onSuccess {
					Snackbar.make(findViewById(android.R.id.content), "Zurückgegeben: ${MoneyValueService.formatCents(totalAmountCents)}", Snackbar.LENGTH_LONG).show()
				}.onFailure {
					Snackbar.make(findViewById(android.R.id.content), "Pfandrückgabe konnte nicht gespeichert werden.", Snackbar.LENGTH_LONG).show()
				}.also {
					depositReturnInProgress = false
				}
			}
			.setNegativeButton("Abbrechen", null)
			.show()
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

		thread {
			try {
				val releaseAsset = resolveLatestApkAsset()
				if (releaseAsset == null || releaseAsset.checksumUrl.isNullOrBlank()) {
					runOnUiThread {
						Snackbar.make(
							findViewById(android.R.id.content),
							"Update-Release ohne Prüfsumme gefunden. Installation aus Sicherheitsgründen abgebrochen.",
							Snackbar.LENGTH_LONG
						).show()
					}
					return@thread
				}
				runOnUiThread { startDownloadViaDownloadManager(releaseAsset) }
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

	private fun startDownloadViaDownloadManager(releaseAsset: ReleaseAsset) {
		val apkFile = File(cacheDir, "speckdealer-update.apk")

		thread {
			try {
				val url = java.net.URL(releaseAsset.apkUrl)
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

				val checksumValid = verifyApkChecksum(apkFile, releaseAsset.checksumUrl)
				if (!checksumValid) {
					runOnUiThread {
						Snackbar.make(
							findViewById(android.R.id.content),
							"Update-Prüfsumme ungültig. Installation abgebrochen.",
							Snackbar.LENGTH_LONG
						).show()
					}
					return@thread
				}

				val signatureValid = verifyApkSignatureMatchesInstalled(apkFile)
				if (!signatureValid) {
					runOnUiThread {
						Snackbar.make(
							findViewById(android.R.id.content),
							"Update-Signatur ungültig oder abweichend. Installation abgebrochen.",
							Snackbar.LENGTH_LONG
						).show()
					}
					return@thread
				}

				runOnUiThread { installApkFile(apkFile) }
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

	private fun installApkFile(apkFile: File) {
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

	private fun doInstallApk(apkFile: File) {
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

	/** Fragt die GitHub API ab und gibt APK + zugehörige SHA256-URL zurück. */
	private fun resolveLatestApkAsset(): ReleaseAsset? {
		val connection = java.net.URL(GITHUB_API_LATEST).openConnection() as HttpURLConnection
		connection.setRequestProperty("Accept", "application/vnd.github+json")
		connection.setRequestProperty("User-Agent", "SpeckdealerApp")
		connection.connectTimeout = 10000
		connection.readTimeout = 10000
		connection.connect()
		val json = connection.inputStream.bufferedReader().use { it.readText() }
		connection.disconnect()

		val assetsStart = json.indexOf("\"assets\"")
		if (assetsStart < 0) return null
		val urls = mutableListOf<String>()
		var idx = json.indexOf('[', assetsStart)
		while (idx >= 0) {
			val urlKey = json.indexOf("\"browser_download_url\"", idx)
			if (urlKey < 0) break
			val colon = json.indexOf(':', urlKey)
			val q1 = json.indexOf('"', colon + 1)
			val q2 = json.indexOf('"', q1 + 1)
			if (q1 < 0 || q2 < 0) break
			urls += json.substring(q1 + 1, q2)
			idx = q2
		}

		val apkUrl = urls.firstOrNull { it.endsWith(".apk") && !it.contains("-debug", ignoreCase = true) }
			?: urls.firstOrNull { it.endsWith(".apk") }
			?: return null
		val apkFileName = apkUrl.substringAfterLast('/')
		val checksumUrl = urls.firstOrNull { it.endsWith("$apkFileName.sha256") }
		return ReleaseAsset(apkUrl = apkUrl, checksumUrl = checksumUrl)
	}

	private fun verifyApkChecksum(apkFile: File, checksumUrl: String?): Boolean {
		if (checksumUrl.isNullOrBlank()) return false
		val expected = downloadChecksumValue(checksumUrl) ?: return false
		val actual = sha256OfFile(apkFile)
		return expected.equals(actual, ignoreCase = true)
	}

	private fun downloadChecksumValue(url: String): String? {
		val connection = java.net.URL(url).openConnection() as HttpURLConnection
		connection.setRequestProperty("User-Agent", "SpeckdealerApp")
		connection.connectTimeout = 10000
		connection.readTimeout = 10000
		connection.connect()
		val content = connection.inputStream.bufferedReader().use { it.readText() }.trim()
		connection.disconnect()
		if (content.isBlank()) return null
		return content.split(Regex("\\s+")).firstOrNull()?.trim()?.ifBlank { null }
	}

	private fun sha256OfFile(file: File): String {
		val digest = MessageDigest.getInstance("SHA-256")
		file.inputStream().use { input ->
			val buffer = ByteArray(8192)
			while (true) {
				val read = input.read(buffer)
				if (read <= 0) break
				digest.update(buffer, 0, read)
			}
		}
		return digest.digest().joinToString("") { "%02x".format(it) }
	}

	private fun verifyApkSignatureMatchesInstalled(apkFile: File): Boolean {
		val installed = signingDigestsForInstalledApp()
		if (installed.isEmpty()) return false
		val downloaded = signingDigestsForApkFile(apkFile)
		if (downloaded.isEmpty()) return false
		return installed.intersect(downloaded).isNotEmpty()
	}

	private fun signingDigestsForInstalledApp(): Set<String> {
		val packageInfo = packageManager.getPackageInfoCompat(packageName) ?: return emptySet()
		return extractSigningDigests(packageInfo)
	}

	private fun signingDigestsForApkFile(apkFile: File): Set<String> {
		val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			PackageManager.GET_SIGNING_CERTIFICATES
		} else {
			@Suppress("DEPRECATION")
			PackageManager.GET_SIGNATURES
		}
		val packageInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags) ?: return emptySet()
		return extractSigningDigests(packageInfo)
	}

	private fun extractSigningDigests(packageInfo: PackageInfo): Set<String> {
		val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			val signingInfo = packageInfo.signingInfo ?: return emptySet()
			if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
		} else {
			@Suppress("DEPRECATION")
			packageInfo.signatures
		}
		if (signatures.isNullOrEmpty()) return emptySet()
		return signatures.map { signature ->
			val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
			digest.joinToString("") { "%02x".format(it) }
		}.toSet()
	}

	private fun PackageManager.getPackageInfoCompat(targetPackageName: String): PackageInfo? {
		return try {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				getPackageInfo(targetPackageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
			} else {
				@Suppress("DEPRECATION")
				getPackageInfo(targetPackageName, PackageManager.GET_SIGNATURES)
			}
		} catch (_: Exception) {
			null
		}
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

