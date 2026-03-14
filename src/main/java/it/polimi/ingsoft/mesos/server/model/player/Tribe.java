package it.polimi.ingsoft.mesos.server.model.player;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.characters.*;

import java.util.ArrayList;

public class Tribe {
    private ArrayList<CharacterCard> characters;
    private ArrayList<BuildingCard> buildings;
    public Tribe() {
        this.characters = new ArrayList<>();
        this.buildings = new ArrayList<>();
    }
    public void addCharacter(CharacterCard c){
        this.characters.add(c);
    }

    public void addBuilding(BuildingCard b){
        this.buildings.add(b);
    }

    public int countHunters() {
        return (int) this.characters.stream()
                .filter(c -> c instanceof Hunter)
                .count();
    }

    public int countArtists() {
        return (int) this.characters.stream()
                .filter(c -> c instanceof Artist)
                .count();
    }

    public int countGatherers() {
        return (int) this.characters.stream()
                .filter(c -> c instanceof Gatherer)
                .count();
    }

    public int getShamansStars() {
        return this.characters.stream()
                .filter(c -> c instanceof Shaman)
                .mapToInt(c -> ((Shaman) c).getStars())
                .sum();
    }
    public int getCharactersSize(){
        return this.characters.size();
    }
    // TODO: once the building card is created we can check if the player has the building that has the zero pp rule for the shaman event
    public boolean hasBuildingForShamanRitual(){
        return false;
    }

}
