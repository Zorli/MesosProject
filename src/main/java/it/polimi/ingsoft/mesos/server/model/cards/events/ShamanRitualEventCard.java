package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;


public class ShamanRitualEventCard extends Card implements EventAction {
    private final int prestigePointsForHigher;
    private final int prestigePointsForLower;

    public ShamanRitualEventCard(int id, Era era, int prestigePointsForHigher, int prestigePointsForLower) {
        super(id, era);
        this.prestigePointsForHigher = prestigePointsForHigher;
        this.prestigePointsForLower = prestigePointsForLower;
    }

    // TODO: control if the player has the building that has the zero pp rule for the shaman event
    @Override
    public void applyEffect(ArrayList<Player> players) {

        ArrayList<Integer> shamanStars = new ArrayList<>();
        for (Player player : players) {
            shamanStars.add(player.getTribe().getShamansStars());
        }

        int maxStars = shamanStars.stream().mapToInt(Integer::intValue).max().orElse(0);
        int minStars = shamanStars.stream().mapToInt(Integer::intValue).min().orElse(0);

        boolean allEqual = maxStars == minStars;

        for (int i = 0; i < players.size(); i++) {
            int playerStars = shamanStars.get(i);

            if (allEqual) {
                players.get(i).gainPrestigePoints(this.prestigePointsForHigher);
                players.get(i).payPrestigePoints(this.prestigePointsForLower);
            } else {
                if (playerStars == maxStars) {
                    // controllo per edificio che fa guadagnare
                    players.get(i).gainPrestigePoints(this.prestigePointsForHigher);
                } else if (playerStars == minStars) {
                    if (players.get(i).getTribe().hasBuildingForShamanRitual()) {
                        continue;
                    } else {
                        players.get(i).payPrestigePoints(this.prestigePointsForLower);
                    }
                }
            }
        }
    }
}