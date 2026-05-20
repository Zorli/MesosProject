# Mesos 
Progetto prova finale per l'esame di Ingegneria del Software.
Lo scopo del progetto è quello di implementare il gioco da tavolo mesos in java, in modo da poter
giocare con altri player, ognuno dal proprio device, collegandosi a un server.
È possibile giocare sia da interfaccia testuale che grafica. La versione qui implementata ha inoltre le seguenti
feature aggiuntive:
- supporto per multi-partita in parallelo sullo stesso server
- Resilienza alle disconnessioni da parte dei client

## Usage

### Configurazione server

`LaunchServer` legge la configurazione dal file locale `server.env`.
Il file usa il formato `KEY=value`:

```env
MESOS_TCP_PORT=12345
MESOS_RMI_PORT=1099
MESOS_SERVER_RMI_HOST=localhost
```

`MESOS_TCP_PORT` è la porta usata dal server TCP.
`MESOS_RMI_PORT` è la porta del registry RMI.
`MESOS_SERVER_RMI_HOST` è l'indirizzo che RMI inserisce negli stub esportati dal server.
Se server e client sono sulla stessa macchina può essere `localhost`.
Se il server deve essere raggiunto da un altro dispositivo nella stessa rete o hotspot, deve essere l'IP del server in quella rete.

Esempio su hotspot:

```env
MESOS_TCP_PORT=12345
MESOS_RMI_PORT=1099
MESOS_SERVER_RMI_HOST=172.20.10.2
```

`server.env` è l'unica fonte di verità per la configurazione di rete del server.
Le variabili d'ambiente del processo non sovrascrivono questi valori.

### Configurazione client

`LaunchClient` legge la configurazione dal file locale `client.env`:

```env
MESOS_SERVER_HOST=172.20.10.2
MESOS_TCP_PORT=12345
MESOS_RMI_PORT=1099
MESOS_CLIENT_RMI_HOST=172.20.10.3
```

`MESOS_SERVER_HOST` è l'IP della macchina su cui gira il server.
`MESOS_TCP_PORT` è la porta TCP del server.
`MESOS_RMI_PORT` è la porta del registry RMI del server.
`MESOS_CLIENT_RMI_HOST` è l'IP del client, usato solo quando si sceglie RMI perché il server deve richiamare il client tramite callback.

## Architettura del progetto
In questa sezione viene spiegata l'architettura ad alto livello del progetto, poi nelle relative 
sottosezioni verranno spiegati in dettaglio i particolari implementativi e i pattern applicati.

