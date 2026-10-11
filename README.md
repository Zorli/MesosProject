# Mesos

This repository contains the Java implementation of the board game **Mesos**, developed as the final project of the Software Engineering course 2025-2026 at Politecnico di Milano.

**Final grade: 30 cum laude** 

The project was carried out by group GC3, made up of the following members:
- Zorloni Fabio
- Vaccari Tommaso
- Viggiano Luca
- Zanatta Tommaso


The application was designed as a distributed client-server system following the Model-View-Controller pattern.
The server holds the game model, handles the application logic and coordinates the matches;
the clients let players interact with the game through either a text-based interface
or a graphical interface.

## Implemented Requirements

With respect to the grading table provided with the specification, the project implements:

**Complete Rules + TUI + GUI + RMI + Socket + 3 Advanced Features**

In particular, the following were implemented:

- **Complete Rules**: the game supports the complete Mesos rules (not the advanced ones).
- **TUI**: the client can be launched with a text-based interface.
- **GUI**: the client can be launched with a JavaFX graphical interface.
- **TCP Socket**: client and server can communicate over TCP.
- **Java RMI**: client and server can communicate through Java Remote Method Invocation.
- **Advanced Feature — Multiple matches**: the server can handle several matches at the same time.
- **Advanced Feature — Resilience to disconnections**: a disconnected client can reconnect and
continue the match. If a player disconnects, their turn is skipped. If only one player remains connected,
a timer starts, and when it expires the only player still connected is declared the winner.
- **Advanced Feature — Database with player ranking**


## Configuration, Build and Launch

This section explains how to configure the network, and then how to launch the server and clients using
the pre-built JARs in the `deliverables/jar` folder.

### 1. Finding local IP addresses and ports
If server and client run on the same machine, you can use `localhost` for the IP configuration.
If instead you want to play over a local network, for example with several computers connected to the same Wi-Fi network
or to the same hotspot, you need to find:
- the IP address of the machine running the server
- the IP address of the machine running the client
- the server's TCP port
- the server's RMI port

Below are the commands to find the IP address assigned to the machine on the local network,
depending on the operating system:

* **macOS**:
  ```bash
  ipconfig getifaddr en0
  ```
  If you are connected through an interface other than Wi-Fi, check the address with `ifconfig`.
* **Linux**:
  ```bash
  ip route get 1.1.1.1 | awk '{print $7}'
  ```
  Alternatively, run `ip a` or `hostname -I`.
* **Windows**:
  ```cmd
  ipconfig
  ```
  Look for the `IPv4 Address` entry, for example `192.168.1.56`.

As for the ports, you need to know in advance
how the server was configured.

### 2. Firewall permissions
If server and client are not on the same machine, the operating system may block connections even
when IPs and ports are correct.

On the machine running the server, Java must be allowed to accept incoming connections and to use
outgoing connections. Depending on how the project is launched, the program to authorize may appear
as `java`, `JavaVM` or `IntelliJ IDEA`.

This applies to macOS, Windows and Linux:
- the server must be able to accept TCP connections on the TCP port chosen at runtime;
- the server must be able to publish and serve RMI on the RMI port chosen at runtime;
- with RMI, the client must also be able to receive the server's callback, so it may be necessary to authorize
  Java or IntelliJ on the client machine as well.

If the firewall asks whether to allow network access, allow it at least for the local network used
to play.

### 3. Interactive launch from the terminal
The network configuration is requested entirely at runtime through interactive terminal prompts.

To launch, simply run the JAR and answer the questions. The server always starts both TCP and RMI;
the client also asks which interface (GUI/TUI) and which network protocol (TCP/RMI) to use.

#### Server
```bash
java -jar deliverables/jar/mesos-server.jar
```

For example, once the server is started, the interaction will be:

```text
Enter MESOS_TCP_PORT [default 12345]: 12345
Enter MESOS_RMI_PORT [default 1099]: 1099
Enter MESOS_SERVER_IP_ADDRESS [default localhost]: 192.168.1.56
```

- `MESOS_TCP_PORT` is the port used for TCP.
- `MESOS_RMI_PORT` is the port used by the RMI registry.
- `MESOS_SERVER_IP_ADDRESS` is the IP address that RMI puts into the server stub.

Ports must be integers between `1024` and `65535`. If you press Enter without typing
anything, the server uses `12345` for TCP, `1099` for RMI and `localhost` as the server IP. If the clients
are on other machines, `MESOS_SERVER_IP_ADDRESS` must be set to the server's local IP.

#### Client
For the client, use the JAR that matches your operating system:
- `mesos-client-mac-aarch64.jar` for macOS Apple Silicon
- `mesos-client-mac-x64.jar` for macOS Intel
- `mesos-client-linux-x64.jar` for Linux x64
- `mesos-client-windows-x64.jar` for Windows x64

```bash
java -jar deliverables/jar/mesos-client-mac-aarch64.jar
```

For example, once the client is started, the interaction will be:

```text
Enter MESOS_SERVER_IP_ADDRESS [default localhost]: 192.168.1.56
Enter MESOS_TCP_PORT [default 12345]: 12345
Enter MESOS_RMI_PORT [default 1099]: 1099
Enter MESOS_CLIENT_IP_ADDRESS [default localhost]: 192.168.1.74
Select UI mode:
1) GUI
2) TUI
Select network technology:
1) TCP
2) RMI
```

- `MESOS_SERVER_IP_ADDRESS` is the IP address of the machine running the server. It is used for both TCP
  and RMI.
- `MESOS_TCP_PORT` is the TCP port exposed by the server.
- `MESOS_RMI_PORT` is the RMI registry port exposed by the server.
- `MESOS_CLIENT_IP_ADDRESS` is the IP address that RMI puts into the client's callback stub.

Ports must be integers between `1024` and `65535`. If you press Enter without typing
anything, the client uses `localhost` as the server IP, `12345` for TCP, `1099` for RMI and `localhost`
as the client IP. If client and server are on different machines, `MESOS_SERVER_IP_ADDRESS` must be
set to the server's local IP and `MESOS_CLIENT_IP_ADDRESS` to the client's local IP.

### Starting the Database (Docker)
For the advanced feature that saves the match ranking to a database (MySQL), the container must be started before the server.

The project root contains a ready-to-use `docker-compose.yml` file. To start it, run:
```bash
docker compose up -d
```

