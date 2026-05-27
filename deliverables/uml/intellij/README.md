# UML generati da IntelliJ IDEA

Questa cartella contiene i diagrammi UML generati direttamente con la funzione
**Diagrams** di IntelliJ IDEA Ultimate. Sono complementari ai diagrammi PlantUML
in `docs/uml/puml/` (sorgenti) e `docs/uml/svg/` (rendering).

## Come rigenerare i diagrammi

Richiede **IntelliJ IDEA Ultimate** (la versione Community non ha il plugin
Diagrams).

### Diagramma di una classe / package

1. Apri il progetto in IntelliJ IDEA.
2. Nel pannello **Project**, fai click destro su una classe o un package, ad es.
   `it.polimi.ingsw.mesos.server.controller`.
3. Seleziona **Diagrams → Show Diagram...** (scorciatoia: `⌥⇧⌘U` su macOS,
   `Ctrl+Alt+Shift+U` su Windows/Linux).
4. Nella toolbar del diagramma scegli cosa mostrare:
   - **Fields**: campi delle classi
   - **Constructors / Methods / Properties**: visibilità membri
   - **Show Dependencies**: relazioni di uso (use/import)
   - **Show Inner Classes**
5. Per limitare la profondità, usa il menù contestuale **Add Class to Diagram**
   o **Show in Diagram** sulle dipendenze selezionate.

### Esportazione

1. Con il diagramma aperto, fai click destro sull'area e scegli
   **Export Diagram → Export to File...**.
2. Salva il file in questa cartella, scegliendo come formato:
   - **SVG** (preferito — vettoriale, leggero, navigabile da browser)
   - **PNG** (per Word / PDF)
3. Il file `.uml` di IntelliJ (layout sorgente) può essere salvato a parte
   con **Save UML diagram as...** se vuoi poterlo riaprire nella stessa
   disposizione.

## File suggeriti da generare

Per coerenza con i diagrammi PlantUML, generare almeno:

| File                              | Cosa rappresentare                                |
| --------------------------------- | -------------------------------------------------- |
| `client-intellij.svg`             | Package `client` completo                          |
| `server-controller-intellij.svg`  | Package `server.controller`                        |
| `server-model-intellij.svg`       | Package `server.model` (con sotto-packages)        |
| `server-network-intellij.svg`     | Package `server.network` + `server.virtualview`    |
| `cards-intellij.svg`              | Gerarchia delle carte in `server.model.cards`      |

## Note

- Disabilita "Show Implicit Generic Types" se il diagramma diventa illeggibile.
- Per i diagrammi grandi (es. `server.model`), considera di esportare a
  zoom 100% e poi ridurre, così il testo resta nitido nello SVG.
- I file `.uml` salvati da IntelliJ contengono solo layout, non rigenerano
  automaticamente quando il codice cambia: ricordati di ri-esportare dopo
  refactoring importanti.
