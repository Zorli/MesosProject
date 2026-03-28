## Contesto
L'obiettivo della persistenza runtime del match e' avere un solo entry point applicativo per ogni
transizione durevole della partita: `GameOrchestrator`.

Ad oggi questo obiettivo e' solo parzialmente raggiunto:
- `setupMatch()` persiste `GAME_INITIALIZED`
- `moveTotem()` persiste `TURN_STARTED` quando il piazzamento termina e si entra in draw phase
- i metodi di dominio per draw, extra draw, resolve effects, reset board e final score esistono
- i checkpoint applicativi `TURN_FINISHED`, `ERA_CHANGED` e `MATCH_COMPLETED` esistono nel
  `MatchPersistenceCoordinator`

Il gap aperto e' che l'orchestrator non espone ancora i metodi controller per le fasi successive,
quindi la persistenza di quelle transizioni non e' ancora centralizzata davvero.

## Stato attuale dell'implementazione
Il codice oggi e' in questa situazione:

### Gia' cablato nell'orchestrator
- `setupMatch()` -> `recordMatchInitialized(...)`
- `moveTotem(...)` -> `recordAfterMoveTotem(...)`

### Preparato ma non ancora usato dall'orchestrator
- `recordTurnFinished(...)`
- `recordEraChanged(...)`
- `recordMatchCompleted(...)`

### Disponibile solo nel dominio
- `Game#chooseCard(...)`
- `Game#extraDrawn(...)`
- `Game#resolveEffects()`
- `Game#resetBoard()`
- `Game#calculateFinalScore()`

Questo significa che il dominio sa gia' avanzare le fasi della partita, ma l'application layer non
ha ancora preso possesso completo del timing di persistenza.

## Perche' il problema non e' solo teorico
Il sistema puo' funzionare nei flow gia' coperti dai test e nei casi in cui i caller ricordano di
salvare manualmente al checkpoint corretto. Pero' questa soluzione resta fragile perche':

### 1. Il checkpoint timing e' distribuito fuori dall'orchestrator
Se il caller ottiene `Game` e invoca direttamente `chooseCard()`, `resolveEffects()` o
`calculateFinalScore()`, puo' dimenticare di salvare o salvare nel momento sbagliato.

### 2. La semantica dei checkpoint puo' degradarsi
Quando la persistenza viene invocata manualmente da test o caller esterni, il rischio non e' solo
"non salvo". Il rischio e' anche "salvo con il checkpoint sbagliato", rendendo meno affidabile il
significato di `TURN_STARTED`, `TURN_FINISHED`, `ERA_CHANGED` e `MATCH_COMPLETED`.

### 3. Il contratto di restore esteso non e' ancora imposto dal CI
I test contrattuali pensati per draw phase, reset board, era change e final scoring sono ancora
disabilitati. Quindi i checkpoint tardi non sono ancora protetti da regressioni continue.

### 4. La policy di errore di persistenza non e' ancora concentrata in un solo posto
Se una mutazione di dominio riesce e il save fallisce subito dopo, oggi non esiste ancora un punto
unico dove definire con chiarezza la policy applicativa del fallimento.

### 5. Il flow finale ranking + completamento match non e' ancora orchestrato end-to-end
La chiusura della partita deve coordinare almeno:
- aggiornamento ranking
- persistenza `MATCH_COMPLETED`
- eventuale cleanup finale

Finche' questo flow non vive nell'orchestrator, il rischio e' avere allineamenti parziali tra stato
del match, checkpoint persistito e ranking.

## Effetto pratico sul codice
Il problema nascosto non e' che il model sia sbagliato nel calcolo delle fasi. Il problema e' che la
correttezza del salvataggio dipende ancora dalla disciplina del chiamante.

In pratica oggi il sistema e':
- corretto nei flow gia' incapsulati da orchestrator
- plausibilmente corretto nei flow manuali se il caller fa tutto giusto
- non ancora robusto come boundary architetturale

Questa differenza e' importante: "funziona nei test attuali" non equivale a "abbiamo davvero un solo
entry point di persistenza".

## Criterio di chiusura dell'issue
L'issue puo' considerarsi chiusa solo quando tutte queste condizioni sono vere:

1. `GameOrchestrator` espone i metodi pubblici per:
   - `chooseCard(...)`
   - `extraDrawn(...)`
   - `resolveEffects()`
   - `resetBoard()`
   - `calculateFinalScore()`
2. Ogni metodo applica il checkpoint corretto solo dopo il successo della mutazione di dominio.
3. Il dominio resta persistence-agnostic: niente save in `Game`, `Board`, `TurnState`, `TurnOrderTrack`.
4. I caller non devono piu' conoscere il timing dei checkpoint per i flow standard.
5. I test contrattuali estesi di save/restore vengono abilitati e passano.
6. Il flow finale allinea in modo esplicito ranking, `MATCH_COMPLETED` e cleanup.

## Indicazione per la PR applicativa futura
La futura PR che chiude davvero il problema deve essere valutata soprattutto su questi punti:
- ownership unica del checkpoint timing nell'orchestrator
- semantica coerente dei checkpoint
- assenza di persistenza nel model
- copertura test sul restore dopo draw, resolve, reset, era change e final scoring
- definizione chiara della policy in caso di save fallito
