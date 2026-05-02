# Design Doc - Ranking e persistenza MySQL

## Stato del documento

Documento aggiornato per la branch `migrate/postgree-to-mysql`.

Il contenuto descrive lo stato corrente del codice, non solo l'intenzione di design. Dove
l'implementazione espone un contratto ma il resto del server non lo usa ancora, il documento lo
segnala esplicitamente.

## Obiettivo

Il modulo ranking deve fornire al server tre capacita' persistenti:

- registrare nickname univoci nel database ranking;
- registrare una partita completata una sola volta;
- aggiornare il rating Elo globale e rendere interrogabili leaderboard e storico match.

La parte critica non e' il singolo salvataggio, ma la consistenza tra rating aggregati e righe di
storico. Se una partita viene accettata, tutte le righe dei giocatori e tutti gli aggiornamenti Elo
devono essere visibili insieme. Se l'operazione fallisce, il match non deve lasciare uno stato
parzialmente applicato.

## Scope

Il ranking appartiene al package:

`it.polimi.ingsoft.mesos.server.model.ranking`

Il package copre:

- contratti pubblici per registrazione utenti, registrazione match e query;
- validazione e normalizzazione input;
- calcolo placement ed Elo;
- persistenza MySQL via JDBC;
- bootstrap dello schema ranking;
- retry su errori transitori MySQL.

Il package non decide le regole di gioco. Il punteggio finale arriva dal model `Game`, oggi come
prestige points dei `Player` al momento in cui la partita entra in `GameStatus.FINISHED`.

## Vista d'insieme

Il design separa dominio e infrastruttura in questo modo:

- `RankingService`: contratto pubblico usato dal resto del server;
- `MySqlRankingService`: application service che valida, calcola e persiste;
- `EloRatingCalculator`: logica pura di placement ed Elo;
- `RankingInputValidator`: normalizzazione e precondizioni sugli input;
- `MySqlDatabaseSupport`: apertura connessioni, isolamento transazionale, schema init e retry;
- `MySqlRankingQueries`: DDL e DML centralizzati;
- DTO immutabili: `MatchScore`, `LeaderboardEntry`, `MatchRankingEntry`, `MatchHistory`,
  `RankingResult`;
- record interni: `RatingSnapshot`, `PlayerMatchUpdate`.

In runtime il wiring passa da:

`LaunchServer -> ServerServicesFactory -> MySqlDatabaseBootstrap -> MySqlRankingService -> ServerController -> GameOrchestrator`

Se `MESOS_NO_DB=true`, `ServerServicesFactory` restituisce `rankingService == null`. In questo
caso `GameOrchestrator` non registra il ranking finale.

## Contratti pubblici

`RankingService` espone questi metodi:

```java
boolean registerUser(String nickname);
boolean isNicknameAvailable(String nickname);
RankingResult recordMatchAndGetRanking(
        String gameId,
        int playerCount,
        Instant playedAtUtc,
        String requestingNickname,
        List<MatchScore> finalScores
);
List<LeaderboardEntry> getLeaderboard();
List<MatchHistory> getMatchHistory();
List<MatchHistory> getMatchHistory(int playerCount);
```

Semantica dei contratti:

- `registerUser` normalizza il nickname, lo inserisce se assente e garantisce l'esistenza della riga
  rating iniziale; ritorna `true` se il nickname e' stato appena inserito, `false` se esisteva gia';
- `isNicknameAvailable` ritorna `true` se non esiste una riga in `users`;
- `recordMatchAndGetRanking` registra il match e ritorna il rank globale del richiedente insieme
  alla leaderboard completa aggiornata;
- `getLeaderboard` legge il ranking globale ordinato;
- `getMatchHistory` legge lo storico completo, ordinato dal match piu' recente;
- `getMatchHistory(int)` applica un filtro sul numero di giocatori, valido solo tra 2 e 5.

Eccezioni rilevanti:

- `IllegalArgumentException` per input non validi;
- `DuplicateGameException` quando `gameId` risulta gia' registrato;
- `RankingDataException` per incoerenze di stato ranking;
- `IllegalStateException` come wrapper operativo in caso di errore database non recuperabile.

