package it.polimi.ingsoft.mesos.server.persistence.game.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.polimi.ingsoft.mesos.server.controller.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.Game;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.persistence.game.dto.SavedMatchSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RealGameSerializationTest {
    @Test
    void realGameAfterSetupSerializesWithDefaultObjectMapper() {
        Player alice = new Player("Alice", 0, 0, TotemColor.RED);
        Player bob = new Player("Bob", 0, 0, TotemColor.BLUE);
        List<Player> players = new ArrayList<>(List.of(alice, bob));

        Game game = DefaultGameFactory.getInstance().createGame(players);
        game.setup();

        GameSnapshotMapper mapper = new GameSnapshotMapper();
        SavedMatchSnapshot snapshot = mapper.toSnapshot(game, "Alice");

        ObjectMapper om = new ObjectMapper();
        assertDoesNotThrow(() -> om.writeValueAsString(snapshot));
    }
}
