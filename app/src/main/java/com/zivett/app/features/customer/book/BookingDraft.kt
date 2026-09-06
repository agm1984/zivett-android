package com.zivett.app.features.customer.book

import com.zivett.app.core.network.ApiRequest
import com.zivett.app.core.network.ApiRequest.Method
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.core.network.LaravelInstantSerializer
import com.zivett.app.core.network.MultipartForm
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant

/// The booking wizard's server-side draft (`booking_drafts`): state
/// autosaves as the booker moves, photos upload the moment they're
/// picked, and the whole thing survives a killed app or a different
/// device. The payload is the wizard's own snapshot, stored opaquely by
/// the server and — deliberately — written in the exact key spelling the
/// web wizard uses (`draftSnapshot()` in BookingWizard.vue), so a draft
/// started on one platform resumes on the other.
@Serializable(with = ServerBookingDraft.Serializer::class)
data class ServerBookingDraft(
    val payload: BookingDraftPayload? = null,
    val category: String? = null,
    val updatedAt: Instant? = null,
    val photos: List<Photo> = emptyList(),
) {
    @Serializable
    data class Photo(val id: Int, val url: String? = null, val thumbUrl: String? = null)

    /// A draft created by a photo upload before any state save carries
    /// `payload: []` (an empty PHP array) — tolerate any non-snapshot
    /// shape rather than failing the whole fetch.
    object Serializer : KSerializer<ServerBookingDraft> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ServerBookingDraft")

        override fun deserialize(decoder: Decoder): ServerBookingDraft {
            val json = decoder as JsonDecoder
            val obj = json.decodeJsonElement() as? JsonObject ?: return ServerBookingDraft()
            val payload = obj["payload"]?.let { element -> runCatching { JsonCoding.plain.decodeFromJsonElement(BookingDraftPayload.serializer(), element) }.getOrNull() }
            val category = (obj["category"] as? JsonPrimitive)?.contentOrNull
            val updatedAt = (obj["updated_at"] as? JsonPrimitive)?.contentOrNull?.let { JsonCoding.parseDate(it) }
            val photos = obj["photos"]?.let { runCatching { JsonCoding.json.decodeFromJsonElement(ListSerializer(Photo.serializer()), it) }.getOrNull() } ?: emptyList()
            return ServerBookingDraft(payload, category, updatedAt, photos)
        }

        override fun serialize(encoder: Encoder, value: ServerBookingDraft) {
            throw UnsupportedOperationException("ServerBookingDraft is read-only")
        }
    }
}

@Serializable
data class BookingDraftResponse(val draft: ServerBookingDraft? = null)

