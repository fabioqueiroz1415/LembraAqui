package com.lembraaqui.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateParserTest {
    @Test fun parsesCommaDecimalWithCommaSeparator() {
        val result = CoordinateParser.parse("-12,1231234, -47,9238498234")
        assertTrue(result is CoordinateParseResult.Success)
        val c = (result as CoordinateParseResult.Success).coordinates
        assertEquals(-12.1231234, c.latitude, 0.0000001)
        assertEquals(-47.9238498234, c.longitude, 0.0000001)
    }

    @Test fun parsesWithoutSpaces() {
        val result = CoordinateParser.parse("-12,1231234,-47,9238498234")
        assertTrue(result is CoordinateParseResult.Success)
    }

    @Test fun parsesSpaceSeparated() {
        val result = CoordinateParser.parse("-12,1231234 -47,9238498234")
        assertTrue(result is CoordinateParseResult.Success)
    }

    @Test fun acceptsDotDecimal() {
        val result = CoordinateParser.parse("-12.123, -47.923")
        assertTrue(result is CoordinateParseResult.Success)
    }

    @Test fun rejectsLatitudeOutsideRange() {
        val result = CoordinateParser.parse("-91,0000, -47,0000")
        assertTrue(result is CoordinateParseResult.Error)
    }

    @Test fun rejectsLongitudeOutsideRange() {
        val result = CoordinateParser.parse("-12,0000, -181,0000")
        assertTrue(result is CoordinateParseResult.Error)
    }
}
