package com.speckdealer.app.update

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

enum class UpdateStatus {
	CHECK_RELEASE,
	DOWNLOAD_APK,
	CHECK_CHECKSUM,
	CHECK_METADATA,
	CHECK_SIGNATURE,
	UPDATE_READY,
	INSTALLATION_WAITING_FOR_USER,
	UPDATE_STARTED,
	NO_NEW_VERSION,
	ERROR
}

data class ReleaseAssetInfo(
	val releaseTag: String,
	val releaseName: String,
	val apkAssetName: String,
	val apkUrl: String,
	val checksumUrl: String,
	val draft: Boolean,
	val prerelease: Boolean
)

data class ApkSnapshot(
	val packageName: String,
	val versionCode: Long,
	val versionName: String,
	val certificateDigests: Set<String>,
	val sha256: String? = null
)

data class UpdateCheckResult(
	val status: UpdateStatus,
	val message: String,
	val diagnosticMessage: String,
	val installedVersionCode: Long,
	val downloadedVersionCode: Long,
	val installedCertificateDigest: String? = null,
	val downloadedCertificateDigest: String? = null
)

fun parseLatestReleaseAsset(json: String, expectedAssetName: String = "app-release.apk"): ReleaseAssetInfo? {
	val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
	if (root.optBoolean("draft", false) || root.optBoolean("prerelease", false)) return null
	val releaseTag = root.optString("tag_name", "")
	val releaseName = root.optString("name", "")
	val assets = root.optJSONArray("assets") ?: return null
	var apkUrl: String? = null
	var apkName: String? = null
	for (i in 0 until assets.length()) {
		val asset = assets.optJSONObject(i) ?: continue
		val name = asset.optString("name")
		if (!name.equals(expectedAssetName, ignoreCase = true)) continue
		val url = asset.optString("browser_download_url")
		if (url.isBlank()) continue
		apkUrl = url
		apkName = name
		break
	}
	if (apkUrl.isNullOrBlank() || apkName.isNullOrBlank()) return null
	val checksumName = "$apkName.sha256"
	var checksumUrl: String? = null
	for (i in 0 until assets.length()) {
		val asset = assets.optJSONObject(i) ?: continue
		if (!asset.optString("name").equals(checksumName, ignoreCase = true)) continue
		val url = asset.optString("browser_download_url")
		if (url.isNotBlank()) {
			checksumUrl = url
			break
		}
	}
	if (checksumUrl.isNullOrBlank()) return null
	return ReleaseAssetInfo(
		releaseTag = releaseTag,
		releaseName = releaseName,
		apkAssetName = apkName,
		apkUrl = apkUrl,
		checksumUrl = checksumUrl,
		draft = root.optBoolean("draft", false),
		prerelease = root.optBoolean("prerelease", false)
	)
}

fun sha256OfFile(file: File): String {
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

fun loadArchivePackageInfo(packageManager: PackageManager, apkFile: File): PackageInfo? {
	val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
		PackageManager.GET_SIGNING_CERTIFICATES
	} else {
		@Suppress("DEPRECATION")
		PackageManager.GET_SIGNATURES
	}
	return packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
}

fun loadInstalledPackageInfo(packageManager: PackageManager, packageName: String): PackageInfo? {
	return try {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
		} else {
			@Suppress("DEPRECATION")
			packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
		}
	} catch (_: Exception) {
		null
	}
}

