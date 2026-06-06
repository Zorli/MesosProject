package it.polimi.ingsoft.mesos.server.model.game.game;

import it.polimi.ingsoft.mesos.server.model.game.board.Board;
import it.polimi.ingsoft.mesos.server.model.game.board.offerpath.OfferSpace;
import it.polimi.ingsoft.mesos.server.model.game.board.offerpath.StartingBlock;
import it.polimi.ingsoft.mesos.server.model.game.cards.BuildingCard;
import it.polimi.ingsoft.mesos.server.model.game.cards.Card;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Builder;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Hunter;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Inventor;
import it.polimi.ingsoft.mesos.server.model.game.cards.characters.Shaman;
import it.polimi.ingsoft.mesos.server.model.game.cards.events.EventCard;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.error.ErrorPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.BoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.CardPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.GamePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.OfferSpacePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.PlayerPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.StartingBlockPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.TribePayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.TurnOrderPayload;
import it.polimi.ingsoft.mesos.shared.payload.servertoclient.game.TurnStatePayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts the internal game model into payload objects sent to clients.
 *
 * <p>The server model contains rich Java objects such as {@link Game},
 * {@link Board}, {@link Player}, and card instances. Clients must not receive
 * those model objects directly. They receive simple serializable payloads that
 * describe the visible state of the match.
 *
 * <p>This mapper is deliberately separate from {@link Game}. The game owns the
 * rules and state transitions; this class owns only the translation from model
 * objects to network DTOs.
 */
final class GamePayloadMapper {
    /**
     * Builds the payload representing the current game state.
     *
     * @param game game to convert
     * @return serializable game payload
     */
    static GamePayload from(Game game) {
        return new GamePayload(
                game.getId(),
                game.getStatus().name(),
                game.getCurrentEra().getIntegerEra(),
                toTurnOrderPayload(game.getRoundFlow()),
                new TurnStatePayload(game.getPlayerDraftState().getPhase().name()),
                toPlayerPayloads(game.getPlayers(),game),
                toBoardPayload(game.getBoard())
        );
    }

    /**
     * Builds an error payload.
     *
     * @param code machine-readable error code
     * @param message human-readable error message
     * @return serializable error payload
     */
    static ErrorPayload toErrorPayload(String code, String message) {
        return new ErrorPayload(code, message);
    }

    /**
     * Converts round flow into the turn-order payload visible to clients.
     *
     * <p>The payload contains the current round number, current round phase,
     * current player nickname, and the ordered nicknames of the players involved
     * in the active phase.
     *
     * @param roundFlow round flow owned by the game
     * @return payload describing current phase order
     */
    private static TurnOrderPayload toTurnOrderPayload(RoundFlow roundFlow) {
        List<Player> order = getCurrentPhaseOrderOrEmpty(roundFlow);
        String currentPlayerNickname = getCurrentPlayerNicknameOrNull(roundFlow, order);
        List<String> nicknames = toPlayerNicknames(order);

        return new TurnOrderPayload(
                roundFlow.getRoundNumber(),
                roundFlow.getCurrentRoundPhase().name(),
                currentPlayerNickname,
                nicknames
        );
    }

    /**
     * Reads the current phase order, returning an empty order before setup.
     *
     * <p>A newly created game has no initialized order until setup runs. The
     * payload must still be buildable in that state, so the mapper translates
     * the "order not initialized" condition into an empty payload list.
     *
     * @param roundFlow round flow to read
     * @return current phase order, or an empty list if setup has not initialized it
     */
    private static List<Player> getCurrentPhaseOrderOrEmpty(RoundFlow roundFlow) {
        try {
            return roundFlow.getCurrentPhaseOrder();
        } catch (IllegalStateException exception) {
            return List.of();
        }
    }

    /**
     * Returns the current player's nickname when an order exists.
     *
     * <p>If the order is empty, there cannot be a current player in the payload,
     * so the method returns {@code null}.
     *
     * @param roundFlow round flow containing the current-player cursor
     * @param order already-read current phase order
     * @return current player nickname, or {@code null} when no order exists
     */
    private static String getCurrentPlayerNicknameOrNull(RoundFlow roundFlow, List<Player> order) {
        if (order.isEmpty()) {
            return null;
        }

        return roundFlow.getCurrentPlayer().getNickname();
    }

    /**
     * Converts players into the nickname list used by the network payload.
     *
     * @param players ordered players to convert
     * @return ordered player nicknames
     */
    private static List<String> toPlayerNicknames(List<Player> players) {
        List<String> nicknames = new ArrayList<>();

        for (Player player : players) {
            nicknames.add(player.getNickname());
        }

        return nicknames;
    }

    /**
     * Converts all game players into client payloads.
     *
     * @param players model players
     * @param game
     * @return payload representation of every player
     */
    private static List<PlayerPayload> toPlayerPayloads(List<Player> players, Game game) {
        List<PlayerPayload> payloads = new ArrayList<>();

        for (Player player : players) {
            payloads.add(toPlayerPayload(player,game));
        }

        return payloads;
    }

    /**
     * Converts one model player into a client payload.
     *
     * <p>The payload contains only client-visible data: nickname, resources,
     * prestige points, totem color, and tribe cards.
     *
     * @param player player to convert
     * @param game
     * @return payload representation of the player
     */
    private static PlayerPayload toPlayerPayload(Player player, Game game) {
        TribePayload tribePayload = toTribePayload(player);

        return new PlayerPayload(
                player.getNickname(),
                player.getFood(),
                player.getPrestigePoints(),
                player.getTotem().name(),
                tribePayload,
                Boolean.TRUE.equals(game.getConnectedPlayers().get(player))
        );
    }

