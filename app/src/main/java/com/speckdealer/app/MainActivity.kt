package com.speckdealer.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.lifecycleScope
import com.speckdealer.app.update.InstallReconcileAction
import com.speckdealer.app.update.InstallStage
import com.speckdealer.app.update.PendingInstallState
import com.speckdealer.app.update.decideInstallReconcile
import com.speckdealer.app.update.shouldShowInstallPrompt
import com.speckdealer.app.update.snapshotArchiveApk
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
import kotlinx.coroutines.launch
import kotlin.concurrent.thread
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

	private var appUpdateManager: AppUpdateManager? = null
	private var cachedUpdateInfo: AppUpdateInfo? = null
	private var pendingApkFile: File? = null
	private var installPermissionDialog: AlertDialog? = null
	private var installPromptDialog: AlertDialog? = null
	private var depositReturnInProgress = false
	private var installFlowState: PendingInstallState = PendingInstallState()
	private val updatePrefs by lazy { getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE) }

	private data class ReleaseAsset(
		val apkUrl: String,
		val checksumUrl: String,
		val assetName: String,
		val releaseTag: String,
		val releaseName: String
	)

	private data class ApkValidationResult(
		val isValid: Boolean,
		val errorMessage: String? = null,
		val diagnosticMessage: String
	)

	private data class InstalledAppMetadata(
		val packageName: String,
		val versionCode: Long,
		val versionName: String
	)

	enum class DepositReturnType(
		val storageValue: String,
		val displayName: String,
		val amountLookupTokens: List<String>,
		val missingAmountMessage: String
	) {
		BOTTLE(
			storageValue = "BOTTLE",
			displayName = "Flasche",
			amountLookupTokens = listOf("flasche", "bottle"),
			missingAmountMessage = "Für Flaschenpfand ist aktuell kein Betrag konfiguriert. Bitte lege den Pfandbetrag in der Artikelverwaltung fest."
		),
		GLASS(
			storageValue = "GLASS",
			displayName = "Glas",
			amountLookupTokens = listOf("glas", "glass", "glass_01", "glass_02"),
			missingAmountMessage = "Für Glaspfand ist aktuell kein Betrag konfiguriert. Bitte lege den Pfandbetrag in der Artikelverwaltung fest."
		),
		PLATE(
			storageValue = "PLATE",
			displayName = "Teller",
			amountLookupTokens = listOf("teller", "plate"),
			missingAmountMessage = "Für Tellerpfand ist aktuell kein Betrag konfiguriert. Bitte lege den Pfandbetrag in der Artikelverwaltung fest."
		)
	}

	companion object {
		private const val UPDATE_REQUEST_CODE = 1001
		private const val REQUEST_INSTALL_PERMISSION = 1002
		private const val PREFERENCES_NAME = "speckdealer_prefs"
		private const val KEY_LAST_VERSION_CODE = "last_version_code"
		private const val KEY_LAST_VERSION_NAME = "last_version_name"
		private const val KEY_INSTALL_STAGE = "install_stage"
		private const val KEY_INSTALL_EXPECTED_CODE = "install_expected_code"
		private const val KEY_INSTALL_EXPECTED_NAME = "install_expected_name"
		private const val KEY_INSTALL_APK_PATH = "install_apk_path"
		private const val KEY_INSTALL_STARTED_AT = "install_started_at"
		private const val KEY_INSTALL_MESSAGE = "install_message"
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
			installFlowState = loadInstallFlowState()
			DevModeConfig.setDevEntryEnabled(BuildConfig.ENABLE_DEV_MODE)
			setupMenuTiles()
			showChangelogIfUpdated()
			startIntroTransition()
			StartupCrashLogger.logEvent(this, "MainActivity UI init fertig | logs=" + StartupCrashLogger.getKnownPaths(this).joinToString("; "))
		} catch (e: Exception) {
			StartupCrashLogger.logEvent(this, "MainActivity UI init Fehler", e)
			e.printStackTrace()
		}
	}

	override fun onStart() {
		super.onStart()
		reconcileInstallFlowState("onStart")
	}

	override fun onStop() {
		closeInstallDialogs("onStop")
		super.onStop()
	}

	override fun onNewIntent(intent: Intent?) {
		super.onNewIntent(intent)
		reconcileInstallFlowState("onNewIntent")
	}

	override fun onResume() {
		super.onResume()
		StartupCrashLogger.logEvent(this, "MainActivity.onResume")
		try {
			reconcileInstallFlowState("onResume")
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
			val updateTile = findViewById<View?>(R.id.updateTile)
			val archiveTile = findViewById<View?>(R.id.archiveTile)
			val deviceSyncTile = findViewById<View?>(R.id.deviceSyncTile)
			val devModeEntry = findViewById<TextView?>(R.id.devModeEntry)
			val versionInfoText = findViewById<TextView?>(R.id.versionInfoText)

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
			if (archiveTile == null) {
				StartupCrashLogger.logEvent(this, "setupMenuTiles: archiveTile fehlt im Layout")
			}
			if (deviceSyncTile == null) {
				StartupCrashLogger.logEvent(this, "setupMenuTiles: deviceSyncTile fehlt im Layout")
			}
			if (devModeEntry == null) {
				StartupCrashLogger.logEvent(this, "setupMenuTiles: devModeEntry fehlt im Layout")
			}
			if (versionInfoText == null) {
				StartupCrashLogger.logEvent(this, "setupMenuTiles: versionInfoText fehlt im Layout")
			}

			salesTile?.setOnClickListener {
				try {
					startActivity(Intent(this, SalesActivity::class.java).putExtra(AppDataMode.EXTRA_DATA_MODE, AppDataMode.MODE_PRODUCTION))
				} catch (e: Exception) {
					StartupCrashLogger.logEvent(this, "SalesActivity öffnen Fehler", e)
					e.printStackTrace()
					Snackbar.make(findViewById(android.R.id.content), "Fehler beim Öffnen: ${e.message}", Snackbar.LENGTH_LONG).show()
				}
			}

			ordersTile?.setOnClickListener {
				try {
					startActivity(Intent(this, OrdersActivity::class.java).putExtra(AppDataMode.EXTRA_DATA_MODE, AppDataMode.MODE_PRODUCTION))
				} catch (e: Exception) {
					e.printStackTrace()
					Snackbar.make(findViewById(android.R.id.content), "Fehler beim Öffnen: ${e.message}", Snackbar.LENGTH_LONG).show()
				}
			}

			articleManagementTile?.setOnClickListener {
				try {
					startActivity(Intent(this, ArticleManagementActivity::class.java).putExtra(AppDataMode.EXTRA_DATA_MODE, AppDataMode.MODE_PRODUCTION))
				} catch (e: Exception) {
					StartupCrashLogger.logEvent(this, "ArticleManagementActivity öffnen Fehler", e)
					e.printStackTrace()
					Snackbar.make(findViewById(android.R.id.content), "Fehler beim Öffnen: ${e.message}", Snackbar.LENGTH_LONG).show()
				}
			}

			dailyReportTile?.setOnClickListener {
				try {
					startActivity(Intent(this, DailyReportActivity::class.java).putExtra(AppDataMode.EXTRA_DATA_MODE, AppDataMode.MODE_PRODUCTION))
				} catch (e: Exception) {
					StartupCrashLogger.logEvent(this, "DailyReportActivity öffnen Fehler", e)
					e.printStackTrace()
					Snackbar.make(findViewById(android.R.id.content), "Fehler beim Öffnen: ${e.message}", Snackbar.LENGTH_LONG).show()
				}
			}

			settingsTile?.setOnClickListener {
				try {
					startActivity(Intent(this, SettingsActivity::class.java).putExtra(AppDataMode.EXTRA_DATA_MODE, AppDataMode.MODE_PRODUCTION))
				} catch (e: Exception) {
					StartupCrashLogger.logEvent(this, "SettingsTile öffnen Fehler", e)
					e.printStackTrace()
					Snackbar.make(findViewById(android.R.id.content), "Fehler beim Öffnen: ${e.message}", Snackbar.LENGTH_LONG).show()
				}
			}

			archiveTile?.setOnClickListener {
				startActivity(Intent(this, ArchivedReportsActivity::class.java).putExtra(AppDataMode.EXTRA_DATA_MODE, AppDataMode.MODE_PRODUCTION))
			}

			deviceSyncTile?.setOnClickListener {
				startActivity(Intent(this, DeviceSyncActivity::class.java).putExtra(AppDataMode.EXTRA_DATA_MODE, AppDataMode.MODE_PRODUCTION))
			}

			devModeEntry?.let { entry ->
				entry.visibility = if (DevModeConfig.isDevEntryEnabled) View.VISIBLE else View.GONE
				entry.setOnClickListener { showDevModePasswordDialog() }
			}
			versionInfoText?.text = "Version ${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})"

			updateTile?.setOnClickListener {
				try {
					if (isInstalledFromPlayStore()) {
						startImmediateUpdateIfAvailable()
					} else {
						lifecycleScope.launch {
							runCatching {
								com.speckdealer.app.update.RuntimeUpdateFlow.downloadValidatedRelease(this@MainActivity)
							}.onSuccess { file ->
								file?.let { installApkFile(it) }
							}.onFailure { e ->
								StartupCrashLogger.logEvent(this@MainActivity, "Update-Start Fehler", e)
								val updateError = e.message?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName
								Snackbar.make(findViewById(android.R.id.content), "Fehler beim Update: $updateError", Snackbar.LENGTH_LONG).show()
							}
						}
					}
				} catch (e: Exception) {
					StartupCrashLogger.logEvent(this, "Update-Start Fehler", e)
					e.printStackTrace()
					val updateError = e.message?.takeIf { it.isNotBlank() } ?: e::class.java.simpleName
					Snackbar.make(findViewById(android.R.id.content), "Fehler beim Update: $updateError", Snackbar.LENGTH_LONG).show()
				}
			}
			StartupCrashLogger.logEvent(this, "setupMenuTiles fertig")
		} catch (e: Exception) {
			StartupCrashLogger.logEvent(this, "setupMenuTiles Fehler", e)
			e.printStackTrace()
		}
	}

	private fun showDevModePasswordDialog() {
		if (!DevModeConfig.isDevEntryEnabled) {
			Snackbar.make(findViewById(android.R.id.content), "Diese Funktion ist in diesem Build deaktiviert.", Snackbar.LENGTH_LONG).show()
			return
		}

		val input = EditText(this).apply {
			inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
			hint = "Passwort"
		}
		val dialog = AlertDialog.Builder(this)
			.setTitle("Dev-Modus")
			.setMessage("Passwort eingeben")
			.setView(input)
			.setPositiveButton("Öffnen", null)
			.setNegativeButton("Abbrechen", null)
			.create()

		dialog.setOnShowListener {
			val openButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
			openButton.setOnClickListener {
				when (val result = DevModeConfig.checkPassword(input.text?.toString().orEmpty())) {
					is DevPasswordCheckResult.Success -> {
						DevModeConfig.activateSession()
						dialog.dismiss()
						startActivity(Intent(this, DevModeActivity::class.java))
					}
					is DevPasswordCheckResult.Empty -> {
						input.error = "Bitte Passwort eingeben"
					}
					is DevPasswordCheckResult.Invalid -> {
						input.error = "Passwort ist nicht korrekt"
					}
					is DevPasswordCheckResult.Locked -> {
						openButton.isEnabled = false
						startLockCountdown(openButton, result.remainingMs)
					}
				}
			}
		}

		dialog.show()
	}

	private fun startLockCountdown(button: android.widget.Button, remainingMs: Long) {
		object : CountDownTimer(remainingMs, 1000L) {
			override fun onTick(millisUntilFinished: Long) {
				val seconds = (millisUntilFinished / 1000L).coerceAtLeast(1L)
				button.text = "Warten ($seconds s)"
			}

			override fun onFinish() {
				button.text = "Öffnen"
				button.isEnabled = true
			}
		}.start()
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
		val depositTypes = DepositReturnType.entries.toTypedArray()
		val labels = depositTypes.map { it.displayName }.toTypedArray()

		AlertDialog.Builder(this)
			.setTitle("Welcher Pfand soll zurückgegeben werden?")
			.setItems(labels) { _, selectedIndex ->
				val selectedType = depositTypes[selectedIndex]
				val unitAmountCents = resolveConfiguredDepositAmountCents(repository, selectedType)
				if (unitAmountCents == null) {
					Snackbar.make(findViewById(android.R.id.content), selectedType.missingAmountMessage, Snackbar.LENGTH_LONG).show()
					return@setItems
				}
				showDepositQuantityDialog(selectedType, unitAmountCents, depositStorage)
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun resolveConfiguredDepositAmountCents(
		repository: com.speckdealer.app.data.ArticleRepository,
		type: DepositReturnType
	): Long? {
		type.amountLookupTokens.forEach { token ->
			val article = repository.getDepositArticleForType(token)
			if (article != null && article.priceCents > 0) {
				return article.priceCents.toLong()
			}
		}

		val fallback = repository.getDepositArticles().firstOrNull { article ->
			val resolvedType = repository.resolveDepositType(article)
			article.priceCents > 0 && type.amountLookupTokens.any { token ->
				token.equals(resolvedType, ignoreCase = true) || article.name.contains(token, ignoreCase = true)
			}
		}
		return fallback?.priceCents?.toLong()
	}

	private fun showDepositQuantityDialog(
		selectedType: DepositReturnType,
		unitAmountCents: Long,
		depositStorage: DepositMovementStorage
	) {
		if (unitAmountCents <= 0L) {
			Snackbar.make(findViewById(android.R.id.content), selectedType.missingAmountMessage, Snackbar.LENGTH_LONG).show()
			return
		}
		val input = EditText(this).apply {
			inputType = InputType.TYPE_CLASS_NUMBER
			hint = "Anzahl"
		}
		AlertDialog.Builder(this)
			.setTitle("Pfandrückgabe")
			.setMessage("Pfandart: ${selectedType.displayName}\nEinzelbetrag: ${MoneyValueService.formatCents(unitAmountCents)}")
			.setView(input)
			.setPositiveButton("Weiter") { _, _ ->
				val quantityText = input.text?.toString()?.trim().orEmpty()
				val quantity = quantityText.toIntOrNull()
				if (quantity == null || quantity <= 0 || quantity > 10_000) {
					Snackbar.make(findViewById(android.R.id.content), "Bitte eine gültige positive Anzahl eingeben.", Snackbar.LENGTH_LONG).show()
					return@setPositiveButton
				}
				val total = unitAmountCents * quantity.toLong()
				showDepositReturnConfirmation(selectedType, quantity, unitAmountCents, total, depositStorage)
			}
			.setNegativeButton("Abbrechen", null)
			.show()
	}

	private fun showDepositReturnConfirmation(
		selectedType: DepositReturnType,
		quantity: Int,
		unitAmountCents: Long,
		totalAmountCents: Long,
		depositStorage: DepositMovementStorage
	) {
		val summary = buildString {
			appendLine("Pfandart: ${selectedType.displayName}")
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
							depositType = selectedType.storageValue,
							displayName = selectedType.displayName,
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
			val menuScrollContainer = findViewById<View?>(R.id.menuScrollContainer)

			welcomeContainer.animate()
				.alpha(0f)
				.setStartDelay(1300)
				.setDuration(700)
				.withEndAction {
					try {
						welcomeContainer.visibility = View.GONE
						menuContainer.visibility = View.VISIBLE
						menuScrollContainer?.visibility = View.VISIBLE
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
			Snackbar.make(
				findViewById(android.R.id.content),
				"Play-Updateprüfung nicht verfügbar. Kein Fallback auf GitHub bei Play-Installation.",
				Snackbar.LENGTH_LONG
			).show()
			return
		}

		manager.appUpdateInfo.addOnSuccessListener { freshInfo ->
			cachedUpdateInfo = freshInfo
			if (isImmediateUpdateAvailable(freshInfo)) {
				startImmediateUpdate(freshInfo)
			} else {
				Snackbar.make(
					findViewById(android.R.id.content),
					getString(R.string.no_update_available),
					Snackbar.LENGTH_LONG
				).show()
			}
		}.addOnFailureListener {
			Snackbar.make(
				findViewById(android.R.id.content),
				"Play-Updateprüfung fehlgeschlagen. Kein Fallback auf GitHub bei Play-Installation.",
				Snackbar.LENGTH_LONG
			).show()
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


	private fun installApkFile(apkFile: File) {
		if (isFinishing || isDestroyed) return
		val downloaded = snapshotArchiveApk(packageManager, apkFile)
		if (downloaded == null) {
			Snackbar.make(findViewById(android.R.id.content), "APK-Metadaten konnten nicht gelesen werden.", Snackbar.LENGTH_LONG).show()
			return
		}
		installFlowState = PendingInstallState(
			stage = InstallStage.INSTALL_WAITING_USER,
			expectedVersionCode = downloaded.versionCode,
			expectedVersionName = downloaded.versionName,
			apkPath = apkFile.absolutePath,
			startedAtUtcMs = System.currentTimeMillis(),
			lastMessage = "Installation wartet auf Bestätigung"
		)
		persistInstallFlowState(installFlowState)
		pendingApkFile = apkFile
		// Ab Android 8: Berechtigung für unbekannte Quellen prüfen
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			if (!packageManager.canRequestPackageInstalls()) {
				showInstallPermissionDialog()
				return
			}
		}
		showInstallReadyDialog(apkFile)
	}

	private fun doInstallApk(apkFile: File) {
		try {
			if (!apkFile.exists() || !apkFile.canRead()) {
				installFlowState = installFlowState.copy(stage = InstallStage.INSTALL_FAILED, lastMessage = "APK nicht lesbar")
				persistInstallFlowState(installFlowState)
				Snackbar.make(findViewById(android.R.id.content), "APK-Datei fehlt oder ist nicht lesbar.", Snackbar.LENGTH_LONG).show()
				return
			}
			installFlowState = installFlowState.copy(stage = InstallStage.INSTALL_STARTED, lastMessage = "Installation gestartet")
			persistInstallFlowState(installFlowState)
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
			installFlowState = installFlowState.copy(stage = InstallStage.INSTALL_FAILED, lastMessage = e.message.orEmpty())
			persistInstallFlowState(installFlowState)
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
			StartupCrashLogger.logEvent(this, "InstallFlow activityResult install-permission received")
			val apk = pendingApkFile
			if (apk != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
				&& packageManager.canRequestPackageInstalls()) {
				doInstallApk(apk)
			} else {
				installFlowState = installFlowState.copy(stage = InstallStage.INSTALL_CANCELLED, lastMessage = "Installation nicht erlaubt")
				persistInstallFlowState(installFlowState)
			}
		}
	}


	private fun showInstallPermissionDialog() {
		if (isFinishing || isDestroyed) return
		if (installFlowState.stage == InstallStage.INSTALL_SUCCESS) return
		installPermissionDialog?.dismiss()
		installPermissionDialog = AlertDialog.Builder(this)
			.setTitle("Installation erlauben")
			.setMessage("Bitte erlaube die Installation aus unbekannten Quellen für diese App, dann wird das Update automatisch installiert.")
			.setPositiveButton("Einstellungen öffnen") { _, _ ->
				val intent = Intent(
					Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
					Uri.parse("package:$packageName")
				)
				startActivityForResult(intent, REQUEST_INSTALL_PERMISSION)
			}
			.setNegativeButton("Abbrechen") { _, _ ->
				installFlowState = installFlowState.copy(stage = InstallStage.INSTALL_CANCELLED, lastMessage = "Vom Nutzer abgebrochen")
				persistInstallFlowState(installFlowState)
			}
			.setOnDismissListener {
				installPermissionDialog = null
			}
			.create()
		installPermissionDialog?.show()
	}

	private fun showInstallReadyDialog(apkFile: File) {
		if (isFinishing || isDestroyed) return
		val installedVersionCode = runCatching {
			PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))
		}.getOrDefault(0L)
		val shouldShow = shouldShowInstallPrompt(
			pending = installFlowState,
			installedVersionCode = installedVersionCode,
			apkExists = apkFile.exists(),
			apkReadable = apkFile.canRead()
		)
		if (!shouldShow) {
			reconcileInstallFlowState("showInstallReadyDialog")
			return
		}
		installPromptDialog?.dismiss()
		installPromptDialog = AlertDialog.Builder(this)
			.setTitle("Update installieren")
			.setMessage("Die APK ist geprüft und bereit. Jetzt Installation starten?")
			.setPositiveButton("Installieren") { _, _ ->
				StartupCrashLogger.logEvent(this, "InstallFlow user action: install clicked")
				doInstallApk(apkFile)
			}
			.setNegativeButton("Später") { _, _ ->
				StartupCrashLogger.logEvent(this, "InstallFlow user action: postponed")
				persistInstallFlowState(installFlowState.copy(stage = InstallStage.INSTALL_WAITING_USER, lastMessage = "Installation zurückgestellt"))
			}
			.setOnDismissListener {
				installPromptDialog = null
			}
			.create()
		installPromptDialog?.show()
	}

	private fun loadInstallFlowState(): PendingInstallState {
		val stageName = updatePrefs.getString(KEY_INSTALL_STAGE, InstallStage.NONE.name).orEmpty()
		val stage = runCatching { InstallStage.valueOf(stageName) }.getOrDefault(InstallStage.NONE)
		return PendingInstallState(
			stage = stage,
			expectedVersionCode = updatePrefs.getLong(KEY_INSTALL_EXPECTED_CODE, 0L),
			expectedVersionName = updatePrefs.getString(KEY_INSTALL_EXPECTED_NAME, "").orEmpty(),
			apkPath = updatePrefs.getString(KEY_INSTALL_APK_PATH, "").orEmpty(),
			startedAtUtcMs = updatePrefs.getLong(KEY_INSTALL_STARTED_AT, 0L),
			lastMessage = updatePrefs.getString(KEY_INSTALL_MESSAGE, "").orEmpty()
		)
	}

	private fun persistInstallFlowState(state: PendingInstallState) {
		StartupCrashLogger.logEvent(
			this,
			"InstallFlow persist | stage=${state.stage} | expectedCode=${state.expectedVersionCode} | apkPathSet=${state.apkPath.isNotBlank()} | msg=${state.lastMessage.take(80)}"
		)
		installFlowState = state
		updatePrefs.edit()
			.putString(KEY_INSTALL_STAGE, state.stage.name)
			.putLong(KEY_INSTALL_EXPECTED_CODE, state.expectedVersionCode)
			.putString(KEY_INSTALL_EXPECTED_NAME, state.expectedVersionName)
			.putString(KEY_INSTALL_APK_PATH, state.apkPath)
			.putLong(KEY_INSTALL_STARTED_AT, state.startedAtUtcMs)
			.putString(KEY_INSTALL_MESSAGE, state.lastMessage)
			.apply()
	}

	private fun clearInstallFlowState() {
		StartupCrashLogger.logEvent(this, "InstallFlow clear pending status")
		pendingApkFile = null
		closeInstallDialogs("clearInstallFlowState")
		installFlowState = PendingInstallState(stage = InstallStage.NONE)
		updatePrefs.edit()
			.remove(KEY_INSTALL_STAGE)
			.remove(KEY_INSTALL_EXPECTED_CODE)
			.remove(KEY_INSTALL_EXPECTED_NAME)
			.remove(KEY_INSTALL_APK_PATH)
			.remove(KEY_INSTALL_STARTED_AT)
			.remove(KEY_INSTALL_MESSAGE)
			.apply()
	}

	private fun reconcileInstallFlowState(source: String) {
		val previous = installFlowState
		val installedInfo = runCatching {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
			} else {
				@Suppress("DEPRECATION")
				packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
			}
		}.getOrNull()
		val installedVersionCode = installedInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
		val installedVersionName = installedInfo?.versionName.orEmpty().ifBlank { "(leer)" }
		StartupCrashLogger.logEvent(
			this,
			"InstallFlow reconcile($source) | stage=${installFlowState.stage} | expectedCode=${installFlowState.expectedVersionCode} | installedCode=$installedVersionCode"
		)
		val action = decideInstallReconcile(
			installedVersionCode = installedVersionCode,
			expectedVersionCode = installFlowState.expectedVersionCode,
			hasPendingInstall = installFlowState.stage != InstallStage.NONE
		)
		if (previous.stage == InstallStage.INSTALL_STARTED && installedVersionCode < previous.expectedVersionCode) {
			StartupCrashLogger.logEvent(this, "InstallFlow callback possibly pending/absent after return: expected=${previous.expectedVersionCode} installed=$installedVersionCode")
		}
		when (action) {
			InstallReconcileAction.MARK_SUCCESS_AND_CLEAR -> {
				StartupCrashLogger.logEvent(this, "Installationsstatus bereinigt ($source): erfolgreich installiert code=$installedVersionCode name=$installedVersionName")
				Snackbar.make(findViewById(android.R.id.content), "Update erfolgreich installiert (Code $installedVersionCode).", Snackbar.LENGTH_LONG).show()
				clearInstallFlowState()
			}
			InstallReconcileAction.MARK_CANCELLED_AND_CLEAR -> {
				StartupCrashLogger.logEvent(this, "Installationsstatus bereinigt ($source): ungültiger Pending-Status")
				clearInstallFlowState()
			}
			InstallReconcileAction.KEEP_PENDING -> {
				val apk = installFlowState.apkPath.takeIf { it.isNotBlank() }?.let { File(it) }
				val shouldShow = shouldShowInstallPrompt(
					pending = installFlowState,
					installedVersionCode = installedVersionCode,
					apkExists = apk?.exists() == true,
					apkReadable = apk?.canRead() == true
				)
				if (!shouldShow && installFlowState.stage == InstallStage.INSTALL_WAITING_USER) {
					persistInstallFlowState(installFlowState.copy(stage = InstallStage.INSTALL_CANCELLED, lastMessage = "APK nicht mehr verfügbar"))
				}
			}
		}
	}

	private fun closeInstallDialogs(source: String) {
		if (installPermissionDialog != null || installPromptDialog != null) {
			StartupCrashLogger.logEvent(this, "InstallFlow close dialogs ($source)")
		}
		installPermissionDialog?.dismiss()
		installPermissionDialog = null
		installPromptDialog?.dismiss()
		installPromptDialog = null
	}

	override fun onDestroy() {
		closeInstallDialogs("onDestroy")
		super.onDestroy()
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

