package com.zivett.app.features.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.zivett.app.app.LocalAppEnvironment
import com.zivett.app.core.PhoneFormatter
import com.zivett.app.core.models.OwnAddressBody
import com.zivett.app.core.models.CustomerRefusal
import com.zivett.app.core.models.ExceptionRequestBody
import com.zivett.app.core.models.LabelValue
import com.zivett.app.core.models.PlanOffer
import com.zivett.app.core.models.ProCustomer
import com.zivett.app.core.models.ProCustomerBody
import com.zivett.app.core.models.ProCustomerEndpoints
import com.zivett.app.core.network.ApiClient
import com.zivett.app.core.network.ApiError
import com.zivett.app.core.network.JsonCoding
import com.zivett.app.core.network.userMessage
import com.zivett.app.design.ZAddressFields
import com.zivett.app.design.ZBody
import com.zivett.app.design.ZButton
import com.zivett.app.design.ZButtonStyle
import com.zivett.app.design.ZCaption
import com.zivett.app.design.ZCheckbox
import com.zivett.app.design.ZDropdown
import com.zivett.app.design.ZHeadline
import com.zivett.app.design.ZSheet
import com.zivett.app.design.ZSpacing
import com.zivett.app.design.ZTextArea
import com.zivett.app.design.ZTextField
import com.zivett.app.design.ZTextTone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/// What a new customer form starts with (a request being added).
data class CustomerPrefill(val name: String = "", val phone: String = "", val email: String = "", val address: String = "", val source: String = "")

/// The add / edit customer form (web `CustomerFormModal`): the ZiVETT
/// customer check runs on every save — a customer met on ZiVETT is
/// refused (with "This was my customer first"), an address match needs
/// "a different person" confirmed, and the plan's limit shows the plan
/// that lifts it.
class CustomerFormModel(private val client: ApiClient, val editing: ProCustomer?, prefill: CustomerPrefill?) {
    var name by mutableStateOf(editing?.name ?: prefill?.name.orEmpty())
    var phone by mutableStateOf(editing?.phone ?: prefill?.phone.orEmpty())
    var email by mutableStateOf(editing?.email ?: prefill?.email.orEmpty())
    var source by mutableStateOf(editing?.source ?: prefill?.source.orEmpty())
    var notes by mutableStateOf(editing?.notes.orEmpty())
    var noReminders by mutableStateOf(editing?.noReminders ?: false)
    var address by mutableStateOf(prefill?.address.orEmpty())
    var unit by mutableStateOf("")

    var errors by mutableStateOf<Map<String, String>>(emptyMap())
    var message by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
        private set
    /// Blocked: a ZiVETT customer (CRM-09).
    var refusal by mutableStateOf<String?>(null)
    /// Same address as a ZiVETT job: confirm it's someone else.
    var confirming by mutableStateOf<String?>(null)
    var contesting by mutableStateOf(false)
    var contestReason by mutableStateOf("")
    var contestError by mutableStateOf<String?>(null)
    var contestSent by mutableStateOf(false)
    /// The plan's customer limit was reached.
    var limit by mutableStateOf<PlanOffer?>(null)
    var atLimit by mutableStateOf(false)

    private fun body(confirmDifferent: Boolean) = ProCustomerBody(
        name = name.trim(),
        phone = phone.trim().ifBlank { null },
        email = email.trim().ifBlank { null },
        source = source.ifBlank { null },
        notes = notes.trim().ifBlank { null },
        noReminders = noReminders,
        address = if (editing == null && address.isNotBlank()) OwnAddressBody(address.trim(), unit.trim().ifBlank { null }) else null,
        confirmDifferentPerson = if (confirmDifferent) true else null,
    )

    /// The saved customer, or null when the form stays open.
    suspend fun save(confirmDifferent: Boolean = false): ProCustomer? {
        errors = emptyMap(); message = null
        saving = true
        return try {
            if (editing != null) client.send(ProCustomerEndpoints.update(editing.id, body(confirmDifferent))).customer
            else client.send(ProCustomerEndpoints.create(body(confirmDifferent))).customer
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError.Validation) {
            when (e.errors.code) {
                "zivett_customer" -> refusal = e.errors.message
                "limit_reached" -> atLimit = true
                else -> { errors = e.errors.firstMessages; if (errors.isEmpty()) message = e.errors.message }
            }
            null
        } catch (e: ApiError.Conflict) {
            val refusal = runCatching { JsonCoding.json.decodeFromString<CustomerRefusal>(e.body.decodeToString()) }.getOrNull()
            if (refusal?.code == "confirm_different_person") confirming = refusal.message ?: e.userMessage else message = e.userMessage
            null
        } catch (e: Exception) {
            message = e.userMessage
            null
        } finally {
            saving = false
        }
    }

    /// "This was my customer first": the details typed, plus why.
    suspend fun sendContest() {
        contestError = null
        if (contestReason.trim().length < 10) { contestError = "Tell us a little more, such as when you first worked for them."; return }
        saving = true
        try {
            val body = body(false)
            client.send(ProCustomerEndpoints.exceptionRequest(ExceptionRequestBody(body.name, body.phone, body.email, body.source, body.notes, if (address.isNotBlank()) OwnAddressBody(address.trim(), unit.trim().ifBlank { null }) else null, contestReason.trim(), editing?.id)))
            contestSent = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            contestError = (e as? ApiError)?.first("reason") ?: e.userMessage
        } finally {
            saving = false
        }
    }
}

