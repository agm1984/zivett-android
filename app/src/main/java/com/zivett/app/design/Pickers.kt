package com.zivett.app.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateLabel = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.CANADA)

/// A calendar-day field: the stored value is "Y-m-d" ("" = none), shown
/// as "Tue, Nov 10, 2026"; tapping opens the Material date picker. Days
/// are LOCAL (never via UTC instants), like the web's `lib/dates.js`.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZDateField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, error: String? = null, placeholder: String = "No date", clearable: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    val date = value.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
    Column(modifier) {
        Box {
            ZTextField(label, date?.format(dateLabel) ?: "", {}, placeholder = placeholder, error = error, enabled = true)
            Box(Modifier.matchParentSize().clickable { open = true })
        }
        if (clearable && date != null) ZTextAction("Clear date") { onChange("") }
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = {
                state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                open = false
            }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

/// A time-of-day field: "HH:MM" ("" = none), shown "1:30 pm".
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZTimeField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, error: String? = null, placeholder: String = "Any time") {
    var open by remember { mutableStateOf(false) }
    val parts = value.split(":").mapNotNull { it.toIntOrNull() }.takeIf { it.size >= 2 }
    val shown = parts?.let { (h, m) -> "${if (h % 12 == 0) 12 else h % 12}:${"%02d".format(m)} ${if (h < 12) "am" else "pm"}" } ?: ""
    Column(modifier) {
        Box(Modifier.fillMaxWidth()) {
            ZTextField(label, shown, {}, placeholder = placeholder, error = error)
            Box(Modifier.matchParentSize().clickable { open = true })
        }
        if (parts != null) ZTextAction("Clear time") { onChange("") }
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = parts?.get(0) ?: 9, initialMinute = parts?.get(1) ?: 0, is24Hour = false)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { onChange("%02d:%02d".format(state.hour, state.minute)); open = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}
