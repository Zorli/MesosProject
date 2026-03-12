# Design Doc - Gestione della risoluzione delle carte evento
## Descrizione
Alla fine del turno è necessario risolvere le carte evento che si trovano nell'ultima riga. L'evento Sostentamento va
sempre risolto per ultimo. Durante la risoluzione dell'evento è necessario ispezionare la tribe di ogni giocatore, dal momento
che gli eventi portano bonus/malus che dipendono dal deck. Inoltre, se presenti, è necessario controllare anche gli
edifici, perché alcuni forniscono vantaggi aggiuntivi proprio durante gli eventi. Quindi, in generale, c'è una dipendenza da:
- Stato corrente della partita (tribe di altri giocatori, vedi evento sciamano)
- Composizione della tribe
- Eventuali edifici


## Problema
Attualmente stiamo concentrando tutta la logica della carta evento in un unico punto. Questo ci crea problemi
con gli edifici e probabilmente ci sta facendo perdere per strada qualche design pattern utile.

## Scopo
Separare:
- Chi gestisce la risoluzione
- Chi conosce la logica propria dell'evento
- Chi modifica realmente con bonus/malus i giocatori

## Soluzione ad alto livello
Potremmo separare chi conosce i dati, chi conosce la logica e chi la applica effettivamente, così da non avere
un'unica classe che si occupa di tutto.

L'idea è che durante la risoluzione, per ogni singolo giocatore, la carta evento emetta un oggetto che contiene
le informazioni dell'evento in sé e i bonus e malus effettivi da applicare.
Ovvero, l'idea è che `LowRowEventResolver` prepari un oggetto contenente una foto dello stato attuale della partita e gli outcome post
applicazione dell'evento da sovrascrivere allo stato del giocatore.
Questo perché ci sono alcuni eventi che non possono essere applicati guardando al singolo giocatore, ma hanno bisogno di
un contesto più ampio. Quindi il flusso sarebbe:
1. Viene chiamata su `LowRow` la risoluzione degli eventi.
2. `LowRow` stabilisce l'ordine di applicazione degli eventi.
3. `LowRow` prepara uno snapshot della partita da mandare alla carta evento.
4. Chiamo `applyEffect` sulla carta evento, alla quale passo il contesto globale (lo snapshot) e in cui scrivere anche i bonus
e malus per ogni singolo giocatore, i quali però verranno scritti in un secondo momento.
5. Dopo aver scritto bonus e malus per ogni singolo giocatore basandomi solo sulle carte, procedo a interrogare i suoi edifici
passandogli lo stato, così, se necessario, possono applicare i relativi bonus e malus.


Mi immagino che lo stato mantenga anche informazioni sull'evento in corso, in modo tale che l'edificio possa avere tutto il contesto necessario.
Magari tutti gli edifici possono ereditare `applyEffect` da un'interfaccia, poi solo quelli che hanno effettivamente un effetto
fanno override con modifiche, mentre gli altri non fanno nulla.
`LowRowEventResolver -> EventCard.resolve(context) -> Building modifiers -> apply final resolution`
Dove la regola base di dominio rimane nella carta evento.

## Chi fa cosa

### LowRowEventResolver
- Determina l'ordine di risoluzione (Sostentamento per ultimo)
- Costruisce contesto + stato bonus/malus da far circolare
### ContestoEvento
- Mi fa vedere lo stato attuale della partita (magari `HashMap` con la tribe di ogni giocatore)
- Altri dati utili del round
### Event Card
- Contiene la logica di applicazione dell'evento, ma non la applica lei direttamente al gioco o ai giocatori.
- Crea un oggetto `EventResolution`

### EventResolution
- Contiene il risultato teorico dell'applicazione dell'evento da applicare poi per davvero a ogni giocatore, tipo
  + food, -food, ecc.

### Metodo building event modifier
Ereditato dall'interfaccia: solo chi applica davvero l'evento fa override, riceve il risultato base dell'evento che si trova in `EventResolution`
e può applicare bonus e malus al giocatore che lo possiede.

# Esempio

Immagino venga risolto il Rituale Sciamanico:
- Chi ha più stelle ottiene bonus
- Chi ne ha di meno perde punti
- In caso di parità regola particolare
Flusso:
Il `LowRowEventResolver` costruisce l'ordine di risoluzione degli eventi, e il Rituale Sciamanico è il primo.
Viene costruito il contesto della partita e viene chiamato `ShamanRitualCard.resolve(context)`.
L'evento legge le stelle di tutti i giocatori e calcola, per esempio, `minStars`, `maxStars`, ecc.
Produce una `EventResolution` con un outcome per ogni giocatore.
Si torna al `LowRowEventResolver`, lì viene chiamato edificio per edificio di ogni giocatore e si chiede di alterare l'effetto finale
se necessario.
`LowRow` applica gli outcome ai singoli giocatori.

# Problemi aperti
E generalizzabile come struttura? Ovvero tutti i building si adattano bene a questo schema?
Se si bisogna capire bene come passare ai building il solo contesto necessario per operare, non tutto quello della partita
se non erro i building non operano su contesto globale.