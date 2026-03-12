package it.polimi.ingsoft.mesos.server.model.board.offerpath;
import it.polimi.ingsoft.mesos.server.model.player.Player;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class OfferPath {
    private StartingTile startingTile;
    private List<OfferSpace> offerSpaces;

    public OfferPath(StartingTile startingTile, List<OfferSpace> offerSpaces) {
        this.startingTile = startingTile;
        this.offerSpaces = offerSpaces;
    }

    public void clearOfferSpaces() {
        this.offerSpaces.forEach(OfferSpace::clear);
    }
    /**
     * Estrae i giocatori dagli spazi occupati, ordinandoli in base all'indice dello spazio. serve per ordertrack
     * essendo offerpath ad avere il riferimento agli offerspace
     */
    public List<Player> getPlayersInOrder() {
        return this.offerSpaces.stream()
                .filter(space -> space.getOccupiedBy() != null)
                .sorted(Comparator.comparingInt(OfferSpace::getPositionIndex))
                .map(OfferSpace::getOccupiedBy)
                .collect(Collectors.toList());
    }
    public void returnPlayersToStartingTile() {
        this.clearOfferSpaces();
        }
    }
