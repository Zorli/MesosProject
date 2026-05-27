# JavaFX GUI — Developer Onboarding & Modification Guide

This document is a practical guide to the Mesos game client's JavaFX user interface. The GUI is intentionally small and centralized: a single FXML file describes every screen, a single Java class (`GUIView`) is both the controller and the `ClientView` adapter, and a single CSS file styles everything. The screens are organized as a *page stack*: five sibling nodes share one `StackPane`, and only one is made visible at a time. After reading this guide you should be able to add a button, bind a new label to a server payload, add a brand-new page, and avoid the two or three pitfalls that bite every newcomer to JavaFX.

## 5-minute onboarding (TL;DR)

- The GUI lives in three files: `gui.fxml` (layout), `mesos-gui.css` (look), `GUIView.java` (behaviour + `ClientView` impl).
- `gui.fxml` has a `BorderPane` root. The `top` is a small brand bar (`HBox fx:id="topBar"`). The `center` is a `StackPane fx:id="pages"` containing five sibling pages: `loginPage`, `lobbyPage`, `lobbyFormPage`, `gamePage`, `endPage`.
- Only one page should be visible at a time. Use `showPage(node)` to switch; it toggles `visible` and `managed` on each child.
- FXML and Java are bound by name: `fx:id="foo"` matches `@FXML private SomeNode foo;`, and `onAction="#handleX"` matches `@FXML private void handleX()`.
- Style classes in FXML (`styleClass="primary-button"`) match selectors in `mesos-gui.css` (`.primary-button { ... }`).
- The FXML controller is set explicitly: `loader.setController(this)` inside `GUIView.openStage`.
- JavaFX has one UI thread. Any code that touches JavaFX nodes from a non-FX thread (network callbacks, RMI handlers) must be wrapped: `runOnFx(() -> { ... })`. Forgetting this throws `IllegalStateException: Not on FX application thread`.
- FXML `onAction` handlers already run on the FX thread. Do **not** wrap them again.
- The JVM can only call `Platform.startup(...)` once; `GUIView` guards this with a static `AtomicBoolean JAVAFX_STARTED`.
- Outgoing server commands are routed through `callServer(...)`, which converts thrown exceptions into a visible `ErrorPayload("CLIENT_GUI_ERROR", ...)`.
- Build with Maven (Java 21, JavaFX 21.0.6). Run the entry point `it.polimi.ingsoft.mesos.client.LaunchClient`.
- At startup the launcher asks `1) GUI / 2) TUI` and `1) TCP / 2) RMI`. Pick `1` then `1` for the default path.

## Architecture at a glance

```
FXML  ──load──►  FXMLLoader  ──inject──►  GUIView (@FXML fields & handlers)
                                            │
                                            ▼
ServerProxy  ◄── outgoing commands ──  GUIView  ◄── incoming payloads ──  MessageRouter ◄── VirtualServer (TCP/RMI)
```

- **`gui.fxml`** is the XML scene graph. `FXMLLoader` parses it and constructs the JavaFX nodes.
- **`FXMLLoader`** injects nodes into the controller's `@FXML` fields and binds `onAction` handlers to controller methods.
- **`GUIView`** is the FXML controller. It is also the `ClientView` implementation: the routing layer hands it incoming payloads and it renders them.
- **`ServerProxy`** (TCP or RMI) is the outgoing channel: `GUIView` calls methods like `authenticate(...)`, `createLobby(...)`, `chooseCard(...)`.
- **`MessageRouter`** reads `ServerMessage.type` and dispatches to the matching `ClientView` method (`showError`, `showLobbyUpdate`, etc.).
- **`VirtualServer`** is the remote server abstraction. The proxy turns network events into payload objects that flow into the router.

## File map

