package it.polimi.ingsoft.mesos.server.model.game.game;

import it.polimi.ingsoft.mesos.server.model.game.board.Board;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingDeck;
import it.polimi.ingsoft.mesos.server.model.game.cards.TribeDeck;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Gatherer;
import it.polimi.ingsoft.mesos.server.model.game.enums.BenefitType;
import it.polimi.ingsoft.mesos.server.model.game.enums.Era;
import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.enums.TribeCardType;
import it.polimi.ingsoft.mesos.server.model.game.game.enums.RoundPhase;
import it.polimi.ingsoft.mesos.server.model.game.game.exception.CardRowSelectionNotAllowedException;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import it.polimi.ingsoft.mesos.server.model.game.player.Tribe;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtraDrawUpperRowOnlyTest {

    @Test
    void extraDrawOrderIgnoresPlayersWithOnlyLowerRowCardsAvailable() {
        Gatherer lowerCard = new Gatherer(1, Era.first, TribeCardType.GATHERER);
        Gatherer secondLowerCard = new Gatherer(2, Era.first, TribeCardType.GATHERER);
        Gatherer thirdLowerCard = new Gatherer(3, Era.first, TribeCardType.GATHERER);
        BuildingCard upperCard = new BuildingCard(4, Era.first, 0, 0, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        Game game = gameWithRows(lowerCard, secondLowerCard, thirdLowerCard, upperCard);
        Player alice = game.getPlayers().get(0);
        giveExtraDrawBuilding(alice);

        game.getRoundFlow().setCurrentPhaseOrder(List.of(alice));
        game.getRoundFlow().setCurrentRoundPhase(RoundPhase.PLAYER_DRAWS);
        game.getPlayerDraftState().registerDrawQuota(alice, 1, 0);

        StandardCardDraftHandler handler = new StandardCardDraftHandler(
                game,
                game.getBoard(),
                game.getRoundFlow(),
                game.getPlayerDraftState()
        );
        handler.chooseCard(alice, upperCard.getId());

        assertEquals(RoundPhase.RESOLVING_EFFECTS, game.getRoundFlow().getCurrentRoundPhase());
    }

    @Test
    void extraDrawnRejectsLowerRowCards() {
        Gatherer lowerCard = new Gatherer(11, Era.first, TribeCardType.GATHERER);
        Game game = gameWithRows(
                lowerCard,
                new Gatherer(12, Era.first, TribeCardType.GATHERER),
                new Gatherer(13, Era.first, TribeCardType.GATHERER),
                new BuildingCard(14, Era.first, 0, 0, BenefitType.FINAL_BONUS_BIG_PP_REWARD)
        );
        Player alice = game.getPlayers().get(0);
        giveExtraDrawBuilding(alice);
        prepareExtraDrawTurn(game, alice);

        ExtraCardDraftHandler handler = new ExtraCardDraftHandler(game, game.getBoard(), game.getRoundFlow());

        assertThrows(CardRowSelectionNotAllowedException.class, () -> handler.extraDrawn(alice, lowerCard.getId()));
        assertTrue(game.getBoard().getLowerRowCards().contains(lowerCard));
    }

    @Test
    void extraDrawnAcceptsUpperRowCards() {
        BuildingCard upperCard = new BuildingCard(24, Era.first, 0, 0, BenefitType.FINAL_BONUS_BIG_PP_REWARD);
        Game game = gameWithRows(
                new Gatherer(21, Era.first, TribeCardType.GATHERER),
                new Gatherer(22, Era.first, TribeCardType.GATHERER),
                new Gatherer(23, Era.first, TribeCardType.GATHERER),
                upperCard
        );
        Player alice = game.getPlayers().get(0);
        giveExtraDrawBuilding(alice);
        prepareExtraDrawTurn(game, alice);

        ExtraCardDraftHandler handler = new ExtraCardDraftHandler(game, game.getBoard(), game.getRoundFlow());
        handler.extraDrawn(alice, upperCard.getId());

        assertFalse(game.getBoard().getUpperRowCards().contains(upperCard));
        assertTrue(alice.getTribe().getBuildings().contains(upperCard));
        assertEquals(RoundPhase.RESOLVING_EFFECTS, game.getRoundFlow().getCurrentRoundPhase());
    }

    private static Game gameWithRows(Gatherer firstLowerCard,
                                     Gatherer secondLowerCard,
                                     Gatherer thirdLowerCard,
                                     BuildingCard upperCard) {
        List<Player> players = List.of(
                new Player("Alice", 10, 0, TotemColor.RED, new Tribe()),
                new Player("Bob", 10, 0, TotemColor.BLUE, new Tribe())
        );
        Board board = new Board(
                new TribeDeck(new ArrayList<>(List.of(firstLowerCard, secondLowerCard, thirdLowerCard))),
                new BuildingDeck(new ArrayList<>(List.of(upperCard))),
                players
        );
        Game game = new Game(1, players, board);
        game.setup();
        return game;
    }

    private static void giveExtraDrawBuilding(Player player) {
        player.getTribe().addBuilding(new BuildingCard(100, Era.first, 0, 0, BenefitType.END_ROUND_EXTRA_CARD));
    }

    private static void prepareExtraDrawTurn(Game game, Player player) {
        game.getRoundFlow().setCurrentPhaseOrder(List.of(player));
        game.getRoundFlow().setCurrentRoundPhase(RoundPhase.EXTRA_DRAWS);
    }
}
