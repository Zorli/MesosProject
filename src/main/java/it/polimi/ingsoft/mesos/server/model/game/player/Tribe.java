package it.polimi.ingsoft.mesos.server.model.game.player;

import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.PairOfInventor;
import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.SetForFood;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.*;
import it.polimi.ingsoft.mesos.server.model.game.cards.buildings.ShamanStarsBonus;
import it.polimi.ingsoft.mesos.server.model.game.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stores the cards collected by a player and computes tribe-dependent bonuses.
 *
 * <p>The tribe aggregate groups all character and building cards acquired during the match. It
 * exposes convenience queries used by event resolution and final scoring to count specific card
 * types, detect protection effects, and compute derived rewards based on the owned buildings.
 */
public class Tribe {
    private ArrayList<CharacterCard> characters;
    private ArrayList<BuildingCard> buildings;

    /**
     * Creates an empty tribe with no characters or buildings.
     */
    public Tribe() {
        this.characters = new ArrayList<>();
        this.buildings = new ArrayList<>();
    }

    /**
     * Creates a tribe with an explicit initial owned card collection.
     * <p>
     * This constructor is used when a caller already has the complete tribe state and must
     * rebuild it without re-triggering draw-time side effects.
     *
     * @param characters initial character cards
     * @param buildings initial building cards
     */
    public Tribe(List<CharacterCard> characters, List<BuildingCard> buildings) {
        this.characters = new ArrayList<>(characters == null ? List.of() : characters);
        this.buildings = new ArrayList<>(buildings == null ? List.of() : buildings);
    }

    /**
     * Adds a character card to the tribe.
     *
     * @param c character card to add
     */
    public void addCharacter(CharacterCard c){
        this.characters.add(c);
    }

    /**
     * Adds a character card to the tribe and notifies owned post-draw buildings that accumulate
     * rewards from newly acquired characters.
     *
     * @param c character card to add
     * @param player owner of the tribe receiving the character
     */
    public void addCharacter(CharacterCard c, Player player){
        this.characters.add(c);
        notifyHunterDraw(c, player);
        notifyPostDrawBuildings(c, player);
    }

    /**
     * Adds a building card to the tribe.
     *
     * @param b building card to add
     */
    public void addBuilding(BuildingCard b){
        this.buildings.add(b);
    }

    /**
     * Applies the immediate hunter effect when the acquired character is a hunter.
     *
     * @param character character that has just been drawn
     * @param player owner receiving the character
     */
    private void notifyHunterDraw(CharacterCard character, Player player) {
        if (character.getType() == TribeCardType.HUNTER) {
            ((Hunter) character).applyEffect(player);
        }
    }

    /**
     * Notifies owned buildings that react to newly acquired character cards.
     *
     * @param character character that has just been drawn
     * @param player owner receiving the character
     */
    private void notifyPostDrawBuildings(CharacterCard character, Player player) {
        for (BuildingCard building : this.buildings) {
            if (building.getBenefitType() == BenefitType.POST_DRAW_SET_FOR_FOOD) {
                ((SetForFood)building).addCharacterTypeAndApplyEffect(character.getType(), player);
            }
            if (building.getBenefitType() == BenefitType.POST_DRAW_PAIR_OF_INVENTOR_WITH_SAME_INVENTION && character.getType() == TribeCardType.INVENTOR) {
                ((PairOfInventor)building).addInventionTypeAndApplyEffect(((Inventor)character).getInventionType(), player);
            }
        }
    }

    /**
     * Counts how many hunter cards are present in the tribe.
     *
     * @return number of hunters
     */
    public int countHunters() {
        return (int) this.characters.stream()
                .filter(c -> c instanceof Hunter)
                .count();
    }

    /**
     * Counts how many artist cards are present in the tribe.
     *
     * @return number of artists
     */
    public int countArtists() {
        return (int) this.characters.stream()
                .filter(c -> c instanceof Artist)
                .count();
    }

    /**
     * Counts how many gatherer cards are present in the tribe.
     *
     * @return number of gatherers
     */
    public int countGatherers() {
        return (int) this.characters.stream()
                .filter(c -> c instanceof Gatherer)
                .count();
    }

