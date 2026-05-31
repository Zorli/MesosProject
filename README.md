# Mesos 

Questo repository contiene l’implementazione Java del gioco da tavolo **Mesos**, sviluppata come prova finale del progetto di Ingegneria del Software 2025-2026 al Politecnico di Milano. 

Il progetto è stato svolto dal gruppo GC3, composto dai seguenti membri:
- Vaccari Tommaso
- Viggiano Luca 
- Zanatta Tommaso 
- Zorloni Fabio


L’applicazione è stata progettata come sistema distribuito client-server secondo il pattern Model-View-Controller. Il server mantiene il modello di gioco, gestisce la logica applicativa e coordina le partite; i client permettono ai giocatori di interagire con il gioco attraverso interfaccia testuale o interfaccia grafica JavaFX.

## Requisiti implementati

Rispetto alla tabella di valutazione fornita insieme alla specifica, il progetto implementa:

**Regole Complete + TUI + GUI + RMI + Socket + 2 Funzionalità Avanzate**

In particolare sono stati implementati:

- **Regole Complete**: il gioco supporta le regole complete di Mesos(non quelle avanzate)
- **TUI**: il client può essere avviato con interfaccia testuale.
- **GUI**: il client può essere avviato con interfaccia grafica JavaFX.
- **Socket TCP**: client e server possono comunicare tramite protocollo TCP.
- **Java RMI**: client e server possono comunicare tramite Java Remote Method Invocation.
- **Funzionalità Avanzata — Partite multiple**: il server può gestire più partite contemporaneamente.
- **Funzionalità Avanzata — Resilienza alle disconnessioni**: un client disconnesso può riconnettersi e continuare la partita. Se un player si disconnette il suo turno viene saltato. Se rimane un solo player connesso parte un timer, al termine del quale l'unico giocatore rimasto connesso viene decretato vincitore.


## Configurazione, Compilazione e Avvio

In questa sezione viene spiegato come configurare la rete, e poi come avviare server e client usando
i JAR già compilati presenti nella cartella `deliverables/jar`.

### 1. Recuperare gli indirizzi IP locali
Se server e client vengono eseguiti sulla stessa macchina, si può usare `localhost`.
Se invece si vuole giocare in rete locale, ad esempio con più computer collegati alla stessa rete Wi-Fi
o allo stesso hotspot, bisogna recuperare:
- l'indirizzo IP della macchina su cui viene avviato il server
- l'indirizzo IP della macchina su cui viene avviato il client, necessario solo se si sceglie RMI

Di seguito i comandi per recuperare l'indirizzo IP locale a seconda del sistema operativo:

* **macOS**:
  ```bash
  ipconfig getifaddr en0
  ```
  Se si è connessi con una interfaccia diversa dal Wi-Fi, verificare l'indirizzo con `ifconfig`.
* **Linux**:
  ```bash
  ip route get 1.1.1.1 | awk '{print $7}'
  ```
  In alternativa, eseguire `ip a` o `hostname -I`.
* **Windows**:
  ```cmd
  ipconfig
  ```
  Cercare la voce `Indirizzo IPv4`, ad esempio `192.168.1.56`.


### 2. Permessi firewall
Se server e client non sono sulla stessa macchina, il sistema operativo può bloccare le connessioni anche
quando IP e porte sono corretti.

Sulla macchina che esegue il server bisogna permettere a Java di ricevere connessioni in entrata e di usare
connessioni in uscita. A seconda di come viene avviato il progetto, il programma da autorizzare può comparire
come `java`, `javaw`, `JavaVM` oppure `IntelliJ IDEA`.

Questo vale su macOS, Windows e Linux:
- il server deve poter accettare connessioni TCP sulla porta `MESOS_TCP_PORT`;
- il server deve poter pubblicare e servire RMI sulla porta `MESOS_RMI_PORT`;
- con RMI, anche il client deve poter ricevere la callback del server, quindi può essere necessario autorizzare
  Java o IntelliJ anche sulla macchina client.

Se il firewall chiede se consentire l'accesso alla rete, bisogna permetterlo almeno per la rete locale usata
per giocare. In caso contrario il client può non riuscire a connettersi, oppure RMI può collegarsi ma non
riuscire a consegnare le risposte.

### 3. Avvio usando parametri da terminale
Il codice non legge argomenti posizionali passati al `main`, quindi la configurazione da terminale
deve essere passata come proprietà Java, cioè con `-D` prima di `-jar`.
Queste proprietà sovrascrivono i valori presenti nei file `.env`.

Il server avvia sempre sia TCP che RMI, mentre il client chiede da terminale quale interfaccia usare
e quale protocollo di rete usare.

#### Server
```bash
java \
  -DMESOS_TCP_PORT=12345 \
  -DMESOS_RMI_PORT=1099 \
  -DMESOS_SERVER_RMI_HOST=<IP_SERVER> \
  -jar deliverables/jar/mesos-server.jar
```

`MESOS_TCP_PORT` è la porta usata per TCP.
`MESOS_RMI_PORT` è la porta usata dal registry RMI.
`MESOS_SERVER_RMI_HOST` è l'indirizzo che RMI inserisce nello stub del server: se si gioca in locale
può essere `localhost`, se invece i client sono su altre macchine deve essere l'IP locale del server.

#### Client
Per il client bisogna usare il JAR adatto al sistema operativo:
- `mesos-client-mac-aarch64.jar` per macOS Apple Silicon
- `mesos-client-mac-x64.jar` per macOS Intel
- `mesos-client-linux-x64.jar` per Linux x64
- `mesos-client-windows-x64.jar` per Windows x64

