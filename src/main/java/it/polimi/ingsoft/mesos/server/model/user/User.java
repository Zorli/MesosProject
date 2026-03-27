package it.polimi.ingsoft.mesos.server.model.user;
import java.util.UUID;
import java.util.Objects;

public class User {
    private final String nickname;     // Identificativo univoco visibile
    private final String sessionToken; // Token segreto per la riconnessione
    private boolean isOnline;

    public User(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("Il nickname non può essere vuoto");
        }
        this.nickname = nickname;
        // Generiamo un token univoco al momento della creazione
        this.sessionToken = UUID.randomUUID().toString();
        this.isOnline = true;
    }

    public String getNickname() {
        return nickname;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        this.isOnline = online;
    }

    // equals e hashCode basati sul nickname, poiché deve essere univoco
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return nickname.equals(user.nickname);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nickname);
    }
}
