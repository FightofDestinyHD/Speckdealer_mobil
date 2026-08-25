# Copilot Instructions

## General Guidelines
- User bevorzugt lokal gesetzte Git-Author-Daten mit Name 'FightofDestinyHD' und no-reply E-Mail-Muster '<username>@users.noreply.github.com'.
- Builds immer lokal erzeugen; CI soll nur Upload/Release der lokal erzeugten Artefakte übernehmen (kein Cloud-Build).
- Bei Änderungen immer Release Notes erhalten.
- Bei erfolgreichem Build immer fragen, ob der Release ausgeführt werden soll, bevor der Release erfolgt.
- Nutzer erwartet erst nach verifizierter Prüfung eine Aussage wie 'Fix ist behoben' und reagiert negativ auf unbestätigte Erfolgsmeldungen.
- Nutzer erwartet, dass ich den vollständigen technischen Ablauf eigenständig ausführe und keine manuellen Schritte an ihn delegiere.
- Nach v0.1.69 soll die Versionsreihe auf v0.2.0 umgestellt werden.
- Beim Erstellen von Releases immer auf konsistente versionCode-/versionName-Werte und dieselbe produktive Signatur achten, damit Updates ohne manuelle Neuinstallation funktionieren. Releases müssen mit demselben Keystore signiert werden.
- Nutzer erwartet bei ADB-/Build-Aktionen einen echten Nachweis mit konkreter Konsolenausgabe statt pauschaler Erfolgsmeldung.
- Nutzer erwartet bei Installationsaussagen einen direkten, überprüfbaren Nachweis mit konkreter Konsolenausgabe statt pauschaler Bestätigung.
- Nach jeder Codeänderung zuerst Debug-APK per ADB bauen/installieren/verifizieren; Release-Befehl erst nach erfolgreichem Debug-ADB-Check ausführen.
- Wenn der Nutzer die Update-Funktion testen will, keine ADB-Installation durchführen, da dies den Test verfälscht.

## Repository Access
- GitHub Token wird zur Laufzeit per Umgebungsvariable GH_RELEASE_TOKEN übergeben.