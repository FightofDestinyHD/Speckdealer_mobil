# Copilot Instructions

## General Guidelines
- User bevorzugt lokal gesetzte Git-Author-Daten mit Name 'FightofDestinyHD' und no-reply E-Mail-Muster '<username>@users.noreply.github.com'.
- Builds immer lokal erzeugen; CI soll nur Upload/Release der lokal erzeugten Artefakte übernehmen (kein Cloud-Build).
- Bei Änderungen immer Release Notes erhalten.
- Bei erfolgreichem Build immer fragen, ob der Release ausgeführt werden soll, bevor der Release erfolgt.
- Nutzer erwartet erst nach verifizierter Prüfung eine Aussage wie 'Fix ist behoben' und reagiert negativ auf unbestätigte Erfolgsmeldungen.

## Repository Access
- GitHub Token wird zur Laufzeit per Umgebungsvariable GH_RELEASE_TOKEN übergeben.