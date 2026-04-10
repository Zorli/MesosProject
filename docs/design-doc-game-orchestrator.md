# Design Doc - Game Orchestrator

## Contesto

### Package di riferimento
`it.polimi.ingsoft.mesos.server.controller.GameOrchestrator`

### Classi principali
- `GameOrchestrator`
- `MatchPersistenceCoordinator`
- `MatchPersistenceFailureException`
- `MatchFinalizationFailureException`
- `MatchFrozenException`

### Obiettivo
Descrivere il ruolo del `GameOrchestrator` come boundary applicativo della partita dopo la fase
di lobby.

Il documento chiarisce:
- quali responsabilita' appartengono all'orchestrator;
- quali responsabilita' restano nel dominio `Game`;
- come vengono gestiti persistenza, ranking e payload client-facing;
- quali invarianti devono essere mantenute dai caller.

## Problema che il package risolve
Durante setup e waiting in lobby il player interagisce con il controller/lobby layer.

Quando la partita parte, pero', tutte le action di gameplay devono attraversare un solo entry
point: `GameOrchestrator`.

Questo evita tre problemi:
- il `ServerController` non diventa un collo di bottiglia con logica di partita specifica;
- il caller non deve conoscere quando salvare i checkpoint durevoli;
- il dominio `Game` resta concentrato su stato e regole, senza dipendere da persistenza, ranking o
  payload di rete.

In breve:
- `LobbyManager` prepara il match;
- `GameOrchestrator` coordina il match attivo;
- `Game` applica le regole di dominio;
- `SavedMatchService` e `RankingService` sono servizi applicativi/infrastrutturali chiamati dal
  boundary corretto.

## Vista d'insieme

### Pipeline di avvio nuovo match
1. `ServerController` autentica il nickname e gestisce il pre-match generico.
2. `LobbyManager` crea la lobby e raccoglie i player.
3. Quando la lobby e' `READY`, `LobbyManager#createGameFromLobby(...)` crea i `Player`.
4. `LobbyManager` costruisce il `GameOrchestrator` passando:
   - `GameFactory`;
   - lista player;
   - host nickname della lobby;
   - `SavedMatchService`;
   - eventuale `RankingService`.
5. `GameOrchestrator` usa la factory per creare il `Game`.
6. Da quel momento le action di gameplay devono passare dall'orchestrator.

### Pipeline di resume
1. `LobbyManager` crea una lobby `RESUMABLE` associata al `gameId` salvato.
2. La lobby diventa `READY` solo quando tutti i nickname originari sono rientrati.
3. `LobbyManager#resumeGameFromLobby(...)` chiama `SavedMatchService#restoreMatch(...)`.
4. Il servizio ricostruisce il `Game` e restituisce anche l'host nickname originale.
5. `LobbyManager` crea un `GameOrchestrator` da `Game` gia' restaurato.
6. La partita riprende con lo stesso boundary applicativo di un match nuovo.

Questa distinzione e' importante: una lobby resumable non deve mai creare un nuovo `Game`, ma deve
sempre ripristinare lo stato persistito.

## Responsabilita'

### `GameOrchestrator`
Responsabilita' principali:
- possedere il riferimento al `Game` di una singola partita;
- mantenere il roster dei player della partita;
- conservare l'host nickname usato per persistenza e ranking;
- esporre i metodi applicativi di gameplay;
- invocare il dominio solo dopo avere verificato che il match sia scrivibile;
- tradurre le transizioni riuscite in checkpoint di persistenza;
- congelare il match se un checkpoint durevole fallisce;
- costruire il `GamePayload` da inviare ai client;
- coordinare la finalizzazione del match con ranking opzionale.

Il punto architetturale e' che l'orchestrator decide quando una action puo' diventare
authoritative. Il dominio decide come cambia lo stato, ma l'application layer decide quando quel
cambiamento e' stato salvato e puo' essere riconosciuto al client.

