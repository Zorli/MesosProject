# Game Functional Specification As-Is

This document describes the behavior currently implemented by `Game`,
`TurnOrderTrack`, and `PlayersTurnFSM`.

Its purpose is to provide a functional reference before refactoring the game
flow. A refactor should preserve the behavior listed here unless a specific item
is explicitly marked as ambiguous or fragile and intentionally changed.

This is not a desired design document. It is an as-is contract extracted from
the current implementation.

## Source Classes

The behavior is mainly derived from:

- `Game`;
- `TurnOrderTrack`;
- `PlayersTurnFSM`;
- `Board`;
- `OfferPath`;
- `OfferSpace`;
- `StartingTile`;
- `StartingBlock`.

The refactor can move responsibilities out of these classes, but the externally
observable behavior of the game flow must remain equivalent.

## Main Responsibilities

`Game` is the aggregate root of one match.

It owns:

- the game id;
- the players;
- the current era;
- the game status;
- the state version;
- the board;
- the round order tracker;
- the player turn state machine;
- the listeners that receive game updates or errors.

`TurnOrderTrack` owns the round-level flow:

- ordered list of players for the current phase;
- index of the current player;
- current round number;
- current round phase.

`PlayersTurnFSM` owns the draw-turn state:

- whether a player is waiting for card selection;
- how many upper-row choices each player still has;
- how many lower-row choices each player still has.

`Board` owns the physical board state used by `Game`:

- upper visible row;
- lower visible row;
- offer path;
- starting tile;
- tribe deck;
- building deck.

`OfferPath` owns:

- which offer spaces exist for the current player count;
- which player occupies each offer space;
- how players move from the starting tile to offer spaces;
- how players return from offer spaces to the starting tile;
- the resolution order after totem placement.

`StartingTile` owns:

- ordered starting blocks;
- current players on those blocks;
- the next-round order based on occupied starting blocks.

## Game Statuses

The game status can be:

- `SETUP`: the match exists but initial setup is not completed;
- `IN_PROGRESS`: normal gameplay is active;
- `FINISHED`: final scoring has been calculated.

Public game actions are executed through `executeGameAction`.

If an action changes the game state version, the game notifies all listeners with
the current game payload.

If an action throws `IllegalArgumentException` or `IllegalStateException`, the
game catches it and notifies listeners with an error payload. The error code is
the exception class name, and the message is the exception message.

Errors are therefore observable through listener notifications, not by exception
propagation from public action methods.

## Listener And Version Contract

`Game` stores listeners in insertion order.

`addListener`:

- rejects a null listener;
- does not add the same listener twice;
- preserves already registered listeners.

`removeListener` removes the listener if present and does nothing otherwise.

`notifyListeners` copies the listener list before iterating. This means listener
notification is based on the listener set present at the beginning of the
notification call.

Each public game action runs inside `executeGameAction`:

1. the previous `stateVersion` is stored;
2. the action body runs;
3. if `stateVersion` changed, listeners receive `buildPayload()`;
4. if the action throws `IllegalArgumentException` or `IllegalStateException`,
   listeners receive an error payload instead.

One public action can increment `stateVersion` multiple times internally, but
listeners receive at most one game payload for that public action.

`notifyError(code, message)` immediately broadcasts an error payload to all
listeners.

`notifyActionError(target)` sends only to `target`, and only if `target` is not
null. It uses:

- code: `GAME_ACTION_ERROR`;
- message: `Action non permitted in this phase.`

## Round Phases

The round phase can be:

- `PLACING_TOTEMS`;
- `PLAYER_DRAWS`;
- `EXTRA_DRAWS`;
- `RESOLVING_EFFECTS`;
- `RESETTING_BOARD`;
- `FINAL_SCORING`.

Only some phases are directly triggered by user actions:

- `PLACING_TOTEMS` accepts `moveTotem`;
- `PLAYER_DRAWS` accepts `chooseCard`;
- `EXTRA_DRAWS` accepts `extraDrawn`.

The other phases are passive phases. They are advanced automatically by `Game`
after a player action completes.

Passive phases are not public user commands in the current implementation.

## Setup Flow

`setup` is valid only when the game status is `SETUP`.

When setup runs, the game:

1. builds the offer path on the board;
2. sets up the initial visible card rows;
3. gives initial food to players;
4. initializes the turn order with the original player list;
5. advances the round number from `0` to `1`;
6. sets the round phase to `PLACING_TOTEMS`;
7. resets the player turn state machine;
8. changes the game status to `IN_PROGRESS`;
9. marks the state as changed.

