package it.polimi.ingsoft.mesos.server.controller.command;
import it.polimi.ingsoft.mesos.shared.payload.client.*;

public record EnqueuedMessage(String sessionId, ClientPayload payload) {}