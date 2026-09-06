package com.zivett.app.core

/// The structured half of a single stored address string — the Kotlin
/// mirror of the web's `lib/regions.js` + `AddressFields.vue`. Parse and
/// compose round-trip exactly, and free text that doesn't parse lands
/// whole in the street field: visible, editable, nothing lost.
data class AddressParts(
    var line1: String = "",
    var city: String = "",
    var region: String = "",
    var postal: String = "",
) {
    data class RegionOption(val code: String, val name: String)
    data class RegionGroup(val label: String, val options: List<RegionOption>)

    /// The composed string the API stores — same shape the web writes.
    val composed: String
        get() = listOf(
            line1.trim(),
            city.trim(),
            listOf(region, postal.trim()).filter { it.isNotEmpty() }.joinToString(" "),
        ).filter { it.isNotEmpty() }.joinToString(", ")

    val isComplete: Boolean
        get() = line1.isNotBlank() && city.isNotBlank() && region.isNotEmpty()

    companion object {
        /// Grouped for the picker: Canada first (the launch market).
        val regionGroups: List<RegionGroup> = listOf(
            RegionGroup("Canada", listOf(
                "AB" to "Alberta", "BC" to "British Columbia", "MB" to "Manitoba",
                "NB" to "New Brunswick", "NL" to "Newfoundland and Labrador", "NS" to "Nova Scotia",
                "NT" to "Northwest Territories", "NU" to "Nunavut", "ON" to "Ontario",
                "PE" to "Prince Edward Island", "QC" to "Quebec", "SK" to "Saskatchewan", "YT" to "Yukon",
            ).map { RegionOption(it.first, it.second) }),
            RegionGroup("United States", listOf(
                "AL" to "Alabama", "AK" to "Alaska", "AZ" to "Arizona", "AR" to "Arkansas",
                "CA" to "California", "CO" to "Colorado", "CT" to "Connecticut", "DE" to "Delaware",
                "DC" to "District of Columbia", "FL" to "Florida", "GA" to "Georgia", "HI" to "Hawaii",
                "ID" to "Idaho", "IL" to "Illinois", "IN" to "Indiana", "IA" to "Iowa",
                "KS" to "Kansas", "KY" to "Kentucky", "LA" to "Louisiana", "ME" to "Maine",
                "MD" to "Maryland", "MA" to "Massachusetts", "MI" to "Michigan", "MN" to "Minnesota",
                "MS" to "Mississippi", "MO" to "Missouri", "MT" to "Montana", "NE" to "Nebraska",
                "NV" to "Nevada", "NH" to "New Hampshire", "NJ" to "New Jersey", "NM" to "New Mexico",
                "NY" to "New York", "NC" to "North Carolina", "ND" to "North Dakota", "OH" to "Ohio",
                "OK" to "Oklahoma", "OR" to "Oregon", "PA" to "Pennsylvania", "RI" to "Rhode Island",
                "SC" to "South Carolina", "SD" to "South Dakota", "TN" to "Tennessee", "TX" to "Texas",
                "UT" to "Utah", "VT" to "Vermont", "VA" to "Virginia", "WA" to "Washington",
                "WV" to "West Virginia", "WI" to "Wisconsin", "WY" to "Wyoming",
            ).map { RegionOption(it.first, it.second) }),
        )

        val regionCodes: Set<String> = regionGroups.flatMap { group -> group.options.map { it.code } }.toSet()

        fun isRegionCode(value: String): Boolean = value in regionCodes

        /// "14 Alder Court, Nanaimo, BC V9R 2K1" → the fields, best-effort.
        fun parse(address: String): AddressParts {
            val segments = address.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            if (segments.size < 2) return AddressParts(line1 = segments.firstOrNull() ?: "")

            val tail = segments.last().split(" ")
            val hasRegion = tail.firstOrNull()?.let(::isRegionCode) == true
            val lineCount = segments.size - (if (hasRegion) 2 else 1)
            val line = segments.take(lineCount).joinToString(", ")

            return AddressParts(
                line1 = if (line.isEmpty()) segments[0] else line,
                city = if (hasRegion) segments[segments.size - 2] else segments.last(),
                region = if (hasRegion) tail[0] else "",
                postal = if (hasRegion) tail.drop(1).joinToString(" ") else "",
            )
        }
    }
}
