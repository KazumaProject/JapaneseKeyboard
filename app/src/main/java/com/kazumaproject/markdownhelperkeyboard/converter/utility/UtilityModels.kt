package com.kazumaproject.markdownhelperkeyboard.converter.utility

@JvmInline
value class UnitId(val value: String) {
    init {
        require(value.matches(Regex("[a-z0-9._-]+"))) { "UnitId must be stable ASCII" }
    }

    override fun toString(): String = value
}

enum class AngleMode { DEGREES, RADIANS }

enum class RegionalUnitProfile { JAPAN, UNITED_STATES, UNITED_KINGDOM }

enum class UnitCategory {
    LENGTH,
    AREA,
    VOLUME,
    MASS,
    TEMPERATURE,
    SPEED,
    PRESSURE,
    ENERGY,
    POWER,
    TIME,
    DATA,
}

sealed class Precision {
    data object Auto : Precision()

    @Deprecated("Use DecimalPlaces(0)")
    data object Integer : Precision()

    data class DecimalPlaces(val places: Int) : Precision() {
        init {
            require(places in MIN_DECIMAL_PLACES..MAX_DECIMAL_PLACES)
        }
    }

    data class SignificantDigits(val digits: Int) : Precision() {
        init {
            require(digits in MIN_DIGITS..MAX_DIGITS)
        }
    }

    companion object {
        const val MIN_DIGITS = 1
        const val MAX_DIGITS = 15
        const val MIN_DECIMAL_PLACES = 0
        const val MAX_DECIMAL_PLACES = 15
    }
}

data class UnitTargetSetting(
    val unitId: UnitId,
    val precision: Precision = Precision.Auto,
)

data class UtilityCandidateConfig(
    val calculationEnabled: Boolean = true,
    val unitConversionEnabled: Boolean = true,
    val includeExpressionCandidate: Boolean = true,
    val angleMode: AngleMode = AngleMode.DEGREES,
    val calculationPrecision: Precision = Precision.Auto,
    val regionalUnitProfile: RegionalUnitProfile = RegionalUnitProfile.JAPAN,
    val unitTargets: Map<UnitCategory, List<UnitTargetSetting>> = defaultUnitTargets(),
) {
    init {
        require(unitTargets.values.all { it.size <= MAX_TARGETS_PER_CATEGORY })
    }

    companion object {
        const val MAX_TARGETS_PER_CATEGORY = 8

        fun defaultUnitTargets(): Map<UnitCategory, List<UnitTargetSetting>> = mapOf(
            UnitCategory.LENGTH to targets("length.m" to 6, "length.cm" to 6, "length.ft" to 5, "length.in" to 5),
            UnitCategory.AREA to targets("area.m2" to 6, "area.km2" to 6, "area.ft2" to 5, "area.tsubo" to 5),
            UnitCategory.VOLUME to targets("volume.ml" to 6, "volume.l" to 6, "volume.m3" to 6, "volume.cup_jp" to 5),
            UnitCategory.MASS to targets("mass.g" to 6, "mass.kg" to 6, "mass.lb" to 3, "mass.oz" to 4),
            UnitCategory.TEMPERATURE to targets("temperature.c" to 6, "temperature.f" to 6, "temperature.k" to 6),
            UnitCategory.SPEED to targets("speed.kmh" to 6, "speed.ms" to 6, "speed.mph" to 5, "speed.kn" to 5),
            UnitCategory.PRESSURE to targets("pressure.kpa" to 6, "pressure.bar" to 6, "pressure.psi" to 5, "pressure.atm" to 6),
            UnitCategory.ENERGY to targets("energy.j" to 6, "energy.kj" to 6, "energy.kwh" to 6, "energy.kcal" to 5),
            UnitCategory.POWER to targets("power.w" to 6, "power.kw" to 6, "power.hp" to 5, "power.ps" to 5),
            UnitCategory.TIME to targets("time.s" to 6, "time.min" to 6, "time.h" to 6, "time.day" to 6),
            UnitCategory.DATA to targets("data.b" to 6, "data.kb" to 6, "data.mb" to 6, "data.gb" to 6),
        )

        private fun targets(vararg values: Pair<String, Int>) = values.map { (id, digits) ->
            UnitTargetSetting(UnitId(id), Precision.SignificantDigits(digits))
        }
    }
}

enum class UtilityTrigger {
    NONE,
    EXPLICIT_CALCULATION,
    EXPLICIT_UNIT_CONVERSION,
    AUTOMATIC_UNIT_CONVERSION,
}

enum class UtilityCandidateKind { CALCULATION, UNIT_CONVERSION, LITERAL }

data class UtilityCandidate(
    val text: String,
    val kind: UtilityCandidateKind,
)

data class UtilityCandidateResult(
    val candidates: List<UtilityCandidate> = emptyList(),
    val trigger: UtilityTrigger = UtilityTrigger.NONE,
    val preferredSourceText: String? = null,
) {
    val hasCandidates: Boolean get() = candidates.isNotEmpty()

    companion object {
        val Empty = UtilityCandidateResult()
    }
}
