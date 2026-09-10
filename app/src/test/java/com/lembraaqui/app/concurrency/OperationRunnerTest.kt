package com.lembraaqui.app.concurrency

import com.lembraaqui.app.background.PlaceEventLocks
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OperationRunnerTest {
    @Test fun duplicateTapRunsOnlyOnceAndOtherActionsContinue() = runTest {
        val runner = OperationRunner(this, StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<Unit>()
        var saves = 0
        var otherCompleted = false
        runner.launch("save") { saves++; gate.await() }
        runner.launch("save") { saves++ }
        runner.launch("other", onDone = { otherCompleted = true }) { 42 }
        runCurrent()
        assertEquals(1, saves)
        assertTrue(otherCompleted)
        assertEquals(setOf("save"), runner.busy.value)
        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(runner.busy.value.isEmpty())
    }

    @Test fun failedOperationReleasesButtonAndCanBeRetried() = runTest {
        val runner = OperationRunner(this, StandardTestDispatcher(testScheduler))
        runner.launch<Unit>("save") { error("Falha de teste") }
        advanceUntilIdle()
        assertEquals("Falha de teste", runner.message.value?.text)
        assertTrue(runner.busy.value.isEmpty())
        var retried = false
        runner.launch("save", onDone = { retried = true }) { Unit }
        advanceUntilIdle()
        assertTrue(retried)
    }

    @Test fun cancellationDoesNotDeliverSuccessOrErrorAndReleasesState() = runTest {
        val runner = OperationRunner(this, StandardTestDispatcher(testScheduler))
        var callbacks = 0
        val job = runner.launch<Unit>("location", onDone = { callbacks++ }, onError = { callbacks++ }) {
            awaitCancellation()
        }
        runCurrent()
        assertTrue("location" in runner.busy.value)
        job.cancel()
        advanceUntilIdle()
        assertEquals(0, callbacks)
        assertNull(runner.message.value)
        assertTrue(runner.busy.value.isEmpty())
    }

    @Test fun cancellationIsNotConvertedToResultFailure() = runTest {
        val cancellation = CancellationException("Encerrado")
        try {
            suspendResult<Unit> { throw cancellation }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test fun dismissingOldMessagePreservesNewMessage() = runTest {
        val runner = OperationRunner(this, StandardTestDispatcher(testScheduler))
        runner.notify("Primeiro")
        val old = runner.message.value!!
        runner.notify("Segundo")
        runner.dismiss(old.id)
        assertEquals("Segundo", runner.message.value?.text)
    }

    @Test fun samePlaceIsSerializedWhileOtherPlacesCanProgress() = runTest {
        val gate = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        launch {
            PlaceEventLocks.withLock("test-a") {
                order += "a-start"
                gate.await()
                order += "a-end"
            }
        }
        runCurrent()
        val waiting = launch { PlaceEventLocks.withLock("test-a") { order += "a-second" } }
        launch { PlaceEventLocks.withLock("test-b") { order += "b" } }
        runCurrent()
        assertEquals(listOf("a-start", "b"), order)
        waiting.cancel()
        gate.complete(Unit)
        advanceUntilIdle()
        PlaceEventLocks.withLock("test-a") { order += "a-after-cancel" }
        assertEquals(listOf("a-start", "b", "a-end", "a-after-cancel"), order)
    }

    @Test fun blockingWorkDoesNotBlockUiAndCallbackReturnsToUiThread() = runBlocking {
        Executors.newSingleThreadExecutor { Thread(it, "test-ui") }.asCoroutineDispatcher().use { ui ->
            Executors.newSingleThreadExecutor { Thread(it, "test-io") }.asCoroutineDispatcher().use { io ->
                val scope = CoroutineScope(SupervisorJob() + ui)
                val runner = OperationRunner(scope, io)
                val started = CompletableDeferred<String>()
                val done = CompletableDeferred<Pair<String, String>>()
                val gate = CountDownLatch(1)
                try {
                    runner.launch("save", onDone = { done.complete(it to Thread.currentThread().name) },
                        onError = { done.completeExceptionally(AssertionError(it)) }) {
                        started.complete(Thread.currentThread().name)
                        check(gate.await(5, TimeUnit.SECONDS)) { "Test gate timed out" }
                        "saved"
                    }
                    assertEquals("test-io", withTimeout(5_000) { started.await() })
                    // This must finish while the IO thread is blocked, before gate.countDown().
                    assertEquals("test-ui", withTimeout(5_000) { withContext(ui) { Thread.currentThread().name } })
                    gate.countDown()
                    assertEquals("saved" to "test-ui", withTimeout(5_000) { done.await() })
                } finally {
                    gate.countDown()
                    scope.cancel()
                }
            }
        }
    }
}