    /**
     * Computes the total number of shaman stars, including bonuses granted by buildings.
     *
     * @return total shaman stars currently available to the tribe
     */
    public int getShamansStars() {
        int bonusStars = 0;
        for(BuildingCard b: buildings){
            if(b.getBenefitType() == BenefitType.EVENT_BONUS_STARS_SHAMAN){
                bonusStars += ((ShamanStarsBonus) b).getStarsBonus();
            }
        }
        return bonusStars + this.characters.stream()
                .filter(c -> c instanceof Shaman)
                .mapToInt(c -> ((Shaman) c).getStars())
                .sum();
    }

    /**
     * Returns how many character cards are currently in the tribe.
     *
     * @return number of character cards
     */
    public int getCharactersSize(){
        return this.characters.size();
    }

    /**
     * Checks whether the tribe owns a building that protects against the shaman ritual loss.
     *
     * @return {@code true} when the protection building is present
     */
    public boolean hasBuildingForShamanRitualProtection(){
        return this.hasBuildingWithBenefit(BenefitType.EVENT_BONUS_LOSS_FROM_SHAMAN_EVENT);
    }
    public boolean hasBuildingForEndOfTurnFood(){
        return this.hasBuildingWithBenefit(BenefitType.END_ROUND_TOTEM_POSITION);
    }
    /**
     * Returns the building cards currently owned by the tribe.
     *
     * @return read-only list of owned building cards
     */
    public List<BuildingCard> getBuildings(){
        return Collections.unmodifiableList(this.buildings);
    }

    /**
     * Returns the character cards currently owned by the tribe.
     *
     * @return read-only list of owned character cards
     */
    public List<CharacterCard> getCharacters(){
        return Collections.unmodifiableList(this.characters);
    }

    /**
     * Checks whether the tribe owns the building that doubles the shaman ritual winner effect.
     *
     * @return {@code true} when the multiplier building is present
     */
    public boolean hasWinMultiplier(){
        return this.hasBuildingWithBenefit(BenefitType.EVENT_BONUS_SHAMAN_WIN_MULTIPLIER);
    }

    /**
     * Checks whether the tribe owns a building that grants an extra draw during the end-of-round
     * sequence.
     *
     * @return {@code true} when at least one extra-draw building is present
     */
    public boolean hasEndRoundExtraCardBuilding() {
        return this.hasBuildingWithBenefit(BenefitType.END_ROUND_EXTRA_CARD);
    }

    /**
     * Computes the total food discount granted by all builder character cards currently owned by
     * the tribe.
     *
     * @return total building-cost discount generated by builders
     */
    public int getTotalFoodDiscountFromBuilders() {
        int totalDiscount = 0;
        for (CharacterCard card : this.characters) {
            if (card.getType() == TribeCardType.BUILDER) {
                totalDiscount += ((Builder) card).getPriceDiscount();
            }
        }
        return totalDiscount;
    }

    /**
     * Computes the prestige points contributed by builder cards during final scoring, including the
     * optional doubling effect of the corresponding building.
     *
     * @return final prestige-point reward generated by builders
     */
    public int getPrestigePointsForBuilderInFinalPhase(){
        int reward = 0;
        for (CharacterCard card : this.characters){
            if(card.getType() == TribeCardType.BUILDER){
                reward += ((Builder) card).getPrestigePoints();
            }
        }
        for (BuildingCard build: this.buildings){
            if(build.getBenefitType() == BenefitType.BUILDER_TOTAL_PP_MULTIPLIER){
                reward *= 2;
            }
        }
        return reward;
    }

    /**
     * Returns whether at least one owned building exposes the specified benefit type.
     *
     * @param benefitType benefit to search for
     * @return {@code true} when the tribe owns a matching building
     */
    private boolean hasBuildingWithBenefit(BenefitType benefitType) {
        for (BuildingCard building : this.buildings) {
            if (building.getBenefitType() == benefitType) {
                return true;
            }
        }
        return false;
    }

}
