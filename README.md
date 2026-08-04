# Speckdealer App (Android)

Android-Tablet-optimiertes Grundgerüst mit lokalem Build und automatisiertem Upload/Release-Workflow.

## Voraussetzungen
- Android Studio (aktuelle stabile Version)
- Android SDK Platform 31
- JDK 17 lokal für Build/Signierung

## Lokal starten
1. Projekt in Android Studio öffnen.
2. Gradle-Sync ausführen.
3. App auf einem Tablet oder Emulator (>= Android 7.0 / API 24) starten.

## Tablet-Optimierung
- Standardlayout: `app/src/main/res/layout/activity_main.xml`
- Tabletlayout (`sw600dp`): `app/src/main/res/layout-sw600dp/activity_main.xml`

## Lokaler Build (verbindlich)
Builds werden lokal erzeugt, dann hochgeladen/released.

1. Lokal Release bauen:
   - `./gradlew :app:assembleRelease :app:bundleRelease`
2. Artefakte ins Repo kopieren:
   - `release-artifacts/app-release.apk`
   - `release-artifacts/app-release.aab`
3. Commit + Push der Artefakte.

## Upload-Workflow (ohne Cloud-Build)
- Workflow: `.github/workflows/android-ci.yml`
- Zweck: prüft nur, ob lokale Artefakte vorhanden sind, und lädt diese als Workflow-Artefakte hoch.

## Release-Workflow (ohne Cloud-Build)
- Workflow: `.github/workflows/android-release.yml`
- Trigger:
  - Tag-Push wie `v1.0.0`
  - manuell per `workflow_dispatch`
- Zweck: erstellt GitHub Release direkt aus
  - `release-artifacts/app-release.apk`
  - `release-artifacts/app-release.aab`

## Nächster Schritt für echte Auto-Updates
Für echte automatische Endnutzer-Updates auf Tablets ist ein fester Kanal nötig (z. B. Google Play Internal Track + In-App-Updates).
