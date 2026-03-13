package it.polimi.ingsoft.mesos.server.model.board.offerpath;


import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.ArrayList;
import java.util.List;

public class StartingTile {
    // Lista che contiene i blocchi di partenza ordinati (dal primo all'ultimo)
    private List<StartingBlock> startingBlocks;

    public StartingTile() {
        this.startingBlocks = new ArrayList<>();
    }

    /**
     * Inizializza i blocchi di partenza in base al numero di giocatori.
     */
    public void initializeBlocks(int playerCount) {
        // TODO: Istanziare qui i vari StartingBlock necessari in base a playerCount
    }

    /**
     * Riceve la lista dei giocatori già ordinata (es. dal TurnOrderTrack)
     * e li posiziona fisicamente nei blocchi corrispondenti.
     */
    public void placePlayers(List<Player> orderedPlayers) {
        // 1. Assicuriamoci che tutti i blocchi siano vuoti prima di iniziare
        this.startingBlocks.forEach(StartingBlock::clear);

        // 2. Iteriamo sui giocatori e li piazziamo nei blocchi in ordine sequenziale
        for (int i = 0; i < orderedPlayers.size(); i++) {
            // Controllo di sicurezza: verifichiamo che esista un blocco per il giocatore
            if (i < this.startingBlocks.size()) {
                StartingBlock currentBlock = this.startingBlocks.get(i);
                Player currentPlayer = orderedPlayers.get(i);

                // Il giocatore occupa il blocco
                currentBlock.occupy(currentPlayer);
            }
        }
    }

    /**
     * Risolve gli effetti di fine round per i giocatori posizionati sui blocchi.
     * La logica gestisce esclusivamente l'aumento o il pagamento di cibo,
     * applicando una penalità in punti prestigio in caso di cibo insufficiente.
     */
    public void resolveEffects() {

        for (StartingBlock block : this.startingBlocks) {
            Player player = block.getOccupiedBy();

            if (player != null) {

                int foodMod = block.getFoodModifier();

                // CASO 1: Il blocco fornisce cibo (Bonus)
                if (foodMod > 0) {
                    player.gainFood(foodMod);
                }
                // CASO 2: Il blocco richiede il pagamento di cibo (Malus/Costo)
                else if (foodMod < 0) {
                    int foodCost = Math.abs(foodMod);
                    if (player.getFood() >= foodCost) {
                        player.payFood(foodCost);
                    } else {
                        int prestigePenalty = block.getPrestigeModifier();
                        player.gainPrestigePoints(prestigePenalty);
                    }
                }
            }
        }
    }
}
