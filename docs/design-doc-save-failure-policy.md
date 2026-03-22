# Design Doc - Save Failure Policy

## Obiettivo
Definire una policy applicativa non ambigua per i fallimenti di persistenza durante una partita
attiva, in modo che il restore non possa mai esporre uno stato piu' vecchio o piu' nuovo
dell'ultima azione riconosciuta come riuscita dal server.

## Contratto da garantire
Il contratto corretto da fissare e' questo:

- una action e' `acknowledged` solo se la mutazione di dominio e' stata applicata e il checkpoint
  corrispondente e' stato persistito con successo
- dopo crash o restart, il match ripristinato non puo' essere piu' indietro dell'ultima action
  acknowledged
- il sistema non deve mai riconoscere al client un avanzamento che esiste solo in memoria

Questo implica una scelta architetturale precisa: il server non puo' continuare normalmente in
"retry later" dopo un fallimento di save se vuole mantenere questo contratto.

## Decisione architetturale
La policy raccomandata e' `strict durability first`.

### Regola principale
- il transport non invia `success` al client finche' il save del checkpoint non e' riuscito

### Conseguenza
- se la mutazione di dominio riesce ma il save fallisce, il match entra in stato `FROZEN`
  o `PERSISTENCE_UNAVAILABLE`
- da quel momento il match non accetta nuove action di gameplay finche' non viene eseguito un
  recovery esplicito

### Motivazione
Questa e' la sola policy coerente con il goal:
- niente gap tra stato acknowledged e stato durevole
- niente ambiguita' sul significato di una risposta `ok`
- niente divergenza silenziosa tra RAM e PostgreSQL

## Policy esplicita da adottare
### 1. Ordine delle operazioni
Per ogni action che attraversa un checkpoint durevole:

1. `GameOrchestrator` valida il comando e invoca il dominio
2. il dominio applica la mutazione in memoria
3. `MatchPersistenceCoordinator` tenta il save del checkpoint
4. solo se il save riesce il transport invia `success`

### 2. Fallimento del save
Se il punto 3 fallisce:
- il transport non deve inviare `success`
- il match viene marcato come `FROZEN`
- tutte le action successive sul match vengono rifiutate
- il client riceve un errore esplicito di persistenza, non un errore generico

### 3. Recovery
Il recovery deve essere esplicito, non implicito:
- o il match viene chiuso e riaperto dal last durable checkpoint
- o il server fornisce una procedura amministrativa di retry/reconcile prima di riaprire il match

Nel primo slice la soluzione migliore e' la piu' semplice:
- freeze del match
- resume dal checkpoint persistito piu' recente

## Cosa non fare
### Non continuare in memoria e ritentare "piu' tardi"
Questa strategia e' `availability first`, non `durability first`.

Se il server:
- applica la mutazione
- risponde `ok`
- ma non ha ancora persistito

allora un crash puo' produrre un restore piu' vecchio dell'ultima action acknowledged.
Quindi questa soluzione viola esattamente il contratto che vogliamo garantire.

### Non fare ack ottimistico
Un `success` spedito prima del commit del checkpoint rende impossibile dire cosa sia davvero
autoritativo tra client, RAM e DB.

## Boundary contract tra layer
### `GameOrchestrator`
- e' il boundary applicativo che decide se una action puo' diventare authoritative
- non deve considerare completata una action finche' la persistenza non ha confermato il checkpoint
- se il save fallisce deve cambiare lo stato operativo del match in `FROZEN`

### `MatchPersistenceCoordinator`
- traduce la transizione di dominio nel checkpoint corretto
- non decide il comportamento client-facing
- propaga il fallimento verso l'orchestrator senza nasconderlo

### Transport client-facing
- invia `success` solo dopo il save riuscito
- invia un errore esplicito quando il save fallisce
- dopo il freeze rifiuta ulteriori action sul match con un errore di stato coerente

## Semantica delle risposte verso il client
### `success`
Significa sempre:
- mutazione di dominio applicata
- checkpoint persistito
- stato authoritative aggiornato

### `persistence_failed`
Significa:
- il server non considera authoritative l'avanzamento appena tentato
- il match e' stato congelato per evitare divergenze ulteriori
- il client deve fermarsi e attendere restore o recovery

### `match_frozen`
Significa:
- il match e' bloccato per precedente fallimento di persistenza
- nessuna nuova action di gameplay verra' accettata

## Impatto sui checkpoint
La stessa policy vale per tutti i checkpoint durevoli:
- `GAME_INITIALIZED`
- `TURN_STARTED`
- `TURN_FINISHED`
- `ERA_CHANGED`
- `MATCH_COMPLETED`

Non devono esistere checkpoint "best effort" se il contratto di restore e' forte.

## Strategia minima di implementazione
### Slice 1
- introdurre uno stato operativo di match congelato lato orchestrator
- fare propagare le eccezioni di save fino al boundary applicativo
- impedire l'ack client prima del save
- rifiutare ogni comando successivo su match congelato

### Slice 2
- aggiungere signalling client esplicito per `persistence_failed` e `match_frozen`
- aggiungere bootstrap/recovery dal last durable checkpoint

### Slice 3 opzionale
- strumenti amministrativi per retry controllato, diagnosi e chiusura pulita del match

## Test matrix di integrazione
Questi test devono fissare il contratto in modo stabile.

### 1. Action acknowledged solo dopo save riuscito
- dato un save riuscito su checkpoint
- quando il client invia una action valida
- allora il server risponde `success`
- e il restore successivo include quella action

### 2. Nessun ack se il save fallisce
- dato un failure injector nel repository
- quando la mutazione di dominio raggiunge il checkpoint
- allora il server non invia `success`
- e l'errore esposto al client e' `persistence_failed`

### 3. Match congelato dopo save failure
- dato un fallimento di persistenza su una action acknowledged candidate
- quando il client invia una seconda action sullo stesso match
- allora il server la rifiuta con `match_frozen`

### 4. Restore dal last durable checkpoint dopo crash
- dato un checkpoint `N` persistito
- e una action successiva applicata in memoria ma non acknowledged
- quando il server cade e il match viene restaurato
- allora il restore riparte esattamente da `N`

### 5. Nessuno stato "piu' nuovo" del last ack
- dato un flusso con save failure dopo mutazione
- quando si confronta lo stato restaurato con quello visto dal client
- allora il restore non contiene mai effetti che il client non abbia ricevuto come `success`

### 6. Nessuno stato "piu' vecchio" del last ack
- dato un flusso con piu' checkpoint acknowledged
- quando il server cade dopo avere mandato l'ultimo `success`
- allora il restore non perde quell'ultimo avanzamento

### 7. Freeze indipendente dal tipo di checkpoint
- ripetere il failure test su:
  - `GAME_INITIALIZED`
  - `TURN_STARTED`
  - `TURN_FINISHED`
  - `ERA_CHANGED`
  - `MATCH_COMPLETED`

### 8. Resume di un match congelato
- dato un match congelato per failure di persistenza
- quando il server viene riavviato e il match viene ripristinato
- allora lo stato corrisponde all'ultimo checkpoint durevole
- e il match non include l'action che non era stata acknowledged

## Conclusione
Se il progetto vuole davvero garantire coerenza forte tra action acknowledged e stato restaurabile,
la policy corretta non e' "continuo senza DB e ritento dopo".

La policy corretta e':
- save prima dell'ack
- freeze su failure
- restore dall'ultimo checkpoint durevole

Qualsiasi variante piu' permissiva puo' essere una scelta di prodotto legittima, ma deve essere
documentata come contratto piu' debole, non come implementazione equivalente.