Initial food is assigned by player position:

- first player receives 2 food;
- second and third players receive 3 food;
- fourth and later players receive 4 food.

### Offer Path Setup

The offer path always has the same possible spaces, but only spaces whose
minimum player count is less than or equal to the actual player count are added.

| Space | Position | Min players | Food bonus | Upper choices | Lower choices |
| --- | ---: | ---: | ---: | ---: | ---: |
| `A` | 1 | 5 | 3 | 0 | 0 |
| `B` | 2 | 2 | 0 | 0 | 1 |
| `C` | 3 | 2 | 0 | 1 | 0 |
| `D` | 4 | 3 | 0 | 0 | 2 |
| `E` | 5 | 2 | 0 | 1 | 1 |
| `F` | 6 | 2 | 0 | 2 | 0 |
| `G` | 7 | 4 | 0 | 2 | 1 |

The starting tile is also configured during setup.

Starting blocks depend on player count:

| Players | Food modifiers | Prestige modifiers |
| ---: | --- | --- |
| 2 | `1, -1` | `0, -2` |
| 3 | `2, 0, -1` | `0, 0, -2` |
| 4 | `2, 1, 0, -1` | `0, 0, 0, -2` |
| 5 | `3, 1, 0, 0, -1` | `0, 0, 0, 0, -2` |

During setup, players are placed on starting blocks in the same order as the
game player list. Starting block effects are not resolved during setup because
blocks are constructed already occupied.

### Initial Row Setup

The board initializes visible rows in this order:

1. draw tribe cards until the lower row contains `numberOfPlayers + 1`
   character cards, unless the tribe deck becomes empty;
2. during this first step, event cards are placed in the upper row instead of
   the lower row;
3. draw additional tribe cards into the upper row until the upper row contains
   `numberOfPlayers + 4` tribe cards, unless the tribe deck becomes empty;
4. draw era-1 building cards into the upper row:
   - 1 building for 2 players;
   - 2 buildings for 3 or more players.

## Totem Placement Flow

`moveTotem(player, tile)` is valid only when:

- the game status is `IN_PROGRESS`;
- the round phase is `PLACING_TOTEMS`;
- `player` is the current player in `TurnOrderTrack`;
- `tile` is not null;
- the board reports that the tile is available.

When the action is valid, the game:

1. places the player on the selected offer-path space;
2. asks the board how many upper and lower cards that player may draw;
3. registers those draw counters in `PlayersTurnFSM`.

Placing the player on an offer space also removes the player from the starting
tile.

The food bonus of the selected offer space is not resolved immediately by
`moveTotem`, except for space `A`, which is resolved later when all totems have
been placed.

If the player is not the last player in the current order, the turn cursor moves
to the next player.

If the player is the last player in the current order, the game:

1. resolves offer-space `A`;
2. replaces the turn order with the board resolution order;
3. skips players with no drawable cards while there is still another player;
4. sets the round phase to `PLAYER_DRAWS`.

Resolving offer-space `A` means:

1. find occupied space `A`;
2. apply its immediate action to its player;
3. return that player to the starting tile.

Returning a player to the starting tile means:

1. clear the offer space occupied by that player;
2. place the player on the first available starting block;
3. immediately resolve the starting block effect.

Starting block effect:

- if food modifier is positive, the player gains that food;
- if food modifier is negative and the player has enough food, the player pays
  that food;
- if food modifier is negative and the player does not have enough food, the
  player gains the prestige modifier, which is normally a penalty.

The resolution order after all totems have been placed is the list of players
still occupying offer spaces, sorted by offer-space position, excluding space
`A`.

The skip loop after resolution order setup only skips players while the current
player has no drawable cards and is not the last player. If the last player has
no drawable cards, the phase still becomes `PLAYER_DRAWS` with that player as
current player.

## Standard Card Draw Flow

`chooseCard(player, cardId)` is valid only when:

- the game status is `IN_PROGRESS`;
- the round phase is `PLAYER_DRAWS`;
- `player` is the current player in `TurnOrderTrack`.

The public method converts `cardId` into a board card using `board.findCardById`.
If no card is found, the internal flow receives `null`.

Before choosing the card, the game ensures that `PlayersTurnFSM` is waiting for
card selection:

- if the FSM is completed, it starts card selection for the current player;
- if the FSM is not waiting for card selection after that, the action is invalid.

Starting card selection requires both upper and lower counters to have been
registered for that player.

The selected card is valid only if:

