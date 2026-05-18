# Funzionalità Avanzata di resilienza alle disconnessioni

L'obbiettivo è ottenere le seguenti funzionalità:
- Quando il ping pong rileva che un player si è disconesso aggiorna il session registry scrivendo su una mappa da che game si è disconesso
Dopodiche aggiorna il game dicendodgli che un determinato player si è disconnesso. Ricordo che se è rimasto un solo player il game
ha 5 minuti di tempo prima che si riconneta qualcuno, altrimenti termina e chi ha vinto è il player che è rimasto fino alla fine,
probabilmente ci sono da adattare un po i payload.

- Il player può riconettersi e in tal caso viene inserito direttamente nella partita che aveva abbandonato


I punti da tenere bene a mente sono che dobbiamo usare il sessionregistry e le code come previsto senza svalicare.





Problemi aperti:
- C'è un momento nel quale il game esiste e poi può essere rimosso quindi il mettere in coda un messaggio può creare null pointer exception?

