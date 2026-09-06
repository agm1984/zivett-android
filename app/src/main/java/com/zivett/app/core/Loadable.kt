package com.zivett.app.core

import com.zivett.app.core.network.userMessage

/// The three states every remote screen goes through. Shared so list
/// and detail screens render loading/error identically.
sealed interface Loadable<out T> {
    object Loading : Loadable<Nothing>
    data class Loaded<T>(val loaded: T) : Loadable<T>
    data class Failed(val message: String) : Loadable<Nothing>

    val value: T?
        get() = (this as? Loaded<T>)?.loaded
}

/// Runs `operation` and returns the next state, keeping stale data on
/// a failed refresh. Usage: `state = state.reloaded { … }`.
suspend fun <T> Loadable<T>.reloaded(operation: suspend () -> T): Loadable<T> =
    try {
        Loadable.Loaded(operation())
    } catch (error: Throwable) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        if (value == null) Loadable.Failed(error.userMessage) else this
    }
