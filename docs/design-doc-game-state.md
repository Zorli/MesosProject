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


## Analisi classe per classe

### `Game`
Nota : 
- GameOrchestrator = application service / adapter socket
  - Game = aggregate root / facciata del dominio
  - classi interne di Game = dettagli implementativi del dominio
Responsabilità principali
Game si occupa di mantenere le informazioni rilevanti per il match e di fornire un entrypoint su cui fare le chiamate 
  - per il game orchestrator.

Stato che possiede

Operazioni che espone
- setup()
Ipotizzo che il gioco sia già stato creato e pertanto ha già gli attributi fissati, quelli necessari per il setup 
- del gioco
sono il numero di player.
Per la creazione del game chiamo una factory che mi fornisce già il gioco con tutto istanziato e pronto per essere 
- runnato a seconda del numero di giocatori

