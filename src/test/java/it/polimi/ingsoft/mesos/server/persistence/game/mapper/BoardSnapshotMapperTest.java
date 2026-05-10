package it.polimi.ingsoft.mesos.server.persistence.game.mapper;

import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.board.UpperPile;
import it.polimi.ingsoft.mesos.server.model.board.LowerPile;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferPath;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Gatherer;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BoardSnapshotMapperTest {
    private final BoardSnapshotMapper mapper = new BoardSnapshotMapper();

    @Test
    void toSnapshotCapturesDeckRowsOfferSpacesAndStartingBlocks() {
        List<Player> players = List.of(
                new Player("Alice", 0, 0, TotemColor.RED),
                new Player("Bob", 0, 0, TotemColor.BLUE)
        );
        Board board = buildBoard(players);

        SavedMatchSnapshot.BoardSnapshot snapshot = this.mapper.toSnapshot(board);

        assertEquals(List.of(10, 11), snapshot.remainingTribeDeck());
        assertEquals(List.of(20, 21), snapshot.remainingBuildingDeck());
        assertEquals(List.of(12), snapshot.upperTribeCards());
        assertEquals(List.of(22), snapshot.upperBuildingCards());
        assertEquals(List.of(13), snapshot.lowerTribeCards());
        assertEquals(List.of(23), snapshot.lowerBuildingCards());

        SavedMatchSnapshot.OfferSpaceSnapshot firstSpace = snapshot.offerSpaces().get(0);
        assertEquals("B", firstSpace.code());
        assertEquals("Alice", firstSpace.occupiedByNickname());

        SavedMatchSnapshot.OfferSpaceSnapshot secondSpace = snapshot.offerSpaces().get(1);
        assertEquals("C", secondSpace.code());
        assertNull(secondSpace.occupiedByNickname());

        assertEquals(2, snapshot.startingBlocks().size());
        SavedMatchSnapshot.StartingBlockSnapshot occupiedBlock = snapshot.startingBlocks().stream()
                .filter(block -> block.occupiedByNickname() == null)
                .findFirst()
                .orElseThrow();
        assertEquals(1, occupiedBlock.position());

        SavedMatchSnapshot.StartingBlockSnapshot startingForBob = snapshot.startingBlocks().stream()
                .filter(block -> "Bob".equals(block.occupiedByNickname()))
                .findFirst()
                .orElseThrow();
        assertEquals(-2, startingForBob.prestigeModifier());
    }

    @Test
    void toSnapshotRejectsNullBoard() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> this.mapper.toSnapshot(null));
    }

    private static Board buildBoard(List<Player> players) {
        Board board = new Board(
                TribeDeck.fromOrderedCards(new ArrayList<>(List.of(
                        createTribeCard(10, Era.first, TribeCardType.GATHERER),
                        createTribeCard(11, Era.first, TribeCardType.EVENT)
                ))),
                BuildingDeck.fromOrderedCards(new ArrayList<>(List.of(
                        new BuildingCard(20, Era.first, 4, 1, BenefitType.FINAL_PRESTIGE_POINTS_FOR_SET),
                        new BuildingCard(21, Era.first, 5, 2, BenefitType.FINAL_PRESTIGE_POINTS_FOR_SET)
                ))),
                players,
                new UpperPile(
                        new ArrayList<>(List.of(
                                createTribeCard(12, Era.first, TribeCardType.GATHERER)
                        )),
                        new ArrayList<>(List.of(
                                new BuildingCard(22, Era.first, 1, 0, BenefitType.FINAL_PRESTIGE_POINTS_FOR_SET)
                        ))
                ),
                new LowerPile(
                        new ArrayList<>(List.of(
                                createTribeCard(13, Era.first, TribeCardType.GATHERER)
                        )),
                        new ArrayList<>(List.of(
                                new BuildingCard(23, Era.first, 2, 0, BenefitType.FINAL_PRESTIGE_POINTS_FOR_SET)
                        ))
                ),
                boardOfferPath(players)
        );

        return board;
    }

    private static OfferPath boardOfferPath(List<Player> players) {
        OfferPath offerPath = new OfferPath();
        offerPath.setup(players);
        offerPath.placePlayer(players.get(0), "B");
        return offerPath;
    }

    private static TribeCard createTribeCard(int id, Era era, TribeCardType type) {
        return new Gatherer(id, era, type);
    }
}
