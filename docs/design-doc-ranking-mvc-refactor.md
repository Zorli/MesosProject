# Design Doc - Ranking MVC/OOP Refactor

## Status

This document describes the target architecture for the ranking module after the
PostgreSQL to MySQL migration branch.

The existing document `docs/design-doc-ranking-db.md` describes the current
implementation. This document describes what must be changed to make the ranking
architecture simpler, cleaner, MVC-compliant, and easier to test.

## Goals

The ranking architecture must:

- respect MVC boundaries;
- keep MySQL as an implementation detail;
- keep domain logic independent from controllers, network, and views;
- use OOP only where it gives a real boundary or removes coupling;
- avoid unnecessary layers, generic abstractions, and framework-like structure;
- make ranking finalization reliable, idempotent, and testable;
- expose ranking results to clients through explicit payloads;
- expose match history as an official ranking read API.

The target is not a heavy clean-architecture rewrite. The target is a small,
clear MVC design with strict responsibilities.

## Current Runtime Behavior

### Server Startup And Wiring

`LaunchServer` builds runtime services through `ServerServicesFactory`. That
factory decides whether ranking is backed by MySQL or by the no-db
implementation, then injects the resulting `RankingService` into
`ServerController`.

The same controller stack is shared by TCP and RMI:

1. TCP clients are represented by `VirtualViewTCP`.
2. RMI clients are represented by `VirtualViewRMI`.
3. Both virtual views register a session in `ServerSessionRegistry`.
4. Incoming client commands become `ClientPayload` instances.
5. `MessageRouter` reads the session phase and forwards the payload to the
   current phase owner.

### Phase Routing

Routing is phase based:

- `PRE_LOBBY` payloads go to `ServerController`;
- `LOBBY` payloads go to the matching `LobbyController`;
- `GAME` payloads go to the matching `GameOrchestrator`.

This is the correct place to preserve MVC simplicity. Ranking queries that are
available before the lobby should be handled as pre-lobby commands by
`ServerController`; ranking finalization should stay in `GameOrchestrator`
because that component already detects the finished game.

### Current Ranking Service Flow

The current `RankingService` exposes:

```java
boolean registerUser(String nickname);
boolean isNicknameAvailable(String nickname);
RankingResult recordMatchAndGetRanking(
        String gameId,
        int playerCount,
        Instant playedAtUtc,
        String requestingNickname,
        List<MatchScore> finalScores
);
List<LeaderboardEntry> getLeaderboard();
List<MatchHistory> getMatchHistory();
List<MatchHistory> getMatchHistory(int playerCount);
```

`MySqlRankingService` currently performs the full write use case directly:

1. normalize the match input;
2. verify the requesting nickname is part of the match;
3. open a write transaction;
4. insert the match header;
5. if the `gameId` already exists, return the current requester rank and the
   current leaderboard;
6. ensure users and rating rows exist;
7. compute placements and Elo updates;
8. persist `user_ratings` and `match_results`;
9. load the requester global rank and the full post-write leaderboard;
10. commit.

The current leaderboard uses this ordering:

```sql
RANK() OVER (ORDER BY elo DESC, wins DESC, nickname ASC)
```

Because `nickname ASC` is part of the window ordering, equal `elo` and `wins`
do not share a rank. If the desired product behavior is tied ranks,
`nickname ASC` should move to the outer display ordering. If the desired
behavior is a unique position for every user, `ROW_NUMBER()` is clearer than
`RANK()`.

### Current Match-End Ranking Flow

`GameOrchestrator` already calls ranking finalization after game-mutating
commands when the game reaches `FINISHED`. It builds final ranking scores from
players, calls `RankingService.recordMatchAndGetRanking(...)`, stores the
returned `RankingResult`, and marks ranking as recorded.

The missing part is the outbound MVC update. The final result is not mapped to a
server payload, not sent through TCP/RMI, and not rendered by the client.

### Current Data Gaps For The Requested UI

The user-facing final match ranking update needs this global information for
each participant:

```text
username
old ranking score -> new ranking score
previous global rank -> new global rank
```

Today:

- old and new Elo exist internally in `PlayerMatchUpdate`;
- old and new Elo are persisted historically as `elo_before`, `elo_delta`, and
  `elo_after`;
- the new rank exists only for the requester in `RankingResult.playerRank`;
- the new rank for all players can be derived from the returned leaderboard;
- the previous global rank for each participant is not computed;
- previous and new global rank are not persisted in `match_results`;
- an idempotent replay cannot reconstruct historical rank movement after later
  matches unless rank movement was persisted.

### Patterns Already In Use

The current application already has useful patterns that should be reused:

- front controller/router: `MessageRouter`;
- controller worker queues: `ServerController`, `LobbyController`,
  `GameOrchestrator`;
- registry: `ServerSessionRegistry`;
- virtual view adapter: `VirtualViewTCP` and `VirtualViewRMI`;
- observer/listener: models notify `Listener.notifyUpdate(Object payload)`;
- service: `RankingService`;
- factory/wiring: `ServerServicesFactory`.

The refactor should build on these patterns. It should not introduce command
class hierarchies, generic repositories, or a routing framework for two ranking
events.

## Current Problems To Fix

### Mixed Responsibilities In `MySqlRankingService`

`MySqlRankingService` currently owns too much:

- public ranking use cases;
- input validation;
- Elo calculation orchestration;
- JDBC connection usage;
- transaction handling;
- SQL statement execution;
- result-set mapping;
- duplicate-key handling;
- history grouping.

This makes the class hard to test without MySQL and makes the controller-facing
model API depend too much on persistence details.

### MySQL Bootstrap Is Not A Real Migration System

The current bootstrap creates tables with `CREATE TABLE IF NOT EXISTS` and then
checks that required table names exist. It does not verify:

- schema version;
- column definitions;
- indexes;
- foreign keys;
- check constraints;
- table engine;
- collation.

This is risky for a migration branch because an old or incorrectly created MySQL
schema can be accepted as healthy.

### Ranking Finalization Is Not Fully Idempotent

