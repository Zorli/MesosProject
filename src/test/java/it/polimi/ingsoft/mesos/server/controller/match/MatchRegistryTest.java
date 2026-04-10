package it.polimi.ingsoft.mesos.server.controller.match;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchRegistryTest {

    @Test
    void registerStoresRuntimeByGameId() {
        MatchRegistry registry = new MatchRegistry();
        MatchRuntime runtime = new MatchRuntime(101);

        registry.register(runtime);

        assertSame(runtime, registry.find(101).orElseThrow());
        assertSame(runtime, registry.require(101));

        registry.closeAll();
    }

    @Test
    void registerRejectsDuplicateGameId() {
        MatchRegistry registry = new MatchRegistry();
        MatchRuntime first = new MatchRuntime(101);
        MatchRuntime duplicate = new MatchRuntime(101);

        registry.register(first);

        assertThrows(MatchAlreadyRegisteredException.class, () -> registry.register(duplicate));
        assertSame(first, registry.require(101));

        duplicate.close();
        registry.closeAll();
    }

    @Test
    void findReturnsEmptyForUnknownGameId() {
        MatchRegistry registry = new MatchRegistry();

        assertTrue(registry.find(404).isEmpty());
    }

    @Test
    void requireThrowsForUnknownGameId() {
        MatchRegistry registry = new MatchRegistry();

        assertThrows(MatchNotFoundException.class, () -> registry.require(404));
    }

    @Test
    void removeReturnsAndUnregistersRuntime() {
        MatchRegistry registry = new MatchRegistry();
        MatchRuntime runtime = new MatchRuntime(101);
        registry.register(runtime);

        assertSame(runtime, registry.remove(101).orElseThrow());

        assertTrue(registry.find(101).isEmpty());
        assertThrows(MatchNotFoundException.class, () -> registry.require(101));
        runtime.close();
    }

    @Test
    void closeMatchRemovesRuntimeAndClosesIt() {
        MatchRegistry registry = new MatchRegistry();
        MatchRuntime runtime = new MatchRuntime(101);
        registry.register(runtime);

        registry.closeMatch(101);

        assertTrue(registry.find(101).isEmpty());
        assertThrows(IllegalStateException.class, () -> runtime.submit(() -> "closed"));
    }

    @Test
    void closeUnknownMatchIsNoOp() {
        MatchRegistry registry = new MatchRegistry();

        assertDoesNotThrow(() -> registry.closeMatch(404));
    }

    @Test
    void closeAllClosesAndRemovesEveryRuntime() {
        MatchRegistry registry = new MatchRegistry();
        MatchRuntime first = new MatchRuntime(101);
        MatchRuntime second = new MatchRuntime(102);
        registry.register(first);
        registry.register(second);

        registry.closeAll();

        assertTrue(registry.find(101).isEmpty());
        assertTrue(registry.find(102).isEmpty());
        assertThrows(IllegalStateException.class, () -> first.submit(() -> "closed"));
        assertThrows(IllegalStateException.class, () -> second.submit(() -> "closed"));
    }

    @Test
    void registerRejectsNullRuntime() {
        MatchRegistry registry = new MatchRegistry();

        assertThrows(NullPointerException.class, () -> registry.register(null));
    }
}
