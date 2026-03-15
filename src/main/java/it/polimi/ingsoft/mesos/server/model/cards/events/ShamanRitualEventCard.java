package it.polimi.ingsoft.mesos.server.model.cards.events;

import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;


/**
 * Event card that compares shaman stars across players and assigns the
 * configured reward or penalty, taking into account building modifiers.
 */
public class ShamanRitualEventCard extends EventCard {
    private final int prestigePointsForHigher;
    private final int prestigePointsForLower;

    /**
     * Creates the event card.
     *
     * @param id card identifier
     * @param era card era
     * @param type tribe card type, expected to be {@code EVENT}
     * @param prestigePointsForHigher prestige points awarded to the highest star total
     * @param prestigePointsForLower prestige points lost by the lowest star total
     */
    public ShamanRitualEventCard(int id, Era era, TribeCardType type, int prestigePointsForHigher, int prestigePointsForLower) {
        super(id, era, type);
        this.prestigePointsForHigher = prestigePointsForHigher;
        this.prestigePointsForLower = prestigePointsForLower;
    }

    /**
     * Applies the event to all players in the match.
     *
     * @param players players affected by the event
     */
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
                if (players.get(i).getTribe().hasWinMultiplier()) {
                    players.get(i).gainPrestigePoints(this.prestigePointsForHigher * 2);
                } else {
                    players.get(i).gainPrestigePoints(this.prestigePointsForHigher);
                }
                if (players.get(i).getTribe().hasBuildingForShamanRitualProtection()) {
                    continue;
                } else {
                    players.get(i).payPrestigePoints(this.prestigePointsForLower);
                }
            } else {
                if (playerStars == maxStars) {
                    if (players.get(i).getTribe().hasWinMultiplier()) {
                        players.get(i).gainPrestigePoints(this.prestigePointsForHigher * 2);
                    } else {
                        players.get(i).gainPrestigePoints(this.prestigePointsForHigher);
                    }
                } else if (playerStars == minStars) {
                    if (players.get(i).getTribe().hasBuildingForShamanRitualProtection()) {
                        continue;
                    } else {
                        players.get(i).payPrestigePoints(this.prestigePointsForLower);
                    }
                }
            }
        }
    }

    /**
     * Returns the prestige point reward assigned to the highest shaman total.
     *
     * @return positive reward
     */
    public int getPrestigePointsForHigher() {
        return this.prestigePointsForHigher;
    }

    /**
     * Returns the prestige point penalty assigned to the lowest shaman total.
     *
     * @return negative prestige point value
     */
    public int getPrestigePointsForLower() {
        return this.prestigePointsForLower;
    }
}
