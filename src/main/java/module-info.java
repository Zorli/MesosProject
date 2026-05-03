  module it.polimi.ingsoft.mesos {
      requires java.sql;
      requires javafx.controls;
      requires javafx.fxml;

      requires org.controlsfx.controls;
      requires com.fasterxml.jackson.databind;
      requires com.google.gson;
      requires java.rmi;
      //requires it.polimi.ingsoft.mesos;

      opens it.polimi.ingsoft.mesos.client.view.gui to javafx.fxml, javafx.graphics;
      opens it.polimi.ingsoft.mesos.server to com.google.gson;
      opens it.polimi.ingsoft.mesos.shared.payload.client to com.google.gson;
      opens it.polimi.ingsoft.mesos.shared.payload.server to com.google.gson;
      exports it.polimi.ingsoft.mesos.shared to java.rmi;
      exports it.polimi.ingsoft.mesos.server.virtualview to java.rmi;
      exports it.polimi.ingsoft.mesos.client.virtualserver to java.rmi;
      exports it.polimi.ingsoft.mesos.shared.payload.client;
      exports it.polimi.ingsoft.mesos.shared.payload.server;
      exports it.polimi.ingsoft.mesos.client;
      exports it.polimi.ingsoft.mesos.client.view.gui;
      exports it.polimi.ingsoft.mesos.server;
      exports it.polimi.ingsoft.mesos.server.model.cards;
      exports it.polimi.ingsoft.mesos.server.model.cards.buildings;
      exports it.polimi.ingsoft.mesos.server.model.cards.characters;
      exports it.polimi.ingsoft.mesos.server.model.cards.events;
      exports it.polimi.ingsoft.mesos.server.model.enums;
      exports it.polimi.ingsoft.mesos.server.model.player;
      exports it.polimi.ingsoft.mesos.server.model.ranking;
        exports it.polimi.ingsoft.mesos.server.model.persistence;
      exports it.polimi.ingsoft.mesos.shared.messages;
      opens it.polimi.ingsoft.mesos.shared.messages to com.google.gson;
      exports it.polimi.ingsoft.mesos.client.serverproxy to java.rmi;
  }
