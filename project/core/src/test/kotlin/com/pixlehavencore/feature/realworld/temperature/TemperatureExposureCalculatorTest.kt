package com.pixlehavencore.feature.realworld.temperature

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TemperatureExposureCalculatorTest {

    private val settings = TemperatureExposureSettings(
        coldThreshold = 10.0,
        heatThreshold = 8.0,
        baseGainPerSecond = 0.018,
        minGainPerSecond = 0.0025,
        recoveryPerSecond = 0.01,
        minExtremeMultiplier = 0.35,
        maxExtremeMultiplier = 1.0,
        waterGainMultiplier = 1.5,
        blockProtectionMax = 0.4,
        blockProtectionFullModifier = 20.0,
    )

    @Test
    fun `detects cold and heat exposure direction`() {
        assertEquals(
            ExposureDirection.COLD,
            TemperatureExposureCalculator.detectDirection(
                effectiveEnvTemp = 0.0,
                comfortMin = 15.0,
                comfortMax = 36.0,
                settings = settings,
            ),
        )
        assertEquals(
            ExposureDirection.HEAT,
            TemperatureExposureCalculator.detectDirection(
                effectiveEnvTemp = 50.0,
                comfortMin = 15.0,
                comfortMax = 36.0,
                settings = settings,
            ),
        )
        assertEquals(
            ExposureDirection.NONE,
            TemperatureExposureCalculator.detectDirection(
                effectiveEnvTemp = 25.0,
                comfortMin = 15.0,
                comfortMax = 36.0,
                settings = settings,
            ),
        )
    }

    @Test
    fun `naked cold exposure grows pressure faster than protected exposure`() {
        val naked = TemperatureExposureCalculator.updatePressures(
            coldPressure = 0.0,
            heatPressure = 0.0,
            direction = ExposureDirection.COLD,
            severity = 2.0,
            protectionScore = 0.0,
            isInWater = false,
            tickSeconds = 10.0,
            settings = settings,
        )
        val protected = TemperatureExposureCalculator.updatePressures(
            coldPressure = 0.0,
            heatPressure = 0.0,
            direction = ExposureDirection.COLD,
            severity = 2.0,
            protectionScore = 0.7,
            isInWater = false,
            tickSeconds = 10.0,
            settings = settings,
        )

        assertTrue(naked.cold > protected.cold)
        assertEquals(0.36, naked.cold, 0.0001)
        assertEquals(0.108, protected.cold, 0.0001)
    }

    @Test
    fun `cold exposure recovers heat pressure`() {
        val result = TemperatureExposureCalculator.updatePressures(
            coldPressure = 0.0,
            heatPressure = 0.5,
            direction = ExposureDirection.COLD,
            severity = 1.0,
            protectionScore = 0.0,
            isInWater = false,
            tickSeconds = 10.0,
            settings = settings,
        )

        assertTrue(result.cold > 0.0)
        assertEquals(0.4, result.heat, 0.0001)
    }

    @Test
    fun `non extreme environment recovers both pressures`() {
        val result = TemperatureExposureCalculator.updatePressures(
            coldPressure = 0.5,
            heatPressure = 0.25,
            direction = ExposureDirection.NONE,
            severity = 0.0,
            protectionScore = 0.0,
            isInWater = false,
            tickSeconds = 10.0,
            settings = settings,
        )

        assertEquals(0.4, result.cold, 0.0001)
        assertEquals(0.15, result.heat, 0.0001)
    }

    @Test
    fun `water exposure applies gain multiplier`() {
        val air = TemperatureExposureCalculator.updatePressures(
            coldPressure = 0.0,
            heatPressure = 0.0,
            direction = ExposureDirection.COLD,
            severity = 1.0,
            protectionScore = 0.0,
            isInWater = false,
            tickSeconds = 10.0,
            settings = settings,
        )
        val water = TemperatureExposureCalculator.updatePressures(
            coldPressure = 0.0,
            heatPressure = 0.0,
            direction = ExposureDirection.COLD,
            severity = 1.0,
            protectionScore = 0.0,
            isInWater = true,
            tickSeconds = 10.0,
            settings = settings,
        )

        assertEquals(air.cold * 1.5, water.cold, 0.0001)
    }

    @Test
    fun `multiplier interpolates between configured bounds`() {
        assertEquals(0.35, TemperatureExposureCalculator.multiplier(0.0, settings), 0.0001)
        assertEquals(0.675, TemperatureExposureCalculator.multiplier(0.5, settings), 0.0001)
        assertEquals(1.0, TemperatureExposureCalculator.multiplier(1.0, settings), 0.0001)
    }

    @Test
    fun `block protection only helps matching exposure direction`() {
        assertEquals(
            0.4,
            TemperatureExposureCalculator.blockProtection(
                direction = ExposureDirection.COLD,
                blockModifier = 25.0,
                settings = settings,
            ),
            0.0001,
        )
        assertEquals(
            0.0,
            TemperatureExposureCalculator.blockProtection(
                direction = ExposureDirection.COLD,
                blockModifier = -25.0,
                settings = settings,
            ),
            0.0001,
        )
        assertEquals(
            0.4,
            TemperatureExposureCalculator.blockProtection(
                direction = ExposureDirection.HEAT,
                blockModifier = -25.0,
                settings = settings,
            ),
            0.0001,
        )
    }
}
