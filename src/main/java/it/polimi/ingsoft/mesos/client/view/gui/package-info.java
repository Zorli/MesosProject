/**
 * JavaFX Graphical User Interface (GUI) for the Mesos client.
 *
 * <h2>What lives in this package</h2>
 * <ul>
 *   <li>{@link it.polimi.ingsoft.mesos.client.view.gui.GUIView} &mdash; the single
 *       JavaFX controller. It implements
 *       {@link it.polimi.ingsoft.mesos.client.view.ClientView} (the input side:
 *       payloads coming from the server are rendered into nodes) and at the
 *       same time owns the {@code @FXML}-injected fields and {@code @FXML}
 *       event handlers referenced by {@code gui.fxml} (the output side: user
 *       interactions are translated into commands sent through a
 *       {@link it.polimi.ingsoft.mesos.client.serverproxy.ServerProxy}).</li>
 *   <li>{@code gui.fxml} (sibling resources folder) &mdash; the only FXML file
 *       of the application. It declares the static layout: a {@code BorderPane}
 *       shell with a top bar and a centred {@code StackPane fx:id="pages"} that
 *       contains five sibling pages (login, lobby list, lobby form, game,
 *       end). Exactly one page is made visible at a time by
 *       {@code showPage(Node)}.</li>
 *   <li>{@code mesos-gui.css} (sibling resources folder) &mdash; visual theme
 *       applied to the scene. FXML nodes are themed through their
 *       {@code styleClass} attribute, which maps directly to CSS class
 *       selectors (e.g. {@code styleClass="primary-button"} &harr;
 *       {@code .primary-button { ... }}).</li>
 * </ul>
 *
 * <h2>How FXML binds to {@code GUIView}</h2>
 * <p>The binding between {@code gui.fxml} and {@code GUIView} relies on three
 * naming conventions that are enforced at FXML load time:
 * <ol>
 *   <li>{@code fx:id="foo"} on an FXML node injects that node into the
 *       {@code @FXML private SomeType foo} field of the controller. The field
 *       name and the node type must match exactly.</li>
 *   <li>{@code onAction="#handleFoo"} on an FXML control calls the
 *       {@code @FXML private void handleFoo()} method of the controller.
 *       The method must be {@code void} and take either zero arguments or a
 *       single {@code ActionEvent}.</li>
 *   <li>{@code styleClass="bar"} on an FXML node binds it to the
 *       {@code .bar} selector in {@code mesos-gui.css}. JavaFX CSS uses the
 *       {@code -fx-...} property prefix (e.g. {@code -fx-background-color}).</li>
 * </ol>
 * <p>The annotated {@code @FXML private void initialize()} method runs
 * automatically after FXML injection completes; it is the place to seed
 * combo-boxes, choose the initial page, and start auxiliary timers.
 *
 * <h2>Threading model</h2>
 * <p>JavaFX nodes can only be read or modified on the JavaFX Application
 * Thread. {@code GUIView} receives server payloads from TCP and RMI networking
 * threads, so every {@link it.polimi.ingsoft.mesos.client.view.ClientView}
 * callback delegates its UI work through {@code runOnFx(Runnable)}, which
 * runs the work immediately when already on the FX thread and otherwise
 * schedules it through {@link javafx.application.Platform#runLater}.
 * {@code @FXML} event handlers, by contrast, are already invoked on the FX
 * thread and must not be wrapped again.
 *
 * <h2>Page-stack architecture</h2>
 * <p>All five logical screens of the application live as sibling children of
 * the central {@code StackPane}. They are not separate FXML files. Pages
 * therefore share the same scene graph and the same controller, and dynamic
 * content (lobby cards, market cards, offer-path tiles, player rows, tribe
 * strips, final ranking) is rendered into empty placeholder containers such
 * as {@code lobbyListContainer}, {@code upperRowContainer},
 * {@code lowerRowContainer}, {@code offerPathContainer},
 * {@code playerListContainer}, {@code tribeCardsContainer} and
 * {@code finalRankingContainer}.
 *
 * <h2>Where to go next</h2>
 * <p>For a step-by-step modification guide (adding a button, adding a page,
 * binding a new payload to the UI, recipes and common pitfalls) see the
 * Markdown document {@code docs/guide-javafx-gui-modification.md} in the
 * project root.
 */
package it.polimi.ingsoft.mesos.client.view.gui;
