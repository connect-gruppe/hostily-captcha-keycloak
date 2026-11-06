# Changelog

## 0.2.0 (04.11.2026)

* Neu: Einstellung „Mode“ mit fünf Werten: `portal` (Wie im Kundenportal, das Attribut `data-modus` entfällt), `visible`, `invisible`, `hold` (gedrückt halten), `slide` (Schieberegler); dazu „Hold time (ms)“ (800 bis 3000, Vorgabe 1500) für `hold`.
* Bestehende Konfigurationen mit `visible`/`invisible` bleiben unverändert; unbekannte Werte gelten als `visible`.
* Schritt-Seite und Registrierung setzen `data-modus` und `data-haltezeit` entsprechend; Hinweistexte zu Halten/Schieben (messages_de, messages_en).
* Browsertest: alle fünf Werte, Haltezeit, Registrierung.

## 0.1.0

* Erste Fassung.
