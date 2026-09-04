package com.lembraaqui.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

enum class ReminderType(val label: String) {
    ARRIVING("Chegando"),
    DWELL("No local"),
    LEAVING("Saindo")
}

enum class RepeatMode(val label: String) {
    ONCE("Uma vez"),
    EVERY_TIME("Toda vez"),
    ONCE_PER_DAY("Uma vez por dia")
}

enum class LocationTransition { ENTER, DWELL, EXIT }

data class ReminderPolicyInput(
    val active: Boolean,
    val oneShotCompleted: Boolean,
    val daysMask: Int,
    val startMinute: Int?,
    val endMinute: Int?,
    val repeatMode: RepeatMode,
    val lastTriggeredDayKey: String?,
    val lastTriggeredCycleId: Long?
)

data class LocationState(
    val inside: Boolean,
    val cycleId: Long,
    val lastTransitionAt: Long?,
    val enteredAt: Long?
)

data class TransitionDecision(
    val accepted: Boolean,
    val state: LocationState
)

object WeekdayMask {
    const val ALL = 0b1111111

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    fun contains(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0
    fun toggle(mask: Int, day: DayOfWeek): Int = mask xor bit(day)
}

object ReminderPolicy {
    fun isTimeAllowed(startMinute: Int?, endMinute: Int?, nowMinute: Int): Boolean {
        if (startMinute == null || endMinute == null) return true
        return if (startMinute <= endMinute) {
            nowMinute in startMinute..endMinute
        } else {
            nowMinute >= startMinute || nowMinute <= endMinute
        }
    }

    fun isAllowed(input: ReminderPolicyInput, now: ZonedDateTime, cycleId: Long): Boolean {
        if (!input.active || input.oneShotCompleted) return false
        if (!WeekdayMask.contains(input.daysMask, now.dayOfWeek)) return false
        val minute = now.hour * 60 + now.minute
        if (!isTimeAllowed(input.startMinute, input.endMinute, minute)) return false
        return when (input.repeatMode) {
            RepeatMode.ONCE -> !input.oneShotCompleted
            RepeatMode.ONCE_PER_DAY -> input.lastTriggeredDayKey != dayKey(now.toLocalDate())
            RepeatMode.EVERY_TIME -> input.lastTriggeredCycleId != cycleId
        }
    }

    fun dayKey(date: LocalDate): String = date.toString()
}

object TransitionReducer {
    /**
     * Reentradas muito rápidas são tratadas como o mesmo ciclo. Assim um EXIT real
     * nunca é perdido (e cancela a permanência), mas oscilações ENTER/EXIT na borda
     * não criam um novo ciclo capaz de repetir notificações "Toda vez".
     */
    const val EDGE_DEBOUNCE_MS = 90_000L

    fun reduce(current: LocationState, transition: LocationTransition, nowMs: Long): TransitionDecision {
        return when (transition) {
            LocationTransition.ENTER -> {
                if (current.inside) TransitionDecision(false, current)
                else {
                    val rapidReentry = current.cycleId > 0 && current.lastTransitionAt?.let {
                        nowMs - it in 0 until EDGE_DEBOUNCE_MS
                    } == true
                    TransitionDecision(
                        true,
                        current.copy(
                            inside = true,
                            cycleId = if (rapidReentry) current.cycleId else current.cycleId + 1,
                            lastTransitionAt = nowMs,
                            enteredAt = nowMs
                        )
                    )
                }
            }
            LocationTransition.EXIT -> {
                if (!current.inside) TransitionDecision(false, current)
                else TransitionDecision(
                    true,
                    current.copy(
                        inside = false,
                        lastTransitionAt = nowMs,
                        enteredAt = null
                    )
                )
            }
            LocationTransition.DWELL -> TransitionDecision(current.inside, current)
        }
    }
}
