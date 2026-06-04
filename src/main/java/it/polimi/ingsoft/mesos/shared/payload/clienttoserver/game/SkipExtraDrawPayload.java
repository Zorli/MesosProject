package it.polimi.ingsoft.mesos.shared.payload.clienttoserver.game;




import java.io.Serializable;

public class SkipExtraDrawPayload implements Serializable, GameClientPayload {
     /**
      * Constructs a new SkipExtraDrawPayload.
      *
      * This payload is sent by the server to the client to indicate that the player has
      * chosen to skip performing an extra draw action, either by explicitly sending a
      * skip command or by not responding within the allowed time frame.
      */
}
