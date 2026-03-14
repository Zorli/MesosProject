package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;

public abstract class EventCard extends TribeCard implements EventAction {
    public EventCard(int id, Era era, TribeCardType type) { super(id, era, type);}
    @Override
    public boolean isDrawable(Player player) {
        return false;
    }
}
