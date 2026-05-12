package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.Listener;
import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.board.CardRow;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class GameTestSupport {
    private GameTestSupport() {
    }

    static ArrayList<Player> players(String... nicknames) {
        ArrayList<Player> players = new ArrayList<>();
        TotemColor[] colors = TotemColor.values();

        for (int i = 0; i < nicknames.length; i++) {
            players.add(new Player(nicknames[i], 0, 0, colors[i]));
        }

        return players;
    }

    static Player player(String nickname, int food, TotemColor color) {
        return new Player(nickname, food, 0, color);
    }

    static void assertGameError(Game game, Class<? extends RuntimeException> expectedError, Runnable action) {
        RecordingListener listener = new RecordingListener();
        long previousVersion = game.getStateVersion();
        game.addListener(listener);

        action.run();

        assertEquals(previousVersion, game.getStateVersion());
        ErrorPayload errorPayload = assertInstanceOf(ErrorPayload.class, listener.payload);
        assertEquals(expectedError.getSimpleName(), errorPayload.code);
    }

    static final class RecordingListener implements Listener {
        private Object payload;

        @Override
        public void notifyUpdate(Object payload) {
            this.payload = payload;
        }

        Object payload() {
            return this.payload;
        }
    }

    static class RecordingBoard extends Board {
        boolean buildOfferPathCalled;
        boolean setupInitialRowsCalled;
        boolean resolveEventACalled;
        boolean resolveLowerEventsCalled;
        boolean resolveAllEventsCalled;
        boolean resetBoardAfterRoundCalled;
        boolean eraChanged;
        CardRow cardRow = CardRow.NOT_PRESENT;
        List<Card> upperCards = List.of();
        List<Card> lowerCards = List.of();
        List<Card> upperDraftableCards = List.of();
        List<Card> lowerDraftableCards = List.of();
        final Map<Player, List<Card>> upperDraftableCardsByPlayer = new HashMap<>();
        final Map<Player, List<Card>> lowerDraftableCardsByPlayer = new HashMap<>();
        final Map<Player, int[]> drawableCardsByPlayer = new HashMap<>();
        List<Player> resolutionOrder = List.of();
        List<Player> nextRoundTurnOrder = List.of();
        final List<Player> returnedPlayers = new ArrayList<>();
        boolean upperRemoved;
        boolean lowerRemoved;

        RecordingBoard() {
            super(null, null, new ArrayList<>());
        }

        RecordingBoard(List<Player> players) {
            super(null, null, players);
            this.resolutionOrder = new ArrayList<>(players);
            this.nextRoundTurnOrder = new ArrayList<>(players);
        }

        @Override
        public void buildOfferPath() {
            this.buildOfferPathCalled = true;
        }

        @Override
        public void setupInitialRows() {
            this.setupInitialRowsCalled = true;
        }

        @Override
        public boolean isSpaceAvailable(String targetCode) {
            return List.of("A", "B", "C", "D", "E").contains(targetCode);
        }

        @Override
        public void placePlayerOnOfferSpace(Player player, String targetCode) {
        }

        @Override
        public int[] returnNumberDrawableCard(Player player) {
            return this.drawableCardsByPlayer.getOrDefault(player, new int[]{1, 1});
        }

        @Override
        public List<Player> getResolutionOrder() {
            return new ArrayList<>(this.resolutionOrder);
        }

        @Override
        public List<Player> getNextRoundTurnOrder() {
            return new ArrayList<>(this.nextRoundTurnOrder);
        }

        @Override
        public void resolveEventA() {
            this.resolveEventACalled = true;
        }

        @Override
        public CardRow getCardRow(Card card) {
            return this.cardRow;
        }

        @Override
        public List<Card> getUpperRowCards() {
            return this.upperCards;
        }

        @Override
        public List<Card> getLowerRowCards() {
            return this.lowerCards;
        }

        @Override
        public List<Card> getDraftableUpperCards(Player player) {
            return this.upperDraftableCardsByPlayer.getOrDefault(player, this.upperDraftableCards);
        }

        @Override
        public List<Card> getDraftableLowerCards(Player player) {
            return this.lowerDraftableCardsByPlayer.getOrDefault(player, this.lowerDraftableCards);
        }

        @Override
        public void removeUpperCard(Card card) {
            this.upperRemoved = true;
            this.upperCards = without(this.upperCards, card);
            this.upperDraftableCards = without(this.upperDraftableCards, card);
            this.upperDraftableCardsByPlayer.replaceAll((player, cards) -> without(cards, card));
        }

        @Override
        public void removeLowerCard(Card card) {
            this.lowerRemoved = true;
            this.lowerCards = without(this.lowerCards, card);
            this.lowerDraftableCards = without(this.lowerDraftableCards, card);
            this.lowerDraftableCardsByPlayer.replaceAll((player, cards) -> without(cards, card));
        }

        @Override
        public void returnPlayerToStartingTile(Player player) {
            this.returnedPlayers.add(player);
        }

        @Override
        public void resolveLowerEvents() {
            this.resolveLowerEventsCalled = true;
        }

        @Override
        public void resolveAllEvents() {
            this.resolveAllEventsCalled = true;
        }

        @Override
        public boolean resetBoardAfterRound(Era currentEra) {
            this.resetBoardAfterRoundCalled = true;
            return this.eraChanged;
        }

        private static List<Card> without(List<Card> cards, Card removedCard) {
            return cards.stream()
                    .filter(card -> !card.equals(removedCard))
                    .toList();
        }
    }
}