## Dominio ranking

Le entita' persistite dal ranking sono volutamente piccole:

- utente registrato: identificato solo dal `nickname`;
- rating aggregato: Elo corrente, partite giocate, vittorie, win streak;
- risultato match: una riga immutabile per ogni giocatore, con snapshot Elo before/delta/after.

Il nickname e' la chiave stabile del ranking. La collation MySQL e' `utf8mb4_bin`, quindi la
persistenza e' case-sensitive: `Alice` e `alice` sono nickname distinti.

Le regole di validazione correnti sono:

- nickname non `null`, non blank, massimo 255 caratteri, normalizzato con `trim`;
- `gameId` non `null`, non blank, massimo 255 caratteri, normalizzato con `trim`;
- `playedAtUtc` obbligatorio;
- `playerCount` tra 2 e 5;
- lista `finalScores` non vuota, dimensione uguale a `playerCount`;
- nessun nickname duplicato nella lista punteggi;
- `finalScore` non negativo.

## Flusso di registrazione nickname

Il contratto ranking prevede questo flusso:

1. Il chiamante riceve un nickname dal client.
2. `RankingInputValidator.normalizeNickname` applica `trim` e valida lunghezza/non blank.
3. `MySqlRankingService.registerUser` prova un `INSERT INTO users`.
4. Se MySQL segnala duplicate key, il metodo tratta il nickname come gia' registrato.
5. Lo stesso metodo chiama `ensureRatingExists`, che usa un upsert mirato su `user_ratings` con
   Elo iniziale.
6. Il boolean di ritorno distingue nuovo inserimento (`true`) da nickname gia' presente (`false`).

Il rating iniziale e' `1000`, configurato da `RankingConfig.defaultConfig()`.

Dettaglio importante sullo stato corrente: il flusso di autenticazione TCP/RMI non chiama ancora
`RankingService.registerUser`. Oggi `VirtualViewTCP`, `RmiSessionFactory` e `ServerController`
delegano a `ServerSessionRegistry.authenticate`, che garantisce solo unicita' in memoria tra le
sessioni attive. Il modulo ranking espone gia' il contratto per la registrazione persistente, ma il
controller deve ancora integrarlo nel login se si vuole bloccare un nickname gia' registrato in
database.

`registerUser` usa l'errore duplicate key per preservare il boolean del contratto pubblico e usa un
upsert idempotente solo per garantire la riga rating. Non apre pero' una transazione esplicita
dedicata: in caso di errore tra inserimento utente e creazione rating, una chiamata successiva o il
flusso di registrazione match puo' riparare lo stato tramite lo stesso upsert rating.

## Flusso di registrazione match

La registrazione match e' agganciata a `GameOrchestrator`.

Ogni metodo pubblico di gameplay (`setupMatch`, `moveTotem`, `chooseCard`, `extraDrawn`) chiama
`recordRankingIfFinished` dopo aver delegato al `Game`. Se il `Game` e' passato a
`GameStatus.FINISHED` e il ranking service e' disponibile, l'orchestrator registra il match.

Input passato a `RankingService.recordMatchAndGetRanking`:

- `gameId`: UUID generato dal `GameOrchestrator` per il solo ranking persistente;
- `playerCount`: numero di player nella partita;
- `playedAtUtc`: `Instant.now()`;
- `requestingNickname`: nickname dell'host della lobby;
- `finalScores`: lista di `MatchScore(nickname, player.getPrestigePoints())`.

Il `Game` mantiene ancora il proprio id intero per lo stato runtime e per i payload esistenti.
Il ranking usa invece un UUID separato, creato una volta nel costruttore dell'orchestrator, cosi'
da non collidere con partite storiche dopo un riavvio del server.

Il flag `rankingRecorded` impedisce che lo stesso `GameOrchestrator` registri due volte il match
nella stessa istanza server.

Se il ranking service fallisce durante la finalizzazione:

- `GameOrchestrator` imposta `frozen = true`;
- rilancia `RankingFinalizationFailureException`;
- le chiamate successive falliscono con `MatchFrozenException`.

Il risultato positivo viene mantenuto in `finalRankingResult`, ma non viene ancora propagato ai
client tramite un payload dedicato.

## Calcolo Elo

Il calcolo e' incapsulato in `EloRatingCalculator`.

### Placement

I punteggi finali vengono ordinati per:

1. `finalScore` decrescente;
2. `nickname` crescente, solo per rendere deterministico l'ordine dei pari punteggio.

Il placement e' di tipo "competition ranking": punteggi uguali hanno lo stesso placement e il
placement successivo tiene conto delle posizioni occupate. Esempio: `30, 25, 25` produce
placement `1, 2, 2`.

### Expected score

Per ogni giocatore viene calcolata la media delle probabilita' Elo pairwise contro tutti gli
avversari:

```text
E(player, opponent) = 1 / (1 + 10 ^ ((eloOpponent - eloPlayer) / 400))
expected = media(E(player, opponent))
```

### Actual score

L'actual score e' anch'esso una media pairwise:

- `1.0` contro un avversario con placement peggiore;
- `0.5` contro un avversario con stesso placement;
- `0.0` contro un avversario con placement migliore.

### K factor

Il K factor cresce con il numero di giocatori:

```text
K = baseK + (playerCount - minPlayers) * kStepPerExtraPlayer
```

Con la configurazione di default:

- 2 giocatori: `24`;
- 3 giocatori: `26`;
- 4 giocatori: `28`;
- 5 giocatori: `30`.

### Delta e streak

Il delta base e':

```text
baseDelta = round(K * (actual - expected))
```

Un giocatore e' considerato vincitore se ha `placement == 1`. Questo include eventuali pareggi al
primo posto. I vincitori incrementano `wins` e `win_streak`; gli altri azzerano la streak.

Il bonus streak parte dalla seconda vittoria consecutiva:

```text
streakBonus = min(streakBonusCap, (newWinStreak - 1) * streakBonusStep)
```

Configurazione default:

- `streakBonusStep = 2`;
- `streakBonusCap = 8`.

Il delta finale e':

```text
eloDelta = baseDelta + streakBonus
eloAfter = max(minElo, eloBefore + eloDelta)
```

Il minimo Elo default e' `100`.

## Persistenza MySQL

Il backend persistente usa MySQL via JDBC e richiede InnoDB.

Le tabelle sono create da `MySqlRankingSchemaModule`, che contribuisce tre DDL:

- `users`;
- `user_ratings`;
- `match_results`.

Le query sono centralizzate in `MySqlRankingQueries`. La scelta riduce duplicazione di stringhe SQL
e rende visibile in un solo punto lo schema effettivo del modulo.

### Tabella `users`

Colonne:

- `nickname VARCHAR(255) NOT NULL`;
- `created_at DATETIME(6) NOT NULL`.

Vincoli:

- primary key su `nickname`.

Uso:

- anagrafica minima dei nickname registrati;
- target delle foreign key da rating e storico.

### Tabella `user_ratings`

Colonne:

- `nickname VARCHAR(255) NOT NULL`;
- `elo INTEGER NOT NULL`;
- `games_played INTEGER NOT NULL DEFAULT 0`;
- `wins INTEGER NOT NULL DEFAULT 0`;
- `win_streak INTEGER NOT NULL DEFAULT 0`;
- `last_updated DATETIME(6) NOT NULL`.

Vincoli e indici:

- primary key su `nickname`;
- foreign key `nickname -> users.nickname`;
- index `idx_user_ratings_leaderboard(elo DESC, wins DESC, nickname ASC)`.

Uso:

- stato aggregato corrente;
- sorgente della leaderboard globale;
- snapshot pre-match per il calcolo Elo successivo.

### Tabella `match_results`

Colonne:

- `id BIGINT AUTO_INCREMENT`;
- `game_id VARCHAR(255) NOT NULL`;
- `nickname VARCHAR(255) NOT NULL`;
- `final_score INTEGER NOT NULL`;
- `placement INTEGER NOT NULL`;
- `played_at DATETIME(6) NOT NULL`;
- `player_count INTEGER NOT NULL`;
- `elo_before INTEGER NOT NULL`;
- `elo_delta INTEGER NOT NULL`;
- `elo_after INTEGER NOT NULL`;
- `win_streak_after INTEGER NOT NULL`.

Vincoli e indici:

- primary key su `id`;
- foreign key `nickname -> users.nickname`;
- unique key `(game_id, nickname)`;
- check su score, placement, player count, Elo e win streak;
- index `idx_match_results_history(played_at DESC, game_id ASC, placement ASC)`;
- index `idx_match_results_player_count(player_count, played_at DESC)`.

Uso:

- storico immutabile dei risultati per giocatore;
- ricostruzione di `MatchHistory`;
- diagnosi di come ogni match ha modificato l'Elo.

Non esiste oggi una tabella header `matches`: un match e' rappresentato dal gruppo di righe con lo
stesso `game_id` in `match_results`.

## Query di lettura

La leaderboard globale usa una window function:

```sql
RANK() OVER (ORDER BY elo DESC, wins DESC, nickname ASC)
```

L'ordinamento premia prima Elo, poi numero di vittorie, poi nickname. Poiche' `nickname` e' unico e
fa parte dell'`ORDER BY`, l'ordine finale e' deterministico.

Lo storico viene letto da `match_results`, ordinato per:

- `played_at DESC`;
- `game_id ASC`;
- `placement ASC`;
- `nickname ASC`.

Il mapper raggruppa le righe consecutive per `game_id` in oggetti `MatchHistory`.

## Transazioni e atomicita'

`recordMatchAndGetRanking` e' l'operazione transazionale principale.

Flusso interno:

1. normalizza e valida tutti gli input fuori dalla transazione;
2. apre una connessione MySQL;
3. imposta `autoCommit(false)`;
4. verifica se `game_id` e' gia' presente;
5. crea utenti e rating mancanti con upsert mirati su duplicate key;
6. carica gli snapshot rating dei giocatori;
7. calcola placement e aggiornamenti Elo;
8. aggiorna `user_ratings` in batch;
9. inserisce le righe `match_results` in batch;
10. legge leaderboard globale e rank del richiedente nello stesso stato transazionale;
11. esegue `commit`.

Qualunque eccezione prima del commit attiva `rollback`.

La connessione e' aperta con isolamento `Connection.TRANSACTION_SERIALIZABLE`. Questo rende piu'
conservativa la concorrenza tra partite che coinvolgono gli stessi giocatori o lo stesso `game_id`.

## Idempotenza e duplicate game

Il sistema usa due difese contro il doppio conteggio della stessa partita:

- difesa in memoria: `GameOrchestrator.rankingRecorded`;
- difesa persistente: `MySqlRankingService.gameAlreadyRecorded` prima degli insert e unique key
  `(game_id, nickname)` sulle righe match.

Se il `game_id` e' gia' presente nello storico, il service lancia `DuplicateGameException`.

La difesa corrente e' adeguata per il flusso attuale, ma non e' il modello piu' forte possibile:
non esiste una tabella `matches` con primary key su `game_id`. Una futura evoluzione dovrebbe
introdurre una riga header `matches(game_id PRIMARY KEY, played_at, player_count, ...)` e inserire
quella riga come primo passo della transazione. In quel modello l'idempotenza sarebbe garantita da
un vincolo univoco diretto sul match, non solo dal gruppo di righe giocatore.

Il `game_id` persistito non coincide con `Game#getId()`: e' un UUID dedicato al ranking. Questa
scelta evita collisioni tra storico persistente e contatori runtime che ripartono dopo un riavvio.

## Concorrenza e retry

`MySqlDatabaseSupport.executeWithRetry` riesegue l'intera operazione quando MySQL segnala errori
transitori:

- SQL state `40001`;
- SQL state `41000`;
- error code `1205` lock wait timeout;
- error code `1213` deadlock.

Il numero massimo di retry arriva da `RankingConfig.maxBusyRetries`, default `3`. Il backoff e'
lineare:

```text
sleep = 50ms * attempt
```

Le operazioni transazionali sono retry-safe perche':

- i cambiamenti parziali vengono rollbackati prima di propagare la `SQLException`;
- gli insert di utenti/rating sono idempotenti;
- la duplicazione di match viene controllata prima degli aggiornamenti Elo;
- gli aggiornamenti rating e gli insert storico sono nello stesso commit.

Gli errori di dominio, come `DuplicateGameException`, non vengono ritentati.

Per carichi piu' alti si puo' migliorare il controllo della concorrenza caricando gli snapshot con
lock espliciti e ordinamento stabile dei nickname, cosi' da ridurre deadlock tra partite che
coinvolgono player sovrapposti.

## Bootstrap runtime

Il bootstrap avviene in `ServerServicesFactory.create`.

Se il database e' abilitato:

1. `ServerRuntimeConfig.fromEnvironment` legge URL, credenziali e retry;
2. `MySqlDatabaseBootstrap.ensureReady` crea il database target se manca;
3. verifica raggiungibilita' con `SELECT 1`;
4. esegue i DDL dei moduli schema registrati;
5. verifica che le tabelle richieste esistano;
6. costruisce `MySqlRankingService`;
7. il costruttore del service richiama a sua volta `database.initializeSchema`.

I DDL usano `CREATE TABLE IF NOT EXISTS`, quindi l'inizializzazione e' idempotente. Il bootstrap
mantiene anche una cache per target e moduli schema gia' inizializzati nello stesso processo.

Se il database non e' raggiungibile o lo schema non e' sano, `MySqlDatabaseHealthException` ferma
l'avvio del server. `LaunchServer.run` intercetta l'eccezione, stampa lo stack trace su `err` e
ritorna exit code `1`.

## Configurazione locale

Default runtime:

- `MESOS_DB_URL`: `jdbc:mysql://localhost:3306/mesos?connectionTimeZone=UTC&allowPublicKeyRetrieval=true&useSSL=false`;
- `MESOS_DB_ADMIN_URL`: derivato dal target usando database `mysql`;
- `MESOS_DB_USER`: `root`;
- `MESOS_DB_PASSWORD`: `root`;
- `MESOS_DB_MAX_BUSY_RETRIES`: `3`;
- `MESOS_NO_DB`: se `true`, disabilita il ranking persistente.

Il repository include `docker-compose.yml` con MySQL `8.4`:

- database default `mesos`;
- root password default `root`;
- porta default `3306`;
- timezone `+00:00`;
- charset `utf8mb4`;
- collation `utf8mb4_bin`;
- volume `mysql_data`.

Variabili del compose:

- `MESOS_MYSQL_DB`;
- `MESOS_MYSQL_ROOT_PASSWORD`;
- `MESOS_MYSQL_PORT`.

Per sviluppo locale il percorso atteso e':

```bash
docker compose up -d mysql
./mvnw test
```

`./mvnw test` esegue solo i test unitari. Per validare query, schema e transazioni MySQL serve
`./mvnw verify` con un MySQL locale raggiungibile o con Docker/Testcontainers funzionante.

## Configurazione test

I test di integrazione ereditano da `MySqlIntegrationTestSupport`.

Strategia:

- preferisce un MySQL raggiungibile via `MESOS_DB_*` o default locali;
- se non disponibile, prova Testcontainers con immagine `mysql:8.4`;
- crea un database isolato per ogni test;
- elimina i database creati in `@AfterEach`.

Il backend test puo' essere forzato con:

- system property `mesos.test.mysql.backend`;
- env var `MESOS_TEST_MYSQL_BACKEND`.

Valori supportati:

- `auto`;
- `external` o `local`;
- `testcontainers` o `container`.

## Punti di integrazione col server

### Avvio server