| Path | Purpose |
|---|---|
| `src/main/resources/it/polimi/ingsoft/mesos/client/view/gui/gui.fxml` | The single FXML defining all five pages, top bar, and all `fx:id` / `onAction` wiring. |
| `src/main/resources/it/polimi/ingsoft/mesos/client/view/gui/mesos-gui.css` | The single stylesheet. All `.styleClass` selectors used in FXML resolve here. |
| `src/main/java/it/polimi/ingsoft/mesos/client/view/gui/GUIView.java` | The controller, the `ClientView` impl, and the JavaFX bootstrap (~1836 lines). |
| `src/main/java/it/polimi/ingsoft/mesos/client/view/ClientView.java` | Contract implemented by both GUI and TUI: `showError`, `showAuthenticationResult`, `showLobbyUpdate`, `showLobbyList`, `showGameUpdate`, `showFinalGameResult`. |
| `src/main/java/it/polimi/ingsoft/mesos/client/view/MessageRouter.java` | Reads `ServerMessage.type`, calls the matching `ClientView` method. |
| `src/main/java/it/polimi/ingsoft/mesos/client/LaunchClient.java` | Entry point: prompts GUI/TUI + TCP/RMI, attaches the `ServerProxy`, calls `start()`. |
| `src/main/java/it/polimi/ingsoft/mesos/client/serverproxy/*` | Outgoing TCP and RMI implementations of `ServerProxy`. |
| `src/main/resources/it/polimi/ingsoft/mesos/assets/cards/<id>.jpg` | Card art keyed by card id. Loader falls back to card `1` if missing. |
| `src/main/resources/it/polimi/ingsoft/mesos/assets/offerPath/<code>.png` | Offer-path tile art keyed by tile code. |
| `src/main/resources/it/polimi/ingsoft/mesos/assets/totems/<totem>.png` | Player totem icons. |
| `docs/uml/svg/client.svg` | Client UML — useful when you want a wider picture. |

## The page-stack pattern

The `center` of the root `BorderPane` is a `StackPane fx:id="pages"`. A `StackPane` lays all of its children on top of one another. The GUI exploits this for navigation: every "screen" is just a sibling node inside `pages`, and switching screens means toggling visibility.

Pages, in declaration order:

| `fx:id` | Type | Role | Notable children |
|---|---|---|---|
| `loginPage` | `StackPane` | Nickname entry. Shown first. | `nicknameField`, `loginButton`, `loginStatusLabel` |
| `lobbyPage` | `VBox` | List of open lobbies. | `lobbyListContainer`, `openCreateTableButton`, `lobbyCountLabel`, `lobbyStatusLabel` |
| `lobbyFormPage` | `VBox` | Create / control current lobby. | `capacityComboBox`, `createLobbyButton`, `startLobbyButton`, `exitLobbyButton`, `currentLobby*Label` |
| `gamePage` | `ScrollPane` | The full match. | `upperRowContainer`, `lowerRowContainer`, `offerPathContainer`, `playerListContainer`, `tribeCardsContainer`, `matchTitleLabel`, `turnLabel`, `selectedCardLabel` |
| `endPage` | `VBox` | Final ranking + back button. | `finalRankingContainer` |

Switching is done by `showPage`:

```java
private void showPage(javafx.scene.Node page) {
    for (Node child : pages.getChildren()) {
        boolean active = (child == page);
        child.setVisible(active);
        child.setManaged(active);
    }
    // topBar is hidden only on the login page
    topBar.setVisible(page != loginPage);
    topBar.setManaged(page != loginPage);
    // start / stop the lobbyRefreshTimer Timeline depending on the page
}
```

Two consequences worth knowing:

1. All `StackPane` children are visible by default. If you add a sixth page and never call `showPage` for it, it will sit on top of whatever was shown first. Always pass through `showPage` once during `initialize()` to normalize the stack.
2. `topBar` lives in the `BorderPane.top`, *outside* the page stack. `showPage` hides it specifically on `loginPage`.

## How FXML binds to the controller

### 6.1 `fx:id` ↔ `@FXML private Node id;`

