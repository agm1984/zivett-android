package com.zivett.app.core

import com.zivett.app.core.models.CustomerEndpoints
import com.zivett.app.core.network.ApiClient
import kotlinx.coroutines.CancellationException

/// Address-form auto-fill — the Kotlin mirror of the web's
/// `lib/addressLookup.js`. Once the street line is finished (the forms
/// call this when the street field loses focus — never per keystroke,
/// Nominatim forbids autocomplete-style use) look it up and fill the
/// city / province / postcode the person left BLANK. Typed values always
/// win, and nothing here rewrites the street itself.
object AddressLookup {
    /// What the lookup can fill — `POST /api/geocode` `details: true` → `parts`.
    data class Found(val city: String?, val region: String?, val postal: String?)

    /// A street worth looking up: a number and a name ("14 Alder Ct"),
    /// with at least one of the other fields still blank.
    fun worthLookingUp(parts: AddressParts): Boolean {
        val street = parts.line1.trim()
        val anyBlank = parts.city.isBlank() || parts.region.isBlank() || parts.postal.isBlank()
        return anyBlank && street.length >= 6 && street.any { it.isDigit() } && Regex("[a-z]{2}", RegexOption.IGNORE_CASE).containsMatchIn(street)
    }

    /// Whatever's already typed narrows the search.
    fun query(parts: AddressParts): String =
        listOf(parts.line1, parts.city, parts.region, parts.postal).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", ")

    /// Fill ONLY the blank fields from `found` (a region that isn't one of
    /// our 2-letter codes is dropped). Returns the merged parts and which
    /// fields were filled — empty when nothing changed.
    fun merge(parts: AddressParts, found: Found): Pair<AddressParts, List<String>> {
        val filled = mutableListOf<String>()
        var next = parts
        if (parts.city.isBlank() && !found.city.isNullOrBlank()) { next = next.copy(city = found.city); filled += "city" }
        val region = found.region?.uppercase()?.takeIf(AddressParts::isRegionCode)
        if (parts.region.isBlank() && region != null) { next = next.copy(region = region); filled += "region" }
        if (parts.postal.isBlank() && !found.postal.isNullOrBlank()) { next = next.copy(postal = found.postal); filled += "postal" }
        return next to filled
    }

    /// The network half: null when nothing was found or the call failed
    /// (auto-fill is a convenience — it never surfaces an error).
    suspend fun lookUp(client: ApiClient, parts: AddressParts): Found? = try {
        client.send(CustomerEndpoints.geocodeDetails(query(parts))).parts?.let { Found(it.city, it.region, it.postalCode) }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    /// The whole pass for a form: look `parts` up when it's worth it, then
    /// merge into what the form holds NOW (`current()`) — skipped if the
    /// street changed while we looked (they kept typing). Returns the
    /// merged parts, or null when nothing was filled.
    suspend fun fill(client: ApiClient, parts: AddressParts, current: () -> AddressParts): AddressParts? {
        if (!worthLookingUp(parts)) return null
        val found = lookUp(client, parts) ?: return null
        val now = current()
        if (now.line1.trim() != parts.line1.trim()) return null
        val (merged, filled) = merge(now, found)
        return merged.takeIf { filled.isNotEmpty() }
    }

    const val filledNotice = "We filled in what we could from the street — check it's right."
}