### `Game`
Responsabilita' principali:
- mantenere lo stato mutabile della partita;
- applicare le regole di dominio;
- validare fase, turno, carte disponibili e stato partita;
- aggiornare `TurnState`, `TurnOrderTrack`, `Board`, player e tribe;
- incrementare la versione dello stato quando cambia il dominio.

`Game` non deve:
- chiamare repository o servizi SQL;
- conoscere `SavedMatchService`;
- conoscere `RankingService`;
- costruire payload di rete;
- decidere la policy di ack verso il client.

### `MatchPersistenceCoordinator`
Responsabilita' principali:
- essere posseduto da un solo `GameOrchestrator`;
- tradurre il checkpoint applicativo nel metodo corretto di `SavedMatchService`;
- mantenere fuori dal dominio la policy di persistenza;
- propagare le eccezioni di save senza nasconderle.

Il coordinator non decide se congelare il match. Questa decisione resta nell'orchestrator, perche'
ha effetto client-facing.

### `SavedMatchService`
Responsabilita' principali:
- salvare i checkpoint della partita;
- restaurare un match salvato;
- gestire presenza online/offline dei player nel match persistito;
- esporre se la persistenza reale e' abilitata.

L'orchestrator puo' lavorare anche con `NoOpSavedMatchService`, utile quando il runtime non vuole
persistenza reale.

### `RankingService`
Responsabilita' principali:
- registrare un match completato;
- calcolare e restituire il risultato ranking.

Il ranking e' opzionale: se il servizio non viene passato all'orchestrator, la finalizzazione
salva comunque il match completato e restituisce `Optional.empty()`.

## Metodi pubblici dell'orchestrator

### Costruzione da factory
Usata per un match nuovo.

Input principali:
- `GameFactory`;
- `ArrayList<Player>`;
- host nickname;
- `SavedMatchService`;
- eventuale `RankingService`.

Il costruttore:
- valida factory e player;
- crea il `Game` tramite `gameFactory.createGame(players)`;
- copia il roster;
- crea il `MatchPersistenceCoordinator`.

### Costruzione da game restaurato
Usata per resume.

Input principali:
- `Game` gia' ricostruito;
- host nickname originale;
- `SavedMatchService`;
- eventuale `RankingService`.

Il costruttore:
- riusa l'aggregate restaurato;
- prende i player dal `Game`;
- rifiuta un restored game senza player;
- ricrea il coordinator sullo stesso host nickname.

### `setupMatch()`
Flusso:
1. verifica che il match non sia frozen;
2. chiama `game.setup()`;
3. salva checkpoint `GAME_INITIALIZED`.

Il setup porta il match nello stato iniziale giocabile e apre la fase di piazzamento totem.

### `moveTotem(Player player, String tile)`
Flusso:
1. verifica che il match non sia frozen;
2. legge la fase round precedente;
3. chiama `game.moveTotem(player, tile)`;
4. se il piazzamento termina e la fase diventa `PLAYER_DRAWS`, salva `TURN_STARTED`.

Il checkpoint viene emesso solo quando la transizione verso la fase di draw e' effettivamente
avvenuta. Le singole placement intermedie non generano `TURN_STARTED`.

### `chooseCard(Player player, Card card)`
Flusso:
1. verifica che il match non sia frozen;
2. salva player attivo e fase precedente;
3. chiama `game.chooseCard(player, card)`;
4. se il turno avanza a un altro player o a un'altra fase, salva `TURN_FINISHED`.

Il metodo copre il draw standard.

### `extraDrawn(Player player, Card card)`
Flusso analogo a `chooseCard(...)`, ma delega al dominio il bonus draw.

Se il bonus draw chiude il turno o cambia fase, viene salvato `TURN_FINISHED`.

### `resolveEffects()`
Flusso:
1. verifica che il match non sia frozen;
2. chiama `game.resolveEffects()`;
3. se la nuova fase e' `FINAL_SCORING`, salva `MATCH_COMPLETED`;
4. se la nuova fase e' `RESETTING_BOARD`, salva `TURN_FINISHED`.