Esempio:
```bash
java \
  -DMESOS_SERVER_HOST=<IP_SERVER> \
  -DMESOS_TCP_PORT=12345 \
  -DMESOS_RMI_PORT=1099 \
  -DMESOS_CLIENT_RMI_HOST=<IP_CLIENT> \
  -jar deliverables/jar/mesos-client-mac-aarch64.jar
```

`MESOS_SERVER_HOST` è l'indirizzo della macchina su cui gira il server.
`MESOS_CLIENT_RMI_HOST` serve solo per RMI, perchè in quel caso anche il server deve poter richiamare
il client tramite callback. Se si sceglie TCP, questo valore viene ignorato.

Ad esempio, se il server ha IP `192.168.1.56` e il client ha IP `192.168.1.74`, si può avviare
il server con:

```bash
java -DMESOS_TCP_PORT=12345 -DMESOS_RMI_PORT=1099 -DMESOS_SERVER_RMI_HOST=192.168.1.56 -jar deliverables/jar/mesos-server.jar
```

e il client con:

```bash
java -DMESOS_SERVER_HOST=192.168.1.56 -DMESOS_TCP_PORT=12345 -DMESOS_RMI_PORT=1099 -DMESOS_CLIENT_RMI_HOST=192.168.1.74 -jar deliverables/jar/mesos-client-mac-aarch64.jar
```



### 4. Configurazione tramite file `.env`
In alternativa ai parametri da terminale, il server legge `server.env` e il client legge `client.env`.
I file vengono cercati nella directory da cui viene lanciato il comando, quindi per usare quelli già presenti
in `deliverables/jar` bisogna prima spostarsi in quella cartella:

```bash
cd deliverables/jar
```

#### Server (`server.env`)
```env
MESOS_TCP_PORT=12345
MESOS_RMI_PORT=1099
MESOS_SERVER_RMI_HOST=<IP_SERVER>
```

#### Client (`client.env`)
```env
MESOS_SERVER_HOST=<IP_SERVER>
MESOS_TCP_PORT=12345
MESOS_RMI_PORT=1099
MESOS_CLIENT_RMI_HOST=<IP_CLIENT>
```

Poi si possono avviare i JAR senza passare altre proprietà:

```bash
java -jar mesos-server.jar
```

```bash
java -jar mesos-client-mac-aarch64.jar
```

### Compilazione e Creazione dei JAR
Il progetto è gestito tramite Maven, usato per compilare il codice, eseguire i test e gestire le dipendenze.

Per compilare il progetto ed eseguire i test:
```
./mvnw clean test
```

Per generare i FAT JAR finali:
```
./assets/scripts/create-jar.sh
```

Lo script crea i JAR nella cartella: deliverables/jar

---

## Architettura del progetto
In questa sezione viene spiegata l'architettura ad alto livello del progetto, poi nelle relative 
sottosezioni verranno spiegati in dettaglio i pattern applicati e i dettagli implementativi.

Il design pattern principale del progetto è quello del[Model View Controller distribuito](https://github.com/T-vaccari/javafx-distributed-mvc),
ovvero una versione del  MVC in cui la View si trova su una altra macchina rispetto al Controller 
e al Model.

Ricordiamo il pattern MVC classico è un Pattern architetturale che separa il modello dei dati 
di una applicazione(model) dalla
rappresentazione grafica (view) e dalla logica di controllo (controller). 
L'idea è quella di avere la View che si occupa della logica di presentazione delle informazioni
e di raccogliere input dall'utente, il controller invece si occupa 
di coordinare il Model,
per gestire le richieste dell'utente. Infine il Model si occupa di mantenere lo stato e la logica 
di dominio. 
In generale esistono diverse varianti del MVC non distribuito, che hanno alcune differenza su come 
la view interagisce con gli altri due componenti, in particolare qui è stata implementata 
la versione nella quale
la view viene notificata dal Model a seguito di un update, la view non conosce quindi il Model, 
e sotto un certo
punto di vista il Model non conosce la view, poichè viene applicato il pattern observer, ovvero il model
ha riferimenti alla view sotto forma di interfacce listener.
Di seguito uno schema illustrato del pattern:

![MVC.png](assets/images/MVC.png)

L'user interagisce con la View, immaginiamo che richieda un servizio, il flusso è il seguente:
1) La view contatta il controller a seguito dell'input utente
2) Il controller fa una prima validazione della richiesta, e sa, in base alla richiesta ricevuta, 
che cosa
richiedere al model. Qui si può fare una prima distinzione tra controller thin e fat, 
a seconda di quanta logica di dominio
viene spostata all'interno del controler. In generale in questa implementazione abbiamo sempre
optato per avere controller thin. 
Il controller dopo aver validato la richiesta, richiede il servizio effettivo al model, che di conseguenza
reagisce e aggiorna il suo stato interno.
3) Dopo che il modello ha modificato il proprio stato interno e ha applicato la logica di dominio, 
deve notificare le relative virtual view.  Possono essercene più di una, dato che più utenti 
possono utilizzare lo stesso modello. Nella nostra applicazione del pattern abbiamo scelto 
di non permettere al modello di conoscere direttamente le view, ma di avere invece iscritti dei listener,
che poi di fatto sono concretizzati dalle virtualview.

