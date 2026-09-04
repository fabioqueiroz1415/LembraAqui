package com.lembraaqui.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionReducerTest {
    @Test fun enteringStartsNewCycle() {
        val decision = TransitionReducer.reduce(LocationState(false, 4, null, null), LocationTransition.ENTER, 10_000)
        assertTrue(decision.accepted)
        assertTrue(decision.state.inside)
        assertEquals(5, decision.state.cycleId)
        assertEquals(10_000, decision.state.enteredAt)
    }

    @Test fun duplicateEnterIsIgnored() {
        val decision = TransitionReducer.reduce(LocationState(true, 4, 1_000, 1_000), LocationTransition.ENTER, 200_000)
        assertFalse(decision.accepted)
        assertEquals(4, decision.state.cycleId)
    }

    @Test fun quickExitIsAlwaysAcceptedSoDwellCanBeCancelled() {
        val state = LocationState(true, 2, 100_000, 100_000)
        val exit = TransitionReducer.reduce(state, LocationTransition.EXIT, 110_000)
        assertTrue(exit.accepted)
        assertFalse(exit.state.inside)
        assertEquals(null, exit.state.enteredAt)
    }

    @Test fun borderOscillationReentryKeepsSameCycle() {
        val outside = LocationState(false, 2, 110_000, null)
        val enter = TransitionReducer.reduce(outside, LocationTransition.ENTER, 150_000)
        assertTrue(enter.accepted)
        assertTrue(enter.state.inside)
        assertEquals(2, enter.state.cycleId)
    }

    @Test fun reentryAfterDebounceStartsNewCycle() {
        val outside = LocationState(false, 2, 100_000, null)
        val enter = TransitionReducer.reduce(outside, LocationTransition.ENTER, 200_001)
        assertTrue(enter.accepted)
        assertEquals(3, enter.state.cycleId)
    }
}