`LaunchServer` costruisce `ServerServices`, passa `rankingService` a `ServerController` e poi avvia
RMI e TCP. Il controller conserva il riferimento e lo passa a `GameOrchestrator` quando una lobby
parte.

### Login e nickname

Stato corrente:

- TCP: `ClientCommandHandler -> VirtualViewTCP.authenticate -> ServerController.authenticate`;
- RMI: `RmiSessionFactory.authenticate -> ServerController.authenticate`;
- controllo effettivo: `ServerSessionRegistry`, solo in memoria e solo sulle sessioni attive.

Integrazione mancante:

- chiamare `rankingService.isNicknameAvailable` e/o `rankingService.registerUser` durante il login;
- decidere la semantica di nickname persistente: riservato per sempre oppure riutilizzabile dopo
  logout;
- mappare errori database/auth in payload protocol-facing coerenti per TCP e RMI.

### Fine partita

Stato corrente:

- `Game` calcola il final scoring e imposta `GameStatus.FINISHED`;
- `GameOrchestrator` osserva lo stato dopo ogni comando pubblico;
- se il ranking e' abilitato, registra il match;
- salva il `RankingResult` in memoria.

Integrazione mancante:

- payload server per inviare rank/leaderboard finale ai client;
- comandi client per richiedere leaderboard e storico;
- gestione UX del caso in cui la partita sia conclusa ma il ranking non sia stato salvato.

## Test presenti

Il package ranking ha copertura su:

- DTO e validazioni: `MatchScoreTest`, `LeaderboardEntryTest`, `MatchRankingEntryTest`,
  `MatchHistoryTest`, `RankingResultTest`, `RatingSnapshotTest`;
- configurazione e validatore: `RankingConfigTest`, `RankingInputValidatorTest`,
  `MySqlConnectionConfigTest`;
- calcolo Elo: `EloRatingCalculatorTest`;
- query e supporto database: `MySqlRankingQueriesTest`, `MySqlDatabaseSupportTest`;
- service MySQL: `MySqlRankingServiceTest`, `MySqlRankingServiceIntegrationTest`;
- eccezioni: `DuplicateGameExceptionTest`, `RankingDataExceptionTest`;
- integrazione orchestrator: `GameOrchestratorRankingTest`;
- runtime config e no-db: `ServerRuntimeConfigTest`, `ServerServicesFactoryTest`,
  `NoOpRankingServiceTest`.

## Limiti noti

- La registrazione persistente del nickname non e' ancora agganciata al login TCP/RMI.
- Il risultato ranking finale non viene ancora inviato ai client.
- Non esistono endpoint/protocol commands per leaderboard e storico.
- Non c'e' una tabella `matches` con unique key su `game_id`.
- La registrazione nickname non usa una transazione esplicita tra `users` e `user_ratings`.
- I parametri Elo sono in codice, non configurabili via environment.
- Il modulo assume `finalScore >= 0`; se il dominio gioco permette prestige points negativi, il
  mapping verso `MatchScore` deve essere rivisto.
- Lo schema usa `CREATE TABLE IF NOT EXISTS`, ma non ha versionamento o migrazioni evolutive.
- Le query leaderboard e history restituiscono liste complete, senza paginazione.
- Non ci sono metriche o logging strutturato per retry, deadlock, duplicate game o ranking failure.

## Evoluzioni possibili

- Integrare `RankingService.registerUser` nel flusso auth e rendere persistente la semantica dei
  nickname.
- Aggiungere payload e comandi per leaderboard, storico e ranking finale.
- Introdurre tabella `matches` con `game_id` primary key e stato header del match.
- Aggiungere lock espliciti sugli snapshot rating dei giocatori coinvolti.
- Estrarre mapper SQL dedicati se `MySqlRankingService` cresce ancora.
- Versionare lo schema con migration tool o tabella interna di schema version.
- Rendere configurabili i parametri Elo e registrarne il valore nello storico per audit.
- Aggiungere paginazione e filtri su leaderboard/history.
- Aggiungere test concorrenti multi-thread su match con player sovrapposti e duplicate game.