- it is present in the upper or lower board row;
- it is drawable by the player according to the board;
- the FSM still allows a choice from that row.

When the selected card is valid, the game:

1. removes it from the board row;
2. applies it to the player;
3. tells the FSM that a card was chosen from that row.

Applying the card means:

- if it is a `BuildingCard`, the player pays its effective cost and the building
  is added to the player's tribe;
- otherwise it is cast to `CharacterCard` and added to the player's tribe.

After a card is applied, the FSM decrements only the counter for the selected
row. The other row counter is unchanged.

After the card is applied, the game completes the player's draw turn if:

- the FSM says the turn is completed; or
- the player has no available draw left on the board.

Completing the draw turn means:

1. returning the player to the starting tile;
2. resetting that player's FSM counters;
3. advancing to the next player with an available draw, or ending standard draws.

`hasAnyAvailableDraw(player)` is true only when at least one of these is true:

- the player has remaining upper choices in the FSM and the board has at least
  one upper card drawable by that player;
- the player has remaining lower choices in the FSM and the board has at least
  one lower card drawable by that player.

When advancing after a completed draw turn:

1. if the current player is the last player, standard draws end;
2. otherwise the turn cursor advances;
3. if the next player has any available draw, the method stops;
4. if the next player has no available draw, that player is returned to the
   starting tile and the player's FSM counters are reset;
5. the loop continues.

When standard draws end, the game checks the next-round order from the board:

- players with an end-round extra-card building and at least one draftable card
  enter `EXTRA_DRAWS`;
- if no player is eligible, the phase becomes `RESOLVING_EFFECTS`.

After `chooseCard`, passive phases are advanced automatically.

Eligibility for `EXTRA_DRAWS` is based on the board next-round order, not on the
old offer-path resolution order.

Extra-draw eligibility requires both:

- `player.getTribe().hasEndRoundExtraCardBuilding()` is true;
- at least one upper or lower board card is drawable by that player.

## Extra Draw Flow

`extraDrawn(player, cardId)` is valid only when:

- the game status is `IN_PROGRESS`;
- the round phase is `EXTRA_DRAWS`;
- `player` is the current player in `TurnOrderTrack`;
- the player has an end-round extra-card building.

The selected card is valid only if:

- it is present in the upper or lower board row;
- it is drawable by the player according to the board.

Unlike standard draws, extra draws do not use the FSM row counters.

When the selected card is valid, the game:

1. removes it from the board row;
2. applies it to the player;
3. advances the extra-draw flow.

If the current player is the last player in the extra-draw order, the game:

1. replaces the order with the next-round order from the board;
2. sets the phase to `RESOLVING_EFFECTS`.

Otherwise, the cursor advances to the next extra-draw player.

After `extraDrawn`, passive phases are advanced automatically.

Extra draws apply the selected card in the same way as standard draws:

- buildings are paid and added as buildings;
- non-buildings are cast to `CharacterCard` and added as characters.

The extra-draw flow does not return the current player to the starting tile
after the extra draw. The player is already expected to be on the starting tile
because extra-draw order is derived from next-round starting order.

## Passive Phase Flow

`advancePassivePhases` runs while the game is `IN_PROGRESS`.

It repeatedly handles passive phases until the game reaches a phase that needs a
player action, or until the game is no longer in progress.

When the phase is `RESOLVING_EFFECTS`:

- if the round number is `10`, the board resolves all visible events and the
  phase becomes `FINAL_SCORING`;
- otherwise, the board resolves lower-row events and the phase becomes
  `RESETTING_BOARD`.

When the phase is `RESETTING_BOARD`:

1. the board resets the visible rows after the round;
2. if the board reports an era change, the current era advances;
3. the round number is incremented;
4. the FSM is reset for the new round;
5. the phase becomes `PLACING_TOTEMS`.

When the phase is `FINAL_SCORING`:

1. each player's buildings apply final prestige points;
2. each player's buildings apply their effect;
3. each player gains builder final prestige points from the tribe;
4. the game status becomes `FINISHED`.

### Board Reset Details

Resetting the board after a non-final round means:

1. discard all lower-row cards;
2. move all upper-row tribe cards to the lower row;
3. refill the upper row with tribe cards until it contains
   `numberOfPlayers + 4` tribe cards or the tribe deck is empty.

If a drawn tribe card belongs to a different era from the current era and no era
change has already been triggered during this refill:

1. mark that an era change happened;
2. discard lower-row building cards;
3. move all upper-row building cards to the lower row;
4. refill upper-row building cards for the new era;
5. continue adding the drawn tribe card to the upper row.

