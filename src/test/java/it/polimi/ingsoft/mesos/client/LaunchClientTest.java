package it.polimi.ingsoft.mesos.client;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchClientTest {
    private static final int DEFAULT_RMI_PORT = 1099;

    @Test
    void mainReturnsSafelyWhenTuiRmiServerBindingIsMissing() throws Exception {
        Registry registry = LocateRegistry.createRegistry(DEFAULT_RMI_PORT);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            assertDoesNotThrow(() -> runLauncherWithInput("2\n2\n", output));

            String rendered = output.toString(StandardCharsets.UTF_8).toLowerCase();
            assertTrue(rendered.contains("server"), "startup failure message should mention the server");
            assertTrue(
                    rendered.contains("failed to connect")
                            || rendered.contains("unable to connect")
                            || rendered.contains("could not connect"),
                    "startup failure message should be friendly and actionable"
            );
        } finally {
            UnicastRemoteObject.unexportObject(registry, true);
        }
    }

    /**
     * Runs the launcher with synthetic console streams because LaunchClient currently
     * constructs its scanner and network dependencies inline.
     *
     * @param inputText console input consumed by the launcher
     * @param output captured launcher output
     */
    private static void runLauncherWithInput(String inputText, ByteArrayOutputStream output) {
        ByteArrayInputStream input = new ByteArrayInputStream(inputText.getBytes(StandardCharsets.UTF_8));
        PrintStream originalOut = System.out;
        java.io.InputStream originalIn = System.in;

        try (PrintStream capturedOut = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setIn(input);
            System.setOut(capturedOut);
            LaunchClient.main(new String[0]);
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
    }
}
