package dev.adrian.showdown

enum class BattleAnnouncerCue(
    val assetName: String,
    val playbackDurationMillis: Long
) {
    BATTLE_START("tb_014", 1_776L),
    SWITCH("tb_142", 1_314L),
    MOVE("tb_150m", 1_321L),
    HIT("tb_100", 851L),
    MULTI_HIT("tb_120", 1_424L),
    MISS("tb_040", 1_355L),
    FAIL("tb_550", 1_577L),
    CANNOT_MOVE("tb_390m", 1_271L),
    FAINT("h1b_107", 1_332L),
    INTIMIDATE("cb_130", 1_759L),
    POISON("kb_020", 1_480L),
    BURN("kb_040", 1_640L),
    HAIL("cb_230", 2_399L),
    SANDSTORM("cb_240", 2_501L),
    HEAL("kb_010", 3_139L),
    ITEM("tb_675", 1_876L),
    BATTLE_END("eb_010", 1_715L)
}

object BattleAnnouncerCueResolver {
    fun cuesForProtocol(lines: List<String>): List<BattleAnnouncerCue> {
        val cues = mutableListOf<BattleAnnouncerCue>()
        var moveDamageCueStart = -1
        lines.forEach { line ->
            when (val cue = cueForProtocolLine(line)) {
                BattleAnnouncerCue.MOVE -> {
                    cues += cue
                    moveDamageCueStart = cues.size
                }
                BattleAnnouncerCue.HIT -> cues += cue
                BattleAnnouncerCue.MULTI_HIT -> {
                    if (moveDamageCueStart >= 0) {
                        cues.subList(moveDamageCueStart, cues.size).removeAll { it == BattleAnnouncerCue.HIT }
                    }
                    cues += cue
                }
                null -> Unit
                else -> cues += cue
            }
        }
        return cues
    }

    fun cueForProtocolLine(line: String): BattleAnnouncerCue? {
        val fields = line.split('|')
        return when (fields.getOrNull(1)) {
            "init" -> BattleAnnouncerCue.BATTLE_START.takeIf { fields.getOrNull(2) == "battle" }
            "switch", "drag", "replace" -> BattleAnnouncerCue.SWITCH
            "move" -> BattleAnnouncerCue.MOVE
            "-damage" -> BattleAnnouncerCue.HIT.takeIf { isDirectMoveDamage(fields, allowUnannotated = true) }
            "-sethp" -> BattleAnnouncerCue.HIT.takeIf { isDirectMoveDamage(fields, allowUnannotated = false) }
            "-hitcount" -> BattleAnnouncerCue.MULTI_HIT
            "-miss" -> BattleAnnouncerCue.MISS
            "-fail", "-block", "-notarget" -> BattleAnnouncerCue.FAIL
            "cant" -> BattleAnnouncerCue.CANNOT_MOVE
            "faint" -> BattleAnnouncerCue.FAINT
            "win", "tie", "draw", "prematureend" -> BattleAnnouncerCue.BATTLE_END
            "-ability" -> BattleAnnouncerCue.INTIMIDATE.takeIf { fields.getOrNull(3).equals("Intimidate", true) }
            "-status" -> when (fields.getOrNull(3)?.lowercase()) {
                "psn", "tox" -> BattleAnnouncerCue.POISON
                "brn" -> BattleAnnouncerCue.BURN
                else -> null
            }
            "-weather" -> when (fields.getOrNull(2)?.substringBefore(':')?.lowercase()) {
                "hail" -> BattleAnnouncerCue.HAIL
                "sandstorm" -> BattleAnnouncerCue.SANDSTORM
                else -> null
            }
            "-heal" -> BattleAnnouncerCue.HEAL
            "-item", "-eat" -> BattleAnnouncerCue.ITEM
            else -> null
        }
    }

    private fun isDirectMoveDamage(fields: List<String>, allowUnannotated: Boolean): Boolean {
        val source = fields.drop(4)
            .firstOrNull { it.trim().startsWith("[from]", true) }
            ?.trim()
        return source?.startsWith("[from] move:", true) ?: allowUnannotated
    }
}

object BattleAnnouncerAssets {
    fun assetPath(cue: BattleAnnouncerCue): String = "announcer/${cue.assetName}.wav"
}
