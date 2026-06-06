/**
 * Provides the server-side domain model for Mesos.
 *
 * <p>This package groups the core abstractions used to represent game state: boards, cards,
 * players and their tribes, and the match orchestration (round flow, drafting, and phase
 * handling). Nested packages split these responsibilities into focused areas — {@code game}
 * for the match aggregate and its rules, {@code lobby} and {@code preLobby} for the models that
 * precede a match — while keeping all game-domain logic under a single namespace.
 */
package it.polimi.ingsoft.mesos.server.model;
