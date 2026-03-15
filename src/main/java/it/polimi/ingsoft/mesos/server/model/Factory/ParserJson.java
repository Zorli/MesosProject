package it.polimi.ingsoft.mesos.server.model.Factory;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;

public class ParserJson {
    //The scope of this class is to parse the json file containing the cards and then returning
    //the three decks based on the number of people
    private static final String DECK_SETUP_PATH = "src/main/java/it/polimi/ingsoft/mesos/server/model/Factory/deckSetup.json";


    public static TribeDeck parseTribeDeck() {
        String path = DECK_SETUP_PATH;
        return null;
    }

    public static BuildingDeck parseBuildingDeck() {
        String path = DECK_SETUP_PATH;
        return null;
    }
}
