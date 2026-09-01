# BorderBattle

BorderBattle ist ein Paper-Plugin fuer Minecraft 1.21.11. Es verwaltet eine Challenge mit Joinphase, WorldBorder, Moderator-Modus, Eliminierungen, Border-Warnungen und kleinen Admin-Commands fuer laufende Runden.

## Features

- Joinphase mit eingefrorener Welt: Zeit bleibt bei 0, kein Regen, kein Mob-Spawning.
- Spieler sind vor dem Start im Adventure Mode.
- `/challenge start` startet die Runde, setzt Spieler in Survival und aktiviert Tageszyklus, Wetter und Mob-Spawning.
- `/challenge stop` beendet die Runde, setzt normale Spieler zurueck in Adventure und entfernt gespeicherte Eliminierungen.
- Moderator-Modus ueber `/mod`; Moderatoren bleiben Spectator und zaehlen nicht als aktive Spieler.
- Eliminierte Spieler werden gespeichert und direkt in Spectator gesetzt.
- BossBar zeigt ausgeschiedene Spieler im Verhaeltnis zur Spieleranzahl.
- Title-Warnung, wenn aktive Spieler nah an der Border stehen.
- Border-Shrink-Warnung fuer alle Spieler.
- Glow-Command fuer zeitlich begrenzten Glowing-Effekt.
- Difficulty-Commands fuer feste und stufenweise Schwierigkeit.

## Commands

| Command | Beschreibung | Permission |
| --- | --- | --- |
| `/mod` | Schaltet den eigenen Moderator-Modus um. | `borderbattle.mod` |
| `/challenge start` | Startet die Challenge. | `borderbattle.challenge` |
| `/challenge stop` | Stoppt die Challenge und aktiviert wieder die Joinphase. | `borderbattle.challenge` |
| `/border go <bloecke> <sekunden>` | Bewegt die WorldBorder auf die angegebene Groesse. | `borderbattle.border` |
| `/border stop` | Stoppt die aktuelle Border-Bewegung. | `borderbattle.border` |
| `/glow <sekunden>` | Gibt allen Online-Spielern fuer die angegebene Zeit den Glowing-Effekt. | `borderbattle.glow` |
| `/schwer` | Setzt die Schwierigkeit direkt auf `HARD`. | `borderbattle.difficulty` |
| `/low` | Setzt die Schwierigkeit direkt auf `EASY`. | `borderbattle.difficulty` |
| `/schwerer` | Erhoeht die Schwierigkeit um eine Stufe. | `borderbattle.difficulty` |
| `/lower` | Senkt die Schwierigkeit um eine Stufe. | `borderbattle.difficulty` |

Alias:

- `/challange` fuer `/challenge`

## Permissions

Alle Plugin-Rechte sind standardmaessig fuer OPs freigegeben:

- `borderbattle.mod`
- `borderbattle.challenge`
- `borderbattle.border`
- `borderbattle.glow`
- `borderbattle.difficulty`

Nicht-OPs brauchen die jeweilige Permission ueber ein Permission-Plugin oder die Server-Konfiguration.

## Config

Die Config liegt nach dem ersten Start unter:

```text
plugins/BorderBattle/config.yml
```

Standardwerte:

```yaml
waitingBorderSize: 20.0
challengeBorderSize: 1000.0
```

## Build

```bash
./gradlew build
```

Die fertige Plugin-JAR wird hier erstellt:

```text
build/libs/BorderBattle-1.0-SNAPSHOT-all.jar
```

Diese JAR kommt in den `plugins`-Ordner eines Paper-Servers.

## Lokaler Testserver

Der lokale Paper-Testserver kann mit Gradle gestartet werden:

```bash
./gradlew runServer
```

Der Server laeuft danach standardmaessig auf:

```text
localhost:25565
```

Zum sauberen Stoppen im Server-Terminal:

```text
stop
```
