# Design Doc - Match Observer

## Contesto

### Feature di riferimento
`observer pattern` per le notifiche di partita

### Package coinvolti
- `it.polimi.ingsoft.mesos.server.controller.GameOrchestrator`
- `it.polimi.ingsoft.mesos.server.virtualview`
- `it.polimi.ingsoft.mesos.shared.payload.server`

### Obiettivo
Definire il system design dell'observer pattern usato per notificare a tutti i giocatori della stessa
partita gli aggiornamenti di stato dopo una mutazione riuscita.

Il documento chiarisce:
- qual e' il subject osservabile;
- chi sono gli observer concreti;
- quando nasce il subject;
- quando gli observer si iscrivono e si disiscrivono;
- quando scatta la notifica;
- come ogni transport (`TCP` e `RMI`) inoltra l'update al proprio client.

## Problema da risolvere

Oggi una partita viene coordinata da `GameOrchestrator`, ma non esiste ancora un meccanismo
standard per propagare in broadcast gli aggiornamenti a tutti i giocatori del match.

Serve introdurre una soluzione che garantisca:
- una sola sorgente autorevole degli aggiornamenti della partita;
- propagazione dello stesso cambiamento a tutte le sessioni collegate al match;
- nessuna dipendenza del dominio `Game` da socket, callback RMI o view;
- riuso del payload client-facing gia' esistente.

In breve:
- un client esegue una action;
- lo stato della partita cambia;
- tutti i client della partita devono ricevere l'update.

## Decisione architetturale

Il subject osservabile non deve essere `Game`, ma `GameOrchestrator`.

Motivazioni:
- `Game` e' dominio puro e non deve conoscere la rete;
- `GameOrchestrator` e' il boundary di match che sa quando una mutazione e' andata a buon fine;
- `GameOrchestrator` espone gia' `getCurrentPayload()` e quindi sa costruire lo snapshot da inviare ai client.

Quindi il pattern scelto e':
- `GameOrchestrator` = subject osservabile per una singola partita;
- `VirtualViewTCP` e `VirtualViewRMI` = observer concreti iscritti alla partita;
- `GamePayload` = snapshot condiviso usato per notificare lo stato aggiornato.

## Modello concettuale

Per ogni partita avviata esiste:
- un solo `GameOrchestrator`;
- una sola lista di observer associata a quel `GameOrchestrator`;
- un observer per ogni sessione/client della partita.

Questo implica:
- `1 lobby avviata -> 1 partita -> 1 subject`;
- `N giocatori della partita -> N observer iscritti al subject`.

La lobby non e' il subject.
La lobby e' solo il contenitore pre-match da cui nasce la partita.

## Componenti da introdurre

### `MatchObserver`
Nuova interfaccia applicativa che rappresenta un endpoint interessato agli update di una singola
partita.

Responsabilita':
- ricevere la notifica che lo stato del match e' cambiato;
- recuperare il nuovo snapshot dal `GameOrchestrator`;
- inoltrarlo al proprio client secondo il transport concreto.

Forma minima consigliata:
- `void onMatchUpdated();`

La firma minimale e' intenzionale: il recupero dello snapshot resta demandato all'observer, come da
flusso concordato.

### `GameOrchestrator`
Esteso con comportamento da subject osservabile.

Nuove responsabilita':
- mantenere l'elenco degli observer iscritti al match;
- consentire registrazione e rimozione observer;
- notificare tutti gli observer dopo una mutazione riuscita;
- non notificare nulla in caso di errore.

API minime da aggiungere:
- `addObserver(MatchObserver observer)`
- `removeObserver(MatchObserver observer)`
- `notifyObservers()`

### `VirtualViewTCP`
Observer concreto lato server per le sessioni TCP.

Nuove responsabilita':
- iscriversi alla partita quando riceve il riferimento al `GameOrchestrator`;
- reagire a `onMatchUpdated()`;
- chiedere `GamePayload` al `GameOrchestrator`;
- inviare `ServerMessageType.GAME_UPDATE` sul socket del proprio client.

### `VirtualViewRMI`
Observer concreto lato server per le sessioni RMI.

Nuove responsabilita':
- iscriversi alla partita quando riceve il riferimento al `GameOrchestrator`;
- reagire a `onMatchUpdated()`;
- chiedere `GamePayload` al `GameOrchestrator`;
- inoltrare il payload con `clientCallback.handleGameUpdate(...)`.

## Lifecycle

### 1. Fase lobby
Durante la lobby il pattern observer di match non e' ancora attivo.

In questa fase:
- esistono le `VirtualView`;
- esiste la lobby;
- non esiste ancora la partita;
- non esiste ancora il subject osservabile del match.

### 2. Creazione della partita
Quando la lobby viene avviata:

1. `LobbyManager#createGameFromLobby(...)` crea il `GameOrchestrator`;
2. il `GameOrchestrator` nasce con una lista observer inizialmente vuota;
3. le `VirtualView` dei giocatori della partita ricevono il riferimento allo stesso orchestrator;
4. ogni `VirtualView` si iscrive come observer.

