package com.zivett.app.core.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/// One decoder/encoder pair for the whole app so every model agrees on
/// key casing (Laravel's snake_case ↔ Kotlin camelCase) and date formats.
@OptIn(ExperimentalSerializationApi::class)
object JsonCoding {
    /// The app-wide coder: snake_case on the wire, unknown keys ignored,
    /// absent keys → null, nil properties omitted when encoding (Laravel's
    /// `validated()` skips absent keys, so omission means "unchanged").
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

    /// Same wire shape, but nulls are WRITTEN — for bodies that must be
    /// able to clear a field (quote revisions clearing a proposed slot).
    val explicitNulls: Json = Json(json) { explicitNulls = true; encodeDefaults = true }

    /// No naming strategy at all — the booking draft snapshot is stored
    /// opaquely in the web wizard's exact key spelling (camelCase blocks,
    /// snake_case inside `form`), so its models carry literal names.
    val plain: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    /// Laravel emits `2026-08-22T14:03:11.000000Z` for datetimes and
    /// `2026-08-22` for date casts; tolerate both plus plain ISO-8601.
    fun parseDate(raw: String): Instant? {
        try { return Instant.parse(raw) } catch (_: DateTimeParseException) {}
        try { return DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(raw, Instant::from) } catch (_: DateTimeParseException) {}
        try { return LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant() } catch (_: DateTimeParseException) {}
        return null
    }
}

/// `Instant` on the wire as Laravel writes it. Model files opt in with
/// `@file:UseSerializers(LaravelInstantSerializer::class)`.
object LaravelInstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LaravelInstant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        val raw = decoder.decodeString()
        return JsonCoding.parseDate(raw) ?: throw IllegalArgumentException("Unrecognized date: $raw")
    }

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(DateTimeFormatter.ISO_INSTANT.format(value))
    }
}

/// A category-id → cents table as PHP writes it. An organization with no
/// trades yet serializes its empty associative array as `[]`, not `{}`,
/// and a trade without a rate rides as `null`; a plain `Map<String, Int>`
/// refuses both. Fields opt in with `@Serializable(with = PhpRatesSerializer::class)`.
object PhpRatesSerializer : KSerializer<Map<String, Int>> {
    private val delegate = MapSerializer(String.serializer(), Int.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): Map<String, Int> {
        val input = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        return when (val element = input.decodeJsonElement()) {
            is JsonObject -> element.entries.mapNotNull { (key, value) ->
                (value as? JsonPrimitive)?.intOrNull?.let { key to it }
            }.toMap()
            // PHP only produces a list here when the ids happen to be
            // 0..n-1; the index is the id in that case.
            is JsonArray -> element.withIndex().mapNotNull { (index, value) ->
                (value as? JsonPrimitive)?.intOrNull?.let { index.toString() to it }
            }.toMap()
            JsonNull -> emptyMap()
            else -> emptyMap()
        }
    }

    override fun serialize(encoder: Encoder, value: Map<String, Int>) = delegate.serialize(encoder, value)
}
