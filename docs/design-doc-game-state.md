# Design Doc - Gestione Stato

## Contesto

### Package di riferimento
`it.polimi.ingsoft.mesos.server.model.GameState`

### Obiettivo 
Descrivere il package `game-state` come contenitore dello stato della partita, chiarendo:
- quali classiFai contiene
- quali responsabilita' assegna a ciascuna classe
- con quali componenti si interfaccia

## Problema che il package risolve
- Punto di raccolta delle informazioni generali del game
- Punto di aggregazione dei servizi di dominio offerti e che possono essere chiamati da game orchestrator.
- Check di consistenza sulle chiamate fatte da un certo client.
Gestione dello stato corrente e modifica dello stato corrente.
 
## Vista d'insieme

### Classi presenti nel package
- `Game`
- 'turnstate'
- 'turnodertrack'
## Rapporto con game orchestrator
1. GameController valida che la partita possa partire su richiesta dell'host.
2. GameController crea GameOrchestrator.
3. GameOrchestrator chiama factory.createGame(players).
4. GameOrchestrator chiama game.setup()(scelta iniziale delle pedine e primi malus/Bonus)
5. Il Game passa a stato attivo 
- GameOrchestrator = application service / adapter socket una volta iniziata la partita
- Game = aggregate root / facciata del dominio, dove game orchestrator si rivolge per effettuare le chiamate
- classi interne di Game = dettagli implementativi del dominio
Faccio predispozione per fare in modo che possa essere parallelizato l'avvio di due game, usando stesso metodo 
factory


## Analisi classe per classe

### `Game`
Responsabilità principali
Game si occupa di mantenere le informazioni rilevanti per il match e di fornire un entrypoint su cui fare le chiamate 
per il game orchestrator.
A ogni chiamata principale di game orchestrator verso game potrei ritornare un oggetto che incapsula il risultato 
dell'azione cosi che il game orchestrator possa essere notificato di questo evento.
TurnState FSM del singolo turno del giocatore
TurnOderTrack tiene conto dell'ordine del round

Stato che possiede

Operazioni che espone
- setup()
Ipotizzo che il gioco sia già stato creato e pertanto ha già gli attributi fissati, quelli necessari per il setup 
- del gioco
sono il numero di player.
Per la creazione del game chiamo una factory che mi fornisce già il gioco con tutto istanziato e pronto per essere 
- runnato a seconda del numero di giocatori

