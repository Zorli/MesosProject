package it.polimi.ingsoft.mesos.server.model.player;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Hunter;

import java.util.ArrayList;

public class Tribe {
    private ArrayList<TribeCard> characters;
    private ArrayList<BuildingCard> buildings;

    public void addCharacter(TribeCard c){
        this.characters.add(c);
    }

    public void addBuilding(BuildingCard b){
        this.buildings.add(b);
    }

    public int countHunters() {
        int count = 0;
        for (TribeCard character : this.characters) {
            if (character instanceof Hunter) {
                count++;
            }
        }
        return count;
    }

    public int countShamans() {
        return (int) this.characters.stream()
                .filter(c -> c instanceof it.polimi.ingsoft.mesos.server.model.cards.characters.Shaman)
                .count();
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

}
