package dev.adrian.showdown

import java.util.Locale

object ShowdownTeamCoverage {
    data class Member(val types: List<String>, val ability: String)

    data class TypeResult(
        val attackingType: String,
        val weaknesses: Int,
        val resistances: Int,
        val immunities: Int
    )

    data class Analysis(
        val typeResults: List<TypeResult>,
        val environmentalNote: String?
    )

    private val typeNames = ShowdownMoveDex.typeChartNames().map { it.uppercase(Locale.ROOT) }

    fun generation(formatId: String): Int = Regex("gen\\s*([0-9]+)", RegexOption.IGNORE_CASE)
        .find(formatId)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.coerceIn(1, 9)
        ?: 9

    fun analyze(
        members: List<Member>,
        typeChart: Map<String, Map<String, Int>>,
        generation: Int
    ): Analysis {
        val availableTypes = when {
            generation <= 1 -> typeNames.filterNot { it in setOf("DARK", "STEEL", "FAIRY", "STELLAR") }
            generation <= 5 -> typeNames.filterNot { it in setOf("FAIRY", "STELLAR") }
            generation <= 8 -> typeNames.filterNot { it == "STELLAR" }
            else -> typeNames
        }
        val fieldEffects = fieldEffects(members, generation)
        val fieldEffect = fieldEffects.singleOrNull()
        val typeResults = availableTypes.map { attackingType ->
            var weaknesses = 0
            var resistances = 0
            var immunities = 0
            members.forEach { member ->
                val multiplier = effectiveness(attackingType, member, typeChart, generation, fieldEffect)
                when {
                    multiplier == 0.0 -> immunities++
                    multiplier > 1.0 -> weaknesses++
                    multiplier < 1.0 -> resistances++
                }
            }
            TypeResult(attackingType, weaknesses, resistances, immunities)
        }
        val environmentalNote = when (fieldEffect) {
            FieldEffect.DELTA_STREAM -> "Assumes Delta Stream is active."
            FieldEffect.PRIMORDIAL_SEA -> "Assumes Primordial Sea is active."
            FieldEffect.DESOLATE_LAND -> "Assumes Desolate Land is active."
            null -> if (fieldEffects.size > 1) "Conflicting field-setting abilities; field effects are omitted." else null
        }
        return Analysis(typeResults, environmentalNote)
    }

    private fun effectiveness(
        attackingType: String,
        member: Member,
        typeChart: Map<String, Map<String, Int>>,
        generation: Int,
        fieldEffect: FieldEffect?
    ): Double {
        if (fieldEffect == FieldEffect.PRIMORDIAL_SEA && attackingType == "FIRE") return 0.0
        if (fieldEffect == FieldEffect.DESOLATE_LAND && attackingType == "WATER") return 0.0
        var multiplier = 1.0
        member.types.map { it.uppercase(Locale.ROOT) }.forEach { defendingType ->
            multiplier *= when {
                generation == 1 && attackingType == "GHOST" && defendingType == "PSYCHIC" -> 0.0
                generation == 1 && defendingType == "BUG" && attackingType == "POISON" -> 2.0
                generation == 1 && defendingType == "POISON" && attackingType == "BUG" -> 2.0
                generation == 1 && defendingType == "FIRE" && attackingType == "ICE" -> 1.0
                generation in 2..5 && defendingType == "STEEL" && attackingType in setOf("DARK", "GHOST") -> 0.5
                generation == 2 && defendingType == "FIRE" && attackingType == "ICE" -> 0.5
                generation >= 6 && fieldEffect == FieldEffect.DELTA_STREAM && defendingType == "FLYING" && attackingType in setOf("ELECTRIC", "ICE", "ROCK") -> 1.0
                else -> when (typeChart[defendingType.lowercase(Locale.ROOT)]?.get(attackingType)) {
                    1 -> 2.0
                    2 -> 0.5
                    3 -> 0.0
                    else -> 1.0
                }
            }
        }

        val ability = member.ability.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)
        multiplier = when {
            attackingType == "GROUND" && ability == "levitate" && generation >= 3 -> 0.0
            attackingType == "GROUND" && ability == "eartheater" && generation >= 9 -> 0.0
            attackingType == "FIRE" && ability == "flashfire" && generation >= 3 -> 0.0
            attackingType == "FIRE" && ability == "wellbakedbody" && generation >= 9 -> 0.0
            attackingType == "WATER" && ability == "dryskin" && generation >= 4 -> 0.0
            attackingType == "ELECTRIC" && ability == "voltabsorb" && generation >= 3 -> 0.0
            attackingType == "ELECTRIC" && ability == "motordrive" && generation >= 4 -> 0.0
            attackingType == "ELECTRIC" && ability == "lightningrod" && generation >= 5 -> 0.0
            attackingType == "WATER" && ability == "waterabsorb" && generation >= 3 -> 0.0
            attackingType == "WATER" && ability == "stormdrain" && generation >= 5 -> 0.0
            attackingType == "GRASS" && ability == "sapsipper" && generation >= 5 -> 0.0
            attackingType == "FIRE" && ability == "thickfat" && generation >= 3 -> multiplier * 0.5
            attackingType == "ICE" && ability == "thickfat" && generation >= 3 -> multiplier * 0.5
            attackingType == "FIRE" && ability == "heatproof" && generation >= 4 -> multiplier * 0.5
            attackingType == "FIRE" && ability == "waterbubble" && generation >= 7 -> multiplier * 0.5
            attackingType == "FIRE" && ability == "dryskin" && generation >= 4 -> multiplier * 1.25
            attackingType == "FIRE" && ability == "fluffy" && generation >= 7 -> multiplier * 2.0
            attackingType == "GHOST" && ability == "purifyingsalt" && generation >= 9 -> multiplier * 0.5
            else -> multiplier
        }
        if (ability == "wonderguard" && generation >= 3 && multiplier <= 1.0) return 0.0
        val hasSuperEffectiveDamageReduction = ability == "filter" && generation >= 4 ||
            ability == "solidrock" && generation >= 4 ||
            ability == "prismarmor" && generation >= 7
        if (multiplier > 1.0 && hasSuperEffectiveDamageReduction) multiplier *= 0.75
        return multiplier
    }

    private fun fieldEffects(members: List<Member>, generation: Int): Set<FieldEffect> {
        if (generation < 6) return emptySet()
        return members.mapNotNull { member ->
            when (member.ability.lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)) {
                "deltastream" -> FieldEffect.DELTA_STREAM
                "primordialsea" -> FieldEffect.PRIMORDIAL_SEA
                "desolateland" -> FieldEffect.DESOLATE_LAND
                else -> null
            }
        }.toSet()
    }

    private enum class FieldEffect {
        DELTA_STREAM,
        PRIMORDIAL_SEA,
        DESOLATE_LAND
    }
}
