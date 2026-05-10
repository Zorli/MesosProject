package it.polimi.ingsoft.mesos.server.model.game;

import it.polimi.ingsoft.mesos.server.model.board.Board;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.OfferSpace;
import it.polimi.ingsoft.mesos.server.model.board.offerpath.StartingBlock;
import it.polimi.ingsoft.mesos.server.model.cards.Card;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.BoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.CardPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.OfferSpacePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.PlayerPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.StartingBlockPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.TribePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.TurnOrderPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.TurnStatePayload;

import java.util.List;

final class GamePayloadMapper {
    private GamePayloadMapper() {
    }

    static GamePayload from(Game game) {
        return new GamePayload(
                game.getId(),
                game.getStatus().name(),
                game.getCurrentEra().ordinal(),
                toTurnOrderPayload(game.getTurnOrderTrack()),
                new TurnStatePayload(game.getTurnState().getPhase().name()),
                game.getPlayers().stream().map(GamePayloadMapper::toPlayerPayload).toList(),
                toBoardPayload(game.getBoard())
        );
    }

    static ErrorPayload toErrorPayload(String code, String message) {
        return new ErrorPayload(code, message);
    }

    private static TurnOrderPayload toTurnOrderPayload(TurnOrderTrack turnOrderTrack) {
        List<Player> order;
        try {
            order = turnOrderTrack.getTurnOrder();
        } catch (IllegalStateException e) {
            order = List.of();
        }

        String currentPlayerNickname = order.isEmpty()
                ? null
                : turnOrderTrack.getCurrentPlayer().getNickname();

        return new TurnOrderPayload(
                turnOrderTrack.getRoundNumber(),
                turnOrderTrack.getCurrentRoundPhase().name(),
                currentPlayerNickname,
                order.stream().map(Player::getNickname).toList()
        );
    }

    private static PlayerPayload toPlayerPayload(Player player) {
        List<CardPayload> characters = player.getTribe().getCharacters().stream()
                .map(GamePayloadMapper::toCardPayload)
                .toList();
        List<CardPayload> buildings = player.getTribe().getBuildings().stream()
                .map(GamePayloadMapper::toCardPayload)
                .toList();
        TribePayload tribe = new TribePayload(characters, buildings);

        return new PlayerPayload(
                player.getNickname(),
                player.getFood(),
                player.getPrestigePoints(),
                player.getTotem().name(),
                tribe
        );
    }

    private static CardPayload toCardPayload(Card card) {
        return new CardPayload(
                card.getId(),
                card.getClass().getSimpleName(),
                card.getEra().ordinal()
        );
    }

    private static BoardPayload toBoardPayload(Board board) {
        List<CardPayload> upperRow = board.getUpperRowCards().stream()
                .map(GamePayloadMapper::toCardPayload)
                .toList();
        List<CardPayload> lowerRow = board.getLowerRowCards().stream()
                .map(GamePayloadMapper::toCardPayload)
                .toList();
        List<OfferSpacePayload> offerSpaces = board.getOfferSpaces().stream()
                .map(GamePayloadMapper::toOfferSpacePayload)
                .toList();
        List<StartingBlockPayload> startingBlocks = board.getStartingBlocks().stream()
                .map(GamePayloadMapper::toStartingBlockPayload)
                .toList();

        return new BoardPayload(upperRow, lowerRow, offerSpaces, startingBlocks);
    }

    private static OfferSpacePayload toOfferSpacePayload(OfferSpace space) {
        String occupiedBy = space.getOccupiedBy() == null ? null : space.getOccupiedBy().getNickname();
        return new OfferSpacePayload(
                space.getCode(),
                space.getTopRowCardsToTake(),
                space.getBottomRowCardsToTake(),
                occupiedBy
        );
    }

    private static StartingBlockPayload toStartingBlockPayload(StartingBlock block) {
        String occupiedBy = block.getOccupiedBy() == null ? null : block.getOccupiedBy().getNickname();
        return new StartingBlockPayload(
                block.getPosition(),
                block.getFoodModifier(),
                block.getPrestigeModifier(),
                occupiedBy
        );
    }
}
