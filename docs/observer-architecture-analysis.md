# Analisi Architetturale Per Observer Pattern e TDD

## Scopo

Questo documento riassume l'analisi del codice esistente per preparare una feature basata su observer pattern.
L'obiettivo non e' proporre subito l'implementazione, ma chiarire:

- dove passano oggi i flussi client/server;
- dove vive davvero lo stato autorevole;
- quali punti sono gia' adatti a diventare sorgenti di eventi;
- quali gap tecnici vanno considerati prima di iniziare il TDD.

## Perimetro del codice

- `src/main/java`: 194 file
- `src/test/java`: 102 file

Macro-moduli principali:

- `client.view`: rendering TUI/GUI
- `client.virtualserver`: proxy client-side verso il server e callback inbound
- `shared.messages` / `shared.payload`: protocollo applicativo condiviso
- `server.network`: parsing TCP e contesto connessione
- `server.virtualview`: boundary server-side per TCP e RMI
- `server.controller`: pre-match orchestration
- `server.model`: dominio di gioco, lobby, persistenza, ranking

## Architettura logica

La struttura reale del sistema e' questa:

1. `ClientView`
2. `ServerProxy` client-side
3. trasporto (`TCP` o `RMI`)
4. `VirtualView` server-side
5. `ServerController`
6. `LobbyManager` per il pre-match
7. `GameOrchestrator` per il match attivo
8. `Game` come aggregate root
9. sottosistemi di dominio (`Board`, `TurnOrderTrack`, `TurnState`, `Player`, `Tribe`)
10. servizi applicativi opzionali (`SavedMatchService`, `RankingService`)

Questa separazione e' gia' buona per introdurre un observer pattern senza contaminare il dominio con dettagli di rete.

## Flusso pre-match

### TCP

Il client invia un JSON `ClientMessage { type, payload }`.

Il percorso e':

1. `VirtualServerTCP` serializza la richiesta
2. `TcpServerMessageReceiver` deserializza le risposte
3. `ConnectionAcceptor` crea una `VirtualViewTCP` per socket
4. `ClientCommandHandler` valida `type` e payload
5. `VirtualViewTCP` invoca `ServerController`
6. la risposta torna come `ServerMessage { type, payload }`

Il pre-match oggi implementa:

- `AUTHENTICATE`
- `LIST_LOBBIES`
- `CREATE_LOBBY`
- `JOIN_LOBBY`
- `EXIT_LOBBY`
- `START_LOBBY`

### RMI

Il percorso e' simile ma tipizzato:

1. il client chiama `RmiConnectionService.authenticate(...)`
2. `RmiSessionFactory` autentica e crea una `VirtualViewRMI` dedicata
3. il client riceve uno stub `RmiServerService`
4. il server usa `RmiClientService` per callback asincroni verso il client

RMI realizza gia' una forma di callback push, ma non un observer architetturale vero e proprio: la relazione e' 1 client -> 1 sessione, non 1 sorgente stato -> N subscriber.

## Flusso di match

Quando una lobby parte:

1. `ServerController#createGameFromLobby(...)`
2. `LobbyManager#createGameFromLobby(...)`
3. `GameOrchestrator`
4. `GameFactory#createGame(...)`
5. `Game`

Da quel momento il boundary corretto non e' piu' `LobbyManager`, ma `GameOrchestrator`.

Questo e' il punto giusto dove intercettare:

- mutazioni andate a buon fine;
- creazione del payload client-facing;
- eventuale fan-out verso piu' client osservatori della stessa partita.

## Stato autorevole e snapshot client-facing

Lo stato autorevole vive in `Game`.

`Game` gestisce:

- stato della partita (`GameStatus`)
- era corrente
- board
- ordine del turno
- stato del turno
- `stateVersion`

`GameOrchestrator` costruisce il `GamePayload` da esporre ai client e lo cachea su `stateVersion`.

Questa e' una base molto importante:

- il dominio non conosce la view;
- l'application layer conosce gia' il concetto di "stato mutato -> snapshot da esporre";
- un observer puo' pubblicare `GamePayload` oppure eventi derivati senza toccare la logica di `Game`.

## Stato attuale del protocollo di rete

Il protocollo e' concettualmente definito ma non completamente implementato.

### Gia' presenti

- enum `ClientMessageType` con comandi pre-match e in-match
- enum `ServerMessageType` con `ERROR`, `PRE_MATCH_UPDATE`, `LOBBY_LIST`, `GAME_UPDATE`
- payload condivisi lato client e lato server
- interfaccia `VirtualView` che include anche i metodi di gameplay

### Non ancora chiuso end-to-end

- `ClientCommandHandler` dispatcha solo i comandi pre-match
- `VirtualViewTCP` ha tutti i metodi di gameplay non implementati
- `VirtualViewRMI` ha tutti i metodi di gameplay non implementati
- il client-side `ServerProxy` espone solo chiamate pre-match
- `VirtualServerTCP` e `ServerProxyRMI` non espongono `startLobby` ne' i metodi di gameplay
- la TUI stampa `start-lobby` nell'help ma non ha il relativo handler

