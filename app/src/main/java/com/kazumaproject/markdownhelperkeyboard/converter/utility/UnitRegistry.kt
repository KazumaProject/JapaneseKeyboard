package com.kazumaproject.markdownhelperkeyboard.converter.utility

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

data class UnitDefinition(
    val id: UnitId,
    val category: UnitCategory,
    val symbol: String,
    val factorToBase: BigDecimal,
    val offsetToBase: BigDecimal = BigDecimal.ZERO,
    val exactAliases: Set<String> = emptySet(),
    val wordAliases: Set<String> = emptySet(),
) {
    fun toBase(value: BigDecimal, context: MathContext): BigDecimal =
        value.multiply(factorToBase, context).add(offsetToBase, context)

    fun fromBase(value: BigDecimal, context: MathContext): BigDecimal =
        value.subtract(offsetToBase, context).divide(factorToBase, context)
}

class UnitRegistry private constructor(
    definitions: List<UnitDefinition>,
) {
    private data class AliasEntry(
        val alias: String,
        val ignoreCase: Boolean,
        val definition: UnitDefinition,
    )

    data class Match(val definition: UnitDefinition, val length: Int)

    private val byId = definitions.associateBy(UnitDefinition::id)
    private val aliasEntries = definitions.flatMap { definition ->
        val exact = (definition.exactAliases + definition.symbol).map {
            AliasEntry(it, false, definition)
        }
        val folded = definition.wordAliases.map { AliasEntry(it, true, definition) }
        exact + folded
    }.distinctBy { Triple(it.alias, it.ignoreCase, it.definition.id) }
        .sortedByDescending { it.alias.length }

    val units: List<UnitDefinition> = definitions.toList()

    fun findById(id: UnitId): UnitDefinition? = byId[id]

    fun findExact(
        text: String,
        expectedCategory: UnitCategory? = null,
        profile: RegionalUnitProfile = RegionalUnitProfile.JAPAN,
    ): UnitDefinition? {
        val matches = matchCandidates(text, 0)
            .filter { it.length == text.length }
            .map(Match::definition)
            .filter { expectedCategory == null || it.category == expectedCategory }
        return resolveAmbiguity(matches, profile, expectedCategory)
    }

    fun matchAt(
        text: String,
        offset: Int,
        expectedCategory: UnitCategory? = null,
        profile: RegionalUnitProfile = RegionalUnitProfile.JAPAN,
    ): Match? {
        val candidates = matchCandidates(text, offset)
        val categoryMatches = if (expectedCategory == null) {
            candidates
        } else {
            candidates.filter { it.definition.category == expectedCategory }
        }
        val longest = categoryMatches.maxOfOrNull(Match::length) ?: return null
        val definitions = categoryMatches.filter { it.length == longest }.map(Match::definition)
        return resolveAmbiguity(definitions, profile, expectedCategory)?.let { Match(it, longest) }
    }

    private fun matchCandidates(text: String, offset: Int): List<Match> = aliasEntries.mapNotNull { entry ->
        if (offset + entry.alias.length > text.length) return@mapNotNull null
        val characterAfterAlias = text.getOrNull(offset + entry.alias.length)
        if (entry.alias.lastOrNull()?.isDigit() == true && characterAfterAlias?.isLetter() == true) {
            return@mapNotNull null
        }
        if (text.regionMatches(offset, entry.alias, 0, entry.alias.length, entry.ignoreCase)) {
            Match(entry.definition, entry.alias.length)
        } else {
            null
        }
    }

    private fun resolveAmbiguity(
        definitions: List<UnitDefinition>,
        profile: RegionalUnitProfile,
        expectedCategory: UnitCategory?,
    ): UnitDefinition? {
        val distinct = definitions.distinctBy(UnitDefinition::id)
        if (distinct.isEmpty()) return null
        if (distinct.size == 1) return distinct.single()

        fun find(suffix: String) = distinct.firstOrNull { it.id.value.endsWith(suffix) }
        if (expectedCategory == UnitCategory.TIME) find(".min")?.let { return it }
        when (distinct.first().category) {
            UnitCategory.VOLUME -> when (profile) {
                RegionalUnitProfile.JAPAN -> find("_jp") ?: find("_us")
                RegionalUnitProfile.UNITED_STATES -> find("_us")
                RegionalUnitProfile.UNITED_KINGDOM -> find("_uk")
            }?.let { return it }
            UnitCategory.MASS -> when (profile) {
                RegionalUnitProfile.JAPAN -> find("mass.t")
                RegionalUnitProfile.UNITED_STATES -> find("short_ton")
                RegionalUnitProfile.UNITED_KINGDOM -> find("long_ton")
            }?.let { return it }
            UnitCategory.POWER -> when (profile) {
                RegionalUnitProfile.JAPAN -> find(".ps")
                RegionalUnitProfile.UNITED_STATES,
                RegionalUnitProfile.UNITED_KINGDOM -> find(".hp")
            }?.let { return it }
            else -> Unit
        }
        return distinct.firstOrNull()
    }

    companion object {
        val Default: UnitRegistry by lazy { UnitRegistry(createDefinitions()) }

        private val registryContext = MathContext(50, RoundingMode.HALF_EVEN)

        private fun decimal(value: String) = BigDecimal(value)
        private fun rational(numerator: String, denominator: String) =
            decimal(numerator).divide(decimal(denominator), registryContext)

        private fun unit(
            id: String,
            category: UnitCategory,
            symbol: String,
            factor: String,
            vararg aliases: String,
            words: Set<String> = emptySet(),
            offset: BigDecimal = BigDecimal.ZERO,
        ) = UnitDefinition(
            id = UnitId(id),
            category = category,
            symbol = symbol,
            factorToBase = decimal(factor),
            offsetToBase = offset,
            exactAliases = aliases.toSet(),
            wordAliases = words,
        )

        private fun createDefinitions(): List<UnitDefinition> = buildList {
            // Length (base: metre)
            add(unit("length.nm", UnitCategory.LENGTH, "nm", "1e-9", words = setOf("nanometer", "nanometers", "nanometre", "nanometres", "ナノメートル")))
            add(unit("length.um", UnitCategory.LENGTH, "µm", "1e-6", "um", "μm", words = setOf("micrometer", "micrometers", "micrometre", "micrometres", "マイクロメートル")))
            add(unit("length.mm", UnitCategory.LENGTH, "mm", "1e-3", words = setOf("millimeter", "millimeters", "millimetre", "millimetres", "ミリメートル")))
            add(unit("length.cm", UnitCategory.LENGTH, "cm", "1e-2", words = setOf("centimeter", "centimeters", "centimetre", "centimetres", "センチ", "センチメートル")))
            add(unit("length.m", UnitCategory.LENGTH, "m", "1", words = setOf("meter", "meters", "metre", "metres", "メートル")))
            add(unit("length.km", UnitCategory.LENGTH, "km", "1e3", words = setOf("kilometer", "kilometers", "kilometre", "kilometres", "キロ", "キロメートル")))
            add(unit("length.angstrom", UnitCategory.LENGTH, "Å", "1e-10", "Å", words = setOf("angstrom", "angstroms", "オングストローム")))
            add(unit("length.in", UnitCategory.LENGTH, "in", "0.0254", "\"", "″", words = setOf("inch", "inches", "インチ")))
            add(unit("length.ft", UnitCategory.LENGTH, "ft", "0.3048", "'", "′", words = setOf("foot", "feet", "フィート")))
            add(unit("length.yd", UnitCategory.LENGTH, "yd", "0.9144", words = setOf("yard", "yards", "ヤード")))
            add(unit("length.mi", UnitCategory.LENGTH, "mi", "1609.344", words = setOf("mile", "miles", "マイル")))
            add(unit("length.nmi", UnitCategory.LENGTH, "nmi", "1852", words = setOf("nautical mile", "nautical miles", "海里")))
            add(unit("length.shaku", UnitCategory.LENGTH, "尺", rational("10", "33").toPlainString(), words = setOf("しゃく")))
            add(unit("length.sun", UnitCategory.LENGTH, "寸", rational("1", "33").toPlainString(), words = setOf("すん")))
            add(unit("length.kujirajaku", UnitCategory.LENGTH, "鯨尺", rational("25", "66").toPlainString()))
            add(unit("length.kujirasun", UnitCategory.LENGTH, "鯨寸", rational("5", "132").toPlainString()))

            // Area (base: square metre)
            add(unit("area.mm2", UnitCategory.AREA, "mm²", "1e-6", "mm2", "mm^2", words = setOf("square millimeter", "square millimeters", "平方ミリメートル")))
            add(unit("area.cm2", UnitCategory.AREA, "cm²", "1e-4", "cm2", "cm^2", words = setOf("square centimeter", "square centimeters", "平方センチメートル")))
            add(unit("area.m2", UnitCategory.AREA, "m²", "1", "m2", "m^2", words = setOf("square meter", "square meters", "square metre", "square metres", "平方メートル")))
            add(unit("area.km2", UnitCategory.AREA, "km²", "1e6", "km2", "km^2", words = setOf("square kilometer", "square kilometers", "平方キロメートル")))
            add(unit("area.in2", UnitCategory.AREA, "in²", "0.00064516", "in2", "in^2", words = setOf("square inch", "square inches")))
            add(unit("area.ft2", UnitCategory.AREA, "ft²", "0.09290304", "ft2", "ft^2", words = setOf("square foot", "square feet")))
            add(unit("area.yd2", UnitCategory.AREA, "yd²", "0.83612736", "yd2", "yd^2", words = setOf("square yard", "square yards")))
            add(unit("area.a", UnitCategory.AREA, "a", "100", words = setOf("are", "ares", "アール")))
            add(unit("area.ha", UnitCategory.AREA, "ha", "10000", words = setOf("hectare", "hectares", "ヘクタール")))
            add(unit("area.acre", UnitCategory.AREA, "acre", "4046.8564224", words = setOf("acres", "エーカー")))
            add(unit("area.tsubo", UnitCategory.AREA, "坪", rational("400", "121").toPlainString(), words = setOf("つぼ")))

            // Volume (base: litre)
            add(unit("volume.ul", UnitCategory.VOLUME, "µL", "1e-6", "uL", "μL", words = setOf("microliter", "microliters", "microlitre", "microlitres", "マイクロリットル")))
            add(unit("volume.ml", UnitCategory.VOLUME, "mL", "1e-3", words = setOf("milliliter", "milliliters", "millilitre", "millilitres", "ミリリットル")))
            add(unit("volume.l", UnitCategory.VOLUME, "L", "1", "l", words = setOf("liter", "liters", "litre", "litres", "リットル")))
            add(unit("volume.cm3", UnitCategory.VOLUME, "cm³", "1e-3", "cm3", "cm^3", "cc", words = setOf("cubic centimeter", "cubic centimeters", "立方センチメートル")))
            add(unit("volume.m3", UnitCategory.VOLUME, "m³", "1000", "m3", "m^3", words = setOf("cubic meter", "cubic meters", "cubic metre", "cubic metres", "立方メートル")))
            add(unit("volume.in3", UnitCategory.VOLUME, "in³", "0.016387064", "in3", "in^3", words = setOf("cubic inch", "cubic inches")))
            add(unit("volume.ft3", UnitCategory.VOLUME, "ft³", "28.316846592", "ft3", "ft^3", words = setOf("cubic foot", "cubic feet")))
            add(unit("volume.tsp_jp", UnitCategory.VOLUME, "tspJP", "0.005", "tspJP", words = setOf("tsp", "teaspoon", "teaspoons", "小さじ")))
            add(unit("volume.tsp_us", UnitCategory.VOLUME, "tspUS", "0.00492892159375", "tspUS", words = setOf("tsp", "teaspoon", "teaspoons")))
            add(unit("volume.tsp_uk", UnitCategory.VOLUME, "tspUK", "0.005919388020833333333333333333333333", "tspUK", words = setOf("tsp", "teaspoon", "teaspoons")))
            add(unit("volume.tbsp_jp", UnitCategory.VOLUME, "tbspJP", "0.015", "tbspJP", words = setOf("tbsp", "tablespoon", "tablespoons", "大さじ")))
            add(unit("volume.tbsp_us", UnitCategory.VOLUME, "tbspUS", "0.01478676478125", "tbspUS", words = setOf("tbsp", "tablespoon", "tablespoons")))
            add(unit("volume.tbsp_uk", UnitCategory.VOLUME, "tbspUK", "0.0177581640625", "tbspUK", words = setOf("tbsp", "tablespoon", "tablespoons")))
            add(unit("volume.cup_jp", UnitCategory.VOLUME, "cupJP", "0.2", "cupJP", words = setOf("cup", "cups", "カップ")))
            add(unit("volume.cup_us", UnitCategory.VOLUME, "cupUS", "0.2365882365", "cupUS", words = setOf("cup", "cups")))
            add(unit("volume.cup_uk", UnitCategory.VOLUME, "cupUK", "0.284130625", "cupUK", words = setOf("cup", "cups")))
            add(unit("volume.floz_us", UnitCategory.VOLUME, "flOzUS", "0.0295735295625", "fl oz US", "flOzUS", words = setOf("fl oz", "fluid ounce", "fluid ounces")))
            add(unit("volume.floz_uk", UnitCategory.VOLUME, "flOzUK", "0.0284130625", "fl oz UK", "flOzUK", words = setOf("fl oz", "fluid ounce", "fluid ounces")))
            add(unit("volume.pt_us", UnitCategory.VOLUME, "ptUS", "0.473176473", "ptUS", words = setOf("pt", "pint", "pints")))
            add(unit("volume.pt_uk", UnitCategory.VOLUME, "ptUK", "0.56826125", "ptUK", words = setOf("pt", "pint", "pints")))
            add(unit("volume.qt_us", UnitCategory.VOLUME, "qtUS", "0.946352946", "qtUS", words = setOf("qt", "quart", "quarts")))
            add(unit("volume.qt_uk", UnitCategory.VOLUME, "qtUK", "1.1365225", "qtUK", words = setOf("qt", "quart", "quarts")))
            add(unit("volume.gal_us", UnitCategory.VOLUME, "galUS", "3.785411784", "galUS", words = setOf("gal", "gallon", "gallons")))
            add(unit("volume.gal_uk", UnitCategory.VOLUME, "galUK", "4.54609", "galUK", words = setOf("gal", "gallon", "gallons")))
            add(unit("volume.go", UnitCategory.VOLUME, "合", "0.18039"))
            add(unit("volume.sho", UnitCategory.VOLUME, "升", "1.8039"))

            // Mass (base: kilogram)
            add(unit("mass.mg", UnitCategory.MASS, "mg", "1e-6", words = setOf("milligram", "milligrams", "ミリグラム")))
            add(unit("mass.g", UnitCategory.MASS, "g", "1e-3", words = setOf("gram", "grams", "グラム")))
            add(unit("mass.kg", UnitCategory.MASS, "kg", "1", words = setOf("kilogram", "kilograms", "キログラム", "キロ")))
            add(unit("mass.t", UnitCategory.MASS, "t", "1000", words = setOf("ton", "tons", "tonne", "tonnes", "metric ton", "metric tons", "トン")))
            add(unit("mass.oz", UnitCategory.MASS, "oz", "0.028349523125", words = setOf("ounce", "ounces", "オンス")))
            add(unit("mass.lb", UnitCategory.MASS, "lb", "0.45359237", "lbs", words = setOf("pound", "pounds", "ポンド")))
            add(unit("mass.stone", UnitCategory.MASS, "stone", "6.35029318", "st", words = setOf("stones", "ストーン")))
            add(unit("mass.short_ton", UnitCategory.MASS, "shortTon", "907.18474", "short ton", words = setOf("ton", "tons", "short ton", "short tons", "米トン")))
            add(unit("mass.long_ton", UnitCategory.MASS, "longTon", "1016.0469088", "long ton", words = setOf("ton", "tons", "long ton", "long tons", "英トン")))
            add(unit("mass.momme", UnitCategory.MASS, "匁", "0.00375", words = setOf("もんめ")))
            add(unit("mass.kan", UnitCategory.MASS, "貫", "3.75", words = setOf("かん")))

            // Temperature (base: kelvin)
            add(unit("temperature.c", UnitCategory.TEMPERATURE, "°C", "1", "℃", "C", "c", words = setOf("celsius", "degree celsius", "degrees celsius", "摂氏", "摂氏度"), offset = decimal("273.15")))
            add(unit("temperature.f", UnitCategory.TEMPERATURE, "°F", rational("5", "9").toPlainString(), "℉", "F", "f", words = setOf("fahrenheit", "degree fahrenheit", "degrees fahrenheit", "華氏", "華氏度"), offset = rational("45967", "180")))
            add(unit("temperature.k", UnitCategory.TEMPERATURE, "K", "1", words = setOf("kelvin", "kelvins", "ケルビン")))

            // Speed (base: metre per second)
            add(unit("speed.ms", UnitCategory.SPEED, "m/s", "1", words = setOf("meter per second", "meters per second", "metre per second", "秒速")))
            add(unit("speed.kmh", UnitCategory.SPEED, "km/h", rational("5", "18").toPlainString(), "kph", words = setOf("kilometer per hour", "kilometers per hour", "時速")))
            add(unit("speed.fts", UnitCategory.SPEED, "ft/s", "0.3048", "fps", words = setOf("foot per second", "feet per second")))
            add(unit("speed.mph", UnitCategory.SPEED, "mph", "0.44704", words = setOf("mile per hour", "miles per hour")))
            add(unit("speed.kn", UnitCategory.SPEED, "kn", rational("463", "900").toPlainString(), "kt", "kts", words = setOf("knot", "knots", "ノット")))

            // Pressure (base: pascal)
            add(unit("pressure.pa", UnitCategory.PRESSURE, "Pa", "1", words = setOf("pascal", "pascals", "パスカル")))
            add(unit("pressure.hpa", UnitCategory.PRESSURE, "hPa", "100", words = setOf("hectopascal", "hectopascals", "ヘクトパスカル")))
            add(unit("pressure.kpa", UnitCategory.PRESSURE, "kPa", "1000", words = setOf("kilopascal", "kilopascals", "キロパスカル")))
            add(unit("pressure.mpa", UnitCategory.PRESSURE, "MPa", "1e6", words = setOf("megapascal", "megapascals", "メガパスカル")))
            add(unit("pressure.bar", UnitCategory.PRESSURE, "bar", "100000", words = setOf("bars", "バール")))
            add(unit("pressure.atm", UnitCategory.PRESSURE, "atm", "101325", words = setOf("atmosphere", "atmospheres", "気圧")))
            add(unit("pressure.psi", UnitCategory.PRESSURE, "psi", "6894.757293168", words = setOf("pound per square inch", "pounds per square inch")))
            add(unit("pressure.mmhg", UnitCategory.PRESSURE, "mmHg", "133.322387415", words = setOf("millimeter of mercury", "millimeters of mercury")))
            add(unit("pressure.torr", UnitCategory.PRESSURE, "Torr", rational("101325", "760").toPlainString(), "torr"))
            add(unit("pressure.inhg", UnitCategory.PRESSURE, "inHg", "3386.389", words = setOf("inch of mercury", "inches of mercury")))

            // Energy (base: joule)
            add(unit("energy.j", UnitCategory.ENERGY, "J", "1", words = setOf("joule", "joules", "ジュール")))
            add(unit("energy.kj", UnitCategory.ENERGY, "kJ", "1000", words = setOf("kilojoule", "kilojoules", "キロジュール")))
            add(unit("energy.mj", UnitCategory.ENERGY, "MJ", "1e6", words = setOf("megajoule", "megajoules", "メガジュール")))
            add(unit("energy.wh", UnitCategory.ENERGY, "Wh", "3600", words = setOf("watt hour", "watt hours", "ワット時")))
            add(unit("energy.kwh", UnitCategory.ENERGY, "kWh", "3600000", words = setOf("kilowatt hour", "kilowatt hours", "キロワット時")))
            add(unit("energy.cal", UnitCategory.ENERGY, "cal", "4.184", words = setOf("calorie", "calories", "カロリー")))
            add(unit("energy.kcal", UnitCategory.ENERGY, "kcal", "4184", words = setOf("kilocalorie", "kilocalories", "キロカロリー")))
            add(unit("energy.btu", UnitCategory.ENERGY, "BTU", "1055.05585262", "Btu", words = setOf("british thermal unit", "british thermal units")))
            add(unit("energy.ev", UnitCategory.ENERGY, "eV", "1.602176634e-19", words = setOf("electronvolt", "electronvolts", "電子ボルト")))

            // Power (base: watt)
            add(unit("power.mw", UnitCategory.POWER, "mW", "0.001", words = setOf("milliwatt", "milliwatts", "ミリワット")))
            add(unit("power.w", UnitCategory.POWER, "W", "1", words = setOf("watt", "watts", "ワット")))
            add(unit("power.kw", UnitCategory.POWER, "kW", "1000", words = setOf("kilowatt", "kilowatts", "キロワット")))
            add(unit("power.mw_large", UnitCategory.POWER, "MW", "1e6", words = setOf("megawatt", "megawatts", "メガワット")))
            add(unit("power.hp", UnitCategory.POWER, "hp", "745.69987158227022", words = setOf("horsepower", "mechanical horsepower", "馬力")))
            add(unit("power.ps", UnitCategory.POWER, "PS", "735.49875", words = setOf("metric horsepower", "pferdestarke", "仏馬力", "馬力")))

            // Time (base: second)
            add(unit("time.ms", UnitCategory.TIME, "ms", "0.001", words = setOf("millisecond", "milliseconds", "ミリ秒")))
            add(unit("time.s", UnitCategory.TIME, "s", "1", words = setOf("sec", "second", "seconds", "秒")))
            add(unit("time.min", UnitCategory.TIME, "min", "60", "m", words = setOf("minute", "minutes", "分")))
            add(unit("time.h", UnitCategory.TIME, "h", "3600", "hr", words = setOf("hour", "hours", "時間")))
            add(unit("time.day", UnitCategory.TIME, "day", "86400", "d", words = setOf("days", "日")))
            add(unit("time.week", UnitCategory.TIME, "week", "604800", "wk", words = setOf("weeks", "週間", "週")))

            // Data (base: byte; SI and IEC case is significant)
            add(unit("data.bit", UnitCategory.DATA, "bit", "0.125", words = setOf("bits", "ビット")))
            add(unit("data.b", UnitCategory.DATA, "B", "1", words = setOf("byte", "bytes", "バイト")))
            add(unit("data.kb", UnitCategory.DATA, "kB", "1000", words = setOf("kilobyte", "kilobytes", "キロバイト")))
            add(unit("data.mb", UnitCategory.DATA, "MB", "1000000", words = setOf("megabyte", "megabytes", "メガバイト")))
            add(unit("data.gb", UnitCategory.DATA, "GB", "1000000000", words = setOf("gigabyte", "gigabytes", "ギガバイト")))
            add(unit("data.tb", UnitCategory.DATA, "TB", "1000000000000", words = setOf("terabyte", "terabytes", "テラバイト")))
            add(unit("data.pb", UnitCategory.DATA, "PB", "1000000000000000", words = setOf("petabyte", "petabytes", "ペタバイト")))
            add(unit("data.kib", UnitCategory.DATA, "KiB", "1024", words = setOf("kibibyte", "kibibytes")))
            add(unit("data.mib", UnitCategory.DATA, "MiB", "1048576", words = setOf("mebibyte", "mebibytes")))
            add(unit("data.gib", UnitCategory.DATA, "GiB", "1073741824", words = setOf("gibibyte", "gibibytes")))
            add(unit("data.tib", UnitCategory.DATA, "TiB", "1099511627776", words = setOf("tebibyte", "tebibytes")))
            add(unit("data.pib", UnitCategory.DATA, "PiB", "1125899906842624", words = setOf("pebibyte", "pebibytes")))
        }
    }
}
