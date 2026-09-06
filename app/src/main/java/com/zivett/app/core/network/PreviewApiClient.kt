package com.zivett.app.core.network

/// An `ApiClient` that answers from a path → value table. Shared by
/// previews and unit tests: a value stored for a path is returned as-is
/// (already the decoded type), `errors` throws instead, and every call
/// is recorded in `sent` as "METHOD path".
class PreviewApiClient(responses: Map<String, Any> = emptyMap()) : ApiClient {
    val responses: MutableMap<String, Any> = responses.toMutableMap()
    val errors: MutableMap<String, ApiError> = mutableMapOf()
    val sent: MutableList<String> = mutableListOf()

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T> send(request: ApiRequest<T>): T {
        sent.add("${request.method.name} ${request.path}")

        errors[request.path]?.let { throw it }

        val value = responses[request.path]
            ?: return try {
                // Empty responses decode from nothing; raw-bytes requests get zero bytes.
                request.decode(ByteArray(0))
            } catch (_: Throwable) {
                throw ApiError.Server(500, "No preview response for ${request.path}")
            }

        return value as T
    }
}