Conclusione: il protocollo applicativo del match esiste a livello di contratti, ma la pipeline reale e' ancora limitata al pre-match.

## Dove introdurre l'observer pattern

### Punto consigliato

Il punto migliore e' appena sopra il dominio e appena sotto il trasporto:

- `GameOrchestrator` come sorgente di eventi di match
- un publisher/session manager per match come adapter
- `VirtualViewTCP` e `VirtualViewRMI` come subscriber/relay verso i singoli client

In pratica:

- `Game` resta puro e continua a mutare stato
- `GameOrchestrator` conclude una mutazione e produce uno snapshot/evento
- un componente esterno notifica tutti gli osservatori interessati
- ogni trasporto traduce la notifica nella sua forma concreta: JSON TCP o callback RMI

### Punto sconsigliato

Non conviene inserire l'observer:

- dentro `Socket`/`PrintWriter`/`BufferedReader`
- dentro `RmiClientService`
- dentro `Game` come dipendenza verso la rete

Questi livelli devono restare adapter, non sorgenti del dominio.

## Distinzione importante: observer di dominio vs observer di rete

Nel codice esiste gia' una micro-notifica locale in `Tribe`, dove l'acquisizione di un personaggio scatena effetti su hunter o building reattivi.

Questo pero' e':

- locale al dominio;
- sincrono;
- non orientato alla rete;
- non usato come meccanismo di broadcast tra sessioni.

Conviene trattarlo come pattern locale di dominio, separato dal futuro observer per aggiornare client e view.

## Gap architetturali rilevanti

### 1. Concorrenza del pre-match

Il server crea una sessione per client su thread diversi, ma:

- `ServerController` e' condiviso
- `LobbyManager` usa `HashMap`
- `Lobby` usa `ArrayList`
- gran parte delle mutazioni pre-match non e' sincronizzata

Quindi il modello attuale non e' realmente thread-safe a livello di lobby.

### 2. Registrazione utenti non atomica

`UserManager` usa `ConcurrentHashMap`, ma la registrazione nickname fa:

1. `containsKey`
2. `put`

Questa e' una check-then-act non atomica. Con due richieste concorrenti lo stesso nickname puo' passare entrambi i check.

### 3. Wiring incompleto dei servizi runtime

Esiste un'infrastruttura ben fatta per `ServerRuntimeConfig` e `ServerServicesFactory`, ma l'entrypoint server non la usa ancora.

Di fatto oggi `LaunchServer` costruisce solo `new ServerController()` e il match parte via default con `NoOpSavedMatchService`, senza collegare davvero bootstrap DB, saved matches e ranking al runtime reale.

### 4. Protocollo client/server disallineato

I contratti condivisi dichiarano piu' operazioni di quelle che il client puo' davvero invocare.

Questo crea il rischio di introdurre l'observer sopra API ancora incoerenti.

## Implicazioni per la strategia TDD

Per introdurre l'observer in modo sicuro, la sequenza consigliata e':

1. testare il punto di pubblicazione degli eventi a livello application layer
2. testare il fan-out verso piu' view/sessioni senza dipendere dal trasporto
3. solo dopo testare gli adapter TCP e RMI

### Primo set di test consigliato

1. `GameOrchestrator` pubblica un update dopo una mutazione riuscita
2. nessun update viene pubblicato se la mutazione fallisce
3. piu' observer ricevono lo stesso snapshot di match
4. observer rimosso non riceve piu' notifiche
5. `VirtualViewTCP` traduce l'update in `ServerMessageType.GAME_UPDATE`
6. `VirtualViewRMI` traduce l'update in callback `handleGameUpdate(...)`

### Test da non fare per primi

Non partire subito da test end-to-end su socket reali o registry RMI reali.
Prima serve un contratto pulito del publisher e dei subscriber.

## Target architetturale consigliato

Una struttura coerente con il codice attuale sarebbe:

- `MatchObserver` o `GameSessionObserver`
- `MatchEventPublisher` o `GameSessionHub`
- `GameOrchestrator` che notifica il publisher dopo ogni mutazione autorevole
- `VirtualViewTCP` e `VirtualViewRMI` che si registrano come osservatori della partita

Questo consente:

- fan-out verso tutti i player della lobby/match
- unificazione del concetto di update tra TCP e RMI
- test di business indipendenti dal trasporto
- riuso di `GamePayload` gia' esistente come snapshot osservabile

## Conclusione operativa

Il repository e' gia' strutturato abbastanza bene per introdurre un observer pattern, ma il punto corretto non e' il dominio puro e nemmeno il trasporto.

Il punto corretto e' il boundary di match:

- `GameOrchestrator` come sorgente autorevole del cambiamento
- un publisher di sessione/match come adapter
- `VirtualViewTCP` e `VirtualViewRMI` come relay verso i client

Prima di implementare, i due prerequisiti concettuali sono:

1. fissare il contratto dell'observer a livello application layer
2. decidere se sistemare subito il disallineamento del protocollo di gameplay oppure limitare la prima iterazione observer al pre-match