Questa e' la fase in cui il subject va istanziato: alla nascita della partita, non alla creazione
della lobby.

### 3. Partita attiva
Durante il match:
- i comandi passano attraverso `VirtualView`;
- `GameOrchestrator` coordina la mutazione;
- se la mutazione riesce, viene lanciata la notifica a tutti gli observer.

### 4. Chiusura sessione o uscita dal match
Quando una sessione termina:
- l'observer deve essere rimosso dalla lista del `GameOrchestrator`;
- la rimozione evita invii verso sessioni non piu' valide;
- la disiscrizione deve essere idempotente.

## Flusso principale

### Flusso standard di aggiornamento
1. Il client A invia un comando di gameplay.
2. La `VirtualView` di A invoca il metodo corrispondente su `GameOrchestrator`.
3. `GameOrchestrator` chiama `Game`.
4. Se `Game` completa la mutazione senza eccezioni, `GameOrchestrator` chiama `notifyObservers()`.
5. Ogni observer riceve `onMatchUpdated()`.
6. Ogni observer chiama `gameOrchestrator.getCurrentPayload()`.
7. Ogni observer inoltra il `GamePayload` al proprio client.

### Flusso di errore
1. Il client invia un comando.
2. La mutazione fallisce con eccezione.
3. `GameOrchestrator` non chiama `notifyObservers()`.
4. Solo il chiamante riceve l'errore secondo il transport attuale.

Regola fondamentale:
- nessuna notifica broadcast se l'azione non e' stata applicata con successo.

## Operazioni che devono scatenare la notifica

La notifica va eseguita dopo il completamento riuscito di ogni mutazione autorevole del match:

- `setupMatch()`
- `moveTotem(...)`
- `chooseCard(...)`
- `extraDrawn(...)`
- `resolveEffects()`
- `resetBoard()`
- `calculateFinalScore()`

L'ordine corretto e':
1. mutazione del dominio;
2. eventuale persistenza/coordinamento applicativo;
3. notifica observer.

## Politica sul payload

Lo snapshot da inviare ai client resta `GamePayload`.

Scelta:
- non introdurre un nuovo DTO per l'observer nella prima iterazione;
- riusare `getCurrentPayload()` di `GameOrchestrator`;
- mantenere una sola rappresentazione client-facing dello stato.

Vantaggi:
- zero duplicazione di mapping;
- meno rischio di incoerenza tra snapshot e update;
- reuse della cache su `stateVersion`.

## Invarianti di design

Le invarianti che l'implementazione deve preservare sono:

- ogni partita ha un solo subject osservabile;
- ogni `VirtualView` iscritta osserva una sola partita alla volta;
- la stessa `VirtualView` non deve essere iscritta due volte allo stesso match;
- la notifica parte solo dopo una mutazione riuscita;
- tutti gli observer leggono lo snapshot dallo stesso `GameOrchestrator`;
- `Game` non conosce observer, view o rete.

## Policy di implementazione

### Duplicati
`addObserver(...)` deve impedire doppie iscrizioni dello stesso observer.

### Null safety
Non devono essere accettati observer null.

### Rimozione
`removeObserver(...)` deve essere safe anche se l'observer non e' presente.

### Error isolation
Se una `VirtualView` fallisce durante l'invio al client, l'errore non deve compromettere la
notifica agli altri observer della stessa partita.

Questo punto e' importante perche' il broadcast logico del match non deve dipendere dal successo
del singolo transport.

## Sequenza logica per transport

### TCP
1. `onMatchUpdated()`
2. `GamePayload payload = gameOrchestrator.getCurrentPayload()`
3. costruzione `ServerMessage(ServerMessageType.GAME_UPDATE, payload)`
4. invio sul socket del client

### RMI
1. `onMatchUpdated()`
2. `GamePayload payload = gameOrchestrator.getCurrentPayload()`
3. `clientCallback.handleGameUpdate(payload)`

## Scope esplicito della prima iterazione

Questo design copre solo il pattern observer di match.

Fuori scope in questa iterazione:
- refactor generale del networking;
- thread-safety completa del pre-match;
- redesign della lobby;
- ottimizzazioni di batching o delta updates;
- introduzione di event bus generici o code asincrone.

## Strategia TDD consigliata

Ordine suggerito dei test:

1. `GameOrchestrator` registra e rimuove observer
2. `GameOrchestrator` non registra duplicati
3. dopo una mutazione riuscita gli observer vengono notificati
4. dopo una mutazione fallita nessun observer viene notificato
5. `VirtualViewTCP` su notifica invia `GAME_UPDATE`
6. `VirtualViewRMI` su notifica invoca `handleGameUpdate(...)`

## Conclusione

Il design scelto implementa un observer pattern a livello application layer:

- il subject e' `GameOrchestrator`;
- gli observer sono le `VirtualView` della partita;
- la notifica parte dopo ogni mutazione riuscita;
- ogni observer recupera lo snapshot dal `GameOrchestrator` e lo inoltra al proprio client.

La scelta mantiene il dominio pulito e risolve il requisito principale:
- broadcast dello stato aggiornato a tutti i giocatori della stessa partita.
