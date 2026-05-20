package it.polimi.ingsoft.mesos.server.model.game.player;

import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;

/**
 * Represents one participant in a match together with the mutable resources they own.
 *
 * <p>A player is identified by a nickname, owns a totem color, and tracks both food and prestige
 * points. The player also references the {@link Tribe} that stores all collected cards and
 * long-term bonuses accumulated during the game.
 */
public class Player {
    private final String nickname;
    private int food;
    private int prestigePoints;
    private final TotemColor totem;
    private Tribe tribe;

    /**
     * Creates a player with the provided identity, resources, and a fresh empty tribe.
     *
     * @param nickname player's nickname
     * @param food starting food amount
     * @param prestigePoints starting prestige points amount
     * @param totemColor totem color assigned to the player
     */
    public Player(String nickname, int food, int prestigePoints, TotemColor totemColor) {
        this(nickname, food, prestigePoints, totemColor, null);
    }

    /**
     * Creates a player with the provided identity, resources, and tribe aggregate.
     *
     * <p>This overload is used by tests and setup code that need to inject a pre-populated tribe.
     * Passing {@code null} creates a fresh empty tribe so newly created players are immediately ready
     * to collect cards.
     *
     * @param nickname player's nickname
     * @param food starting food amount
     * @param prestigePoints starting prestige points amount
     * @param totemColor totem color assigned to the player
     * @param tribe tribe aggregate owned by the player, or {@code null} to create a new one
     */
    public Player(String nickname, int food, int prestigePoints, TotemColor totemColor, Tribe tribe) {
        this.nickname = nickname;
        this.food = food;
        this.prestigePoints = prestigePoints;
        this.totem = totemColor;
        this.tribe = tribe == null ? new Tribe() : tribe;
    }

    /**
     * Returns the player's nickname.
     *
     * @return nickname used to identify the player
     */
    public String getNickname() {
        return this.nickname;
    }

    /**
     * Adds the specified amount of food to the player.
     *
     * @param amount food amount to gain
     */
    public void gainFood(int amount) {
        this.food += amount;
    }

    /**
     * Pays food, converting any missing remainder into prestige-point loss.
     *
     * @param amount total food cost to pay
     */
    public void payFood(int amount) {
        if (this.food >= amount) {
            this.food -= amount;
        } else {
            int remaining = amount - this.food;
            this.food = 0;
            payPrestigePoints(remaining);
        }
    }

    /**
     * Adds the specified amount of prestige points to the player.
     *
     * @param amount prestige points to gain
     */
    public void gainPrestigePoints(int amount) {
        this.prestigePoints += amount;
    }

    /**
     * Removes the specified amount of prestige points from the player.
     *
     * @param amount prestige points to subtract
     */
    public void payPrestigePoints(int amount) {
        this.prestigePoints -= amount;
    }

    /**
     * Returns the amount of food currently owned by the player.
     *
     * @return current food count
     */
    public int getFood() { return this.food; }

    /**
     * Returns the amount of prestige points currently owned by the player.
     *
     * @return current prestige points
     */
    public int getPrestigePoints() {
        return this.prestigePoints;
    }

    /**
     * Returns the totem color assigned to the player.
     *
     * @return player totem color
     */
    public TotemColor getTotem() {
        return this.totem;
    }

    /**
     * Returns the tribe aggregate owned by the player.
     *
     * @return player's tribe
     */
    public Tribe getTribe() {
        return this.tribe;
    }

    /**
     * Ensures the player has an initialized tribe aggregate before entering a match.
     *
     * <p>The method is idempotent so factories can call it defensively without
     * changing already initialized state.
     */
    public void initializeGameAttributes() {
        if (this.tribe == null) {
            this.tribe = new Tribe();
        }
    }

    /**
     * Returns the total building-cost discount currently granted by the player's builder cards.
     *
     * @return total food discount applied when buying buildings
     */
    public int getBuildingFoodDiscount() {
        return this.tribe == null ? 0 : this.tribe.getTotalFoodDiscountFromBuilders();
    }


}
