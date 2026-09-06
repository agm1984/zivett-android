@file:UseSerializers(LaravelInstantSerializer::class)

package com.zivett.app.core.models

import com.zivett.app.core.network.LaravelInstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant

/// Mirrors `App\Enums\UserRole`. Admins exist but the app has no admin
/// surface — `Access` routes them to a "use the web" screen.
@Serializable
enum class UserRole {
    @SerialName("customer") CUSTOMER,
    @SerialName("business") BUSINESS,
    @SerialName("company") COMPANY,
    @SerialName("admin") ADMIN;

    val wire: String get() = name.lowercase()

    companion object {
        /// Roles a visitor may pick for themselves at signup
        /// (`UserRole::registrable()`).
        val registrable: List<UserRole> = listOf(CUSTOMER, BUSINESS, COMPANY)
    }
}

/// Mirrors `App\Enums\OrganizationRole`.
@Serializable
enum class OrganizationRole {
    @SerialName("admin") ADMIN,
    @SerialName("member") MEMBER;

    val wire: String get() = name.lowercase()
}

/// Mirrors `App\Enums\OrganizationType`.
@Serializable
enum class OrganizationType {
    @SerialName("company") COMPANY,
    @SerialName("business") BUSINESS,
}

/// The organization a company or business user belongs to, as
/// serialized on `user.organization` by `/api/user` and the auth
/// endpoints. Only the fields the app gates on are modelled; extra keys
/// are ignored by the decoder.
@Serializable
data class Organization(
    val id: Int,
    val type: OrganizationType,
    val name: String,
    val approvedAt: Instant? = null,
    val submittedAt: Instant? = null,
    val setupCompletedAt: Instant? = null,
    val suspendedAt: Instant? = null,
    /// 'pro' | 'elite' | 'premium' — the tier badge the org wears.
    val planBadge: String? = null,
) {
    val isApproved: Boolean get() = approvedAt != null
    val isSuspended: Boolean get() = suspendedAt != null
}

/// The signed-in account. Decoded from the `user` object every auth
/// endpoint returns and from `GET /api/user`.
@Serializable
data class User(
    val id: Int,
    val firstName: String,
    val lastName: String,
    val name: String,
    val email: String,
    val phone: String? = null,
    val role: UserRole,
    val organizationRole: OrganizationRole? = null,
    val organization: Organization? = null,
    val emailVerifiedAt: Instant? = null,
    val phoneVerifiedAt: Instant? = null,
    val deactivatedAt: Instant? = null,
    val suspendedAt: Instant? = null,
) {
    val isEmailVerified: Boolean get() = emailVerifiedAt != null
    val isPhoneVerified: Boolean get() = phoneVerifiedAt != null

    /// Two-letter monogram for avatars ("Amara Okafor" → "AO").
    val initials: String
        get() {
            val letters = listOf(firstName, lastName).map { it.trim() }.filter { it.isNotEmpty() }.take(2).mapNotNull { it.firstOrNull() }
            if (letters.isEmpty()) return name.firstOrNull()?.uppercase() ?: ""
            return letters.joinToString("").uppercase()
        }
}

/// Initials for any display name ("Ravensworth Plumbing" → "RP").
fun initialsOf(name: String): String =
    name.split(" ").filter { it.isNotEmpty() }.take(2).mapNotNull { it.firstOrNull() }.joinToString("").uppercase()
