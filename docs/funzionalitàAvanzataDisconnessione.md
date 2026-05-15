# Funzionalità Avanzata di resilienza alle disconnessioni

L'obbiettivo è ottenere le seguenti funzionalità:
- Quando il ping pong rileva che un player si è disconesso aggiorna il session registry scrivendo su una mappa da che game si è disconesso
Dopodiche aggiorna il game dicendodgli che un determinato player si è disconnesso. Ricordo che se è rimasto un solo player il game
ha 5 minuti di tempo prima che si riconneta qualcuno, altrimenti termina e chi ha vinto è il player che è rimasto fino alla fine,
probabilmente ci sono da adattare un po i payload.

- Il player può riconettersi e in tal caso viene inserito direttamente nella partita che aveva abbandonato


I punti da tenere bene a mente sono che dobbiamo usare il sessionregistry e le code come previsto senza svalicare.


## Gestione in caso di disconessione
In caso di disconessione di un determinato player(scoperta dalla pingpong connection) viene messo in coda sul game controller 
un comando, che richiede la chiamata di un metodo handledisconnection(player) sul game. 

L'idea è quella di avere una lista di stato di salute delle connessioni  :Player : Boolean
Appena mi viene chiamato il metodo io segno quel player come disconnesso. A questo punto entro in una serie di casi che devo gestire
accuratamente. Ricordo però in generale che il comportamento obbiettivo che voglio ottenere è il seguente:
- Se un player di disconnette durante la fase movetotem e ha già piazzatto il suo totem segnamo solo sulla mappa che non c'è.
Se invece è il suo turno lo skippo e lo metto in ultima starting tile. Se è invece il turno di un altro player, e tale player disconesso deve
ancora muovere devo aggiungere metodo automatico che fa skip di player che sono disconessi in automatico fino ad arrivare al turno di un player connesso, quelli 
disconessi vengono piazzati in ultima posizione sulla starting tile.
A questo punto arrivo alla fase di choosecard, dal momento che il player disconesso potrebbe essere sulla starting tile e devo chiamare alla board il riordino
quando lo chiamerò esso non potrà pescare le carte poichè nella lista non è proprio presente. Quando l'ultimo giocatore ha pescato facciamo la risoluzione degli eventi
applicando i malus a tutti. A questo punto viene chiamato il metodo della board che mi restituisce il nuovo ordine con il quale muovere i totem,
e io qui faccio la seguente cosa: avanzo i player del TurnFlow fintanto che ne vedo di disconessi fino ad arrivare ad un player che è attivo.
Quando un player si riconnette lo metto come attivo nella mappa, per poter riprendere a giocare (nel turno corrente)dopo una disconnessione esso deve rientrare se aveva già piazzato il totem
deve riconnetersi prima di andare in choose card, se non lo aveva ancora piazzato va direttamente al turno dopo. Per tornare a giocare nel turno dopo 
essendosi disconesso in tale turno, deve rientrare prima che l'ultimo player finisca la fase di pescata.

- Se un player di disconnette durante la fase di pescata, se ha già pescato tutto ok verra skippato automaticamente
rebuilder del turno alla fine del draw, se non ha ancora pescato o è attualmente il suo turno lo mettiamo sullo starting tile in posizione più bassa che c'è 
disponibile e andiamo avanti fintanto che non c'è un player connesso che puo pescare.

Le modifiche sono relativamente limitati, due metodi nel game, aggiustare le chiamate di autoskip in move totem in draw card
e al reset della board per il nuovo turno, e probabilmente qualche chiamata.

Per il fatto di controllare se c'è almeno un player possiamo mettere metodo che ritorna bool, cosi il game controller che gira su thread
Puo campionare il tempo che passa prima che qualcuno si riconnetta, se arriviamo allo scadere del tempo dobbiamo forzare sul game
calculate finalresult in qualche modo e mandare payload di chiusura a tutti e rimettere il rimasto in pre lobby.


Cose da cambiare: Se qualcuno si disconette nel move totem e ha già mosso può rimanere li, se deve ancora venire come al solito.
Durante il passaggio da move totem a drafting la coda di persone che vengono messe nel turnoder per poter pescare viene presa dai giocatori attualmente
in partita. Ovvero mi faccio dare dalla board ordine di risoluzione, lo leggo in ordine, se trovo persona disconessa chiamo metodo della board che
mi mette tale persona più in fondo possibile sullo starting tile, chiamato movePlayerInLastStartingTile() (horrible name), appena trovo una persona che è connessa
la lista inizia da li(questo per permettere che una persona che ha fatto il move totem e si è disconessa ma è rientrata prima della fine del move totem
possa comunque fare la pescata).
In fase di pescata se qualcuno crasha e ha già pescato nessun problema resta dove è, se invece è il suo turno ed è crashato lo mettiamo
in ultima posizione starting tile. Alla ripartenza durante il reset board per la ripartenza del turno, facciamo come fatto per il passaggio da move totme
a draft, ovver recuperiamo possible lista, togliamo i non connessi che quindi non potranno partirecipare al move totem, li mettiamo per ultimi sullo starting tile
e dovranno aspettare il round dopo per poter rientrare, l'importante è che se rientrino durante il movetotme poi non possano rientrare
in draft card ma questo non succede perchè rimangono fermi sullo starting tile.