Questo metodo chiude il round lato applicativo e decide quale checkpoint rappresenta la transizione
di dominio appena avvenuta.

### `resetBoard()`
Flusso:
1. verifica che il match non sia frozen;
2. salva l'era precedente;
3. chiama `game.resetBoard()`;
4. se l'era cambia, salva `ERA_CHANGED`;
5. altrimenti salva `TURN_STARTED`.

Il reset board e' quindi responsabile dell'apertura del round successivo e della distinzione tra
nuovo round nella stessa era e cambio era.

### `calculateFinalScore()`
Flusso:
1. verifica che il match non sia frozen;
2. chiama `game.calculateFinalScore()`;
3. salva il checkpoint finale `MATCH_COMPLETED`;
4. se `RankingService` e' configurato, registra il match nel ranking;
5. restituisce `Optional<RankingResult>`.

La persistenza del match completato viene prima del ranking. In questo modo lo stato finale resta
recuperabile anche se l'aggiornamento ranking fallisce dopo.

## Checkpoint durevoli

I checkpoint gestiti dall'orchestrator sono:
- `GAME_INITIALIZED`;
- `TURN_STARTED`;
- `TURN_FINISHED`;
- `ERA_CHANGED`;
- `MATCH_COMPLETED`.

### Mapping operativo
- `setupMatch()` -> `GAME_INITIALIZED`
- `moveTotem(...)` -> `TURN_STARTED` solo al passaggio da placement a draw
- `chooseCard(...)` -> `TURN_FINISHED` quando il turno/fase avanza
- `extraDrawn(...)` -> `TURN_FINISHED` quando il turno/fase avanza
- `resolveEffects()` -> `TURN_FINISHED` se si passa a reset board
- `resolveEffects()` -> `MATCH_COMPLETED` se si passa a final scoring
- `resetBoard()` -> `ERA_CHANGED` se cambia era
- `resetBoard()` -> `TURN_STARTED` se inizia un nuovo round nella stessa era
- `calculateFinalScore()` -> `MATCH_COMPLETED` dopo lo scoring finale

Il caller non deve invocare manualmente `SavedMatchService` dopo una action standard. Se lo fa,
duplica una responsabilita' gia' assegnata all'orchestrator e rischia di salvare con semantica
sbagliata.

## Policy di errore

La policy adottata e' `strict durability first`.

### Regola
Una action e' completata solo se:
- la mutazione di dominio e' riuscita;
- il checkpoint richiesto e' stato persistito con successo.

### Fallimento durante un checkpoint normale
Se il dominio muta correttamente ma il save fallisce:
1. `persistOrFreeze(...)` cattura la runtime exception;
2. `frozen` diventa `true`;
3. viene lanciata `MatchPersistenceFailureException`;
4. ogni action successiva viene rifiutata con `MatchFrozenException`.

Questa policy impedisce al server di continuare in RAM dopo un errore di persistenza, evitando gap
tra stato acknowledged e stato restaurabile.

### Fallimento durante la finalizzazione
`calculateFinalScore()` usa una policy separata perche' deve coordinare due step:
- save del match completato;
- eventuale registrazione ranking.

Se uno di questi step fallisce:
1. `frozen` diventa `true`;
2. viene lanciata `MatchFinalizationFailureException`;
3. ulteriori action sono rifiutate.

Nota: se il save finale riesce e il ranking fallisce, il match e' gia' persistito come completato,
ma la finalizzazione applicativa non viene considerata chiusa in modo coerente. Per questo il match
viene comunque congelato.

## Payload verso client

`GameOrchestrator` espone:
- `getCurrentPayload()`;
- `buildPayload()`.

### `getCurrentPayload()`
Restituisce un `GamePayload` cache-ato sulla base di `game.getStateVersion()`.

Se la versione del dominio non cambia, viene riusato lo stesso payload. Se il dominio cambia, il
payload viene ricostruito.

Il metodo e' `synchronized` per evitare race sulla cache locale.

