package com.lembraaqui.app.background

import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/** Serializa transições e permanências do mesmo lugar dentro do processo do app. */
object PlaceEventLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withLock(placeId: String, block: suspend () -> T): T {
        val mutex = locks.getOrPut(placeId) { Mutex() }
        mutex.lock()
        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }
}
