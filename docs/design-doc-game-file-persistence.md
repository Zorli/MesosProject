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