Building refill count:

- era `second`:
  - 2 buildings for up to 3 players;
  - 3 buildings for 4 or 5 players;
- era `third`:
  - 3 buildings for 2 players;
  - 4 buildings for 3 or 4 players;
  - 5 buildings for 5 players;
- other eras: 0 buildings.

If an era change is reported, `Game` advances `currentEra` by calling
`currentEra.next()`. The new era is not assigned directly from the drawn card.

## TurnOrderTrack Behavior

A default `TurnOrderTrack` starts with:

- empty turn order;
- current player index `0`;
- round number `0`;
- phase `PLACING_TOTEMS`.

Most read and advance operations require a non-empty order.

These methods require initialized order:

- `getCurrentPlayer`;
- `getTurnOrder`;
- `getCurrentPlayerIndex`;
- `advanceToNextPlayer`;
- `isLastPlayer`.

If the order is empty, they throw `IllegalStateException` with message
`Turn order has not been initialized`.

Setting a new order:

- rejects null or empty lists;
- copies the provided order;
- resets the current player index to `0`.

Advancing to the next player:

- is invalid if the current player is already the last player;
- otherwise increments the current player index by one.

## PlayersTurnFSM Behavior

A default `PlayersTurnFSM` starts in `TURN_COMPLETED` with no registered draw
counters.

Registering drawable cards:

- rejects a null player;
- rejects negative counters;
- stores upper and lower remaining choices for that player.

Starting card selection:

- rejects a null player;
- requires both upper and lower counters to have been registered;
- sets the phase to `WAITING_FOR_CARD_SELECTION`.

Choosing a card:

- rejects a null player;
- rejects `NOT_PRESENT` rows;
- requires the phase to be `WAITING_FOR_CARD_SELECTION`;
- requires a remaining counter for the selected row;
- decrements the selected row counter;
- sets the phase to `TURN_COMPLETED` when the player has no choices left.

Resetting for the next player removes that player's counters and sets the phase
to `TURN_COMPLETED`.

Resetting for a new round clears all counters and sets the phase to
`TURN_COMPLETED`.

The FSM does not know which player is the current turn player. It only stores
and validates draw counters. Turn ownership is validated by `Game` through
`TurnOrderTrack`.

## Error Behavior

Public actions catch these errors and report them as error payloads.

The error payload code is the simple class name of the thrown exception.

Important validation errors:

| Condition | Exception code | Message shape |
| --- | --- | --- |
| Action used in wrong game status | `InvalidGameStatusException` | `Action <action> is not allowed when game status is <status>` |
| Action used in wrong round phase | `InvalidGamePhaseException` | `Action <action> is not allowed during round phase <phase>` |
| Action used in wrong FSM phase | `InvalidTurnPhaseException` | `Action <action> is not allowed during phase <phase>` |
| Wrong player acts | `NotPlayersTurnException` | `It is not the turn of player <actual>. Current player: <expected>` |
| Tile is null, missing, or occupied | `TileNotAvailableException` | `Selected tile is not available: <tile>` |
| Card id does not map to a board card | `CardNotPresentException` | `Requested card is not present on the board: null` |
| Card is not on the board | `CardNotPresentException` | `Requested card is not present on the board: <cardId>` |
| Card is present but not drawable | `CardNotDrawableException` | `Card <cardId> is not drawable by player <nickname>` |
| Player cannot choose from selected row | `CardRowSelectionNotAllowedException` | `Card selection is not allowed from row: <row>` |
| Extra draw without extra-card building | `IllegalStateException` | `Player <nickname> cannot perform an extra draw` |
| FSM starts without registered counters | `IllegalStateException` | `Drawable cards were not registered for player <nickname>` |
| FSM consumes invalid upper row choice | `IllegalStateException` | `Cannot choose from upper row` |
| FSM consumes invalid lower row choice | `IllegalStateException` | `Cannot choose from lower row` |
| FSM receives `NOT_PRESENT` row | `IllegalArgumentException` | `Card row cannot be NOT_PRESENT` |
| Null player passed to FSM registration/start/choice | `IllegalArgumentException` | `Player cannot be null` |
| Negative FSM counters | `IllegalArgumentException` | `Choices must be non-negative` |

Because public actions catch `IllegalArgumentException` and
`IllegalStateException`, these errors normally do not escape from `setup`,
`moveTotem`, `chooseCard`, or `extraDrawn`.

## Ambiguous Or Fragile Behavior

