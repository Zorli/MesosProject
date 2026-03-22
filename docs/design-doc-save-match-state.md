# Design Doc - Save Match State

## Obiettivo
Permettere al server di riprendere una partita non conclusa dopo crash del server o disconnessione completa dei player.

## Direzione architetturale
- La lobby deve poter rappresentare non solo un match nuovo, ma anche un match sospeso da riprendere.
- Il resume flow non deve creare un nuovo `Game`: deve ricaricare uno stato persistito e poi ricostruire l'orchestrator.
- La persistenza di una partita sospesa deve stare su PostgreSQL in un modulo dedicato, separato dal ranking.
- Il salvataggio deve essere incrementale: non va riserializzato ogni volta tutto il match, ma solo le porzioni di stato cambiate nel boundary di dominio corretto.

## Perche' PostgreSQL + Docker
Questa feature porta il server da un semplice storage accessorio a una persistenza runtime vera del match. In questo scenario `PostgreSQL + Docker` e' la scelta corretta per motivi tecnici precisi:

- riproducibilita': tutti sviluppano e testano contro lo stesso engine, la stessa versione del DB e la stessa configurazione base
- isolamento: il DB del progetto e' separato dal sistema locale dello sviluppatore
- modularita': il bootstrap schema puo' evolvere a moduli senza dipendere da installazioni manuali
- scalabilita': PostgreSQL gestisce meglio carico concorrente, locking, indici, transazioni e crescita dello schema rispetto a SQLite
- testabilita': con container dedicati e database isolati possiamo verificare davvero create, restore, cleanup e migrazioni
- portabilita': la stessa configurazione locale puo' essere riusata in CI e in ambienti successivi
- osservabilita': PostgreSQL offre strumenti migliori per ispezionare tabelle, vincoli, errori transazionali e stato del DB

Il punto non e' solo "cambiare database", ma poter trattare il match salvato come dato di dominio affidabile, verificabile e recuperabile.

## Primo slice implementato
- `Lobby` e `LobbyManager` supportano una lobby `RESUMABLE` con roster originale riservato.
- Una lobby resumable passa a `READY` solo quando tutti i player originari sono tornati.
- `createGameFromLobby(...)` rifiuta una lobby resumable, per evitare di generare per errore una nuova partita al posto del restore.

## Stato da salvare
Il dominio attuale suggerisce di separare il salvataggio in quattro blocchi principali:

### 1. Match root
- `game_id`
- `status`
- `current_era`
- `created_at`
- `updated_at`
- `last_checkpoint_type`

### 2. Turn state
- round corrente
- fase corrente del round
- ordine dei player nel round
- indice del player attivo
- mappe delle scelte residue upper/lower di `TurnState`

### 3. Board state
- carte visibili in upper row
- carte visibili in lower row
- stato dell'offer path
- ordine di risoluzione derivato dalle posizioni dei totem
- building deck rimanente
- tribe deck rimanente

### 4. Player state
- nickname
- totem
- food
- prestige points
- carte possedute nella tribe
- stato connessione `online/offline`
- lock di sessione per evitare doppia connessione contemporanea sullo stesso match

## Identificazione carte
- Salvare le carte per `card_id` ha senso ed e' la scelta corretta.
- Nel model ogni `Card` ha gia' un identificatore stabile (`Card#getId()`).
- `ParserJson` e `DeckTemplate` costruiscono le carte a partire da `deckSetup.json`, quindi il DB deve salvare solo l'identita' e la posizione della carta, non il payload completo della carta.
- Il deck JSON diventa il catalogo statico; PostgreSQL salva solo stato runtime e collocazione delle carte.

## Schema consigliato
La soluzione piu' semplice da evolvere e' normalizzare il runtime del match in piu' tabelle, evitando un unico `snapshot_json`.

### `saved_matches`
- una riga per match
- chiave primaria `game_id`
- metadati e stato globale del match