Scenario siamo nel movetotem con rounlist già creta:
Arriva disconnessione:
1) Se player ha già mosso lo segno solo come disconnesso e basta
2) Player è nel turno corrente, lo metto in ultima posizione sullo starting tile e shifto in su gli altri
3) Player deve ancora venire, ci penserà lo skip automatico a saltarlo se arriveremo nel suo turno e lui non è prensente

Durante la migrazione da movetotem a draft creiamo la nuova lista, per farlo facciamo cosi:
La scorriamo in ordine e fintanto che vedo player non connessi in questa lista , quindi vuol dire che hanno fatto la mossa ma non si sono riconnessi intempo
allora procedo a metterli in ultima posizione sullo starting tile e shiftare se ci sono altri già presenti.
Appena scorrendo la lista trovo un player che è connesso allora mi occupo di di crearla da quel punto li, dando la possibilità a chi aveva fatto il move totem
ma si è disconesso di fare il draw card se ritorna in tempo. In draw card ho avanzamento automatico fintanto che non trovo player attivo dopo il turno di un player attivo.

Scencario nel draw card di disconnessione: 
1)Già pescato, allora mettiamo per ultimo e shiftiamo i presenti nello starting tile
2) sE' il suo turno, torna allo startring tile con shift
3) Se deve ancora arrivare non è un problema

Durante il passaggio della reset board diamo la possiblità di fare il movetotem ai giocatori effettivamente connessi secondo ordine originale della board, 
per questo ora che ci penso viene rotto dal fatto che sullo starting tile stiamo rimettendo gente a caso, non va bene, dvo trovare un altra soluzione

# Regola Generale

Durante il round la posizione fisica dei player sulla board deve rappresentare quello che è davvero successo.

Non usiamo la `StartingTile` come parcheggio temporaneo per i disconnessi.

- Se un player ha mosso il totem, resta sull'`OfferSpace`.
- Se un player non ha ancora mosso il totem, resta sulla `StartingTile`.
- Se un player viene saltato durante il draft, allora viene chiuso il suo turno e torna sulla `StartingTile`.

Le liste di fase (`currentPhaseOrder`) decidono chi deve giocare in quella fase.


## MOVE_TOTEMS

### 1. Player già mosso

Se un player si disconnette dopo aver già mosso il totem:

- viene segnato come disconnesso;
- resta sull'`OfferSpace` scelto;
- non viene spostato sulla `StartingTile`;
- se rientra prima della creazione della draft order, può ancora partecipare al draft.

### 2. Player corrente

Se il player corrente si disconnette prima di muovere il totem:

- viene segnato come disconnesso;
- resta sulla `StartingTile`;
- il turno passa al prossimo player connesso;
- se rientra dopo essere stato saltato, non rientra nel `MOVE_TOTEMS` corrente.

### 3. Player che deve ancora giocare

Se un player che deve ancora muovere il totem si disconnette:

- viene segnato come disconnesso;
- resta sulla `StartingTile`;
- quando arriva il suo turno:
    - se è ancora disconnesso, viene saltato;
    - se è rientrato, può muovere normalmente.

## Passaggio MOVE_TOTEMS -> DRAFT

La draft order viene costruita dagli `OfferSpace`, quindi contiene solo player che hanno davvero mosso il totem.

Procedura:

1. leggere la resolution order dalla board;
2. scorrere i player in ordine;
3. aggiungere alla draft order solo i player connessi in quel momento;
4. lasciare fisicamente fermi gli altri player dove sono.

Quindi:

- chi ha mosso e poi è rientrato in tempo può pescare;
- chi ha mosso ma risulta ancora disconnesso non entra nel draft;
- chi non ha mosso non può entrare nel draft, perché è ancora sulla `StartingTile`.

## DRAFT

### 1. Player già pescato

Se un player si disconnette dopo aver pescato:

- viene segnato come disconnesso;
- dovrebbe essere già tornato sulla `StartingTile` tramite la normale logica di fine pescata;
- non serve fare altro.

### 2. Player corrente

Se il player corrente si disconnette prima di pescare:

- viene segnato come disconnesso;
- viene chiuso il suo turno di draft;
- torna sulla `StartingTile`;
- il turno passa al prossimo player connesso nella draft order.

### 3. Player che deve ancora pescare

Se un player che deve ancora pescare si disconnette:

- viene segnato come disconnesso;
- resta sull'`OfferSpace`;
- quando arriva il suo turno:
    - se è ancora disconnesso, viene chiuso il suo turno e torna sulla `StartingTile`;
    - se è rientrato, può pescare normalmente.

## Reset Board

Il reset board non deve ricostruire artificialmente la posizione dei player.

A fine draft:

- i player che hanno pescato sono già tornati sulla `StartingTile`;
- i player saltati perché disconnessi durante il draft sono già tornati sulla `StartingTile`;
- gli `OfferSpace` dovrebbero essere stati svuotati dalla normale logica di chiusura turno.

Per preparare il prossimo `PLACING_TOTEMS`:

1. leggere l'ordine dalla `StartingTile`;
2. tenere nella nuova phase order solo i player connessi;
3. lasciare sulla `StartingTile` anche i player disconnessi, ma fuori dalla phase order.

## Invariante Finale

A inizio `PLACING_TOTEMS`:

- tutti i player fisicamente si trovano sulla `StartingTile`;
- la `currentPhaseOrder` contiene solo i player connessi;
- i player disconnessi restano sulla board, ma non ricevono turno;
- se rientrano durante il `PLACING_TOTEMS`, non vengono inseriti nella phase order già creata;
- potranno rientrare nel flusso normale dal round successivo, salvo regole diverse esplicite