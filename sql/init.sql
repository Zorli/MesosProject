-- Schema for the Mesos game-results history (advanced feature "Classifica partite su DB").
--
-- This script is mounted into the MySQL container so the schema exists from the first
-- startup. It is intentionally redundant with GameResultRepository.ensureSchema(), which
-- issues the same statements when the server boots; keeping both makes the server
-- self-sufficient against a vanilla MySQL image while documenting the schema here.
--
-- Normalized model: every completed game is one row in `partite`, every distinct player
-- is one row in `utenti` (added on first appearance, kept afterwards), and the N-to-N
-- relationship between them is stored in `partecipazioni`, which also carries the score
-- the player obtained in that specific game.

CREATE TABLE IF NOT EXISTS utenti (
    id       BIGINT      AUTO_INCREMENT PRIMARY KEY,
    nickname VARCHAR(64) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS partite (
    id            BIGINT      AUTO_INCREMENT PRIMARY KEY,
    data          DATETIME(3) NOT NULL,
    num_giocatori INT         NOT NULL,
    INDEX idx_num_giocatori (num_giocatori)
);

CREATE TABLE IF NOT EXISTS partecipazioni (
    id_partita BIGINT NOT NULL,
    id_utente  BIGINT NOT NULL,
    punteggio  INT    NOT NULL,
    PRIMARY KEY (id_partita, id_utente),
    FOREIGN KEY (id_partita) REFERENCES partite(id),
    FOREIGN KEY (id_utente)  REFERENCES utenti(id),
    INDEX idx_punteggio (punteggio)
);