### `saved_match_players`
- una riga per player per match
- chiave primaria composta `(game_id, nickname)`
- risorse player e stato connessione
- vincolo univoco logico sul player nel match

### `saved_match_player_cards`
- una riga per carta posseduta da un player
- colonne: `game_id`, `nickname`, `card_id`, `card_zone`, `position_index`
- `card_zone` distingue `TRIBE_CHARACTER` e `TRIBE_BUILDING`

### `saved_match_board_cards`
- una riga per carta presente sul board o nei deck
- colonne: `game_id`, `card_id`, `card_zone`, `position_index`
- `card_zone` distingue `UPPER_ROW`, `LOWER_ROW`, `TRIBE_DECK`, `BUILDING_DECK`, eventuali `DISCARD`

### `saved_match_turn_order`
- una riga per player nell'ordine del round
- colonne: `game_id`, `turn_position`, `nickname`

### `saved_match_offer_spaces`
- una riga per spazio dell'offer path
- occupante corrente e stato dello spazio

### `saved_match_turn_choices`
- una riga per player
- contiene `remaining_upper_choices`, `remaining_lower_choices`

Questa decomposizione permette update piccoli, controlli di consistenza e restore semplice.

## Schema relazionale definitivo proposto
Il DDL definitivo deve nascere da questo modello relazionale.

### `saved_matches`
- PK: `game_id`
- colonne:
  - `game_id`
  - `host_nickname`
  - `player_count`
  - `match_status`
  - `current_era`
  - `game_status`
  - `round_number`
  - `round_phase`
  - `turn_phase`
  - `active_player_nickname`
  - `current_player_index`
  - `last_checkpoint_type`
  - `created_at`
  - `updated_at`

### `saved_match_players`
- PK: `(game_id, nickname)`
- FK: `game_id -> saved_matches.game_id`
- colonne:
  - `nickname`
  - `totem_color`
  - `food`
  - `prestige_points`
  - `is_online`
  - `session_token`
  - `last_seen_at`
- vincoli:
  - un player puo' comparire una sola volta nello stesso match
  - `session_token` nullo quando `is_online = false`

### `saved_match_turn_order`
- PK: `(game_id, turn_position)`
- FK: `(game_id, nickname) -> saved_match_players`
- colonne:
  - `turn_position`
  - `nickname`

### `saved_match_turn_choices`
- PK: `(game_id, nickname)`
- FK: `(game_id, nickname) -> saved_match_players`
- colonne:
  - `remaining_upper_choices`
  - `remaining_lower_choices`

### `saved_match_offer_spaces`
- PK: `(game_id, offer_space_code)`
- FK opzionale: `(game_id, occupied_by_nickname) -> saved_match_players`
- colonne:
  - `offer_space_code`
  - `position_index`
  - `occupied_by_nickname`

### `saved_match_board_cards`
- PK: `(game_id, card_zone, position_index)`
- vincolo unico aggiuntivo: `(game_id, card_id)`
- colonne:
  - `card_id`
  - `card_zone`
  - `position_index`
- `card_zone` ammessi:
  - `UPPER_TRIBE`
  - `UPPER_BUILDING`
  - `LOWER_TRIBE`
  - `LOWER_BUILDING`
  - `TRIBE_DECK`
  - `BUILDING_DECK`

### `saved_match_player_cards`
- PK: `(game_id, nickname, card_zone, position_index)`
- FK: `(game_id, nickname) -> saved_match_players`
- vincolo unico aggiuntivo: `(game_id, card_id)`
- colonne:
  - `card_id`
  - `card_zone`
  - `position_index`
- `card_zone` ammessi:
  - `TRIBE_CHARACTER`
  - `TRIBE_BUILDING`

## Relazioni e regole di cardinalita'
- un match ha molti player
- un match ha molte carte sul board o nei deck
- un match ha molte carte assegnate ai player
- una carta puo' stare in una sola zona del match
- un player puo' appartenere a una sola riga per match
- l'`active_player_nickname` deve sempre riferirsi a un player del match
- l'ordine turno deve essere una permutazione dei player del match