The `fx:id` attribute names a node inside FXML; the loader looks for a matching field in the controller and injects the constructed node into it. The field name and type must match exactly.

```xml
<!-- gui.fxml -->
<Button fx:id="loginButton"
        text="Log in"
        maxWidth="Infinity"
        onAction="#handleLogin"
        styleClass="primary-button"/>
```

```java
// GUIView.java
@FXML private Button loginButton;
```

If the name does not match a field, the FXML still loads (the field stays `null`); if the *type* does not match, `FXMLLoader.load()` throws a `LoadException` at startup.

### 6.2 `onAction="#handle"` ↔ `@FXML private void handle()`

The leading `#` tells FXML "this is a controller method name". The method must be `void`, take either zero arguments or one `ActionEvent`, and be reachable from the loader's controller (which is set via `loader.setController(this)` in `openStage`).

```xml
<!-- gui.fxml -->
<Button fx:id="createLobbyButton"
        text="Create lobby"
        onAction="#handleCreateLobby"
        maxWidth="Infinity"
        styleClass="primary-button"/>
```

```java
// GUIView.java
@FXML
private void handleCreateLobby() {
    Integer capacity = this.capacityComboBox.getValue();
    if (capacity == null) {
        this.showError(new ErrorPayload("CLIENT_GUI_ERROR", "Pick a capacity first."));
        return;
    }
    this.callServer(() -> this.serverProxy.createLobby(capacity));
}
```

Handlers run on the FX Application Thread — see threading below.

### 6.3 `styleClass="x"` ↔ `.x` selector in `mesos-gui.css`

JavaFX CSS uses the same selector syntax as web CSS, but property names are prefixed with `-fx-`.

```xml
<!-- gui.fxml -->
<Label fx:id="lobbyStatusLabel"
       text="Choose an open table or create your own lobby."
       styleClass="section-subtitle"/>
```

```css
/* mesos-gui.css */
.section-subtitle {
    -fx-font-size: 14px;
    -fx-text-fill: #c4c8d4;
}
```

A node can carry several space-separated classes: `styleClass="page lobby-page"`.

## JavaFX threading rules (read this even if you skip everything else)

JavaFX has a single dedicated thread, the **FX Application Thread**, that owns the scene graph. Any read or write to a `Node`, `ObservableList`, property, or stage **must** happen on that thread. The router and the server proxy run on networking threads, so every `ClientView` method that lands inside `GUIView` must hop back to the FX thread before touching anything.

The codebase encapsulates this with a tiny helper:

```java
private void runOnFx(Runnable runnable) {
    if (Platform.isFxApplicationThread()) {
        runnable.run();
    } else {
        Platform.runLater(runnable);
    }
}
```

Every `ClientView` implementation in `GUIView` follows this pattern:

```java
@Override
public void showLobbyList(LobbyListPayload payload) {
    this.runOnFx(() -> {
        this.lobbyListContainer.getChildren().clear();
        for (LobbySummary lobby : payload.lobbies) {
            this.lobbyListContainer.getChildren().add(this.lobbyCard(lobby));
        }
        this.lobbyCountLabel.setText(payload.lobbies.size() + " tables");
    });
}
```

What happens if you forget `runOnFx`? You get a runtime exception:

```
java.lang.IllegalStateException: Not on FX application thread; currentThread = ...
```

What about `onAction` handlers — should they also be wrapped? **No.** Those callbacks fire directly from the FX thread, so wrapping again is harmless but wasteful (`runOnFx` short-circuits if already on FX, but you make the code noisier than it should be).

One more thread-related quirk: `Platform.startup(...)` can be called **only once per JVM**. `GUIView` guards this with a static flag so that re-launching the GUI within the same process does not crash:

```java
private static final AtomicBoolean JAVAFX_STARTED = new AtomicBoolean(false);

public void start() {
    if (JAVAFX_STARTED.compareAndSet(false, true)) {
        Platform.startup(() -> this.openStage(new Stage()));
    } else {
        Platform.runLater(() -> this.openStage(new Stage()));
    }
}
```

