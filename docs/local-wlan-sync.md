# Lokale WLAN-Synchronisation (Speckdealer)

## Ziel
Bestellungen werden lokal gespeichert und zusätzlich zwischen Geräten im selben WLAN synchronisiert.

## Rollen
- **HOST**: startet den lokalen Sync-Server.
- **CLIENT**: verbindet sich mit Host-IP und Port.

## Startablauf
1. Auf Gerät A in `Geräte synchronisieren`:
   - `Host starten`
   - Port und Gerätecode setzen
2. Auf Gerät B in `Geräte synchronisieren`:
   - Host-IP, Port und denselben Gerätecode eintragen
   - `Mit Gerät verbinden`
3. Optional auf Gerät B: `Jetzt synchronisieren`

## Sichtbarer Status
- `Synchronisiert`
- `Wird synchronisiert`
- `Wartet auf Synchronisation`
- `Fehler`

## Transport & Sicherheit
- Lokales TCP Socket-Protokoll (JSON Request/Response)
- Nur erwartete Nachrichtentypen (`SYNC_REQUEST`, `SYNC_RESPONSE`)
- Gerätecode-Prüfung auf Host
- Größenlimit pro Nachricht
- Begrenzung der Bestellungen pro Sync
- Keine Datei-/Befehlsausführung über Netzwerk

## Duplikatschutz / Konfliktregel
Schlüssel: `orderId` (`OrderRecord.id`)

Beim Merge:
1. `orderId` unbekannt → anlegen
2. gleiche `orderId`, gleiche Version/älterer Stand → ignorieren
3. gleiche `orderId`, neuere Version → idempotent aktualisieren
4. ältere Version darf neuere nicht überschreiben

Vergleichsreihenfolge:
1. `syncVersion`
2. `updatedAtUtcMs`
3. `timestampMs`

## Offline-Verhalten
- Verkauf bleibt lokal funktionsfähig
- Neue Bestellungen starten mit `PENDING_SYNC`
- Bei Verbindungsverlust bleibt Bestellung lokal erhalten
- Erneuter Sync nutzt dieselbe `orderId`

## Hinweis
Die Synchronisation ist als zusätzliche LAN-Funktion implementiert und ersetzt keine lokale Persistenz.
