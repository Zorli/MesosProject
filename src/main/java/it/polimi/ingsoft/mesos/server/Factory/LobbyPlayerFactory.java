package it.polimi.ingsoft.mesos.server.factory;

import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Builds the initial player roster for a lobby-backed match.
 *
 * <p>The current policy randomizes the user order and assigns shuffled totem colors before
 * creating fresh match-scoped {@link Player} instances.
 */
public class LobbyPlayerFactory {
    private static final List<TotemColor> AVAILABLE_TOTEMS = List.of(
            TotemColor.RED,
            TotemColor.BLUE,
            TotemColor.WHITE,
            TotemColor.YELLOW,
            TotemColor.BLACK
    );

    /**
     * Creates a randomized list of players starting from connected lobby nicknames.
     *
     * @param nicknames lobby users ready to enter the match
     * @return newly created player roster
     */
    public List<Player> createPlayers(List<String> nicknames) {
        if (nicknames == null) {
            throw new IllegalArgumentException("Nicknames cannot be null");
        }
        if (nicknames.isEmpty()) {
            throw new IllegalArgumentException("Nicknames cannot be empty");
        }
        if (nicknames.size() > AVAILABLE_TOTEMS.size()) {
            throw new IllegalArgumentException("Too many nicknames for available totem colors");
        }

        List<String> shuffledNicknames = new ArrayList<>(nicknames);
        List<TotemColor> shuffledTotems = new ArrayList<>(AVAILABLE_TOTEMS);
        shuffledNicknames.forEach(nickname -> Objects.requireNonNull(nickname, "Nickname cannot be null"));
        Collections.shuffle(shuffledNicknames);
        Collections.shuffle(shuffledTotems);

        List<Player> players = new ArrayList<>(shuffledNicknames.size());
        for (int index = 0; index < shuffledNicknames.size(); index++) {
            players.add(new Player(shuffledNicknames.get(index), 0, 0, shuffledTotems.get(index)));
        }
        return players;
    }
}
