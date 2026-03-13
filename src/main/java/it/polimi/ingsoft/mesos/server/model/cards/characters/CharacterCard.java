package it.polimi.ingsoft.mesos.server.model.cards.characters;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.player.Player;

public abstract class CharacterCard extends TribeCard {
    public CharacterCard(int id, Era era) {
        super(id, era);
    }
    @Override
    public boolean isDrawable(Player player) {
        return true;
    }
}
