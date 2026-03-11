package it.polimi.ingsoft.mesos.server.model.player;

import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;

import java.util.ArrayList;

// TODO: the method takeCard(Card card)

public class Player {
    private String nickname;
    private int food;
    private int prestigePoints;
    private TotemColor totem;
    private Tribe tribe;

    public Player(String nickname, int food, int prestigePoints, TotemColor totemColor, Tribe tribe) {
        this.nickname = nickname;
        this.food = food;
        this.prestigePoints = prestigePoints;
        this.totem = totemColor;
        this.tribe = tribe;
    }


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

    public Tribe getTribe() {
        return this.tribe;
    }

}
