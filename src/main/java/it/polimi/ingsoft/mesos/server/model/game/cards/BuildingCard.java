package it.polimi.ingsoft.mesos.server.model.game.cards;

import it.polimi.ingsoft.mesos.server.model.game.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;

/**
 * Base abstraction for every building card.
 * <p>
 * A building card has a purchase cost, grants prestige points at the end of the
 * game, and identifies its gameplay effect through a {@link BenefitType}.
 */
public class BuildingCard extends Card {
    private int foodCost;
    private int prestigePoints;
    private BenefitType benefitType;

    /**
     * Creates a building card with the given static attributes.
     *
     * @param id card identifier
     * @param era card era
     * @param fc food cost required to draw the building
     * @param pp prestige points granted by the building itself
     * @param benefitType effect associated with the building
     */
    public BuildingCard(int id, Era era, int fc, int pp, BenefitType benefitType){
        super(id, era);
        this.foodCost = fc;
        this.prestigePoints = pp;
        this.benefitType = benefitType;
    }

    /**
     * Checks whether the player can currently draw the building.
     *
     * @param player player attempting to draw the card
     * @return {@code true} if the player has enough food to pay the cost
     */
    @Override
    public boolean isDrawable(Player player) {
        return player.getFood() >= this.getEffectiveFoodCost(player);
    }

    /**
     * Returns the effect identifier of the building.
     *
     * @return the building benefit type
     */
    public BenefitType getBenefitType() {
        return this.benefitType;
    }

    /**
     * Returns the food cost required to obtain the building.
     *
     * @return building food cost
     */
    public int getFoodCost() {
        return this.foodCost;
    }

    /**
     * Returns the actual food cost the provided player has to pay after applying builder discounts.
     *
     * @param player player attempting to acquire the building
     * @return discounted cost, never below zero
     */
    public int getEffectiveFoodCost(Player player) {
        return Math.max(0, this.foodCost - player.getBuildingFoodDiscount());
    }

    /**
     * Charges the effective discounted food cost of this building to the provided player.
     *
     * @param player player acquiring the building
     */
    public void payEffectiveCost(Player player) {
        player.payFood(this.getEffectiveFoodCost(player));
    }

    /**
     * Returns the prestige points printed on the building.
     *
     * @return intrinsic prestige points of the building
     */
    public int getPrestigePoints() {
        return this.prestigePoints;
    }

    /**
     * Grants the building prestige points to the owning player.
     *
     * @param player player receiving the building prestige points
     */
    public void increaseFinalPrestigePoints(Player player){
        player.gainPrestigePoints(this.prestigePoints);
    }

    /**
     * Applies the runtime effect of the building to the owning player.
     *
     * <p>The base implementation is intentionally empty because some buildings only contribute
     * static prestige points or override other hooks instead of running an immediate effect.
     *
     * @param player owner of the building
     */
    public void applyEffect(Player player) {
        // Default implementation does nothing, as some buildings only have intrinsic prestige points.
    }

}
