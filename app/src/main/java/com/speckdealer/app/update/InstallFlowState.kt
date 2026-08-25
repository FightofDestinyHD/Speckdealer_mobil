package com.speckdealer.app.update

enum class InstallStage {
	NONE,
	UPDATE_AVAILABLE,
	DOWNLOADING,
	DOWNLOAD_COMPLETED,
	INSTALL_WAITING_USER,
	INSTALL_STARTED,
	INSTALL_SUCCESS,
	INSTALL_FAILED,
	INSTALL_CANCELLED
}

data class PendingInstallState(
	val stage: InstallStage = InstallStage.NONE,
	val expectedVersionCode: Long = 0L,
	val expectedVersionName: String = "",
	val apkPath: String = "",
	val startedAtUtcMs: Long = 0L,
	val lastMessage: String = ""
)

enum class InstallReconcileAction {
	KEEP_PENDING,
	MARK_SUCCESS_AND_CLEAR,
	MARK_CANCELLED_AND_CLEAR
}

fun decideInstallReconcile(
	installedVersionCode: Long,
	expectedVersionCode: Long,
	hasPendingInstall: Boolean
): InstallReconcileAction {
	if (!hasPendingInstall) return InstallReconcileAction.KEEP_PENDING
	if (expectedVersionCode <= 0L) return InstallReconcileAction.MARK_CANCELLED_AND_CLEAR
	if (installedVersionCode >= expectedVersionCode) return InstallReconcileAction.MARK_SUCCESS_AND_CLEAR
	return InstallReconcileAction.KEEP_PENDING
}

fun shouldShowInstallPrompt(
	pending: PendingInstallState,
	installedVersionCode: Long,
	apkExists: Boolean,
	apkReadable: Boolean
): Boolean {
	if (pending.stage != InstallStage.INSTALL_WAITING_USER) return false
	if (pending.expectedVersionCode > 0 && installedVersionCode >= pending.expectedVersionCode) return false
	if (!apkExists || !apkReadable) return false
	return true
}
