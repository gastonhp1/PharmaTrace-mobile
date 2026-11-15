package ar.pharmatrace.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColdChainTest {
    @Test
    fun `excursion reason is translated with both durations`() {
        assertEquals(
            "35 min 40 s fuera de rango; la política tolera 30 min 00 s",
            verdictReason("2140 seconds out of range, the policy allows 1800"),
        )
    }

    @Test
    fun `broken seal reason uses the given time format`() {
        assertEquals("Precinto roto el t=1793300400", verdictReason("seal broken at 1793300400") { "t=$it" })
    }

    @Test
    fun `unknown text is kept and blank is no reason`() {
        assertEquals("something new", verdictReason(" something new "))
        assertNull(verdictReason("  "))
        assertNull(verdictReason(null))
    }
}