L'idea adesso è quella di prendere il pattern classico ed adattarlo ad un contesto nel quale la 
view si trova sulla macchina dell'utente, invece il model ed il controller si trovano sul server. 
E' quindi necessario aggiungere un layer di rete per il trasporto delle informazioni, nascondendo però
ai blocchi del pattern il fatto che di mezzo ci sia la rete. Ovvero per ottenere una architettura il quanto
più pulità possibile la rete deve essere nascosta, non deve essere parte integrante di questo modello.
Per nascondere la presenza della rete sono stati introdotti contratti comuni, che rappresentano 
localmente gli oggetti remoti con cui ciascun lato deve interagire.
Sul client, la user interface non comunica direttamente con la rete, ma usa un `ServerProxy`, 
che rappresenta il server remoto ed espone i servizi applicativi richiedibili dal client. Ovvero 
è stato applicato il remote proxy pattern.
Sempre sul client, il `VirtualServer` rappresenta il canale attraverso cui arrivano 
i messaggi dal server verso l'interfaccia utente locale. Espone infatti i servizi che il server può
richiedere sul client.
Sul server, la `VirtualView` rappresenta invece l'interfaccia utente remota del client: 
riceve i comandi provenienti dal
client e permette al server di notificare aggiornamenti verso quel client. Espone i servizi che 
il client può richiedere al server.
Non è stato introdotto un `ServerProxy` simmetrico lato server, perché il server non deve invocare 
una varietà di
servizi applicativi sul client. In quel verso della comunicazione è sufficiente la `VirtualView`,
che mantiene il
riferimento al client remoto e offre il punto unico attraverso cui inviargli notifiche.

Per chiarire meglio l'architettura può essere utile questo schema illustrato:

![project-architecture.png](assets/images/DistributedMVC.png)

## Protocollo di comunicazione
L'implementazione supporta due protocolli di comunicazione, TCP ed RMI.
Per mantenere coerente l'architettura e le funzionalità tra i due protocolli, sono stati definiti contratti comuni 
per la comunicazione tra client e server.

In particolare sono stati fissati i seguenti contratti:
- Servizi che il client espone al server per ricevere messaggi server -> client: interfaccia `VirtualServer`
- Servizi che il server espone al client per ricevere comandi client -> server: interfaccia `VirtualView`
- Messaggi che vengono scambiati via rete tramite questi servizi

Questi contratti derivano direttamente dalla scelta di adottare un MVC distribuito: poiché interfaccia utente, 
controller e model non si trovano nello stesso processo, alcune interazioni
devono necessariamente attraversare la rete.
Per evitare che il protocollo di rete entri nella logica applicativa, tali interazioni sono state espresse 
tramite interfacce comuni.
Ogni implementazione di rete, TCP o RMI, deve quindi offrire gli stessi servizi, 
così da mantenere invariato il comportamento funzionale del sistema indipendentemente dal protocollo scelto.


Gli stessi contratti hanno quindi un doppio ruolo: a livello architetturale realizzano il MVC distribuito, 
mentre a livello implementativo definiscono il comportamento che TCP e RMI
devono offrire in modo equivalente.

Il primo passo è stato quindi fissare i servizi che server e client devono offrire reciprocamente,
in modo da poterli implementare sia con TCP che con RMI.

A livello di codice questo introduce una differenza tecnica tra i due protocolli.
Nel caso TCP le interfacce possono essere normali interfacce Java, usate direttamente dalle classi concrete.
Nel caso RMI, invece, le interfacce remote devono estendere `Remote`, devono essere visibili sia al client 
che al server e i loro metodi devono dichiarare `RemoteException`.

Per questo motivo i contratti logici descritti sopra hanno una controparte specifica per RMI nel package shared:
- `VirtualServer` e `RmiClientService` rappresentano il contratto logico server -> client
- `VirtualView` e `RmiServerService` rappresentano il contratto logico client -> server

Queste interfacce non sono identiche a livello tecnico, ma devono rimanere allineate a livello funzionale: 
ogni servizio disponibile in TCP deve avere il corrispondente servizio anche in
RMI.

RMI richiede inoltre un ulteriore contratto, `RmiConnectionService`, usato dal client per aprire 
la connessione iniziale e ottenere lo stub della propria sessione server-side

### Messaggi e Payload usati dal protocollo
Come già detto per comunicare via rete è stato necessario fissare i contratti dei messaggi scambiati, in modo tale
da poter implementare sia TCP che RMI in modo coerente. In particolare si è cercato di mantenere 
i payload il più possibile
semplici e snelli in modo da ridurre la quantità di dati scambiati e riuscire ad avere 
una gestione semplice sia nella serializzazione JSON con GSON, sia nel passaggio degli oggetti tramite RMI.

Per comodità abbiamo suddiviso in maniera gerarchica i payload rispetto alla fase di gioco a cui fanno riferimento,
ovvero:
- pre-lobby: payload usati per la fase di pre-lobby
- lobby: payload usati per la fase di lobby
- in-game: payload usati per la fase di gioco

Abbiamo inoltre la suddivisione rispetto al senso di comunicazione, ovvero:
- client -> server: payload usati per i messaggi inviati dal client al server(package `clienttoserver`)
- server -> client: payload usati per i messaggi inviati dal server al client(package `servertoclient`)

Nel codice bisogna distinguere tra messaggio e payload.
Il payload contiene i dati applicativi veri e propri: ad esempio il nickname scelto dal client,
l'id della lobby, la carta selezionata o lo stato aggiornato della partita.
Il messaggio invece aggiunge l'informazione necessaria per interpretare quel payload.

Nel caso TCP questa distinzione è esplicita, perché sulla socket arriva solo una stringa JSON:
per questo il payload viene inserito dentro un messaggio wrapper formato da:
- `type`: indica il tipo del messaggio
- `payload`: contiene i dati associati a quel tipo

Nel codice questo wrapper esiste in due varianti, una per ciascuna direzione:
- `ClientMessage`, con un `ClientMessageType` e un payload che implementa `ClientPayload`,
  per i messaggi client -> server
- `ServerMessage`, con un `ServerMessageType` e un payload che implementa `ServerPayload`,
  per i messaggi server -> client

Gli enum `ClientMessageType` e `ServerMessageType` indicano il tipo del messaggio applicativo
e permettono al codice di capire quale payload aspettarsi e quindi quale classe concreta usare
in fase di deserializzazione.

