package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;

public interface EventAction {
    void applyEffect(ArrayList<Player> players);
}
