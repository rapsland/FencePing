package de.fenceping.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordParseTest {
    @Test
    fun commaDecimalParses() {
        assertEquals(51.6736, "51,6736".replace(',', '.').toDoubleOrNull())
        assertEquals(8.3448, "8.3448".replace(',', '.').toDoubleOrNull())
    }

    @Test
    fun garbageIsNull() {
        assertNull("abc".replace(',', '.').toDoubleOrNull())
    }

    @Test
    fun radiusClamped() {
        val r = 10f.coerceIn(50f, 2000f)
        assertEquals(50f, r)
        assertEquals(2000f, 9999f.coerceIn(50f, 2000f))
    }
}