Ad esempio, un comando client -> server ha questa forma logica:
```json
{
  "type": "AUTHENTICATE",
  "payload": {
    "nickname": "tom"
  }
}
```

Il campo `type` è necessario perché il campo `payload` è tipizzato sull'interfaccia della direzione
(`ClientPayload` o `ServerPayload`): prima si legge il tipo del messaggio, poi si sceglie la classe
concreta con cui deserializzare il payload.
Nel caso RMI il problema della deserializzazione JSON non esiste, perché gli oggetti vengono passati
direttamente tramite invocazione remota.
Tuttavia vengono riutilizzati gli stessi payload, così TCP e RMI mantengono lo stesso contratto dati
a livello applicativo.

Ora vediamo in dettaglio i payload che vengono usati dal client per comunicare con il server.
Il `ClientMessage` rappresenta il messaggio inviato dal client al server ed è composto da
un `ClientMessageType` e da un payload.
I payload veri e propri implementano invece l'interfaccia `ClientPayload`, che rappresenta il contratto
di base per i dati inviati dal client al server.

`ClientPayload` viene poi specializzato dalle sotto interfacce relative alle varie fasi di comunicazione:
`PreLobbyClientPayload`, `LobbyClientPayload`, `GameClientPayload` e `ConnectionClientPayload`.

Fra i payload che implementano `PreLobbyClientPayload` abbiamo:
- `AuthenticationPayload`, che contiene il nickname scelto dal client
- `CreateLobbyPayload`, che contiene la dimensione scelta per la lobby
- `JoinLobbyPayload`, che contiene l'id della lobby a cui unirsi
- `ListLobbiesPayload`, che non contiene campi e serve solo a richiedere la lista delle lobby

Fra i payload che implementano `LobbyClientPayload` abbiamo:
- `StartLobbyPayload`, usato per richiedere l'avvio della lobby
- `ExitLobbyPayload`, usato per uscire dalla lobby

Fra i payload che implementano `GameClientPayload` abbiamo:
- `MoveTotemPayload`, che contiene la tile su cui spostare il totem
- `ChooseCardPayload`, che contiene l'id della carta scelta
- `ExtraDrawPayload`, che contiene l'id della carta scelta per la pescata extra

Infine, fra i payload di connessione abbiamo `PongPayload`, che implementa `ConnectionClientPayload`
e viene usato per rispondere al ping del server(spiegato in una sezione successiva)

Per quanto riguarda invece i payload che vengono inviati dal server al client, anche qui bisogna distinguere
tra messaggio e payload: `ServerMessage` rappresenta il messaggio inviato dal server al client ed è composto
da un `ServerMessageType` e da un payload.
I payload veri e propri implementano l'interfaccia `ServerPayload`, che rappresenta il contratto di base
per i dati inviati dal server al client.

`ServerPayload` viene poi specializzato dalle sotto interfacce relative alle varie fasi di comunicazione:
`PreLobbyServerPayload`, `LobbyServerPayload`, `GameServerPayload` ed `ErrorServerPayload`.

Fra i payload che implementano `PreLobbyServerPayload` abbiamo:
- `AuthenticationResultPayload`, che contiene il risultato della fase di autenticazione
- `LobbyListPayload`, che contiene la lista delle lobby disponibili tramite oggetti `LobbySummaryPayload`

Fra i payload che implementano `LobbyServerPayload` abbiamo:
- `LobbyUpdatePayload`, che rappresenta un aggiornamento della fase di lobby, ad esempio autenticazione,
  creazione lobby, join, uscita o avvio della partita

Fra i payload che implementano `GameServerPayload` abbiamo:
- `GamePayload`, che rappresenta lo stato della partita inviato al client, contenente stato del gioco,
  era corrente, ordine dei turni, stato del turno, giocatori e board

Fra i payload di errore abbiamo:
- `ErrorPayload`, che contiene codice e messaggio dell'errore

Alcuni payload, come `LobbySummaryPayload`, `BoardPayload`, `PlayerPayload`, `CardPayload`,
`OfferSpacePayload`, `StartingBlockPayload`, `TribePayload`, `TurnOrderPayload` e `TurnStatePayload`,
non sono messaggi principali inviati direttamente,
ma payload di supporto contenuti dentro payload più grandi.

In sintesi, i payload definiscono il contratto dati comune.
TCP li trasporta dentro messaggi JSON con `type` e `payload`, mentre RMI li passa direttamente come oggetti
nelle invocazioni remote.

### Protocollo TCP
TCP è un protocollo di trasporto a basso livello che permette a due processi di comunicare tramite socket.
Una socket rappresenta un endpoint di una comunicazione bidirezionale: ogni endpoint è identificato 
da un indirizzo IP e da una porta.
Una connessione TCP è quindi identificata dalla coppia di endpoint coinvolti, cioè endpoint client ed endpoint server.

Nel modello client-server, il server apre una `ServerSocket` su una porta e rimane in attesa di connessioni 
tramite `accept()`.
Quando un client si collega creando una `Socket` verso quell'indirizzo e quella porta, il server riceve una 
nuova socket dedicata a quella connessione.
Da quel momento client e server comunicano usando gli stream associati alla socket: un input stream 
per leggere e un output stream per scrivere.

Dal punto di vista delle socket i dati sono uno stream continuo di byte: è quindi responsabilità dell'applicazione 
stabilire come separare un messaggio dal successivo.
Nel nostro caso è stato definito un semplice protocollo applicativo sopra TCP, ogni messaggio viene 
serializzato in JSON e scritto sulla socket come una singola riga di testo.
Dall'altro lato il messaggio viene letto con `readLine()`, quindi una riga corrisponde 
a un messaggio applicativo completo.

