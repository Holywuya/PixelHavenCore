package com.pixlehavencore.feature.realworld.temperature

data class TemperatureExposureSettings(
    val coldThreshold: Double,
    val heatThreshold: Double,
    val baseGainPerSecond: Double,
    val minGainPerSecond: Double,
    val recoveryPerSecond: Double,
    val minExtremeMultiplier: Double,
    val maxExtremeMultiplier: Double,
    val waterGainMultiplier: Double,
    val blockProtectionMax: Double,
    val blockProtectionFullModifier: Double,
)

data class TemperatureExposurePressures(
    val cold: Double,
    val heat: Double,
)

enum class ExposureDirection {
    NONE,
    COLD,
    HEAT,
}

object TemperatureExposureCalculator {

    fun detectDirection(
        effectiveEnvTemp: Double,
        comfortMin: Double,
        comfortMax: Double,
        settings: TemperatureExposureSettings,
    ): ExposureDirection {
        val coldBoundary = comfortMin - settings.coldThreshold
        val heatBoundary = comfortMax + settings.heatThreshold
        return when {
            effectiveEnvTemp < coldBoundary -> ExposureDirection.COLD
            effectiveEnvTemp > heatBoundary -> ExposureDirection.HEAT
            else -> ExposureDirection.NONE
        }
    }

    fun severity(
        effectiveEnvTemp: Double,
        comfortMin: Double,
        comfortMax: Double,
        direction: ExposureDirection,
        settings: TemperatureExposureSettings,
    ): Double {
        return when (direction) {
            ExposureDirection.COLD -> {
                val boundary = comfortMin - settings.coldThreshold
                val overshoot = (boundary - effectiveEnvTemp).coerceAtLeast(0.0)
                (1.0 + overshoot / settings.coldThreshold.coerceAtLeast(0.1)).coerceIn(1.0, 3.0)
            }
            ExposureDirection.HEAT -> {
                val boundary = comfortMax + settings.heatThreshold
                val overshoot = (effectiveEnvTemp - boundary).coerceAtLeast(0.0)
                (1.0 + overshoot / settings.heatThreshold.coerceAtLeast(0.1)).coerceIn(1.0, 3.0)
            }
            ExposureDirection.NONE -> 0.0
        }
    }

    fun updatePressures(
        coldPressure: Double,
        heatPressure: Double,
        direction: ExposureDirection,
        severity: Double,
        protectionScore: Double,
        isInWater: Boolean,
        tickSeconds: Double,
        settings: TemperatureExposureSettings,
    ): TemperatureExposurePressures {
        val dt = tickSeconds.coerceAtLeast(0.0)
        val recoveredCold = recover(coldPressure, dt, settings)
        val recoveredHeat = recover(heatPressure, dt, settings)

        return when (direction) {
            ExposureDirection.COLD -> TemperatureExposurePressures(
                cold = gain(coldPressure, severity, protectionScore, isInWater, dt, settings),
                heat = recoveredHeat,
            )
            ExposureDirection.HEAT -> TemperatureExposurePressures(
                cold = recoveredCold,
                heat = gain(heatPressure, severity, protectionScore, isInWater, dt, settings),
            )
            ExposureDirection.NONE -> TemperatureExposurePressures(
                cold = recoveredCold,
                heat = recoveredHeat,
            )
        }
    }

    fun multiplier(pressure: Double, settings: TemperatureExposureSettings): Double {
        val normalizedPressure = pressure.coerceIn(0.0, 1.0)
        val min = settings.minExtremeMultiplier
        val max = settings.maxExtremeMultiplier.coerceAtLeast(min)
        return min + (max - min) * normalizedPressure
    }

    fun blockProtection(
        direction: ExposureDirection,
        blockModifier: Double,
        settings: TemperatureExposureSettings,
    ): Double {
        val fullModifier = settings.blockProtectionFullModifier.coerceAtLeast(0.1)
        val rawProtection = when {
            direction == ExposureDirection.COLD && blockModifier > 0.0 -> blockModifier / fullModifier
            direction == ExposureDirection.HEAT && blockModifier < 0.0 -> -blockModifier / fullModifier
            else -> 0.0
        }
        return rawProtection.coerceIn(0.0, settings.blockProtectionMax)
    }

    fun activePressure(pressures: TemperatureExposurePressures, direction: ExposureDirection): Double {
        return when (direction) {
            ExposureDirection.COLD -> pressures.cold
            ExposureDirection.HEAT -> pressures.heat
            ExposureDirection.NONE -> 0.0
        }
    }

    private fun gain(
        current: Double,
        severity: Double,
        protectionScore: Double,
        isInWater: Boolean,
        tickSeconds: Double,
        settings: TemperatureExposureSettings,
    ): Double {
        val protectedGain = settings.baseGainPerSecond *
            severity.coerceAtLeast(0.0) *
            (1.0 - protectionScore.coerceIn(0.0, 1.0))
        val waterMultiplier = if (isInWater) settings.waterGainMultiplier else 1.0
        val gainPerSecond = (protectedGain * waterMultiplier).coerceAtLeast(settings.minGainPerSecond)
        return (current + gainPerSecond * tickSeconds).coerceIn(0.0, 1.0)
    }

    private fun recover(current: Double, tickSeconds: Double, settings: TemperatureExposureSettings): Double {
        return (current - settings.recoveryPerSecond * tickSeconds).coerceIn(0.0, 1.0)
    }
}