fun extractSigningDigests(packageInfo: PackageInfo): Set<String> {
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

fun snapshotInstalledApp(packageManager: PackageManager, packageName: String): ApkSnapshot? {
	val packageInfo = loadInstalledPackageInfo(packageManager, packageName) ?: return null
	return ApkSnapshot(
		packageName = packageName,
		versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
		versionName = packageInfo.versionName.orEmpty().ifBlank { "(leer)" },
		certificateDigests = extractSigningDigests(packageInfo)
	)
}

fun snapshotArchiveApk(packageManager: PackageManager, apkFile: File, sha256: String? = null): ApkSnapshot? {
	val packageInfo = loadArchivePackageInfo(packageManager, apkFile) ?: return null
	return ApkSnapshot(
		packageName = packageInfo.packageName.orEmpty(),
		versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
		versionName = packageInfo.versionName.orEmpty().ifBlank { "(leer)" },
		certificateDigests = extractSigningDigests(packageInfo),
		sha256 = sha256?.trim()?.lowercase()
	)
}

private fun singleCertificateDigestOrNull(digests: Set<String>): String? {
	return digests.singleOrNull()?.trim()?.lowercase()?.ifBlank { null }
}

fun evaluateUpdateCompatibility(
	expectedPackageName: String,
	installed: ApkSnapshot,
	downloaded: ApkSnapshot
): UpdateCheckResult {
	val installedDigest = singleCertificateDigestOrNull(installed.certificateDigests)
	val downloadedDigest = singleCertificateDigestOrNull(downloaded.certificateDigests)
	val diagnostic = buildString {
		append("Installiert: ")
		append(installed.packageName)
		append(" | versionCode=")
		append(installed.versionCode)
		append(" | versionName=")
		append(installed.versionName)
		append(" | cert=")
		append(installed.certificateDigests.joinToString(",").ifBlank { "(leer)" })
		append(" | digest=")
		append(installedDigest ?: "(mehrdeutig/leer)")
		append('\n')
		append("Heruntergeladen: ")
		append(downloaded.packageName)
		append(" | versionCode=")
		append(downloaded.versionCode)
		append(" | versionName=")
		append(downloaded.versionName)
		append(" | cert=")
		append(downloaded.certificateDigests.joinToString(",").ifBlank { "(leer)" })
		append(" | digest=")
		append(downloadedDigest ?: "(mehrdeutig/leer)")
	}

	if (downloaded.packageName != expectedPackageName) {
		return UpdateCheckResult(
			status = UpdateStatus.ERROR,
			message = "Update abgebrochen: Das heruntergeladene Paket gehört nicht zu $expectedPackageName.",
			diagnosticMessage = diagnostic,
			installedVersionCode = installed.versionCode,
			downloadedVersionCode = downloaded.versionCode,
			installedCertificateDigest = installedDigest,
			downloadedCertificateDigest = downloadedDigest
		)
	}

	if (downloaded.versionCode <= installed.versionCode) {
		return UpdateCheckResult(
			status = UpdateStatus.NO_NEW_VERSION,
			message = "Keine neuere Version verfügbar. Installiert: ${installed.versionCode}, verfügbar: ${downloaded.versionCode}.",
			diagnosticMessage = diagnostic,
			installedVersionCode = installed.versionCode,
			downloadedVersionCode = downloaded.versionCode,
			installedCertificateDigest = installedDigest,
			downloadedCertificateDigest = downloadedDigest
		)
	}

	if (installedDigest == null || downloadedDigest == null) {
		return UpdateCheckResult(
			status = UpdateStatus.ERROR,
			message = "Update abgebrochen: Der öffentliche SHA-256-Zertifikatsdigest konnte nicht eindeutig gelesen werden.",
			diagnosticMessage = diagnostic,
			installedVersionCode = installed.versionCode,
			downloadedVersionCode = downloaded.versionCode,
			installedCertificateDigest = installedDigest,
			downloadedCertificateDigest = downloadedDigest
		)
	}

	if (downloadedDigest != installedDigest) {
		return UpdateCheckResult(
			status = UpdateStatus.ERROR,
			message = "Update abgebrochen: Die Signatur der heruntergeladenen APK passt nicht zur installierten App.",
			diagnosticMessage = diagnostic,
			installedVersionCode = installed.versionCode,
			downloadedVersionCode = downloaded.versionCode,
			installedCertificateDigest = installedDigest,
			downloadedCertificateDigest = downloadedDigest
		)
	}

	return UpdateCheckResult(
		status = UpdateStatus.UPDATE_READY,
		message = "Update bereit. Installation kann gestartet werden.",
		diagnosticMessage = diagnostic,
		installedVersionCode = installed.versionCode,
		downloadedVersionCode = downloaded.versionCode,
		installedCertificateDigest = installedDigest,
		downloadedCertificateDigest = downloadedDigest
	)
}
