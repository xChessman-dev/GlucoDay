package dev.chessman.glucoday.domain

/** A rounded display string is not a new measurement until the user changes that field. */
object NumericEdits {
    fun required(
        text: String,
        originalText: String,
        originalValue: Double?,
        parse: (String) -> Double,
    ): Double = if (originalValue != null && text == originalText) originalValue else parse(text)

    fun optional(
        text: String,
        originalText: String,
        originalValue: Double?,
        parse: (String) -> Double?,
    ): Double? = if (text == originalText) originalValue else parse(text)
}
