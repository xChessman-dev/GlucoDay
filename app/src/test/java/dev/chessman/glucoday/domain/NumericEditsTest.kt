package dev.chessman.glucoday.domain

import org.junit.Assert.*
import org.junit.Test

class NumericEditsTest {
    @Test fun unchangedMgDlDisplayKeepsOriginalCanonicalMeasurement() {
        val original = 5.1234567890123
        val saved = NumericEdits.required("92", "92", original) { InputValidation.glucoseMmol(it, GlucoseUnit.MG_DL) }
        assertEquals(original.toBits(), saved.toBits())
    }

    @Test fun unchangedRoundedNutritionAndWeightKeepFullPrecision() {
        listOf("12,3" to 12.3456789, "257" to 257.456789, "70,1" to 70.123456789).forEach { (display, original) ->
            val saved = NumericEdits.required(display, display, original) { InputValidation.decimal(it, "Значение") }
            assertEquals(original.toBits(), saved.toBits())
        }
    }

    @Test fun unchangedTargetsPreserveDistinctValuesEvenIfDisplaysAreEqual() {
        val low = 5.111111111
        val high = 5.122222222
        val savedLow = NumericEdits.optional("5,1", "5,1", low) { InputValidation.glucoseMmol(it, GlucoseUnit.MMOL_L) }
        val savedHigh = NumericEdits.optional("5,1", "5,1", high) { InputValidation.glucoseMmol(it, GlucoseUnit.MMOL_L) }
        assertEquals(low, savedLow)
        assertEquals(high, savedHigh)
        InputValidation.profile(Profile(glucoseTargetMinMmol = savedLow, glucoseTargetMaxMmol = savedHigh))
    }

    @Test fun editedValueIsParsedAndOptionalFieldCanBeCleared() {
        val edited = NumericEdits.required("100", "92", 5.123456789) { InputValidation.glucoseMmol(it, GlucoseUnit.MG_DL) }
        assertEquals(GlucoseConversions.toMmol(100.0, GlucoseUnit.MG_DL), edited, 0.0)
        assertNull(NumericEdits.optional("", "257", 257.456789) { InputValidation.optionalDecimal(it, "Ккал") })
        assertEquals(5.6, NumericEdits.required("5,6", "", null) { InputValidation.glucoseMmol(it, GlucoseUnit.MMOL_L) }, 0.0)
    }

    @Test fun changedInvalidTextStillFailsValidation() {
        try {
            NumericEdits.required("NaN", "5,1", 5.123456789) { InputValidation.glucoseMmol(it, GlucoseUnit.MMOL_L) }
            fail("An edited invalid value must be rejected")
        } catch (_: IllegalArgumentException) { }
    }
}
