package it.polimi.ingsoft.mesos.server.persistence.game;

import it.polimi.ingsoft.mesos.server.model.game.Game;

import java.util.List;

public interface MatchPersistence {
    void save(Game game, String hostNickname);

    List<RestoredMatch> loadActiveMatches();

    void delete(int gameId);

    static MatchPersistence disabled() {
        return new MatchPersistence() {
            @Override
            public void save(Game game, String hostNickname) {
            }

            @Override
            public List<RestoredMatch> loadActiveMatches() {
                return List.of();
            }

            @Override
            public void delete(int gameId) {
            }
        };
    }
}
