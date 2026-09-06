package com.zivett.app.core

/// The web's `lib/phone.js` mask: ten digits → "(555) 012-3456". The
/// server strips formatting again (`RegisterRequest::prepareForValidation`)
/// so either form is accepted; the mask is purely for the person typing.
object PhoneFormatter {
    fun digits(value: String): String = value.filter { it.isDigit() }.take(10)

    fun mask(value: String): String {
        val digits = digits(value)
        if (digits.isEmpty()) return ""

        val area = digits.take(3)
        val exchange = digits.drop(3).take(3)
        val line = digits.drop(6)

        var result = "($area"
        if (digits.length > 3) result += ") $exchange"
        if (digits.length > 6) result += "-$line"
        return result
    }

    fun isComplete(value: String): Boolean = digits(value).length == 10
}
