package it.polimi.ingsoft.mesos.server.persistence.game.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.board.UpperPile;
import it.polimi.ingsoft.mesos.server.model.board.LowerPile;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferPath;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.cards.TribeCard;
import it.polimi.ingsoft.mesos.server.model.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.game.*;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Gatherer;
import it.polimi.ingsoft.mesos.server.model.game.enums.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameSnapshotMapperTest {
    private static final String HOST = "host";

    @Test
    void toSnapshotMapsAllCriticalFieldsFromRuntimeGame() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-05-09T12:00:00Z"), ZoneOffset.UTC);
        GameSnapshotMapper mapper = new GameSnapshotMapper(fixedClock);

        SavedMatchSnapshot snapshot = mapper.toSnapshot(
                buildGame(),
                HOST
        );

        assertEquals(1, snapshot.schemaVersion());
        assertEquals("2026-05-09T12:00:00Z", snapshot.savedAtUtc());
        assertEquals(42, snapshot.game().id());
        assertEquals(HOST, snapshot.game().hostNickname());
        assertEquals(GameStatus.IN_PROGRESS.name(), snapshot.game().status());
        assertEquals(Era.first.name(), snapshot.game().currentEra());
        assertEquals(128L, snapshot.game().stateVersion());
        assertEquals(2, snapshot.players().size());
        assertEquals(4, snapshot.board().offerSpaces().size());
        assertEquals(RoundPhase.PLACING_TOTEMS.name(), snapshot.turnOrder().currentRoundPhase());
        assertEquals("WAITING_FOR_CARD_SELECTION", snapshot.turnState().phase());
    }

    @Test
    void toSnapshotSerializesAndDeserializesAsSnapshotDto() throws Exception {
        SavedMatchSnapshot snapshot = new GameSnapshotMapper().toSnapshot(buildGame(), HOST);
        ObjectMapper objectMapper = new ObjectMapper();
        String payload = objectMapper.writeValueAsString(snapshot);
        SavedMatchSnapshot restored = objectMapper.readValue(payload, SavedMatchSnapshot.class);

        assertEquals(snapshot, restored);
    }

    @Test
    void toSnapshotRejectsNullInputs() {
        GameSnapshotMapper mapper = new GameSnapshotMapper();

        assertThrows(IllegalArgumentException.class, () -> mapper.toSnapshot(null, HOST));
        assertThrows(IllegalArgumentException.class, () -> mapper.toSnapshot(buildGame(), " "));
    }

    private static Game buildGame() {
        Player alice = new Player("Alice", 0, 0, TotemColor.RED);
        Player bob = new Player("Bob", 0, 0, TotemColor.BLUE);
        List<Player> players = List.of(alice, bob);
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
                new UpperPile(new ArrayList<>(), new ArrayList<>()),
                new LowerPile(new ArrayList<>(), new ArrayList<>()),
                boardOfferPath(players)
        );
        TurnOrderTrack turnOrderTrack = new TurnOrderTrack(players, 0, 3, RoundPhase.PLACING_TOTEMS);
        PlayersTurnFSM playersTurnFSM = new PlayersTurnFSM();
        playersTurnFSM.registerDrawableCards(alice, 0, 0);
        playersTurnFSM.startCardSelection(alice);

        return new Game(42, players, playersTurnFSM, turnOrderTrack, board, Era.first, GameStatus.IN_PROGRESS, 128L);
    }

    private static OfferPath boardOfferPath(List<Player> players) {
        OfferPath offerPath = new OfferPath();
        offerPath.setup(players);
        offerPath.placePlayer(players.get(0), "A");
        return offerPath;
    }

    private static TribeCard createTribeCard(int id, Era era, TribeCardType type) {
        return new Gatherer(id, era, type);
    }
}
