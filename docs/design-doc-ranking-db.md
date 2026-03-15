# Design Doc - Gestione database ranking

## Contesto

### Package di riferimento
`it.polimi.ingsoft.mesos.server.model.ranking`

### Obiettivo
Descrivere la scelta di persistenza del modulo ranking lato server, chiarendo:
- perche' e' stato scelto SQLite;
- quali responsabilita' sono distribuite tra service, supporto database e query SQL;
- quali invarianti di dominio sono protetti a livello applicativo e a livello database;
- quali punti di integrazione restano verso orchestrator e controller.

## Problema

Il modulo ranking deve gestire tre esigenze contemporaneamente:
- registrazione di nickname univoci;
- persistenza dello storico partite con dati sufficienti a ricostruire leaderboard e dettaglio match;
- aggiornamento consistente del rating Elo globale a fine partita.

La parte critica non e' soltanto salvare dati, ma farlo in modo atomico:
- la stessa partita non deve essere inserita due volte;
- rating e storico devono essere allineati;
- in caso di errore non devono restare aggiornamenti parziali.

## Scelta implementativa

La soluzione adottata e' una persistenza locale su `SQLite`, incapsulata dietro un service di dominio.

Questa scelta e' adatta al progetto perche':
- non introduce infrastruttura esterna per sviluppo e test;
- consente persistenza reale, non solo in-memory;
- offre transazioni, vincoli e indici sufficienti per il caso d'uso corrente;
- mantiene semplice il setup dei test automatici.

Il tradeoff e' che SQLite non e' la scelta ideale per carichi concorrenti elevati multi-process. Per il perimetro attuale del progetto e' comunque una soluzione pragmatica.

## Vista d'insieme

### Facade di dominio
`RankingService` espone il contratto usato dal resto del server:
- `registerUser(...)`
- `isNicknameAvailable(...)`
- `recordMatchAndGetRanking(...)`
- `getLeaderboard()`
- `getMatchHistory()`
- `getMatchHistory(int playerCount)`

### Implementazione concreta
`SqliteRankingService` e' l'application service del package ranking:
- valida input e precondizioni;
- apre le transazioni;
- legge lo stato rating corrente;
- calcola gli aggiornamenti Elo;
- persiste risultati partita e rating aggiornati;
- restituisce al chiamante leaderboard e ranking della partita.

### Supporto infrastrutturale
`SqliteDatabaseSupport` incapsula gli aspetti tecnici di accesso al database:
- creazione cartelle e file;
- inizializzazione schema;
- apertura connessioni con pragma coerenti;
- retry su errori `busy` o `locked`.

### SQL centralizzato
`SqliteRankingQueries` contiene DDL e DML, cosi' da evitare stringhe SQL sparse in piu' classi.

## Modello dati persistito

Il database conserva tre insiemi di informazioni:
- anagrafica utenti registrati;
- rating corrente per ogni nickname;
- storico delle partite giocate con dettaglio per singolo player.

I dati persistiti per una riga di risultato partita includono:
- `gameId`;
- `nickname`;
- `finalScore`;
- `placement`;
- timestamp UTC;
- numero di giocatori;
- Elo before;
- Elo delta;
- Elo after;
- win streak dopo la partita.

## Invarianti di dominio

Le regole attualmente difese dal modulo sono:
- una partita valida ha tra `2` e `5` giocatori;
- `gameId` e' univoco e impedisce duplicate insert;
- nickname normalizzato con `trim` e non vuoto;
- niente nickname duplicati all'interno della stessa partita;
- leaderboard e storico devono derivare da uno stato transazionale consistente.

Queste regole sono distribuite su due livelli:

### Livello applicativo
- `RankingInputValidator` valida nickname, `gameId`, timestamp, player count e lista score;
- `EloRatingCalculator` calcola placement e delta rating;
- `SqliteRankingService` garantisce il flusso corretto dell'operazione.

### Livello database
- vincoli `UNIQUE`;
- foreign keys;
- check constraints;
- transazioni esplicite con rollback su errore.

## Componenti principali

### `RankingConfig`
Raccoglie i parametri configurabili del modulo:
- range giocatori accettato;
- Elo iniziale e minimo;
- base K e incremento per partite con piu' giocatori;
- bonus win streak;
- numero massimo di retry sui lock del database.

### `RankingInputValidator`
Normalizza e valida gli input esterni:
- nickname;
- identificativo partita;
- timestamp;
- player count;
- punteggi finali.

### `EloRatingCalculator`
Contiene la logica di calcolo:
- ordinamento per placement;
- gestione dei pareggi;
- expected score multi-player;
- delta finale;
- applicazione del bonus streak;
- clamp al minimo Elo configurato.

### `SqliteDatabaseSupport`
Gestisce la parte operativa di SQLite:
- `initializeSchema()`;
- `openConnection()`;
- `executeWithRetry(...)`.

In apertura connessione vengono usati pragma coerenti con il modulo:
- `foreign_keys = ON`
- `journal_mode = WAL`
- `busy_timeout`

### `SqliteRankingService`
Coordina l'intera operazione di registrazione partita:
1. valida input;
2. apre transazione;
3. recupera snapshot rating correnti;
4. calcola aggiornamenti Elo;
5. persiste rating e storico match;
6. esegue `commit`;
7. in caso di errore esegue `rollback`.

## Atomicita' e concorrenza

Il package cerca di ridurre race condition e aggiornamenti parziali tramite:
- transazioni SQL esplicite;
- livello di isolamento coerente;
- modalita' `WAL` per migliorare concorrenza in lettura/scrittura;
- retry con backoff su errori transitori del database;
- vincolo di unicita' sul match per garantire idempotenza pratica in inserimento.

Per il progetto corrente questo e' sufficiente. Se in futuro il numero di processi concorrenti aumentasse sensibilmente, la stessa architettura andrebbe mantenuta ma con backend DB piu' adatto, per esempio PostgreSQL.

## Pattern e struttura

Le scelte di design principali del package sono:
- `Facade/Service` tramite `RankingService`;
- separazione netta tra dominio, validazione e infrastruttura;
- SQL centralizzato;
- value object immutabili per input, output e snapshot intermedi;
- validazione fail-fast sugli invarianti.

## Test presenti

Il package ha gia' una copertura divisa tra unit test e integration test.

Unit test principali:
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

Test di servizio e integrazione:
- `SqliteRankingServiceTest`
- `SqliteRankingServiceIntegrationTest`

## Punti di integrazione ancora aperti

Il package e' progettato per essere usato dal flusso applicativo principale, ma restano da consolidare i collegamenti con il resto del server:
- verifica disponibilita' nickname nel flusso di login o join;
- chiamata a `recordMatchAndGetRanking(...)` a fine partita;
- propagazione al client di leaderboard e ranking finale.

## Miglioramenti possibili

I miglioramenti piu' naturali del modulo sono:
- estrarre eventuali mapper SQL dedicati se `SqliteRankingService` cresce troppo;
- aggiungere test concorrenti piu' aggressivi;
- rendere esplicita la strategia di migrazione schema se il database evolve.
