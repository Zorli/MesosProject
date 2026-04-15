package it.polimi.ingsoft.mesos.server.virtualview;

import it.polimi.ingsoft.mesos.shared.messages.ServerMessage;
import it.polimi.ingsoft.mesos.shared.payload.client.AuthenticationPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ChooseCardPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.CreateLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ExitLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ExtraDrawPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.FinalScorePayload;
import it.polimi.ingsoft.mesos.shared.payload.client.JoinLobbyPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.MoveTotemPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ResolveEffectsPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.ResetBoardPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.SetupMatchPayload;
import it.polimi.ingsoft.mesos.shared.payload.client.StartLobbyPayload;

import java.io.Closeable;
// Here we expose the services that the client can request to the server.
// This interface defines the common contract shared by TCP and RMI: with TCP these
// operations are reached through message deserialization and dispatch, while with RMI
// the same methods will be invoked directly as remote calls.
// On the client side, the concrete transport object acts as a proxy toward this contract.
public interface VirtualView extends Closeable {
    // Pre-match
    ServerMessage authenticate(AuthenticationPayload payload);
    ServerMessage createLobby(CreateLobbyPayload payload);
    ServerMessage joinLobby(JoinLobbyPayload payload);
    ServerMessage exitLobby(ExitLobbyPayload payload);
    ServerMessage startLobby(StartLobbyPayload payload);

    // In-match
    ServerMessage setupMatch(SetupMatchPayload payload);
    ServerMessage moveTotem(MoveTotemPayload payload);
    ServerMessage chooseCard(ChooseCardPayload payload);
    ServerMessage extraDraw(ExtraDrawPayload payload);
    ServerMessage resolveEffects(ResolveEffectsPayload payload);
    ServerMessage resetBoard(ResetBoardPayload payload);
    ServerMessage finalScore(FinalScorePayload payload);
}
