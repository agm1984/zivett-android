package com.zivett.app.core.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer
import java.util.UUID

/// A description of one call: method, path relative to the API base, an
/// already-encoded body, and how the response bytes decode.
/// Endpoints are declared as static factories on the feature that owns
/// them (see `AuthEndpoints`, `CustomerEndpoints`), so call sites read as
/// `client.send(AuthEndpoints.login(...))`.
class ApiRequest<T>(
    val method: Method,
    /// Relative to the base URL, e.g. `api/auth/login`.
    val path: String,
    val query: List<Pair<String, String>> = emptyList(),
    val body: ByteArray? = null,
    /// `application/json` unless a multipart form set its own boundary.
    val contentType: String = "application/json",
    /// Whether the bearer token (if any) is attached. Public endpoints
    /// still *accept* one, but a stale token on a public call should
    /// never be the reason it fails.
    val authenticated: Boolean = true,
    val decode: (ByteArray) -> T,
) {
    enum class Method { GET, POST, PUT, PATCH, DELETE }

    companion object {
        /// JSON-decoded response of type T.
        inline fun <reified T> json(
            method: Method,
            path: String,
            query: List<Pair<String, String>> = emptyList(),
            body: ByteArray? = null,
            contentType: String = "application/json",
            authenticated: Boolean = true,
        ): ApiRequest<T> = ApiRequest(method, path, query, body, contentType, authenticated) { bytes ->
            try {
                JsonCoding.json.decodeFromString<T>(bytes.decodeToString())
            } catch (error: Exception) {
                throw ApiError.Decoding(error.toString())
            }
        }

        /// JSON in, JSON out — the common case.
        inline fun <reified T, reified B> json(
            method: Method,
            path: String,
            body: B,
            authenticated: Boolean = true,
            query: List<Pair<String, String>> = emptyList(),
            coder: kotlinx.serialization.json.Json = JsonCoding.json,
        ): ApiRequest<T> = json(method, path, query, coder.encodeToString(serializer<B>(), body).encodeToByteArray(), "application/json", authenticated)

        inline fun <reified T> get(path: String, query: List<Pair<String, String>> = emptyList(), authenticated: Boolean = true): ApiRequest<T> =
            json(Method.GET, path, query, null, "application/json", authenticated)

        inline fun <reified T> post(path: String): ApiRequest<T> = json(Method.POST, path)
        inline fun <reified T, reified B> post(path: String, body: B, authenticated: Boolean = true): ApiRequest<T> = json(Method.POST, path, body, authenticated)
        inline fun <reified T, reified B> patch(path: String, body: B): ApiRequest<T> = json(Method.PATCH, path, body)
        inline fun <reified T> delete(path: String): ApiRequest<T> = json(Method.DELETE, path)
        inline fun <reified T, reified B> delete(path: String, body: B): ApiRequest<T> = json(Method.DELETE, path, body)

        /// Ad-hoc JSON bodies (mixed value types) built with `buildJsonObject`.
        inline fun <reified T> jsonElement(method: Method, path: String, body: JsonElement, authenticated: Boolean = true): ApiRequest<T> =
            json(method, path, emptyList(), JsonCoding.json.encodeToString(JsonElement.serializer(), body).encodeToByteArray(), "application/json", authenticated)

        /// For endpoints that return 204 / a body we don't care about.
        fun empty(method: Method, path: String, body: ByteArray? = null, contentType: String = "application/json", authenticated: Boolean = true): ApiRequest<Unit> =
            ApiRequest(method, path, emptyList(), body, contentType, authenticated) { }

        inline fun <reified B> empty(method: Method, path: String, body: B, authenticated: Boolean = true): ApiRequest<Unit> =
            empty(method, path, JsonCoding.json.encodeToString(serializer<B>(), body).encodeToByteArray(), "application/json", authenticated)

        /// File endpoints (credential downloads) want the raw bytes.
        fun bytes(path: String): ApiRequest<ByteArray> = ApiRequest(Method.GET, path) { it }

        /// Multipart upload decoding to T.
        inline fun <reified T> multipart(path: String, form: MultipartForm, authenticated: Boolean = true): ApiRequest<T> =
            json(Method.POST, path, emptyList(), form.body(), form.contentType, authenticated)
    }
}

/// Minimal multipart/form-data builder for photo and document uploads.
class MultipartForm {
    val boundary = "zivett-${UUID.randomUUID()}"
    private val parts = java.io.ByteArrayOutputStream()

    val contentType: String get() = "multipart/form-data; boundary=$boundary"

    /// Parts plus the closing boundary — always a complete body.
    fun body(): ByteArray = parts.toByteArray() + "--$boundary--\r\n".encodeToByteArray()

    fun addField(name: String, value: String) {
        parts.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".encodeToByteArray())
    }

    fun addFile(name: String, filename: String, mimeType: String, data: ByteArray) {
        parts.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"; filename=\"$filename\"\r\nContent-Type: $mimeType\r\n\r\n".encodeToByteArray())
        parts.write(data)
        parts.write("\r\n".encodeToByteArray())
    }
}

@Suppress("unused")
private val unusedSerializerImport: KSerializer<String>? = null
