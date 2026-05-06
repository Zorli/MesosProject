# Game File Persistence Design

Related issue: #123

This document defines the planned design for file-based persistence of in-progress
matches. The goal is to make the implementation small, test-driven, easy to review,
and aligned with the current server architecture.

This branch is still a draft and is not ready to merge.

## Goals

- Persist each started match to a readable JSON checkpoint file.
- Restore the latest confirmed game state after a server crash.
- Save only after valid state-changing game actions.
- Keep persistence out of the domain model and behind a small application port.
- Keep the implementation reviewable through explicit DTOs and focused tests.

## Non-Goals

- No Java object serialization through `ObjectOutputStream`.
- No database-backed match persistence.
- No reuse of the removed PostgreSQL saved-match implementation.
- No persistence of pre-match lobby state in the first implementation slice.
- No persistence of sockets, listeners, RMI objects, command queues, threads, or
  live session connections.

## Approved Decisions

- Checkpoints are JSON text files with dedicated DTOs.
- Save failures use strict semantics: the match is frozen and a domain exception
  is raised instead of continuing without a reliable checkpoint.
- Only already-started matches are persisted.
- Finished matches delete their checkpoint after successful finalization.
- Checkpoints live under `MESOS_GAME_PERSISTENCE_DIR` when configured, otherwise
  under `data/game-checkpoints`.

## Why JSON DTOs Instead Of Java Serialization

The persistence file must be readable and reviewable. Java serialization produces
binary output, couples the file to private field layouts, and makes snapshot
compatibility fragile after refactors.

The domain object graph also contains runtime collaborators that must not be
persisted, such as listeners, sessions, sockets, RMI callbacks, queues, and
threads. JSON DTOs create an explicit boundary: only the state needed to rebuild
the match is written, while runtime infrastructure is recreated after restart.

`GamePayload` alone is not enough for restore because it does not contain all
server-side state, including remaining deck order, `TurnState` counters,
`TurnOrderTrack.currentPlayerIndex`, `stateVersion`, and mutable runtime state
inside some building cards.

## Events That Produce Checkpoints

Persistence starts when a game exists. Lobby creation, lobby join, lobby exit,
authentication, lobby listing, pong, and connection registration do not produce
game checkpoints in the first implementation slice.

Checkpoint after these successful state changes:

- `GameOrchestrator.setupMatch`
- `GameOrchestrator.moveTotem`
- `GameOrchestrator.chooseCard`
- `GameOrchestrator.extraDrawn`

`chooseCard` and `extraDrawn` may trigger passive phases inside `Game` before the
action returns. The checkpoint must be written after the full action completes,
including:

- lower/event resolution
- board reset and deck refill
- era changes
- final scoring

Invalid actions must not produce checkpoints. The implementation should compare
`Game.getStateVersion()` before and after the action and checkpoint only when the
version changes.

## Architecture

Persistence belongs to the application layer, not the domain model. `Game` and
related model classes remain free of filesystem knowledge.

The recommended package root for the new feature is:

```text
it.polimi.ingsoft.mesos.server.persistence.game
```

This package is intentionally outside `it.polimi.ingsoft.mesos.server.model`.
The existing `server.model.persistence` package is currently used for runtime
database/ranking services, but new match checkpoint infrastructure should avoid
suggesting that persistence is part of the domain model. The domain model must
not import any persistence package.

Introduce a port:

```java
public interface MatchPersistence {
    List<SavedMatchSnapshot> loadActiveMatches();
    void save(SavedMatchSnapshot snapshot);
    void delete(int gameId);
}
```

Planned implementations:

- `FileMatchPersistence`: reads and writes JSON files under the configured
  checkpoint directory.
- `NoOpMatchPersistence`: default safe implementation for tests or disabled
  persistence.

Supporting components:

- `GameSnapshotMapper`: converts `Game` into `SavedMatchSnapshot`.
- `GameSnapshotRestoreFactory`: rebuilds `Game`, `Board`, `TurnState`,
  `TurnOrderTrack`, `Player`, decks, piles, and offer path from a snapshot.
- `SavedMatchSnapshot`: versioned DTO tree representing the JSON file.

Classes that must not depend on `Path`, `File`, or `Files`:

- `Game`, `Board`, `TurnState`, `TurnOrderTrack`, `Player`, `Tribe`, `Card`,
  `Deck`
- `Lobby`, `ServerModel`, `MessageRouter`
- `VirtualViewTCP`, `VirtualViewRMI`, `PingPongRoutine`, `ConnectionAcceptor`
- `GameOrchestrator`, `ServerController`, and `ServerSessionRegistry` should only
  depend on the persistence port or DTOs, not on filesystem APIs.

## Implementation Blueprint

This section lists the planned production classes and the existing classes that
will need targeted changes. The intent is to keep the implementation easy to
review and aligned with MVC boundaries.

### New Packages And Classes

#### `it.polimi.ingsoft.mesos.server.persistence.game`

- `MatchPersistence`
  - Port used by controllers/application services.
  - Methods:
    - `List<SavedMatchSnapshot> loadActiveMatches()`
    - `void save(SavedMatchSnapshot snapshot)`
    - `void delete(int gameId)`
  - Must not expose filesystem or JSON library types.

- `NoOpMatchPersistence`
  - Null-object implementation.
  - Used by existing tests, disabled persistence mode, and backward-compatible
    constructors.

- `MatchPersistenceConfig`
  - Record containing `enabled`, `checkpointDirectory`, and `schemaVersion`.
  - Built from `ServerRuntimeConfig`.

- `MatchPersistenceException`
  - Base unchecked persistence exception.

- `MatchCheckpointWriteException`
  - Thrown when a save/delete fails.
  - `GameOrchestrator` converts this into strict freeze behavior.

- `MatchCheckpointReadException`
  - Used for unrecoverable read failures.

- `MatchSnapshotValidationException`
  - Used when JSON is readable but semantically invalid.

- `UnsupportedSnapshotVersionException`
  - Used when `schemaVersion` is unsupported.

#### `it.polimi.ingsoft.mesos.server.persistence.game.dto`

DTOs must be records or simple immutable classes containing only primitives,
strings, enum names, lists, maps, and other DTOs. They must not contain `Game`,
`Player`, `Card`, `Listener`, `GamePayload`, or `ClientPayload`.

- `SavedMatchSnapshot`
  - Root checkpoint DTO.
  - Fields: `schemaVersion`, `savedAtUtc`, `game`, `players`, `board`,
    `turnOrder`, `turnState`.

- `GameSnapshot`
  - Fields: `id`, `hostNickname`, `status`, `currentEra`, `stateVersion`.

- `PlayerSnapshot`
  - Fields: `nickname`, `totem`, `food`, `prestigePoints`, `tribe`.

- `TribeSnapshot`
  - Fields: `characters`, `buildings`.

- `BuildingCardSnapshot`
  - Fields: `id`, `runtimeState`.

- `BoardSnapshot`
  - Fields: `upperTribe`, `upperBuildings`, `lowerTribe`, `lowerBuildings`,
    `tribeDeck`, `buildingDeck`, `offerSpaces`, `startingBlocks`.

- `OfferSpaceSnapshot`
  - Fields: `code`, `occupiedBy`.

- `StartingBlockSnapshot`
  - Fields: `position`, `occupiedBy`.

- `TurnOrderSnapshot`
  - Fields: `nicknames`, `currentPlayerIndex`, `roundNumber`, `phase`.

- `TurnStateSnapshot`
  - Fields: `phase`, `remainingUpperChoices`, `remainingLowerChoices`.

#### `it.polimi.ingsoft.mesos.server.persistence.game.mapper`

- `GameSnapshotMapper`
  - Public aggregate mapper.
  - Suggested constructor: `GameSnapshotMapper(Clock clock)`.
  - Suggested method: `SavedMatchSnapshot toSnapshot(Game game, String hostNickname)`.

- `PlayerSnapshotMapper`
  - Package-private helper.

- `BoardSnapshotMapper`
  - Package-private helper for piles, decks, offer path, and starting tile.

- `TurnSnapshotMapper`
  - Package-private helper converting `Player` identity to nickname maps.

- `CardSnapshotMapper`
  - Package-private helper converting cards to ids and runtime state.

#### `it.polimi.ingsoft.mesos.server.persistence.game.restore`

