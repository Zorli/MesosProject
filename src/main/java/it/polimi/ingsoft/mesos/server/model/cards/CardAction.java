package it.polimi.ingsoft.mesos.server.model.cards;
import it.polimi.ingsoft.mesos.server.model.enums.Era;
import it.polimi.ingsoft.mesos.server.model.player.Player;

// Visto che i mazzi sono costanti una volta determinato il num di giocatori non ha troppo senso inserire IsPlayable...

// Sarebbe meglio strutturare il file json in modo da avere un default deck ed eventualmente
// le varie aggiunte in base al numero di players.
// In questo modo si evita la chiamata di metodi ulteriori dato che è un'informazione determinata a priori e fissa.

public interface CardAction {
    Era getEra();
    boolean isDrawable(Player player);
}