The database primary key on `matches.game_id` prevents double counting, but a
replayed successful request is treated as an error. If the first commit succeeds
and the caller loses the response, a retry with the same `gameId` should return
the already recorded result when the payload matches.

### Ranking Domain Rejects Valid Game Scores

The game domain can produce negative prestige points, while ranking DTOs and SQL
constraints currently reject negative final scores. A valid finished match can
therefore fail ranking finalization.

### Ranking Result Does Not Reach The View

`GameOrchestrator` stores the final `RankingResult`, but no server payload,
TCP/RMI callback, `ClientView` method, or TUI renderer exposes it to players.
This violates the MVC flow: the model result is never mapped by the controller
layer to a view-facing update.

### `mvn test` Does Not Exercise MySQL

Database-backed ranking tests are tagged as integration tests and are excluded
from Surefire. The real MySQL gate is `./mvnw verify`, not `./mvnw test`.

## MVC Boundaries

### Model

The model owns game and ranking rules.

Ranking model responsibilities:

- normalize and validate ranking input;
- calculate placement and Elo updates;
- enforce ranking invariants;
- record completed matches;
- expose leaderboard and match history queries;
- hide persistence details behind a repository boundary.

The model must not depend on:

- `server.controller`;
- `server.network`;
- TCP/RMI classes;
- client classes;
- TUI or GUI classes;
- shared server-message payloads used only for rendering.

### Controller

The controller orchestrates application flow.

Controller responsibilities:

- authenticate users;
- create and start lobbies;
- route gameplay commands to `GameOrchestrator`;
- call ranking through `RankingService`;
- map model results to server payloads;
- notify virtual views.

The controller must not:

- execute SQL;
- calculate Elo;
- inspect MySQL errors;
- render text;
- contain TUI/GUI-specific decisions.

### View

Views render payloads.

View responsibilities:

- receive payloads from TCP/RMI adapters;
- render game updates, errors, lobby updates, ranking updates, leaderboard, and
  history;
- parse user commands in the client layer.

Views must not:

- calculate placement or Elo;
- open database connections;
- decide whether a nickname is valid in ranking terms;
- know about repository classes.

## Target Package Structure

Keep the structure small and explicit.

```text
it.polimi.ingsoft.mesos.server.model.ranking
```

Public ranking API and immutable DTOs:

- `RankingService`
- `DefaultRankingService`
- `RankingConfig`
- `CompletedMatch`
- `PlayerFinalScore`
- `RankingFinalizationResult`
- `MatchStanding`
- `LeaderboardEntry`
- `MatchHistory`
- `DuplicateGameException`
- `RankingDataException`

```text
it.polimi.ingsoft.mesos.server.model.ranking.domain
```

Pure domain logic:

- `RankingInputValidator`
- `EloRatingCalculator`
- `RatingSnapshot`
- `PlayerMatchUpdate`

No JDBC, no controller dependencies, no payload dependencies.

```text
it.polimi.ingsoft.mesos.server.model.ranking.persistence
```

Internal persistence port:

- `RankingRepository`
- `RankingTransaction`
- `RankingWork<T>`

This package exists only to keep SQL out of `DefaultRankingService`. It should
not grow into a generic framework.

```text
it.polimi.ingsoft.mesos.server.model.ranking.persistence.mysql
```

MySQL adapter:

- `MySqlRankingRepository`
- `MySqlRankingTransaction`
- `MySqlRankingMapper`
- `MySqlRankingQueries`
- `MySqlRankingSchemaModule`

```text
it.polimi.ingsoft.mesos.server.model.persistence.mysql
```

Shared MySQL infrastructure:

- `MySqlConnectionConfig`
- `MySqlDatabaseSupport`
- `MySqlDatabaseBootstrap`
- `MySqlSchemaModule`

These are not ranking domain concepts and should not live in the ranking root.

## Architecture At A Glance

This feature should stay understandable from three questions:

1. What does the ranking model know?
2. Who asks the ranking model to do work?
3. How does the result reach TCP/RMI clients?

The answer is deliberately small:

```text
Client command / game end
-> controller
-> RankingService
-> domain calculator + repository
-> model DTO result
-> RankingPayloadMapper
-> shared payload
-> VirtualView TCP/RMI
-> ClientView / TUI
```

### Minimal Class Set

The design should use the following minimum set of classes. New classes should
be added only when they remove real coupling or keep a boundary clean.

| Area | Class | Pattern | Why It Exists |
|---|---|---|---|
| Model API | `RankingService` | Service | Single controller-facing entry point for ranking use cases. |
| Model API | `DefaultRankingService` | Service implementation | Coordinates validation, Elo calculation, idempotency, and persistence. |
| Model DTO | `CompletedMatch` | Immutable DTO / command object | Carries one finished match into ranking. |
| Model DTO | `PlayerFinalScore` | Immutable DTO | Carries one player's final game score. |
| Model DTO | `RankingFinalizationResult` | Immutable DTO | Returns match-end ranking movement and post-match leaderboard. |
| Model DTO | `MatchStanding` | Immutable DTO | Describes one player's match result and global rank movement. |
| Model DTO | `LeaderboardEntry` | Immutable DTO | Describes one row in the current global leaderboard. |
| Domain | `RankingInputValidator` | Validator | Normalizes and validates nicknames, game ids, player counts, and scores. |
| Domain | `EloRatingCalculator` | Domain service | Pure Elo/placement calculation with no I/O. |
| Domain | `RatingSnapshot` | Immutable DTO | Pre-match rating state loaded from persistence. |
| Domain | `PlayerMatchUpdate` | Immutable DTO | Computed post-match write model for one player. |
| Persistence port | `RankingRepository` | Repository | Keeps JDBC/MySQL out of the service. |
| Persistence port | `RankingTransaction` | Transaction script boundary | Exposes only transaction-scoped ranking operations. |
| MySQL adapter | `MySqlRankingRepository` | Repository adapter | Opens transactions, retries whole transactions, translates persistence errors. |
| MySQL adapter | `MySqlRankingTransaction` | Transaction script | Executes SQL for one ranking transaction. |
| MySQL adapter | `MySqlRankingMapper` | Mapper | Converts SQL rows to model DTOs. |
| Controller | `RankingPayloadMapper` | Mapper | Converts model DTOs to shared TCP/RMI payloads. |
| Shared payload | `GlobalRankingPayload` | DTO | Response for the pre-lobby global ranking API. |
| Shared payload | `FinalMatchRankingPayload` | DTO | Server event for end-of-match ranking movement. |
| Shared payload | `MatchHistoryPayload` | DTO | Response for ranking match-history queries. |

