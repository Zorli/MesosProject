package it.polimi.ingsoft.mesos.server.model.user;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Gestisce l'elenco di tutti gli utenti attualmente registrati/connessi al server.
 */
public class UserManager {
    // Mappa Token -> User (Ottima per le riconnessioni sicure)
    private final Map<String, User> usersByToken;

    // Mappa Nickname -> User (Ottima per controllare se un nome è già preso)
    private final Map<String, User> usersByNickname;

    public UserManager() {
        // Usiamo ConcurrentHashMap perché il server è multi-thread
        this.usersByToken = new ConcurrentHashMap<>();
        this.usersByNickname = new ConcurrentHashMap<>();
    }

    /**
     * Tenta di registrare un nuovo utente.
     * @param nickname Il nome scelto dall'utente
     * @return L'oggetto User appena creato
     * @throws IllegalArgumentException se il nickname è già in uso
     */
    public User registerUser(String nickname) {
        if (usersByNickname.containsKey(nickname)) {
            // Qui potresti anche lanciare un'eccezione personalizzata, es. NicknameAlreadyTakenException
            throw new IllegalArgumentException("Nickname già in uso: " + nickname);
        }

        User newUser = new User(nickname);
        usersByNickname.put(newUser.getNickname(), newUser);
        usersByToken.put(newUser.getSessionToken(), newUser);

        return newUser;
    }

    /**
     * Recupera un utente tramite il suo token di sessione (usato per la riconnessione).
     */
    public Optional<User> getUserByToken(String token) {
        return Optional.ofNullable(usersByToken.get(token));
    }

    /**
     * Recupera un utente tramite il suo nickname.
     */
    public Optional<User> getUserByNickname(String nickname) {
        return Optional.ofNullable(usersByNickname.get(nickname));
    }

    /**
     * Segna un utente come offline (es. quando cade la connessione).
     * NON lo rimuove dalle mappe, così può riconnettersi in seguito.
     */
    public void disconnectUser(String token) {
        User user = usersByToken.get(token);
        if (user != null) {
            user.setOnline(false);
        }
    }
}