La socket è full-duplex: la stessa connessione permette lettura e scrittura in entrambi i versi.
Nel codice, però, la lettura deve restare sempre attiva ed è bloccante; per questo viene eseguita 
in un thread dedicato.

```java
while ((message = reader.readLine()) != null) {
    handleMessage(message);
}
```

La chiamata a `readLine()` rimane in attesa finché non arriva una riga completa.
Se la connessione viene chiusa dall'altro lato, `readLine()` restituisce `null` e il ciclo termina.
Propriò perchè ci sono metodi bloccanti, nasce la necessità di usare thread per gestire il ciclo
di raccolta dei messaggi in entrata.

La scrittura, invece, avviene solo quando c'è effettivamente un messaggio da inviare:

```java
writer.println(serializedMessage);
```

Non serve quindi un thread dedicato alla scrittura, il codice scrive sulla socket nel momento in cui deve 
inviare un comando o una notifica.

A livello operativo, sul server TCP viene creato un `ConnectionAcceptor`, che apre la `ServerSocket`
e viene avviato su un thread dedicato:

```java
ConnectionAcceptor acceptor = new ConnectionAcceptor(tcpPort, preLobbyController, router);
acceptor.start();
Thread acceptorThread = new Thread(acceptor, "mesos-connection-acceptor");
acceptorThread.start();
```

Il compito del `ConnectionAcceptor` è accettare nuove connessioni e creare una `VirtualViewTCP`
per ogni client collegato:

```java
Socket clientSocket = this.serverSocket.accept();
VirtualViewTCP virtualView = new VirtualViewTCP(clientSocket, this.preLobbyController, this.router);
Thread virtualViewThread = new Thread(virtualView, "mesos-virtual-view-" + clientSocket.getPort());
virtualViewThread.start();
```

Sul client, `ServerProxyTCP` apre la socket verso il server, prepara il writer per i messaggi in uscita
e avvia un `VirtualServerTCP` per gestire i messaggi in ingresso:

```java
this.socket = new Socket(serverHost, port);
this.writer = new PrintWriter(this.socket.getOutputStream(), true);
this.virtualServer = new VirtualServerTCP(this.socket, clientView, this);
this.virtualServerThread = new Thread(this.virtualServer, "mesos-client-server-message-receiver");
this.virtualServerThread.start();
```

Dentro `VirtualServerTCP`, invece, viene preparato il reader sulla stessa socket e parte il ciclo di lettura
dei messaggi provenienti dal server:

```java
BufferedReader reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

while (this.running && (serializedMessage = reader.readLine()) != null) {
    this.handleMessage(serializedMessage);
}
```
Questo è il punto concreto in cui il thread del `VirtualServerTCP` resta in ascolto dei messaggi server -> client.


![flusso TCP](assets/images/FlussoTCP.png)



Quindi il flusso concreto TCP è:
- il server crea un `ConnectionAcceptor`
- il `ConnectionAcceptor` apre la `ServerSocket`
- quando arriva un client, viene creata una `VirtualViewTCP`
- ogni `VirtualViewTCP` gira su un thread dedicato alla comunicazione con quel client
- il client usa `ServerProxyTCP` per inviare messaggi al server
- il client usa `VirtualServerTCP` per restare in ascolto dei messaggi provenienti dal server

#### GSON
GSON è la libreria usata per trasformare oggetti Java in stringhe JSON e stringhe JSON in oggetti Java.
Nel protocollo TCP questo passaggio è necessario perché sulla socket viaggiano righe di testo sotto 
forma di byte, non oggetti Java.

Quando il client invia un comando, `ServerProxyTCP` crea un `ClientMessage`, composto da:
- `type`: indica il tipo di messaggio, ad esempio `AUTHENTICATE`
- `payload`: contiene i dati necessari per quel messaggio, ad esempio il nickname

Un messaggio client -> server serializzato in JSON ha quindi questa forma:
```json
{
  "type": "AUTHENTICATE",
  "payload": {
    "nickname": "tom"
  }
}
```
Il wrapper client -> server è quindi:
```java
public class ClientMessage implements Serializable {
    public final ClientMessageType type;
    public final ClientPayload payload;

    public ClientMessage(ClientMessageType type, ClientPayload payload) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.payload = payload;
    }
}
```

Il wrapper server -> client è equivalente, ma usa i tipi della direzione opposta:
```java
public class ServerMessage implements Serializable {
    public final ServerMessageType type;
    public final ServerPayload payload;

    public ServerMessage(ServerMessageType type, ServerPayload payload) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.payload = payload;
    }
}
```
Il campo payload è quindi vincolato all'interfaccia corretta della direzione del messaggio.
Quando arriva un messaggio, però, bisogna comunque capire quale payload concreto contiene.
Per farlo il codice legge prima il campo `type` e poi chiede a GSON di deserializzare il `payload`
usando la classe corrispondente.

La serializzazione avviene con:

```java
ClientMessage message = new ClientMessage(ClientMessageType.AUTHENTICATE, new AuthenticationPayload("tom"));
String serializedMessage = GSON.toJson(message);
```