This starts the MySQL database on port `3306` with the default credentials, which match the default values offered when the server is launched interactively.

To stop the database:
```bash
docker compose down
```

### Building and Creating the JARs
The project is managed with Maven, which is used to compile the code, run the tests and manage dependencies.

To compile the project and run the tests:
```
./mvnw clean test
```

To generate the final FAT JARs, on **macOS / Linux**:
```bash
./assets/scripts/create-jar.sh
```

on **Windows**:
```bat
assets\scripts\create-jar.bat
```

Both scripts run the same Maven builds and only create the JARs in the `deliverables/jar` folder.
Javadoc generation is separate from the JARs.

To regenerate the HTML Javadoc, on **macOS / Linux**:
```bash
./assets/scripts/create-javadoc.sh
```

on **Windows**:
```bat
assets\scripts\create-javadoc.bat
```

The Javadoc is generated in the `deliverables/javadoc/apidocs` folder.

---

## Project Architecture
This section explains the high-level architecture of the project; the following
subsections then explain in detail the patterns applied and the implementation details.

The main design pattern of the project is the [distributed Model View Controller](https://github.com/T-vaccari/javafx-distributed-mvc),
a version of MVC in which the View lives on a different machine from the Controller
and the Model.

Recall that the classic MVC pattern is an architectural pattern that separates an application's data model
(model) from its graphical representation (view) and from its control logic (controller).
The idea is that the View handles the presentation logic and collects
user input, while the controller
coordinates the Model to handle user requests.
Finally, the Model holds the state and the domain
logic.
In general there are several variants of non-distributed MVC, which differ in how
the view interacts with the other two components. In particular, here we implemented
the version in which
the view is notified by the Model after an update; the view therefore does not know the Model,
and in a certain sense the Model does not know the view either, because the observer pattern is applied: the model
holds references to the view in the form of listener interfaces.
Below is an illustrated diagram of the pattern:

![MVC.png](assets/images/MVC.png)

The user interacts with the View; suppose they request a service. The flow is as follows:
1. The view contacts the controller following the user input.
2. The controller performs a first validation of the request and, based on the request received, knows what
to ask the model. Here we can make a first distinction between thin and fat controllers,
depending on how much domain logic
is moved into the controller. In this implementation we always
chose thin controllers.
After validating the request, the controller asks the model for the actual service, and the model
reacts and updates its internal state.
3. After the model has changed its internal state and applied the domain logic,
it must notify the related virtual views. There can be more than one, since several users
can use the same model. In our application of the pattern we chose
not to let the model know the views directly, but to have listeners subscribed instead,
which are in fact implemented by the virtual views.

The idea now is to take the classic pattern and adapt it to a context in which the
view lives on the user's machine, while the model and controller live on the server.
A network layer must therefore be added to carry information, while hiding
the presence of the network from the blocks of the pattern. In other words, to obtain an architecture that is as
clean as possible, the network must be hidden and must not be an integral part of this model.
To hide the presence of the network, common contracts were introduced that locally represent
the remote objects each side has to interact with.
On the client, the user interface does not communicate directly with the network, but uses a `ServerProxy`,
which represents the remote server and exposes the application services the client can request.
In this sense the remote proxy pattern was applied.
Also on the client, the `VirtualServer` represents the channel through which
messages from the server reach the local user interface. It exposes the services the server can
request on the client.
On the server, the `VirtualView` instead represents the client's remote user interface:
it receives the commands coming from the client and lets the server push updates to that client.
It exposes the services that
the client can request from the server.
No symmetric `ServerProxy` was introduced on the server side, because the server does not need to invoke
a variety of application services on the client. In that direction of communication the `VirtualView` is enough:
it holds the reference to the remote client and offers the single point through which notifications are sent to it.

To clarify the architecture further, this illustrated diagram may help:

![project-architecture.png](assets/images/DistributedMVC.png)

## Communication Protocol

The project adopts a distributed MVC architecture: view, controller and model live in different processes, so some interactions cross the network.
For this separation not to compromise the cleanliness of the architecture, the network must be transparent to the application logic: neither the view nor the model should know whether they are communicating via TCP or RMI.

To achieve this, the system introduces two contracts that locally simulate the presence of the remote counterpart — each one pretends to be the object its own side would like to have nearby:

- **`VirtualView`** (lives on the server): to the model, it is the client's view.
  The model registers it as a listener through the Observer pattern and calls `notifyUpdate()` on it exactly as it would with a local view; it is the concrete `VirtualView` that takes care of serializing the update and carrying it across the network to the real client.

- **`VirtualServer`** (lives on the client): to the UI, it is the receiving end of the server.
  It receives the messages arriving from the network and delivers them to the local view, which knows nothing about the underlying transport.

These contracts play a double role: at the architectural level they are the communication points between client and server; at the implementation level they let TCP and RMI offer the same services without the application logic needing to know which protocol is in use.

However, TCP and RMI have different technical constraints that prevent using the same interfaces in both cases.
With TCP, the interfaces are plain Java interfaces.
With RMI, instead, remote interfaces must extend `Remote` and their methods must declare `RemoteException`, because RMI cannot guarantee that a remote call succeeds the way a local call would.

This difference introduces two levels of contract in the code:
- **Application level** — `VirtualView` and `VirtualServer` describe the services independently of the protocol used
- **RMI level** — `RmiServerService` and `RmiClientService` expose equivalent services with the technical constraints required by RMI

The functional alignment between the two levels is therefore:
- `VirtualView` ↔ `RmiServerService`: client → server commands
- `VirtualServer` ↔ `RmiClientService`: server → client notifications

RMI requires an additional contract, `RmiConnectionService`, which has no counterpart at the application level.
The client uses it to open the initial connection, obtain the stub of its own server-side session and pass the server its own callback for notifications.
It is the entry point of all RMI communication, before individual per-client sessions exist.

### Messages and Payloads used by the protocol

In the code we need to distinguish between message and payload.
The payload contains the actual application data: the nickname chosen by the client, the lobby id, the selected card, the updated state of the match.
The message is the envelope that adds the information needed to interpret that payload.

The concrete shape of these types is mainly dictated by the TCP problem.
Only a JSON string arrives on the socket: the receiver does not know in advance which concrete payload class to rebuild, because at the contract level the payload is seen only through the `ClientPayload` or `ServerPayload` interfaces.
To solve this, every message includes a `type` field that acts as a discriminator: the receiver first reads the type, then asks GSON to deserialize the payload into the matching concrete class.

The wrapper therefore comes in two variants, one for each direction:
- `ClientMessage`, with a `ClientMessageType` and a payload implementing `ClientPayload`, for client → server messages
- `ServerMessage`, with a `ServerMessageType` and a payload implementing `ServerPayload`, for server → client messages

RMI does not have this problem: objects are passed directly through remote invocation, without explicit JSON serialization.
However, it reuses the same payloads, so TCP and RMI share the same data contract at the application level and the domain logic does not depend on the chosen protocol.

Payloads are organized hierarchically by game phase:
- pre-lobby: payloads used in the authentication and lobby discovery phase
- lobby: payloads used in the lobby creation and management phase
- in-game: payloads used during the match

And by direction of communication:
- client → server: package `clienttoserver`, root `ClientPayload`
- server → client: package `servertoclient`, root `ServerPayload`

![Client Payloads](assets/images/clientPayload.png)

![Server Payloads](assets/images/serverPayload.png)

Some payloads, such as `LobbySummaryPayload`, `BoardPayload`, `PlayerPayload`, `CardPayload`, `OfferSpacePayload`, `StartingBlockPayload`, `TribePayload`, `TurnOrderPayload` and `TurnStatePayload`, are not sent directly as top-level messages but are supporting payloads nested inside larger payloads, used to structure complex data such as the board state or the current turn.

### TCP Protocol

TCP is a transport protocol based on a bidirectional communication channel: the socket, uniquely identified by the quadruple `(source_IP, source_port, destination_IP, destination_port)`.
Once the connection is established, the two endpoints can write and read data on the same channel in both directions.

From the data point of view, however, the socket exposes a continuous stream of bytes: there is no natural boundary between one message and the next.
It is therefore the application's responsibility to define a framing protocol.
In this project the choice is simple: each application message is serialized to JSON and written to the socket as a single line of text.
On the other side the message is read with `readLine()`, so one line corresponds to one complete application message.

Reading with `readLine()` is blocking: the thread stays suspended until a complete line arrives or the connection closes.
For this reason reading cannot happen on the main thread: both server and client dedicate a separate thread to receiving messages, so the rest of the application can keep working while the reader thread waits.

```java
while ((message = reader.readLine()) != null) {
    handleMessage(message);
}
```

Writing, instead, only happens when there is a command or notification to send, so it does not need a dedicated thread:

```java
writer.println(serializedMessage);
```

#### Server-side flow

On the server a `ConnectionAcceptor` is created, which opens the `ServerSocket` on the configured port and runs on a dedicated thread waiting for new connections:

```java
ConnectionAcceptor acceptor = new ConnectionAcceptor(tcpPort, serverConnectionLifecycle, router);
Thread acceptorThread = new Thread(acceptor, "mesos-connection-acceptor");
acceptorThread.start();
```

For each client that connects, the `ConnectionAcceptor` creates a dedicated `VirtualViewTCP` and starts it on its own thread:

```java
Socket clientSocket = this.serverSocket.accept();
VirtualViewTCP virtualView = new VirtualViewTCP(clientSocket, this.serverConnectionLifecycle, this.router);
Thread virtualViewThread = new Thread(virtualView, "mesos-virtual-view-" + clientSocket.getPort());
virtualViewThread.start();
```

Each `VirtualViewTCP` is therefore the session of a single client: it runs on its own thread, reads incoming messages from that client and can send notifications to it.

#### Client-side flow

On the client, `ServerProxyTCP` opens the socket to the server and prepares the writer for outgoing messages.
At the same time it starts a `VirtualServerTCP` on a dedicated thread, which keeps listening for incoming messages from the server:

```java
this.socket = new Socket(serverHost, port);
this.writer = new PrintWriter(this.socket.getOutputStream(), true);
this.virtualServer = new VirtualServerTCP(this.socket, clientView, this);
this.virtualServerThread = new Thread(this.virtualServer, "mesos-client-server-message-receiver");
this.virtualServerThread.start();
```

Inside `VirtualServerTCP` the reader is prepared and the read loop starts:

```java
BufferedReader reader = new BufferedReader(new InputStreamReader(this.socket.getInputStream()));

while (this.running && (serializedMessage = reader.readLine()) != null) {
    this.handleMessage(serializedMessage);
}
```

![TCP flow](assets/images/FlussoTCP.png)

In summary, the concrete TCP flow is:
- the server starts the `ConnectionAcceptor` on a dedicated thread
- for each client that connects, a `VirtualViewTCP` is created on its own thread
- the client opens the socket and starts `VirtualServerTCP` on a thread dedicated to receiving
- client → server: `ServerProxyTCP` serializes and writes to the writer
- server → client: `VirtualViewTCP` serializes and writes, `VirtualServerTCP` reads and delivers to the view

#### GSON

GSON is the library used to turn Java objects into JSON strings and vice versa.
Serializing an outgoing message is straightforward:

```java
ClientMessage message = new ClientMessage(ClientMessageType.AUTHENTICATE, new AuthenticationPayload("tom"));
String serializedMessage = GSON.toJson(message);
```

Deserializing incoming messages is more involved because the `payload` field is declared as an interface (`ClientPayload` or `ServerPayload`) and GSON does not know which concrete class to instantiate.
The `type` therefore acts as a discriminator: the message is first read as a `JsonObject` to extract the type, then the payload is deserialized into the matching concrete class.
This process can be seen in the server-side `ClientCommandHandler`:

```java
JsonObject root = GSON.fromJson(rawMessage, JsonObject.class);
ClientMessageType type = ClientMessageType.valueOf(root.get("type").getAsString());
JsonElement payloadElement = root.get("payload");

ClientPayload payload = switch (type) {
    case AUTHENTICATE -> GSON.fromJson(payloadElement, AuthenticationPayload.class);
    case CREATE_LOBBY  -> GSON.fromJson(payloadElement, CreateLobbyPayload.class);
    case MOVE_TOTEM   -> GSON.fromJson(payloadElement, MoveTotemPayload.class);
    // other types mapped to their respective payload classes
};
```

### RMI Protocol

RMI is a Java abstraction for distributed communication: it lets one JVM invoke methods on objects living in another JVM, with the same syntax as a local call.
Unlike TCP, the application code does not open sockets, does not serialize JSON lines and does not maintain `readLine()` loops: these aspects are still present at the transport level but are hidden by the mechanism of stubs and remote interfaces.

The architectural difference compared to TCP mainly concerns the server → client direction.
With TCP the server can write to the client at any time because the bidirectional stream is always open.
With RMI there is no stream: everything is a method invocation on remote objects.
To notify the client, the server needs something it can call: this is why the client must export a remote object — the callback — and pass it to the server during the connection.

![RMI.png](assets/images/HighLevelRMI.png)

#### Connection

The server publishes in the RMI registry an object of type `RmiConnectionEntryPoint`, which implements `RmiConnectionService` and is registered under the name `MesosServer`.
The registry acts as a name service: it lets the client discover the server's entry point without having to know the stub directly.

```java
RmiConnectionService rmiEntryPoint = new RmiConnectionEntryPoint(controller, router);
LocateRegistry.createRegistry(port);
Registry registry = LocateRegistry.getRegistry(port);
registry.rebind("MesosServer", rmiEntryPoint);
```

The client obtains the entry point stub through a lookup:

```java
String registryUrl = "rmi://" + serverHost + ":" + rmiPort + "/MesosServer";
RmiConnectionService connectionService =
        (RmiConnectionService) Naming.lookup(registryUrl);
```

Before calling `connect(...)`, the client exports its own RMI callback, `VirtualServerRMI`: this is the remote object the server will call to deliver notifications to the client.

```java
this.callbackReceiver = new VirtualServerRMI(clientView, exportCallback);
this.serverStub = connectionService.connect(this.callbackReceiver);
this.callbackReceiver.attachServerProxy(this);
```

The call to `connect(...)` reaches the server in `RmiConnectionEntryPoint`, which receives the callback and creates a `VirtualViewRMI` dedicated to that session:

```java
public RmiServerService connect(RmiClientService clientCallback) throws RemoteException {
    return new VirtualViewRMI(this.serverConnectionLifecycle, clientCallback, this.router);
}
```

`VirtualViewRMI` is the server-side remote session of the single client: it registers the connection and keeps the received callback.

```java
this.clientCallback = clientCallback;
this.sessionId = this.serverConnectionLifecycle.registerConnection(this);
```

The value returned by `connect(...)` is a stub of type `RmiServerService`, which the client will use from then on to send commands — without going through the registry again.

#### Message exchange

Client → server: `ServerProxyRMI` invokes a method on the `RmiServerService` stub, and the call arrives in `VirtualViewRMI.handleClientMessage(...)`.
The message is received as a Java object and forwarded directly to the application router:

```java
this.serverStub.handleClientMessage(message);
```

```java
ClientPayload payload = clientMessage.payload;
this.router.routeMessage(this.sessionId, payload);
```

Server → client: the model notifies `VirtualViewRMI` through the Observer pattern.
`VirtualViewRMI` invokes the method on the client's remote callback:

```java
this.clientCallback.handleMessage(
        new ServerMessage(ServerMessageType.AUTHENTICATION_RESULT, authenticationResultPayload)
);
```

The call arrives in `VirtualServerRMI.handleMessage(...)`, which forwards it to the UI's `MessageRouter`:

```java
this.messageRouter.routeMessage(message);
```

![RMI flow](assets/images/FlussoRMI.png)

In summary, the concrete RMI flow is:
- the server registers `RmiConnectionEntryPoint` in the registry as `MesosServer`
- the client performs a lookup and obtains the `RmiConnectionService` stub
- the client exports `VirtualServerRMI` as a callback and calls `connect(callback)`
- the server creates a dedicated `VirtualViewRMI` and returns the `RmiServerService` stub
- client → server: `ServerProxyRMI` invokes `VirtualViewRMI` through the stub
- server → client: `VirtualViewRMI` invokes `VirtualServerRMI` through the callback


## Client Architecture
Recall that the client must be able to choose both the type of user interface (which can be
text-based or graphical) and the type of network protocol (which can be TCP or RMI).
The client's entry point is the `LaunchClient` class, which reads from the command line
first the UI choice and then the network protocol choice, and based on these choices instantiates the blocks needed
to start the client. In particular, the user interface is managed by a controller object.
This object handles the user's requests and updates the view.
How the information is displayed depends on the chosen UI.
Below is a diagram of the client architecture, which will make this clearer:
![client_architecture.png](assets/images/client-architecture.png)

The main blocks of the client architecture are:
- `ClientView`: the interface representing the methods for updating the client's view
- `ServerProxy`: the interface representing the proxy used to communicate with the server
- `VirtualServer`: the interface representing the entry point for messages coming from the server
- `MessageRouter`: the class that routes messages received from the server to the view,
  based on the message type

Clearly these are not all the objects that get created, but they are the main ones needed to understand how
the client is structured.

These interfaces and classes exist mainly to make the network service and the UI service easily
interchangeable at the logic level. In more detail, the meaning of the interfaces
is the following:
- On the user interface side, whether text-based or graphical, the UI must know how to
communicate with the server when the user asks, regardless of whether the message then travels
via TCP or RMI. This is why the `ServerProxy` contract was created: it exposes the methods the client can use
to send messages over the network to the server.
- On the network side, the server must know how to deliver messages to the client, regardless of whether TCP or RMI
is the protocol.
There is therefore the `VirtualServer` contract, which exposes the methods the server can use to notify the client,
regardless of how these messages arrive.
- On the `VirtualServer` side, once a message is received, it must know which method to call on the view in order to
show it to the user. Since every message needs to be displayed and handled in a specific way, the logic
for choosing the method was
centralized in `MessageRouter`, which, based on the payload type, chooses which method of the `ClientView` contract to call.
It is used by both TCP and RMI.
- On the `MessageRouter` side, it must know how to call the view's update service to display a given packet,
regardless of the concrete type of UI on the other side. For this reason the services
for showing a given message to the client were fixed in the `ClientView` interface.

The concrete implementations follow directly from these abstractions:
- `ServerProxyTCP` and `ServerProxyRMI` implement `ServerProxy`, so they expose the same methods to the UI for
communicating with the server, changing only the protocol used.
- `VirtualServerTCP` and `VirtualServerRMI` represent the two ways in which the client receives messages from the server.
Both receive a `ServerMessage` and pass it to the `MessageRouter`.
- `TuiView` and `GUIView` implement `ClientView`, so the `MessageRouter` can update the view without knowing
whether the user is using the TUI or the GUI.

To sum up, the message flow is the following:
- Outgoing: UI controller -> ServerProxy -> server
- Incoming: server -> VirtualServer -> MessageRouter -> ClientView

## Server Architecture
This section explains the main choices made for the server architecture.

The server was designed from the start to handle several matches at the same time.
For this reason the application logic was not concentrated in a single controller/model,
but was split according to the main phases a user can be in:
- pre-lobby: the user is not authenticated, or is authenticated but is not in any lobby
- lobby: the user is authenticated and is in a lobby
- game: the user is inside a match

The idea is therefore to have controllers and models dedicated to the different logical phases.
Each controller receives the commands of the users who are in the phase it controls, and takes care of validating
and modifying the state of the related model.

As already explained, however, the server does not communicate directly with a local user interface.
The user interface lives on the client, so the `VirtualView` interface was introduced on the server.
The `VirtualView` represents, on the server side, the client's remote user interface and hides from the rest of the server
the concrete protocol used to communicate with that client.

The concrete implementations are:
- `VirtualViewTCP`, used when the client communicates via TCP socket
- `VirtualViewRMI`, used when the client communicates via RMI

Both implement the same logical contract.
This lets the rest of the server always work against the same concept of remote view,
without knowing whether TCP or RMI is behind that connection.

On the incoming side, the `VirtualView` receives a message from the client, rebuilds the application payload
and forwards it to the `MessageRouter`.
The `VirtualView` knows which connection the message comes from through the `sessionId`, but it does not decide
which controller should handle that command.

That responsibility belongs to the `MessageRouter`.
The router receives:
- the `sessionId` of the connection
- the payload sent by the client

The router chooses the controller to forward the message to based on the user's runtime
state: given the `sessionId`, the `MessageRouter`
queries the `ServerSessionRegistry`, retrieves the user's current phase and forwards the command
to the controller associated with that phase.

So:
- if the user is in pre-lobby, the command is forwarded to the `PreLobbyController`
- if the user is in a lobby, the command is forwarded to the controller of the corresponding lobby
- if the user is in game, the command is forwarded to the controller of the corresponding match

The payload is interpreted only afterwards, by the controller that received the command.
This is important because the network layer and the router contain no application logic:
they simply deliver the command to the right point of the server based on the session state.

On the outgoing side, instead, the models do not know the `VirtualView`s directly.
When they need to notify the client of an update, they only see the `Listener` contract.

The `VirtualView`s implement `Listener`, so they can be registered as recipients of the updates
produced by the models.
From the model's point of view, however, there is no `VirtualViewTCP` or `VirtualViewRMI`:
there is only an object that implements `Listener` and therefore knows how to receive a notification.

This keeps the model independent of both the network and the concrete type of remote view.
The model produces the update and invokes the `Listener` contract; it is then the concrete `VirtualView`
that turns that update into a message to send to the client.

In this way the server keeps three responsibilities separate:
- the network is handled by the concrete implementations of `VirtualView`
- message dispatching is handled by the `MessageRouter`
- domain logic is handled by the controllers and models of the various phases

This separation is important because it lets the server stay independent of the network protocol.
Once a message has reached the server as an application payload, the rest of the logic
no longer needs to know whether that message arrived via TCP or via RMI.

![AllServer.png](assets/images/AllServer.png)

### Threads involved
This section recaps the threads involved on the server. This will make
the reasons behind some technical decisions clearer.
When the server starts, the thread running the `ConnectionAcceptor` is created:
the class that accepts incoming connection requests on the `ServerSocket`.
When a client requests a connection on the socket, a `VirtualViewTCP` is created and runs on its own thread.
This is because continuously reading from the socket with TCP is blocking,
so a dedicated thread is needed.
With RMI, instead, when the server starts a remote object `RmiConnectionEntryPoint` is created
and published in the registry under the name `MesosServer`.
The client performs the lookup on the registry and obtains a stub to this entry point.
Once the RMI client has requested the connection through `connect(...)`, a
`VirtualViewRMI` dedicated to that connection is created; it does not run on a thread created manually by our code,
but is managed by the RMI infrastructure.

Each server controller runs on its own thread. In the pre-lobby case, this controller is a singleton.
For lobbies there is one controller and one model for each lobby created, and the same goes for games.
The reason for this decision is explained in another section. The gist is that there are
many threads, which may need to read and write user-specific information.

### Information management
As mentioned, at runtime it is necessary to keep, read and write information for every user connected to the server.
In particular, every connection to the server is uniquely identified by a session-id.
All the information about a given connected user can be retrieved starting from the session-id.
Since we need this information to be centralized in a single point that
guarantees safety from a concurrency standpoint, the `ServerSessionRegistry` class was created,
which is therefore the only point of the server where users' runtime data must be stored.
This class uses internal maps to retain the data, and thread-safe methods created
specifically to run safe queries and avoid race conditions.

### Disconnection handling
This section explains at a high level the ideas and mechanisms behind the
advanced feature of resilience to disconnections.
First, though, we need to discuss the technical components that make this feature possible.

#### Ping Pong Routine
As mentioned, we already have the class that lets us centralize information. One of the key pieces
of information we store centrally is the last heartbeat response from each client.
The idea is to have a verification protocol that lets us periodically assess
which connections are actually alive. This lets us close any connections that remain
open but are actually hanging, i.e. the client has crashed and no longer responds.
Everything is based on two messages exchanged between client and server: a Ping message from server to client
and a Pong message in response from client to server.
Periodically, with a dedicated routine, a PING message is sent to all connections that are currently
open. Meanwhile, we also check whether, for a given connection, the maximum waiting time since the last PONG
received has expired. If so, that connection is closed. When the client receives
a PING it replies with a PONG. That PONG is handled by a class responsible for the connection lifecycle,
namely `ServerConnectionLifecycle`. This heartbeat protocol relies on the communication infrastructure
already existing between client and server, and it is also agnostic to the low-level communication protocol chosen,
thanks to the fact that we use concrete implementations of `VirtualView`, `ServerProxy` and `VirtualServer`, which hide
the whole network layer from us.
The `PingPongRoutine` uses the `ClientConnection` interface to send the PING to the client.
This way the routine does not know the concrete implementations of the virtual views, but only sees
`ClientConnection`s.
A session's actual timeout is detected by the `PingPongRoutine`, which during its periodic check
verifies whether a connection's last PONG is too old.
At that point it calls on the `ServerConnectionLifecycle` class, which as a first step cleanly closes
that connection.
Then `ServerConnectionLifecycle` updates the internal states consistently with the phase
the player was in, acting on both the `ServerSessionRegistry` and the controllers and models specific
to that phase.
For lobby and game, the session is not immediately removed from the `ServerSessionRegistry`.
The internal command `SystemDisconnectCommand` carries only the `sessionId`; the phase controller then uses
that session-id to retrieve from the registry the information still needed for the cleanup.
Only after updating the model and the phase state is the session unregistered.

#### Disconnection handling in Pre Lobby
If a user disconnects while in the pre-lobby phase, `ServerConnectionLifecycle` does not need to delegate
the cleanup to a specific controller.
In this phase the user does not yet belong to a lobby or a game, so there is no phase state
to update inside a dedicated model.
For this reason the lifecycle directly calls `ServerSessionRegistry.unregister(sessionId)`.
If the session was authenticated, the registry also removes the association between nickname and session-id, freeing
that nickname for a future authentication.

#### Disconnection handling in Lobby
If a user disconnects while in a lobby, `ServerConnectionLifecycle` does not modify the lobby state
directly, but retrieves the `LobbyController` associated with the lobby the session is in
and sends it an internal `SystemDisconnectCommand`.

The `LobbyController` processes the command in its own queue and calls `disconnectUser(...)`.
At this point it retrieves the session's nickname and listener from the `ServerSessionRegistry`, removes the player
from the `Lobby` model through `removePlayer(...)` and notifies the other clients with `notifyPlayerLeft(...)`.
The lobby-specific logic therefore stays in the model: if the disconnected player was the host, `Lobby`
assigns the host role to the first remaining player; if instead the lobby becomes empty, its state becomes `CLOSED`.
In that case the controller removes both the lobby's controller and model from the `ServerSessionRegistry`
and finally unregisters the disconnected session.

#### Disconnection handling in Game
If a user disconnects while in game, `ServerConnectionLifecycle` retrieves the `GameController`
associated with the session's game and sends it an internal `SystemDisconnectCommand`.
Here too the command carries only the session-id, because the session stays registered
until the controller has finished handling the disconnection.

The `GameController` processes the command in its own queue and resolves from the `ServerSessionRegistry` both the session's
listener and the associated `Player`.
As a first step it removes the listener from the `Game` model, so that client no longer receives updates.
It then delegates to the model through `game.disconnectedPlayer(player)`.

The domain logic stays in the `Game` model: the player is marked as not connected in the
`connectedPlayers` map, and then the behavior changes based on the current round phase.

During the `PLACING_TOTEMS` phase, `TotemPlacementHandler` is used.
If the player had already placed their totem, their position on the board is not changed.
If instead they were the current player and should therefore have placed their totem at that moment, their turn is
skipped and the cursor moves to the next connected player.
If the player had not yet reached their turn, nothing is done right away: the player stays on the
starting tile and will be skipped when the cursor reaches their turn, if they are still disconnected.

The case between totem placement and card drawing is handled when the `PLAYER_DRAWS` phase starts.
At that moment the resolution order of the offer path is built.
If a player placed their totem, disconnected, but reconnected before their draft turn,
they can draw normally.
If instead they are still disconnected when their draft turn comes, their turn is closed without drawing:
they are moved to the starting tile and their draw allowances are reset to zero.

During the `PLAYER_DRAWS` phase, `CardDraftHandler` is used.
If the current player disconnects, `skipDisconnectedPlayer(...)` immediately closes their turn,
moves them back to the starting tile and resets their remaining choices.
Then `advanceToNextDrawablePlayerOrEndStandardDraft()` looks for the next connected player who can actually draw,
or closes the standard draft phase if there are no others.
If instead a player who is not the current one disconnects, the board is not changed right away:
it will be handled when the cursor reaches that player.

During the `EXTRA_DRAWS` phase, if the player entitled to the extra draw disconnects,
the optional draw is skipped and the round moves on to the `RESOLVING_EFFECTS` phase.
At the end of the specific handling, `Game` calls `passiveRoundPhaseHandler.advancePassivePhases()`,
so the match does not get stuck on an automatic phase or on a player who is no longer connected.

After the model has accepted the disconnection, the controller checks whether the match has ended.
If no connected player remains, the model sets the game to the `FINISHED` state and the controller performs the cleanup
of the game through `checkGameEndAndCleanup()`.
If instead the match is not over, the controller records a reconnection slot in the `ServerSessionRegistry`
with `recordGameDisconnection(...)`, removes the old session from the game and then unregisters it.
If only one connected player remains, the last-player timer is also started through `startLastPlayerTimer()`.
If another player reconnects in time, `reConnectedPlayer(...)` cancels the timer through `cancelLastPlayerTimer()`.
If instead nobody comes back before it expires, the timer enqueues a `SystemCloseGameCommand` and the match
is closed.


### Network layer
The server-side network layer is responsible for hiding the concrete protocol from the rest of the application.
Controllers and models do not need to know whether the client is connected via TCP or via RMI: they see a connection
through common contracts.

The central concept is the `VirtualView`.
At the architectural level it represents the client's remote view inside the server: it is the object that lets
the server treat the client as if it were a local view.
On the incoming side it is the point where client -> server commands arrive; on the outgoing side it also implements
`Listener`, so it can receive updates from the models and send them to the client.
This way the model notifies a `Listener`, not a socket or an RMI stub.

The concrete implementations are `VirtualViewTCP` and `VirtualViewRMI`.
Both do three things:
- they register a new session in the `ServerSessionRegistry` through `ServerConnectionLifecycle`
- they receive messages from the client, rebuild an application payload and pass it to the `MessageRouter`
- they receive server -> client payloads and send them to the client using the concrete protocol

The `MessageRouter` is the dividing point between the network and the application logic.
The virtual view passes it only the `sessionId` and the `ClientPayload`.
The router queries the `ServerSessionRegistry`, works out which phase that session is in
and puts the command in the queue of the correct controller: `PreLobbyController`, `LobbyController`
or `GameController`.

#### TCP
In the TCP case the server starts from `ConnectionAcceptor`.
This class opens a `ServerSocket`, stays in the `accept()` loop and for each new socket creates a
dedicated `VirtualViewTCP`.
The `VirtualViewTCP` is then started on a separate thread, because it must stay in a blocking read
on the client's socket.

```java
Socket clientSocket = this.serverSocket.accept();
VirtualViewTCP virtualView = new VirtualViewTCP(clientSocket, this.serverConnectionLifecycle, this.router);
Thread virtualViewThread = new Thread(virtualView);
virtualViewThread.start();
```

Inside `VirtualViewTCP` the socket channels are prepared.
The `PrintWriter` is used for server -> client messages, while the `BufferedReader` is used in the
read loop for client -> server messages.

```java
while ((rawMessage = reader.readLine()) != null) {
    this.commandHandler.handle(rawMessage);
}
```

The `ClientCommandHandler` class is the TCP-specific helper that takes a JSON line, reads the `type` field,
deserializes the correct payload and rebuilds a `ClientMessage`.
If the message is a `PONG`, it is not sent to the application controllers, but is used to update
the connection lifecycle.
For all other messages `VirtualViewTCP.handleClientMessage(...)` is called, which forwards the payload
to the `MessageRouter`.

On the outgoing side the flow is reversed.
The model produces a `ServerPayload`, often built through phase-specific mappers
such as `PreLobbyPayloadMapper`, `LobbyPayloadMapper` or `GamePayloadMapper`.
The `VirtualViewTCP` receives that payload through `notifyUpdate(...)`, wraps it in a `ServerMessage`
with the related `ServerMessageType`, serializes it with GSON and writes it to the socket.

#### RMI
In the RMI case the first remote object exposed by the server is `RmiConnectionEntryPoint`.
This object implements `RmiConnectionService` and is published in the registry under the name `MesosServer`.
The client looks up `MesosServer`, obtains the entry point stub and calls `connect(...)`,
passing its own RMI callback.

```java
public RmiServerService connect(RmiClientService clientCallback) throws RemoteException {
    return new VirtualViewRMI(this.serverConnectionLifecycle, clientCallback, this.router);
}
```

This call creates a `VirtualViewRMI` dedicated to that client.
The `VirtualViewRMI` is the RMI equivalent of `VirtualViewTCP`: it registers a session, keeps the client's
callback and becomes the server-side stub the client will use to send subsequent commands.

On the incoming side, when the client invokes a remote method on the stub, a `ClientMessage` arrives already rebuilt
by RMI.
The `VirtualViewRMI` checks that the message is valid and then passes the payload to the `MessageRouter`.
It does not need to parse JSON or manage a socket manually, because that work is hidden
by the RMI infrastructure.

On the outgoing side, instead, the `VirtualViewRMI` uses the remote callback `RmiClientService`.
When it receives a `ServerPayload` through `notifyUpdate(...)`, it wraps it in a `ServerMessage` and calls
`clientCallback.handleMessage(...)`.
So here too the model only sees a `Listener`, while the RMI details stay confined to the virtual view.

#### Helpers
The main helpers of the network layer are the ones that keep the rest of the server independent
of the concrete protocol.

`ClientConnection` is the minimal contract for a registered client connection.
It exposes the lifecycle operations, in particular `sendPing()` and `close()`.
Both `VirtualViewTCP` and `VirtualViewRMI` implement this interface: the `PingPongRoutine`
can therefore send pings and close connections without knowing whether TCP or RMI is behind them.

`ServerConnectionLifecycle` centralizes the management of the connections' lifetime.
When a virtual view is created, it registers through `registerConnection(...)` and obtains a `sessionId`.
When a PONG arrives, the virtual view calls `recordPong(...)`.
When a connection must be closed, the lifecycle reads the current phase from the `ServerSessionRegistry`
and decides whether to unregister the session directly or to send a `SystemDisconnectCommand`
to the correct lobby/game controller.

`PingPongRoutine` is the periodic heartbeat routine.
At every interval it reads the session snapshots from the `ServerSessionRegistry`.
If a session has not expired, it sends a ping through `ClientConnection.sendPing()`.
If instead a session has expired, it calls `ServerConnectionLifecycle.handleConnectionTimeout(...)`,
which closes the connection and starts the disconnection flow consistent with the user's phase.

`ProtocolErrorMapper` is used at the boundary of the network layer.
If TCP or RMI intercept a malformed message or an error before the command reaches the controllers,
the exception is turned into an `ErrorPayload`.
Actual application errors, instead, remain the responsibility of the controllers and models.


### Multi-Match Management
Multi-match management is based on a simple idea: each important application phase has a controller
that runs on its own thread and processes commands through a queue.
This way messages can arrive from different threads, for example TCP threads, RMI callbacks,
timeout routines or other controllers, but the logic of a single phase is still executed in order
by a single thread.
The controller's thread therefore becomes the single point of serialization of commands for that phase,
protecting the related model from direct concurrent access.

The common contract for the controllers' internal messages is `QueuedCommand`.
Each controller exposes `enqueueMessage(...)`, which adds a command to its own `BlockingQueue`.
The controller's `run()` method then waits with `commandQueue.take()` and processes one command at a time.

```java
QueuedCommand cmd = this.commandQueue.take();
```

These queues do not only contain messages that arrived from the network.
Client -> server messages are wrapped in `ClientPayloadCommand`: they contain the `sessionId`
and the `ClientPayload` rebuilt by the network layer.
There are also internal commands generated by the server, such as `SystemJoinCommand`, `SystemDisconnectCommand`,
`SystemSetupMatchCommand` and `SystemCloseGameCommand`.
These do not represent direct client requests, but events the server needs the correct controller
to process in the same order as the other commands.

The `MessageRouter` is the point where messages coming from the network enter this system.
It receives the `sessionId` and the payload from the virtual view, reads the session's current phase
from the `ServerSessionRegistry` and puts a `ClientPayloadCommand` in the queue of the correct controller.
If the session is in pre-lobby it uses the `PreLobbyController`; if it is in a lobby it retrieves the `LobbyController`
of that specific lobby; if it is in game it retrieves the `GameController` of that specific match.

This structure is applied to the server's three main phases: pre-lobby, lobby and game.
The pre-lobby is global, while lobby and game have dedicated controllers and models for each instance created.
The following sections explain the concrete role of each phase and how sessions are moved
from one phase to another.

### Pre Lobby
The pre-lobby is the server's global phase, i.e. the one where newly connected users are
before entering a lobby or a match.
For this reason there is only one `PreLobbyController`, created when the server starts.
The controller owns a command queue and delegates the application logic to the `PreLobbyModel`.

The commands handled in this phase are those defined by `PreLobbyCommandType`:
authentication, lobby list, lobby creation and lobby join.
The controller receives a `ClientPayloadCommand`, extracts the payload and chooses the correct method based
on the payload type.
If the payload is not allowed in pre-lobby, a phase error is sent to the client.

Authentication is handled by the model through `authenticate(...)`.
The model registers the nickname in the `ServerSessionRegistry` and sends the client an
`AuthenticationResultPayload`.
If the nickname is invalid or already taken, an `ErrorPayload` is sent.
After a successful authentication, the controller also checks whether that nickname has a reconnection slot
for a match: if it exists and the game is still open, it puts a `SystemJoinCommand` in the queue
of the related `GameController`.

The lobby list is obtained from the `ServerSessionRegistry` through `getOpenLobbies()`.
The model then builds the response payload with `PreLobbyPayloadMapper`, so the client receives only
a lightweight representation of the available lobbies.

Creating a lobby always starts from the pre-lobby.
The model validates that the session is authenticated and not already in a lobby, then creates the `Lobby` model
through the registry.
The controller completes the architectural part: it moves the session to the lobby phase with `enterLobby(...)`,
creates a `LobbyController`, registers it in the `ServerSessionRegistry` and starts the thread dedicated to the lobby.

Joining an existing lobby is split into two steps.
First the `PreLobbyModel` validates that the lobby exists, is joinable and that the user can enter.
Then the `PreLobbyController` retrieves the target `LobbyController` and sends it a `SystemJoinCommand`.
This way the actual change to the lobby does not happen on the pre-lobby thread,
but on the thread of the controller that owns that lobby.

### Lobby
The lobby is a specific application phase: each open lobby has its own `Lobby` model
and its own `LobbyController`.
The controller runs on a dedicated thread and serializes all the commands concerning that lobby.
This prevents several clients from modifying the same lobby model at the same time.

The `LobbyController` receives two main types of commands.
Client commands arrive as `ClientPayloadCommand` and are interpreted through `LobbyCommandType`:
leaving the lobby and starting the lobby.
Internal commands, instead, come from the server: `SystemJoinCommand` when a user must join
and `SystemDisconnectCommand` when a session in the lobby disconnects.

When a `SystemJoinCommand` arrives, the controller retrieves nickname and listener from the `ServerSessionRegistry`,
passes them to the `Lobby` model through `addPlayer(...)`, then updates the registry with `enterLobby(...)`.
If the insertion succeeds, the model notifies all listeners with `notifyPlayerJoined(...)`.

When a user voluntarily leaves the lobby, the controller calls `lobby.removePlayer(...)`,
moves the session back to pre-lobby with `leaveLobby(...)` and notifies the other clients with `notifyPlayerLeft(...)`.
If the lobby becomes empty, the controller removes both the `LobbyController`
and the `Lobby` model from the registry.
Disconnection follows a similar logic, but at the end the session is unregistered from the registry
because it is no longer an active session.

Starting the lobby can only be requested by the host.
The validation is in the `Lobby` model, through `startMatch(...)`.
If the lobby can start, the controller creates the `Player`s from the lobby's users through
`LobbyPlayerFactory`, creates the `Game` model through `GameFactory` and creates a `GameController`
dedicated to the match.

The lobby -> game transition also updates the `ServerSessionRegistry`.
All the lobby's sessions are moved to the game phase with `enterGame(...)`;
the listeners are removed from the lobby and added to the game.
The new `GameController` is registered in the registry, while the lobby's controller and model are removed.
Finally the game's thread is started and, after a short countdown, a `SystemSetupMatchCommand`
is enqueued to initialize the match.

### Game
The game is the phase in which a single match is handled.
Each match has its own `Game` model and its own `GameController`, registered in the `ServerSessionRegistry`
as the game's orchestrator.
Here too the controller runs on a dedicated thread and becomes the single point of serialization
of changes to the match model.

The `GameController` receives client commands and internal commands.
Client commands arrive as game payloads and are classified through `GameCommandType`:
`MOVE_TOTEM`, `CHOOSE_CARD` and `EXTRA_DRAW`.
Before calling the model, the controller resolves the `Player` associated with the session using the registry.
It then delegates the actual logic to the `Game` model, calling `moveTotem(...)`, `chooseCard(...)`
or `extraDrawn(...)`.

Internal commands, instead, are used to manage the match lifecycle.
`SystemSetupMatchCommand` starts the initial setup of the game.
`SystemDisconnectCommand` handles a player's disconnection.
`SystemJoinCommand` is used for the reconnection of an already authenticated player.
`SystemCloseGameCommand` forcibly closes the match, for example when the last-player timer expires.
These events also go through the same queue, so they never modify the model from external threads.

The `Game` model contains the domain logic of the match.
The controller does not decide the round rules: it only orchestrates messages, sessions and the model.
When the model changes state, it notifies the registered listeners, i.e. the virtual views of the clients still connected.
The payloads sent to the clients are built using `GamePayloadMapper`, so the network receives a
representation of the game suited for transfer, not the model's internal objects directly.

After every relevant application or internal command, the controller calls `checkGameEndAndCleanup()`.
If the game has reached the `FINISHED` state, the controller marks the game as closing, cancels any timers,
moves the sessions still registered out of the game, removes the `GameController` from the registry
and deletes the reconnection slots associated with that match.

Reconnection is always handled through the `GameController`'s queue.
When a player reconnects, the controller consumes the reconnection slot from the `ServerSessionRegistry`,
brings the new session back into the game, adds the new listener to the model and marks the player as connected.
If after the reconnection there is no longer only one connected player, the last-player timer is cancelled.