/// The cross-platform wizard snapshot. Key spellings are the web's JS
/// identifiers (camelCase blocks, snake_case inside `form`), so this
/// type ALWAYS goes through `JsonCoding.plain` (no naming strategy).
@Serializable
data class BookingDraftPayload(
    val form: Form = Form(),
    val intakeAnswers: Map<String, Answer>? = null,
    val selectedWindows: List<String>? = null,
    val newAddress: NewAddress? = null,
    val addressChoice: AddressChoice? = null,
    val savedUnit: String? = null,
    val modePreset: Boolean? = null,
    val stepIndex: Int? = null,
) {
    /// `string | string[]` — single/short/description answers are plain
    /// strings on the web, multi answers arrays.
    @Serializable(with = Answer.Serializer::class)
    data class Answer(val values: List<String>, val isMany: Boolean) {
        companion object {
            fun one(value: String) = Answer(listOf(value), false)
            fun many(values: List<String>) = Answer(values, true)
        }

        object Serializer : KSerializer<Answer> {
            override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BookingDraftAnswer")

            override fun deserialize(decoder: Decoder): Answer {
                val element = (decoder as JsonDecoder).decodeJsonElement()
                return if (element is JsonArray) many(element.jsonArray.mapNotNull { (it as? JsonPrimitive)?.contentOrNull })
                else one((element as? JsonPrimitive)?.contentOrNull ?: "")
            }

            override fun serialize(encoder: Encoder, value: Answer) {
                val json = encoder as JsonEncoder
                if (value.isMany) json.encodeJsonElement(JsonArray(value.values.map { JsonPrimitive(it) }))
                else json.encodeJsonElement(JsonPrimitive(value.values.firstOrNull() ?: ""))
            }
        }
    }

    /// `'new' | <saved place id>`.
    @Serializable(with = AddressChoice.Serializer::class)
    data class AddressChoice(val placeId: Int?) {
        val isNew: Boolean get() = placeId == null

        companion object {
            val new = AddressChoice(null)
            fun place(id: Int) = AddressChoice(id)
        }

        object Serializer : KSerializer<AddressChoice> {
            override val descriptor: SerialDescriptor = buildClassSerialDescriptor("BookingDraftAddressChoice")

            override fun deserialize(decoder: Decoder): AddressChoice {
                val element = (decoder as JsonDecoder).decodeJsonElement()
                return AddressChoice((element as? JsonPrimitive)?.intOrNull)
            }

            override fun serialize(encoder: Encoder, value: AddressChoice) {
                val json = encoder as JsonEncoder
                json.encodeJsonElement(value.placeId?.let { JsonPrimitive(it) } ?: JsonPrimitive("new"))
            }
        }
    }

    /// The web wizard's `form` reactive — snake_case keys in the JSON.
    @Serializable
    data class Form(
        val mode: String? = null,
        @SerialName("service_category_id") val serviceCategoryId: Int? = null,
        val issue: String? = null,
        val urgency: String? = null,
        @SerialName("access_notes") val accessNotes: String? = null,
        // Customer shape.
        val address: String? = null,
        @SerialName("customer_address_id") val customerAddressId: Int? = null,
        // Business shape.
        @SerialName("property_id") val propertyId: Int? = null,
        val unit: String? = null,
        val tenant: String? = null,
    )

    @Serializable
    data class NewAddress(val line1: String? = null, val unit: String? = null, val city: String? = null, val region: String? = null, val postal: String? = null)
}

/// `PUT /api/{area}/booking-draft` body.
@Serializable
data class BookingDraftSaveBody(val payload: BookingDraftPayload, @SerialName("service_category_id") val serviceCategoryId: Int? = null)

/// The draft routes exist identically under both booker prefixes.
object BookingDraftEndpoints {
    private fun prefix(area: BookingWizardEngine.Area) = if (area == BookingWizardEngine.Area.BUSINESS) "api/business" else "api/customer"

    fun show(area: BookingWizardEngine.Area) = ApiRequest.get<BookingDraftResponse>("${prefix(area)}/booking-draft")

    fun save(area: BookingWizardEngine.Area, payload: BookingDraftPayload, categoryId: Int?) = ApiRequest.json<BookingDraftResponse>(
        Method.PUT,
        "${prefix(area)}/booking-draft",
        body = JsonCoding.plain.encodeToString(BookingDraftSaveBody.serializer(), BookingDraftSaveBody(payload, categoryId)).encodeToByteArray(),
    )

    fun discard(area: BookingWizardEngine.Area) = ApiRequest.empty(Method.DELETE, "${prefix(area)}/booking-draft")

    fun uploadPhotos(area: BookingWizardEngine.Area, photos: List<ByteArray>): ApiRequest<BookingDraftResponse> {
        val form = MultipartForm()
        photos.forEachIndexed { i, data -> form.addFile("photos[]", "photo-$i.jpg", "image/jpeg", data) }
        return ApiRequest.multipart("${prefix(area)}/booking-draft/photos", form)
    }

    fun deletePhoto(area: BookingWizardEngine.Area, id: Int) = ApiRequest.empty(Method.DELETE, "${prefix(area)}/booking-draft/photos/$id")
}

@Suppress("unused")
private val keepImports: List<Any?> = listOf(String.serializer(), JsonNull, LaravelInstantSerializer)
private val keepElement: JsonElement? = null
