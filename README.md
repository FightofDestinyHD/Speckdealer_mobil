# Speckdealer App (Android)

Android-Tablet-optimiertes Grundgerüst mit automatisiertem Build-Workflow.

## Voraussetzungen
- Android Studio (aktuelle stabile Version)
- Android SDK Platform 31
- JDK 11 (für CI bereits gesetzt)

## Lokal starten
1. Projekt in Android Studio öffnen.
2. Gradle-Sync ausführen.
3. App auf einem Tablet oder Emulator (>= Android 7.0 / API 24) starten.

## Tablet-Optimierung
- Standardlayout: `app/src/main/res/layout/activity_main.xml`
- Tabletlayout (`sw600dp`): `app/src/main/res/layout-sw600dp/activity_main.xml`

## Automatisierte Updates / Delivery-Basis
- Workflow: `.github/workflows/android-ci.yml`
- Bei Push/PR werden automatisch erzeugt:
  - Debug-APK (`app-debug-apk`)
  - Release-AAB (`app-release-aab`)
- Diese Artefakte können als verteilter Build genutzt werden.

## Automatisiertes signiertes Release
Zusätzlich zum CI-Workflow gibt es einen Release-Workflow: `.github/workflows/android-release.yml`.

Trigger:
- Tag-Push wie `v1.0.0`
- manuell per `workflow_dispatch`

Benötigte Repository-Secrets:
- `KEYSTORE_BASE64` (Base64-kodierter Inhalt der `.jks`)
- `KEYSTORE_PASSWORD`
- `KEY_ALIAS`
- `KEY_PASSWORD`

Der Workflow baut signierte Artefakte und veröffentlicht sie als GitHub Release:
- Release-APK
- Release-AAB

## Nächster Schritt für echte Auto-Updates
Für echte automatische Endnutzer-Updates auf Tablets ist ein fester Kanal nötig (z. B. Google Play Internal Track + In-App-Updates).
