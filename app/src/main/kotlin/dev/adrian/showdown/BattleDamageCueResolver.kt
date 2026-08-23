package dev.adrian.showdown

data class BattleHealthUpdate(
    val target: String,
    val health: Float
)

object BattleDamageCueResolver {
    fun directDamageTargets(
        fields: List<String>,
        pendingMoveTargets: List<String>,
        previousHealth: Map<String, Float>
    ): List<String> {
        val event = fields.getOrNull(1)
        if (event != "-damage" && event != "-sethp") return emptyList()
        if (hasNonMoveSource(fields)) return emptyList()
        val pendingTargets = pendingMoveTargets.map(::targetKey)
        return healthUpdates(fields)
            .filter { update ->
                val previous = previousHealth[targetKey(update.target)]
                val decreased = previous == null && event == "-damage" ||
                    previous != null && update.health < previous
                if (!decreased) return@filter false
                pendingTargets.any { pendingTargetMatches(it, update.target) }
            }
            .map(BattleHealthUpdate::target)
            .distinctBy(::targetKey)
    }

    fun healthUpdates(fields: List<String>): List<BattleHealthUpdate> = when (fields.getOrNull(1)) {
        "switch", "drag", "replace" -> fields.getOrNull(2)?.let { target ->
            fields.getOrNull(4)?.let { healthValue(it)?.let { value -> listOf(BattleHealthUpdate(target, value)) } }
        }.orEmpty()
        "-damage", "-heal" -> fields.getOrNull(2)?.let { target ->
            fields.getOrNull(3)?.let { healthValue(it)?.let { value -> listOf(BattleHealthUpdate(target, value)) } }
        }.orEmpty()
        "-sethp" -> buildList {
            var index = 2
            while (index + 1 < fields.size) {
                val target = fields[index].trim()
                if (!target.contains(':') || target.startsWith('[')) break
                val health = healthValue(fields[index + 1]) ?: break
                add(BattleHealthUpdate(target, health))
                index += 2
            }
        }
        else -> emptyList()
    }

    fun targetKey(value: String): String = value.substringBefore(':').trim().lowercase()

    fun hasMoveSource(fields: List<String>): Boolean = protocolAnnotations(fields)
        .any { it.startsWith("[from] move:", true) }

    fun hasNonMoveSource(fields: List<String>): Boolean = protocolAnnotations(fields)
        .any { it.startsWith("[from]", true) && !it.startsWith("[from] move:", true) }

    fun isDirectMoveDamage(fields: List<String>, pendingMoveTargets: List<String>): Boolean =
        directDamageTargets(fields, pendingMoveTargets, emptyMap()).isNotEmpty()

    private fun pendingTargetMatches(pendingTarget: String, actualTarget: String): Boolean {
        val pendingKey = targetKey(pendingTarget)
        if (pendingKey.isBlank() || pendingKey == targetKey(actualTarget)) return true
        return pendingKey.contains("all") ||
            pendingKey.contains("foe") ||
            pendingKey.contains("ally") ||
            pendingKey.contains("adjacent") ||
            pendingKey == "random" ||
            pendingKey == "self"
    }

    private fun protocolAnnotations(fields: List<String>): List<String> = fields.drop(4)
        .map(String::trim)
        .filter { it.startsWith('[') }

    private fun healthValue(value: String): Float? {
        val normalized = value.trim().substringBefore(' ')
        if (normalized.equals("fnt", true)) return 0f
        if (normalized == "0") return 0f
        val slash = normalized.indexOf('/')
        return if (slash > 0) {
            normalized.substring(0, slash).toFloatOrNull()
        } else {
            normalized.removeSuffix("%").toFloatOrNull()
        }
    }
}
