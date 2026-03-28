# Test Coverage Map

## Scope

Questo documento riassume lo stato della suite test su `feat/add-test` dopo l'estensione di:
- fixture condivise per gameplay e restore
- flow test da lobby
- integration test su save/restore
- scenari late-phase (`EXTRA_DRAWS`, `FINAL_SCORING`)
- unit test + mutation analysis su `server.model.cards.buildings`
- unit test + mutation analysis su `server.model.cards.events`
- flow test lunghi live-vs-restored fino a `FINISHED` su 2, 3, 4 e 5 giocatori

Fonti usate:
- classi di test presenti in `src/test/java`
- report `target/site/jacoco/jacoco.csv`

## Nota importante su JaCoCo

Le metriche JaCoCo sono affidabili soprattutto per la parte `model` testata via unit test. Per i package
infrastrutturali PostgreSQL (`server.persistence.*` e parte di `server.model.ranking`) il report resta
parzialmente fuorviante: esistono integration test verdi, ma alcune classi appaiono ancora a copertura
zero nel CSV.

Quindi:
- usare JaCoCo come segnale forte sui package di dominio
- usare anche il mapping `classe <-> test` per persistence/ranking

## Stato per package

### Molto buono

- `server.model.GameState`
  Copertura forte di `Game`, eccezioni, `TurnState`, `TurnOrderTrack`, round phases e regressioni principali.
- `server.model.board`
  `Board`, `UpperPile`, `LowerPile`, `offerpath` sono coperti sia da unit test sia da flow test.
- `server.model.lobby`
  `Lobby` e `LobbyManager` hanno test unitari e test di gameplay da lobby.
- `server.model.player`
  `Player` e `Tribe` hanno test unitari utili e vengono anche attraversati dai flow test.
- `server.model.Factory`
  `DefaultGameFactory`, `ParserJson`, `DeckTemplate` sono coperti in modo sufficiente per il ruolo attuale.
- `server.model.Orchestration`
  `GameOrchestrator` e `MatchPersistenceCoordinator` sono coperti, inclusi checkpoint principali.

### Buono ma con buchi mirati

- `server.model.cards`
  Le astrazioni base (`Deck`, `BuildingCard`, `TribeDeck`, `BuildingDeck`) sono coperte, ma non tutte le
  specializzazioni hanno test dedicati.
- `server.model.cards.characters`
  `Hunter`, `Builder`, `Gatherer`, `Artist`, `Inventor`, `Shaman` sono in parte coperti indirettamente; mancano
  ancora test unitari dedicati per alcune classi piccole ma non critiche.
- `server.model.cards.buildings`
  I building effect principali e quelli di scoring finale hanno test unitari dedicati su reward,
  contatori interni e restore. Una run `PIT` mirata sul package chiude `63/63` mutazioni generate.
- `server.model.cards.events`
  `ShamanRitual`, `CavePainting`, `Sustenance` e `Hunt` hanno ora test unitari mirati oltre ai flow
  test. Una run `PIT` mirata sul package arriva a `52/55` mutazioni uccise; i 3 residui sono
  boundary mutator su clamp a zero di `SustenanceEventCard`, da trattare come probabili equivalenti.

### Coperti soprattutto da integration test o test di supporto

- `server.persistence.savedmatch`
  Flussi setup/turn start/gameplay/late-phase/restore coperti bene a livello integration, inclusi
  restore multipli nella stessa partita fino a `FINAL_SCORING` e `FINISHED`, con scenari
  deterministici a 2, 3, 4 e 5 giocatori.
- `server.persistence.postgres`
  Bootstrap e health path sono coperti da integration test, ma JaCoCo non li rappresenta bene.
- `server.model.ranking`
  Value objects e validator/calculator sono coperti; la parte PostgreSQL è soprattutto integration-driven.

## Priorità reali rimaste

### Priorità alta

- run `PIT` sui package core per trovare test deboli anche con suite verde
- edge case di validazione su `TurnState` e `TurnOrderTrack`
- strategie di scelta ancora più varie nei flow lunghi, oltre alle policy deterministiche attuali

### Priorità media

- `it.polimi.ingsoft.mesos.server.model.GameState.TurnState`
  Coperto, ma si possono aggiungere più test negativi e di consistenza.
- `it.polimi.ingsoft.mesos.server.model.GameState.TurnOrderTrack`
  Coperto, ma mancano alcuni casi di invalid constructor state e boundary order.
- `it.polimi.ingsoft.mesos.server.model.persistence.ServerRuntimeConfig`
  Parzialmente coperto; utile completare casi edge di configurazione.
- `it.polimi.ingsoft.mesos.server.model.persistence.ServerServicesFactory`
  Copertura bassa ma rischio moderato.

### Priorità bassa o non necessaria

- enum semplici e record/DTO puri già attraversati dai test o di valore limitato
- entry point `Main`
- classi banali senza logica autonoma

## Classi che risultano a zero nel CSV ma non sono da interpretare come "non testate"

Queste classi hanno test attivi o sono attraversate da integration test, ma il CSV non le sta misurando bene:

- `server.persistence.savedmatch.PostgresSavedMatchRepository`
- `server.persistence.savedmatch.SavedMatchRestoreFactory`
- `server.persistence.savedmatch.PostgresSavedMatchDatabaseSupport`
- `server.persistence.postgres.PostgresDatabaseBootstrap`
- `server.model.ranking.PostgresRankingService`
- `server.model.ranking.PostgresDatabaseSupport`

## Strategia consigliata per i prossimi step

1. Aggiungere pochi test mirati su `TurnState` e `TurnOrderTrack`
2. Spingere i flow test live-vs-restored su più varietà di scelte e su checkpoint ancora più densi
3. Lasciare la parte PostgreSQL principalmente integration-driven
4. Usare `PIT` sui package core:
   - `server.model.GameState`
   - `server.model.board`
   - `server.model.player`
   - `server.model.lobby`
