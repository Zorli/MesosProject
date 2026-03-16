package it.polimi.ingsoft.mesos.server.model.GameState;

import it.polimi.ingsoft.mesos.server.model.enums.TurnPhase;


/*
    This class is meant to be the FSM that check every step in the single player turn.
*/
//TODO la posizione sulle pedine è quella di iscrizione in lobby

public class TurnState {
    private TurnPhase phase;
    //If this player has the building that gives the perk to take another card, how we handle it?
    //At this point is the offer path that prescribes how much card the player can take
    private int remainingUpperChoices;
    private int remainingLowerChoices;

    public TurnState() {
        this.phase = TurnPhase.WAITING_FOR_TOTEM_MOVE;
        this.remainingUpperChoices = 0;
        this.remainingLowerChoices = 0;
    }

    public TurnPhase getPhase() {
        return this.phase;
    }

    public boolean canMoveTotem() {
        return this.phase == TurnPhase.WAITING_FOR_TOTEM_MOVE;
    }

    public boolean canChooseFromUpperRow() {
        return this.phase == TurnPhase.WAITING_FOR_CARD_SELECTION
                && this.remainingUpperChoices > 0;
    }

    public boolean canChooseFromLowerRow() {
        return this.phase == TurnPhase.WAITING_FOR_CARD_SELECTION
                && this.remainingLowerChoices > 0;
    }

    public void onTotemMoved(int upperChoices, int lowerChoices) {
        if (!canMoveTotem()) {
            throw new IllegalStateException("Totem move is not allowed in this phase ");
        }

        if (upperChoices < 0 || lowerChoices < 0) {
            throw new IllegalArgumentException("Must Be positive numbers");
        }
        this.phase = TurnPhase.WAITING_FOR_CARD_SELECTION;
        this.remainingUpperChoices = upperChoices;
        this.remainingLowerChoices = lowerChoices;
    }

    public void onUpperCardChosen() {
        if (!canChooseFromUpperRow()) {
            throw new IllegalStateException("Cannot choose from upper row");
        }

        this.remainingUpperChoices -= 1;
        this.updatePhaseAfterCardChoice();
    }

    public void onLowerCardChosen() {
        if (!canChooseFromLowerRow()) {
            throw new IllegalStateException("Cannot take card from lower row");
        }

        this.remainingLowerChoices -= 1;
        this.updatePhaseAfterCardChoice();
    }

    public boolean isTurnCompleted() {
        return this.phase == TurnPhase.TURN_COMPLETED;
    }

    public void resetForNextPlayer() {
        if(!(this.phase == TurnPhase.TURN_COMPLETED)){throw  new IllegalStateException("Cannot reset for next player untile the current one hase finished");}
        this.phase = TurnPhase.WAITING_FOR_TOTEM_MOVE;
        this.remainingUpperChoices = 0;
        this.remainingLowerChoices = 0;
    }

    public int getRemainingUpperChoices() {
        return this.remainingUpperChoices;
    }

    public int getRemainingLowerChoices() {
        return this.remainingLowerChoices;
    }

    private void updatePhaseAfterCardChoice() {
        if (this.remainingUpperChoices == 0 && this.remainingLowerChoices
                == 0) {
            this.phase = TurnPhase.TURN_COMPLETED;
        }
    }
}
