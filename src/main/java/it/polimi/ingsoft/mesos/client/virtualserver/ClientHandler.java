package it.polimi.ingsoft.mesos.client.virtualserver;


//This class should offer the read socket service, given that is a blockign loop(and it's used only for tcp)
//we must run in on a different thread , when it receives something from the network it passes it to the contrller
//that handles it with a callback
public class ClientHandler {
}
