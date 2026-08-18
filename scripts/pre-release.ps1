$ErrorActionPreference = 'Stop'

Write-Output '== Release Guard =='
& powershell -ExecutionPolicy Bypass -File scripts/release-guard.ps1
if ($LASTEXITCODE -ne 0) {
	throw 'Release Guard fehlgeschlagen. Commit/Tag/Push abgebrochen.'
}

Write-Output '== Git Diff Check =='
& git diff --check
if ($LASTEXITCODE -ne 0) {
	throw 'git diff --check meldet Fehler. Commit/Tag/Push abgebrochen.'
}

Write-Output '== Status =='
& git status --short --branch

Write-Output '== Stat =='
& git diff --stat

Write-Output 'Pre-Release-Prüfung erfolgreich.'