Finally, closing the main window must close the proxy *and* call `System.exit(0)`, because RMI/TCP threads keep the JVM alive otherwise. This is wired in `openStage`:

```java
targetStage.setOnCloseRequest(event -> this.shutdownClient());
```

## Recipes

The recipes below are deliberately copy-pasteable. Each one names the file, the exact lines to add, and the corresponding Java declarations.

### Recipe A — Add a new button to an existing page

Goal: add a "Refresh" button on the lobby page that re-fetches the lobby list.

**1. FXML.** Inside `<VBox fx:id="lobbyPage">`, next to the existing `openCreateTableButton`:

```xml
<HBox spacing="12">
    <Button fx:id="openCreateTableButton"
            text="Create table"
            onAction="#handleOpenCreateTable"
            styleClass="primary-button"/>

    <Button fx:id="refreshLobbiesButton"
            text="Refresh"
            onAction="#handleRefreshLobbies"
            styleClass="primary-button"/>
</HBox>
```

**2. Controller field.** Inside `GUIView.java`, in the field block with the other `@FXML private Button` declarations:

```java
@FXML private Button refreshLobbiesButton;
```

**3. Controller handler.** Anywhere among the other `@FXML private void handle...` methods:

```java
@FXML
private void handleRefreshLobbies() {
    this.callServer(() -> this.serverProxy.listLobbies());
}
```

That is the entire change. The button now sends a `listLobbies` request on click; when the server replies, the existing `showLobbyList` path repaints `lobbyListContainer`.

### Recipe B — Add a new label that shows dynamic data

Goal: show "Your prestige: N" on the game page, updated on every game update.

**1. FXML.** Inside the game-page header `HBox` (next to `turnLabel`):

```xml
<Label fx:id="myPrestigeLabel"
       text="Prestige: -"
       styleClass="turn-pill"/>
```

**2. Controller field:**

```java
@FXML private Label myPrestigeLabel;
```

**3. Update from the payload.** In `showGameUpdate`, inside the `runOnFx` block where the rest of the header is updated:

```java
@Override
public void showGameUpdate(GamePayload payload) {
    this.runOnFx(() -> {
        // ... existing rendering ...
        PlayerPayload me = payload.players.stream()
            .filter(p -> p.nickname.equals(this.nickname))
            .findFirst()
            .orElse(null);
        int prestige = (me != null) ? me.prestigePoints : 0;
        this.myPrestigeLabel.setText("Prestige: " + prestige);
    });
}
```

Crucially the update lives **inside** `runOnFx`, because `showGameUpdate` is called from a non-FX thread by the router.

### Recipe C — Add a new page to the page stack

Goal: add a "settings" page reachable from the lobby page.

**1. FXML.** Add a new sibling inside `<StackPane fx:id="pages">`, after `endPage`:

```xml
<VBox fx:id="settingsPage"
      alignment="CENTER"
      spacing="18"
      styleClass="page">
    <Label text="Settings" styleClass="section-title"/>
    <Label fx:id="settingsHelpLabel"
           text="Toggle whatever you want here."
           styleClass="section-subtitle"/>
    <Button text="Back"
            onAction="#handleCloseSettings"
            styleClass="primary-button"/>
</VBox>
```

**2. Controller field:**

```java
@FXML private VBox settingsPage;
```

**3. Show / hide it.** Add an opener (called from wherever, e.g. a new button on the lobby page) and a closer:

```java
@FXML
private void handleOpenSettings() {
    this.showPage(this.settingsPage);
}

@FXML
private void handleCloseSettings() {
    this.showPage(this.lobbyPage);
}
```

