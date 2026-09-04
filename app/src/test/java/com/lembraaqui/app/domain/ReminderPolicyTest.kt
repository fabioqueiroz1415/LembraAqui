package com.lembraaqui.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderPolicyTest {
    private val zone = ZoneId.of("America/Sao_Paulo")

    @Test fun normalTimeWindow() {
        assertTrue(ReminderPolicy.isTimeAllowed(17 * 60, 22 * 60, 18 * 60))
        assertFalse(ReminderPolicy.isTimeAllowed(17 * 60, 22 * 60, 10 * 60))
    }

    @Test fun overnightTimeWindow() {
        assertTrue(ReminderPolicy.isTimeAllowed(22 * 60, 6 * 60, 23 * 60))
        assertTrue(ReminderPolicy.isTimeAllowed(22 * 60, 6 * 60, 5 * 60 + 30))
        assertFalse(ReminderPolicy.isTimeAllowed(22 * 60, 6 * 60, 12 * 60))
    }

    @Test fun weekdayRestriction() {
        val monday = ZonedDateTime.of(2026, 9, 7, 18, 0, 0, 0, zone)
        val input = input(daysMask = WeekdayMask.bit(java.time.DayOfWeek.MONDAY))
        assertTrue(ReminderPolicy.isAllowed(input, monday, 1))
        assertFalse(ReminderPolicy.isAllowed(input, monday.plusDays(1), 1))
    }

    @Test fun oncePerDayBlocksSecondTrigger() {
        val now = ZonedDateTime.of(2026, 9, 7, 18, 0, 0, 0, zone)
        val blocked = input(
            repeatMode = RepeatMode.ONCE_PER_DAY,
            lastTriggeredDayKey = ReminderPolicy.dayKey(now.toLocalDate())
        )
        assertFalse(ReminderPolicy.isAllowed(blocked, now, 9))
        assertTrue(ReminderPolicy.isAllowed(blocked, now.plusDays(1), 10))
    }

    @Test fun everyTimeBlocksDuplicateInSameCycle() {
        val now = ZonedDateTime.of(2026, 9, 7, 18, 0, 0, 0, zone)
        val blocked = input(repeatMode = RepeatMode.EVERY_TIME, lastTriggeredCycleId = 3)
        assertFalse(ReminderPolicy.isAllowed(blocked, now, 3))
        assertTrue(ReminderPolicy.isAllowed(blocked, now, 4))
    }


    @Test fun inactiveReminderNeverFires() {
        val now = ZonedDateTime.of(2026, 9, 7, 18, 0, 0, 0, zone)
        assertFalse(ReminderPolicy.isAllowed(input(active = false), now, 1))
    }

    @Test fun oneShotCompletedNeverFires() {
        val now = ZonedDateTime.of(2026, 9, 7, 18, 0, 0, 0, zone)
        assertFalse(ReminderPolicy.isAllowed(input(oneShotCompleted = true, repeatMode = RepeatMode.ONCE), now, 1))
    }

    private fun input(
        active: Boolean = true,
        oneShotCompleted: Boolean = false,
        daysMask: Int = WeekdayMask.ALL,
        startMinute: Int? = null,
        endMinute: Int? = null,
        repeatMode: RepeatMode = RepeatMode.EVERY_TIME,
        lastTriggeredDayKey: String? = null,
        lastTriggeredCycleId: Long? = null
    ) = ReminderPolicyInput(active, oneShotCompleted, daysMask, startMinute, endMinute, repeatMode, lastTriggeredDayKey, lastTriggeredCycleId)
}
