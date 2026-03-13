package it.polimi.ingsoft.mesos.server.model.player;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Hunter;

import java.util.ArrayList;

public class Tribe {
    private ArrayList<Card> characters;
    private ArrayList<BuildingCard> buildings;

    public void addCharacter(Card c){
        this.characters.add(c);
    }

    public void addBuilding(BuildingCard b){
        this.buildings.add(b);
    }

    public int countHunters() {
        int count = 0;
        for (Card character : this.characters) {
            if (character instanceof Hunter) {
                count++;
            }
        }
        return count;
    }

    public int getShamansStars() {
        return this.characters.stream()
                .filter(c -> c instanceof it.polimi.ingsoft.mesos.server.model.cards.characters.Shaman)
                .mapToInt(c -> ((it.polimi.ingsoft.mesos.server.model.cards.characters.Shaman) c).getStars())
                .sum();
    }

    public int countArtists() {
        return (int) this.characters.stream()
                .filter(c -> c instanceof it.polimi.ingsoft.mesos.server.model.cards.characters.Artist)
                .count();
    }

    public int getCharactersSize(){
        return this.characters.size();
    }

    public int countGatherers() {
        return (int) this.characters.stream()
                .filter(c -> c instanceof it.polimi.ingsoft.mesos.server.model.cards.characters.Gatherer)
                .count();
    }

    // TODO: once the building card is created we can check if the player has the building that has the zero pp rule for the shaman event
    public boolean hasBuildingForShamanRitual(){
        return false;
    }

}
