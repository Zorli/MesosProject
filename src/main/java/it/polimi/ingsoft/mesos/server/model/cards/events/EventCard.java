package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.player.Player;

public abstract class EventCard extends TribeCard implements EventAction {
    public EventCard(int id, Era era) { super(id, era);}
    @Override
    public boolean isDrawable(Player player) {
        return false;
    }
}
