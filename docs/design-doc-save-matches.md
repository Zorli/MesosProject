# Design Doc - Save Matches

## Contesto

### Feature di riferimento
`feat/save_matches`

### Obiettivo
Permettere al server di salvare e ricaricare lo stato di una partita non ancora terminata, mantenendo chiara la separazione tra:
- dominio di gioco;
- orchestrazione delle richieste;
- persistenza infrastrutturale.

Il documento risponde a tre domande:
- cosa deve stare nel `model` e cosa nel `controller/orchestrator`;
- come modellare la persistenza di una partita attiva;
- se SQLite resti sufficiente oppure se convenga migrare subito.

## Problema

Oggi il progetto ha gia' una persistenza SQLite per il ranking, ma lo stato della partita vive solo in memoria.

Per supportare `save_matches` servono nuove garanzie:
- ogni match deve avere un identificativo persistente stabile;
- lo stato salvato deve essere consistente e ricaricabile;
- piu' partite possono salvare in contemporanea senza corrompere i dati;
- la logica di gioco non deve dipendere da SQL, JDBC o dettagli del DB.

La domanda tecnica corretta non e' "relazionale o no?", ma:
- quante scritture concorrenti avremo;
- quanto e' grosso uno snapshot;
- con quale frequenza salviamo;
- dobbiamo fare query complesse sullo stato live oppure quasi solo save/load per `gameId`.

## Osservazioni sull'architettura attuale

### Dominio
`Game` e' gia' pensato come aggregate root del match e possiede lo stato mutabile rilevante della partita.

Riferimenti:
- [Game.java](/Users/tommaso/Developer/ing-sw-2026-Vaccari-Zorloni-Viggiano-Zanatta/src/main/java/it/polimi/ingsoft/mesos/server/model/GameState/Game.java)
- [design-doc-game-state.md](/Users/tommaso/Developer/ing-sw-2026-Vaccari-Zorloni-Viggiano-Zanatta/docs/design-doc-game-state.md)

### Application layer
`GameOrchestrator` e' l'entry point del match e quindi e' il posto corretto dove coordinare:
- validazione esterna della richiesta;
- access control sul player chiamante;
- invocazione del dominio;
- trigger di persistenza dopo una mutazione significativa.

Riferimenti:
- [GameOrchestrator.java](/Users/tommaso/Developer/ing-sw-2026-Vaccari-Zorloni-Viggiano-Zanatta/src/main/java/it/polimi/ingsoft/mesos/server/model/Orchestration/GameOrchestrator.java)
- [design-doc-game-orchestrator.md](/Users/tommaso/Developer/ing-sw-2026-Vaccari-Zorloni-Viggiano-Zanatta/docs/design-doc-game-orchestrator.md)

### Persistenza esistente
Il package ranking usa una buona separazione:
- interfaccia di servizio;
- implementazione SQLite;
- supporto infrastrutturale dedicato;
- SQL centralizzato.

Riferimenti:
- [RankingService.java](/Users/tommaso/Developer/ing-sw-2026-Vaccari-Zorloni-Viggiano-Zanatta/src/main/java/it/polimi/ingsoft/mesos/server/model/ranking/RankingService.java)
- [PostgresRankingService.java](/Users/tommaso/Developer/ing-sw-2026-Vaccari-Zorloni-Viggiano-Zanatta/src/main/java/it/polimi/ingsoft/mesos/server/model/ranking/PostgresRankingService.java)
- [PostgresDatabaseSupport.java](/Users/tommaso/Developer/ing-sw-2026-Vaccari-Zorloni-Viggiano-Zanatta/src/main/java/it/polimi/ingsoft/mesos/server/model/ranking/PostgresDatabaseSupport.java)
- [design-doc-ranking-db.md](/Users/tommaso/Developer/ing-sw-2026-Vaccari-Zorloni-Viggiano-Zanatta/docs/design-doc-ranking-db.md)

Questo pattern va riusato anche per `save_matches`.

## Responsabilita': model vs controller

### Cosa deve stare nel model
Nel model devono stare:
- stato del match;
- invarianti di dominio;
- transizioni di stato;
- regole per dire se una partita e' salvabile o terminata;
- conversione tra `Game` e una rappresentazione di snapshot puramente di dominio.

Tradotto operativamente:
- `Game` deve sapere esporre uno snapshot consistente del proprio stato;
- `Game` o una factory dedicata devono sapere ricostruire il dominio da quello snapshot;
- lo snapshot deve essere espresso in classi Java del dominio, non in righe SQL o `ResultSet`.

