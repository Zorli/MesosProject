package it.polimi.ingsoft.mesos.shared.payload.servertoclient.error;

import it.polimi.ingsoft.mesos.shared.payload.servertoclient.ServerPayload;

/**
 * Marker interface for all preLobby-to-client payloads representing error reports.
 * <p>
 * Implementing classes contain details of execution failures, command validation
 * rejections, or protocol/phase errors.
 */
public interface ErrorServerPayload extends ServerPayload {
}
