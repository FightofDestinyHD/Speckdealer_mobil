# Offene Room-Migration nach Schritt 5

## Status
In diesem Arbeitsabschnitt wurde **keine direkte Room-Migration** der JSON-Arrays (Open Orders, Daily Sales) umgesetzt.

## Begründung
Eine sichere Migration erfordert zusätzliche Entitäten, DAOs, Versionierung des Datenbankschemas, Migrationsskripte, Start-up-Reihenfolge und Regressionstests über mehrere App-Flows. Das wäre in diesem Abschnitt zu umfangreich und risikobehaftet.

## Bereits umgesetzt (sicherer Zwischenstand)
- Robuste Persistenzabstraktion für SharedPreferences-JSON-Arrays
- Commit-basierte Fehlererkennung statt stillem `apply()`
- Synchronisierte Read-Modify-Write-Pfade
- Backup/Recovery bei beschädigtem JSON
- Sicherung beschädigter Rohdaten für manuelle Wiederherstellung
- Transaktions-ID und Datensatz-ID für Nachvollziehbarkeit
- Duplikaterkennung beim Persistieren

## Sicherer Room-Migrationspfad (offen)
1. Neue Room-Entitäten für `OrderRecord` und `SaleRecord` einführen.
2. DAOs mit Upsert- und dedizierten Dedupe-Queries ergänzen.
3. DB-Version erhöhen und Migration implementieren.
4. Beim ersten Start nach Upgrade:
   - bestehende JSON-Daten lesen,
   - in einer DB-Transaktion importieren,
   - Import-Status persistent markieren,
   - JSON-Quelle als Recovery-Fallback erst nach erfolgreicher Verifikation auf read-only belassen.
5. Integrations- und Wiederanlauf-Tests für Abbruchfälle (Crash zwischen Read/Write, beschädigte Legacy-JSON) ergänzen.

## Wichtige Leitplanke
Vorhandene lokale Daten dürfen in der späteren Room-Migration nicht automatisch gelöscht werden; fehlerhafte Legacy-Payloads müssen archiviert und nachvollziehbar bleiben.
