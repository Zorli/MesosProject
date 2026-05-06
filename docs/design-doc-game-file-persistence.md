# Game File Persistence Draft

Related issue: #123

This document is an initial checkpoint for the new file-based match persistence work.
It is intentionally not a complete design and does not make the branch ready to merge.

## Goal

Persist an in-progress match on disk so the server can restore the last confirmed
game state after a crash.

The current requirement from issue #123 is to save match state to a text file using
the game payload plus the decks. The save operation should be triggered from the
server/controller flow after valid state-changing actions.

## Non-Goals For This Checkpoint

- No database-backed match persistence.
- No reuse of the removed PostgreSQL saved-match implementation.
- No persistence of sockets, listeners, RMI objects, command queues, threads, or
  live session connections.
- No guarantee yet that this branch can restore a full match end to end.

## Current Architectural Notes

- The active game state lives in `Game`, `Board`, `TurnState`, `TurnOrderTrack`,
  `Player`, `Tribe`, decks, piles, and offer-path classes.
- Runtime collaborators such as `GameOrchestrator`, `ServerSessionRegistry`,
  `VirtualViewTCP`, and `VirtualViewRMI` contain non-persistable infrastructure.
- A restored match will need fresh runtime wiring and fresh client listeners after
  clients reconnect.

## Initial Direction

1. Define a dedicated file persistence component under the existing server
   persistence area.
2. Store one checkpoint per active match.
3. Write checkpoints only after valid state-changing actions.
4. Use a temporary file followed by an atomic move where supported.
5. Ignore or quarantine corrupt/incomplete checkpoint files instead of crashing
   the server during boot.
6. Remove the checkpoint once the match reaches a completed terminal state.

## Open Decisions

- Exact text format: Java serialization is not a text format, so the branch needs
  a deliberate decision between text snapshots and binary object serialization.
- Exact snapshot shape: direct `GamePayload + decks`, dedicated DTOs, or a hybrid
  that keeps deck/card identity stable.
- Reconnect flow: how authenticated users are mapped back into a restored game.
- Test scope for the first implementation slice.