- `GameSnapshotRestoreFactory`
  - Rebuilds a domain `Game` from `SavedMatchSnapshot`.
  - It must rebuild one canonical `Player` instance per nickname and reuse it in
    `Game`, `Board`, `TurnOrderTrack`, `TurnState`, offer spaces, and starting
    blocks.

- `RestoredMatch`
  - Record containing `Game game`, `List<Player> players`, and `String hostNickname`.

- `CardInstanceResolver`
  - Creates fresh card instances from card ids.
  - Validates unknown ids and duplicated card locations.

- `SnapshotValidator`
  - Validates schema, enum values, player references, card duplication,
    turn-order indices, and turn-state counters before restore.

#### `it.polimi.ingsoft.mesos.server.persistence.game.file`

- `FileMatchPersistence`
  - Concrete `MatchPersistence`.
  - Owns JSON serialization and filesystem calls.
  - Suggested constructor:
    - `FileMatchPersistence(Path checkpointDirectory, ObjectMapper objectMapper, Clock clock)`

- `CheckpointFileLocator`
  - Builds checkpoint paths such as `game-<gameId>.json`.
  - Filters temporary and corrupt files during load.

- `AtomicCheckpointWriter`
  - Writes temporary files, flushes/forces data, then atomically moves into place.
  - Must never truncate an existing valid checkpoint before replacement succeeds.

- `CorruptCheckpointQuarantine`
  - Renames invalid checkpoints to `.corrupt-<timestamp>` files.

#### `it.polimi.ingsoft.mesos.server.controller.persistence`

- `RestoredMatchBootstrapper`
  - Application bootstrapper, not a filesystem component.
  - Loads snapshots through `MatchPersistence`, restores games through
    `GameSnapshotRestoreFactory`, creates/registers `GameOrchestrator` instances,
    and registers expected nicknames for reconnect before network listeners start.

### Existing Classes To Change

#### `ServerRuntimeConfig`

File: `src/main/java/it/polimi/ingsoft/mesos/server/model/persistence/ServerRuntimeConfig.java`

Planned changes:

- Add fields:
  - `boolean gamePersistenceEnabled`
  - `String gamePersistenceDirectory` or `Path gamePersistenceDirectory`
- Read environment:
  - `MESOS_GAME_PERSISTENCE_ENABLED`, default `true`
  - `MESOS_GAME_PERSISTENCE_DIR`, default `data/game-checkpoints`
- Add method:
  - `MatchPersistenceConfig toMatchPersistenceConfig()`

This keeps configuration parsing centralized with the existing runtime config.

#### `ServerServices`

File: `src/main/java/it/polimi/ingsoft/mesos/server/model/persistence/ServerServices.java`

Current shape:

```java
public record ServerServices(RankingService rankingService) {}
```

Planned shape:

```java
public record ServerServices(
        RankingService rankingService,
        MatchPersistence matchPersistence
) {}
```

Ranking and game checkpoint persistence must remain independent. No-db mode must
still be able to create a file checkpoint store.

#### `ServerServicesFactory`

File: `src/main/java/it/polimi/ingsoft/mesos/server/model/persistence/ServerServicesFactory.java`

Planned changes:

- Continue creating `RankingService` from DB config.
- Independently create `MatchPersistence` from `MatchPersistenceConfig`.
- Return `NoOpMatchPersistence` when game persistence is disabled.
- Return `FileMatchPersistence` when enabled.

The factory is allowed to know concrete persistence implementations because it is
part of composition/wiring.

#### `LaunchServer`

File: `src/main/java/it/polimi/ingsoft/mesos/server/LaunchServer.java`

Planned changes:

- Actually create `ServerRuntimeConfig` and `ServerServices` during boot.
- Pass `services.rankingService()` and `services.matchPersistence()` into
  `ServerController`.
- Run restored-match bootstrap before starting RMI/TCP listeners.
- Log checkpoint mode, directory, and restored match count.

Network listeners must start only after restore completes.

#### `ServerController`

File: `src/main/java/it/polimi/ingsoft/mesos/server/controller/ServerController.java`

Planned changes:

- Add `MatchPersistence matchPersistence` field.
- Add constructor accepting `GameFactory`, `RankingService`, `MatchPersistence`,
  and `ServerSessionRegistry`.
- Existing constructors delegate to `NoOpMatchPersistence`.
- Pass `matchPersistence` to each new `LobbyController`.
- After successful authentication, check the registry for a resumable restored
  game for that nickname and attach the client if present.
