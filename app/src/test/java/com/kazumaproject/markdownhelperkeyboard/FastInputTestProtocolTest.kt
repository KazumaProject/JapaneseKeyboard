package com.kazumaproject.markdownhelperkeyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FastInputTestProtocolTest {
    @Test
    fun readinessTokenRoundTripsThroughPrivateImeOptions() {
        val token = "12345-9"

        assertEquals(
            token,
            FastInputTestProtocol.tokenFrom(FastInputTestProtocol.privateImeOptions(token)),
        )
    }

    @Test
    fun unrelatedOrEmptyPrivateImeOptionsAreIgnored() {
        assertNull(FastInputTestProtocol.tokenFrom(null))
        assertNull(FastInputTestProtocol.tokenFrom("com.example.option"))
        assertNull(
            FastInputTestProtocol.tokenFrom(FastInputTestProtocol.PRIVATE_IME_OPTION_PREFIX),
        )
    }
}
