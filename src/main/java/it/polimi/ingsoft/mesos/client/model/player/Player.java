package it.polimi.ingsoft.mesos.client.model.player;

import it.polimi.ingsoft.mesos.client.model.enums.TotemColor;

// TODO: add attribute Tribe and the method takeCard(Card card)

public class Player {
    private String nickname;
    private int food;
    private int prestigePoints;
    private TotemColor totem;

    public void gainFood(int amount) {
        this.food += amount;
    }

    public void payFood(int amount) {
        this.food -= amount;
    }

    public void gainPrestigePoints(int amount) {
        this.prestigePoints += amount;
    }

    public void payPrestigePoints(int amount) {
        this.prestigePoints -= amount;
    }

}
