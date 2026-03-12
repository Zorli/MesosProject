package it.polimi.ingsoft.mesos.server.model.board.offerpath;

import it.polimi.ingsoft.mesos.server.model.GameState.Game;
import it.polimi.ingsoft.mesos.server.model.player.Player;

public class OfferSpace {
    private int positionIndex;
    private String code;
    private int minPlayers;
    private int foodBonus;
    private int topRowCardsToTake;
    private int bottomRowCardsToTake;
    private Player occupiedBy;

    /**
     * Costruttore con tutti i parametri necessari per il setup.
     */
    public OfferSpace(int positionIndex, String code, int minPlayers, int foodBonus,
                      int topRowCardsToTake, int bottomRowCardsToTake) {
        this.positionIndex = positionIndex;
        this.code = code;
        this.minPlayers = minPlayers;
        this.foodBonus = foodBonus;
        this.topRowCardsToTake = topRowCardsToTake;
        this.bottomRowCardsToTake = bottomRowCardsToTake;
        this.occupiedBy = null;
    }

    // --- METODI DUPLICATI DA STARTING BLOCK ---
    public void occupy(Player player) {
        this.occupiedBy = player;
    }

    public void clear() {
        this.occupiedBy = null;
    }

    public Player getOccupiedBy() {
        return this.occupiedBy;
    }

    // --- METODI SPECIFICI DI OFFER SPACE  ---

    /**
     * Determina se questo spazio esiste/è giocabile in base al numero di giocatori.
     */
    public boolean isEnabledFor(int playerCount) {
        return playerCount >= this.minPlayers;
    }

    /**
     * Controlla se lo spazio è libero per essere occupato durante il turno.
     */
    public boolean isAvailable() {
        return this.occupiedBy == null;
    }

    /**
     * Risolve l'azione quando un giocatore si piazza qui e preleva le carte.
     */
    public void resolveAction(Game game, Player player) {
        // 1. Assegna il bonus cibo immediato dello spazio
        if (this.foodBonus > 0) {
            player.gainFood(this.foodBonus);
        }

        // 2. Pesca dalla riga superiore manca il draftcard da game
//        for (int i = 0; i < this.topRowCardsToTake; i++) {
//            // game.draftCard restituisce 'true' se il giocatore ha pescato con successo,
//            // o 'false' se non c'erano carte valide o accessibili.
//            boolean success = game.draftCard(player, "UPPER");
//
//            // Se non ha potuto pescare (es. riga vuota o solo carte non acquistabili),
//            // interrompiamo le pescate successive per questa riga.
//            if (!success) {
//                break;
//            }
//        }

//        // 3. Pesca dalla riga inferiore
//        for (int i = 0; i < this.bottomRowCardsToTake; i++) {
//            // dopo che il giocatore ha scelta la cart
//            boolean success = game.draftCard(player, "LOWER");
//
//            if (!success) {
//                break;
//            }
//        }
    }

    // --- GETTERS (Per accedere ai valori se necessario) ---
    public int getPositionIndex() { return positionIndex; }
    public String getCode() { return code; }
}
