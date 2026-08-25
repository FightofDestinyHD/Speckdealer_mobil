package com.speckdealer.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallFlowStateTest {

	@Test
	fun reconcile_marksSuccess_whenInstalledVersionReachedExpected() {
		val action = decideInstallReconcile(
			installedVersionCode = 69,
			expectedVersionCode = 69,
			hasPendingInstall = true
		)
		assertEquals(InstallReconcileAction.MARK_SUCCESS_AND_CLEAR, action)
	}

	@Test
	fun reconcile_keepsPending_whenExpectedNotInstalledYet() {
		val action = decideInstallReconcile(
			installedVersionCode = 68,
			expectedVersionCode = 69,
			hasPendingInstall = true
		)
		assertEquals(InstallReconcileAction.KEEP_PENDING, action)
	}

	@Test
	fun reconcile_clearsCancelled_whenExpectedInvalid() {
		val action = decideInstallReconcile(
			installedVersionCode = 68,
			expectedVersionCode = 0,
			hasPendingInstall = true
		)
		assertEquals(InstallReconcileAction.MARK_CANCELLED_AND_CLEAR, action)
	}

	@Test
	fun showInstallPrompt_onlyWhenPendingAndApkValidAndNotInstalled() {
		val pending = PendingInstallState(
			stage = InstallStage.INSTALL_WAITING_USER,
			expectedVersionCode = 69,
			apkPath = "x.apk"
		)
		assertTrue(shouldShowInstallPrompt(pending, installedVersionCode = 68, apkExists = true, apkReadable = true))
		assertFalse(shouldShowInstallPrompt(pending, installedVersionCode = 69, apkExists = true, apkReadable = true))
		assertFalse(shouldShowInstallPrompt(pending, installedVersionCode = 68, apkExists = false, apkReadable = true))
		assertFalse(shouldShowInstallPrompt(pending, installedVersionCode = 68, apkExists = true, apkReadable = false))
	}

	@Test
	fun showInstallPrompt_falseForNonWaitingStages() {
		val pending = PendingInstallState(
			stage = InstallStage.INSTALL_STARTED,
			expectedVersionCode = 69,
			apkPath = "x.apk"
		)
		assertFalse(shouldShowInstallPrompt(pending, installedVersionCode = 68, apkExists = true, apkReadable = true))
	}

	@Test
	fun reconcile_keepsWhenNoPendingInstall() {
		val action = decideInstallReconcile(
			installedVersionCode = 69,
			expectedVersionCode = 69,
			hasPendingInstall = false
		)
		assertEquals(InstallReconcileAction.KEEP_PENDING, action)
	}
}
