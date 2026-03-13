package it.polimi.ingsoft.mesos.server.model.GameState;


//Class used to inform the game orchestrator after every action performed

public class ActionResult {
    private final TurnState turnState;
    private final String expectedPlayer;
    private final boolean turnEnded;
    private final boolean roundEnded;
    private final boolean gameEnded;

    public ActionResult(){
        this.turnState = null;
        this.expectedPlayer = null;
        this.turnEnded = false;
        this.roundEnded = false;
        this.gameEnded = false;
    }

}