**4. Initial visibility.** `showPage(loginPage)` in `initialize()` already hides every other sibling — including `settingsPage` — so no extra wiring is needed. If you ever add a page *and* skip the first `showPage` call, it will overlap whatever JavaFX paints first. Always make sure `initialize()` (or your boot path) ends with a single `showPage(...)`.

### Recipe D — Add a new style class

Goal: introduce a `.accent-button` highlighted variant on top of `.primary-button`. (Avoid reusing existing names — `.primary-button`, `.secondary-button`, `.ghost-button`, `.danger-button` are already defined in `mesos-gui.css`.)

**1. FXML.** Pick a class name and stack it with whatever else you want; JavaFX accepts multiple values separated by spaces:

```xml
<Button text="Save"
        onAction="#handleSave"
        styleClass="primary-button accent-button"/>
```

**2. CSS.** Add the selector to `mesos-gui.css`. Use the JavaFX `-fx-` property prefix:

```css
.accent-button {
    -fx-background-color: #2e86de;
    -fx-text-fill: white;
    -fx-background-radius: 8;
}

.accent-button:hover {
    -fx-background-color: #54a0ff;
}
```

Save and re-run; JavaFX picks up the stylesheet from `src/main/resources` via `Scene.getStylesheets().add(...)`. There is no live reload — restart the client to see changes.

### Recipe E — Render a new dynamic widget driven by a server payload

Goal: a "recent alerts" list, fed by entries in a hypothetical `GamePayload.alerts`.

**1. FXML.** Add an empty container inside `gamePage` (a `StackPane`, `VBox`, or `HBox`, depending on layout). Make it empty in FXML — the controller will fill it.

```xml
<VBox fx:id="alertsContainer" spacing="6" styleClass="card-frame"/>
```

**2. Controller field:**

```java
@FXML private VBox alertsContainer;
```

**3. Repaint on each game update.** Follow the exact same pattern used by `showLobbyList`: clear, then rebuild:

```java
@Override
public void showGameUpdate(GamePayload payload) {
    this.runOnFx(() -> {
        // ... existing rendering ...
        this.alertsContainer.getChildren().clear();
        for (String alert : payload.alerts) {
            Label entry = new Label(alert);
            entry.getStyleClass().add("muted");
            this.alertsContainer.getChildren().add(entry);
        }
    });
}
```

For a more elaborate cell, mirror the existing `lobbyCard(LobbySummary)` factory method in `GUIView`: build a styled `VBox`/`HBox`, return it, and let the calling block append it to the container.

### Recipe F — Handle a new outgoing command

Goal: send a custom `playerReady()` command to the server when a button is clicked.

**1. Make sure the server proxy exposes the method.** Add the new method to the `ServerProxy` interface (and both TCP and RMI implementations).

**2. Wrap the call with `callServer`.** Inside your handler:

```java
@FXML
private void handlePlayerReady() {
    this.callServer(() -> this.serverProxy.playerReady());
}
```

`callServer` is a tiny helper that already exists:

```java
private void callServer(ServerCall call) {
    if (this.serverProxy == null) {
        this.showError(new ErrorPayload("CLIENT_GUI_ERROR", "ServerProxy non collegato."));
        return;
    }
    try {
        call.run();
    } catch (RuntimeException exception) {
        this.showError(new ErrorPayload("CLIENT_GUI_ERROR", exception.getMessage()));
    }
}
```

This single wrapper guarantees that any thrown `RuntimeException` becomes a visible `ErrorPayload("CLIENT_GUI_ERROR", ...)` shown by `showError`, rather than a stack trace nobody sees.

## Common pitfalls

