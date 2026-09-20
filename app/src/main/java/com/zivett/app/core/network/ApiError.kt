package com.zivett.app.core.network

import kotlinx.serialization.Serializable

/// Every failure the API layer surfaces, shaped after what Laravel sends
/// so screens can react to the *meaning* (a field error, a lockout) rather
/// than re-parsing status codes.
sealed class ApiError : RuntimeException() {
    /// 401 — the token is missing, revoked, or expired. `AuthSession`
    /// signs out locally when it sees this.
    object Unauthenticated : ApiError()

    /// 403 — authenticated, but not allowed (wrong role, suspended,
    /// unverified email on a `verified` route…).
    data class Forbidden(val detail: String?) : ApiError()

    /// 404.
    object NotFound : ApiError()

    /// 422 — Laravel validation failed; field errors are keyed by input.
    data class Validation(val errors: ValidationErrors) : ApiError()

    /// 409 — a state conflict; `body` carries the server's JSON so a
    /// caller can decode a richer shape (quote-accept schedule conflicts,
    /// payment action required).
    class Conflict(val detail: String?, val body: ByteArray) : ApiError()

    /// 429 — throttled. `retryAfter` is seconds when the server said so.
    data class RateLimited(val retryAfter: Int?) : ApiError()

    /// Any other non-2xx.
    data class Server(val status: Int, val detail: String?) : ApiError()

    /// The request never produced a response (offline, DNS, timeout).
    data class Transport(val detail: String) : ApiError()

    /// A 2xx whose body didn't match the expected shape.
    data class Decoding(val detail: String) : ApiError()

    /// Something a human can read on screen.
    val userMessage: String
        get() = when (this) {
            Unauthenticated -> "Your session has ended. Please log in again."
            is Forbidden -> detail ?: "You don't have access to that."
            NotFound -> "We couldn't find that."
            is Validation -> errors.message
            is Conflict -> detail ?: "That changed while you were looking — please refresh and try again."
            is RateLimited -> "Too many attempts — please wait a moment and try again."
            is Server -> detail ?: "Something went wrong on our end. Please try again."
            is Transport -> "We couldn't reach ZiVETT. Check your connection and try again."
            is Decoding -> "We got an unexpected response. Please try again."
        }

    override val message: String get() = userMessage

    /// The bank's message when this is a DECLINED charge — a 422 that
    /// isn't a form problem (`{ message, code: "payment_declined" }`, no
    /// `errors`). Pay surfaces react by keeping the screen open and
    /// offering "Use a different card" instead of toasting and closing.
    val paymentDeclinedMessage: String?
        get() = (this as? Validation)?.errors?.takeIf { it.code == "payment_declined" }?.message

    /// The bank wants to authenticate a pay-time charge: 409
    /// `{ code: "payment_action_required", client_secret, payment_method_id }`.
    /// Null for every other conflict, so callers can fall through to the
    /// server's own message.
    val paymentAction: PaymentAction?
        get() = (this as? Conflict)
            ?.let { runCatching { JsonCoding.json.decodeFromString<PaymentAction>(it.body.decodeToString()) }.getOrNull() }
            ?.takeIf { it.code == "payment_action_required" }

    /// The first message for a field, if this is a validation failure —
    /// lets a form show inline errors with a one-liner.
    fun first(field: String): String? = (this as? Validation)?.errors?.first(field)
}

/// Laravel's 422 body: `{ message, errors: { field: [messages] } }` — or,
/// for a domain refusal rather than a form problem, `{ message, code }`
/// with no `errors` (a declined card is `payment_declined`). The `code`
/// used to be dropped on decode, so no screen could tell the two apart.
@Serializable
data class ValidationErrors(
    val message: String,
    val errors: Map<String, List<String>> = emptyMap(),
    val code: String? = null,
) {
    fun first(field: String): String? = errors[field]?.firstOrNull()

    /// Field → first message, the shape every form keeps.
    val firstMessages: Map<String, String>
        get() = errors.mapNotNull { (key, value) -> value.firstOrNull()?.let { key to it } }.toMap()
}

/// The 409 a pay/close call answers when the bank demands authentication.
/// The server charges OFF-session, so the PaymentIntent sits in
/// `requires_payment_method` — it has to be CONFIRMED again on-session
/// with `paymentMethodId`, not just "next-actioned". An older server
/// omits the id.
@Serializable
data class PaymentAction(val code: String? = null, val clientSecret: String? = null, val paymentMethodId: String? = null)

/// Laravel's generic error body (`abort(403, 'message')` and friends).
@Serializable
data class ServerMessage(val message: String? = null)

/// The error message a screen shows for any throwable: the API's human
/// copy when it's ours, the platform's otherwise.
val Throwable.userMessage: String
    get() = (this as? ApiError)?.userMessage ?: (localizedMessage ?: toString())