- Keep filesystem and JSON details out of this class.

#### `LobbyController`

File: `src/main/java/it/polimi/ingsoft/mesos/server/controller/LobbyController.java`

Planned changes:

- Add `MatchPersistence matchPersistence` field.
- Constructor receives the port and stores it.
- When creating `GameOrchestrator`, pass `matchPersistence`.
- Do not persist lobby create/join/exit in the first slice.

`LobbyController` remains responsible for transitioning from lobby to game. It
does not know JSON or filesystem details.

#### `GameOrchestrator`

File: `src/main/java/it/polimi/ingsoft/mesos/server/controller/GameOrchestrator/GameOrchestrator.java`

Planned changes:

- Add dependencies:
  - `MatchPersistence matchPersistence`
  - `GameSnapshotMapper snapshotMapper`
- Add constructor overloads that accept these dependencies.
- Existing constructors delegate to `NoOpMatchPersistence` and a default mapper.
- Add a private wrapper:

```java
private void persistAfterStateChange(Runnable action)
```

Behavior:

1. Store `previousVersion = game.getStateVersion()`.
2. Run the action.
3. If `stateVersion` did not change, do nothing.
4. If game is active, save snapshot.
5. If save fails, freeze the match, notify error, and throw
   `MatchPersistenceFailureException`.
6. If game is finished and finalization succeeds, delete checkpoint.

Apply the wrapper to:

- `setupMatch`
- `moveTotem`
- `chooseCard`
- `extraDrawn`

This keeps the checkpoint trigger in the application layer while leaving `Game`
free of persistence calls.

#### `ServerSessionRegistry`

File: `src/main/java/it/polimi/ingsoft/mesos/server/controller/ServerSessionRegistry.java`

Planned changes:

- Add a restored-player index:
  - `Map<String, Integer> restoredGameIdByNickname`
- Add methods:
  - `void registerRestoredGamePlayers(int gameId, List<String> nicknames)`
  - `Optional<Integer> restoredGameIdFor(String nickname)`
  - `void clearRestoredGame(int gameId)`

This supports reconnect without persisting live sessions. It does not read files
and does not know snapshot DTOs.

#### `Game`

File: `src/main/java/it/polimi/ingsoft/mesos/server/model/game/Game.java`

Planned changes:

- Add restore constructor overload that accepts `stateVersion`.
- Ensure restored games start with `listeners = new ArrayList<>()`.
- Add only getters needed by mappers if missing.

No `Game` method should call persistence.

#### Board, Piles, Offer Path, Cards

Potential targeted getter additions may be needed for mapper/restore tests:

- board visible row card ids
- remaining deck card ids already available through `getRemainingTribeDeckCards`
  and `getRemainingBuildingDeckCards`
- offer spaces and starting blocks as read-only views
- pile contents as read-only views

Add getters only when a test demonstrates they are needed. Prefer read-only views
over setters. Restore should use existing restore-friendly constructors where
available.

#### `module-info.java`

Planned changes:

- Export the new persistence API if tests/module boundaries require it.
- Open DTO package to the JSON library if reflective serialization is used.

Jackson is already required by the module. The implementation should prefer an
explicit `ObjectMapper` with records/DTOs and avoid reflection surprises where
practical.

### Classes That Must Not Change For Persistence

These packages should not gain checkpoint-specific logic:

- `server.network`
- `server.virtualview`
- `shared.payload.*`
- concrete card behavior classes, except through existing
  `exportRuntimeState` / `restoreRuntimeState`

Network classes only deliver messages. Shared payloads remain protocol/view DTOs,
not persistence snapshots.

## MVC And Dependency Rules

The persistence design must preserve the current MVC split:

- Model applies game rules and exposes state.
- Controller/orchestrator decides when application events happen.
- Persistence infrastructure stores and restores snapshots.
- Network/virtual view only transports commands and payloads.

Allowed dependency direction:

```text
LaunchServer
  -> controller
  -> network / virtualview
  -> persistence concrete
  -> runtime services

controller
  -> model
  -> persistence API
  -> ranking API
  -> shared payloads

persistence
  -> model
  -> persistence DTOs
  -> JSON/filesystem

model
  -> model subpackages only

network / virtualview
  -> controller
  -> shared payloads
```

