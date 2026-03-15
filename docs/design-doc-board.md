# Design Doc - Gestione plancia di gioco (Board)

## Contesto

### Package di riferimento
`it.polimi.ingsoft.mesos.server.model.board`

### Obiettivo
Descrivere l'architettura logica e strutturale della plancia di gioco lato server, chiarendo:
- perche' e' stato scelto il pattern Facade per la classe centrale;
- quali responsabilita' sono distribuite tra la plancia e i suoi sotto-componenti fisici;
- quali invarianti di dominio sono protetti a livello di plancia e a livello di singola casella;
- quali punti di integrazione restano verso il controller della partita (`Game`).

## Problema

Il modulo della plancia deve gestire molteplici esigenze strettamente legate alle regole del gioco:
- configurazione dinamica degli spazi offerta validi in base al numero di giocatori;
- gestione del ciclo di vita delle carte (pesca iniziale, scorrimento a fine round, scarto, rimpiazzo e cambio Era);
- risoluzione degli effetti di fine round (assegnazione o pagamento cibo, penalita' prestigio, eventi di sostentamento);
- ricalcolo dell'ordine di turno basato sul posizionamento fisico dei totem.

La parte critica non e' soltanto tracciare questi dati temporanei, ma farlo in modo architetturalmente sicuro:
- il controller del gioco non deve conoscere le strutture dati interne (liste, stream, array) usate per i mazzi e gli spazi;
- lo spostamento delle carte e l'aggiornamento dell'ordine di turno devono avvenire a cascata, garantendo che non si verifichino aggiornamenti parziali o stati inconsistenti tra un round e l'altro.

## Scelta implementativa

La soluzione adottata e' un'architettura basata su Composition e pattern Facade, incapsulata nella classe principale `Board`.

Questa scelta e' ottimale per il progetto perche':
- accentra la logica di mutazione dello stato in un'unica interfaccia pubblica;
- separa nettamente le strutture fisiche vuote (create autonomamente all'interno) dai dati complessi (deck e giocatori, forniti tramite Dependency Injection);
- offre metodi ad alto livello e puramente descrittivi (es. `placePlayerOnOfferSpace`, `shiftUpperToLower`) che nascondono la complessita' logica sottostante;
- mantiene semplice e isolata la stesura dei test automatici (JUnit).

Il tradeoff e' che la classe `Board` assume il ruolo di "passacarte" per alcune operazioni elementari, aumentando leggermente la quantita' di codice boilerplate. Tuttavia, ai fini dell'incapsulamento e della sicurezza del dominio, e' la soluzione piu' solida.

## Vista d'insieme

### Facade di dominio
`Board` espone i contratti usati dal resto del server (`Game` o Controller):
- `setupInitialRows()` e `buildOfferPath()` per l'inizializzazione.
- `isSpaceAvailable(...)` e `placePlayerOnOfferSpace(...)` per la fase di piazzamento.
- `getDraftableUpperCards(...)` e `getDraftableLowerCards(...)` per filtrare le azioni legali.
- `shiftUpperToLower()`, `resolveLowerEvents()` e `rebuildOrderFromOfferPath()` per la manutenzione di fine round.

### Implementazione concreta dei sotto-componenti
Le logiche specifiche risiedono nei componenti specializzati:
- `OfferPath` e `StartingTile`: validano i codici di posizionamento, filtrano gli spazi attivi per player count e risolvono l'algoritmo per il nuovo ordine di turno.
- `UpperPile` e `LowerPile`: incapsulano la persistenza in memoria delle carte, filtrano la visibilita' in base ai requisiti e coordinano gli scarti e gli eventi.

### Supporto infrastrutturale
Non sono previsti supporti di persistenza esterna (es. SQLite) all'interno di questo specifico package, in quanto lo stato della plancia e' volatile e mantenuto in memoria per l'intera esecuzione della partita corrente.

## Stato del dominio gestito

Il modulo conserva insiemi di informazioni in memoria che rappresentano lo stato fisico della partita:
- stato delle carte attualmente visibili sul tavolo, rigorosamente divise tra riga superiore e inferiore, e differenziate per tipologia (Personaggi, Edifici, Eventi);
- stato degli spazi offerta (codice identificativo univoco, requisiti di player count, bonus erogato, occupazione attuale);
- stato dei blocchi di partenza (penalita' prestigio, bonus cibo, occupazione a fine round).

I dati emessi a seguito di un piazzamento con successo includono:
- esito booleano dell'azione;
- numero esatto di carte pescabili dalla riga superiore;
- numero esatto di carte pescabili dalla riga inferiore.

## Invarianti di dominio

Le regole attualmente difese dal modulo sono:
- gli spazi offerta instanziati devono variare strettamente per partite da `2` a `5` giocatori;
- uno spazio offerta non puo' essere occupato se gia' registrato come non disponibile per il round corrente;
- i giocatori non ricevono opzioni di pesca per carte di cui non possiedono i requisiti di costo/risorse;
- l'ordine di turno del round successivo deriva strettamente e matematicamente dalla posizione dei totem sugli spazi offerta.

Queste regole sono distribuite su due livelli:

### Livello Facade (Board e Path)
- `Board` orchestra le chiamate dall'esterno per garantire che refill, shift ed eventi avvengano nella sequenza corretta imposta dal regolamento.
- `OfferPath` agisce da scudo per le query di posizionamento e per l'estrazione sicura della lista giocatori riordinata.

### Livello Componente
- `OfferSpace` protegge il proprio stato di occupazione interno.
- `StartingBlock` esegue i controlli sulle risorse del giocatore per l'applicazione delle penalita' di prestigio.

## Componenti principali

### `Board`
Coordina l'intera plancia. Inizializza i sotto-componenti vuoti e riceve via costruttore le istanze di `TribeDeck`, `BuildingDeck` e `List<Player>`.

### `OfferPath`
Gestisce la collezione di `OfferSpace` e il ciclo di vita della `StartingTile`. Riceve codici stringa (es. "A", "B") e li mappa alle azioni di gioco, risolvendo la logica di estrazione per il ripristino dei totem.

### `StartingTile` & `StartingBlock`
Gestiscono la fase passiva e i malus previsti dal regolamento. Applicano le modifiche alle risorse dei giocatori basandosi sull'indice progressivo fornito dall'`OfferPath`.

### `UpperPile` & `LowerPile`
Classi wrapper per le collezioni `List<Card>`. Astraggono le logiche di inserimento differenziato (scartando/posizionando eventi in automatico) e raggruppano le operazioni di shift.

## Atomicita' e consistenza

Il package riduce l'incidenza di race condition o stati del tabellone non validi tramite:
- profondo isolamento dello stato (le liste di carte e spazi non vengono mai restituite come reference modificabili, ma clonate o esposte in sola lettura);
- operazioni di mutazione accorpate (`placePlayerOnOfferSpace` verifica, occupa e distribuisce bonus in una singola esecuzione continua);
- sanificazione dei parametri passati all'esterno (es. creazione dinamica di nuovi `ArrayList` per prevenire `ClassCastException` in fase di risoluzione eventi).

## Pattern e struttura

Le scelte di design principali del package sono:
- Facade per mascherare l'intero sottosistema plancia esponendo esclusivamente la classe `Board`;
- Composition per la gestione del ciclo di vita dei componenti fisici instanziati all'interno;
- Dependency Injection per disaccoppiare la logica della plancia dal parsing dei file JSON relativi ai deck e alle anagrafiche dei giocatori;
- Information Expert applicato costantemente per delegare la risoluzione logica alla classe che detiene lo specifico attributo.

## Test presenti

Il package prevede una copertura di test modulare, divisa tra collaudi unitari e di integrazione logica.

Unit test previsti per i componenti:
- `OfferSpaceTest`
- `StartingBlockTest`
- `StartingTileTest`
- `OfferPathTest`
- `UpperPileTest`
- `LowerPileTest`

Test di servizio e integrazione previsti:
- `BoardSetupTest`
- `BoardTurnActionTest`
- `BoardEndRoundIntegrationTest`

## Punti di integrazione ancora aperti

Il package e' pronto per essere agganciato al flusso di controllo (`Game`), ma restano da implementare lato Controller:
- la chiamata iniziale a `board.getAvailableOfferSpaces()` per limitare le scelte di input fornite via rete al giocatore;
- l'implementazione del ciclo di drafting reale (basato sugli indici restituiti dalla plancia);
- l'innesco puntuale di `board.rebuildOrderFromOfferPath()` al termine della fase attiva per aggiornare il `TurnOrderTrack`.

## Miglioramenti possibili

I miglioramenti futuri individuati per il modulo sono:
- estrazione dei parametri hardcoded di bilanciamento (es. i modificatori int di cibo e prestigio dentro `StartingTile` e `OfferPath`) verso un file JSON centralizzato;
- introduzione di un Data Transfer Object (DTO) in sola lettura per ottimizzare e alleggerire l'invio dello stato del tabellone attraverso i socket di rete verso la View.
