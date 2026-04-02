package it.polimi.ingsoft.mesos.server.controller.Factory;

import it.polimi.ingsoft.mesos.server.model.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.player.Player;
import it.polimi.ingsoft.mesos.server.model.user.User;

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
     * Creates a randomized list of players starting from connected lobby users.
     *
     * @param users lobby users ready to enter the match
     * @return newly created player roster
     */
    public List<Player> createPlayers(List<User> users) {
        if (users == null) {
            throw new IllegalArgumentException("Users cannot be null");
        }
        if (users.isEmpty()) {
            throw new IllegalArgumentException("Users cannot be empty");
        }
        if (users.size() > AVAILABLE_TOTEMS.size()) {
            throw new IllegalArgumentException("Too many users for available totem colors");
        }

        List<User> shuffledUsers = new ArrayList<>(users);
        List<TotemColor> shuffledTotems = new ArrayList<>(AVAILABLE_TOTEMS);
        shuffledUsers.forEach(user -> Objects.requireNonNull(user, "User cannot be null"));
        Collections.shuffle(shuffledUsers);
        Collections.shuffle(shuffledTotems);

        List<Player> players = new ArrayList<>(shuffledUsers.size());
        for (int index = 0; index < shuffledUsers.size(); index++) {
            User user = shuffledUsers.get(index);
            players.add(new Player(user.getNickname(), 0, 0, shuffledTotems.get(index)));
        }
        return players;
    }
}
