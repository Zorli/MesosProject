package it.polimi.ingsoft.mesos.server.model;

public interface Listener {
    void notifyUpdate(Object payload);
}
