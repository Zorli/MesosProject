package it.polimi.ingsoft.mesos.server.testing;

import it.polimi.ingsoft.mesos.server.controller.Factory.ParserJson;
import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.GameState.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.GameState.TurnOrderTrack;
import it.polimi.ingsoft.mesos.server.model.GameState.TurnState;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.board.LowerPile;
import it.polimi.ingsoft.mesos.server.model.board.UpperPile;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferPath;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferSpace;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.StartingTile;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds advanced runtime scenarios used by save/restore and late-game tests.
 */
public final class AdvancedGameStateFixtures {
    private AdvancedGameStateFixtures() {
    }

    /**
     * Creates a late-game scenario already positioned in the extra-draw phase.
     *
     * <p>Alice owns the extra-draw building and can take one visible upper-row character. Bob is
     * already first in the prepared starting order of the next round.
     *
     * @return game ready to exercise {@code extraDrawn(...)} and the following resolve-effects step
     */
    public static Game createExtraDrawScenario() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        Player alice = players.getFirst();
        Player bob = players.get(1);

        alice.getTribe().addBuilding((BuildingCard) card(116));

        Board board = new Board(
                tribeDeck(67, 68),
                buildingDeck(117),
                players,
                new UpperPile(List.of((TribeCard) card(66)), List.of()),
                new LowerPile(List.of(), List.of()),
                offerPathFor(List.of(bob, alice), 2)
        );

        return new Game(
                900,
                players,
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 8, RoundPhase.EXTRA_DRAWS),
                board,
                Era.third,
                GameStatus.IN_PROGRESS
        );
    }

    /**
     * Creates a late-game scenario ready for final scoring with real catalog cards.
     *
     * <p>Alice owns one builder worth three prestige points and the big-reward final-scoring
     * building worth twenty-five prestige points.
     *
     * @return game ready to execute {@code calculateFinalScore()}
     */
    public static Game createFinalScoringScenario() {
        ArrayList<Player> players = MesosTestFixtures.createPlayers("Alice", "Bob");
        Player alice = players.getFirst();

        alice.getTribe().addCharacter((it.polimi.ingsoft.mesos.server.model.cards.characters.CharacterCard) card(6));
        alice.getTribe().addBuilding((BuildingCard) card(117));

        Board board = new Board(
                tribeDeck(),
                buildingDeck(),
                players,
                new UpperPile(List.of(), List.of()),
                new LowerPile(List.of(), List.of()),
                offerPathFor(players, 2)
        );

        return new Game(
                901,
                players,
                new TurnState(),
                new TurnOrderTrack(List.of(players.getFirst(), players.get(1)), 0, 10, RoundPhase.FINAL_SCORING),
                board,
                Era.third,
                GameStatus.IN_PROGRESS
        );
    }

    private static OfferPath offerPathFor(List<Player> nextRoundOrder, int playerCount) {
        StartingTile startingTile = new StartingTile();
        startingTile.setup(nextRoundOrder);
        return new OfferPath(startingTile, activeOfferSpacesFor(playerCount));
    }

    private static List<OfferSpace> activeOfferSpacesFor(int playerCount) {
        ArrayList<OfferSpace> spaces = new ArrayList<>();
        List<OfferSpace> allSpaces = List.of(
                new OfferSpace(1, "A", 5, 3, 0, 0),
                new OfferSpace(2, "B", 2, 0, 0, 1),
                new OfferSpace(3, "C", 2, 0, 1, 0),
                new OfferSpace(4, "D", 3, 0, 0, 2),
                new OfferSpace(5, "E", 2, 0, 1, 1),
                new OfferSpace(6, "F", 2, 0, 2, 0),
                new OfferSpace(7, "G", 4, 0, 2, 1)
        );

        for (OfferSpace space : allSpaces) {
            if (space.getMinPlayers() <= playerCount) {
                spaces.add(space);
            }
        }

        return spaces;
    }

    private static TribeDeck tribeDeck(int... cardIds) {
        ArrayList<Card> cards = new ArrayList<>();
        for (int cardId : cardIds) {
            cards.add(card(cardId));
        }
        return TribeDeck.fromOrderedCards(cards);
    }

    private static BuildingDeck buildingDeck(int... cardIds) {
        ArrayList<Card> cards = new ArrayList<>();
        for (int cardId : cardIds) {
            cards.add(card(cardId));
        }
        return BuildingDeck.fromOrderedCards(cards);
    }

    private static Card card(int cardId) {
        return ParserJson.getDeckTemplate().createCardById(cardId);
    }
}