### Classes We Should Not Add

Avoid these unless a future feature creates a clear need:

- `GetRankingCommand`, `RecordRankingCommand`, or similar command classes: the
  existing payload enum plus controller switch is enough;
- one service per query, such as `LeaderboardService` or `MatchHistoryService`;
- generic `Repository<T>` abstractions;
- generic ORM-like entities for `users`, `matches`, or `match_results`;
- ranking observers/listeners inside the model service;
- payload classes inside `server.model.ranking`;
- SQL or MySQL error handling inside controllers.

### Dependency Rule Of Thumb

Every class should be easy to place by dependency direction:

- if it calculates ranking, it belongs in `server.model.ranking.domain`;
- if it coordinates a ranking use case, it belongs behind `RankingService`;
- if it talks to MySQL, it belongs in the MySQL persistence adapter;
- if it creates TCP/RMI payloads, it belongs in the controller boundary;
- if it renders text, it belongs in the client view layer.

## Dependency Direction

Dependencies flow in one direction:

```text
controller -> model.ranking public API
DefaultRankingService -> ranking.domain + ranking.persistence
MySqlRankingRepository -> ranking.persistence + shared MySQL support
client views -> shared payloads
```

Forbidden dependencies:

```text
ranking -> controller
ranking -> network
ranking -> client
ranking.domain -> persistence
controller -> mysql adapter
view -> ranking persistence
```

`module-info.java` should export only the packages that external modules really
need. Domain and persistence implementation packages should remain internal
unless tests require a deliberate exception.

## OOP Patterns Used

The refactor should use a small set of explicit patterns. Each pattern must have
a concrete reason in the codebase.

### Service

`RankingService` is the controller-facing model service.

Purpose:

- expose ranking use cases to controllers;
- hide domain workflow and persistence coordination;
- keep controller code independent from MySQL.

Implementation:

- `DefaultRankingService`

Do not create one service per method. One ranking service is enough.

### Repository

`RankingRepository` is an internal persistence boundary.

Purpose:

- remove JDBC and SQL from `DefaultRankingService`;
- make ranking use-case tests possible with a fake repository;
- keep transaction handling in one persistence component.

Implementation:

- `MySqlRankingRepository`

The repository should expose ranking-specific operations, not generic CRUD.

### Transaction Script

`RankingTransaction` is a narrow transaction object passed to a repository
callback.

Purpose:

- keep snapshot loading, Elo computation, rating updates, history inserts, and
  final reads inside one transaction;
- avoid leaking `Connection` to the service.

Implementation:

- `MySqlRankingTransaction`

### Mapper

`MySqlRankingMapper` maps SQL rows to domain DTOs.

Purpose:

- keep `ResultSet` parsing out of the repository workflow;
- centralize UTC timestamp conversion;
- centralize match-history grouping.

### Immutable DTO

All public ranking data crossing package boundaries should be immutable records.

Examples:

- `CompletedMatch`
- `PlayerFinalScore`
- `RankingFinalizationResult`
- `MatchStanding`
- `LeaderboardEntry`
- `MatchHistory`

DTO records validate their own local invariants and defensively copy lists.

### Factory/Wiring

`ServerServicesFactory` remains the composition point for runtime services.

Purpose:

- choose no-db or MySQL-backed ranking at startup;
- keep controller constructors simple;
- avoid service construction inside controllers.

No dependency-injection framework is needed.

## Public Ranking API

The controller-facing service should be simple:

```java
public interface RankingService {
    boolean registerUser(String nickname);
    boolean isNicknameAvailable(String nickname);
    RankingFinalizationResult recordCompletedMatch(CompletedMatch match);
    List<LeaderboardEntry> getLeaderboard();
    List<MatchHistory> getMatchHistory();
    List<MatchHistory> getMatchHistory(int playerCount);
}
```

`CompletedMatch` should carry one coherent aggregate:

```java
public record CompletedMatch(
        String gameId,
        Instant playedAtUtc,
        List<PlayerFinalScore> finalScores
) {
}
```

`playerCount` should be derived from `finalScores.size()`. It should not be
passed separately because redundant inputs can disagree.

`RankingFinalizationResult` should describe the completed match and the
transactional rank movement needed by the view:

```java
public record RankingFinalizationResult(
        String gameId,
        boolean recordedNow,
        List<MatchStanding> standings,
        List<LeaderboardEntry> leaderboard
) {
}
```

`standings` is the match-local and ranking-delta view for the match
participants. `leaderboard` is the post-match global ranking snapshot returned
by the same use case so the controller does not need a second read to publish
the final match update. The explicit pre-lobby global ranking API still calls
`getLeaderboard()` directly.

## Class Catalog

### Public Model API

#### `RankingService`

Type: interface.

Pattern: Service.

Owned by: model.

Used by: `ServerController`, `LobbyController`, `GameOrchestrator`.

Methods:

```java
boolean registerUser(String nickname);
boolean isNicknameAvailable(String nickname);
RankingFinalizationResult recordCompletedMatch(CompletedMatch match);
List<LeaderboardEntry> getLeaderboard();
List<MatchHistory> getMatchHistory();
List<MatchHistory> getMatchHistory(int playerCount);
```

Rules:

- no SQL exceptions leak from this interface;
- invalid input throws `IllegalArgumentException`;
- data/state inconsistencies throw `RankingDataException`;
- duplicate conflicting matches throw a ranking-specific conflict exception.

#### `DefaultRankingService`

Type: final class.

Pattern: Service implementation.

Responsibilities:

- normalize input;
- enforce use-case preconditions;
- call `EloRatingCalculator`;
- call `RankingRepository`;
- decide idempotent replay semantics;
- return immutable result DTOs.

