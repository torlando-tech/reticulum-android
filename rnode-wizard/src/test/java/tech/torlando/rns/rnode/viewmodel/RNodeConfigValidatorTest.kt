package tech.torlando.rns.rnode.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tech.torlando.rns.rnode.data.FrequencyRegion

class RNodeConfigValidatorTest {

    private val eu868 = FrequencyRegion(
        id = "eu868",
        name = "EU 863-870 MHz",
        frequencyStart = 863_000_000,
        frequencyEnd = 870_000_000,
        maxTxPower = 14,
        defaultTxPower = 14,
        dutyCycle = 1,
        description = "Europe",
    )

    private val us915 = FrequencyRegion(
        id = "us915",
        name = "US 902-928 MHz",
        frequencyStart = 902_000_000,
        frequencyEnd = 928_000_000,
        maxTxPower = 22,
        defaultTxPower = 22,
        dutyCycle = 100,
        description = "United States",
    )

    // --- Name validation ---

    @Test
    fun `blank name is invalid`() {
        assertFalse(RNodeConfigValidator.validateName("").isValid)
        assertFalse(RNodeConfigValidator.validateName("   ").isValid)
    }

    @Test
    fun `non-blank name is valid`() {
        assertTrue(RNodeConfigValidator.validateName("My RNode").isValid)
    }

    // --- Frequency validation ---

    @Test
    fun `blank frequency is valid (allows typing)`() {
        assertTrue(RNodeConfigValidator.validateFrequency("", null).isValid)
    }

    @Test
    fun `non-numeric frequency is invalid`() {
        val result = RNodeConfigValidator.validateFrequency("abc", null)
        assertFalse(result.isValid)
        assertEquals("Invalid number", result.errorMessage)
    }

    @Test
    fun `frequency within default range is valid`() {
        assertTrue(RNodeConfigValidator.validateFrequency("868000000", null).isValid)
    }

    @Test
    fun `frequency below default range is invalid`() {
        assertFalse(RNodeConfigValidator.validateFrequency("100000000", null).isValid)
    }

    @Test
    fun `frequency above default range is invalid`() {
        assertFalse(RNodeConfigValidator.validateFrequency("4000000000", null).isValid)
    }

    @Test
    fun `frequency within region range is valid`() {
        assertTrue(RNodeConfigValidator.validateFrequency("868000000", eu868).isValid)
    }

    @Test
    fun `frequency outside region range is invalid`() {
        assertFalse(RNodeConfigValidator.validateFrequency("915000000", eu868).isValid)
    }

    @Test
    fun `frequency at region boundary is valid`() {
        assertTrue(RNodeConfigValidator.validateFrequency("863000000", eu868).isValid)
        assertTrue(RNodeConfigValidator.validateFrequency("870000000", eu868).isValid)
    }

    // --- Bandwidth validation ---

    @Test
    fun `blank bandwidth is valid`() {
        assertTrue(RNodeConfigValidator.validateBandwidth("").isValid)
    }

    @Test
    fun `bandwidth below minimum is invalid`() {
        assertFalse(RNodeConfigValidator.validateBandwidth("5000").isValid)
    }

    @Test
    fun `bandwidth above maximum is invalid`() {
        assertFalse(RNodeConfigValidator.validateBandwidth("2000000").isValid)
    }

    @Test
    fun `bandwidth at boundaries is valid`() {
        assertTrue(RNodeConfigValidator.validateBandwidth("7800").isValid)
        assertTrue(RNodeConfigValidator.validateBandwidth("1625000").isValid)
    }

    @Test
    fun `common bandwidth 125000 is valid`() {
        assertTrue(RNodeConfigValidator.validateBandwidth("125000").isValid)
    }

    // --- Spreading factor validation ---

    @Test
    fun `spreading factor range 5 to 12`() {
        assertFalse(RNodeConfigValidator.validateSpreadingFactor("4").isValid)
        assertTrue(RNodeConfigValidator.validateSpreadingFactor("5").isValid)
        assertTrue(RNodeConfigValidator.validateSpreadingFactor("12").isValid)
        assertFalse(RNodeConfigValidator.validateSpreadingFactor("13").isValid)
    }

    // --- Coding rate validation ---

    @Test
    fun `coding rate range 5 to 8`() {
        assertFalse(RNodeConfigValidator.validateCodingRate("4").isValid)
        assertTrue(RNodeConfigValidator.validateCodingRate("5").isValid)
        assertTrue(RNodeConfigValidator.validateCodingRate("8").isValid)
        assertFalse(RNodeConfigValidator.validateCodingRate("9").isValid)
    }

    // --- TX power validation ---

    @Test
    fun `tx power within default range`() {
        assertTrue(RNodeConfigValidator.validateTxPower("0", null).isValid)
        assertTrue(RNodeConfigValidator.validateTxPower("22", null).isValid)
        assertFalse(RNodeConfigValidator.validateTxPower("23", null).isValid)
    }