Forbidden:

- model importing persistence
- model importing filesystem or JSON libraries
- controller importing `FileMatchPersistence`, `Path`, or `Files`
- persistence importing network or virtual views
- shared payloads importing persistence DTOs
- using `GamePayload` as the checkpoint format

`SystemJoinPayload` already bends the shared/model boundary by carrying a
`Listener`; the persistence feature must not repeat that pattern.

## Runtime Flows

### Starting A Match

1. `LobbyController.startLobby` creates players and a `GameOrchestrator`.
2. The orchestrator receives `MatchPersistence`.
3. `setupMatch` runs after the countdown.
4. If setup changes `Game.stateVersion`, the orchestrator writes the first
   checkpoint.

### After A Valid Game Action

1. `GameOrchestrator` stores `previousVersion = game.getStateVersion()`.
2. It invokes the game action.
3. If `Game.stateVersion` changed, it builds a snapshot.
4. If the game is still active, it calls `matchPersistence.save(snapshot)`.
5. If the game is finished, it runs finalization and deletes the checkpoint after
   successful completion.

### Save Failure

If `matchPersistence.save` fails after a valid state change:

1. Freeze the match.
2. Notify players through the existing error path.
3. Raise a domain-level persistence failure exception.

The in-memory state is not rolled back. The match is frozen because allowing more
actions would break the guarantee that restart restores the latest confirmed
state.

### Server Boot Restore

1. `LaunchServer` builds `ServerServices`, including `MatchPersistence`.
2. Before opening TCP/RMI, the server loads active snapshots.
3. Valid snapshots are rebuilt into `Game` and `GameOrchestrator` instances.
4. Restored orchestrators are registered in `ServerSessionRegistry`.
5. Expected player nicknames are registered for reconnect.
6. Network services start after restore has completed.

### Reconnect

1. A client reconnects and authenticates with its nickname.
2. The server checks whether that nickname belongs to a restored game.
3. If yes, the session enters the restored game.
4. The current listener is attached to the restored `Game`.
5. The server sends the current `GamePayload` to that client.

No old listener, socket, or RMI callback is reused after restart.

### Finished Match

When `GameStatus.FINISHED` is reached:

1. Final scoring has already mutated the game state.
2. Ranking finalization runs if ranking is enabled.
3. After successful finalization, `matchPersistence.delete(gameId)` removes the
   checkpoint.

If finalization fails, the checkpoint remains so the match can be recovered or
investigated.

## Snapshot Shape

The JSON file is a DTO, not a serialized Java object. It stores stable identifiers
and reconstructable state.

Required top-level fields:

- `schemaVersion`
- `savedAtUtc`
- `game`
- `players`
- `board`
- `turnOrder`
- `turnState`

Example shape:

```json
{
  "schemaVersion": 1,
  "savedAtUtc": "2026-05-06T20:45:00Z",
  "game": {
    "id": 7,
    "hostNickname": "Alice",
    "status": "IN_PROGRESS",
    "currentEra": "second",
    "stateVersion": 42
  },
  "players": [
    {
      "nickname": "Alice",
      "totem": "RED",
      "food": 4,
      "prestigePoints": 12,
      "tribe": {
        "characters": [1, 6, 40],
        "buildings": [
          {
            "id": 92,
            "runtimeState": {
              "HUNTER": 1,
              "BUILDER": 0
            }
          }
        ]
      }
    }
  ],
  "board": {
    "upperTribe": [55, 56],
    "upperBuildings": [101],
    "lowerTribe": [30, 70],
    "lowerBuildings": [],
    "tribeDeck": [71, 72, 73],
    "buildingDeck": [102, 103],
    "offerSpaces": [
      {
        "code": "B",
        "occupiedBy": "Alice"
      }
    ],
    "startingBlocks": [
      {
        "position": 1,
        "occupiedBy": null
      },
      {
        "position": 2,
        "occupiedBy": "Bob"
      }
    ]
  },
  "turnOrder": {
    "nicknames": ["Alice", "Bob"],
    "currentPlayerIndex": 0,
    "roundNumber": 4,
    "phase": "PLAYER_DRAWS"
  },
  "turnState": {
    "phase": "WAITING_FOR_CARD_SELECTION",
    "remainingUpperChoices": {
      "Alice": 1
    },
    "remainingLowerChoices": {
      "Alice": 0
    }
  }
}
```

