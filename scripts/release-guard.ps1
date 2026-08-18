param(
	[switch]$SkipBuild = $false,
	[switch]$AllowNoDevice = $false
)

$ErrorActionPreference = 'Stop'

function Fail([string]$message) {
	throw "RELEASE_GUARD_ABORT: $message"
}

function Get-ApkVersionCode([string]$aaptPath, [string]$apkPath) {
	if (-not (Test-Path $apkPath)) { return $null }
	$line = & $aaptPath dump badging $apkPath | Select-String -Pattern "^package:" | Select-Object -First 1
	if (-not $line) { return $null }
	$match = [regex]::Match($line.ToString(), "versionCode='(\d+)'")
	if (-not $match.Success) { return $null }
	return [int]$match.Groups[1].Value
}

function Get-ApkPackageId([string]$aaptPath, [string]$apkPath) {
	$line = & $aaptPath dump badging $apkPath | Select-String -Pattern "^package:" | Select-Object -First 1
	if (-not $line) { return "" }
	$match = [regex]::Match($line.ToString(), "name='([^']+)'")
	if (-not $match.Success) { return "" }
	return $match.Groups[1].Value
}

function Get-ApkSignerSha256([string]$apksignerPath, [string]$apkPath) {
	$out = & $apksignerPath verify --verbose --print-certs $apkPath
	$line = $out | Select-String -Pattern "Signer #1 certificate SHA-256 digest:" | Select-Object -First 1
	if (-not $line) { return "" }
	return ($line.ToString().Split(':')[-1]).Trim().ToLowerInvariant()
}

$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$aapt = Join-Path $sdk 'build-tools\34.0.0\aapt.exe'
$apksigner = Join-Path $sdk 'build-tools\34.0.0\apksigner.bat'
$adb = Join-Path $sdk 'platform-tools\adb.exe'

if (-not (Test-Path $aapt)) { Fail "aapt nicht gefunden: $aapt" }
if (-not (Test-Path $apksigner)) { Fail "apksigner nicht gefunden: $apksigner" }
if (-not (Test-Path $adb)) { Fail "adb nicht gefunden: $adb" }

$gradleFile = 'app/build.gradle.kts'
if (-not (Test-Path $gradleFile)) { Fail "build.gradle.kts fehlt" }
$gradleText = Get-Content $gradleFile -Raw
$sourceCodeMatch = [regex]::Match($gradleText, 'versionCode\s*=\s*(\d+)')
$sourceNameMatch = [regex]::Match($gradleText, 'versionName\s*=\s*"([^"]+)"')
$appIdMatch = [regex]::Match($gradleText, 'applicationId\s*=\s*"([^"]+)"')
if (-not $sourceCodeMatch.Success) { Fail "versionCode in build.gradle.kts nicht gefunden" }
if (-not $sourceNameMatch.Success) { Fail "versionName in build.gradle.kts nicht gefunden" }
if (-not $appIdMatch.Success) { Fail "applicationId in build.gradle.kts nicht gefunden" }

$sourceVersionCode = [int]$sourceCodeMatch.Groups[1].Value
$sourceVersionName = $sourceNameMatch.Groups[1].Value
$sourceAppId = $appIdMatch.Groups[1].Value
if ($sourceAppId -ne 'com.speckdealer.app') { Fail "ApplicationId ist nicht com.speckdealer.app ($sourceAppId)" }

$externalVersionCodes = New-Object System.Collections.Generic.List[int]

$lastTag = (& git tag --sort=-version:refname | Select-Object -First 1)
if ($lastTag) {
	$tagDigits = [regex]::Match($lastTag, '(\d+)$')
	if ($tagDigits.Success) { [void]$externalVersionCodes.Add([int]$tagDigits.Groups[1].Value) }
}

$releaseArtifactsApk = 'release-artifacts/app-release.apk'
$artifactCode = Get-ApkVersionCode -aaptPath $aapt -apkPath $releaseArtifactsApk
if ($artifactCode) { [void]$externalVersionCodes.Add($artifactCode) }

$tmpLatestDir = 'tmp/release-guard'
New-Item -ItemType Directory -Path $tmpLatestDir -Force | Out-Null
$tmpLatestApk = Join-Path $tmpLatestDir 'latest-release.apk'
$releaseInfo = Invoke-RestMethod -Uri 'https://api.github.com/repos/FightofDestinyHD/Speckdealer_mobil/releases/latest'
$latestApkUrl = ($releaseInfo.assets | Where-Object { $_.name -eq 'app-release.apk' } | Select-Object -First 1).browser_download_url
if ([string]::IsNullOrWhiteSpace($latestApkUrl)) { Fail 'GitHub latest release APK nicht gefunden' }
Invoke-WebRequest -Uri $latestApkUrl -OutFile $tmpLatestApk
$latestCode = Get-ApkVersionCode -aaptPath $aapt -apkPath $tmpLatestApk
if ($latestCode) { [void]$externalVersionCodes.Add($latestCode) }