In generale invece la deserializzazione avviene con:
```java
GSON.fromJson(json, ClasseDaCreare.class)
```
Il motivo per cui il wrapper deve contenere il type, cosi come già detto, è dovuto al fatto che quando in
TCP viene ricevuta la stringa in JSON non si sa a priori che classe concreta fra le varie esistenti di tipo payload
andare a ricreare, pertanto è qui che entra in gioco il type. Il type ci fornisce il tipo di payload, cosi possiamo
chidedere a GSON di deserializzare quella stringa GSON e ricostruire proprio una determinata classe.
Ad esempio si puo vedere questo processo nel CommandHandler sul server.
Qui il messaggio viene prima letto come `JsonObject`, così il codice può leggere il `type` prima di
deserializzare il `payload`:
```java
JsonObject root = GSON.fromJson(rawMessage, JsonObject.class);
ClientMessageType type = ClientMessageType.valueOf(root.get("type").getAsString());
JsonElement payloadElement = root.get("payload");

ClientPayload payload = switch (type) {
    case AUTHENTICATE -> GSON.fromJson(payloadElement, AuthenticationPayload.class);
    case CREATE_LOBBY -> GSON.fromJson(payloadElement, CreateLobbyPayload.class);
    case MOVE_TOTEM -> GSON.fromJson(payloadElement, MoveTotemPayload.class);
    // altri type mappati sulle rispettive classi di payload
};
```

Il punto è che il campo `payload` di `ClientMessage` non è dichiarato come `AuthenticationPayload`
o `MoveTotemPayload`, ma come `ClientPayload`.
Questa informazione è sufficiente per il contratto Java, ma non basta a GSON per ricostruire l'oggetto
concreto quando riceve una stringa JSON.
Per questo il `type` fa da discriminante: prima viene letto il tipo del messaggio, poi in base a quel tipo
viene scelta la classe concreta con cui deserializzare il payload.

### Protocollo RMI
RMI (Remote Method Invocation) è un'astrazione fornita da Java per semplificare la programmazione distribuita.
L'idea di fondo è permettere a una JVM di invocare metodi su oggetti che si trovano in un'altra JVM,
con una sintassi uguale a quella di una normale chiamata a metodo.
RMI quindi nasconde al codice applicativo la gestione diretta delle socket e della serializzazione:
il programmatore non deve scrivere manualmente byte su uno stream, come avviene in TCP,
ma invoca un metodo su un oggetto che rappresenta l'oggetto remoto.
Nel caso classico client-server, l'oggetto remoto si trova sul server e il client lo usa tramite uno stub.
Lo stub è un proxy locale: implementa la stessa interfaccia remota dell'oggetto server e inoltra la chiamata
attraverso la rete.
Lato server, RMI riceve la chiamata, la deserializza e la fa arrivare all'oggetto remoto concreto.
Quindi la rete non sparisce davvero: viene solo nascosta dietro il meccanismo degli stub e delle interfacce remote.
Per questo i metodi remoti devono dichiarare `RemoteException`, perché una chiamata che sembra locale
può comunque fallire per motivi di rete (è anche il motivo per il quale abbiamo dovuto duplicare le interfacce di rete 
come già spiegato sopra).

![RMI.png](assets/images/HighLevelRMI.png)

A livello operativo, il primo oggetto remoto pubblicato dal server è `RmiConnectionEntryPoint`.
Questo oggetto implementa `RmiConnectionService` e viene registrato nel registry RMI con il nome 
`MesosServer`.

```java
RmiConnectionService rmiEntryPoint = new RmiConnectionEntryPoint(controller, router);
LocateRegistry.createRegistry(port);
Registry registry = LocateRegistry.getRegistry(port);
registry.rebind("MesosServer", rmiEntryPoint);
```

Il registry permette al client di ottenere lo stub dell'entry point remoto usando il nome `MesosServer`.

Sul client, infatti, il primo passo è il lookup:

```java
String registryUrl = "rmi://" + serverHost + ":" + rmiPort + "/MesosServer";
RmiConnectionService connectionService =
        (RmiConnectionService) Naming.lookup(registryUrl);
```

A questo punto il client possiede uno stub di tipo `RmiConnectionService`.
Questo stub espone il metodo `connect(...)`, che serve ad aprire una connessione logica con il server.

Prima di chiamare `connect(...)`, però, il client crea il proprio callback RMI, cioè `VirtualServerRMI`.
Questo oggetto rappresenta il punto di ingresso lato client per i messaggi server -> client.

```java
this.callbackReceiver = new VirtualServerRMI(clientView, exportCallback);
this.serverStub = connectionService.connect(this.callbackReceiver);
this.callbackReceiver.attachServerProxy(this);
```

La chiamata a `connect(...)` arriva sul server dentro `RmiConnectionEntryPoint`.
Il server riceve il callback del client e crea una `VirtualViewRMI` dedicata a quella connessione:

```java
public RmiServerService connect(RmiClientService clientCallback) throws RemoteException {
    return new VirtualViewRMI(this.preLobbyController, clientCallback, this.router);
}
```

La `VirtualViewRMI` è quindi la sessione remota del singolo client lato server.
Quando viene creata, registra una nuova connessione nel `ServerController` e conserva il callback del client:

```java
this.clientCallback = clientCallback;
this.sessionId = this.preLobbyController.registerConnection(this);
```

Il valore ritornato da `connect(...)` è uno stub di tipo `RmiServerService`.
Da quel momento il client non usa più il registry per inviare comandi:
usa direttamente lo stub della propria sessione remota.

```java
this.serverStub.handleClientMessage(message);
```

Sul server, la chiamata arriva in `VirtualViewRMI.handleClientMessage(...)`.
Qui il messaggio viene ricevuto come oggetto Java, non come stringa JSON, e viene inoltrato al router applicativo:

```java
ClientPayload payload = clientMessage.payload;
this.router.routeMessage(this.sessionId, payload);
```

Il flusso inverso funziona usando il callback passato dal client.
Quando il server deve notificare un aggiornamento, `VirtualViewRMI` invoca un metodo sul callback remoto
`RmiClientService`:

```java
this.clientCallback.handleMessage(
        new ServerMessage(ServerMessageType.AUTHENTICATION_RESULT, authenticationResultPayload)
);
```

Sul client questa chiamata arriva in `VirtualServerRMI.handleMessage(...)`, che inoltra il messaggio
al `MessageRouter` della user interface:

