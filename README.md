# Speckdealer App (Android)

Android-Tablet-optimiertes Grundgerüst mit lokalem Build und automatisiertem Upload/Release-Workflow.

## Voraussetzungen
- Android Studio (aktuelle stabile Version)
- Android SDK Platform 34
- JDK 17+ lokal für Build/Signierung

## Lokale Signierung sicher konfigurieren
1. `keystore.properties.example` nach `keystore.properties` kopieren.
2. Echte Werte nur lokal eintragen (niemals committen).
3. Alternativ `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` als Umgebungsvariablen setzen.

Ohne valide Signierung schlägt ein Release-Build absichtlich fehl.

## Lokal starten
1. Projekt in Android Studio öffnen.
2. Gradle-Sync ausführen.
3. App auf einem Tablet oder Emulator (>= Android 7.0 / API 24) starten.

## Tablet-Optimierung
- Standardlayout: `app/src/main/res/layout/activity_main.xml`
- Tabletlayout (`sw600dp`): `app/src/main/res/layout-sw600dp/activity_main.xml`

## Lokaler Build (verbindlich)
Builds werden lokal erzeugt, dann hochgeladen/released.

1. Vor Commit/Tag/Push Release-Guard ausführen:
   - `powershell -ExecutionPolicy Bypass -File scripts/pre-release.ps1`
2. Der Guard prüft automatisch:
   - monotones `versionCode` (`max(externe Quellen) + 1`)
   - `applicationId == com.speckdealer.app`
   - lokaler Full-Build (`test lint assembleRelease bundleRelease`)
   - gebaute APK-Version gegen Quellcode
   - Signaturgleichheit: neue APK ↔ produktiver Keystore ↔ installierte/Referenz-APK
3. Nur bei erfolgreichem Guard Commit/Tag/Push ausführen.
4. Artefakte bereitstellen:
   - `release-artifacts/app-release.apk`
   - `release-artifacts/app-release.aab`
5. Prüfsummen erzeugen:
   - `sha256sum release-artifacts/app-release.apk | awk '{print $1}' > release-artifacts/app-release.apk.sha256`
   - `sha256sum release-artifacts/app-release.aab | awk '{print $1}' > release-artifacts/app-release.aab.sha256`
6. Artefakte bewusst versionieren (falls gewünscht):
   - `git add -f release-artifacts/app-release.apk release-artifacts/app-release.aab release-artifacts/app-release.apk.sha256 release-artifacts/app-release.aab.sha256`

## Upload-Workflow (ohne Cloud-Build)
- Workflow: `.github/workflows/android-ci.yml`
- Zweck: prüft nur lokale Artefakte und deren Prüfsummen, dann Upload als Workflow-Artefakte.

## Release-Workflow (ohne Cloud-Build)
- Workflow: `.github/workflows/android-release.yml`
- Trigger:
  - Tag-Push wie `v1.0.0`
  - manuell per `workflow_dispatch`
- Zweck: erstellt GitHub Release direkt aus lokalen Artefakten + `.sha256`.

## Update-Sicherheit in der App
Bei GitHub-basierten APK-Updates werden vor Installation geprüft:
- SHA-256-Prüfsumme (`.apk.sha256` muss im Release vorhanden sein)
- Signaturgleichheit zwischen installierter App und heruntergeladener APK

## Nächster Schritt für echte Auto-Updates
Für echte automatische Endnutzer-Updates auf Tablets ist ein fester Kanal nötig (z. B. Google Play Internal Track + In-App-Updates).