### Cosa NON deve stare nel model
Nel model non devono stare:
- query SQL;
- apertura connessioni JDBC;
- scelta del file/path del database;
- retry su lock;
- decisione di quando salvare.

Se `Game` inizia a conoscere SQLite, avete accoppiato il cuore del dominio a un dettaglio infrastrutturale.

### Cosa deve stare nel controller/orchestrator
Nel controller di alto livello o, meglio, nel `GameOrchestrator` devono stare:
- ricezione della richiesta remota;
- verifica che il player appartenga al match;
- chiamata al metodo di dominio;
- decisione di fare save dopo un'azione che cambia stato;
- gestione errori applicativi;
- eventuale coordinamento con `LobbyManager` o registry delle partite attive.

In breve:
- `Game` decide come cambia lo stato;
- `GameOrchestrator` decide quando persistere quel cambiamento;
- il repository decide dove e come persisterlo.

## Scelta consigliata: snapshot, non event sourcing

Per questo progetto la soluzione migliore e' salvare snapshot completi del match, non una sequenza di eventi.

Perche':
- il dominio e' ancora in evoluzione;
- avete bisogno prima di tutto di ripartenza semplice;
- il costo cognitivo di event sourcing e replay sarebbe alto;
- non emerge ancora un bisogno forte di audit completo mossa per mossa.

Quindi il flusso raccomandato e':
1. il client invia una richiesta;
2. `GameOrchestrator` valida e chiama `Game`;
3. `Game` muta il dominio;
4. l'orchestrator chiede uno snapshot consistente;
5. il repository persiste lo snapshot in modo atomico.

## Modello dati consigliato

### Entita' di dominio da introdurre
- `GameSnapshot`
- `PlayerSnapshot`
- `BoardSnapshot`
- eventuali snapshot di `TurnState`, `TurnOrderTrack`, deck e carte visibili/nascoste

Queste classi devono essere serializzabili con Jackson, ma non dipendere da JDBC.

### Contratto applicativo
Introdurre una porta di persistenza, per esempio:
- `SavedGameRepository`

Metodi minimi:
- `save(GameSnapshot snapshot)`
- `Optional<GameSnapshot> findByGameId(String gameId)`
- `delete(String gameId)` a partita finita
- opzionale `listResumableGames()`

### Dove mettere cosa
- `model.GameState`: `Game`, snapshot di dominio, eventuale `GameSnapshotMapper`
- `model.persistence` oppure package dedicato `server.persistence.matches`: repository e implementazioni DB
- `Orchestration`: coordinamento save/load

## Schema persistente consigliato

Per una prima versione, la struttura piu' pragmatica e':

### Tabella `saved_games`
- `game_id` TEXT PRIMARY KEY
- `status` TEXT NOT NULL
- `version` INTEGER NOT NULL
- `player_count` INTEGER NOT NULL
- `created_at` TEXT NOT NULL
- `updated_at` TEXT NOT NULL
- `last_turn_at` TEXT NOT NULL
- `snapshot_json` TEXT NOT NULL

### Indici
- indice su `updated_at`
- indice su `status`

### Significato
- `snapshot_json` contiene lo snapshot completo serializzato;
- le colonne denormalizzate servono per query amministrative o resume list;
- `version` serve per optimistic locking e per evoluzione schema.

## Relazionale vs documentale

Il problema principale qui non e' che il database sia relazionale.

Per `save_matches` il caso d'uso dominante sembra essere:
- scrittura per chiave `gameId`;
- lettura per chiave `gameId`;
- poche query analitiche sul contenuto interno dello stato.

Questo pattern si adatta bene anche a un database relazionale usando una colonna JSON/TEXT per lo snapshot.

Quindi:
- non vi serve modellare tutto il dominio in 20 tabelle relazionali fin da subito;
- non vi serve nemmeno passare per forza a Mongo o simili;
- il nodo critico e' la concorrenza in scrittura, non il modello relazionale in se'.

## SQLite: quando va bene e quando no

### Cosa fa bene SQLite
Per il vostro progetto, SQLite ha gia' vantaggi concreti:
- zero infrastruttura esterna;
- ottimo per sviluppo e test;
- transazioni vere;
- setup semplice;
- sufficiente se i salvataggi sono poco frequenti e il server gira in un solo processo.

