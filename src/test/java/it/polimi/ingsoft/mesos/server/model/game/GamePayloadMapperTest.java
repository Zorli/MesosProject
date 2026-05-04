package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.board.LowerPile;
import it.polimi.ingsoft.mesos.server.model.board.UpperPile;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferPath;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferSpace;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.StartingBlock;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.StartingTile;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Gatherer;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.shared.payload.server.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.server.GamePayload;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GamePayloadMapperTest {

    @Test
    void fromMapsCompleteGameSnapshotToSerializablePayload() {
        Player alice = new Player("Alice", 4, 7, TotemColor.RED);
        Player bob = new Player("Bob", 2, 1, TotemColor.BLUE);
        Gatherer ownedCharacter = new Gatherer(1, Era.first, TribeCardType.GATHERER);
        BuildingCard ownedBuilding = new BuildingCard(2, Era.second, 0, 3, BenefitType.END_ROUND_EXTRA_CARD);
        alice.getTribe().addCharacter(ownedCharacter);
        alice.getTribe().addBuilding(ownedBuilding);

        Gatherer upperCard = new Gatherer(3, Era.first, TribeCardType.GATHERER);
        BuildingCard lowerCard = new BuildingCard(4, Era.third, 1, 2, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        OfferSpace occupiedSpace = new OfferSpace(1, "B", 2, 0, 1, 0);
        occupiedSpace.occupy(alice);
        OfferSpace emptySpace = new OfferSpace(2, "C", 2, 0, 0, 1);
        StartingBlock occupiedBlock = new StartingBlock(1, 2, 0, bob);
        StartingBlock emptyBlock = new StartingBlock(2, -1, -2, null);

        Board board = new Board(
                TribeDeck.fromOrderedCards(new ArrayList<>()),
                BuildingDeck.fromOrderedCards(new ArrayList<>()),
                List.of(alice, bob),
                new UpperPile(List.of(upperCard), List.of()),
                new LowerPile(List.of(), List.of(lowerCard)),
                new OfferPath(new StartingTile(List.of(occupiedBlock, emptyBlock)), List.of(occupiedSpace, emptySpace))
        );
        Game game = new Game(
                42,
                new ArrayList<>(List.of(alice, bob)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice, bob), 0, 5, RoundPhase.PLAYER_DRAWS),
                board,
                Era.second,
                GameStatus.IN_PROGRESS
        );

        GamePayload payload = GamePayloadMapper.from(game);

        assertEquals(42, payload.id);
        assertEquals("IN_PROGRESS", payload.status);
        assertEquals(Era.second.ordinal(), payload.currentEra);
        assertEquals(5, payload.turnOrder.roundNumber);
        assertEquals("PLAYER_DRAWS", payload.turnOrder.currentRoundPhase);
        assertEquals("Alice", payload.turnOrder.currentPlayerNickname);
        assertEquals(List.of("Alice", "Bob"), payload.turnOrder.turnOrder);
        assertEquals("TURN_COMPLETED", payload.turnState.phase);
        assertEquals("Alice", payload.players.get(0).nickname);
        assertEquals("RED", payload.players.get(0).totem);
        assertEquals(4, payload.players.get(0).food);
        assertEquals(7, payload.players.get(0).prestigePoints);
        assertEquals(ownedCharacter.getId(), payload.players.get(0).tribe.characters.get(0).id);
        assertEquals(ownedBuilding.getId(), payload.players.get(0).tribe.buildings.get(0).id);
        assertEquals(upperCard.getId(), payload.board.upperRow.get(0).id);
        assertEquals(lowerCard.getId(), payload.board.lowerRow.get(0).id);
        assertEquals("Alice", payload.board.offerSpaces.get(0).occupiedByNickname);
        assertNull(payload.board.offerSpaces.get(1).occupiedByNickname);
        assertEquals("Bob", payload.board.startingBlocks.get(0).occupiedByNickname);
        assertNull(payload.board.startingBlocks.get(1).occupiedByNickname);
    }

    @Test
    void fromHandlesTurnOrderThatHasNotBeenInitializedYet() {
        Player alice = new Player("Alice", 0, 0, TotemColor.RED);
        Board board = new Board(
                TribeDeck.fromOrderedCards(new ArrayList<>()),
                BuildingDeck.fromOrderedCards(new ArrayList<>()),
                List.of(alice),
                new UpperPile(),
                new LowerPile(),
                new OfferPath(new StartingTile(), List.of())
        );
        Game game = new Game(1, new ArrayList<>(List.of(alice)), new TurnState(), new TurnOrderTrack(), board);

        GamePayload payload = GamePayloadMapper.from(game);

        assertEquals(List.of(), payload.turnOrder.turnOrder);
        assertNull(payload.turnOrder.currentPlayerNickname);
        assertEquals("PLACING_TOTEMS", payload.turnOrder.currentRoundPhase);
    }

    @Test
    void toErrorPayloadPreservesCodeAndMessage() {
        ErrorPayload payload = GamePayloadMapper.toErrorPayload("CODE", "message");

        assertEquals("CODE", payload.code);
        assertEquals("message", payload.message);
    }
}