Constructor:

```java
DefaultRankingService(
        RankingConfig config,
        RankingRepository repository,
        RankingInputValidator validator,
        EloRatingCalculator eloCalculator
)
```

Convenience constructors may create `RankingInputValidator` and
`EloRatingCalculator` from `RankingConfig`, but the repository must be injected.

Forbidden:

- `Connection`;
- `PreparedStatement`;
- SQL strings;
- MySQL error codes;
- TCP/RMI payloads.

#### `CompletedMatch`

Type: record.

Purpose: coherent command object for a finished match.

Fields:

```java
String gameId;
Instant playedAtUtc;
List<PlayerFinalScore> finalScores;
```

Validation:

- `gameId` not null/blank;
- `playedAtUtc` not null;
- `finalScores` size between configured min/max players;
- unique normalized nicknames.

`playerCount` is derived from `finalScores.size()`.

#### `PlayerFinalScore`

Type: record.

Fields:

```java
String nickname;
int finalScore;
```

Rules:

- nickname is normalized through the validator;
- score is signed and can be negative.

This replaces or renames current `MatchScore`. If keeping `MatchScore` is less
disruptive, update its semantics to signed scores and document it as the final
score DTO.

#### `RankingFinalizationResult`

Type: record.

Fields:

```java
String gameId;
boolean recordedNow;
List<MatchStanding> standings;
List<LeaderboardEntry> leaderboard;
```

Rules:

- `recordedNow=true` when the transaction inserted a new match;
- `recordedNow=false` when an exact duplicate replay was found;
- standings include every player, not only the host/requester;
- standings include old/new ranking score and previous/new global rank;
- leaderboard is the post-match global ranking snapshot.

#### `MatchStanding`

Type: record.

Fields:

```java
String nickname;
int finalScore;
int placement;
int oldScore;
int newScore;
int scoreDelta;
int previousGlobalRank;
int newGlobalRank;
boolean won;
int winStreakAfter;
```

Rules:

- one standing per player;
- ordered by placement, then nickname for deterministic rendering;
- `finalScore` is the game score used to rank the match;
- `oldScore` and `newScore` are the global ranking scores, currently Elo;
- `scoreDelta` must be the actual persisted score change, equal to
  `newScore - oldScore`;
- `previousGlobalRank` and `newGlobalRank` are global leaderboard positions;
- derived from persisted match result rows.

#### `LeaderboardEntry`

Type: record.

Fields stay close to current implementation:

```java
String nickname;
int elo;
int wins;
int winStreak;
int rank;
```

Rules:

- read model only;
- no persistence or controller references.

#### `MatchHistory`

Type: record.

Fields:

```java
String gameId;
Instant playedAtUtc;
int playerCount;
List<MatchStanding> standings;
```

Stored standings must include previous and new global rank if exact replay
responses or historical rank movement are required.

This should reuse `MatchStanding` rather than a second almost-identical
historical row type, unless audit requirements need extra fields.

### Domain Internals

#### `RankingInputValidator`

Type: final class.

Responsibilities:

- normalize nicknames;
- normalize game ids;
- validate completed match shape;
- validate player count filters.

Methods:

```java
String normalizeNickname(String nickname);
String normalizeGameId(String gameId);
CompletedMatch normalizeCompletedMatch(CompletedMatch match);
int validatePlayerCount(int playerCount);
```

It should not know about SQL or payloads.

#### `EloRatingCalculator`

Type: final class.

Responsibilities:

- compute placements;
- compute expected score;
- compute actual score;
- compute Elo deltas;
- compute win/win-streak updates.

Methods:

```java
Map<String, Integer> computePlacements(List<PlayerFinalScore> scores);
List<PlayerMatchUpdate> computeUpdates(
        List<PlayerFinalScore> scores,
        Map<String, Integer> placements,
        Map<String, RatingSnapshot> snapshots
);
```

The calculator is pure. Given the same inputs, it returns the same outputs and
does not mutate the database.

#### `RatingSnapshot`

Type: record.

Purpose: pre-match aggregate rating state.

Fields:

```java
String nickname;
int elo;
int gamesPlayed;
int wins;
int winStreak;
```

#### `PlayerMatchUpdate`

Type: record.

Purpose: computed write model for one player's post-match rating and history
row.

Fields:

```java
String nickname;
int finalScore;
int placement;
int oldScore;
int newScore;
int scoreDelta;
int gamesAfter;
int winsAfter;
int winStreakAfter;
```

### Persistence Port

#### `RankingRepository`

Type: package-private interface.

Responsibilities:

- expose transaction callbacks;
- expose simple read-only queries when no write transaction is needed.

Methods:

```java
<T> T inWriteTransaction(RankingWork<T> work);
<T> T inReadTransaction(RankingWork<T> work);
```

Alternative acceptable shape:

```java
RankingFinalizationResult recordCompletedMatch(
        CompletedMatch match,
        RankingComputation computation
);
```

Use the callback shape if the Elo computation must stay visibly inside the same
transaction.

#### `RankingTransaction`

Type: package-private interface.

Responsibilities:

- expose the minimum operations needed by `DefaultRankingService`;
- hide JDBC.

Methods:

```java
Optional<StoredMatch> findStoredMatch(String gameId);
void insertMatchHeader(CompletedMatch match);
void ensureUsersAndRatingsExist(List<PlayerFinalScore> scores);
Map<String, RatingSnapshot> loadRatingSnapshotsForUpdate(List<String> nicknames);
Map<String, Integer> loadGlobalRanks(List<String> nicknames);
void saveMatchUpdates(String gameId, List<PlayerMatchUpdate> updates);
void saveGlobalRankMovement(
        String gameId,
        Map<String, Integer> previousRanks,
        Map<String, Integer> newRanks
);
List<LeaderboardEntry> loadLeaderboard();
List<MatchHistory> loadHistory();
List<MatchHistory> loadHistory(int playerCount);
```

#### `StoredMatch`

Type: package-private record.

Purpose: data loaded for duplicate `gameId` handling.

Fields:

```java
String gameId;
Instant playedAtUtc;
int playerCount;
List<MatchStanding> standings;
```

It is not part of the public API unless clients need explicit replay/audit
metadata.

### MySQL Adapter

#### `MySqlRankingRepository`

Type: final class.

Responsibilities:

- open connections through `MySqlDatabaseSupport`;
- start/commit/rollback transactions;
- retry transient MySQL failures around whole transactions;
- translate duplicate-key conflicts into ranking exceptions;
- create `MySqlRankingTransaction`.

It must not calculate Elo.

#### `MySqlRankingTransaction`

Type: final class.

Responsibilities:

- execute SQL statements for one transaction;
- lock rating rows in deterministic nickname order;
- batch update ratings;
- batch insert match results;
- load leaderboard/history.

It receives a caller-owned `Connection` and never closes it directly.

#### `MySqlRankingMapper`

Type: final class.

Responsibilities:

- map `ResultSet` rows to DTOs;
- convert `DATETIME(6)` to `Instant` in UTC;
- group match-history rows.

#### `MySqlRankingQueries`

Type: final utility class.

Responsibilities:

- DDL and DML constants only.

No dynamic SQL construction should be spread outside the MySQL adapter. If an
`IN (...)` clause is required, build it in one repository helper.

#### `MySqlRankingSchemaModule`

Type: final class implementing `MySqlSchemaModule`.

Responsibilities:

- expose ranking DDL or migration metadata;
- expose required object names for health checks.

Schema creation should be owned by bootstrap, not by service constructors.

### Controller And Payload Classes

#### `RankingPayloadMapper`

Type: final class under `server.controller`.

Responsibilities:

- convert ranking model DTOs into shared server payloads;
- keep shared payload construction out of model classes;
- keep protocol naming stable even if the internal ranking score remains Elo.

Methods:

```java
GlobalRankingPayload globalRanking(List<LeaderboardEntry> entries);

FinalMatchRankingPayload finalMatchRanking(RankingFinalizationResult result);

FinalMatchRankingPayload rankingDisabled(String gameId);

FinalMatchRankingPayload rankingFailed(String gameId, String message);

MatchHistoryPayload history(List<MatchHistory> matches);
```

#### `ListGlobalRankingPayload`

Type: shared client payload.

Purpose: explicit client request for the full global ranking before entering a
lobby.

The payload has no fields for the first implementation:

```java
public record ListGlobalRankingPayload() implements ClientPayload {
}
```

Protocol type:

```java
ClientMessageType.LIST_GLOBAL_RANKING
```

#### `GlobalRankingPayload`

Type: shared server payload.

Purpose: response for the pre-lobby full global ranking API.

Fields:

```java
List<GlobalRankingEntryPayload> entries;
```

Protocol type:

```java
ServerMessageType.GLOBAL_RANKING
```

#### `GlobalRankingEntryPayload`

Type: shared server payload row.

Fields:

```java
String username;
int score;
int globalRank;
int wins;
int winStreak;
```

Mapping rules:

- `LeaderboardEntry.nickname()` maps to `username`;
- `LeaderboardEntry.elo()` maps to `score`;
- `LeaderboardEntry.rank()` maps to `globalRank`.

#### `FinalMatchRankingPayload`

Type: shared server payload.

Purpose: automatic update sent to all match participants when the game ends and
ranking finalization completes.

Fields:

```java
String gameId;
FinalMatchRankingStatus status;
List<FinalMatchRankingDeltaPayload> deltas;
String message;
```

Protocol type:

```java
ServerMessageType.FINAL_MATCH_RANKING
```

`message` is used only for disabled/failure states. `deltas` is empty when the
status is `DISABLED` or `FAILED`.

#### `FinalMatchRankingStatus`

Type: enum.

Values:

```java
RECORDED
ALREADY_RECORDED
DISABLED
FAILED
```

#### `FinalMatchRankingDeltaPayload`

Type: shared server payload row.

Fields exactly match the final UI requirement:

```java
String username;
int oldScore;
int newScore;
int previousGlobalRank;
int newGlobalRank;
```

Optional fields that can be added if the TUI/GUI needs richer rendering:

```java
int finalScore;
int placement;
int scoreDelta;
```

The first renderer can display each row as:

```text
Alice score 1000 -> 1024 rank 5 -> 2
```

#### `MatchHistoryPayload`

Type: shared server payload.

Fields:

```java
List<MatchHistoryEntryPayload> matches;
```

History is already part of the ranking service API. It is not required for the
two requested ranking APIs, but the mapper can keep supporting it because the
read model already exists.

### Client View Integration

Add methods to the client-facing view abstraction:

```java
void showGlobalRanking(GlobalRankingPayload payload);
void showFinalMatchRanking(FinalMatchRankingPayload payload);
void showMatchHistory(MatchHistoryPayload payload);
```

TCP and RMI virtual servers only dispatch payloads. TUI/GUI render them.


## Domain Rules

### Final Scores

Final scores are signed integers.

Negative scores are valid because the game domain can produce negative prestige
points. Higher score always ranks better, including negative values.

Required changes:

- remove `finalScore >= 0` rejection from ranking DTOs;
- remove or adjust SQL `CHECK(final_score >= 0)`;
- add tests for negative-score placement and persistence.

### Placement

Placement uses competition ranking:

```text
30, 25, 25, 10 -> 1, 2, 2, 4
```

Ties are deterministic in stored order, but tied players share the same
placement. Nickname ordering may be used only to make row ordering stable.

### Winners

All players with `placement == 1` are winners.

They increment:

- `wins`;
- `win_streak`.

Other players reset `win_streak`.

### Elo

Elo should be based on expected score versus actual pairwise placement outcome.

The current win-streak bonus changes Elo directly. This must be an explicit
product decision:

- preferred target: keep win streak as a statistic, not as an Elo bonus;
- acceptable alternative: keep the bonus but document the system as
  bonus-adjusted rating rather than standard Elo.

The design should not hide this inside the calculator.

### Aggregate Invariants

The domain and database should enforce:

- `elo >= minElo`;
- `gamesPlayed >= 0`;
- `wins >= 0`;
- `winStreak >= 0`;
- `wins <= gamesPlayed`;
- `winStreak <= gamesPlayed`;
- one match result row per player in the completed match.

## Persistence Design

### Repository Boundary

`DefaultRankingService` owns ranking use cases. `MySqlRankingRepository` owns
SQL.

The service should not contain:

- `Connection`;
- `PreparedStatement`;
- `ResultSet`;
- SQL strings;
- MySQL error codes.

The repository should expose operations that match the ranking use case, not
generic CRUD.

Example shape:

```java
interface RankingRepository {
    <T> T inWriteTransaction(RankingWork<T> work);
    <T> T inReadTransaction(RankingWork<T> work);
}
```

The transaction object exposes only ranking-specific operations:

```java
interface RankingTransaction {
    void insertMatchHeader(CompletedMatch match);
    Optional<StoredMatch> findStoredMatch(String gameId);
    void ensureUsersAndRatingsExist(List<PlayerFinalScore> scores);
    Map<String, RatingSnapshot> loadRatingSnapshotsForUpdate(List<String> nicknames);
    void saveMatchUpdates(String gameId, List<PlayerMatchUpdate> updates);
    List<LeaderboardEntry> loadLeaderboard();
    List<MatchHistory> loadHistory();
    List<MatchHistory> loadHistory(int playerCount);
}
```

The exact method names can change during implementation, but the boundary should
stay clear: service coordinates, repository persists.

### Transaction Rules

`registerUser` runs in one short write transaction:

1. normalize nickname;
2. insert user if missing;
3. ensure initial rating row exists;
4. commit.

`recordCompletedMatch` runs in one write transaction:

1. normalize and validate `CompletedMatch`;
2. insert match header;
3. if duplicate `gameId`, execute idempotent replay handling;
4. ensure users and ratings exist;
5. lock involved rating rows in deterministic nickname order;
6. compute placement and Elo in memory;
7. update `user_ratings`;
8. insert `match_results`;
9. commit;
10. return `RankingFinalizationResult`.

Retries wrap the whole transaction. Retrying individual statements is not
enough.

Read-only leaderboard and history queries should not use blanket
`SERIALIZABLE`; short read-only transactions with a lower isolation level are
enough unless a specific consistency need appears.

### Idempotent Duplicate Game Handling

On duplicate `gameId`:

1. load existing `matches` and `match_results`;
2. compare normalized player count and `(nickname, finalScore)` rows with the
   request;
3. if they match, return a `RankingFinalizationResult` with `recordedNow=false`;
4. if they differ, throw a conflict exception and leave data unchanged.

For a perfectly repeatable response, persist enough match standings to rebuild
the original result from `match_results`.

## Schema And Bootstrap

Target tables remain:

- `users`;
- `user_ratings`;
- `matches`;
- `match_results`.

Recommended schema changes:

- add `schema_migrations(version, description, checksum, applied_at)`;
- add checks on `user_ratings` counters;
- allow signed `match_results.final_score`;
- persist `match_results.previous_global_rank`;
- persist `match_results.new_global_rank`;
- verify `InnoDB`;
- verify `utf8mb4_bin`;
- verify foreign keys, indexes, and check constraints.

`MySqlDatabaseBootstrap` should be the only component that:

- creates the database;
- applies migrations;
- verifies schema health.

Service constructors should not run DDL. This avoids duplicated bootstrap
ownership.

## Server And Client Integration

### API 1 - Pre-Lobby Full Global Ranking

The first API lets an authenticated user request the full global leaderboard
while still in pre-lobby. It is a normal pre-lobby command, not a special router
bypass. Keeping it phase-scoped avoids spreading ranking reads across lobby and
game controllers.

Inbound flow:

```text
TUI command `ranking`
-> ServerProxy.listGlobalRanking()
-> ClientMessageType.LIST_GLOBAL_RANKING
-> ListGlobalRankingPayload
-> MessageRouter routes PRE_LOBBY to ServerController
-> ServerController.listGlobalRanking(sessionId)
-> RankingService.getLeaderboard()
-> RankingPayloadMapper.globalRanking(entries)
-> Listener.notifyUpdate(GlobalRankingPayload)
```

Required server additions:

- add `LIST_GLOBAL_RANKING` to `ClientMessageType`;
- add `ListGlobalRankingPayload`;
- add deserialization in `ClientCommandHandler`;
- add `LIST_GLOBAL_RANKING` to `PreLobbyCommandType`;
- add a `ServerController` handler that reads `RankingService.getLeaderboard()`;
- map `LeaderboardEntry` to `GlobalRankingPayload` through
  `RankingPayloadMapper`.

Required transport additions:

- add `GLOBAL_RANKING` to `ServerMessageType`;
- add `VirtualViewTCP.notifyUpdate` handling for `GlobalRankingPayload`;
- add `RmiClientService.handleGlobalRanking(GlobalRankingPayload payload)`;
- add `VirtualViewRMI.notifyUpdate` handling for `GlobalRankingPayload`;
- add TCP client dispatch in `VirtualServerTCP`;
- add RMI client callback handling in `VirtualServerRMI`.

Required client additions:

- add `ServerProxy.listGlobalRanking()`;
- implement it in `ServerProxyTCP` and `ServerProxyRMI`;
- add `ClientView.showGlobalRanking(GlobalRankingPayload payload)`;
- add TUI parser aliases: `ranking`, `leaderboard`, `global-ranking`;
- add a renderer for empty and non-empty leaderboard states.

Suggested TCP request:

```json
{"type":"LIST_GLOBAL_RANKING","payload":{}}
```

Suggested TCP response:

```json
{
  "type":"GLOBAL_RANKING",
  "payload":{
    "entries":[
      {"username":"Alice","score":1042,"globalRank":1,"wins":7,"winStreak":2}
    ]
  }
}
```

### API 2 - Automatic Final Match Ranking

The second API is not a client command. It is a server event emitted
automatically when a game finishes and ranking finalization completes.

Inbound/finalization flow:

```text
Game command mutates Game
-> Game reaches FINISHED
-> GameOrchestrator.recordRankingIfFinished()
-> RankingService.recordCompletedMatch(CompletedMatch)
-> RankingFinalizationResult with per-player rank movement
-> RankingPayloadMapper.finalMatchRanking(result)
-> notify all match participants with FinalMatchRankingPayload
```

`GameOrchestrator` is the correct trigger because it already owns game-end
orchestration. The ranking service must not notify views directly; it only
returns model DTOs. The controller maps those DTOs to shared payloads.

`RankingService.recordCompletedMatch(...)` must compute rank movement
transactionally:

1. normalize and validate the completed match;
2. ensure users and ratings exist;
3. load previous global ranks for all match participants;
4. lock rating snapshots for the involved users in deterministic order;
5. compute placement and ranking score updates;
6. persist rating updates and match results;
7. load new global ranks for all match participants;
8. persist previous/new global ranks in `match_results`;
9. load the post-match leaderboard;
10. return `RankingFinalizationResult`.

Required server additions:

- replace or extend current `RankingResult` with `RankingFinalizationResult`;
- ensure `MatchStanding` contains `oldScore`, `newScore`,
  `previousGlobalRank`, and `newGlobalRank`;
- add `FinalMatchRankingPayload` and `FinalMatchRankingDeltaPayload`;
- add `FINAL_MATCH_RANKING` to `ServerMessageType`;
- add `GameOrchestrator.notifyFinalMatchRanking(...)`;
- send the payload to all match participants, not only the host/requester.

Suggested TCP event:

```json
{
  "type":"FINAL_MATCH_RANKING",
  "payload":{
    "gameId":"42",
    "status":"RECORDED",
    "deltas":[
      {
        "username":"Alice",
        "oldScore":1000,
        "newScore":1024,
        "previousGlobalRank":5,
        "newGlobalRank":2
      }
    ],
    "message":null
  }
}
```

Suggested TUI rendering:

```text
[SERVER][MATCH RANKING] game=42
Alice score 1000 -> 1024 rank 5 -> 2
Bob score 1000 -> 986 rank 5 -> 8
```

### API 3 - Match History

The third API lets an authenticated user inspect completed ranking matches. It
is a read-only ranking API and follows the same MVC rule as global ranking:
controller reads through `RankingService`, mapper converts to shared payload,
client renders only the payload.

Inbound flow:

```text
TUI command `history` or `history 4`
-> ServerProxy.listMatchHistory(optionalPlayerCount)
-> ClientMessageType.LIST_MATCH_HISTORY
-> ListMatchHistoryPayload
-> MessageRouter routes PRE_LOBBY to ServerController
-> ServerController.listMatchHistory(sessionId, playerCount)
-> RankingService.getMatchHistory() or getMatchHistory(playerCount)
-> RankingPayloadMapper.history(matches)
-> Listener.notifyUpdate(MatchHistoryPayload)
```

Required server additions:

- add `LIST_MATCH_HISTORY` to `ClientMessageType`;
- add `ListMatchHistoryPayload(Integer playerCount)`;
- add deserialization in `ClientCommandHandler`;
- add `LIST_MATCH_HISTORY` to `PreLobbyCommandType`;
- add a `ServerController` handler that reads `RankingService.getMatchHistory`;
- map `MatchHistory` to `MatchHistoryPayload` through `RankingPayloadMapper`.

Required transport additions:

- add `MATCH_HISTORY` to `ServerMessageType`;
- add `VirtualViewTCP.notifyUpdate` handling for `MatchHistoryPayload`;
- add `RmiClientService.handleMatchHistory(MatchHistoryPayload payload)`;
- add `VirtualViewRMI.notifyUpdate` handling for `MatchHistoryPayload`;
- add TCP client dispatch in `VirtualServerTCP`;
- add RMI client callback handling in `VirtualServerRMI`.

Required client additions:

- add `ServerProxy.listMatchHistory(Optional<Integer> playerCount)`;
- implement it in `ServerProxyTCP` and `ServerProxyRMI`;
- add `ClientView.showMatchHistory(MatchHistoryPayload payload)`;
- add TUI parser aliases: `history`, `match-history`, `ranking-history`;
- support optional player-count filter, for example `history 4`;
- render empty, unfiltered, and filtered history states.

Suggested TCP request without filter:

```json
{"type":"LIST_MATCH_HISTORY","payload":{}}
```

Suggested TCP request with player-count filter:

```json
{"type":"LIST_MATCH_HISTORY","payload":{"playerCount":4}}
```

Suggested TCP response:

```json
{
  "type":"MATCH_HISTORY",
  "payload":{
    "matches":[
      {
        "gameId":"42",
        "playedAtUtc":"2026-05-06T19:30:00Z",
        "playerCount":4,
        "standings":[
          {"username":"Alice","finalScore":32,"placement":1,"oldScore":1000,"newScore":1024,"previousGlobalRank":5,"newGlobalRank":2}
        ]
      }
    ]
  }
}
```

### Nickname Registration

The current login flow enforces only active-session uniqueness in memory.

The target design must explicitly choose one policy:

- persistent reservation: `registerUser` is called during authentication and a
  nickname belongs permanently to that ranking identity;
- session-only login: ranking auto-creates users at match finalization and
  persistent nickname registration remains out of authentication.

For ranking consistency, persistent reservation is preferred. Logout should free
the active session, not delete the ranking identity.

### Failure And Disabled Mode

When ranking is disabled, finalization should not silently disappear from the
client perspective. The server should either:

- send `FinalMatchRankingPayload(DISABLED)`;
- or document that ranking UI is hidden in no-db mode.

When ranking finalization fails:

- persisted data must not be partially applied;
- the match should be frozen only if the product requires ranking to be
  mandatory;
- the failure must be mapped to a clear protocol-facing error.

## Implementation Plan

### Phase 1 - Domain Corrections

- Allow signed final scores in DTOs and SQL.
- Add negative-score tests.
- Decide whether win streak modifies Elo or remains a statistic.
- Add domain tests for placement, ties, shared first place, and aggregate
  invariants.

### Phase 2 - Service/Persistence Split

