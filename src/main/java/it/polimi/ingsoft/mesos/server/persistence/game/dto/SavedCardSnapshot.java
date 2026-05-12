package it.polimi.ingsoft.mesos.server.persistence.game.dto;

import java.util.Map;

/**
 * Snapshot data for a single building card stored inside a player tribe.
 */
public record SavedCardSnapshot(int id, Map<String, Integer> runtimeState) {
}
