  module it.polimi.ingsoft.mesos {
      requires java.sql;
      requires javafx.controls;
      requires javafx.fxml;

      requires org.controlsfx.controls;
      requires com.fasterxml.jackson.databind;
      //requires it.polimi.ingsoft.mesos;

      opens it.polimi.ingsoft.mesos.client.controller to javafx.fxml;
      exports it.polimi.ingsoft.mesos.client;
      exports it.polimi.ingsoft.mesos.client.view;
      exports it.polimi.ingsoft.mesos.server;
      exports it.polimi.ingsoft.mesos.server.model.cards;
      exports it.polimi.ingsoft.mesos.server.model.cards.buildings;
      exports it.polimi.ingsoft.mesos.server.model.cards.characters;
      exports it.polimi.ingsoft.mesos.server.model.cards.events;
      exports it.polimi.ingsoft.mesos.server.model.enums;
      exports it.polimi.ingsoft.mesos.server.model.player;
      exports it.polimi.ingsoft.mesos.server.model.ranking;
        exports it.polimi.ingsoft.mesos.server.model.persistence;
  }