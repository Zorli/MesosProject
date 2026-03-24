package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.BigPpReward;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.MultiplierForType;
import it.polimi.ingsoft.mesos.server.model.cards.buildings.PrestigePointsForSet;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Builder;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Hunter;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.controller.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GameCalculateFinalScoreTest {

    private static Player player(String nickname, TotemColor color) {
        Player player = new Player(nickname, 0, 0, color, null);
        player.initializeGameAttributes();
        return player;
    }

    private static Game gameInFinalScoring(List<Player> players) {
        return new Game(
                1,
                new ArrayList<>(players),
                new TurnState(),
                new TurnOrderTrack(players, 0, 10, RoundPhase.FINAL_SCORING),
                null,
                Era.third,
                GameStatus.IN_PROGRESS
        );
    }

    @Test
    void calculateFinalScoreAppliesBuildingPpAndEffect() {
        Player alice = player("Alice", TotemColor.RED);
        // BigPpReward: pp printed = 2, extra reward = 25
        alice.getTribe().addBuilding(new BigPpReward(117, Era.third, 10, 2, BenefitType.FINAL_BONUS_BIG_PP_REWARD, 25));

        Game game = gameInFinalScoring(List.of(alice));
        game.calculateFinalScore();

        // 2 (building pp) + 25 (effect) + 0 (no builders) = 27
        assertEquals(27, alice.getPrestigePoints());
        assertEquals(GameStatus.FINISHED, game.getStatus());
    }

    @Test
    void calculateFinalScoreAppliesMultiplierForType() {
        Player alice = player("Alice", TotemColor.RED);
        alice.getTribe().addCharacter(new Hunter(1, Era.first, true, TribeCardType.HUNTER));
        alice.getTribe().addCharacter(new Hunter(2, Era.first, true, TribeCardType.HUNTER));
        // MultiplierForType: pp printed = 8, multiplier = 3 per HUNTER
        alice.getTribe().addBuilding(new MultiplierForType(110, Era.third, 8, 8, BenefitType.FINAL_BONUS_MULTIPLIER_FOR_TYPE, 3, TribeCardType.HUNTER));

        Game game = gameInFinalScoring(List.of(alice));
        game.calculateFinalScore();

        // 8 (building pp) + 2*3 (2 hunters * 3) + 0 (no builders) = 14
        assertEquals(14, alice.getPrestigePoints());
    }

    @Test
    void calculateFinalScoreAppliesPrestigePointsForSet() {
        Player alice = player("Alice", TotemColor.RED);
        // One character of each non-EVENT type to form 1 set
        alice.getTribe().addCharacter(new Hunter(1, Era.first, false, TribeCardType.HUNTER));
        alice.getTribe().addCharacter(new it.polimi.ingsoft.mesos.server.model.cards.characters.Gatherer(2, Era.first, TribeCardType.GATHERER));
        alice.getTribe().addCharacter(new it.polimi.ingsoft.mesos.server.model.cards.characters.Artist(3, Era.first, TribeCardType.ARTIST));
        alice.getTribe().addCharacter(new it.polimi.ingsoft.mesos.server.model.cards.characters.Shaman(4, Era.first, 1, TribeCardType.SHAMAN));
        alice.getTribe().addCharacter(new it.polimi.ingsoft.mesos.server.model.cards.characters.Inventor(5, Era.first, TribeCardType.INVENTOR, it.polimi.ingsoft.mesos.server.model.enums.InventionType.FIRE));
        alice.getTribe().addCharacter(new Builder(6, Era.first, TribeCardType.BUILDER, 0, 0));
        // PrestigePointsForSet: pp printed = 6, pointsPerSet = 6
        alice.getTribe().addBuilding(new PrestigePointsForSet(109, Era.third, 5, 6, BenefitType.FINAL_PRESTIGE_POINTS_FOR_SET, 6));

        Game game = gameInFinalScoring(List.of(alice));
        game.calculateFinalScore();

        // 6 (building pp) + 1*6 (1 complete set) + 0 (builder pp=0) = 12
        assertEquals(12, alice.getPrestigePoints());
    }

    @Test
    void calculateFinalScoreAppliesBuilderBonusOncePerPlayer() {
        Player alice = player("Alice", TotemColor.RED);
        // builder with prestigePoints=5
        alice.getTribe().addCharacter(new Builder(1, Era.first, TribeCardType.BUILDER, 0, 5));
        // two buildings: builder bonus must be applied only once, not twice
        alice.getTribe().addBuilding(new BuildingCard(200, Era.first, 0, 1, BenefitType.END_ROUND_EXTRA_CARD));
        alice.getTribe().addBuilding(new BuildingCard(201, Era.first, 0, 1, BenefitType.END_ROUND_EXTRA_CARD));

        Game game = gameInFinalScoring(List.of(alice));
        game.calculateFinalScore();

        // 1+1 (building pp) + 0 (no effects) + 5 (builder, once) = 7
        assertEquals(7, alice.getPrestigePoints());
    }

    @Test
    void calculateFinalScoreRejectsWrongStatus() {
        Player alice = player("Alice", TotemColor.RED);
        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 10, RoundPhase.FINAL_SCORING),
                null,
                Era.third,
                GameStatus.SETUP
        );

        assertThrows(InvalidGameStatusException.class, game::calculateFinalScore);
    }

    @Test
    void calculateFinalScoreRejectsWrongPhase() {
        Player alice = player("Alice", TotemColor.RED);
        Game game = new Game(
                1,
                new ArrayList<>(List.of(alice)),
                new TurnState(),
                new TurnOrderTrack(List.of(alice), 0, 10, RoundPhase.RESOLVING_EFFECTS),
                null,
                Era.third,
                GameStatus.IN_PROGRESS
        );

        assertThrows(InvalidGamePhaseException.class, game::calculateFinalScore);
    }

    @Test
    void calculateFinalScoreSetsStatusToFinished() {
        Player alice = player("Alice", TotemColor.RED);
        Game game = gameInFinalScoring(List.of(alice));

        game.calculateFinalScore();

        assertEquals(GameStatus.FINISHED, game.getStatus());
    }
}