Il design pattern principale del progetto è quello del[Model View Controller distribuito](https://github.com/T-vaccari/javafx-distributed-mvc), ovvero una versione del
MVC in cui la view si trova su una altra macchina rispetto al controller e il model, come si può vedere nella seguente immagine:
![project-architecture.png](assets/project-architecture.png)

L'idea è quindi quella di applicare il pattern classico nascondendo però la presenza della rete
ai blocchi core del pattern.
Resources:
https://developer.mozilla.org/en-US/docs/Glossary/MVC
https://www.geeksforgeeks.org/system-design/proxy-design-pattern/
https://www.geeksforgeeks.org/system-design/mvc-design-pattern/


### Protocollo di comunicazione
Come da specifica di progetto, supportiamo connessioni sia con il protocollo TCP che con 
il protocollo RMI. Per riuscire a mantenere coerenza tra questi due protocolli è stato necessario fissare
in modo chiaro i contratti che devono essere offerti dalla rete per poter comunicare tra il client e il server.
In particolare sono stati fissati i seguenti contratti:
- Servizi che il client espone al server per ricevere messaggi server -> client: Interfaccia `VirtualServer`
- Servizi che il server espone al client per ricevere comandi client -> server: Interfaccia `VirtualView`
- Messaggi che vengono scambiati via rete tramite questi servizi di rete

I nomi delle interfacce possono essere fuorvianti poiché sullo stesso blocco logico
convivono MVC distribuito e Remote Proxy Pattern:
- Nel MVC distribuito la `VirtualView` è la rappresentazione lato server della view remota del client.
  In questo modo il server può aggiornare una view senza sapere se dietro ci sia TCP, RMI o una view locale.
- Sempre in questa ottica, il `VirtualServer` è il punto di ingresso lato client per i messaggi provenienti dal server.
  Non è il proxy usato dal client per chiamare il server, ma il contratto che permette al server di notificare il client.
- Il `ServerProxy`, invece, è il vero proxy remoto lato client: viene usato dalla UI per inviare comandi al server
  come se il server fosse un oggetto locale. Non ha un equivalente simmetrico lato server perché quel ruolo
  è già coperto dalla `VirtualView` e il server non necessita di chiamare servizi applicativi del client,
  oltre alle sole notifiche verso la view.

Il primo passo è stato quindi fissare i servizi che il server e il client devono offrire reciprocamente, 
in modo da poter poi implementare questi servizi sia in TCP che in RMI. Per fissarli abbiamo 
creato le relativi interfacce con i metodi, sorge pero un problema tecnico, ovvero che RMI
negli stessi metodi di TCP deve dichiarare che lanciano RemoteException, mentre in TCP non è necessario. 
Inoltre il funzionamento di RMI prescrive che le interfacce debanno essere shared tra client e server, 
pertanto fisicamente abbiamo le interfacce di rete duplicate a livello fisico, ma a livello logico 
rappresentano lo stesso contratto, il must è tenerle allineate, in particolare si ha che:
- Interfaccia `VirtualServer` e `RmiClientService` sono lo stesso contratto logico
- Interfaccia `VirtualView` e `RmiServerService` sono lo stesso contratto logico
Per motivi tecnici RMI ha inoltre anche un altra interfaccia, quella del RMIConnectionService, necessario
per poter richiedere lo stub.

#### Differenze tra TCP e RMI
Il protocollo TCP è un protocollo di comunicazione a basso livello, in cui i messaggi vengono scambiati come stringhe 
tramite json, è quindi necessario avere delle classi helper per serializzare e deserializzare i messaggi, inoltre è necessario
conoscere a priori il payload che si sta gestendo, nella sezione successiva verranno infatti spiegati i payload che rappresentano
il contratto del contenuto informativo condiviso fra i due protocolli scambiato via rete.

In particolare per TCP è necessario gestire i socket, canali di streaming di dati in formato di stringa, 
è quindi necessario avere un thread che esegua la continua lettura dei messaggi in arrivo sulla socket, essendo la read bloccante.
Come vedremo nelle sezioni successive istanziamo sia sul client che sul server appositi thread per gestire questo fatto.
La socket è full-duplex, quindi lettura e scrittura possono avvenire indipendentemente:
il thread dedicato serve per restare in ascolto dei messaggi in arrivo, mentre in uscita non serve un thread dedicato,
perchè la scrittura viene fatta direttamente quando c'è un messaggio da inviare.

Per serializzare e deserializzare i messaggi in TCP viene usata GSON, che inferisce dalla dichiarazione della classe, 
se abbastanza semplice, come serializzare e deserializzare, in questo modo è sufficiente dichiarare i payload come classi con i campi,
e GSON si occupa di tutto il resto, inoltre è possibile usare i payload anche in RMI, in questo modo si mantiene coerenza tra i due protocolli.

#### Come viene usata GSON
GSON è la libreria usata per trasformare oggetti Java in stringhe JSON e stringhe JSON in oggetti Java.
Nel protocollo TCP questo passaggio è necessario perché sulla socket viaggiano righe di testo, non oggetti Java.

Quando il client invia un comando, `ServerProxyTCP` crea un `ClientMessage`, composto da:
- `type`: indica il tipo di messaggio, ad esempio `AUTHENTICATE`
- `payload`: contiene i dati necessari per quel messaggio, ad esempio il nickname

Un messaggio client -> server serializzato in JSON ha quindi questa forma:
```json
{
  "type": "AUTHENTICATE",
  "payload": {
    "nickname": "ada"
  }
}
```
O più in generale direttamente dalla classe ServerMessage abbiamo:
```java
public class ServerMessage implements Serializable {
    public final ServerMessageType type;
    public final Object payload;

    public ServerMessage(ServerMessageType type, Object payload) {
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.payload = payload;
    }
}
```
Quindi ci aspettiamo di poter inferire il payload in arrivo direttamente leggendo il campo type e chiedendo a GSON di 
deserializzare quella data classe.

La serializzazione avviene con:
```java
GSON.toJson(message)
```

La deserializzazione avviene con:
```java
GSON.fromJson(json, ClasseDaCreare.class)
```

Nei punti in cui il codice deve prima capire il tipo del messaggio, il JSON viene letto come `JsonObject`.
Questo permette di leggere prima il campo `type` e poi scegliere la classe corretta del payload:
```java
JsonObject message = GSON.fromJson(line, JsonObject.class);
String type = message.get("type").getAsString();
JsonObject payload = message.getAsJsonObject("payload");
```

Questo è importante perché il campo `payload` nel wrapper `ClientMessage`/`ServerMessage` è generico.
GSON può serializzarlo facilmente, ma quando deve deserializzarlo deve sapere quale classe concreta usare.
Per questo il codice legge prima il `type` e poi associa quel tipo alla classe di payload corretta.




Per RMI invece la situazione è più semplice, poiche il remote method invocation si occupa di tutto il meccanismo di comunicazione, 
è sufficiente implementare le interfacce e registrare gli stub, inoltre RMI gestisce in automatico la serializzazione degli oggetti, 
quindi non è necessario gestire i dettagli di basso livello.
Non serve quindi un ciclo esplicito di lettura sulla socket, perchè la ricezione dei messaggi avviene tramite invocazione
dei metodi remoti esposti dagli stub.

#### Payload condivisi
Come già detto per comunicare via rete è stato necessario fissare i contratti dei messaggi scambiati, in modo tale 
da poter implementare sia TCP che RMI in modo coerente. In particolare si è cercato di mantenere i payload il più possibile 
semplici e snelli in modo da ridurre la quantità di dati scambiati e riuscire ad avere la gestione semplice fornita da GSON.

Per comodità abbiamo suddiviso in maniera gerarchica i payload rispetto alla fase di gioco a cui fanno riferimento,
ovvero:
- pre-lobby: payload usati per la fase di pre-lobby
- lobby: payload usati per la fase di lobby
- in-game: payload usati per la fase di gioco

Abbiamo inoltre la suddivisione rispetto al senso di comunicazione, ovvero:
- client -> server: payload usati per i messaggi inviati dal client al server(package `clienttoserver`)
- server -> client: payload usati per i messaggi inviati dal server al client(package `servertoclient`)

Ora vediamo in dettaglio i payload che vengono usati dal client per comunicare con il server.
Nel codice bisogna distinguere tra messaggio e payload: `ClientMessage` rappresenta il messaggio inviato
dal client al server ed è composto da un `ClientMessageType` e da un payload. I payload veri e propri
implementano invece l'interfaccia `ClientPayload`, che rappresenta il contratto di base per i dati inviati
dal client al server.

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
da un `ServerMessageType` e da un payload. I payload veri e propri implementano l'interfaccia `ServerPayload`,
che rappresenta il contratto di base per i dati inviati dal server al client.

`ServerPayload` viene poi specializzato dalle sotto interfacce relative alle varie fasi di comunicazione:
`PreLobbyServerPayload`, `LobbyServerPayload`, `GameServerPayload` ed `ErrorServerPayload`.

Fra i payload che implementano `PreLobbyServerPayload` abbiamo:
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

Il wrapper `ClientMessage`/`ServerMessage` serve soprattutto nel caso TCP, perché il messaggio arriva come stringa JSON e
GSON non può sapere da solo quale classe concreta usare per deserializzare il payload generico.
Per questo nel wrapper viene sempre inserito anche il `type`: prima si legge il type del messaggio,
poi in base a quel type si deserializza il payload nella relativa classe concreta.
In RMI invece la serializzazione degli oggetti viene gestita direttamente RMI.

### Architettura del Client
Ricordiamo che il client deve poter scegliere sia la tipologia di user interface (che può essere di tipo
testuale o grafica) sia la tipologia di protocollo di rete (che può essere TCP o RMI). 
Il punto di avvio del client è la classe `LaunchClient` che si occupa di leggere da riga di comando
prima la scelta della UI e poi la scelta del protocollo di rete, in base a queste scelte istanzia i blocchi necessari 
per poter avviare il client. In particolare a gestire la user interface è un oggetto controller.
Tale oggetto si occupa di gestire le richieste dell'utente e aggiornare la view. 
Il modo in cui vengono mostrate le informazioni dipende dalla 
UI scelta. Di seguito una immagine della architettura del client, che renderà più chiaro il discorso:
![client_architecture.png](assets/client-architecture.png)

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
- `TuiController` e `GUIController` implementano `ClientView`, quindi il `MessageRouter` può aggiornare la view senza sapere se l'utente sta usando TUI o GUI.

Quindi riassumendo il flusso dei messaggi è il seguente:
- In uscita: controller UI -> ServerProxy -> server
- In entrata: server -> VirtualServer -> MessageRouter -> ClientView

### Architettura del Server
#### Session Registry 
