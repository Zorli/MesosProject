package it.polimi.ingsoft.mesos.server.controller;

import it.polimi.ingsoft.mesos.server.controller.Factory.DefaultGameFactory;
import it.polimi.ingsoft.mesos.server.controller.GameOrchestrator.GameOrchestrator;
import it.polimi.ingsoft.mesos.server.testing.MesosTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the active-match registry used to share match orchestrators across sessions.
 */
class ActiveMatchRegistryTest {

    @Test
    void getOrCreateReturnsTheSameInstanceAndInvokesCreatorOnce() {
        ActiveMatchRegistry registry = new ActiveMatchRegistry();
        AtomicInteger creatorCalls = new AtomicInteger();

        GameOrchestrator first = registry.getOrCreate(7, () -> {
            creatorCalls.incrementAndGet();
            return new GameOrchestrator(DefaultGameFactory.getInstance(), MesosTestFixtures.createPlayers(2));
        });
        GameOrchestrator second = registry.getOrCreate(7, () -> {
            creatorCalls.incrementAndGet();
            return new GameOrchestrator(DefaultGameFactory.getInstance(), MesosTestFixtures.createPlayers(2));
        });

        assertSame(first, second);
        assertEquals(1, creatorCalls.get());
    }

    @Test
    void getOrCreateRejectsNullCreatorAndNullOrchestratorResults() {
        ActiveMatchRegistry registry = new ActiveMatchRegistry();

        assertThrows(NullPointerException.class, () -> registry.getOrCreate(1, null));
        assertThrows(NullPointerException.class, () -> registry.getOrCreate(1, () -> null));
    }
}