## Identity Rules

The snapshot must never persist `Player` objects as map keys. `Player` currently
uses object identity, so restore must build a single `nickname -> Player` map and
reuse those exact instances in:

- `Game.players`
- `TurnOrderTrack.turnOrder`
- `TurnState` choice maps
- offer spaces
- starting blocks
- `Board.players`

Cards are persisted by `cardId`. Restore recreates card instances from the deck
catalog and places each instance in exactly one location: deck, pile, or player
tribe. Building cards must also restore `runtimeState` through
`BuildingCard.restoreRuntimeState`.

## File Store Rules

- One file per active match: `game-<gameId>.json`.
- Write to a temporary file in the same directory.
- Flush and force file contents before replacing the target.
- Replace with `Files.move(temp, target, ATOMIC_MOVE, REPLACE_EXISTING)` when
  supported.
- Never truncate the current valid checkpoint before the replacement is ready.
- Ignore stale temporary files on boot.
- Quarantine corrupt checkpoint files with a `.corrupt-<timestamp>` suffix.
- A corrupt checkpoint must not prevent other valid matches from loading.
- Unsupported `schemaVersion` fails validation and is quarantined.

## Validation Rules

Restore must reject snapshots with:

- unknown card ids
- duplicated card ids in multiple locations
- turn-order nicknames not present in players
- turn-state counters for unknown players
- occupied offer spaces or starting blocks referencing unknown players
- invalid enum values
- unsupported `schemaVersion`

Rejected snapshots are quarantined, not deleted.

## TDD Plan

### Slice 0: Baseline

- Ensure existing tests compile and run before adding persistence tests.
- Fix unrelated constructor/test drift only if it blocks the persistence work.

### Slice 1: Snapshot Mapper

Add tests for `GameSnapshotMapper` and restore factory:

- `roundTripSetupGamePreservesObservableState`
- `roundTripAfterTotemPlacementPreservesOfferPathAndTurnChoices`
- `roundTripDuringCardSelectionPreservesRemainingRowChoices`
- `roundTripPreservesRemainingDeckOrder`
- `roundTripPreservesOwnedBuildingRuntimeState`

Use existing test helpers such as `GameStateAssertions.assertEquivalent`.

### Slice 2: File Store

Add `FileMatchPersistenceTest` with `@TempDir`:

- `saveWritesReadableJsonCheckpointForGameId`
- `saveReplacesExistingCheckpoint`
- `deleteRemovesCheckpointAndIgnoresMissingFile`
- `loadAllReturnsEmptyWhenDirectoryDoesNotExist`
- `loadAllQuarantinesCorruptCheckpoint`
- `saveDoesNotLeaveTemporaryFileOnSuccess`

### Slice 3: Orchestrator Hook

Add `GameOrchestratorPersistenceTest` with fake stores:

- `setupMatchSavesAfterSuccessfulStateChange`
- `moveTotemSavesAfterSuccessfulStateChange`
- `chooseCardSavesAfterSuccessfulStateChange`
- `extraDrawnSavesAfterSuccessfulStateChange`
- `invalidActionDoesNotSaveCheckpoint`
- `checkpointFailureFreezesMatch`
- `finishedMatchDeletesCheckpoint`

### Slice 4: Restore Flow Without Network

Add a flow test using a temporary directory and no TCP/RMI:

- save an active match
- load it from disk
- rebuild the domain model
- continue with the next legal action
- verify equivalence or expected state progression

### Slice 5: Runtime Wiring

Add config and service factory tests:

- persistence can be disabled with `NoOpMatchPersistence`
- checkpoint directory comes from `MESOS_GAME_PERSISTENCE_DIR`
- default directory is `data/game-checkpoints`
- ranking DB and file checkpoint persistence remain independent

## Implementation Order

1. Define DTOs and mapper tests.
2. Implement snapshot mapper and restore factory.
3. Implement file store and filesystem tests.
4. Add orchestrator hook with strict failure behavior.
5. Wire services and boot restore.
6. Add reconnect handling.

Each step should keep the PR reviewable and should pass tests before moving to
the next slice.
