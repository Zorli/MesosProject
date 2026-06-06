/**
 * Server-side controllers that drive the application through its lifecycle phases.
 *
 * <p>Controllers consume queued commands and translate client payloads and system events into
 * operations on the Mesos domain model. The package is split by phase:
 * {@code preLobby} ({@link it.polimi.ingsoft.mesos.server.controller.preLobby.PreLobbyController}),
 * {@code lobby} ({@link it.polimi.ingsoft.mesos.server.controller.lobby.LobbyController}), and
 * {@code game} ({@link it.polimi.ingsoft.mesos.server.controller.game.GameController}). The
 * {@code command} subpackage holds the queued command types ({@code ClientPayloadCommand} and the
 * {@code System*} commands) that controllers process.
 */
package it.polimi.ingsoft.mesos.server.controller;
