# Ranking Package (`model/ranking`)

## Obiettivo
Questo package implementa il **modello di classifica** lato server:

- registrazione nickname univoci;
- persistenza storico partite su SQLite;
- aggiornamento Elo globale a fine partita;
- recupero leaderboard globale e storico partite (totale o filtrato per numero giocatori).

Il package è pensato per essere usato dal controller/orchestrator, ma è autosufficiente lato model e garantisce transazioni atomiche sulle operazioni critiche.

---

## Vincoli di dominio implementati

- Una partita valida ha **2..5 giocatori**.
- `gameId` è univoco: la stessa partita non può essere inserita due volte.
- Ogni riga di risultato partita contiene:
  - nickname,
  - punteggio finale,
  - placement,
  - data UTC,
  - numero giocatori,
  - Elo before/delta/after,
  - win streak after.
- Nickname normalizzato (`trim`) e non vuoto.

---

## Architettura e responsabilità

### Facade di dominio

### `RankingService`
Interfaccia del servizio model esposto al resto del server.

Metodi principali:

- `registerUser(...)`
- `isNicknameAvailable(...)`
- `recordMatchAndGetRanking(...)`
- `getLeaderboard()`
- `getMatchHistory()`
- `getMatchHistory(int playerCount)`

### `SqliteRankingService`
Implementazione concreta dell’interfaccia, orchestratore del flusso ranking.

Attributi principali:

- `RankingConfig config`: parametri Elo/validazione/retry;
- `SqliteDatabaseSupport database`: connessioni, schema e retry busy;
- `RankingInputValidator validator`: normalizzazione + validazioni input;
- `EloRatingCalculator eloCalculator`: calcolo placement e delta Elo.

Comportamento chiave:

- transazione atomica in `recordMatchAndGetRanking(...)`;
- rollback su errori;
- controllo anti-duplicazione partita (`gameId`);
- persistenza batch di rating e risultati.

---

## Value Objects / DTO immutabili

### `MatchScore`
Input minimo per un giocatore in partita (`nickname`, `finalScore`).

### `LeaderboardEntry`
Riga classifica globale (`nickname`, `elo`, `wins`, `winStreak`, `rank`).

### `MatchRankingEntry`
Riga ranking di una partita con informazioni Elo complete.

### `MatchHistory`
Aggregato storico di una partita (`gameId`, `playedAtUtc`, `playerCount`, `ranking`).

### `RankingResult`
Output di `recordMatchAndGetRanking(...)`: `playerRank` del requester + leaderboard globale aggiornata.

### `RatingSnapshot`
Snapshot pre-partita dello stato rating di un player (uso interno).

### `PlayerMatchUpdate`
Delta calcolato per un singolo player (uso interno) da persistire su rating e history.

Tutti questi oggetti sono record immutabili e validano i propri invarianti nel costruttore compatto.

---

## Componenti di supporto

### `RankingConfig`
Config centralizzata:

- range player (`minPlayers`, `maxPlayers`),
- parametri Elo (`initialElo`, `minElo`, `baseK`, `kStepPerExtraPlayer`),
- streak bonus (`streakBonusStep`, `streakBonusCap`),
- retry DB (`maxBusyRetries`).

`defaultConfig()` fornisce valori standard di progetto.

### `RankingInputValidator`
Normalizza e valida input esterni:

- nickname e gameId,
- timestamp,
- playerCount,
- lista `finalScores` (size coerente, no duplicati nickname, no null).

### `EloRatingCalculator`
Contiene la logica Elo:

- `computePlacements(...)`: ordina per punteggio decrescente e gestisce pareggi;
- `computeUpdates(...)`: calcola expected/actual score multi-player, base delta, streak bonus, clamp a `minElo`.

Streak bonus:

- parte dalla seconda vittoria consecutiva,
- è limitato da `streakBonusCap`.

### `SqliteDatabaseSupport`
Incapsula aspetti infrastrutturali SQLite:

- creazione cartelle DB,
- `initializeSchema()` (tabelle + indici),
- `openConnection()` con pragmas (`foreign_keys`, `WAL`, `busy_timeout`),
- `executeWithRetry(...)` su errori busy/locked con backoff progressivo.

### `SqliteRankingQueries`
Contiene tutte le query SQL centralizzate (DDL e DML).

---

## Eccezioni di dominio

### `DuplicateGameException`
Lanciata quando una partita con stesso `gameId` è già registrata.

### `RankingDataException`
Lanciata per inconsistenze dati o stato ranking non recuperabile.

---

## Atomicità, concorrenza e race condition

Il package riduce race condition e garantisce consistenza tramite:

- transazione SQL esplicita (`setAutoCommit(false)` + `commit/rollback`);
- `TRANSACTION_SERIALIZABLE` sulle connessioni;
- modalità `WAL` per concorrenza migliore su SQLite;
- retry su lock transienti con backoff (`executeWithRetry`);
- vincoli DB (`UNIQUE(game_id, nickname)`, check constraints, foreign keys).

Nota: SQLite è ottimo per sviluppo/test e piccoli carichi concorrenti. Per carichi elevati multi-process, MySQL/PostgreSQL restano più adatti.

---

## Pattern e scelte di design

- **Facade/Service**: `RankingService` espone un contratto chiaro al resto del model/controller.
- **Value Object immutabili**: record per input/output e stato intermedio.
- **Single Responsibility**:
  - validazione separata (`RankingInputValidator`),
  - calcolo Elo separato (`EloRatingCalculator`),
  - infrastruttura DB separata (`SqliteDatabaseSupport`),
  - SQL centralizzato (`SqliteRankingQueries`).
- **Fail-fast validation**: invarianti controllati presto.
- **Idempotenza funzionale** su match insert via `gameId` univoco.

---

## Test presenti nel package

Unit test su value object, eccezioni e componenti logici:

- `MatchScoreTest`
- `LeaderboardEntryTest`
- `MatchRankingEntryTest`
- `MatchHistoryTest`
- `RankingResultTest`
- `RankingConfigTest`
- `RankingInputValidatorTest`
- `EloRatingCalculatorTest`
- `DuplicateGameExceptionTest`
- `RankingDataExceptionTest`
- `RatingSnapshotTest`
- `PlayerMatchUpdateTest`
- `SqliteDatabaseSupportTest`
- `SqliteRankingQueriesTest`

Test di servizio/integrazione:

- `SqliteRankingServiceTest`
- `SqliteRankingServiceIntegrationTest`

I test coprono validazioni, persistenza, duplicate game protection, ordering classifica, storico partite, filtro per player count e comportamento Elo/streak.

---

## Debolezze attuali / miglioramenti possibili

1. Estrarre mapper SQL in classe dedicata per ridurre ancora la responsabilità di `SqliteRankingService`.
2. Aggiungere test concorrenti multi-thread più aggressivi.

---

## TODO di integrazione controller

Nel codice sono presenti TODO per integrare:

- verifica disponibilità nickname in login/join flow;
- chiamata a `recordMatchAndGetRanking(...)` a fine partita;
- invio al client di rank globale e leaderboard completa.

