package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.controller.player.Player;

import java.util.ArrayList;

/**
 * Contract implemented by event cards that can resolve effects on all players in a match.
 */
public interface EventAction {
    /**
     * Applies the event effect to the provided list of players.
     *
     * @param players players affected by the event
     */
    void applyEffect(ArrayList<Player> players);
}
