module it.polimi.ingsoft.mesos {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;

    opens it.polimi.ingsoft.mesos.client.controller to javafx.fxml;
    exports it.polimi.ingsoft.mesos.client;
    exports it.polimi.ingsoft.mesos.client.model;
    exports it.polimi.ingsoft.mesos.server;
}
