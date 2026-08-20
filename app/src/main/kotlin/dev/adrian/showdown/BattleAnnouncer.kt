package dev.adrian.showdown

enum class BattleAnnouncerCue(val assetName: String) {
    BATTLE_START("tb_014"),
    SWITCH("tb_142"),
    MOVE("tb_150m"),
    HIT("tb_100"),
    MULTI_HIT("tb_120"),
    MISS("tb_040"),
    FAIL("tb_550"),
    CANNOT_MOVE("tb_390m"),
    FAINT("h1b_107"),
    INTIMIDATE("cb_130"),
    POISON("kb_020"),
    BURN("kb_040"),
    HAIL("cb_230"),
    SANDSTORM("cb_240"),
    HEAL("kb_010"),
    ITEM("tb_675"),
    BATTLE_END("eb_010")
}

object BattleAnnouncerCueResolver {
    fun cuesForProtocol(lines: List<String>): List<BattleAnnouncerCue> = lines.mapNotNull(::cueForProtocolLine)

    fun cueForProtocolLine(line: String): BattleAnnouncerCue? {
        val fields = line.split('|')
        return when (fields.getOrNull(1)) {
            "init" -> BattleAnnouncerCue.BATTLE_START.takeIf { fields.getOrNull(2) == "battle" }
            "switch", "drag", "replace" -> BattleAnnouncerCue.SWITCH
            "move" -> BattleAnnouncerCue.MOVE
            "-damage" -> BattleAnnouncerCue.HIT.takeIf { isDirectMoveDamage(fields) }
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

    private fun isDirectMoveDamage(fields: List<String>): Boolean = fields.size == 4
}

object BattleAnnouncerAssets {
    fun assetPath(cue: BattleAnnouncerCue): String = "announcer/${cue.assetName}.wav"
}
