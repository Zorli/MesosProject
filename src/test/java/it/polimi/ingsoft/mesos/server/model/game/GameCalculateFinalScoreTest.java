package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.board.CardRow;
import it.polimi.ingsoft.mesos.server.model.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Builder;
import it.polimi.ingsoft.mesos.server.model.cards.characters.Hunter;
import it.polimi.ingsoft.mesos.server.model.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.GameStatus;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static it.polimi.ingsoft.mesos.server.model.game.GameTestSupport.player;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GameCalculateFinalScoreTest {

    @Test
    void finalScoringAppliesBuildingPrestigeEffectsAndBuilderBonusOnce() {
        Player alice = player("Alice", 5, TotemColor.RED);
        alice.getTribe().addCharacter(new Builder(1, Era.first, TribeCardType.BUILDER, 0, 5));
        alice.getTribe().addBuilding(new BuildingCard(100, Era.third, 0, 2, BenefitType.END_ROUND_EXTRA_CARD));
        alice.getTribe().addBuilding(new EffectBuildingCard(101, 3, 7));
        Hunter finalDraw = hunter(50);
        Game game = finalRoundSingleDrawGame(alice, finalDraw);

        game.chooseCard(alice, finalDraw.getId());

        assertEquals(17, alice.getPrestigePoints());
        assertEquals(GameStatus.FINISHED, game.getStatus());
    }

    @Test
    void finalScoringHandlesPlayersWithoutBuildings() {
        Player alice = player("Alice", 5, TotemColor.RED);
        Hunter finalDraw = hunter(51);
        Game game = finalRoundSingleDrawGame(alice, finalDraw);

        game.chooseCard(alice, finalDraw.getId());

        assertEquals(0, alice.getPrestigePoints());
        assertEquals(GameStatus.FINISHED, game.getStatus());
    }

    private static Game finalRoundSingleDrawGame(Player player, Hunter finalDraw) {
        GameTestSupport.RecordingBoard board = new GameTestSupport.RecordingBoard(List.of(player));
        board.cardRow = CardRow.LOWER;
        board.lowerCards = List.of(finalDraw);
        board.lowerDraftableCards = List.of(finalDraw);
        board.nextRoundTurnOrder = List.of(player);
        TurnState turnState = new TurnState();
        turnState.registerDrawableCards(player, 0, 1);
        return new Game(
                1,
                new ArrayList<>(List.of(player)),
                turnState,
                new TurnOrderTrack(List.of(player), 0, 10, RoundPhase.PLAYER_DRAWS),
                board,
                Era.third,
                GameStatus.IN_PROGRESS
        );
    }

    private static Hunter hunter(int id) {
        return new Hunter(id, Era.first, false, TribeCardType.HUNTER);
    }

    private static final class EffectBuildingCard extends BuildingCard {
        private final int effectPrestigePoints;

        private EffectBuildingCard(int id, int printedPrestigePoints, int effectPrestigePoints) {
            super(id, Era.third, 0, printedPrestigePoints, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
            this.effectPrestigePoints = effectPrestigePoints;
        }

        @Override
        public void applyEffect(Player player) {
            player.gainPrestigePoints(this.effectPrestigePoints);
        }
    }
}