### Contenuto del payload
Il payload contiene:
- id partita;
- status partita;
- era corrente;
- turn order;
- turn state;
- player con risorse, totem e tribe;
- board con upper row, lower row, offer spaces e starting blocks.

Questa conversione resta nell'orchestrator perche' e' una rappresentazione client-facing, non una
regola di dominio.

## Boundary con il transport

Il transport deve trattare l'orchestrator come endpoint della partita attiva.

Flusso corretto:
1. riceve comando client;
2. risolve sessione, player e match;
3. chiama il metodo corrispondente su `GameOrchestrator`;
4. se il metodo torna senza eccezioni, invia success/update;
5. se riceve `MatchPersistenceFailureException`, espone errore di persistenza;
6. se riceve `MatchFrozenException`, espone errore di match congelato;
7. se riceve eccezioni di dominio, espone errore di action non valida.

Il transport non deve:
- chiamare direttamente `Game` per mutazioni standard;
- salvare checkpoint manualmente;
- mandare ack prima del ritorno dell'orchestrator.

## Invarianti architetturali

- una partita attiva ha un solo `GameOrchestrator` autorevole;
- ogni action di gameplay standard attraversa l'orchestrator;
- il dominio resta persistence-agnostic;
- il checkpoint timing non e' responsabilita' del caller;
- il save avviene prima dell'ack;
- un save fallito congela il match;
- il resume ricostruisce l'orchestrator attorno al `Game` persistito;
- il ranking viene registrato solo dopo il save del match completato;
- il payload client-facing viene costruito fuori dal dominio.

## Test di riferimento

La copertura principale sta in:
- `GameOrchestratorTest`
- `MatchPersistenceCoordinatorTest`
- `LobbyManagerGameplayFlowTest`
- `LobbyManagerResumeFlowTest`

I test fissano questi contratti:
- i costruttori creano o riusano il `Game` corretto;
- il setup salva `GAME_INITIALIZED`;
- il piazzamento completo salva `TURN_STARTED`;
- il draw standard e bonus salvano `TURN_FINISHED` quando il turno avanza;
- resolve effects salva il checkpoint corretto in base alla fase risultante;
- reset board distingue `TURN_STARTED` da `ERA_CHANGED`;
- final scoring salva prima il match completato e poi aggiorna ranking;
- payload viene cache-ato finche' la versione di stato non cambia;
- un fallimento di save congela il match;
- i costruttori rifiutano input incoerenti.

## Note di evoluzione

### Access control
Oggi molte validazioni di turno e fase sono gia' nel dominio. Il layer di transport deve comunque
risolvere il player autenticato e passare all'orchestrator il `Player` corretto per la sessione.

Se in futuro si introduce un registry centrale dei match attivi, deve registrare orchestrator per
`gameId` e non esporre direttamente il `Game` ai caller remoti.

### Cleanup del match completato
La finalizzazione oggi coordina save completato e ranking. Il cleanup del match persistito resta una
policy separata:
- mantenere storico resumable/completato;
- eliminare snapshot non piu' necessari;
- archiviare solo dati ranking.

Questa decisione non deve essere spostata nel dominio.

### Concorrenza
La cache payload e' sincronizzata, ma i metodi di mutazione non sono marcati `synchronized`.

Il design assume che il routing applicativo serializzi le action per match oppure che venga aggiunto
un lock per orchestrator quando il transport iniziera' a gestire comandi concorrenti reali.

## Conclusione
`GameOrchestrator` e' l'application service del match attivo.

Il suo valore non e' duplicare la logica del `Game`, ma concentrare in un solo punto cio' che il
dominio non deve sapere:
- quando persistere;
- quando riconoscere una action al client;
- come reagire a un save fallito;
- come produrre payload;
- come completare il match verso il ranking.

Questa separazione mantiene il dominio testabile e indipendente dall'infrastruttura, e rende il flow
di partita piu' robusto per salvataggio, restore e futura integrazione di rete.