/// The form as a sheet. `onSaved` gets the saved customer (the sheet is
/// closed by then); `onPlan` opens the Plan screen from the limit card.
@Composable
fun CustomerFormSheet(
    editing: ProCustomer? = null,
    prefill: CustomerPrefill? = null,
    sources: List<LabelValue>,
    atLimit: Boolean = false,
    upgrade: PlanOffer? = null,
    onPlan: () -> Unit,
    onDismiss: () -> Unit,
    onSaved: (ProCustomer) -> Unit,
) {
    val environment = LocalAppEnvironment.current
    val scope = rememberCoroutineScope()
    val model = remember(editing?.id) { CustomerFormModel(environment.client, editing, prefill).also { it.limit = upgrade; if (atLimit && editing == null) it.atLimit = true } }

    ZSheet(onDismiss, title = if (editing != null) "Edit customer" else "Add a customer", dismissable = !model.saving) {
        when {
            model.atLimit -> {
                ZHeadline("You've reached your customer limit")
                ZBody(model.limit?.let { "Your plan holds this many customers. The ${it.name} plan holds more." } ?: "Your plan holds this many customers.")
                ZCaption("Archived customers count too. Invoices and recording payments are never limited.", tone = ZTextTone.SOFT)
                if (model.limit != null) ZButton("See plans") { onDismiss(); onPlan() }
                ZButton("Close", style = ZButtonStyle.OUTLINE, onClick = onDismiss)
            }
            model.refusal != null -> {
                ZHeadline("This is a ZiVETT customer")
                ZBody(model.refusal.orEmpty())
                ZCaption("When they need work again, they book through ZiVETT like any other customer.", tone = ZTextTone.SOFT)
                if (model.contestSent) {
                    ZBody("Sent to ZiVETT support. If we agree, they're added to your list, and you're told either way.")
                    ZButton("Close", onClick = onDismiss)
                } else if (model.contesting) {
                    ZTextArea("How do you know them?", model.contestReason, { model.contestReason = it.take(1000) }, placeholder = "They've been my customer since 2021. I have invoices from before they used ZiVETT.", error = model.contestError, minLines = 3)
                    ZCaption("ZiVETT support reads this with the details you typed, and may ask for an older invoice. If we agree, they're added to your list and you're told either way.")
                    ZButton("Send to support", loading = model.saving) { scope.launch { model.sendContest() } }
                    ZButton("Back", style = ZButtonStyle.GHOST) { model.contesting = false }
                } else {
                    ZButton("This was my customer first", style = ZButtonStyle.OUTLINE) { model.contesting = true }
                    ZButton("Change details", style = ZButtonStyle.GHOST) { model.refusal = null }
                    ZButton("Close", onClick = onDismiss)
                }
            }
            model.confirming != null -> {
                ZHeadline("Is this a different person?")
                ZBody(model.confirming.orEmpty())
                ZButton("Yes, it's a different person", loading = model.saving) { scope.launch { model.save(confirmDifferent = true)?.let { onDismiss(); onSaved(it) } } }
                ZButton("Go back", style = ZButtonStyle.GHOST) { model.confirming = null }
            }
            else -> {
                ZTextField("Name", model.name, { model.name = it }, placeholder = "Pat Lee", error = model.errors["name"], capitalization = KeyboardCapitalization.Words)
                ZTextField("Phone", model.phone, { model.phone = PhoneFormatter.mask(it) }, placeholder = "(555) 012-3456", error = model.errors["phone"], keyboardType = KeyboardType.Phone)
                ZTextField("Email", model.email, { model.email = it }, placeholder = "pat@example.com", error = model.errors["email"], keyboardType = KeyboardType.Email)
                ZCaption("A phone number or an email address, or both.")
                if (editing?.sourceLocked == true) {
                    ZTextField("Where they came from", editing.sourceLabel ?: "", {}, enabled = false)
                    ZCaption("Locked once you've sent them an invoice. ZiVETT support can change it.")
                } else {
                    ZDropdown("Where they came from", model.source, sources.map { it.value to it.label }, placeholder = "Choose one", error = model.errors["source"]) { model.source = it }
                }
                if (editing == null) {
                    ZAddressFields(model.address, { model.address = it }, error = model.errors["address.address_line1"], lookupClient = environment.client)
                    ZTextField("Unit, suite, etc. (optional)", model.unit, { model.unit = it }, error = model.errors["address.address_line2"])
                }
                ZCheckbox(model.noReminders, { model.noReminders = it }, "Don't send this customer automatic reminders")
                ZTextArea("Private notes (optional)", model.notes, { model.notes = it.take(2000) }, placeholder = "Gate code, pets, best time to call", error = model.errors["notes"], minLines = 3)
                if (editing == null) ZCaption("Your customers are yours. ZiVETT never contacts them, markets to them or shows them to other Pros.")
                model.message?.let { com.zivett.app.design.ZBanner(it, tone = com.zivett.app.design.ZTone.DANGER) }
                Row(horizontalArrangement = Arrangement.spacedBy(ZSpacing.sm)) {
                    ZButton("Cancel", style = ZButtonStyle.GHOST, modifier = Modifier.weight(1f), onClick = onDismiss)
                    ZButton(if (editing != null) "Save changes" else "Add customer", modifier = Modifier.weight(1f), loading = model.saving, enabled = model.name.isNotBlank()) {
                        scope.launch { model.save()?.let { onDismiss(); onSaved(it) } }
                    }
                }
            }
        }
    }
}
