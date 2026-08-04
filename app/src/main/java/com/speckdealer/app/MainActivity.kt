package com.speckdealer.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

class MainActivity : AppCompatActivity() {

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)
	}

	override fun onResume() {
		super.onResume()
		checkForImmediateUpdate()
	}

	private fun checkForImmediateUpdate() {
		val appUpdateManager = AppUpdateManagerFactory.create(this)
		val appUpdateInfoTask = appUpdateManager.appUpdateInfo

		appUpdateInfoTask.addOnSuccessListener { info ->
			if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
				info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
			) {
				Snackbar.make(
					findViewById(android.R.id.content),
					getString(R.string.update_available),
					Snackbar.LENGTH_LONG
				).show()
			}
		}
	}
}