```java
this.messageRouter.routeMessage(message);
```
![FlussoRMI.png](assets/images/FlussoRMI.png)
Quindi il flusso concreto è:
- il server registra `RmiConnectionEntryPoint` nel registry come `MesosServer`
- il client fa lookup e ottiene lo stub `RmiConnectionService`
- il client esporta `VirtualServerRMI` come callback
- il client chiama `connect(callback)`
- il server crea una `VirtualViewRMI` dedicata
- il client riceve lo stub `RmiServerService` della propria sessione
- client -> server: `ServerProxyRMI` chiama `VirtualViewRMI`
- server -> client: `VirtualViewRMI` chiama `VirtualServerRMI`


## Architettura del Client
Ricordiamo che il client deve poter scegliere sia la tipologia di user interface (che può essere di tipo
testuale o grafica) sia la tipologia di protocollo di rete (che può essere TCP o RMI). 
Il punto di avvio del client è la classe `LaunchClient` che si occupa di leggere da riga di comando
prima la scelta della UI e poi la scelta del protocollo di rete, in base a queste scelte istanzia i blocchi necessari 
per poter avviare il client. In particolare a gestire la user interface è un oggetto controller.
Tale oggetto si occupa di gestire le richieste dell'utente e aggiornare la view. 
Il modo in cui vengono mostrate le informazioni dipende dalla 
UI scelta. Di seguito una immagine della architettura del client, che renderà più chiaro il discorso:
![client_architecture.png](assets/images/client-architecture.png)

I blocchi principali dell'architettura client sono:
- `ClientView`: è l'interfaccia che rappresenta i metodi per modificare la view del client
- `ServerProxy`: è l'interfaccia che rappresenta il proxy per comunicare con il server
- `VirtualServer`: è l'interfaccia che rappresenta il punto di ingresso per i messaggi provenienti dal server
- `MessageRouter`: è la classe che si occupa di instradare i messaggi ricevuti dal server alla view, in base al type del messaggio
Chiaramente non sono tutti gli oggetti che vengono creati, ma sono i principali oggetti per capire come
è stato strutturato il client.

Queste interfacce e classi esistono principalmente per rendere facilmente interscambiabili a livello di logica
il servizio di rete e il servizio di UI. In particolare andando nel dettaglio, il significato delle interfacce è il seguente:
- Lato User Interface indipendentemente che sia testuale o grafica, deve sapere come poter 
comunicare con il server su richiesta dell'utente, indipendentemente che poi il messaggio viaggi 
via TCP o RMI. Per questo è stato creato il contratto `ServerProxy`, che espone i metodi che il client può usare per mandare messaggi
via rete al server.
- Lato rete il server deve sapere come recapitare i messaggi al client, indipendentemente che ci sia TCP o RMI come protocollo,
esiste quindi il contratto `VirtualServer`, che espone i metodi che il server può usare per notificare il client, indipendentemente da come questi messaggi arrivano.
- Lato `VirtualServer`, una volta ricevuto il messaggio bisogna sapere quale metodo chiamare sulla view per poterlo
mostrare all'utente. Poiché ogni messaggio necessita di essere mostrato e gestito in una certa maniera, la logica di scelta del metodo è stata
centralizzata in `MessageRouter`, che in base al tipo di payload sceglie quale metodo del contratto `ClientView` chiamare.
Usato sia da TCP che da RMI.
- Lato `messageRouter` deve poter sapere come chiamare il servizio di update della view per mostrare un dato pacchetto,
indipendentemente dal tipo concreto di UI che si trova dall'altra parte. Per questo motivo sono stati fissati i servizi per poter mostrare
al client un dato messaggio nell'interfaccia `ClientView`. 

Le implementazioni concrete seguono direttamente queste astrazioni:
- `ServerProxyTCP` e `ServerProxyRMI` implementano `ServerProxy`, quindi espongono alla UI gli stessi metodi per comunicare con il server, cambiando solo il protocollo usato.
- `VirtualServerTCP` e `VirtualServerRMI` rappresentano i due modi in cui il client riceve messaggi dal server. Entrambi ricevono un `ServerMessage` e lo passano al `MessageRouter`.
- `TuiView` e `GUIView` implementano `ClientView`, quindi il `MessageRouter` può aggiornare la view senza sapere se l'utente sta usando TUI o GUI.

Quindi riassumendo il flusso dei messaggi è il seguente:
- In uscita: controller UI -> ServerProxy -> server
- In entrata: server -> VirtualServer -> MessageRouter -> ClientView

## Architettura del Server
In questa sezione vengono spiegate le scelte che sono state fatte per l'architettura del server.
Il server è stato progettato sin dal principio per poter gestire più partite in contemporanea. Questo ha portato ad estendere la logica del modello MVC in maniera funzionale sui tre blocchi logici che caratterizzano le fasi di vita del gioco. Le fasi di vita caratterizzanti il gioco sono le seguenti:
- fase di pre lobby: User non è autenticato, oppure è autenticato ma non si trova in nessuna lobby
- fase di lobby: User è autenticato e si trova in una lobby 
- fase di Game. User è in partita

Quindi l'idea è stata quella di avere rispettivamente un controller ed un model che gestiscano le rispettive fasi logiche, ovvero si occupano di validare e gestire la logica del loro specifico dominio. 

Come già spiegato, per poter nascondere ai vari model e controller il fatto che la view non si trova sul server,
è stata introdotta l'interfaccia `VirtualView` che dovrebbe rappresentare uno stub della view sul server.
Questo stub nasconde la rete, quindi si ha che per i messaggi in entrata questa classe si occupa di gestire la raccolta e la ricostruzione del messaggio, senza conoscere a chi è destinato tale messaggio, che vedremo essere 
gestito da un router, che sa a chi inoltrare il messaggio. 
Invece per i messaggi in uscita la view viene contattata dai model tramite interfaccia `Listener` per poter 
contattare il reale client, senza pero far conoscere ai model la rete.

