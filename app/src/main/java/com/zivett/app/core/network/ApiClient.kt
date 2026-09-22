package com.zivett.app.core.network

import com.zivett.app.core.storage.TokenStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/// The one seam between the app and the network. Features depend on this
/// interface; tests substitute `PreviewApiClient`, the live app uses
/// `HttpApiClient`.
interface ApiClient {
    suspend fun <T> send(request: ApiRequest<T>): T
}

/// OkHttp-backed client. Attaches the bearer token from `TokenStore`,
/// asks for JSON, and translates Laravel's error bodies into `ApiError`.
///
/// A 401 from any authenticated call is reported through
/// `onUnauthenticated` so `AuthSession` can drop the dead token in one
/// place instead of every screen checking for it.
class HttpApiClient(
    private val baseUrl: String,
    private val tokenStore: TokenStore,
    private val http: OkHttpClient = defaultHttp(),
    /// Where `onUnauthenticated` runs — the main thread in the app, so
    /// the session's Compose state flips on the right thread.
    private val callbackDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : ApiClient {
    var onUnauthenticated: (() -> Unit)? = null

    override suspend fun <T> send(request: ApiRequest<T>): T {
        val okRequest = makeRequest(request)

        val (response, data) = try {
            http.newCall(okRequest).await()
        } catch (error: IOException) {
            throw ApiError.Transport(error.message ?: error.toString())
        }

        if (response.code !in 200..299) {
            val error = errorFor(response.code, response.header("Retry-After"), data)
            if (error is ApiError.Unauthenticated && request.authenticated) {
                withContext(callbackDispatcher) { onUnauthenticated?.invoke() }
            }
            throw error
        }

        return request.decode(data)
    }

    private fun makeRequest(request: ApiRequest<*>): Request {
        val base: HttpUrl = try { baseUrl.toHttpUrl() } catch (_: IllegalArgumentException) { throw ApiError.Transport("Bad base URL $baseUrl") }
        val url = base.newBuilder().apply {
            request.path.split('/').filter { it.isNotEmpty() }.forEach { addPathSegment(it) }
            request.query.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()

        val builder = Request.Builder().url(url)
            .header("Accept", "application/json")
            // Laravel's `expectsJson()` also keys off this; without it a
            // validation failure can come back as a redirect.
            .header("X-Requested-With", "XMLHttpRequest")

        val body = request.body?.toRequestBody(request.contentType.toMediaType())
            // OkHttp refuses body-less POST/PUT/PATCH/DELETE; an empty
            // JSON body keeps the server's parsers happy.
            ?: if (request.method != ApiRequest.Method.GET) ByteArray(0).toRequestBody("application/json".toMediaType()) else null

        builder.method(request.method.name, body)

        if (request.authenticated) {
            tokenStore.read()?.let { builder.header("Authorization", "Bearer $it") }
        }

        return builder.build()
    }

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        /// Maps a non-2xx response to the matching `ApiError`.
        fun errorFor(status: Int, retryAfter: String?, data: ByteArray): ApiError {
            val text = data.decodeToString()
            val body = runCatching { JsonCoding.json.decodeFromString<ServerMessage>(text) }.getOrNull()
            val message = body?.message

            return when (status) {
                401 -> ApiError.Unauthenticated
                403 -> ApiError.Forbidden(message)
                404 -> ApiError.NotFound
                409 -> ApiError.Conflict(message, data)
                422 -> runCatching { JsonCoding.json.decodeFromString<ValidationErrors>(text) }.getOrNull()
                    ?.let { ApiError.Validation(it) }
                    ?: ApiError.Validation(ValidationErrors(message ?: "The given data was invalid."))
                429 -> ApiError.RateLimited(retryAfter?.toIntOrNull())
                else -> ApiError.Server(status, message, body?.code)
            }
        }
    }
}

/// Runs the call on OkHttp's dispatcher and reads the whole body THERE —
/// the coroutine resumes on the caller's (main) dispatcher, where a
/// streaming read would trip NetworkOnMainThreadException.
private suspend fun Call.await(): Pair<Response, ByteArray> = suspendCancellableCoroutine { continuation ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            val bytes = try {
                response.use { it.body.bytes() }
            } catch (e: IOException) {
                if (!continuation.isCancelled) continuation.resumeWithException(e)
                return
            }
            continuation.resume(response to bytes)
        }
    })
    continuation.invokeOnCancellation { cancel() }
}
