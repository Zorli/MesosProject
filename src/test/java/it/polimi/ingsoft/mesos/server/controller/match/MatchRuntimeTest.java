package it.polimi.ingsoft.mesos.server.controller.match;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchRuntimeTest {
    private static final int TEST_GAME_ID = 42;
    private static final long TIMEOUT_SECONDS = 2L;

    @Test
    void submitCompletesFutureWithCommandResult() throws Exception {
        try (MatchRuntime runtime = new MatchRuntime(TEST_GAME_ID)) {
            CompletableFuture<String> result = runtime.submit(() -> "ok");

            assertEquals("ok", await(result));
        }
    }

    @Test
    void submitExecutesCommandsInFifoOrder() throws Exception {
        try (MatchRuntime runtime = new MatchRuntime(TEST_GAME_ID)) {
            List<Integer> executionOrder = new ArrayList<>();

            CompletableFuture<Void> first = runtime.submit(() -> {
                executionOrder.add(1);
                return null;
            });
            CompletableFuture<Void> second = runtime.submit(() -> {
                executionOrder.add(2);
                return null;
            });
            CompletableFuture<Void> third = runtime.submit(() -> {
                executionOrder.add(3);
                return null;
            });

            await(first);
            await(second);
            await(third);

            assertEquals(List.of(1, 2, 3), executionOrder);
        }
    }

    @Test
    void concurrentSubmissionsAreSerializedForSameMatch() throws Exception {
        int commandCount = 8;
        ExecutorService callers = Executors.newFixedThreadPool(commandCount);
        try (MatchRuntime runtime = new MatchRuntime(TEST_GAME_ID)) {
            CyclicBarrier simultaneousSubmit = new CyclicBarrier(commandCount);
            CountDownLatch firstCommandStarted = new CountDownLatch(1);
            CountDownLatch releaseCommands = new CountDownLatch(1);
            AtomicInteger activeCommands = new AtomicInteger();
            AtomicInteger maxObservedConcurrency = new AtomicInteger();
            AtomicInteger executedCommands = new AtomicInteger();
            List<Future<CompletableFuture<Void>>> submitted = new ArrayList<>();

            for (int index = 0; index < commandCount; index++) {
                submitted.add(callers.submit(() -> {
                    simultaneousSubmit.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    return runtime.submit(() -> {
                        int active = activeCommands.incrementAndGet();
                        maxObservedConcurrency.updateAndGet(current -> Math.max(current, active));
                        firstCommandStarted.countDown();
                        try {
                            releaseCommands.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                            executedCommands.incrementAndGet();
                            return null;
                        } finally {
                            activeCommands.decrementAndGet();
                        }
                    });
                }));
            }

            List<CompletableFuture<Void>> commandResults = new ArrayList<>();
            for (Future<CompletableFuture<Void>> submittedCommand : submitted) {
                commandResults.add(submittedCommand.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }

            assertTrue(firstCommandStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            assertEquals(1, activeCommands.get());

            releaseCommands.countDown();
            for (CompletableFuture<Void> commandResult : commandResults) {
                await(commandResult);
            }

            assertEquals(1, maxObservedConcurrency.get());
            assertEquals(commandCount, executedCommands.get());
        } finally {
            callers.shutdownNow();
        }
    }

    @Test
    void longRunningCommandBlocksLaterCommandsUntilItCompletes() throws Exception {
        try (MatchRuntime runtime = new MatchRuntime(TEST_GAME_ID)) {
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            AtomicBoolean secondRan = new AtomicBoolean(false);

            CompletableFuture<Void> first = runtime.submit(() -> {
                firstStarted.countDown();
                releaseFirst.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return null;
            });
            assertTrue(firstStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            CompletableFuture<Void> second = runtime.submit(() -> {
                secondRan.set(true);
                return null;
            });

            assertFalse(secondRan.get());
            assertFalse(second.isDone());

            releaseFirst.countDown();
            await(first);
            await(second);

            assertTrue(secondRan.get());
        }
    }

    @Test
    void futureIsNotCompletedBeforeCommandRuns() throws Exception {
        try (MatchRuntime runtime = new MatchRuntime(TEST_GAME_ID)) {
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            AtomicBoolean secondStarted = new AtomicBoolean(false);

            CompletableFuture<Void> first = runtime.submit(() -> {
                firstStarted.countDown();
                releaseFirst.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return null;
            });
            assertTrue(firstStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            CompletableFuture<String> second = runtime.submit(() -> {
                secondStarted.set(true);
                return "second";
            });

            assertFalse(secondStarted.get());
            assertFalse(second.isDone());

            releaseFirst.countDown();
            await(first);
            assertEquals("second", await(second));
        }
    }

    @Test
    void failedCommandCompletesFutureExceptionally() {
        try (MatchRuntime runtime = new MatchRuntime(TEST_GAME_ID)) {
            IllegalStateException failure = new IllegalStateException("boom");

            CompletableFuture<String> result = runtime.submit(() -> {
                throw failure;
            });

            ExecutionException exception = assertThrows(
                    ExecutionException.class,
                    () -> result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            );
            assertEquals(failure, exception.getCause());
        }
    }

    @Test
    void failedCommandDoesNotStopWorker() throws Exception {
        try (MatchRuntime runtime = new MatchRuntime(TEST_GAME_ID)) {
            CompletableFuture<String> failing = runtime.submit(() -> {
                throw new IllegalStateException("first command failed");
            });

            assertThrows(ExecutionException.class, () -> failing.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            CompletableFuture<String> afterFailure = runtime.submit(() -> "after");

            assertEquals("after", await(afterFailure));
        }
    }

    @Test
    void submitRejectsNullCommand() {
        try (MatchRuntime runtime = new MatchRuntime(TEST_GAME_ID)) {
            assertThrows(NullPointerException.class, () -> runtime.submit(null));
        }
    }

    @Test
    void queuedCommandsRunOnWorkerThread() throws Exception {
        try (MatchRuntime runtime = new MatchRuntime(TEST_GAME_ID)) {
            Thread callerThread = Thread.currentThread();
            CompletableFuture<Thread> workerThread = runtime.submit(Thread::currentThread);

            assertNotEquals(callerThread, await(workerThread));
        }
    }

    @Test
    void runtimeThreadNameContainsMatchId() throws Exception {
        try (MatchRuntime runtime = new MatchRuntime(TEST_GAME_ID)) {
            CompletableFuture<String> threadName = runtime.submit(() -> Thread.currentThread().getName());

            assertTrue(await(threadName).contains("match-" + TEST_GAME_ID));
        }
    }

    @Test
    void manySubmittedCommandsAllCompleteExactlyOnce() throws Exception {
        int commandCount = 100;
        try (MatchRuntime runtime = new MatchRuntime(TEST_GAME_ID)) {
            AtomicInteger executedCommands = new AtomicInteger();
            List<CompletableFuture<Integer>> results = new ArrayList<>();

            for (int index = 0; index < commandCount; index++) {
                int commandIndex = index;
                results.add(runtime.submit(() -> {
                    executedCommands.incrementAndGet();
                    return commandIndex;
                }));
            }

            for (int index = 0; index < commandCount; index++) {
                assertEquals(index, await(results.get(index)));
            }
            assertEquals(commandCount, executedCommands.get());
        }
    }

    @Test
    void closeRejectsNewSubmissions() {
        MatchRuntime runtime = new MatchRuntime(TEST_GAME_ID);

        runtime.close();

        assertThrows(IllegalStateException.class, () -> runtime.submit(() -> "not accepted"));
    }

    @Test
    void closeIsIdempotent() {
        MatchRuntime runtime = new MatchRuntime(TEST_GAME_ID);

        runtime.close();
        runtime.close();

        assertThrows(IllegalStateException.class, () -> runtime.submit(() -> "not accepted"));
    }

    @Test
    void closeWhileCommandIsRunningHasDefinedBehavior() throws Exception {
        MatchRuntime runtime = new MatchRuntime(TEST_GAME_ID);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicReference<CompletableFuture<String>> queuedReference = new AtomicReference<>();

        CompletableFuture<String> running = runtime.submit(() -> {
            firstStarted.countDown();
            releaseFirst.await();
            return "running";
        });
        assertTrue(firstStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        CompletableFuture<String> queued = runtime.submit(() -> "queued");
        queuedReference.set(queued);

        runtime.close();
        releaseFirst.countDown();

        ExecutionException runningFailure = assertThrows(
                ExecutionException.class,
                () -> running.get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        );
        assertTrue(runningFailure.getCause() instanceof InterruptedException);

        CompletableFuture<String> queuedResult = queuedReference.get();
        assertTrue(queuedResult.isCancelled() || queuedResult.isCompletedExceptionally());
    }

    private static <T> T await(CompletableFuture<T> future)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