## Strategia di restore guidata dallo schema
Lo schema deve essere considerato valido solo se consente questo flusso senza ambiguita':

1. carico `saved_matches`
2. carico i player e ricostruisco `Player` e `Tribe`
3. carico le carte dei player e le reinserisco nelle tribe
4. carico le carte board/deck e ricostruisco `UpperPile`, `LowerPile`, `TribeDeck`, `BuildingDeck`
5. carico `saved_match_offer_spaces`
6. carico `saved_match_turn_order`
7. carico `saved_match_turn_choices`
8. ricostruisco `TurnState` e `TurnOrderTrack`
9. costruisco `Game`

Se un passo non e' ricostruibile solo dal DB + catalogo carte statico, lo schema non e' ancora sufficiente.

## Checkpoint di salvataggio
I checkpoint applicativi da supportare sono:

### `GAME_INITIALIZED`
Salva:
- root match
- tutti i player
- deck iniziali
- board iniziale
- round iniziale

### `TURN_STARTED`
Salva:
- round corrente
- ordine turno
- player attivo
- carte visibili sul board
- deck rimanenti

### `ERA_CHANGED`
Salva:
- `current_era`
- nuove carte visibili
- nuovi building disponibili
- deck rimanenti dopo refill

### `TURN_FINISHED`
Salva:
- food e prestige aggiornati
- carte prese dai player
- board ripulito o aggiornato
- ordine del turno successivo se gia' determinato

### `MATCH_COMPLETED`
Salva:
- punteggi finali
- ranking aggiornato
- stato match `COMPLETED`

Poi il match persistito puo' essere eliminato in una transazione finale separata, dopo che ranking e chiusura sono andati a buon fine.

## Boundary model / orchestrator / persistence
### Nel model
- `Game` resta aggregate root del dominio.
- `Game` deve esporre metodi che descrivono lo stato in forma persistibile, non conoscere SQL o JDBC.
- `Board`, `TurnState`, `TurnOrderTrack`, `Player`, `Tribe` devono esporre getter o snapshot piccoli e mirati.

### Nell'application layer
- `GameOrchestrator` coordina i comandi client.
- Dopo ogni transizione di dominio rilevante, chiama un servizio applicativo di persistenza.
- Qui vanno le policy "quando salvo" e "quando creo la lobby resumable".
- Qui deve vivere anche la policy "quando posso mandare ack al client" in caso di save failure.

### Nell'infrastruttura
- `SavedMatchRepository` incapsula PostgreSQL.
- Un `SavedMatchMapper` traduce `Game` in record persistibili e viceversa.
- Un `SavedMatchConsistencyChecker` valida invarianti prima di confermare il save o dopo il restore.

Per la policy applicativa sui fallimenti di persistenza, il contratto di ack verso il client e la
test matrix da fissare, vedere [design-doc-save-failure-policy.md](/Users/tommaso/Developer/ing-sw-2026-Vaccari-Zorloni-Viggiano-Zanatta/docs/design-doc-save-failure-policy.md).

## API applicative da esporre
### `SavedMatchService`
- `initializeMatch(Game game, List<Player> players)`
- `saveTurnStarted(Game game)`
- `saveEraChanged(Game game)`
- `saveTurnFinished(Game game)`
- `markPlayerOnline(String gameId, String nickname, String connectionToken)`
- `markPlayerOffline(String gameId, String nickname)`
- `resumeMatch(String gameId)`
- `completeMatch(Game game)`
- `deleteMatch(String gameId)`

### `SavedMatchRepository`
- `insertMatchRoot(...)`
- `updateMatchCheckpoint(...)`
- `replaceTurnOrder(...)`
- `replaceTurnChoices(...)`
- `replaceBoardCards(...)`
- `replacePlayerCards(...)`
- `updatePlayerResources(...)`
- `updatePlayerPresence(...)`
- `loadMatch(String gameId)`
- `deleteExpiredInProgressMatches()`

