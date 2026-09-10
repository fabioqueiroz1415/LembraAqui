package com.lembraaqui.app.concurrency

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The owner scope runs on Main. Only work runs on the injected background dispatcher. */
class OperationRunner(
    private val scope: CoroutineScope,
    private val background: CoroutineDispatcher = Dispatchers.IO
) {
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<Message?>(null)
    val message = _message.asStateFlow()
    private var nextMessageId = 0L

    data class Message(val id: Long, val text: String)

    fun notify(text: String) { _message.value = Message(++nextMessageId, text) }
    fun dismiss(id: Long) {
        if (_message.value?.id == id) _message.value = null
    }

    fun <T> launch(
        key: String,
        onDone: (T) -> Unit = {},
        onError: (String) -> Unit = ::notify,
        work: suspend () -> T
    ) = scope.launch operation@{
        // Confined to Main: check and insertion happen before the first suspension.
        if (key in _busy.value) return@operation
        _busy.value = _busy.value + key
        try {
            val result = try {
                Result.success(withContext(background) { work() })
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            // Back on the owner (Main) dispatcher for Compose state and navigation.
            result.fold(onDone) { onError(it.message ?: "Não foi possível concluir a operação.") }
        } finally {
            _busy.value = _busy.value - key
        }
    }
}

/** Unlike runCatching, never turns structured cancellation into a normal failure. */
suspend fun <T> suspendResult(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    Result.failure(error)
}
