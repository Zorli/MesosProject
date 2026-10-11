package it.polimi.ingsoft.mesos.server.factory;

import it.polimi.ingsoft.mesos.server.model.game.enums.TotemColor;
import it.polimi.ingsoft.mesos.server.model.game.player.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the initial player roster for a lobby-backed match.
 *
 * <p>The factory preserves the lobby nickname order and uses the totem color
 * selected by each lobby player before the match starts.
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
     * Creates match players starting from lobby nicknames and their selected totem colors.
     *
     * @param nicknames lobby users ready to enter the match, in lobby order
     * @param totemColorByNickname map from player nickname to selected totem color
     * @return newly created player roster
     * @throws IllegalArgumentException if {@code nicknames} is null, empty, contains a
     *                                  null entry, exceeds the available totem colors,
     *                                  or does not match the totem-color map exactly
     */
    public List<Player> createPlayers(List<String> nicknames, Map<String, TotemColor> totemColorByNickname) {
        if (nicknames == null) {
            throw new IllegalArgumentException("Nicknames cannot be null");
        }
        if (nicknames.isEmpty()) {
            throw new IllegalArgumentException("Nicknames cannot be empty");
        }
        for (String nickname : nicknames) {
            if (nickname == null) {
                throw new IllegalArgumentException("Nicknames cannot contain null entries");
            }
        }
        if (totemColorByNickname == null) {
            throw new IllegalArgumentException("Totem color by nickname map cannot be null");
        }
        if (!totemColorByNickname.keySet().equals(Set.copyOf(nicknames))) {
            throw new IllegalArgumentException("Totem color by nickname map must contain exactly the lobby players");
        }

        if (nicknames.size() > AVAILABLE_TOTEMS.size()) {
            throw new IllegalArgumentException("Too many nicknames for available totem colors");
        }

        List<Player> players = new ArrayList<>();

        for (String nickname : nicknames) {
            TotemColor selectedColor = totemColorByNickname.get(nickname);
            if (selectedColor == null) {
                throw new IllegalArgumentException("Missing totem color for player: " + nickname);
            }

            players.add(new Player(nickname, 0, 0, selectedColor));
        }

        return players;
    }
}
