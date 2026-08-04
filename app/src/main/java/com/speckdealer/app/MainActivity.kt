package com.speckdealer.app

import android.os.Bundle
import android.view.View
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
		startIntroTransition()
	}

	override fun onResume() {
		super.onResume()
		checkForImmediateUpdate()
	}

	private fun setupUpdateTile() {
		findViewById<View>(R.id.updateTile).setOnClickListener {
			startImmediateUpdateIfAvailable()
		}
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
				Snackbar.make(
					findViewById(android.R.id.content),
					getString(R.string.no_update_available),
					Snackbar.LENGTH_SHORT
				).show()
			}
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

	private fun isImmediateUpdateAvailable(info: AppUpdateInfo): Boolean {
		return info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
			info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
	}

	companion object {
		private const val UPDATE_REQUEST_CODE = 1001
	}
}
