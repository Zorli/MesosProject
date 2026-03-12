# Design Doc - Gestione Json per inializzazione mazzo

Idea : Avere un json con già i mazzi pre costruiti a seconda del numero di giocatori.
Applichiamo strategy pattern.
Va definito chiaramente il layout di memoria del json e i metadati che ci servono.
In fase di lettura separiamo già per era cosi poi possiamo procedere a fare shuffle assestante di liste separate per era
poi le uniamo. 

## Modello dati 
Il file `deckSetup.json` rappresenta il deck per `2`, `3`, `4`, `5` giocatori.
Non salviamo "deck base + delta", ma il contenuto completo del deck per ogni player count.

Prima di scrivere il parser necessito di capire bene se è la struttura più adatta.