$installedOrReferenceApk = ''
& $adb start-server | Out-Null
$deviceCount = ((& $adb devices) | Select-String -Pattern '\tdevice$' | Measure-Object).Count
if ($deviceCount -gt 0) {
	$pkgLine = (& $adb shell pm path com.speckdealer.app | Select-String -Pattern '^package:') | Select-Object -First 1
	if ($pkgLine) {
		$deviceApkPath = $pkgLine.ToString().Replace('package:', '').Trim()
		$pulledApk = Join-Path $tmpLatestDir 'installed-device.apk'
		& $adb pull $deviceApkPath $pulledApk | Out-Null
		$installedOrReferenceApk = $pulledApk
	}
} else {
	if (Test-Path 'tmp/installed-after-update.apk') {
		$installedOrReferenceApk = 'tmp/installed-after-update.apk'
	} elseif (Test-Path 'tmp/installed-app.apk') {
		$installedOrReferenceApk = 'tmp/installed-app.apk'
	} elseif (-not $AllowNoDevice) {
		Fail 'Kein ADB-Gerät verbunden und keine lokale Referenz-APK vorhanden.'
	}
}

if (-not [string]::IsNullOrWhiteSpace($installedOrReferenceApk)) {
	$installedCode = Get-ApkVersionCode -aaptPath $aapt -apkPath $installedOrReferenceApk
	if ($installedCode) { [void]$externalVersionCodes.Add($installedCode) }
}

$maxExternal = if ($externalVersionCodes.Count -gt 0) { ($externalVersionCodes | Measure-Object -Maximum).Maximum } else { 0 }
$expectedSource = $maxExternal + 1
if ($sourceVersionCode -ne $expectedSource) {
	Fail "versionCode ist $sourceVersionCode, erwartet exakt $expectedSource (max extern $maxExternal + 1)."
}

if (-not $SkipBuild) {
	$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
	$env:Path = "$env:JAVA_HOME\bin;$env:Path"
	& .\gradlew.bat test lint assembleRelease bundleRelease
	if ($LASTEXITCODE -ne 0) { Fail 'Gradle Build fehlgeschlagen.' }
}

$builtApk = 'app/build/outputs/apk/release/app-release.apk'
if (-not (Test-Path $builtApk)) { Fail "Release-APK fehlt: $builtApk" }

$builtCode = Get-ApkVersionCode -aaptPath $aapt -apkPath $builtApk
if ($builtCode -ne $sourceVersionCode) { Fail "APK-versionCode $builtCode != Quellcode-versionCode $sourceVersionCode" }
$builtAppId = Get-ApkPackageId -aaptPath $aapt -apkPath $builtApk
if ($builtAppId -ne 'com.speckdealer.app') { Fail "APK ApplicationId ist $builtAppId statt com.speckdealer.app" }

$propsFile = 'keystore.properties'
if (-not (Test-Path $propsFile)) { Fail 'keystore.properties fehlt.' }
$props = @{}
Get-Content $propsFile | ForEach-Object {
	if ($_ -match '^(\s*#|\s*$)') { return }
	$pair = $_ -split '=', 2
	if ($pair.Length -eq 2) { $props[$pair[0].Trim()] = $pair[1].Trim() }
}

$storeFile = $props['storeFile']
$alias = $props['keyAlias']
$storePass = $props['storePassword']
if ([string]::IsNullOrWhiteSpace($storeFile) -or [string]::IsNullOrWhiteSpace($alias) -or [string]::IsNullOrWhiteSpace($storePass)) {
	Fail 'keystore.properties unvollständig (storeFile/keyAlias/storePassword).'
}

$keytoolOut = keytool -list -v -keystore $storeFile -alias $alias -storepass $storePass
$keytoolShaLine = $keytoolOut | Select-String -Pattern 'SHA256:' | Select-Object -First 1
if (-not $keytoolShaLine) { Fail 'Keystore SHA256 konnte nicht gelesen werden.' }
$keytoolSha = ($keytoolShaLine.ToString().Split(':', 2)[1]).Replace(':', '').Trim().ToLowerInvariant()

$builtSha = Get-ApkSignerSha256 -apksignerPath $apksigner -apkPath $builtApk
if ([string]::IsNullOrWhiteSpace($builtSha)) { Fail 'APK-Signaturdigest konnte nicht gelesen werden.' }
if ($builtSha -ne $keytoolSha) { Fail 'APK-Signatur weicht vom produktiven Keystore ab.' }

if (-not [string]::IsNullOrWhiteSpace($installedOrReferenceApk)) {
	$refSha = Get-ApkSignerSha256 -apksignerPath $apksigner -apkPath $installedOrReferenceApk
	if ([string]::IsNullOrWhiteSpace($refSha)) { Fail 'Referenz-APK-Digest konnte nicht gelesen werden.' }
	if ($refSha -ne $builtSha) { Fail 'Neue APK-Signatur weicht von installierter/letzter funktionierender APK ab.' }
}

Write-Output "RELEASE_GUARD_OK"
Write-Output "versionCode=$sourceVersionCode"
Write-Output "versionName=$sourceVersionName"
Write-Output "maxExternalVersionCode=$maxExternal"
Write-Output "signingSha256=$builtSha"
if (-not [string]::IsNullOrWhiteSpace($installedOrReferenceApk)) {
	Write-Output "referenceApk=$installedOrReferenceApk"
}