- Introduce `DefaultRankingService`.
- Introduce internal `RankingRepository` and transaction boundary.
- Extract SQL into `MySqlRankingRepository`, `MySqlRankingTransaction`, and
  `MySqlRankingMapper`.
- Keep `MySqlRankingService` temporarily as a compatibility wrapper or remove it
  after callers are migrated.
- Update `ServerServicesFactory` to build the new service.

### Phase 3 - MySQL Bootstrap Hardening

- Move all DDL ownership to bootstrap.
- Add `schema_migrations`.
- Add schema health checks for columns, indexes, constraints, engine, and
  collation.
- Remove constructor-time schema initialization from ranking service classes.

### Phase 4 - Idempotency And Rank Movement

- Implement duplicate `gameId` replay detection.
- Return `recordedNow=false` for exact replay.
- Throw conflict for same `gameId` with different payload.
- Compute previous global rank before rating updates.
- Compute new global rank after rating updates.
- Persist previous/new global rank in `match_results`.
- Add integration tests for replay, conflict, and historical rank movement.

### Phase 5 - MVC Payload Integration

- Add `ListGlobalRankingPayload` and `ClientMessageType.LIST_GLOBAL_RANKING`.
- Add `ListMatchHistoryPayload` and `ClientMessageType.LIST_MATCH_HISTORY`.
- Add `GlobalRankingPayload` and `ServerMessageType.GLOBAL_RANKING`.
- Add `FinalMatchRankingPayload` and `ServerMessageType.FINAL_MATCH_RANKING`.
- Add `MatchHistoryPayload` and `ServerMessageType.MATCH_HISTORY`.
- Add TCP and RMI dispatch for all ranking server payloads.
- Add `ServerProxy.listGlobalRanking()`.
- Add `ServerProxy.listMatchHistory(Optional<Integer> playerCount)`.
- Add `ClientView.showGlobalRanking(...)`,
  `ClientView.showFinalMatchRanking(...)`, and
  `ClientView.showMatchHistory(...)`.
- Add TUI command aliases: `ranking`, `leaderboard`, `global-ranking`.
- Add TUI command aliases: `history`, `match-history`, `ranking-history`.
- Add final match ranking renderer with `username score old -> new rank old -> new`.
- Add match history renderer with unfiltered, filtered, and empty states.
- Add controller and client tests.

### Phase 6 - CI And Rollout

- Treat `./mvnw test` as the fast unit gate.
- Treat `./mvnw verify` as the required MySQL release gate.
- Run either Testcontainers or external MySQL in CI.
- Document no-db startup smoke testing.

## Required Tests

### Unit Tests

- `RankingInputValidator` accepts signed scores through normalized match input.
- `EloRatingCalculator` covers 2 to 5 players.
- Placement tests cover all ties, first-place ties, lower-place ties, and
  negative scores.
- DTOs protect defensive copies and reject invalid counters.
- `DefaultRankingService` can be tested with a fake repository and no MySQL.

### Integration Tests

- MySQL schema bootstrap creates and verifies all required objects.
- Case-sensitive nicknames work under `utf8mb4_bin`.
- Completed matches auto-create missing users and ratings.
- Negative final scores persist and order correctly.
- Duplicate exact replay is idempotent.
- Duplicate conflicting replay fails without data changes.
- Transaction failure leaves no partial match header, result rows, Elo
  updates, or rank-movement columns.
- Concurrent matches with overlapping players do not lose updates.
- Exact replay returns the same old/new score and old/new global rank movement.

### Controller And Client Tests

- `ServerController.startLobby` passes the configured ranking service to
  `GameOrchestrator`.
- A pre-lobby ranking command returns `GlobalRankingPayload`.
- A pre-lobby match-history command returns `MatchHistoryPayload`.
- A match-history player-count filter calls `RankingService.getMatchHistory(int)`.
- A finished match emits `FinalMatchRankingPayload` to every participant.
- Final match payload rows contain username, old score, new score, previous
  global rank, and new global rank.
- Disabled ranking mode has explicit behavior.
- Ranking failure maps to an error visible through TCP/RMI.
- TCP receiver dispatches global ranking and final match ranking payloads.
- RMI callback dispatches global ranking and final match ranking payloads.
- TUI renders final standings, leaderboard, history, empty states, and failures.

## CI Commands

Fast gate:

```bash
./mvnw -q test
```

Full MySQL gate with Testcontainers:

```bash
./mvnw -q verify -Dmesos.ranking.db.test.backend=testcontainers
```

Full MySQL gate with local Docker:

```bash
docker compose up -d mysql
MESOS_RANKING_DB_TEST_BACKEND=external ./mvnw -q verify
```

No-db smoke:

```bash
MESOS_RANKING_DB_ENABLED=false ./mvnw -q -Dexec.mainClass=it.polimi.ingsoft.mesos.server.LaunchServer exec:java
```

## Acceptance Criteria

The refactor is complete when:

- controllers depend only on `RankingService` and ranking DTOs;
- no controller or view imports MySQL ranking classes;
- `DefaultRankingService` contains no JDBC code;
- MySQL SQL and mapping are isolated in the MySQL adapter;
- bootstrap validates schema beyond table existence;
- negative final scores are valid ranking inputs;
- duplicate exact match replay is idempotent;
- conflicting duplicate match replay is rejected;
- pre-lobby global ranking reaches TCP and RMI clients through `GlobalRankingPayload`;
- match history reaches TCP and RMI clients through `MatchHistoryPayload`;
- final match ranking reaches TCP and RMI clients through
  `FinalMatchRankingPayload`;
- final match ranking shows username, old score, new score, previous global
  rank, and new global rank for every participant;
- no-db mode has explicit client-facing behavior or documented hidden UI;
- `./mvnw -q test` passes;
- `./mvnw -q verify -Dmesos.ranking.db.test.backend=testcontainers` passes.

## Non-Goals

- Persisting full in-progress game state.
- Introducing a general ORM.
- Introducing a dependency injection framework.
- Creating repository interfaces for every class.
- Moving to a layered architecture more complex than MVC.
- Adding pagination before leaderboard/history payloads exist.