    /**
     * Converts the player's tribe into character and building payload lists.
     *
     * @param player player whose tribe must be converted
     * @return payload representation of the player's tribe
     */
    private static TribePayload toTribePayload(Player player) {
        List<CardPayload> characterPayloads = toCardPayloads(player.getTribe().getCharacters());
        List<CardPayload> buildingPayloads = toCardPayloads(player.getTribe().getBuildings());

        return new TribePayload(characterPayloads, buildingPayloads);
    }

    /**
     * Converts all visible board information into a client payload.
     *
     * <p>The board payload exposes visible upper-row cards, lower-row cards,
     * offer spaces, and starting blocks. Hidden deck contents are intentionally
     * not included.
     *
     * @param board board to convert
     * @return payload representation of the visible board
     */
    private static BoardPayload toBoardPayload(Board board) {
        List<CardPayload> upperRow = toCardPayloads(board.getUpperRowCards());
        List<CardPayload> lowerRow = toCardPayloads(board.getLowerRowCards());
        List<OfferSpacePayload> offerSpaces = toOfferSpacePayloads(board.getOfferSpaces());
        List<StartingBlockPayload> startingBlocks = toStartingBlockPayloads(board.getStartingBlocks());

        return new BoardPayload(upperRow, lowerRow, offerSpaces, startingBlocks);
    }

    /**
     * Converts a list of model cards into serializable card payloads.
     *
     * @param cards cards to convert
     * @return card payload list in the same order
     */
    private static List<CardPayload> toCardPayloads(List<? extends Card> cards) {
        List<CardPayload> payloads = new ArrayList<>();

        for (Card card : cards) {
            payloads.add(toCardPayload(card));
        }

        return payloads;
    }

    /**
     * Converts one model card into the minimal card data needed by clients.
     *
     * @param card card to convert
     * @return payload containing id, runtime card type, and era ordinal
     */
    private static CardPayload toCardPayload(Card card) {
        int id = card.getId();
        int era = card.getEra().getIntegerEra();
        String type = card.getCardType();

        if (card instanceof Shaman shaman) {
            return CardPayload.shaman(id, type, era, shaman.getStars());
        }
        if (card instanceof Hunter hunter) {
            return CardPayload.hunter(id, type, era, hunter.getHasEffect());
        }
        if (card instanceof Inventor inventor) {
            return CardPayload.inventor(id, type, era, inventor.getInventionType().name());
        }
        if (card instanceof Builder builder) {
            return CardPayload.builder(id, type, era, builder.getPriceDiscount(), builder.getPrestigePoints());
        }
        if (card instanceof BuildingCard buildingCard) {
            return CardPayload.building(id, type, era, buildingCard.getFoodCost(), buildingCard.getPrestigePoints());
        }
        if(card instanceof EventCard eventCard){
            return CardPayload.event(id, type, era, eventCard.getCompleteName());
        }

        return CardPayload.base(id, type, era);
    }

    /**
     * Converts all offer spaces into payloads.
     *
     * @param spaces model offer spaces
     * @return payload list in board order
     */
    private static List<OfferSpacePayload> toOfferSpacePayloads(List<OfferSpace> spaces) {
        List<OfferSpacePayload> payloads = new ArrayList<>();

        for (OfferSpace space : spaces) {
            payloads.add(toOfferSpacePayload(space));
        }

        return payloads;
    }

    /**
     * Converts one offer space into client-visible data.
     *
     * <p>The occupying player is represented by nickname because payloads must
     * not expose model object references.
     *
     * @param space offer space to convert
     * @return payload representation of the offer space
     */
    private static OfferSpacePayload toOfferSpacePayload(OfferSpace space) {
        String occupiedByNickname = null;

        if (space.getOccupiedBy() != null) {
            occupiedByNickname = space.getOccupiedBy().getNickname();
        }

        return new OfferSpacePayload(
                space.getCode(),
                space.getTopRowCardsToTake(),
                space.getBottomRowCardsToTake(),
                occupiedByNickname
        );
    }

    /**
     * Converts all starting blocks into payloads.
     *
     * @param blocks starting tile blocks
     * @return payload list in starting-tile order
     */
    private static List<StartingBlockPayload> toStartingBlockPayloads(List<StartingBlock> blocks) {
        List<StartingBlockPayload> payloads = new ArrayList<>();

        for (StartingBlock block : blocks) {
            payloads.add(toStartingBlockPayload(block));
        }

        return payloads;
    }

    /**
     * Converts one starting block into client-visible data.
     *
     * <p>The block contains its position, modifiers, and the nickname of the
     * player currently occupying it, if any.
     *
     * @param block starting block to convert
     * @return payload representation of the starting block
     */
    private static StartingBlockPayload toStartingBlockPayload(StartingBlock block) {
        String occupiedByNickname = null;

        if (block.getOccupiedBy() != null) {
            occupiedByNickname = block.getOccupiedBy().getNickname();
        }

        return new StartingBlockPayload(
                block.getPosition(),
                block.getFoodModifier(),
                block.getPrestigeModifier(),
                occupiedByNickname
        );
    }
}