Il repository deve lavorare in transazione per checkpoint, non con tante transazioni scollegate.

## Integrazione minima con `GameOrchestrator`
Con il codice attuale l'orchestrator nasce solo da una factory di match nuovi:
- [GameOrchestrator.java](/Users/tommaso/Developer/ing-sw-2026-Vaccari-Zorloni-Viggiano-Zanatta/src/main/java/it/polimi/ingsoft/mesos/server/model/Orchestration/GameOrchestrator.java#L40)
- [GameFactory.java](/Users/tommaso/Developer/ing-sw-2026-Vaccari-Zorloni-Viggiano-Zanatta/src/main/java/it/polimi/ingsoft/mesos/server/model/Factory/GameFactory.java#L19)

Per supportare il resume con modifiche minime, la soluzione consigliata e':

### 1. Lasciare invariata `GameFactory`
`GameFactory#createGame(...)` resta dedicata solo ai match nuovi.

### 2. Aggiungere un secondo costruttore a `GameOrchestrator`
- `GameOrchestrator(GameFactory gameFactory, ArrayList<Player> players)` per il flow attuale
- `GameOrchestrator(Game restoredGame)` per il flow di resume

Il secondo costruttore:
- assegna `this.game = restoredGame`
- assegna `this.players = restoredGame.getPlayers()`
- non richiama `setup()`
- non passa dalla factory

### 3. Introdurre un entry point applicativo separato per il resume
In `LobbyManager` o in un service dedicato:
- `resumeGameFromLobby(int lobbyId, String gameId)`

Flow:
1. valido la lobby `RESUMABLE`
2. verifico che tutti i player attesi siano rientrati
3. chiamo `savedMatchService.resumeMatch(gameId)`
4. costruisco `new GameOrchestrator(restoredGame)`
5. porto la lobby a `STARTED`

### 4. Non mischiare "new game" e "restore game" nello stesso metodo
`createGameFromLobby(...)` deve restare dedicato al caso standard.
Il restore deve avere un metodo diverso, altrimenti il branching sporca la semantica del flow principale.

### 5. Estensione opzionale futura
Se piu' avanti servira' uniformare ancora di piu' i due flow, si puo' introdurre un piccolo provider:
- `GameBootstrapper#createNewGame(players)`
- `GameBootstrapper#restoreGame(gameId)`

Ma al momento sarebbe un livello in piu' non necessario.

## Restore flow
1. La lobby resumable viene creata usando `game_id` e roster originario.
2. I player rientrano; per ciascuno si aggiorna `online/offline`.
3. Quando tutti sono presenti, il server esegue `resumeMatch(gameId)`.
4. Il mapper ricostruisce `Player`, `Tribe`, `Board`, `TurnState`, `TurnOrderTrack`.
5. Si crea un `Game` gia' idratato, senza passare dal normale setup di un match nuovo.
6. L'orchestrator viene ricostruito sul `Game` ripristinato.

## Consistenza e verifiche
Al salvataggio e al restore vanno verificati almeno:
- nessuna carta duplicata nello stesso match
- numero totale carte coerente con il catalogo atteso per quel player count
- ogni carta appartiene a una sola zona
- tutti i player del turno order esistono nel match
- `activePlayer` appartiene al turn order
- le scelte residue di `TurnState` sono definite solo per player del match

## Cleanup dei match non conclusi
- Un trigger PostgreSQL puo' aiutare a pulire i match scaduti durante le write.
- Da solo non garantisce la rimozione "esattamente dopo 2 ore", perche' in PostgreSQL il trigger scatta solo su insert/update/delete.
- Se il requisito e' forte, serve anche un job periodico applicativo.
- Quindi la soluzione robusta e':
  - trigger di cleanup opportunistico
  - metodo applicativo `deleteExpiredInProgressMatches()`
  - invocazione del cleanup a bootstrap server e a ogni save checkpoint

## Cleanup periodico automatico
Se vogliamo che il DB verifichi in modo periodico e autonomo i match scaduti, le opzioni corrette sono:

### Opzione: scheduler applicativo
- il server esegue periodicamente `deleteExpiredInProgressMatches()`
- vantaggio: nessuna dipendenza da estensioni PostgreSQL
- limite: il cleanup dipende dal processo server

### Decisione consigliata
- tenere il trigger attuale come cleanup opportunistico
- aggiungere uno scheduler applicativo come baseline affidabile

In pratica: si, un controllo periodico automatico e' possibile, ma il trigger da solo non basta.

## Validazione dello schema prima del DDL finale
Prima di fissare lo schema come definitivo dobbiamo provare che sia sufficiente a riesumare una partita vera.

### Test minimi obbligatori
1. setup match nuovo e persistenza `GAME_INITIALIZED`
2. restore immediato dopo setup
3. save a `TURN_STARTED` con board popolato e deck ridotti
4. restore a meta' turno con `activePlayer` e scelte residue valorizzate
5. save dopo cambio era
6. restore dopo cambio era con nuove carte building/tribe corrette
7. save a `TURN_FINISHED` con risorse player aggiornate
8. restore completo e confronto strutturale tra stato atteso e stato ricostruito
9. persistenza di due match in parallelo senza contaminazione
10. blocco di doppia connessione dello stesso player sullo stesso match

### Criterio di accettazione
Lo schema e' definitivo solo quando un test di round-trip:
- parte da un `Game`
- persiste nel DB
- ricostruisce un nuovo `Game`
- confronta tutte le parti persistite rilevanti

e il confronto risulta coerente.

## Evoluzione consigliata in step
1. Introdurre DTO persistenti e `SavedMatchRepository` senza toccare ancora il flow di resume.
   Stato: completato.
2. Aggiungere nel model i getter minimi mancanti per leggere deck residui, board rows, offer path e ordine turno.
   Stato: completato.
3. Implementare `SavedMatchMapper` per `Game -> persistence` e `persistence -> Game`.
   Stato: completato.
4. Salvare solo ai checkpoint `GAME_INITIALIZED`, `TURN_STARTED`, `ERA_CHANGED`, `TURN_FINISHED`, `MATCH_COMPLETED`.
   Stato: parziale. I checkpoint esistono tutti nel layer di persistenza, ma `GameOrchestrator` non e' ancora il solo entry point applicativo per tutte le transizioni durevoli.
5. Gestire lo stato di presenza dei player e il lock di connessione.
   Stato: sostanzialmente completato.
6. Aggiungere `resumeMatch(gameId)` e integrazione con `LobbyManager`.
   Stato: completato.
7. Aggiungere consistency checker e test di restore completo.
   Stato: completato. Il consistency checker copre invarianti strutturali e semantiche di snapshot, e il restore contract esteso copre checkpoint di draw, extra-draw, era change e final scoring.

## Nota pratica
La nota pratica originale sul model non e' piu' il blocco principale: deck residui, board rows e ordine turno oggi sono leggibili in modo sufficiente per il mapping.

Il blocco residuo vero e' applicativo:
- centralizzare davvero tutti i checkpoint in `GameOrchestrator`
- fissare la policy su save failure e ack client

I test contrattuali estesi sui flow oltre il semplice piazzamento totem sono ora attivi e verificano:
- restore a meta' draw con checkpoint `TURN_FINISHED`
- restore dopo `ERA_CHANGED`
- restore e continuazione nella fase `EXTRA_DRAWS`
- restore prima e dopo `MATCH_COMPLETED` nel final scoring

La policy di save failure e freeze del match e' documentata separatamente in [design-doc-save-failure-policy.md](/Users/tommaso/Developer/ing-sw-2026-Vaccari-Zorloni-Viggiano-Zanatta/docs/design-doc-save-failure-policy.md).
