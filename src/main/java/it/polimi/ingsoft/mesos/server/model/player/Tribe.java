package it.polimi.ingsoft.mesos.server.model.player;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.ShamanStarsBonus;
import it.polimi.ingsoft.mesos.server.model.cards.characters.*;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;

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
    public int getCharactersSize(){
        return this.characters.size();
    }

    public boolean hasBuildingForShamanRitualProtection(){
        for (BuildingCard b: buildings){
            if(b.getBenefitType() == BenefitType.EVENT_BONUS_LOSS_FROM_SHAMAN_EVENT){
                return true;
            }
        }
        return false;
    }

    public ArrayList<BuildingCard> getBuildings(){
        return this.buildings;
    }

    public ArrayList<CharacterCard> getCharacters(){
        return this.characters;
    }

    public boolean hasWinMultiplier(){
        for(BuildingCard b: buildings){
            if(b.getBenefitType() == BenefitType.EVENT_BONUS_SHAMAN_WIN_MULTIPLIER){
                return true;
            }
        }
        return false;
    }

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

}
