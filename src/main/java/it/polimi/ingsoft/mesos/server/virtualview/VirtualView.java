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
