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

