package com.zenmode.app.data.local.database

import com.zenmode.app.domain.model.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `every status round-trips by name`() {
        SessionStatus.entries.forEach { status ->
            val stored = converters.fromSessionStatus(status)
            assertEquals(status.name, stored)
            assertEquals(status, converters.toSessionStatus(stored))
        }
    }

    @Test
    fun `unknown status decays to CANCELLED instead of throwing`() {
        assertEquals(SessionStatus.CANCELLED, converters.toSessionStatus("SOMETHING_NEW"))
        assertEquals(SessionStatus.CANCELLED, converters.toSessionStatus(""))
    }
}