- **`fx:id` and `@FXML` field name drift.** Renaming either side without updating the other gives you a `LoadException` (type mismatch) or a silently `null` field (name mismatch).
- **Typo in `onAction="#handleX"`.** No exception — the button just does nothing. Always verify the handler is wired by clicking it once after the change.
- **Touching a node from a network thread without `runOnFx`.** Throws `IllegalStateException: Not on FX application thread`. Every `ClientView` method in `GUIView` must begin with `this.runOnFx(() -> { ... });`.
- **All `StackPane` children are visible by default.** A newly added sibling page will overlap whatever was shown first until `showPage(...)` runs at least once. The existing `initialize()` already calls `showPage(loginPage)`; do not break that.
- **Calling `Platform.startup(...)` twice.** Throws `IllegalStateException: Toolkit already initialized`. Use the existing `JAVAFX_STARTED` `AtomicBoolean` guard.
- **Editing the CSS file under `target/`.** Maven copies resources from `src/main/resources` on every build; changes inside `target/` are blown away. Always edit `src/main/resources/.../mesos-gui.css`.
- **`controlsfx` is on the classpath but unused.** The project depends on `controlsfx 11.2.1`, but every visible widget is a stock JavaFX control. Do not pull in `controlsfx` types just because they auto-complete — it adds inconsistency without benefit.
- **Not closing the proxy on window close.** RMI/TCP threads are non-daemon and will keep the JVM alive after the window is gone. Keep the existing `setOnCloseRequest(event -> shutdownClient())` wiring, and route any new shutdown work through `shutdownClient()`.
- **Forgetting the optimistic counter rollback.** The controller pre-decrements local pick counters before the server confirms (`localUpperChoicesUsed`, `localLowerChoicesUsed`, `pendingDraftRow`, `localDraftTurnKey`). If your new handler also mutates this state, make sure `showError` triggers `rollbackPendingDraftChoice()` when applicable — otherwise the local view diverges from the server.
- **Hard-coding asset paths.** Assets are loaded relative to the classpath: `/it/polimi/ingsoft/mesos/assets/cards/<id>.jpg`, `/it/polimi/ingsoft/mesos/assets/offerPath/<code>.png`, `/it/polimi/ingsoft/mesos/assets/totems/<totem>.png`. Stay inside these directories or the fallback (card `1`) will quietly kick in.

## Build and run

This is a Maven project on Java 21 with JavaFX 21.0.6 (`javafx-controls`, `javafx-fxml`) and `controlsfx 11.2.1`. The Maven main class is `it.polimi.ingsoft.mesos.client.LaunchClient`.

Quick start from the project root:

```bash
# Build
./mvnw clean package

# Run the client via exec (preferred during development)
./mvnw exec:java -Dexec.mainClass=it.polimi.ingsoft.mesos.client.LaunchClient

# Or run the packaged jar (if your build configuration produces a runnable jar)
java -jar target/<your-jar>.jar
```

At startup the launcher prompts twice on stdin:

```
1) GUI
2) TUI
> 1

1) TCP
2) RMI
> 1
```

Pick `1` and `1` for the default GUI-over-TCP path. The first prompt drives whether `GUIView` or the TUI is constructed; the second selects the `ServerProxy` implementation passed to `guiController.attachServerProxy(...)`. After both prompts, `guiController.start()` runs and the JavaFX window appears on the login page.

## Where to go next

- **`ClientView.java`** — the six-method contract every view must satisfy. If you add a new payload type, you add a method here first.
- **`MessageRouter.java`** — routing from `ServerMessage.type` to the right `ClientView` method. New payloads need a new branch here.
- **`ServerProxy` (TCP and RMI implementations under `client/serverproxy/`)** — outgoing commands. Add new methods here when you need a new client-to-server message.
- **`mesos-gui.css`** — for visual-only changes. JavaFX CSS reference: `-fx-` prefixed properties, same selector grammar as web CSS, no live reload.
- **`docs/uml/svg/client.svg`** — the client-side UML diagram; useful when reasoning about how `GUIView`, `MessageRouter`, and the proxies fit together with the rest of the client.
- **`GUIView.openStage(Stage)`** — the stage bootstrap (FXMLLoader setup, `setController(this)`, scene + stylesheet, `setOnCloseRequest`). Read this once; it is the closest thing the project has to a UI `main`.
