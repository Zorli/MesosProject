package it.polimi.ingsoft.mesos.server.model.cards.buildings;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.characters.CharacterCard;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Building that grants prestige points for each complete set of distinct character types owned by
 * the player at final scoring time.
 */
public class PrestigePointsForSet extends BuildingCard {
    private final int pointsPerSet;

    /**
     * Creates the building.
     *
     * @param id card identifier
     * @param era card era
     * @param fc food cost
     * @param pp prestige points printed on the building
     * @param type effect represented by the building
     * @param pointsPerSet prestige points granted for each completed set
     */
    public PrestigePointsForSet(int id, Era era, int fc, int pp, BenefitType type, int pointsPerSet) {
        super(id, era, fc, pp, type);
        this.pointsPerSet = pointsPerSet;
    }

    /**
     * Applies the final-scoring reward by counting how many full sets of character types the
     * player owns.
     *
     * @param player player receiving the prestige-point reward
     */
    public void applyEffect(Player player) {
        Map<TribeCardType, Integer> characterTypeCountMap;
        characterTypeCountMap = new HashMap<>();
        for (TribeCardType type : TribeCardType.values()) {
            // now the map has only the character types
            if(type != TribeCardType.EVENT) {
                characterTypeCountMap.put(type, 0);
            }
        }
        for (CharacterCard card : player.getTribe().getCharacters()) {
            if (characterTypeCountMap.containsKey(card.getType())) {
                characterTypeCountMap.put(card.getType(), characterTypeCountMap.get(card.getType()) + 1);
            }
        }
        int numberOfSet = 0;
        while (characterTypeCountMap.values().stream().allMatch(count -> count > 0)){
            numberOfSet++;
            characterTypeCountMap.replaceAll((t, v) -> characterTypeCountMap.get(t) - 1);
        }
        player.gainPrestigePoints(numberOfSet * this.pointsPerSet);
    }
}