Questa impostazione ci ha permesso quindi di ottenere una separazione della gestione di logica di dominio, relativa alle tre fasi, e una agnosticità dei controller e dei model al fatto che per contattare il client si attraversi una connessione internet(viene quindi anche nascosto il protocollo concreto di rete utilizzato).

//TODO : Qui necessito di avere una vista chaira in exclaidrwa del server ad alto livello in cui si vedono le due virtualview che ricevono i messaggi, che implmentano lo stesso contratto logico e che loro si occupano solo di gestire la rete e i messaggi in entrata ed in uscita. A gestire lo smazzamento dei messaggi relativamente alla fase di gioco tramite message router, e far vedere che i controller ricevono messaggio mandano al model e model notifica rispsota tramite interfaccia listener.

La classe che quindi si occupa di instradare il messaggio al controller corretto è il message router.
Tutti gli errori riguardanti il protocollo di comunicazione vengono gestiti da una classe in comune alla 
VirtualViewal TCP ed RMI in modo da avere una uniformità di gestione degli errori. 

Il router inoltra il messaggio di un dato User al relativo controller e model della fase in cui si trova.
Ovvero per ogni User è sempre noto a runtime in quale delle tre fasi si trova, 
e grazie a questa informazione si può fare riferimento ai relativi model e controller 
per la validazione del messaggio arrivato.

### Thread in gioco
In questa sezione viene fatto un recap dei thread in gioco sul server. Questo renderà 
più chiaro il perchè di alcune decisioni tecniche prese. 
All'avvio del server viene creato il thread su cui gira il `ConnectionAcceptor`,
la classe che si occupa di accettare le richieste di connessione in ingresso sulla `ServerSocket`.
Quando un client richiede una connessione sulla socket, viene creata una `VirtualViewTCP` che gira su un thread. 
Questo è dovuto al fatto che la lettura continua della socket per TCP è bloccante,
e necessito quindi di un thread. Nel caso RMI invece quello che succede all'avvio del server,
è che viene caricato nel RMI registry la classe `RmiConnectionEntryPoint`, che funge da stub inizialmente, 
per connettersi al server. Una volta che il client RMI ha richiesto la connessione tramite lo stub pubblicato
nel RMI registry, viene creata una VirtualVIewRMI che non gira invece su un thread assestante ma è gestita 
dall'infrastruttura di RMI. 

Ogni controller del server gira su un thread assestante. Nel caso pre-lobby tale controller è singleton.
Nel caso delle lobby si ha un controller ed un model per ogni lobby creata, lo stesso per i game.
Il motivo di questa decisione verrà spiegato in un altra sezione. Il succo del discorso è che ci sono 
molti thread, i quali potrebbero necessitare di leggere e scrivere informazioni caratteristiche dell'utente. 

### Gestione delle informazioni
A runtime come detto è necessario mantenere, leggere e scrivere informazioni per ogni User connesso al server. 
In particolare ogni connessione verso il server viene identificata in maniera univoca da un session-id. 
Tutte le informazione di un dato User che è connesso possono essere recuperate a partire dal session-id.
Dal momento che abbiamo la necessità di avere queste informazioni centralizzate in un unico punto che 
garantisca safety dal punto di vista della concorrenza, è stato creata la classe `SessionRegistry`
che rappresenta quindi l'unico punto del server nella quale devono essere salvati i dati a runtime degli utenti.
Questa classe è caratterizzata da mappe interne per la retention dei dati, e metodi thread-safe creati 
appositamente per fare query sicure, evitando race-condition.

### Gestione delle disconnessioni
In questa sezione viene spiegato ad alto livello quali sono le idee e i meccanismi dietro al funzionamento 
della funzionalità avanzata resilienza alle disconnessioni. 
Prima è però necessario discutere delle componenti tecniche che consentono a questa funzionalità di esistere.

#### Ping Pong Routine
Come detto abbiamo già la classe che ci consente di centralizzare le informazioni. Una di queste informazioni
fondamentali che salviamo centralizzata è l'ultima risposta di heartbeat da ogni client.
L'idea è quella di avere un protocollo di verifica che ci consenta di effettuare un assesment periodico 
delle connessioni che sono effettivamente in vita. Questo ci consente di chiudere eventuali connessioni rimaste 
aperte, ma che effettivamente sono appese, ovvero il client è crashato e non risponde più.
Il tutto si basa su due messaggi scambiati tra client e server, ovvero messaggio di Ping da server verso client
e messaggio di Pong in risposta dal client verso il server.
Periodicamente con una routine apposita, vengono inviate a tutte le connessioni che risultano attualmente aperte
un messaggio di PING. Nel frattempo si verifica anche se per una data connessione è scaduto il tempo di attesa
massimo dall'ultimo PONG ricevuto. Se cosi fosse si procede a chiudere tale connessione. Il client quando riceve
un PING risponde con un PONG. Tale PONG viene gestito da una classe che si occupa del lifecycle delle connessioni
ovvero `ServerConnectionLifeCycle`. Tale protocollo di heartbeat si appoggia sull'infrastruttura di comunicazione
già esistente tra client e server, è inoltre agnostico al tipo di protocollo a basso livello di comuncazione scelto, grazie
al fatto che usiamo implementazioni concrete di VirtualView ServerProxy e VirtualServer, che ci nascondono 
tutto il layer di rete. 

#### Gestione disconnesione in pre Lobby

#### Gestione disconessione in Lobby 


#### Gestione disconnesione Game 


### Layer di rete



### Pre Lobby

### Lobby 


### Game