These behaviors are implemented today, but should be reviewed before or during
the refactor.

1. `Game` catches both validation errors and state errors inside
   `executeGameAction`, then reports them as payloads instead of propagating
   them.

2. `chooseCard` and `extraDrawn` pass `null` internally when `cardId` is not
   found. The resulting error depends on later card-row validation.

3. Standard draw selection and extra draw selection duplicate most of the same
   card validation and removal logic, but standard draws also check FSM row
   counters.

4. Passive phases are advanced automatically after `chooseCard` and `extraDrawn`.
   This means one user action can trigger multiple internal state transitions.

5. In the last round, resolving effects moves directly from `RESOLVING_EFFECTS`
   to `FINAL_SCORING`, then passive advancement immediately calculates final
   scoring and finishes the game.

6. `applyChosenCardToPlayer` treats every non-building card as a `CharacterCard`.
   This assumes event cards are never selectable/drawable by players.

7. `TurnOrderTrack` allows constructing a restored state with any non-negative
   round number and any non-null phase. It does not validate phase consistency.

8. `PlayersTurnFSM` stores counters by `Player` object identity/equality. Restored
   players must therefore be the same logical player objects used by the game.

9. `completeExtraDrawTurn` advances to the next player in the extra-draw order
   without rechecking whether that next player still has a draftable card at that
   exact moment.

10. `stateVersion` can be incremented multiple times during one public action,
    but listeners receive only one payload after the action completes.

## Refactor Constraint

The first refactor should preserve the public behavior of:

- `setup`;
- `moveTotem`;
- `chooseCard`;
- `extraDrawn`;
- listener notifications;
- error notification behavior;
- state version changes that affect notification;
- payload building through `GamePayloadMapper`.

The internal structure can change, but each phase transition described above
must remain equivalent unless explicitly changed in a separate, reviewed step.

## Post-Refactor Equivalence Checklist

After refactoring, verify the following manually or with focused tests:

1. `setup` creates the same offer spaces for each player count.
2. `setup` creates the same starting blocks for each player count.
3. `setup` gives the same initial food by player position.
4. `setup` fills upper and lower rows with the same rules.
5. `setup` moves status from `SETUP` to `IN_PROGRESS`.
6. `setup` moves round number from `0` to `1`.
7. `moveTotem` rejects wrong status, wrong phase, wrong player, and unavailable tile.
8. `moveTotem` removes the player from the starting tile.
9. `moveTotem` registers the exact upper/lower draw counters returned by the board.
10. Last totem placement resolves space `A` before entering `PLAYER_DRAWS`.
11. Resolution order excludes space `A`.
12. Resolution order follows offer-space position.
13. Players with no available standard draw are skipped as before.
14. `chooseCard` rejects wrong status, wrong phase, wrong player, missing card,
    non-drawable card, and row choices not allowed by the FSM.
15. `chooseCard` starts the FSM lazily when the FSM is `TURN_COMPLETED`.
16. `chooseCard` removes the card from the correct board row.
17. `chooseCard` applies buildings and characters exactly as before.
18. `chooseCard` decrements only the selected row counter.
19. `chooseCard` returns completed players to the starting tile.
20. `chooseCard` advances to the next drawable player or ends standard draws.
21. Standard draw completion enters `EXTRA_DRAWS` only for eligible players.
22. Standard draw completion enters `RESOLVING_EFFECTS` when no extra draw is
    needed.
23. `extraDrawn` rejects wrong status, wrong phase, wrong player, missing card,
    non-drawable card, and players without an extra-card building.
24. `extraDrawn` does not consume FSM counters.
25. `extraDrawn` advances through the extra-draw order exactly as before.
26. Last extra draw enters `RESOLVING_EFFECTS`.
27. Passive phases still advance automatically after `chooseCard` and
    `extraDrawn`.
28. Round 1-9 effect resolution resolves only lower events.
29. Round 10 effect resolution resolves all visible events.
30. Non-final passive flow goes `RESOLVING_EFFECTS` -> `RESETTING_BOARD` ->
    `PLACING_TOTEMS`.
31. Final passive flow goes `RESOLVING_EFFECTS` -> `FINAL_SCORING` -> `FINISHED`.
32. Board reset moves upper tribe cards to lower row and refills upper row as
    before.
33. Era changes still move buildings and refill building cards with the same
    counts.
34. Listener payload notification still happens once per successful public action
    that changed state.
35. Error notification still uses exception simple class name as code.
36. Public action methods still catch `IllegalArgumentException` and
    `IllegalStateException`.