In piu' avete gia' abilitato:
- `journal_mode = WAL`
- `busy_timeout`
- retry applicativo

Riferimento:
- [PostgresDatabaseSupport.java](/Users/tommaso/Developer/ing-sw-2026-Vaccari-Zorloni-Viggiano-Zanatta/src/main/java/it/polimi/ingsoft/mesos/server/model/ranking/PostgresDatabaseSupport.java)

### Limite reale di SQLite
SQLite consente molta concorrenza in lettura, ma in scrittura resta sostanzialmente single-writer.

Quindi diventa un collo di bottiglia se:
- fate autosave ad ogni singola mossa;
- avete molte partite attive in parallelo;
- salvate snapshot grandi;
- volete scalare su piu' processi/server.

### Conclusione pratica
Se il salvataggio avviene:
- a fine turno;
- a fine round;
- su un unico processo server;
- con poche decine di partite attive,

SQLite puo' ancora bastare.

Se invece volete:
- save molto frequenti;
- piu' lobby/match realmente concorrenti;
- possibilita' futura di piu' istanze server;

allora conviene preparare subito la migrazione verso PostgreSQL.

## Raccomandazione

La raccomandazione non e' migrare subito il dominio da relazionale a non relazionale.

La raccomandazione e':
1. introdurre subito una porta `SavedGameRepository`;
2. persistere snapshot completi;
3. tenere il dominio indipendente dal backend;
4. partire, se volete, con una implementazione `SqliteSavedGameRepository`;
5. essere pronti a sostituirla con `PostgresSavedGameRepository` senza toccare `Game`.

In altre parole:
- il rischio architetturale vero e' l'accoppiamento;
- il rischio prestazionale vero e' il single-writer di SQLite.

## PostgreSQL: quando consigliarlo

PostgreSQL e' consigliabile gia' da subito se volete prendere una direzione piu' robusta lato server.

Vantaggi:
- scritture concorrenti reali migliori di SQLite;
- row-level locking;
- colonna `JSONB` utile per snapshot completi ma anche query mirate;
- percorso naturale se il server cresce.

Per questa feature, PostgreSQL con tabella `saved_games` + `snapshot_jsonb` e' probabilmente il punto di equilibrio migliore.

## Flusso di save/load consigliato

### Save
1. il metodo dell'orchestrator riceve la richiesta del player;
2. valida appartenenza al match e precondizioni applicative;
3. invoca `game.moveTotem(...)` o altra azione;
4. produce `GameSnapshot`;
5. chiama `savedGameRepository.save(snapshot)`;
6. notifica il risultato ai client.

### Load / resume
1. un application service chiede `findByGameId(gameId)`;
2. ricostruisce `Game` tramite mapper/factory;
3. crea un nuovo `GameOrchestrator` attorno al `Game` ricostruito;
4. ri-registra il match tra quelli attivi;
5. riapre il flusso di gioco.

### End game
Quando la partita finisce:
- persistete il ranking finale;
- eliminate il salvataggio live, oppure lo marcate come `FINISHED`.

## Best practice operative

- Non salvare da dentro `Game` chiamando il repository.
- Non serializzare direttamente oggetti JDBC-aware o componenti tecnici.
- Non esporre al client lo snapshot interno del dominio cosi' com'e'.
- Tenere separati `DTO di rete`, `snapshot di persistenza` e `aggregate di dominio`.
- Aggiungere `version` allo snapshot per gestire migrazioni future del formato.
- Rendere `save(...)` idempotente per `gameId`.

## Decisione consigliata per il branch

Per `feat/save_matches` la scelta piu' solida e':
- architettura a snapshot;
- repository astratto;
- dominio indipendente dalla persistenza;
- implementazione iniziale anche su SQLite solo se il ritmo di scrittura resta moderato;
- preferenza per PostgreSQL se volete progettare subito per piu' match concorrenti e resume robusto.

## Prossimi passi consigliati

1. Disegnare `GameSnapshot` e sotto-snapshot minimi necessari a ricostruire un match.
2. Definire l'interfaccia `SavedGameRepository`.
3. Decidere il trigger di salvataggio: fine azione, fine turno o fine round.
4. Scegliere il backend iniziale:
   - SQLite se volete massima semplicita';
   - PostgreSQL se volete assorbire meglio concorrenza e crescita.
5. Implementare il flusso di resume partendo da `gameId`.
