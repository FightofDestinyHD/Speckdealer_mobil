package com.speckdealer.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

class MainActivity : AppCompatActivity() {

	private lateinit var appUpdateManager: AppUpdateManager
	private var cachedUpdateInfo: AppUpdateInfo? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
		appUpdateManager = AppUpdateManagerFactory.create(this)
		setupUpdateTile()
		showChangelogIfUpdated()
		startIntroTransition()
	}

	override fun onResume() {
		super.onResume()
		if (isInstalledFromPlayStore()) {
			checkForImmediateUpdate()
		}
	}

	private fun setupUpdateTile() {
		findViewById<View>(R.id.updateTile).setOnClickListener {
			if (isInstalledFromPlayStore()) {
				startImmediateUpdateIfAvailable()
			} else {
				openLatestReleasePage()
			}
		}
	}

	private fun showChangelogIfUpdated() {
		val preferences = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
		val currentVersionCode = packageManager.getPackageInfo(packageName, 0).longVersionCode
		val lastVersionCode = preferences.getLong(KEY_LAST_VERSION_CODE, 0L)

		if (lastVersionCode != 0L && currentVersionCode > lastVersionCode) {
			AlertDialog.Builder(this)
				.setTitle(R.string.changelog_title)
				.setMessage(getString(R.string.changelog_message, BuildConfig.VERSION_NAME))
				.setPositiveButton(android.R.string.ok, null)
				.show()
		}

		preferences.edit().putLong(KEY_LAST_VERSION_CODE, currentVersionCode).apply()
	}

	private fun startIntroTransition() {
		val welcomeContainer = findViewById<View>(R.id.welcomeContainer)
		val menuContainer = findViewById<View>(R.id.menuContainer)

		welcomeContainer.animate()
			.alpha(0f)
			.setStartDelay(1300)
			.setDuration(700)
			.withEndAction {
				welcomeContainer.visibility = View.GONE
				menuContainer.visibility = View.VISIBLE
				menuContainer.alpha = 0f
				menuContainer.animate()
					.alpha(1f)
					.setDuration(450)
					.start()
			}
			.start()
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
				openLatestReleasePage()
			}
		}.addOnFailureListener {
			openLatestReleasePage()
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

	private fun openLatestReleasePage() {
		val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL))
		startActivity(intent)
		Snackbar.make(
			findViewById(android.R.id.content),
			getString(R.string.update_external_info),
			Snackbar.LENGTH_LONG
		).show()
	}

	private fun isImmediateUpdateAvailable(info: AppUpdateInfo): Boolean {
		return info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
			info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
	}

	private fun isInstalledFromPlayStore(): Boolean {
		val installer = packageManager.getInstallerPackageName(packageName)
		return installer == "com.android.vending"
	}

	companion object {
		private const val UPDATE_REQUEST_CODE = 1001
		private const val PREFERENCES_NAME = "speckdealer_prefs"
		private const val KEY_LAST_VERSION_CODE = "last_version_code"
		private const val RELEASES_URL = "https://github.com/FightofDestinyHD/Speckdealer_mobil/releases/latest"
	}
}