    @Test
    fun `tx power constrained by region`() {
        assertTrue(RNodeConfigValidator.validateTxPower("14", eu868).isValid)
        assertFalse(RNodeConfigValidator.validateTxPower("15", eu868).isValid)
    }

    @Test
    fun `negative tx power is invalid`() {
        assertFalse(RNodeConfigValidator.validateTxPower("-1", null).isValid)
    }

    // --- Airtime limit validation ---

    @Test
    fun `blank airtime is valid (no limit)`() {
        assertTrue(RNodeConfigValidator.validateAirtimeLimit("", eu868).isValid)
    }

    @Test
    fun `airtime over 100 percent is invalid`() {
        assertFalse(RNodeConfigValidator.validateAirtimeLimit("101", null).isValid)
    }

    @Test
    fun `negative airtime is invalid`() {
        assertFalse(RNodeConfigValidator.validateAirtimeLimit("-1", null).isValid)
    }

    @Test
    fun `airtime exceeding region duty cycle is invalid`() {
        // EU has 1% duty cycle
        assertFalse(RNodeConfigValidator.validateAirtimeLimit("2", eu868).isValid)
        assertTrue(RNodeConfigValidator.validateAirtimeLimit("1", eu868).isValid)
    }

    @Test
    fun `airtime with no duty cycle limit allows up to 100`() {
        // US has 100% duty cycle (no limit)
        assertTrue(RNodeConfigValidator.validateAirtimeLimit("50", us915).isValid)
        assertTrue(RNodeConfigValidator.validateAirtimeLimit("100", us915).isValid)
    }

    // --- Full config validation ---

    @Test
    fun `valid full config passes`() {
        val input = RNodeConfigInput(
            name = "Test RNode",
            frequency = "868000000",
            bandwidth = "125000",
            spreadingFactor = "8",
            codingRate = "5",
            txPower = "14",
            stAlock = "",
            ltAlock = "",
            region = eu868,
        )
        assertTrue(RNodeConfigValidator.validateConfigSilent(input))
        assertTrue(RNodeConfigValidator.validateConfig(input).isValid)
    }

    @Test
    fun `blank required fields fail silent validation`() {
        val input = RNodeConfigInput(
            name = "Test",
            frequency = "",
            bandwidth = "125000",
            spreadingFactor = "8",
            codingRate = "5",
            txPower = "14",
            stAlock = "",
            ltAlock = "",
            region = null,
        )
        assertFalse(RNodeConfigValidator.validateConfigSilent(input))
    }

    @Test
    fun `full validation returns error messages for blank fields`() {
        val input = RNodeConfigInput(
            name = "Test",
            frequency = "",
            bandwidth = "",
            spreadingFactor = "",
            codingRate = "",
            txPower = "",
            stAlock = "",
            ltAlock = "",
            region = null,
        )
        val result = RNodeConfigValidator.validateConfig(input)
        assertFalse(result.isValid)
        assertTrue(result.frequencyError != null)
        assertTrue(result.bandwidthError != null)
        assertTrue(result.spreadingFactorError != null)
        assertTrue(result.codingRateError != null)
        assertTrue(result.txPowerError != null)
    }

    @Test
    fun `valid config has no error messages`() {
        val input = RNodeConfigInput(
            name = "Test",
            frequency = "915000000",
            bandwidth = "125000",
            spreadingFactor = "7",
            codingRate = "5",
            txPower = "22",
            stAlock = "",
            ltAlock = "",
            region = us915,
        )
        val result = RNodeConfigValidator.validateConfig(input)
        assertTrue(result.isValid)
        assertNull(result.nameError)
        assertNull(result.frequencyError)
        assertNull(result.bandwidthError)
        assertNull(result.spreadingFactorError)
        assertNull(result.codingRateError)
        assertNull(result.txPowerError)
    }

    // --- Helper methods ---

    @Test
    fun `getMaxTxPower returns region value or default`() {
        assertEquals(14, RNodeConfigValidator.getMaxTxPower(eu868))
        assertEquals(22, RNodeConfigValidator.getMaxTxPower(null))
    }

    @Test
    fun `getFrequencyRange returns region bounds or defaults`() {
        val (start, end) = RNodeConfigValidator.getFrequencyRange(eu868)
        assertEquals(863_000_000L, start)
        assertEquals(870_000_000L, end)

        val (defStart, defEnd) = RNodeConfigValidator.getFrequencyRange(null)
        assertEquals(137_000_000L, defStart)
        assertEquals(3_000_000_000L, defEnd)
    }

    @Test
    fun `getMaxAirtimeLimit returns duty cycle or null`() {
        assertEquals(1.0, RNodeConfigValidator.getMaxAirtimeLimit(eu868)!!, 0.001)
        assertNull(RNodeConfigValidator.getMaxAirtimeLimit(us915))
    }
}
