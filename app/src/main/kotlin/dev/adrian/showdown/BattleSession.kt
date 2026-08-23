package dev.adrian.showdown

import org.json.JSONObject
import kotlin.random.Random

private fun inferredRandomTeamFormat(id: String): Boolean {
    val normalized = id.trim().lowercase()
    return normalized.contains("battlefactory") ||
        (normalized.contains("random") && normalized.contains("battle"))
}

class BattleSession {
    enum class Panel {
        MOVES,
        TEAM,
        ACTIVITY,
        MENU
    }

    enum class DecisionKind {
        MOVE,
        SWITCH,
        TEAM_PREVIEW,
        WAIT
    }

    enum class BattlePhase {
        LOBBY,
        TEAM_PREVIEW,
        BATTLE,
        UPKEEP,
        FINISHED
    }

    data class MatchFormat(
        val id: String,
        val label: String,
        val menuLabel: String = label,
        val usesRandomTeams: Boolean = inferredRandomTeamFormat(id),
        val canSearch: Boolean = true,
        val canChallenge: Boolean = true
    ) {
        companion object {
            val GEN6_RANDOM = MatchFormat("gen6randombattle", "[Gen 6] Random Battle", "Gen 6 Random")
            val GEN7_RANDOM = MatchFormat("gen7randombattle", "[Gen 7] Random Battle", "Gen 7 Random")
            val GEN8_RANDOM = MatchFormat("gen8randombattle", "[Gen 8] Random Battle", "Gen 8 Random")
            val GEN9_RANDOM = MatchFormat("gen9randombattle", "[Gen 9] Random Battle", "Gen 9 Random")
            val GEN9_CHAMPIONS_RANDOM_DOUBLES = MatchFormat(
                "gen9championsrandomdoublesbattle",
                "[Gen 9 Champions] Random Doubles Battle",
                "Gen 9 Champions Doubles",
                usesRandomTeams = true
            )
            val defaults = listOf(GEN6_RANDOM, GEN7_RANDOM, GEN8_RANDOM, GEN9_RANDOM, GEN9_CHAMPIONS_RANDOM_DOUBLES)

            fun usesRandomTeams(format: MatchFormat): Boolean = format.usesRandomTeams || usesRandomTeamsFor(format.id)

            fun usesRandomTeamsFor(id: String): Boolean = inferredRandomTeamFormat(id)
        }
    }

    enum class SpriteStyle(
        val displayName: String,
        val animatedCollection: String
    ) {
        MODERN_3D("HD-first animated", "xyani")
    }

    enum class FeedbackType {
        ENTRY,
        POKEMON_CRY,
        MOVE,
        HIT
    }

    enum class HitImpact {
        NORMAL,
        RESISTED,
        SUPER_EFFECTIVE,
        CRITICAL,
        SUPER_EFFECTIVE_CRITICAL
    }

    enum class ClientAction {
        FIND_BATTLE,
        CONFIGURE_SERVER,
        CONFIGURE_ACCOUNT,
        CONFIGURE_TEAM,
        OPEN_ROOMS,
        CHOOSE_FORMAT,
        OPEN_CHAT,
        FORFEIT,
        LEAVE_BATTLE,
        CHALLENGE_PLAYER,
        EXPORT_REPLAY,
        SAVE_REPLAY,
        OPEN_REPLAY_CONTROLS,
        SETTINGS_CHANGED,
        TOGGLE_BATTLE_TIMER,
        CANCEL_CHOICE
    }

    enum class BattleGimmick(val choiceSuffix: String, val label: String) {
        Z_POWER("zmove", "Z-Power"),
        MEGA_EVOLUTION("mega", "Mega Evolution"),
        MEGA_EVOLUTION_X("megax", "Mega X"),
        MEGA_EVOLUTION_Y("megay", "Mega Y"),
        ULTRA_BURST("ultra", "Ultra Burst"),
        DYNAMAX("max", "Dynamax"),
        TERASTALLIZATION("terastallize", "Terastallize")
    }

    fun interface Listener {
        fun onBattleStateChanged()
    }

    fun interface FeedbackListener {
        fun onBattleFeedback(feedback: BattleFeedback)
    }

    fun interface DecisionListener {
        fun onDecision(command: String)
    }

    fun interface ClientActionListener {
        fun onClientAction(action: ClientAction)
    }

    fun interface ChatListener {
        fun onChat(message: String)
    }

    fun interface ProtocolListener {
        fun onProtocol(lines: List<String>)
    }

    fun interface BattleEventListener {
        fun onBattleEvents(events: List<String>)
    }

    data class MoveOption(
        val name: String,
        val type: String,
        val pp: Int,
        val maxPp: Int = pp,
        val category: String = "Status",
        val power: String = "—",
        val accuracy: String = "—",
        val disabled: Boolean = false,
        val target: String = ""
    )

    data class MoveVariant(
        val name: String,
        val type: String,
        val category: String,
        val power: String,
        val accuracy: String,
        val disabled: Boolean,
        val target: String
    )

    data class MoveInfo(
        val power: String,
        val accuracy: String,
        val category: String = "Status",
        val fixedGimmickPower: Boolean = false
    )

    data class TargetOption(
        val label: String,
        val choice: String
    )

    data class BattleFeedback(
        val type: FeedbackType,
        val actor: String = "",
        val target: String = "",
        val move: String = "",
        val impact: HitImpact = HitImpact.NORMAL,
        val delayMillis: Long = 0L,
        val message: String = ""
    )

    data class PokemonDetails(
        val name: String,
        val types: List<String>,
        val level: String,
        val gender: String,
        val hp: String,
        val condition: String,
        val ability: String,
        val item: String,
        val moves: List<String>,
        val stats: String,
        val pokeball: String = "pokeball",
        val species: String = name,
        val shiny: Boolean = false
    )

    data class ActiveCombatant(
        val slot: String,
        val name: String,
        val types: List<String>,
        val level: String,
        val gender: String,
        val hp: String,
        val condition: String,
        val entryAtNanos: Long,
        val dynamaxed: Boolean = false,
        val gMaxed: Boolean = false,
        val volatileEffects: List<String> = emptyList(),
        val turnEffects: List<String> = emptyList(),
        val moveEffects: List<String> = emptyList(),
        val species: String = name,
        val shiny: Boolean = false
    )

    private class ActiveCombatantMap(
        private val onChanged: () -> Unit
    ) : LinkedHashMap<String, ActiveCombatant>() {
        override fun put(key: String, value: ActiveCombatant): ActiveCombatant? {
            val previous = super.put(key, value)
            if (previous != value) onChanged()
            return previous
        }

        override fun remove(key: String): ActiveCombatant? {
            val previous = super.remove(key)
            if (previous != null) onChanged()
            return previous
        }

        override fun clear() {
            if (isEmpty()) return
            super.clear()
            onChanged()
        }
    }

    data class BattleInfo(
        val weather: String,
        val terrain: String,
        val playerSideConditions: List<String>,
        val opponentSideConditions: List<String>,
        val playerBoosts: Map<String, Int>,
        val opponentBoosts: Map<String, Int>,
        val fieldEffects: List<String> = emptyList()
    )

    data class BattleClock(
        val turnSeconds: Int,
        val totalSeconds: Int,
        val graceSeconds: Int
    )

    private data class PendingHit(
        val actor: String,
        val target: String,
        var superEffective: Boolean = false,
        var resisted: Boolean = false,
        var critical: Boolean = false
    )

    private data class ParsedMoveMetric(val value: String, val fromRequest: Boolean)

    private data class BattleRoomPresenceLog(
        val joins: MutableList<BattleRoomUser> = mutableListOf(),
        val leaves: MutableList<BattleRoomUser> = mutableListOf(),
        var fallbackEntry: String? = null
    )

    private data class BattleRoomUser(val group: String, val name: String) {
        val formatted: String
            get() = group + name

        val id: String
            get() = name.filter(Char::isLetterOrDigit).lowercase()
    }

    private data class MoveResolutionSources(
        val typeFromRequest: Boolean = false,
        val powerFromRequest: Boolean = false,
        val accuracyFromRequest: Boolean = false,
        val categoryFromRequest: Boolean = false
    )

    private data class ParsedMoveVariant(
        val variant: MoveVariant,
        val sources: MoveResolutionSources
    )

    private enum class ActivityOrigin {
        SYSTEM,
        PROTOCOL,
        NATIVE,
        CHAT
    }

    private enum class MoveVariantKind {
        Z_POWER,
        DYNAMAX
    }

    private val listeners = mutableListOf<Listener>()
    private val feedbackListeners = mutableListOf<FeedbackListener>()
    private val decisionListeners = mutableListOf<DecisionListener>()
    private val clientActionListeners = mutableListOf<ClientActionListener>()
    private val chatListeners = mutableListOf<ChatListener>()
    private val protocolListeners = mutableListOf<ProtocolListener>()
    private val battleEventListeners = mutableListOf<BattleEventListener>()
    private var protocolEventCollector: MutableList<String>? = null
    private val protocolHistory = mutableListOf<String>()
    private val showdownBattleLogEntries = mutableListOf<String>()
    private val showdownBattleMarkupEntries = mutableMapOf<String, List<String>>()
    private var battleRoomPresenceLog: BattleRoomPresenceLog? = null
    private var battleRoomRenameFallback: String? = null
    private var battleLogGeneration = 0L
    private var nativeBattleLogGeneration = -1L
    private var nativeBattleLogPending = false
    private val battleFeedEntriesCache = mutableMapOf<Int, List<String>>()
    private var hasBattleProtocolTranscript = false
    private var moveTypeResolver: ((String) -> String?)? = null
    private var moveInfoResolver: ((String) -> MoveInfo?)? = null
    private var pokemonTypeResolver: ((String) -> List<String>?)? = null
    private var moveNameResolver: ((String) -> String)? = null
    private var itemNameResolver: ((String) -> String)? = null
    private var abilityNameResolver: ((String) -> String)? = null
    private var abilitySlotResolver: ((String, String) -> String)? = null
    private val availableMatchFormats = MatchFormat.defaults.toMutableList()
    private val battleLog = mutableListOf("Battle started.", "Incineroar entered the field.", "Tapu Koko's Electric Surge activated!")
    private val markupEntries = mutableMapOf<String, String>()
    private val chatMessages = mutableListOf("[Battle] Welcome to Showdown!", "[System] Controller and touch input are ready.")
    private val activityMessages = mutableListOf<String>().apply {
        addAll(battleLog)
        addAll(chatMessages)
    }
    private val activityOrigins = MutableList(activityMessages.size) { ActivityOrigin.SYSTEM }
    private val moves = mutableListOf(
        MoveOption("Fake Out", "NORMAL", 10, 10, "Physical", "40", "100"),
        MoveOption("Flare Blitz", "FIRE", 15, 15, "Physical", "120", "100"),
        MoveOption("Darkest Lariat", "DARK", 10, 10, "Physical", "85", "100"),
        MoveOption("Parting Shot", "DARK", 20, 20, "Status", "—", "—")
    )
    private val moveResolutionSources = MutableList(moves.size) { MoveResolutionSources() }
    private val zMoveVariants = mutableListOf<MoveVariant?>()
    private val zMoveResolutionSources = mutableListOf<MoveResolutionSources?>()
    private val maxMoveVariants = mutableListOf<MoveVariant?>()
    private val maxMoveResolutionSources = mutableListOf<MoveResolutionSources?>()
    private val team = mutableListOf("Incineroar", "Naganadel", "Mimikyu", "Landorus", "Rotom-Wash", "Ferrothorn")
    private val teamPreviewOrder = mutableListOf<Int>()
    private var teamPreviewRequiredSize = 0
    private var protocolTeamPreviewSize = 0
    private val availableGimmicks = mutableListOf(BattleGimmick.Z_POWER)
    private val teamDetails = mutableListOf(
        PokemonDetails("Incineroar", listOf("FIRE", "DARK"), "50", "♂", "100/100", "READY", "Intimidate", "Firium Z", moves.map { it.name }, "HP 100 · Atk 135 · Def 90 · Spe 60"),
        PokemonDetails("Naganadel", listOf("POISON", "DRAGON"), "50", "", "100/100", "READY", "Beast Boost", "Dragonium Z", listOf("Draco Meteor", "Sludge Wave", "Fire Blast", "Nasty Plot"), "HP 73 · SpA 127 · Spe 121"),
        PokemonDetails("Mimikyu", listOf("GHOST", "FAIRY"), "50", "", "100/100", "READY", "Disguise", "Life Orb", listOf("Play Rough", "Shadow Claw", "Shadow Sneak", "Swords Dance"), "HP 55 · Atk 90 · Spe 96"),
        PokemonDetails("Landorus", listOf("GROUND", "FLYING"), "50", "♂", "100/100", "READY", "Intimidate", "Choice Scarf", listOf("Earthquake", "U-turn", "Stone Edge", "Knock Off"), "HP 89 · Atk 145 · Spe 91"),
        PokemonDetails("Rotom-Wash", listOf("ELECTRIC", "WATER"), "50", "", "100/100", "READY", "Levitate", "Leftovers", listOf("Hydro Pump", "Volt Switch", "Will-O-Wisp", "Defog"), "HP 50 · SpA 105 · Spe 86"),
        PokemonDetails("Ferrothorn", listOf("GRASS", "STEEL"), "50", "", "100/100", "READY", "Iron Barbs", "Leftovers", listOf("Power Whip", "Gyro Ball", "Leech Seed", "Spikes"), "HP 74 · Def 131 · SpD 116")
    )
    private var playerDetails = teamDetails.first()
    private var opponentDetails = PokemonDetails(
        "Tapu Koko",
        listOf("ELECTRIC", "FAIRY"),
        "50",
        "",
        "100/100",
        "READY",
        "Electric Surge",
        "Unknown item",
        listOf("Thunderbolt", "Dazzling Gleam", "Volt Switch", "Roost"),
        "HP 70 · SpA 95 · Spe 130"
    )
    private val opponentTeamDetails = mutableListOf(opponentDetails)
    private var playerActiveCombatantsViewDirty = true
    private var opponentActiveCombatantsViewDirty = true
    private var playerActiveCombatantsView: List<ActiveCombatant> = emptyList()
    private var opponentActiveCombatantsView: List<ActiveCombatant> = emptyList()
    private val playerActiveCombatants = ActiveCombatantMap { playerActiveCombatantsViewDirty = true }
    private val opponentActiveCombatants = ActiveCombatantMap { opponentActiveCombatantsViewDirty = true }
    private val playerPartyIdentifiers = mutableListOf<String>()
    private val playerActivePartyIndices = mutableMapOf<String, Int>()
    private val opponentPartyIdentifiers = mutableMapOf<String, Int>()
    private val opponentActivePartyIndices = mutableMapOf<String, Int>()
    private val activeTeamNames = mutableSetOf<String>()
    private val activeSlotNames = mutableMapOf<String, String>()
    private val autoPassActiveSlots = mutableSetOf<Int>()
    private val revivingTeamIndices = mutableSetOf<Int>()
    private val baseTypesBySlot = mutableMapOf<String, List<String>>()
    private val typeChangeBySlot = mutableMapOf<String, List<String>>()
    private val typeAdditionsBySlot = mutableMapOf<String, MutableList<String>>()
    private val terastallizedSlots = mutableSetOf<String>()
    private val teraTypesBySlot = mutableMapOf<String, String>()
    private var battleVisualSeed = Random.nextInt(1, Int.MAX_VALUE)
    private var pendingHit: PendingHit? = null
    private var requestId: Int? = null
    private val activeRequests = mutableListOf<JSONObject>()
    private val activeChoices = mutableListOf<String>()
    private val usedGimmickFamilies = mutableSetOf<String>()
    private val forceSwitchSlots = mutableListOf<Boolean>()
    private val forceSwitchChoices = mutableListOf<String>()
    private val targetOptions = mutableListOf<TargetOption>()
    private var activeSlotIndex = 0
    private var requiredSwitches = 0
    private var selectedTargetIndex = -1
    private var restoredPlayerSlot: String? = null
    private val sideNames = mutableMapOf<String, String>()
    private var playerSlot = "p1"
    private var localUsername: String? = null
    private var liveBattleActive = false
    private var battleSearchActive = false
    private var replayMode = false
    private var spectatorMode = false
    private var battleParticipant = false
    private var openingEntrances = 0
    private var latestOpeningEntranceAtNanos = 0L
    private var weather = ""
    private var terrain = ""
    private val fieldEffects = mutableListOf<String>()
    private var battleClock: BattleClock? = null
    private var battleClockUpdatedAtNanos = 0L
    private var battleTimerEnabled = false
    private var choiceCanBeCancelled = false
    private var requestNoCancel = false
    private var requestTargetable = true
    private var activeGMaxAvailable = false
    private var availableTeraType = ""
    private val playerSideConditions = mutableListOf<String>()
    private val opponentSideConditions = mutableListOf<String>()
    private val playerBoosts = mutableMapOf<String, Int>()
    private val opponentBoosts = mutableMapOf<String, Int>()
    private val playerBoostsBySlot = mutableMapOf<String, MutableMap<String, Int>>()
    private val opponentBoostsBySlot = mutableMapOf<String, MutableMap<String, Int>>()
    private val pendingBatonPassBySide = mutableMapOf<String, String>()

    var panel = Panel.MOVES
        private set
    var focusedMove = 0
        private set
    var focusedTeam = 0
        private set
    var focusedMessage = 0
        private set
    var focusedMenuItem = 0
        private set
    var turn = 1
        private set
    var playerName = "ADRIAN"
        private set
    var opponentName = "GLADION"
        private set
    var playerPokemon = "Incineroar"
        private set
    var opponentPokemon = "Tapu Koko"
        private set
    var playerHp = "100/100"
        private set
    var opponentHp = "100/100"
        private set
    var playerLevel = "50"
        private set
    var opponentLevel = "50"
        private set
    var playerGender = "♂"
        private set
    var opponentGender = ""
        private set
    var playerCondition = "READY"
        private set
    var opponentCondition = "READY"
        private set
    var gameType = "singles"
        private set
    var format = "[Gen 7] OU"
        private set
    var status = "Choose a move"
        private set
    var latestBattleEvent = battleLog.last()
        private set
    var latestBattleEventAtNanos = System.nanoTime()
        private set
    var latestMoveEvent = ""
        private set
    var latestMoveEventAtNanos = 0L
        private set
    var latestFaintedPokemon = ""
        private set
    var latestFaintAtNanos = 0L
        private set
    var playerEntryAtNanos = 0L
        private set
    var opponentEntryAtNanos = 0L
        private set
    var decisionAvailable = true
        private set
    var decisionKind = DecisionKind.MOVE
        private set
    var matchFormat = MatchFormat.GEN9_RANDOM
        private set
    var soundEffectsEnabled = true
        private set
    var musicEnabled = true
        private set
    var hapticsEnabled = true
        private set
    var announcerEnabled = false
        private set
    var spriteStyle = SpriteStyle.MODERN_3D
        private set
    var selectedGimmick: BattleGimmick? = null
        private set
    var battleFinished = false
        private set
    private var terminalBattleResult = ""
    var battlePhase = BattlePhase.LOBBY
        private set
    var battleFeedVisible = true
        private set

    fun playerHealthFraction() = healthFraction(playerHp)

    fun opponentHealthFraction() = healthFraction(opponentHp)

    fun hasActivePlayerCombatant() = playerActiveCombatants.values.takeIf { it.isNotEmpty() }?.any { !it.condition.contains("FNT", true) }
        ?: !playerCondition.contains("FNT", true)

    fun hasActiveOpponentCombatant() = opponentActiveCombatants.values.takeIf { it.isNotEmpty() }?.any { !it.condition.contains("FNT", true) }
        ?: !opponentCondition.contains("FNT", true)

    fun isBattleFinished() = battleFinished

    fun battleResult() = terminalBattleResult.takeIf { battleFinished && it.isNotBlank() }

    fun battleInfo() = BattleInfo(
        weather,
        terrain,
        playerSideConditions.toList(),
        opponentSideConditions.toList(),
        playerBoosts.toMap(),
        opponentBoosts.toMap(),
        fieldEffects.toList()
    )

    fun battleClock() = battleClock

    fun battleClockSeconds(): Int? = battleClock?.let {
        val elapsed = ((System.nanoTime() - battleClockUpdatedAtNanos) / 1_000_000_000L).toInt()
        (it.turnSeconds - elapsed).coerceAtLeast(0)
    }

    fun isBattleTimerEnabled() = battleTimerEnabled

    fun canCancelChoice() = choiceCanBeCancelled && !decisionAvailable && !battleFinished && !replayMode && !spectatorMode && liveBattleActive

    private fun clearBattleClock() {
        battleClock = null
        battleClockUpdatedAtNanos = 0L
        battleTimerEnabled = false
    }

    private fun clearMoveOptions() {
        moves.clear()
        moveResolutionSources.clear()
        zMoveVariants.clear()
        zMoveResolutionSources.clear()
        maxMoveVariants.clear()
        maxMoveResolutionSources.clear()
    }

    fun setMoveTypeResolver(resolver: (String) -> String?) {
        moveTypeResolver = resolver
        val resolvedMoves = moves.mapIndexed { index, move ->
            val sources = moveResolutionSources.getOrNull(index) ?: MoveResolutionSources()
            if (sources.typeFromRequest) move else move.copy(type = resolver(move.name) ?: move.type)
        }
        val resolvedZMoves = zMoveVariants.mapIndexed { index, variant ->
            variant?.let {
                resolveVariantType(
                    it,
                    zMoveResolutionSources.getOrNull(index) ?: MoveResolutionSources(),
                    resolvedMoves.getOrNull(index),
                    MoveVariantKind.Z_POWER,
                    resolver
                )
            }
        }
        val resolvedMaxMoves = maxMoveVariants.mapIndexed { index, variant ->
            variant?.let {
                resolveVariantType(
                    it,
                    maxMoveResolutionSources.getOrNull(index) ?: MoveResolutionSources(),
                    resolvedMoves.getOrNull(index),
                    MoveVariantKind.DYNAMAX,
                    resolver
                )
            }
        }
        if (resolvedMoves == moves && resolvedZMoves == zMoveVariants && resolvedMaxMoves == maxMoveVariants) return
        moves.clear()
        moves += resolvedMoves
        zMoveVariants.clear()
        zMoveVariants += resolvedZMoves
        maxMoveVariants.clear()
        maxMoveVariants += resolvedMaxMoves
        playerDetails = playerDetails.copy(moves = moves.map { it.name })
        notifyListeners()
    }

    fun setMoveInfoResolver(resolver: (String) -> MoveInfo?) {
        moveInfoResolver = resolver
        val resolvedMoves = moves.mapIndexed { index, move ->
            val info = resolver(move.name) ?: return@mapIndexed move
            val sources = moveResolutionSources.getOrNull(index) ?: MoveResolutionSources()
            move.copy(
                power = if (sources.powerFromRequest) move.power else info.power,
                accuracy = if (sources.accuracyFromRequest) move.accuracy else info.accuracy,
                category = if (sources.categoryFromRequest) move.category else info.category
            )
        }
        val resolvedZMoves = zMoveVariants.mapIndexed { index, variant ->
            variant?.let {
                resolveVariantInfo(
                    it,
                    zMoveResolutionSources.getOrNull(index) ?: MoveResolutionSources(),
                    resolvedMoves.getOrNull(index),
                    MoveVariantKind.Z_POWER,
                    resolver
                )
            }
        }
        val resolvedMaxMoves = maxMoveVariants.mapIndexed { index, variant ->
            variant?.let {
                resolveVariantInfo(
                    it,
                    maxMoveResolutionSources.getOrNull(index) ?: MoveResolutionSources(),
                    resolvedMoves.getOrNull(index),
                    MoveVariantKind.DYNAMAX,
                    resolver
                )
            }
        }
        if (resolvedMoves == moves && resolvedZMoves == zMoveVariants && resolvedMaxMoves == maxMoveVariants) return
        moves.clear()
        moves += resolvedMoves
        zMoveVariants.clear()
        zMoveVariants += resolvedZMoves
        maxMoveVariants.clear()
        maxMoveVariants += resolvedMaxMoves
        playerDetails = playerDetails.copy(moves = moves.map { it.name })
        notifyListeners()
    }

    fun setTeamDetailNameResolvers(
        moveResolver: (String) -> String,
        itemResolver: (String) -> String,
        abilityResolver: (String) -> String
    ) {
        moveNameResolver = moveResolver
        itemNameResolver = itemResolver
        abilityNameResolver = abilityResolver
        val updatedTeam = teamDetails.map(::resolveTeamDetailNames)
        val updatedPlayer = resolveTeamDetailNames(playerDetails)
        val updatedOpponent = resolveTeamDetailNames(opponentDetails)
        val updatedOpponentTeam = opponentTeamDetails.map(::resolveTeamDetailNames)
        if (updatedTeam == teamDetails && updatedPlayer == playerDetails && updatedOpponent == opponentDetails && updatedOpponentTeam == opponentTeamDetails) return
        teamDetails.clear()
        teamDetails += updatedTeam
        playerDetails = updatedPlayer
        opponentDetails = updatedOpponent
        opponentTeamDetails.clear()
        opponentTeamDetails += updatedOpponentTeam
        notifyListeners()
    }

    fun setAbilitySlotResolver(resolver: (String, String) -> String) {
        abilitySlotResolver = resolver
        val updatedTeam = teamDetails.map(::resolveTeamDetailNames)
        val updatedPlayer = resolveTeamDetailNames(playerDetails)
        val updatedOpponent = resolveTeamDetailNames(opponentDetails)
        val updatedOpponentTeam = opponentTeamDetails.map(::resolveTeamDetailNames)
        if (updatedTeam == teamDetails && updatedPlayer == playerDetails && updatedOpponent == opponentDetails && updatedOpponentTeam == opponentTeamDetails) return
        teamDetails.clear()
        teamDetails += updatedTeam
        playerDetails = updatedPlayer
        opponentDetails = updatedOpponent
        opponentTeamDetails.clear()
        opponentTeamDetails += updatedOpponentTeam
        notifyListeners()
    }

    fun setPokemonTypeResolver(resolver: (String) -> List<String>?) {
        pokemonTypeResolver = resolver
        val updatedTeam = teamDetails.map { details -> details.withResolvedTypes() }
        val updatedPlayer = playerDetails.withResolvedTypes()
        val updatedOpponent = opponentDetails.withResolvedTypes()
        val updatedOpponentTeam = opponentTeamDetails.map { details -> details.withResolvedTypes() }
        if (updatedTeam == teamDetails && updatedPlayer == playerDetails && updatedOpponent == opponentDetails && updatedOpponentTeam == opponentTeamDetails) return
        teamDetails.clear()
        teamDetails += updatedTeam
        playerDetails = updatedPlayer
        opponentDetails = updatedOpponent
        opponentTeamDetails.clear()
        activeTeamNames.clear()
        activeSlotNames.clear()
        opponentTeamDetails += updatedOpponentTeam
        notifyListeners()
    }

    fun moves() = displayedMoves().toList()

    fun team() = team.toList()

    fun teamPreviewOrder() = teamPreviewOrder.toList()

    fun teamPreviewRequiredSize() = teamPreviewRequiredSize

    fun battleLog() = battleLog.toList()

    fun battleLogGeneration() = battleLogGeneration

    fun markNativeBattleLogSynchronized(generation: Long) {
        if (generation != battleLogGeneration) return
        nativeBattleLogGeneration = generation
        nativeBattleLogPending = false
        battleFeedEntriesCache.clear()
        notifyListeners()
    }

    fun battleFeedEntries(limit: Int = SHOWDOWN_BATTLE_FEED_WINDOW_LIMIT): List<String> {
        val normalizedLimit = limit.coerceAtLeast(0)
        battleFeedEntriesCache[normalizedLimit]?.let { return it }
        val protocolEntries = battleLog.filter(::isBattleFeedEntry)
        val nativeEntries = if (nativeBattleLogGeneration == battleLogGeneration) {
            showdownBattleLogEntries.filter(::isBattleFeedEntry)
        } else {
            emptyList()
        }
        val source = when {
            !hasBattleProtocolTranscript && nativeEntries.isNotEmpty() -> nativeEntries
            nativeEntries.isEmpty() -> protocolEntries
            else -> mergeBattleFeedEntries(protocolEntries, nativeEntries)
        }
        val entries = source
            .fold(mutableListOf<String>()) { uniqueEntries, entry ->
                if (uniqueEntries.lastOrNull()?.let { BattleFeedMessageIdentity.matches(it, entry) } != true) {
                    uniqueEntries += entry
                }
                uniqueEntries
            }
            .takeLast(normalizedLimit)
            .toList()
        battleFeedEntriesCache[normalizedLimit] = entries
        return entries
    }

    fun latestBattleFeedEntry() = battleFeedEntries(1).lastOrNull()

    fun showdownBattleLog() = showdownBattleLogEntries.toList()

    fun resetShowdownBattleLog() {
        showdownBattleLogEntries.clear()
        showdownBattleMarkupEntries.clear()
        nativeBattleLogGeneration = -1L
        nativeBattleLogPending = false
        battleFeedEntriesCache.clear()
        notifyListeners()
    }

    fun appendShowdownBattleLog(value: String, generation: Long = battleLogGeneration) {
        if (generation != battleLogGeneration) return
        val entries = normalizedShowdownEntries(value)
        if (entries.isEmpty()) return
        showdownBattleLogEntries += entries
        while (showdownBattleLogEntries.size > BATTLE_HISTORY_LIMIT) showdownBattleLogEntries.removeAt(0)
        if (!nativeBattleLogPending) nativeBattleLogGeneration = battleLogGeneration
        battleFeedEntriesCache.clear()
        entries.forEach(::appendNativeActivity)
        latestBattleEvent = entries.last()
        latestBattleEventAtNanos = System.nanoTime()
        battleFeedVisible = true
        notifyListeners()
    }

    fun replaceShowdownBattleMarkup(key: String, value: String, generation: Long = battleLogGeneration) {
        if (generation != battleLogGeneration) return
        val normalizedKey = key.trim()
        if (normalizedKey.isBlank()) {
            appendShowdownBattleLog(value, generation)
            return
        }
        val previous = showdownBattleMarkupEntries.remove(normalizedKey).orEmpty()
        val entries = normalizedShowdownEntries(value)
        if (previous == entries) return
        battleFeedEntriesCache.clear()
        previous.forEach { entry ->
            showdownBattleLogEntries.indexOfLast { it == entry }
                .takeIf { it >= 0 }
                ?.let(showdownBattleLogEntries::removeAt)
            activityMessages.indexOfLast { it == entry }
                .takeIf { it >= 0 }
                ?.let(::removeActivityAt)
        }
        if (entries.isNotEmpty()) {
            showdownBattleMarkupEntries[normalizedKey] = entries
            showdownBattleLogEntries += entries
            while (showdownBattleLogEntries.size > BATTLE_HISTORY_LIMIT) showdownBattleLogEntries.removeAt(0)
            entries.forEach(::appendNativeActivity)
            latestBattleEvent = entries.last()
            latestBattleEventAtNanos = System.nanoTime()
        } else {
            latestBattleEvent = showdownBattleLogEntries.lastOrNull().orEmpty()
            latestBattleEventAtNanos = System.nanoTime()
        }
        if (!nativeBattleLogPending) nativeBattleLogGeneration = battleLogGeneration
        battleFeedVisible = true
        notifyListeners()
    }

    fun battleFeedText(): String? = battleLog.asReversed()
        .firstOrNull { it.isNotBlank() && !it.startsWith("Turn ") }

    fun chatMessages() = chatMessages.toList()

    fun activityMessages() = activityMessages.toList()

    fun playerDetails() = playerDetails

    fun opponentDetails() = opponentDetails

    fun playerPartyDetails() = teamDetails.toList()

    fun opponentPartyDetails() = opponentTeamDetails.toList()

    fun playerActiveCombatants(): List<ActiveCombatant> {
        if (playerActiveCombatantsViewDirty) {
            playerActiveCombatantsView = playerActiveCombatants.values.sortedBy { it.slot }
            playerActiveCombatantsViewDirty = false
        }
        return playerActiveCombatantsView
    }

    fun opponentActiveCombatants(): List<ActiveCombatant> {
        if (opponentActiveCombatantsViewDirty) {
            opponentActiveCombatantsView = opponentActiveCombatants.values.sortedBy { it.slot }
            opponentActiveCombatantsViewDirty = false
        }
        return opponentActiveCombatantsView
    }

    fun detailsForActiveCombatant(playerSide: Boolean, slot: String): PokemonDetails? {
        val combatant = (if (playerSide) playerActiveCombatants else opponentActiveCombatants)[slot] ?: return null
        val party = if (playerSide) teamDetails else opponentTeamDetails
        val partyIndex = if (playerSide) playerActivePartyIndices[slot] else opponentActivePartyIndices[slot]
        val base = partyIndex?.let { party.getOrNull(it) }
            ?: party.firstOrNull { it.matchesIdentifier(combatant.name) }
            ?: PokemonDetails(
                name = combatant.name,
                types = combatant.types,
                level = combatant.level,
                gender = combatant.gender,
                hp = combatant.hp,
                condition = combatant.condition,
                ability = "Unknown ability",
                item = "Unknown item",
                moves = emptyList(),
                stats = "",
                species = combatant.species.ifBlank { combatant.name }
            )
        return base.copy(
            name = combatant.name,
            types = combatant.types,
            level = combatant.level,
            gender = combatant.gender,
            hp = combatant.hp,
            condition = combatant.condition,
            species = combatant.species.ifBlank { base.species }
        )
    }

    fun focusedTeamDetails() = teamDetails.getOrElse(focusedTeam) { playerDetails }

    fun teamMemberDetails(index: Int) = teamDetails.getOrElse(index) { playerDetails }

    fun teamCondition(index: Int) = teamDetails.getOrNull(index)?.condition.orEmpty()

    fun teamCardStatus(index: Int): String {
        val details = teamDetails.getOrNull(index)
        val pokemon = team.getOrNull(index)
        return when {
            details?.condition?.contains("FNT", true) == true -> "Fainted"
            decisionKind == DecisionKind.TEAM_PREVIEW -> {
                if (!decisionAvailable) {
                    "Waiting"
                } else {
                    val previewPosition = teamPreviewOrder.indexOf(index)
                    if (previewPosition >= 0) "Order ${previewPosition + 1}/$teamPreviewRequiredSize" else "Tap to order"
                }
            }
            pokemon?.let { it.equals(playerPokemon, true) || details?.matchesIdentifier(playerPokemon) == true } == true -> "In battle"
            decisionKind == DecisionKind.SWITCH && "switch ${index + 1}" in forceSwitchChoices -> {
                val selectedCount = forceSwitchChoices.count { it.startsWith("switch ") }
                if (requiredSwitches > 1) "Selected $selectedCount/$requiredSwitches" else "Selected"
            }
            decisionKind == DecisionKind.SWITCH -> if (decisionAvailable) "Switch in" else "Waiting"
            else -> "Available"
        }
    }

    fun availableGimmicks() = availableGimmicks.toList()

    fun terastallizeType() = availableTeraType

    fun targetOptions() = targetOptions.toList()

    fun canTestFight() = decisionAvailable &&
        decisionKind == DecisionKind.MOVE &&
        !spectatorMode &&
        activeRequests.getOrNull(activeSlotIndex)?.optBoolean("maybeLocked") == true

    fun canShift() = decisionAvailable &&
        decisionKind == DecisionKind.MOVE &&
        !spectatorMode &&
        gameType.equals("triples", true) &&
        activeRequests.size >= 3 &&
        (activeSlotIndex == 0 || activeSlotIndex == 2)

    fun availableMatchFormats() = availableMatchFormats.toList()

    fun isSinglesBattle() = gameType.equals("singles", true)

    fun showdownBackdrop() = SHOWDOWN_BACKDROPS[Math.floorMod(battleVisualSeed, SHOWDOWN_BACKDROPS.size)]

    fun showdownMusicIndex() = Math.floorMod(battleVisualSeed, 15)

    fun addListener(listener: Listener) {
        listeners += listener
    }

    fun removeListener(listener: Listener) {
        listeners -= listener
    }

    fun addFeedbackListener(listener: FeedbackListener) {
        feedbackListeners += listener
    }

    fun removeFeedbackListener(listener: FeedbackListener) {
        feedbackListeners -= listener
    }

    fun addDecisionListener(listener: DecisionListener) {
        decisionListeners += listener
    }

    fun removeDecisionListener(listener: DecisionListener) {
        decisionListeners -= listener
    }

    fun addClientActionListener(listener: ClientActionListener) {
        clientActionListeners += listener
    }

    fun removeClientActionListener(listener: ClientActionListener) {
        clientActionListeners -= listener
    }

    fun addChatListener(listener: ChatListener) {
        chatListeners += listener
    }

    fun removeChatListener(listener: ChatListener) {
        chatListeners -= listener
    }

    fun addProtocolListener(listener: ProtocolListener) {
        protocolListeners += listener
    }

    fun removeProtocolListener(listener: ProtocolListener) {
        protocolListeners -= listener
    }

    fun addBattleEventListener(listener: BattleEventListener) {
        battleEventListeners += listener
    }

    fun removeBattleEventListener(listener: BattleEventListener) {
        battleEventListeners -= listener
    }

    fun protocolHistory() = protocolHistory.toList()

    fun setLocalUsername(username: String) {
        localUsername = username
        sideNames.entries.firstOrNull { it.value.equals(username, true) }?.key?.let { playerSlot = it }
        updatePerspective()
        notifyListeners()
    }

    fun battlePlayerSlot() = playerSlot

    fun restoreBattlePlayerSlot(slot: String?) {
        val value = slot?.takeIf { it.matches(Regex("p[1-4]")) } ?: return
        restoredPlayerSlot = value
        playerSlot = value
        updatePerspective()
        notifyListeners()
    }

    fun localUsername() = localUsername.orEmpty()

    fun setConnectionStatus(value: String) {
        status = ShowdownFormatCompatibility.canonicalizeLegacyText(value)
        notifyListeners()
    }

    fun setBattleSearchActive(value: Boolean) {
        if (battleSearchActive == value) return
        battleSearchActive = value
        notifyListeners()
    }

    fun isBattleSearchActive() = battleSearchActive

    fun setLiveBattleActive(value: Boolean) {
        val changed = liveBattleActive != value
        liveBattleActive = value
        if (value && changed && !battleFinished && !spectatorMode && panel == Panel.MENU) {
            panel = Panel.MOVES
            status = if (decisionAvailable) "Choose a move" else "Battle starting"
        }
        if (!value) {
            battleParticipant = false
            spectatorMode = false
            decisionAvailable = false
            choiceCanBeCancelled = false
            requestNoCancel = false
            requestTargetable = true
            decisionKind = DecisionKind.WAIT
            selectedGimmick = null
            selectedTargetIndex = -1
            activeSlotIndex = 0
            requiredSwitches = 0
            requestId = null
            clearMoveOptions()
            availableGimmicks.clear()
            availableTeraType = ""
            activeRequests.clear()
            activeChoices.clear()
            autoPassActiveSlots.clear()
            revivingTeamIndices.clear()
            usedGimmickFamilies.clear()
            forceSwitchChoices.clear()
            forceSwitchSlots.clear()
            targetOptions.clear()
            if (!battleFinished) {
                battlePhase = BattlePhase.LOBBY
                status = LOBBY_STATUS
                latestBattleEvent = LOBBY_STATUS
                latestBattleEventAtNanos = System.nanoTime()
            }
        }
        if (!changed && value) return
        notifyListeners()
    }

    fun isLiveBattleActive() = liveBattleActive

    fun hasBattleProtocolTranscript() = hasBattleProtocolTranscript

    fun setBattleParticipant(value: Boolean) {
        battleParticipant = value && !spectatorMode
        notifyListeners()
    }

    fun isBattleParticipant() = !spectatorMode && (battleParticipant || restoredPlayerSlot != null || localUsername?.takeIf(String::isNotBlank)?.let { username ->
        sideNames.values.any { it.equals(username, true) }
    } == true)

    fun setReplayMode(value: Boolean) {
        replayMode = value
        if (value) {
            battleParticipant = false
            spectatorMode = false
            decisionAvailable = false
            choiceCanBeCancelled = false
            decisionKind = DecisionKind.WAIT
            activeRequests.clear()
            activeChoices.clear()
            autoPassActiveSlots.clear()
            revivingTeamIndices.clear()
            forceSwitchChoices.clear()
            forceSwitchSlots.clear()
            targetOptions.clear()
        }
        notifyListeners()
    }

    fun isReplayMode() = replayMode

    fun setSpectatorMode(value: Boolean) {
        spectatorMode = value
        if (value) {
            battleParticipant = false
            replayMode = false
            decisionAvailable = false
            choiceCanBeCancelled = false
            requestNoCancel = false
            requestTargetable = true
            decisionKind = DecisionKind.WAIT
            selectedGimmick = null
            selectedTargetIndex = -1
            activeSlotIndex = 0
            requiredSwitches = 0
            requestId = null
            clearMoveOptions()
            availableGimmicks.clear()
            availableTeraType = ""
            activeRequests.clear()
            activeChoices.clear()
            autoPassActiveSlots.clear()
            revivingTeamIndices.clear()
            usedGimmickFamilies.clear()
            forceSwitchChoices.clear()
            forceSwitchSlots.clear()
            targetOptions.clear()
            status = "Spectating battle"
        }
        notifyListeners()
    }

    fun isSpectatorMode() = spectatorMode

    fun prepareForLobby() {
        restoredPlayerSlot = null
        battleParticipant = false
        battleSearchActive = false
        applyInit(listOf("", "init", "battle"))
        replayMode = false
        spectatorMode = false
        protocolHistory.clear()
        battleLog.clear()
        showdownBattleLogEntries.clear()
        showdownBattleMarkupEntries.clear()
        battleRoomPresenceLog = null
        battleRoomRenameFallback = null
        battleFeedEntriesCache.clear()
        battleLog += "No battle in progress."
        chatMessages.clear()
        chatMessages += "[System] Ready for a battle."
        activityMessages.clear()
        activityMessages += battleLog
        activityMessages += chatMessages
        activityOrigins.clear()
        repeat(battleLog.size) { activityOrigins += ActivityOrigin.SYSTEM }
        repeat(chatMessages.size) { activityOrigins += ActivityOrigin.CHAT }
        hasBattleProtocolTranscript = false
        liveBattleActive = false
        battleFinished = false
        terminalBattleResult = ""
        battlePhase = BattlePhase.LOBBY
        battleFeedVisible = true
        decisionAvailable = false
        choiceCanBeCancelled = false
        requestNoCancel = false
        requestTargetable = true
        decisionKind = DecisionKind.WAIT
        panel = Panel.MENU
        status = LOBBY_STATUS
        latestBattleEvent = status
        latestBattleEventAtNanos = System.nanoTime()
        notifyListeners()
    }

    fun prepareForReplay() {
        prepareForLobby()
        battleLog.clear()
        battleLog += "Loading replay…"
        activityMessages.clear()
        activityMessages += battleLog
        activityOrigins.clear()
        repeat(battleLog.size) { activityOrigins += ActivityOrigin.SYSTEM }
        status = "Loading replay…"
        latestBattleEvent = status
        latestBattleEventAtNanos = System.nanoTime()
        notifyListeners()
    }

    fun presentBattleEvent(message: String) {
        if (message.isBlank()) return
        latestBattleEvent = message
        latestBattleEventAtNanos = System.nanoTime()
        notifyListeners()
    }

    fun sendOutMessage(pokemon: String, playerSide: Boolean) =
        if (playerSide) "Go! ${displayPokemonName(pokemon)}!" else "$opponentName sent out ${displayPokemonName(pokemon)}!"

    fun setMatchFormat(format: MatchFormat) {
        matchFormat = ShowdownFormatCompatibility.canonical(format)
        status = "Battle format: ${matchFormatDisplayLabel()}"
        notifyListeners()
    }

    fun applyUserPreferences(
        soundEffects: Boolean,
        music: Boolean,
        haptics: Boolean,
        announcer: Boolean = false
    ) {
        soundEffectsEnabled = soundEffects
        musicEnabled = music
        hapticsEnabled = haptics
        announcerEnabled = announcer
        this.spriteStyle = SpriteStyle.MODERN_3D
        notifyListeners()
    }

    fun applyServerFormats(lines: List<String>) {
        val formats = lines.flatMap(::parseServerFormats)
            .map(ShowdownFormatCompatibility::canonical)
            .distinctBy { it.id.trim().lowercase() }
        if (formats.isEmpty()) return
        val currentWasAdvertised = availableMatchFormats.any { it.id.trim().equals(matchFormat.id.trim(), true) }
        val selected = formats.firstOrNull {
            it.id.trim().equals(matchFormat.id.trim(), true) && (it.canSearch || currentWasAdvertised)
        }
            ?: matchFormat.takeIf { currentWasAdvertised && it.canSearch }
            ?: formats.firstOrNull { it.canSearch }
            ?: formats.first()
        val refreshedFormats = formats.toMutableList()
        if (refreshedFormats.none { it.id.trim().equals(selected.id.trim(), true) }) refreshedFormats.add(0, selected)
        availableMatchFormats.clear()
        availableMatchFormats += refreshedFormats
        matchFormat = ShowdownFormatCompatibility.canonical(selected)
        notifyListeners()
    }

    fun focusMove(index: Int) {
        if (redirectToTeamDecisionPanel()) return
        val selectableMoves = displayedMoves()
        if (index !in selectableMoves.indices) return
        panel = Panel.MOVES
        focusedMove = index
        updateTargetOptions()
        status = "Ready: ${selectableMoves[index].name}"
        notifyListeners()
    }

    fun selectMoveWithTouch(index: Int) {
        if (redirectToTeamDecisionPanel()) return
        val selectableMoves = displayedMoves()
        if (index !in selectableMoves.indices) return
        if (selectableMoves[index].disabled) {
            status = "${selectableMoves[index].name} is disabled."
            notifyListeners()
            return
        }
        focusedMove = index
        panel = Panel.MOVES
        updateTargetOptions()
        confirmSelection()
    }

    fun selectTargetWithTouch(index: Int) {
        if (index !in targetOptions.indices) return
        selectedTargetIndex = index
        status = "Target: ${targetOptions[index].label}"
        confirmSelection()
    }

    fun selectTestFightWithTouch() {
        if (!canTestFight()) return
        submitTestFight()
        notifyListeners()
    }

    fun selectShiftWithTouch() {
        if (!canShift()) return
        submitShiftChoice()
        notifyListeners()
    }

    fun cycleTarget(direction: Int) {
        if (targetOptions.isEmpty()) return
        val current = if (selectedTargetIndex < 0) 0 else selectedTargetIndex
        selectedTargetIndex = Math.floorMod(current + direction, targetOptions.size)
        status = "Target: ${targetOptions[selectedTargetIndex].label}"
        notifyListeners()
    }

    fun selectTeamWithTouch(index: Int) {
        if (index !in team.indices) return
        focusedTeam = index
        panel = Panel.TEAM
        confirmSelection()
    }

    fun moveFocus(horizontal: Int, vertical: Int) {
        when (panel) {
            Panel.MOVES -> moveMoveFocus(horizontal, vertical)
            Panel.TEAM -> moveTeamFocus(horizontal, vertical)
            Panel.ACTIVITY -> moveMessageFocus(activityMessages.size, vertical)
            Panel.MENU -> moveMenuFocus(horizontal, vertical)
        }
    }

    fun selectPanel(nextPanel: Panel) {
        panel = if (requiresTeamDecision()) Panel.TEAM else nextPanel
        focusedMessage = if (panel == Panel.ACTIVITY) activityMessages.lastIndex.coerceAtLeast(0) else 0
        if (panel == Panel.MENU) focusedMenuItem = 0
        if (!liveBattleActive && !battleFinished) {
            if (panel == Panel.MOVES || panel == Panel.TEAM) status = LOBBY_STATUS
        } else if (spectatorMode) {
            status = "Spectating battle"
        } else {
            status = when (panel) {
                Panel.MOVES -> "Choose a move"
                Panel.TEAM -> when (decisionKind) {
                    DecisionKind.SWITCH -> "Choose a Pokémon to switch in"
                    DecisionKind.TEAM_PREVIEW -> teamPreviewPrompt()
                    DecisionKind.MOVE -> "Choose a move or switch Pokémon"
                    else -> "Choose a Pokémon"
                }
                Panel.ACTIVITY -> "Battle activity and chat"
                Panel.MENU -> "Battle menu"
            }
        }
        notifyListeners()
    }

    fun selectMenuItem(index: Int) {
        if (index !in 0 until MENU_ITEM_COUNT) return
        focusedMenuItem = index
        status = "Ready: ${menuAction(index)}"
        notifyListeners()
    }

    fun menuItems() = (0 until MENU_ITEM_COUNT).map(::menuAction)

    fun cyclePanel(direction: Int) {
        val panels = listOf(Panel.MOVES, Panel.TEAM, Panel.ACTIVITY, Panel.MENU)
        val index = panels.indexOf(panel).takeIf { it >= 0 } ?: 0
        selectPanel(panels[Math.floorMod(index + direction, panels.size)])
    }

    fun confirmSelection() {
        if (spectatorMode && panel != Panel.MENU && panel != Panel.ACTIVITY) {
            status = "Spectators cannot make battle choices."
            notifyListeners()
            return
        }
        when (panel) {
            Panel.MOVES -> {
                val selectableMoves = displayedMoves()
                if (!decisionAvailable || decisionKind != DecisionKind.MOVE || focusedMove !in selectableMoves.indices) return
                val move = selectableMoves[focusedMove]
                if (move.disabled) {
                    status = "${move.name} is disabled."
                    return
                }
                val target = targetOptions.getOrNull(selectedTargetIndex)?.choice
                if (targetOptions.isNotEmpty() && target == null) {
                    status = "Choose a target for ${move.name}."
                    return
                }
                val gimmick = selectedGimmick
                val gimmickLabel = gimmick?.let(::gimmickLabel)
                val selectedChoice = "move ${focusedMove + 1}${gimmick?.let { " ${it.choiceSuffix}" } ?: ""}${target?.let { " $it" } ?: ""}"
                val activeRequest = activeRequests.getOrNull(activeSlotIndex)
                val isLastActiveChoice = activeRequests.size <= 1 || activeSlotIndex >= activeRequests.lastIndex
                val choiceMayNotBeCancelled = isLastActiveChoice &&
                    !requestTargetable && activeRequest?.optBoolean("maybeDisabled") == true
                gimmick?.let { usedGimmickFamilies += gimmickFamily(it) }
                if (activeRequests.size > 1) {
                    ensureActiveChoiceSlots()
                    activeChoices[activeSlotIndex] = selectedChoice
                    selectedGimmick = null
                    if (activeSlotIndex < activeRequests.lastIndex) {
                        activeSlotIndex += 1
                        if (prepareNextActiveRequest()) {
                            notifyListeners()
                            return
                        }
                    }
                }
                val selectedChoices = if (activeRequests.size > 1) activeChoices.joinToString(", ") else selectedChoice
                val choice = "/choose $selectedChoices${requestId?.let { "|$it" } ?: ""}"
                status = "Move sent: ${gimmickLabel?.plus(" ") ?: ""}${move.name}"
                appendLog("${displayPokemonName(playerPokemon)} chose ${gimmickLabel?.plus(" ") ?: ""}${move.name}.")
                chatMessages += "[You] $choice"
                decisionAvailable = false
                choiceCanBeCancelled = !requestNoCancel && !choiceMayNotBeCancelled
                selectedGimmick = null
                selectedTargetIndex = -1
                decisionListeners.toList().forEach { it.onDecision(choice) }
            }
            Panel.TEAM -> confirmTeamSelection()
            Panel.ACTIVITY -> publishClientAction(ClientAction.OPEN_CHAT)
            Panel.MENU -> applyMenuSelection()
        }
        notifyListeners()
    }

    fun handleDecisionSendFailure() {
        if (battleFinished || decisionAvailable) return
        selectedGimmick = null
        selectedTargetIndex = -1
        activeChoices.clear()
        usedGimmickFamilies.clear()
        forceSwitchChoices.clear()
        choiceCanBeCancelled = false
        activeSlotIndex = 0
        when (decisionKind) {
            DecisionKind.MOVE -> {
                prepareNextActiveRequest()
                panel = Panel.MOVES
                status = "Connection unavailable. Choose a move again."
            }
            DecisionKind.SWITCH -> {
                panel = Panel.TEAM
                decisionAvailable = team.indices.any(::canSwitchTo)
                status = "Connection unavailable. Choose a Pokémon to switch in again."
            }
            DecisionKind.TEAM_PREVIEW -> {
                teamPreviewOrder.clear()
                panel = Panel.TEAM
                decisionAvailable = teamPreviewRequiredSize > 0 && team.indices.any { !teamCondition(it).contains("FNT", true) }
                status = "Connection unavailable. Set your team order again."
            }
            DecisionKind.WAIT -> return
        }
        notifyListeners()
    }

    fun openChatComposer() {
        publishClientAction(ClientAction.OPEN_CHAT)
    }

    fun sendChat(message: String) {
        val value = message.trim()
        if (value.isBlank()) return
        val displayMessage = "[You] $value"
        chatMessages += displayMessage
        if (chatMessages.size > 32) chatMessages.removeAt(0)
        appendActivity(displayMessage, ActivityOrigin.CHAT)
        status = "Message sent."
        chatListeners.toList().forEach { it.onChat(value) }
        notifyListeners()
    }

    fun removeLocalChat(message: String) {
        val displayMessage = "[You] ${message.trim()}"
        chatMessages.indexOfLast { it == displayMessage }.takeIf { it >= 0 }?.let(chatMessages::removeAt)
        activityMessages.indexOfLast { it == displayMessage }.takeIf { it >= 0 }?.let(::removeActivityAt)
        notifyListeners()
    }

    fun goBack() {
        if (panel == Panel.TEAM && decisionAvailable && decisionKind == DecisionKind.TEAM_PREVIEW && teamPreviewOrder.isNotEmpty()) {
            val removed = teamPreviewOrder.removeAt(teamPreviewOrder.lastIndex)
            status = "Removed ${teamDisplayName(removed)} from the order."
            notifyListeners()
            return
        }
        if (selectedGimmick != null) {
            selectedGimmick = null
            status = "Choose a move"
            notifyListeners()
            return
        }
        if (targetOptions.isNotEmpty()) {
            targetOptions.clear()
            selectedTargetIndex = -1
            status = "Choose a move"
            notifyListeners()
            return
        }
        if (panel != Panel.MOVES) selectPanel(Panel.MOVES)
    }

    fun selectGimmick(gimmick: BattleGimmick) {
        if (gimmick !in availableGimmicks) return
        selectedGimmick = if (selectedGimmick == gimmick) null else gimmick
        updateTargetOptions()
        status = selectedGimmick?.let { "${gimmickLabel(it)} ready: choose a move" } ?: "Choose a move"
        notifyListeners()
    }

    fun cycleGimmick() {
        if (availableGimmicks.isEmpty()) return
        val currentIndex = availableGimmicks.indexOf(selectedGimmick)
        selectedGimmick = availableGimmicks[Math.floorMod(currentIndex + 1, availableGimmicks.size)]
        updateTargetOptions()
        status = "${selectedGimmick?.let(::gimmickLabel)} ready: choose a move"
        notifyListeners()
    }

    fun applyProtocolLine(line: String?) {
        applyProtocolPacket(listOfNotNull(line))
    }

    fun applyLobbyChat(lines: List<String>) {
        lines.map { it.split('|') }
            .filter { it.getOrNull(1) == "c" || it.getOrNull(1) == "c:" || it.getOrNull(1) == "pm" }
            .forEach(::applyChat)
        notifyListeners()
    }

    fun applyProtocolPacket(lines: List<String>) {
        val rawPacket = lines.filter { it.startsWith('|') }
        synchronizePlayerSlot(rawPacket)
        val packet = visibleProtocolLines(rawPacket)
        if (packet.any { it.startsWith("|init|battle") }) protocolHistory.clear()
        protocolHistory += packet
        val events = mutableListOf<String>()
        protocolEventCollector = events
        try {
            packet.forEach { line ->
                val fields = line.split('|')
                if (fields.size < 2) return@forEach
                if (line == "|") {
                    battleFeedVisible = false
                    return@forEach
                }
                when (fields[1]) {
                    "j", "join", "l", "leave" -> battleRoomRenameFallback = null
                    "n", "name" -> Unit
                    "request" -> Unit
                    else -> {
                        battleRoomPresenceLog = null
                        battleRoomRenameFallback = null
                    }
                }
                when (fields[1]) {
                    "init" -> applyInit(fields)
                    "player" -> applyPlayer(fields)
                    "gametype" -> applyGameType(fields)
                    "clearpoke" -> {
                        opponentTeamDetails.clear()
                        opponentPartyIdentifiers.clear()
                        opponentActivePartyIndices.clear()
                    }
                    "showteam" -> applyShowTeam(fields)
                    "gen" -> fields.getOrNull(2)?.toIntOrNull()?.let { appendLog("Generation $it battle.") }
                    "tier" -> if (fields.size > 2) {
                        format = ShowdownFormatCompatibility.canonicalizeLegacyText(fields[2])
                        appendLog("Format: $format")
                    }
                    "rule" -> fields.getOrNull(2)?.let(::sanitizeMarkup)?.takeIf { it.isNotBlank() }?.let { appendLog("Rule: $it") }
                    "teamsize" -> fields.getOrNull(2)?.let { side ->
                        fields.getOrNull(3)?.toIntOrNull()?.let { size ->
                            appendLog("${sideNames[side] ?: side} team size: $size")
                        }
                    }
                    "rated" -> {
                        val message = sanitizeMarkup(fields.drop(2).joinToString("|"))
                        appendLog(message?.takeIf { it.isNotBlank() } ?: "Rated battle.")
                    }
                    "teampreview" -> {
                        battlePhase = BattlePhase.TEAM_PREVIEW
                        fields.getOrNull(2)?.toIntOrNull()?.takeIf { it > 0 }?.let { protocolTeamPreviewSize = it }
                    }
                    "start" -> battlePhase = BattlePhase.BATTLE
                    "upkeep" -> battlePhase = BattlePhase.UPKEEP
                    "turn" -> {
                        battlePhase = BattlePhase.BATTLE
                        applyTurn(fields)
                    }
                    "switch", "drag" -> applySwitch(fields)
                    "replace" -> applySwitch(fields, replacingIllusion = true)
                    "swap" -> applySwap(fields)
                    "poke" -> applyPoke(fields)
                    "updatepoke" -> applyUpdatePoke(fields)
                    "move" -> {
                        publishPendingHit()
                        applyMove(fields)
                    }
                    "-damage" -> {
                        val target = protocolTarget(fields)
                        if (target != null && pendingHit != null && pendingHit?.target != target) publishPendingHit()
                        applyHealth(fields)
                        target?.takeUnless { hasProtocolSource(fields) }?.let {
                            if (pendingHit == null) pendingHit = PendingHit(it, it)
                        }
                    }
                    "-heal" -> {
                        applyHealth(fields)
                        if (!isSilent(fields)) appendLog("${battleActor(fields.getOrNull(2))} recovered health.")
                    }
                    "-sethp" -> {
                        healthUpdateTargetIndices(fields).forEach { targetIndex ->
                            val target = protocolTarget(fields, targetIndex)
                            val previousHealth = fields.getOrNull(targetIndex)?.let(::healthForActor)?.let(::healthFractionOrNull)
                            val nextHealth = fields.getOrNull(targetIndex + 1)?.let(::healthFractionOrNull)
                            if (target != null && pendingHit != null && pendingHit?.target != target) publishPendingHit()
                            applyHealth(fields, targetIndex)
                            if (target != null && previousHealth != null && nextHealth != null && nextHealth < previousHealth && !hasProtocolSource(fields)) {
                                if (pendingHit == null) pendingHit = PendingHit(target, target)
                            }
                        }
                    }
                    "-status" -> applyStatus(fields)
                    "-curestatus" -> applyStatus(fields, cured = true)
                    "-cureteam" -> cureTeam(fields)
                    "-supereffective" -> applyHitModifier(fields, superEffective = true)
                    "-resisted" -> applyHitModifier(fields, resisted = true)
                    "-crit" -> applyHitModifier(fields, critical = true)
                    "-ability" -> applyAbility(fields)
                    "-item" -> applyItem(fields)
                    "-enditem" -> applyEndItem(fields)
                    "-eat" -> applyEat(fields)
                    "-weather" -> applyWeather(fields)
                    "-fieldstart" -> applyFieldEffect(fields, true)
                    "-fieldend" -> applyFieldEffect(fields, false)
                    "-fieldactivate" -> appendLog(battleEffectName(fields.getOrNull(2)).ifBlank { "A field effect activated." })
                    "-sidestart" -> applySideCondition(fields, true)
                    "-sideend" -> applySideCondition(fields, false)
                    "-swapsideconditions" -> swapSideConditions()
                    "-boost" -> applyBoost(fields, 1)
                    "-unboost" -> applyBoost(fields, -1)
                    "-setboost" -> applySetBoost(fields)
                    "cant" -> applyCant(fields)
                    "-fail" -> {
                        val actor = battleActor(fields.getOrNull(2))
                        val effect = battleEffectName(fields.getOrNull(3))
                        appendLog(if (effect.isBlank()) "$actor's move failed." else "$actor failed to use $effect.")
                    }
                    "-block" -> applyBlock(fields)
                    "-notarget" -> appendLog("${battleActor(fields.getOrNull(2))} had no target.")
                    "-miss" -> appendLog("${battleActor(fields.getOrNull(2))}'s attack missed ${battleActor(fields.getOrNull(3))}.")
                    "-immune" -> appendLog("${battleActor(fields.getOrNull(2))} is immune.")
                    "-prepare" -> appendLog("${battleActor(fields.getOrNull(2))} is preparing ${battleEffectName(fields.getOrNull(3))}.")
                    "-mustrecharge" -> appendLog("${battleActor(fields.getOrNull(2))} must recharge.")
                    "-end" -> applyEnd(fields)
                    "-endability" -> applyEndAbility(fields)
                    "-hint", "-message" -> sanitizeMarkup(fields.drop(2).joinToString("|"))?.let(::appendLog)
                    "-waiting" -> appendLog("${battleActor(fields.getOrNull(2))} is waiting for ${battleActor(fields.getOrNull(3))}.")
                    "-hitcount" -> appendLog("${battleActor(fields.getOrNull(2))} was hit ${fields.getOrNull(3).orEmpty()} times.")
                    "-singleturn" -> applySingleBattleEffect(fields, turnScoped = true)
                    "-singlemove" -> applySingleBattleEffect(fields, turnScoped = false)
                    "-activate" -> applyActivate(fields)
                    "-ohko" -> appendLog("It's a one-hit KO!")
                    "-combine" -> appendLog("The move effects combined.")
                    "-candynamax" -> appendLog("Dynamax is available.")
                    "-nothing" -> appendLog("The move had no effect.")
                    "-zpower" -> appendLog("${battleActor(fields.getOrNull(2))} used a Z-Power move.")
                    "-zbroken" -> appendLog("${battleActor(fields.getOrNull(2))}'s protection was broken by Z-Power.")
                    "-clearallboost" -> clearAllBoosts()
                    "-clearboost" -> clearBoosts(fields)
                    "-restoreboost" -> clearNegativeBoosts(fields, restored = true)
                    "-clearnegativeboost" -> clearNegativeBoosts(fields)
                    "-clearpositiveboost" -> clearPositiveBoosts(fields)
                    "-copyboost" -> copyBoosts(fields)
                    "-invertboost" -> invertBoosts(fields)
                    "-swapboost" -> swapBoosts(fields)
                    "detailschange", "-formechange" -> applyFormChange(fields)
                    "-burst" -> applyBurst(fields)
                    "-transform" -> applyTransform(fields)
                    "-mega" -> applyGimmickFormChange(fields, "Mega Evolved.")
                    "-primal" -> applyGimmickFormChange(fields, "reverted to its primal form.")
                    "-center" -> appendLog("The remaining Pokémon moved to the center of the field.")
                    "-terastallize" -> applyTerastallize(fields)
                    "custom" -> applyCustom(fields)
                    "-start" -> applyStart(fields)
                    "faint" -> applyFaint(fields)
                    "request" -> applyRequest(fields)
                    "sentchoice" -> applySentChoice(fields)
                    "win" -> applyWin(fields)
                    "tie", "draw", "prematureend" -> applyTie(fields)
                    "j", "join" -> applyBattleRoomPresence(fields, joining = true)
                    "l", "leave" -> applyBattleRoomPresence(fields, joining = false)
                    "n", "name" -> applyBattleRoomRename(fields)
                    "bigerror" -> sanitizeMarkup(fields.drop(2).joinToString("|"))?.takeIf { it.isNotBlank() }?.let { appendLog("Warning: $it") }
                    "error" -> applyBattleError(fields)
                    "c", "c:" -> applyChat(fields)
                    "inactive" -> {
                        val message = fields.drop(2).joinToString("|")
                        battleTimerEnabled = true
                        val clock = parseBattleClock(message)
                        clock?.let {
                            battleClock = it
                            battleClockUpdatedAtNanos = System.nanoTime()
                        }
                        if (clock == null) message.takeIf { it.isNotBlank() }?.let(::appendLog)
                    }
                    "inactiveoff" -> {
                        clearBattleClock()
                        battleTimerEnabled = false
                        fields.drop(2).joinToString("|")
                            .takeIf(String::isNotBlank)
                            ?.let(::sanitizeMarkup)
                            ?.takeIf(String::isNotBlank)
                            ?.let(::appendLog)
                    }
                    "message" -> sanitizeMarkup(fields.drop(2).joinToString("|"))?.let(::appendLog)
                    "raw", "html" -> appendMarkup(fields.drop(2).joinToString("|"))
                    "uhtml", "uhtmlchange" -> applyMarkup(fields.getOrNull(2), fields.drop(3).joinToString("|"))
                }
            }
            publishPendingHit()
            if (replayMode || spectatorMode) {
                decisionAvailable = false
                decisionKind = DecisionKind.WAIT
                activeRequests.clear()
                activeChoices.clear()
                autoPassActiveSlots.clear()
                revivingTeamIndices.clear()
                usedGimmickFamilies.clear()
                forceSwitchChoices.clear()
                forceSwitchSlots.clear()
                targetOptions.clear()
            }
        } finally {
            protocolEventCollector = null
        }
        protocolListeners.toList().forEach { it.onProtocol(packet) }
        if (events.isNotEmpty()) {
            if (battleEventListeners.isEmpty()) {
                latestBattleEvent = events.last()
                latestBattleEventAtNanos = System.nanoTime()
            } else {
                battleEventListeners.toList().forEach { it.onBattleEvents(events) }
            }
        }
        notifyListeners()
    }

    private fun synchronizePlayerSlot(lines: List<String>) {
        val username = localUsername?.takeIf(String::isNotBlank) ?: return
        lines.asSequence()
            .map { it.split('|') }
            .firstOrNull { fields -> fields.getOrNull(1) == "player" && fields.getOrNull(3)?.equals(username, true) == true }
            ?.getOrNull(2)
            ?.takeIf(String::isNotBlank)
            ?.let { playerSlot = it }
    }

    private fun visibleProtocolLines(lines: List<String>): List<String> {
        val visible = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val fields = lines[index].split('|')
            if (fields.getOrNull(1) == "split") {
                val side = fields.getOrNull(2)?.removePrefix("p")?.toIntOrNull()
                val secret = lines.getOrNull(index + 1)
                val shared = lines.getOrNull(index + 2)
                if (side != null) {
                    val selected = if (isPlayerSide("p$side")) secret else shared
                    selected?.takeIf(String::isNotBlank)?.let(visible::add)
                    index += when {
                        shared != null -> 3
                        secret != null -> 2
                        else -> 1
                    }
                    continue
                }
            }
            visible += lines[index]
            index += 1
        }
        return visible
    }

    private fun applyInit(fields: List<String>) {
        if (fields.getOrNull(2) != "battle") return
        hasBattleProtocolTranscript = true
        battleLog.clear()
        showdownBattleLogEntries.clear()
        showdownBattleMarkupEntries.clear()
        battleRoomPresenceLog = null
        battleRoomRenameFallback = null
        battleFeedEntriesCache.clear()
        battleLogGeneration += 1L
        nativeBattleLogGeneration = -1L
        nativeBattleLogPending = true
        battleLog += "Battle started."
        markupEntries.clear()
        chatMessages.clear()
        activityMessages.clear()
        activityMessages += battleLog
        activityOrigins.clear()
        repeat(battleLog.size) { activityOrigins += ActivityOrigin.PROTOCOL }
        latestBattleEvent = "Battle starting"
        latestBattleEventAtNanos = System.nanoTime()
        battleFeedVisible = true
        latestMoveEvent = ""
        latestMoveEventAtNanos = 0L
        latestFaintedPokemon = ""
        latestFaintAtNanos = 0L
        pendingHit = null
        playerName = localUsername ?: "PLAYER"
        opponentName = "OPPONENT"
        turn = 1
        gameType = "singles"
        format = ""
        teamPreviewOrder.clear()
        teamPreviewRequiredSize = 0
        protocolTeamPreviewSize = 0
        battleVisualSeed = Random.nextInt(1, Int.MAX_VALUE)
        selectedGimmick = null
        availableGimmicks.clear()
        availableTeraType = ""
        panel = Panel.MOVES
        battleFinished = false
        terminalBattleResult = ""
        battlePhase = BattlePhase.BATTLE
        openingEntrances = 0
        latestOpeningEntranceAtNanos = 0L
        playerEntryAtNanos = 0L
        opponentEntryAtNanos = 0L
        requestId = null
        clearMoveOptions()
        playerDetails = teamDetails.firstOrNull() ?: playerDetails
        playerPokemon = playerDetails.name
        playerHp = playerDetails.hp
        playerLevel = playerDetails.level
        playerGender = playerDetails.gender
        playerCondition = playerDetails.condition
        opponentDetails = opponentDetails.copy(name = "Unknown", types = emptyList(), level = "50", gender = "", hp = "100/100", condition = "READY", ability = "Unknown ability", item = "Unknown item", moves = emptyList())
        opponentPokemon = opponentDetails.name
        opponentHp = opponentDetails.hp
        opponentLevel = opponentDetails.level
        opponentGender = opponentDetails.gender
        opponentCondition = opponentDetails.condition
        sideNames.clear()
        activeTeamNames.clear()
        activeSlotNames.clear()
        baseTypesBySlot.clear()
        typeChangeBySlot.clear()
        typeAdditionsBySlot.clear()
        terastallizedSlots.clear()
        teraTypesBySlot.clear()
        playerSlot = restoredPlayerSlot ?: "p1"
        activeRequests.clear()
        activeChoices.clear()
        autoPassActiveSlots.clear()
        revivingTeamIndices.clear()
        usedGimmickFamilies.clear()
        forceSwitchSlots.clear()
        forceSwitchChoices.clear()
        targetOptions.clear()
        playerActiveCombatants.clear()
        opponentActiveCombatants.clear()
        playerPartyIdentifiers.clear()
        playerActivePartyIndices.clear()
        opponentPartyIdentifiers.clear()
        opponentActivePartyIndices.clear()
        activeSlotIndex = 0
        requiredSwitches = 0
        selectedTargetIndex = -1
        opponentTeamDetails.clear()
        weather = ""
        terrain = ""
        fieldEffects.clear()
        clearBattleClock()
        playerSideConditions.clear()
        opponentSideConditions.clear()
        playerBoosts.clear()
        opponentBoosts.clear()
        playerBoostsBySlot.clear()
        opponentBoostsBySlot.clear()
        pendingBatonPassBySide.clear()
        decisionAvailable = false
        choiceCanBeCancelled = false
        requestNoCancel = false
        requestTargetable = true
        activeGMaxAvailable = false
        availableTeraType = ""
        decisionKind = DecisionKind.WAIT
        panel = Panel.MOVES
        status = if (spectatorMode) "Spectating battle" else "Battle starting"
    }

    private fun applyGameType(fields: List<String>) {
        val value = fields.getOrNull(2)?.trim().orEmpty()
        if (value.isBlank()) return
        gameType = value
        val label = when (value.lowercase()) {
            "singles" -> "Singles"
            "doubles" -> "Doubles"
            "multi" -> "Multi"
            "freeforall" -> "Free-for-all"
            "rotation" -> "Rotation"
            else -> value.replaceFirstChar { it.uppercase() }
        }
        appendLog("Battle type: $label.")
    }

    private fun applyPlayer(fields: List<String>) {
        if (fields.size < 4) return
        val side = fields[2]
        sideNames[side] = fields[3]
        if (fields[3].equals(localUsername, true)) {
            playerSlot = side
        }
        updatePerspective()
    }

    private fun applyTurn(fields: List<String>) {
        turn = fields.getOrNull(2)?.toIntOrNull() ?: return
        clearTurnEffects()
        appendLog("Turn $turn.")
    }

    private fun applySwitch(fields: List<String>, replacingIllusion: Boolean = false) {
        if (fields.size < 5) return
        val pokemon = fields[3].substringBefore(',')
        val playerSide = isPlayerSide(fields[2])
        val slot = fields[2].substringBefore(":").trim()
        val side = sideForSlot(slot)
        val shiny = detailsAreShiny(fields[3])
        if (fields.drop(5).any { it.contains("Baton Pass", true) }) pendingBatonPassBySide[side] = slot
        val passedBoosts = pendingBatonPassBySide.remove(side)?.let { sourceSlot ->
            val slots = if (playerSide) playerBoostsBySlot else opponentBoostsBySlot
            slots.remove(sourceSlot)?.toMap()
        }
        val entryDelayMillis = if (replacingIllusion) 0L else queueEntry(playerSide)
        val parsedDetails = parseDetails(fields[3])
        val hp = fields[4]
        val currentCondition = condition(hp)
        when {
            playerSide -> {
                val primary = slot.endsWith("a") || playerActiveCombatants.isEmpty()
                activeTeamNames.clear()
                val identifier = fields[2].substringAfter(':').trim()
                val index = if (replacingIllusion) {
                    playerActivePartyIndices[slot]
                        ?: playerPartyIdentifiers.indexOfFirst { it.equals(identifier, true) }.takeIf { it >= 0 }
                } else {
                    playerPartyIdentifiers.indexOfFirst { it.equals(identifier, true) }
                        .takeIf { it >= 0 }
                        ?: teamDetails.indexOfFirst {
                            it.name.equals(identifier, true) || it.name.equals(pokemon, true) || it.species.equals(pokemon, true)
                        }
                }
                if (index != null && index >= 0) playerActivePartyIndices[slot] = index else playerActivePartyIndices.remove(slot)
                val activeDetails = if (index != null && index >= 0) {
                    teamDetails[index]
                } else {
                    playerDetails.copy(name = identifier.ifBlank { pokemon }, species = pokemon, types = resolvedTypes(pokemon))
                }
                val baseTypes = baseTypesFor(pokemon, activeDetails.types)
                baseTypesBySlot[slot] = baseTypes
                typeChangeBySlot.remove(slot)
                typeAdditionsBySlot.remove(slot)
                terastallizedSlots.remove(slot)
                teraTypesBySlot.remove(slot)
                playerBoostsBySlot.remove(slot)
                if (!passedBoosts.isNullOrEmpty()) playerBoostsBySlot[slot] = passedBoosts.toMutableMap()
                val activeName = identifier.ifBlank { activeDetails.name.ifBlank { pokemon } }
                val activeSpecies = pokemon.ifBlank { activeDetails.species }
                val updatedDetails = activeDetails.copy(
                    name = activeName,
                    species = activeSpecies,
                    types = baseTypes,
                    level = parsedDetails.first,
                    gender = parsedDetails.second,
                    hp = hp,
                    condition = currentCondition,
                    shiny = shiny
                )
                if (index != null && index >= 0) teamDetails[index] = updatedDetails
                playerActiveCombatants[slot] = ActiveCombatant(
                    slot,
                    activeName,
                    updatedDetails.types,
                    parsedDetails.first,
                    parsedDetails.second,
                    hp,
                    currentCondition,
                    playerEntryAtNanos,
                    species = updatedDetails.species,
                    shiny = updatedDetails.shiny
                )
                activeSlotNames[slot] = activeName
                activeTeamNames += activeSlotNames.values
                if (primary) {
                    playerPokemon = activeName
                    playerHp = hp
                    playerLevel = parsedDetails.first
                    playerGender = parsedDetails.second
                    playerCondition = currentCondition
                    playerDetails = updatedDetails
                }
            }
            else -> {
                val primary = slot.endsWith("a") || opponentActiveCombatants.isEmpty()
                val identifier = fields[2].substringAfter(':').trim()
                val index = if (replacingIllusion) {
                    opponentActivePartyIndices[slot] ?: findOpponentPartyIndex(identifier, pokemon, slot)
                } else {
                    findOpponentPartyIndex(identifier, pokemon, slot)
                }
                val existing = opponentTeamDetails.getOrNull(index)
                val activeDetails = if (replacingIllusion && existing != null && !existing.species.equals(pokemon, true)) {
                    unknownOpponentDetails(pokemon, parsedDetails, hp, currentCondition, shiny).copy(name = identifier.ifBlank { pokemon })
                } else {
                    existing ?: unknownOpponentDetails(pokemon, parsedDetails, hp, currentCondition, shiny)
                }
                val baseTypes = baseTypesFor(pokemon, activeDetails.types)
                baseTypesBySlot[slot] = baseTypes
                typeChangeBySlot.remove(slot)
                typeAdditionsBySlot.remove(slot)
                terastallizedSlots.remove(slot)
                teraTypesBySlot.remove(slot)
                opponentBoostsBySlot.remove(slot)
                if (!passedBoosts.isNullOrEmpty()) opponentBoostsBySlot[slot] = passedBoosts.toMutableMap()
                val activeName = identifier.ifBlank { existing?.name ?: pokemon }
                val updatedDetails = activeDetails.copy(
                    name = activeName,
                    species = pokemon,
                    types = baseTypes,
                    level = parsedDetails.first,
                    gender = parsedDetails.second,
                    hp = hp,
                    condition = currentCondition,
                    shiny = shiny
                )
                val resolvedIndex = if (index >= 0) {
                    opponentTeamDetails[index] = updatedDetails
                    index
                } else {
                    opponentTeamDetails += updatedDetails
                    opponentTeamDetails.lastIndex
                }
                opponentPartyIdentifiers[identifier] = resolvedIndex
                opponentActivePartyIndices[slot] = resolvedIndex
                opponentActiveCombatants[slot] = ActiveCombatant(
                    slot,
                    activeName,
                    updatedDetails.types,
                    parsedDetails.first,
                    parsedDetails.second,
                    hp,
                    currentCondition,
                    opponentEntryAtNanos,
                    species = updatedDetails.species,
                    shiny = updatedDetails.shiny
                )
                if (primary) {
                    opponentPokemon = activeName
                    opponentHp = hp
                    opponentLevel = parsedDetails.first
                    opponentGender = parsedDetails.second
                    opponentCondition = currentCondition
                    opponentDetails = updatedDetails
                }
            }
        }
        refreshVisibleBoosts()
        val message = if (replacingIllusion) {
            "${displayPokemonName(fields[2].substringAfter(':').trim(), pokemon)} was revealed as ${displayPokemonName(pokemon)}."
        } else {
            sendOutMessage(pokemon, playerSide)
        }
        appendLog(message)
        if (!replacingIllusion) {
            publishFeedback(BattleFeedback(FeedbackType.ENTRY, actor = pokemon, delayMillis = entryDelayMillis, message = message))
            publishFeedback(BattleFeedback(FeedbackType.POKEMON_CRY, actor = pokemon, delayMillis = entryDelayMillis))
        }
    }

    private fun applySwap(fields: List<String>) {
        val actor = fields.getOrNull(2)?.trim().orEmpty()
        val oldSlot = actor.substringBefore(':').trim()
        val position = fields.getOrNull(3)?.toIntOrNull() ?: return
        val sidePrefix = oldSlot.dropLast(1).takeIf { it.isNotBlank() } ?: return
        val newSlot = "$sidePrefix${('a'.code + position).toChar()}"
        if (oldSlot == newSlot) return
        val playerSide = isPlayerSide(oldSlot)
        val combatants = if (playerSide) playerActiveCombatants else opponentActiveCombatants
        val moving = combatants.remove(oldSlot) ?: return
        val displaced = combatants.remove(newSlot)
        combatants[newSlot] = moving.copy(slot = newSlot)
        displaced?.let { combatants[oldSlot] = it.copy(slot = oldSlot) }
        swapSlotState(oldSlot, newSlot)
        if (playerSide) {
            val movingName = activeSlotNames.remove(oldSlot)
            val displacedName = activeSlotNames.remove(newSlot)
            movingName?.let { activeSlotNames[newSlot] = it }
            displacedName?.let { activeSlotNames[oldSlot] = it }
            activeTeamNames.clear()
            activeTeamNames += activeSlotNames.values
            refreshPlayerPrimary()
        } else {
            refreshOpponentPrimary()
        }
        appendLog("${moving.name} moved to position ${position + 1}.")
    }

    private fun refreshPlayerPrimary() {
        val primary = playerActiveCombatants.entries.firstOrNull { it.key.endsWith('a') }?.value ?: return
        val primarySpecies = playerActivePartyIndices[primary.slot]
            ?.let { teamDetails.getOrNull(it)?.species }
            ?.takeIf(String::isNotBlank)
            ?: primary.name
        playerPokemon = primary.name
        playerHp = primary.hp
        playerLevel = primary.level
        playerGender = primary.gender
        playerCondition = primary.condition
        updatePlayerDetails {
            it.copy(
                name = primary.name,
                species = primarySpecies,
                types = primary.types,
                level = primary.level,
                gender = primary.gender,
                hp = primary.hp,
                condition = primary.condition
            )
        }
    }

    private fun refreshOpponentPrimary() {
        val primary = opponentActiveCombatants.entries.firstOrNull { it.key.endsWith('a') }?.value ?: return
        val primarySpecies = opponentActivePartyIndices[primary.slot]
            ?.let { opponentTeamDetails.getOrNull(it)?.species }
            ?.takeIf(String::isNotBlank)
            ?: primary.name
        opponentPokemon = primary.name
        opponentHp = primary.hp
        opponentLevel = primary.level
        opponentGender = primary.gender
        opponentCondition = primary.condition
        opponentDetails = opponentDetails.copy(
            name = primary.name,
            species = primarySpecies,
            types = primary.types,
            level = primary.level,
            gender = primary.gender,
            hp = primary.hp,
            condition = primary.condition
        )
    }

    private fun applyPoke(fields: List<String>) {
        if (fields.size < 4 || isPlayerSide(fields[2])) return
        val details = fields[3]
        val pokemon = details.substringBefore(',')
        val levelGender = parseDetails(details)
        val shiny = detailsAreShiny(details)
        val existing = opponentTeamDetails.firstOrNull { it.name.equals(pokemon, true) }
        updateOpponentParty(
            existing?.copy(level = levelGender.first, gender = levelGender.second, types = resolvedTypes(existing.species, existing.types), shiny = shiny) ?: PokemonDetails(
                name = pokemon,
                types = resolvedTypes(pokemon),
                level = levelGender.first,
                gender = levelGender.second,
                hp = "100/100",
                condition = "READY",
                ability = "Unknown ability",
                item = "Unknown item",
                moves = emptyList(),
                stats = "",
                species = pokemon,
                shiny = shiny
            )
        )
    }

    private fun applyUpdatePoke(fields: List<String>) {
        val actor = fields.getOrNull(2)?.takeIf(String::isNotBlank) ?: return
        val identifier = actor.substringAfter(':').trim().takeIf(String::isNotBlank) ?: return
        val details = fields.getOrNull(3)?.takeIf(String::isNotBlank) ?: return
        val species = details.substringBefore(',').trim().takeIf(String::isNotBlank) ?: return
        val playerSide = isPlayerSide(actor)
        val party = if (playerSide) teamDetails else opponentTeamDetails
        val index = if (playerSide) {
            playerPartyIdentifiers.indexOfFirst { it.equals(identifier, true) }
                .takeIf { it >= 0 }
                ?: party.indexOfFirst { it.matchesIdentifier(identifier) || it.species.equals(species, true) }
        } else {
            opponentPartyIdentifiers[identifier]
                ?.takeIf { it in party.indices }
                ?: party.indexOfFirst { it.matchesIdentifier(identifier) || it.species.equals(species, true) }
        }
        if (index !in party.indices) return

        val previous = party[index]
        val parsed = parseDetails(details)
        val activeIndices = if (playerSide) playerActivePartyIndices else opponentActivePartyIndices
        val activeSlots = activeIndices.filterValues { it == index }.keys
        val fallbackBaseTypes = activeSlots.asSequence()
            .mapNotNull { baseTypesBySlot[it]?.takeIf(List<String>::isNotEmpty) }
            .firstOrNull()
            ?: previous.types
        val updatedBaseTypes = baseTypesFor(species, fallbackBaseTypes)
        val activeCombatants = if (playerSide) playerActiveCombatants else opponentActiveCombatants
        val updatedTypes = activeSlots.asSequence()
            .filter { it in terastallizedSlots }
            .mapNotNull { activeCombatants[it]?.types }
            .firstOrNull()
            ?: updatedBaseTypes
        val updated = previous.copy(
            name = previous.name.takeIf { !it.equals(previous.species, true) } ?: species,
            species = species,
            types = updatedTypes,
            level = parsed.first,
            gender = parsed.second,
            shiny = detailsAreShiny(details) || previous.shiny
        )
        party[index] = updated
        updateActivePartyMember(playerSide, index, updated, updatedBaseTypes)
    }

    private fun updateActivePartyMember(playerSide: Boolean, index: Int, details: PokemonDetails, baseTypes: List<String>) {
        val activeIndices = if (playerSide) playerActivePartyIndices else opponentActivePartyIndices
        val activeCombatants = if (playerSide) playerActiveCombatants else opponentActiveCombatants
        activeIndices.filterValues { it == index }.keys.toList().forEach { slot ->
            val combatant = activeCombatants[slot] ?: return@forEach
            val displayedTypes = if (slot in terastallizedSlots) combatant.types else details.types
            baseTypesBySlot[slot] = baseTypes
            activeCombatants[slot] = combatant.copy(
                name = details.name,
                species = details.species,
                types = displayedTypes,
                level = details.level,
                gender = details.gender
            )
            if (playerSide) activeSlotNames[slot] = details.name
        }
        if (playerSide) {
            activeTeamNames.clear()
            activeTeamNames += activeSlotNames.values
            if (activeIndices.keys.any { it.endsWith('a') && activeIndices[it] == index }) refreshPlayerPrimary()
        } else if (activeIndices.keys.any { it.endsWith('a') && activeIndices[it] == index }) {
            refreshOpponentPrimary()
        }
    }

    private fun applyCustom(fields: List<String>) {
        if (!fields.getOrNull(2).equals("-endterastallize", true)) return
        val actor = fields.getOrNull(3)?.takeIf(String::isNotBlank) ?: return
        val slot = actor.substringBefore(':').trim()
        val wasTerastallized = slot in terastallizedSlots || slot in teraTypesBySlot
        terastallizedSlots.remove(slot)
        teraTypesBySlot.remove(slot)
        typeChangeBySlot.remove(slot)
        typeAdditionsBySlot.remove(slot)
        if (!wasTerastallized) return
        val baseTypes = baseTypesBySlot[slot].orEmpty().ifEmpty { effectiveTypes(slot) }
        updateActiveTypes(actor, baseTypes)
        appendLog("${battleActor(actor)} returned to its original types.")
    }

    private fun applyShowTeam(fields: List<String>) {
        val side = fields.getOrNull(2)?.trim().orEmpty()
        val packedTeam = fields.drop(3).joinToString("|")
        if (side.isBlank() || packedTeam.isBlank()) return
        val sets = ShowdownTeamCodec.unpack(packedTeam).take(6)
        if (sets.isEmpty()) return
        val playerSide = isPlayerSide(side)
        val existingParty = if (playerSide) teamDetails.toList() else opponentTeamDetails.toList()
        val revealedParty = sets.mapIndexed { index, set ->
            val existing = if (playerSide) {
                existingParty.getOrNull(index)
            } else {
                val name = set.nickname.ifBlank { set.species }
                existingParty.firstOrNull { it.name.equals(name, true) }
            }
            revealedPokemonDetails(set, existing)
        }
        if (playerSide) {
            team.clear()
            team += revealedParty.map { it.name }
            playerPartyIdentifiers.clear()
            playerPartyIdentifiers += revealedParty.map { it.name }
            teamDetails.clear()
            teamDetails += revealedParty
            focusedTeam = focusedTeam.coerceIn(0, team.lastIndex)
            teamDetails.firstOrNull { it.name.equals(playerDetails.name, true) }?.let { playerDetails = it }
        } else {
            opponentTeamDetails.clear()
            opponentTeamDetails += revealedParty
            rebuildOpponentPartyIdentifiers()
            opponentActivePartyIndices.clear()
            opponentActiveCombatants.forEach { (slot, combatant) ->
                findOpponentPartyIndex(combatant.name, combatant.name, slot)
                    .takeIf { it >= 0 }
                    ?.let { opponentActivePartyIndices[slot] = it }
            }
            revealedParty.firstOrNull { it.name.equals(opponentDetails.name, true) }?.let { opponentDetails = it }
        }
        appendLog("${sideNames[side] ?: side} revealed their team.")
    }

    private fun revealedPokemonDetails(set: ShowdownTeamSet, existing: PokemonDetails?): PokemonDetails {
        val name = set.nickname.ifBlank { set.species }
        val types = resolvedTypes(set.species, existing?.types.orEmpty())
        val gender = when (set.gender.uppercase()) {
            "M" -> "♂"
            "F" -> "♀"
            else -> existing?.gender.orEmpty()
        }
        return PokemonDetails(
            name = name,
            types = types,
            level = set.level.toString(),
            gender = gender,
            hp = existing?.hp ?: "100/100",
            condition = existing?.condition ?: "READY",
            ability = set.ability.takeIf(String::isNotBlank)?.let { resolveAbilityName(set.species, it) }
                ?: existing?.ability ?: "Unknown ability",
            item = set.item.takeIf(String::isNotBlank)?.let { itemNameResolver?.invoke(it) ?: it }
                ?: existing?.item ?: "Unknown item",
            moves = set.moves.map { moveNameResolver?.invoke(it) ?: it }.ifEmpty { existing?.moves.orEmpty() },
            stats = existing?.stats.orEmpty(),
            pokeball = set.pokeBall.ifBlank { existing?.pokeball ?: "pokeball" },
            species = set.species.ifBlank { existing?.species ?: name },
            shiny = set.shiny
        )
    }

    private fun resolveTeamDetailNames(details: PokemonDetails) = details.copy(
        ability = details.ability.takeUnless { it.isBlank() || it == "Unknown ability" }?.let { resolveAbilityName(details.species, it) }
            ?: details.ability,
        item = details.item.takeUnless { it.isBlank() || it == "Unknown item" }?.let { itemNameResolver?.invoke(it) ?: it }
            ?: details.item,
        moves = details.moves.map { moveNameResolver?.invoke(it) ?: it }
    )

    private fun resolveAbilityName(species: String, ability: String): String {
        val resolved = abilitySlotResolver?.invoke(species, ability) ?: ability
        return abilityNameResolver?.invoke(resolved) ?: resolved
    }

    private fun queueEntry(playerSide: Boolean): Long {
        val nowNanos = System.nanoTime()
        val entranceAtNanos = when (openingEntrances) {
            0 -> nowNanos
            1 -> maxOf(nowNanos, latestOpeningEntranceAtNanos + BattleSceneTiming.summonDurationNanos)
            else -> nowNanos
        }
        if (playerSide) playerEntryAtNanos = entranceAtNanos else opponentEntryAtNanos = entranceAtNanos
        if (openingEntrances < 2) {
            latestOpeningEntranceAtNanos = entranceAtNanos
            openingEntrances += 1
        }
        return ((entranceAtNanos - nowNanos) / 1_000_000L).coerceAtLeast(0L)
    }

    private fun applyMove(fields: List<String>) {
        if (fields.size <= 3) return
        clearMoveEffects(fields[2])
        val actor = fields[2].substringAfter(':').trim()
        val event = "${displayPokemonName(actor)} used ${fields[3]}!"
        latestMoveEvent = event
        latestMoveEventAtNanos = System.nanoTime()
        appendLog(event)
        publishFeedback(BattleFeedback(FeedbackType.MOVE, actor = actor, target = fields.getOrNull(4)?.substringAfter(':')?.trim().orEmpty(), move = fields[3]))
    }

    private fun applyHealth(fields: List<String>, targetIndex: Int = 2) {
        if (fields.size <= targetIndex + 1) return
        val actor = fields[targetIndex]
        val slot = actor.substringBefore(":").trim()
        val pokemon = actor.substringAfter(':').trim()
        val hp = fields[targetIndex + 1]
        val currentCondition = condition(hp)
        when {
            isPlayerSide(actor) -> {
                playerActiveCombatants[slot]?.let {
                    playerActiveCombatants[slot] = it.copy(hp = hp, condition = currentCondition)
                }
                updatePlayerPartyMemberForSlot(slot, pokemon) { details -> details.copy(hp = hp, condition = currentCondition) }
                if (slot.endsWith('a')) {
                    playerHp = hp
                    playerCondition = currentCondition
                    updatePlayerDetails { it.copy(hp = playerHp, condition = playerCondition) }
                }
            }
            else -> {
                opponentActiveCombatants[slot]?.let {
                    opponentActiveCombatants[slot] = it.copy(hp = hp, condition = currentCondition)
                }
                updateOpponentPartyForSlot(slot) { details -> details.copy(hp = hp, condition = currentCondition) }
                if (slot.endsWith('a')) {
                    opponentHp = hp
                    opponentCondition = currentCondition
                    opponentDetails = opponentDetails.copy(hp = opponentHp, condition = opponentCondition)
                    updateOpponentPartyForSlot(slot) { details -> details.copy(hp = opponentHp, condition = opponentCondition) }
                }
            }
        }
    }

    private fun applyFaint(fields: List<String>) {
        val actor = fields.getOrNull(2) ?: return
        val pokemon = actor.substringAfter(':').trim()
        latestFaintedPokemon = pokemon
        latestFaintAtNanos = System.nanoTime()
        val slot = actor.substringBefore(":").trim()
        if (isPlayerSide(actor)) {
            playerActiveCombatants[slot]?.let {
                playerActiveCombatants[slot] = it.copy(hp = "0 fnt", condition = "FNT")
                updatePlayerPartyMemberForSlot(slot, it.name) { details -> details.copy(hp = "0 fnt", condition = "FNT") }
            }
            if (slot.endsWith('a')) {
                playerHp = "0 fnt"
                playerCondition = "FNT"
                updatePlayerDetails { it.copy(hp = playerHp, condition = playerCondition) }
            }
        } else {
            opponentActiveCombatants[slot]?.let {
                opponentActiveCombatants[slot] = it.copy(hp = "0 fnt", condition = "FNT")
                updateOpponentPartyForSlot(slot) { details -> details.copy(hp = "0 fnt", condition = "FNT") }
            }
            if (slot.endsWith('a')) {
                opponentHp = "0 fnt"
                opponentCondition = "FNT"
                opponentDetails = opponentDetails.copy(hp = opponentHp, condition = opponentCondition)
                updateOpponentPartyForSlot(slot) { details -> details.copy(hp = opponentHp, condition = opponentCondition) }
            }
        }
        if (isPlayerSide(actor)) playerBoostsBySlot.remove(slot) else opponentBoostsBySlot.remove(slot)
        refreshVisibleBoosts()
        appendLog("${displayPokemonName(pokemon)} fainted.")
    }

    private fun applyStatus(fields: List<String>, cured: Boolean = false) {
        val actor = fields.getOrNull(2) ?: return
        val status = if (cured) "READY" else fields.getOrNull(3)?.uppercase() ?: return
        val slot = actor.substringBefore(":").trim()
        val pokemon = actor.substringAfter(':').trim()
        if (isPlayerSide(actor)) {
            playerActiveCombatants[slot]?.let {
                playerActiveCombatants[slot] = it.copy(condition = status)
            }
            updatePlayerPartyMemberForSlot(slot, pokemon) { details -> details.copy(condition = status) }
            if (slot.endsWith('a')) {
                playerCondition = status
                updatePlayerDetails { it.copy(condition = status) }
            }
        } else {
            opponentActiveCombatants[slot]?.let {
                opponentActiveCombatants[slot] = it.copy(condition = status)
            }
            updateOpponentPartyForSlot(slot) { details -> details.copy(condition = status) }
            if (slot.endsWith('a')) {
                opponentCondition = status
                opponentDetails = opponentDetails.copy(condition = status)
                updateOpponentPartyForSlot(slot) { details -> details.copy(condition = opponentCondition) }
            }
        }
        appendLog(
            if (cured) "${battleActor(actor)}'s status was cured."
            else formatStatusAnnouncement(actor, status)
        )
    }

    private fun cureTeam(fields: List<String>) {
        val actor = fields.getOrNull(2) ?: return
        if (isPlayerSide(actor)) {
            teamDetails.replaceAll(::curedDetails)
            playerActiveCombatants.entries.toList().forEach { (slot, combatant) ->
                playerActiveCombatants[slot] = combatant.copy(hp = combatant.hp.substringBefore(' '), condition = if (combatant.condition.contains("FNT", true)) "FNT" else "READY")
            }
            updatePlayerDetails(::curedDetails)
            playerCondition = playerDetails.condition
            playerHp = playerDetails.hp
        } else {
            opponentTeamDetails.replaceAll(::curedDetails)
            opponentActiveCombatants.entries.toList().forEach { (slot, combatant) ->
                opponentActiveCombatants[slot] = combatant.copy(hp = combatant.hp.substringBefore(' '), condition = if (combatant.condition.contains("FNT", true)) "FNT" else "READY")
            }
            opponentDetails = curedDetails(opponentDetails)
            opponentCondition = opponentDetails.condition
            opponentHp = opponentDetails.hp
        }
        appendLog("${battleActor(actor)} cured its side's status conditions.")
    }

    private fun curedDetails(details: PokemonDetails): PokemonDetails {
        val hp = details.hp.substringBefore(' ')
        return details.copy(hp = hp, condition = if (details.condition.contains("FNT", true) || hp.startsWith("0")) "FNT" else "READY")
    }

    private fun applyFormChange(fields: List<String>) {
        applyFormChange(fields, null)
    }

    private fun applyBurst(fields: List<String>) {
        applyFormChange(fields)
        val actor = fields.getOrNull(2) ?: return
        fields.getOrNull(4)
            ?.takeUnless { it.trim().startsWith("[") }
            ?.takeIf(String::isNotBlank)
            ?.let { itemNameResolver?.invoke(it) ?: it }
            ?.let { item -> updateActorDetails(actor) { details -> details.copy(item = item) } }
    }

    private fun applyTransform(fields: List<String>) {
        val actor = fields.getOrNull(2) ?: return
        val target = fields.getOrNull(3) ?: return
        val targetDetails = actorDetails(target)
        val species = targetDetails?.species ?: target.substringAfter(':').trim().ifBlank { return }
        val level = targetDetails?.level ?: "50"
        val gender = when (targetDetails?.gender) {
            "♂" -> ", M"
            "♀" -> ", F"
            else -> ""
        }
        val targetBoosts = boostSlots(target)[targetSlot(target)].orEmpty().toMap()
        applyFormChange(
            listOf("", "-transform", actor, "$species, L$level$gender"),
            targetDetails?.types
        )
        val actorBoosts = boostSlots(actor)
        if (targetBoosts.isEmpty()) actorBoosts.remove(targetSlot(actor)) else actorBoosts[targetSlot(actor)] = targetBoosts.toMutableMap()
        refreshVisibleBoosts()
        targetDetails?.let { details ->
            updateActorDetails(actor) { current ->
                current.copy(
                    ability = details.ability,
                    moves = details.moves
                )
            }
        }
    }

    private fun applyFormChange(fields: List<String>, typeOverride: List<String>?) {
        val actor = fields.getOrNull(2) ?: return
        val details = fields.getOrNull(3) ?: return
        val species = details.substringBefore(',').trim().ifBlank { return }
        val slot = actor.substringBefore(":").trim()
        val formHealth = fields.getOrNull(4)
            ?.takeIf { fields.getOrNull(1) == "detailschange" || fields.getOrNull(1) == "-formechange" }
            ?.takeIf(String::isNotBlank)
        val formCondition = formHealth?.let(::condition)
        val baseTypes = typeOverride?.takeIf { it.isNotEmpty() } ?: baseTypesFor(species, resolvedTypes(species))
        baseTypesBySlot[slot] = baseTypes
        typeChangeBySlot.remove(slot)
        typeAdditionsBySlot.remove(slot)
        if (isPlayerSide(actor)) {
            val current = playerActiveCombatants[slot]
            val parsed = parseDetails(details, current?.level ?: playerDetails.level, current?.gender ?: playerDetails.gender)
            val activeName = playerActiveCombatants[slot]
                ?.let { identityName(actor, it.name, it.species, species) }
                ?: identityName(actor, playerDetails.name, playerDetails.species, species)
            playerActiveCombatants[slot]?.let {
                val types = if (slot in terastallizedSlots) it.types else baseTypes
                val hp = formHealth ?: it.hp
                val status = formCondition ?: it.condition
                playerActiveCombatants[slot] = it.copy(
                    name = activeName,
                    species = species,
                    types = types,
                    level = parsed.first,
                    gender = parsed.second,
                    hp = hp,
                    condition = status
                )
                updatePlayerPartyMemberForSlot(slot, it.name) { party ->
                    party.copy(
                        name = identityName(actor, party.name, party.species, species),
                        species = species,
                        types = types,
                        level = parsed.first,
                        gender = parsed.second,
                        hp = hp,
                        condition = status
                    )
                }
            }
            activeSlotNames[slot] = activeName
            activeTeamNames.clear()
            activeTeamNames += activeSlotNames.values
            if (slot.endsWith('a')) {
                playerPokemon = activeName
                playerLevel = parsed.first
                playerGender = parsed.second
                formHealth?.let {
                    playerHp = it
                    playerCondition = formCondition ?: playerCondition
                }
                updatePlayerDetails {
                    it.copy(
                        name = activeName,
                        species = species,
                        types = if (slot in terastallizedSlots) it.types else baseTypes,
                        level = playerLevel,
                        gender = playerGender,
                        hp = playerHp,
                        condition = playerCondition
                    )
                }
            }
        } else {
            val current = opponentActiveCombatants[slot]
            val parsed = parseDetails(details, current?.level ?: opponentDetails.level, current?.gender ?: opponentDetails.gender)
            val activeName = opponentActiveCombatants[slot]
                ?.let { identityName(actor, it.name, it.species, species) }
                ?: identityName(actor, opponentDetails.name, opponentDetails.species, species)
            opponentActiveCombatants[slot]?.let {
                val types = if (slot in terastallizedSlots) it.types else baseTypes
                val hp = formHealth ?: it.hp
                val status = formCondition ?: it.condition
                opponentActiveCombatants[slot] = it.copy(
                    name = activeName,
                    species = species,
                    types = types,
                    level = parsed.first,
                    gender = parsed.second,
                    hp = hp,
                    condition = status
                )
                updateOpponentPartyForSlot(slot) { party ->
                    party.copy(
                        name = identityName(actor, party.name, party.species, species),
                        species = species,
                        types = types,
                        level = parsed.first,
                        gender = parsed.second,
                        hp = hp,
                        condition = status
                    )
                }
            }
            if (slot.endsWith('a')) {
                opponentPokemon = activeName
                opponentLevel = parsed.first
                opponentGender = parsed.second
                formHealth?.let {
                    opponentHp = it
                    opponentCondition = formCondition ?: opponentCondition
                }
                opponentDetails = opponentDetails.copy(
                    name = activeName,
                    species = species,
                    types = if (slot in terastallizedSlots) opponentDetails.types else baseTypes,
                    level = opponentLevel,
                    gender = opponentGender,
                    hp = opponentHp,
                    condition = opponentCondition
                )
            }
        }
        appendLog("${displayPokemonName(species)} changed form.")
    }

    private fun identityName(actor: String, currentName: String, currentSpecies: String, newSpecies: String): String {
        val identifier = actor.substringAfter(':').trim()
        return when {
            identifier.isBlank() -> newSpecies
            !identifier.equals(currentSpecies, true) -> identifier
            !currentName.equals(currentSpecies, true) -> currentName
            else -> newSpecies
        }
    }

    private fun applyTerastallize(fields: List<String>) {
        val actor = fields.getOrNull(2) ?: return
        val teraType = fields.getOrNull(3)?.uppercase()?.takeIf { it.isNotBlank() } ?: return
        val slot = actor.substringBefore(":").trim()
        terastallizedSlots += slot
        if (teraType == "STELLAR") teraTypesBySlot.remove(slot) else teraTypesBySlot[slot] = teraType
        typeChangeBySlot.remove(slot)
        typeAdditionsBySlot.remove(slot)
        val displayedTypes = if (teraType == "STELLAR") currentTypesForSlot(slot) else listOf(teraType)
        if (isPlayerSide(actor)) {
            playerActiveCombatants[slot]?.let {
                playerActiveCombatants[slot] = it.copy(types = displayedTypes)
                updatePlayerPartyMemberForSlot(slot, it.name) { details -> details.copy(types = displayedTypes) }
            }
            if (slot.endsWith('a')) updatePlayerDetails { it.copy(types = displayedTypes) }
        } else {
            opponentActiveCombatants[slot]?.let {
                opponentActiveCombatants[slot] = it.copy(types = displayedTypes)
                updateOpponentPartyForSlot(slot) { details -> details.copy(types = displayedTypes) }
            }
            if (slot.endsWith('a')) opponentDetails = opponentDetails.copy(types = displayedTypes)
        }
        appendLog("${actor.substringAfter(':').trim()} Terastallized into $teraType.")
    }

    private fun applyStart(fields: List<String>) {
        val actor = fields.getOrNull(2) ?: return
        val slot = actor.substringBefore(":").trim()
        val effect = battleEffectName(fields.getOrNull(3)).lowercase().filter(Char::isLetterOrDigit)
        when (effect) {
            "typechange" -> {
                if (slot in terastallizedSlots) return
                val types = battleTypes(fields.getOrNull(4))
                if (types.isEmpty()) return
                typeChangeBySlot[slot] = types
                typeAdditionsBySlot.remove(slot)
                updateActiveTypes(actor, types)
                if (!isSilent(fields)) appendLog("${battleActor(actor)}'s types changed to ${types.joinToString("/")}.")
            }
            "typeadd" -> {
                if (slot in terastallizedSlots) return
                val type = battleTypes(fields.getOrNull(4)).firstOrNull() ?: return
                val additions = typeAdditionsBySlot.getOrPut(slot) { mutableListOf() }
                if (type !in additions) additions += type
                updateActiveTypes(actor, effectiveTypes(slot))
                if (!isSilent(fields)) appendLog("${battleActor(actor)} gained the $type type.")
            }
            "dynamax" -> {
                updateDynamaxState(actor, true, fields.drop(4).any { it.equals("Gmax", true) })
                if (!isSilent(fields)) appendLog("${battleActor(actor)} Dynamaxed.")
            }
            else -> {
                updateVolatileEffect(actor, effect, true)
                if (!isSilent(fields) && !isHiddenAbilityStateEffect(effect)) {
                    appendLog("${battleActor(actor)}: ${battleEffectName(fields.getOrNull(3))} started.")
                }
            }
        }
    }

    private fun applyEnd(fields: List<String>) {
        val actor = fields.getOrNull(2) ?: return
        val slot = actor.substringBefore(":").trim()
        val effect = battleEffectName(fields.getOrNull(3)).lowercase().filter(Char::isLetterOrDigit)
        when (effect) {
            "typechange" -> {
                typeChangeBySlot.remove(slot)
                updateActiveTypes(actor, effectiveTypes(slot))
                if (!isSilent(fields)) appendLog("${battleActor(actor)}'s temporary types ended.")
            }
            "typeadd" -> {
                typeAdditionsBySlot.remove(slot)
                updateActiveTypes(actor, effectiveTypes(slot))
                if (!isSilent(fields)) appendLog("${battleActor(actor)}'s added type ended.")
            }
            "dynamax" -> {
                updateDynamaxState(actor, false)
                if (!isSilent(fields)) appendLog("${battleActor(actor)} returned to normal size.")
            }
            else -> {
                updateVolatileEffect(actor, effect, false)
                if (!isSilent(fields) && !isHiddenAbilityStateEffect(effect)) {
                    appendLog("${battleActor(actor)}: ${battleEffectName(fields.getOrNull(3))} ended.")
                }
            }
        }
    }

    private fun applyRequest(fields: List<String>) {
        val requestText = fields.getOrNull(2) ?: return
        teamPreviewOrder.clear()
        teamPreviewRequiredSize = 0
        requestNoCancel = false
        requestTargetable = true
        clearMoveOptions()
        availableGimmicks.clear()
        playerDetails = playerDetails.copy(moves = emptyList())
        activeRequests.clear()
        activeChoices.clear()
        autoPassActiveSlots.clear()
        revivingTeamIndices.clear()
        usedGimmickFamilies.clear()
        forceSwitchSlots.clear()
        forceSwitchChoices.clear()
        targetOptions.clear()
        choiceCanBeCancelled = false
        activeSlotIndex = 0
        requiredSwitches = 0
        selectedTargetIndex = -1
        requestId = null
        decisionAvailable = false
        decisionKind = DecisionKind.WAIT
        panel = Panel.MOVES
        status = "Waiting for a battle decision…"
        if (requestText.trim().equals("null", true)) {
            return
        }
        runCatching {
            val request = JSONObject(requestText)
            requestId = request.optInt("rqid", -1).takeIf { it >= 0 }
            requestNoCancel = request.optBoolean("noCancel")
            val activePositions = requestActivePositions(request)
            syncTeamFromRequest(request, activePositions)
            val requestType = request.optString("requestType").lowercase()
            if (request.optBoolean("wait") || requestType == "wait") {
                battlePhase = BattlePhase.BATTLE
                decisionKind = DecisionKind.WAIT
                decisionAvailable = false
                status = "Waiting for the other player…"
                return@runCatching
            }
            if (request.optBoolean("teamPreview") || requestType == "team") {
                battlePhase = BattlePhase.TEAM_PREVIEW
                decisionKind = DecisionKind.TEAM_PREVIEW
                val availableTeamSize = team.indices.count { !teamCondition(it).contains("FNT", true) }
                val requestedTeamSize = teamPreviewSize(request, availableTeamSize)
                teamPreviewRequiredSize = requestedTeamSize.coerceIn(1, maxOf(1, availableTeamSize))
                decisionAvailable = availableTeamSize > 0
                panel = Panel.TEAM
                focusFirstAvailableTeamChoice()
                status = teamPreviewPrompt()
                return@runCatching
            }
            val forceSwitch = request.optJSONArray("forceSwitch")
            battlePhase = BattlePhase.BATTLE
            forceSwitchSlots.clear()
            if (forceSwitch != null) {
                for (index in 0 until forceSwitch.length()) forceSwitchSlots += forceSwitch.optBoolean(index)
            }
            requiredSwitches = forceSwitchSlots.count { it }
            if (requiredSwitches > 0) {
                decisionKind = DecisionKind.SWITCH
                decisionAvailable = team.indices.any { canSwitchTo(it) }
                panel = Panel.TEAM
                focusFirstAvailableTeamChoice()
                status = if (requiredSwitches > 1) "Choose a Pokémon to switch in 1/$requiredSwitches" else "Choose a Pokémon to switch in"
                if (!spectatorMode && !decisionAvailable) submitAutomaticForcedSwitchPasses()
                return@runCatching
            }
            val active = request.optJSONArray("active") ?: run {
                decisionKind = DecisionKind.WAIT
                decisionAvailable = false
                status = "Waiting for a battle decision…"
                return@runCatching
            }
            for (index in 0 until active.length()) activeRequests += active.optJSONObject(index) ?: JSONObject()
            if (activeRequests.isEmpty()) {
                decisionKind = DecisionKind.WAIT
                decisionAvailable = false
                status = "Waiting for a battle decision…"
                return@runCatching
            }
            requestTargetable = request.optBoolean("targetable", activeRequests.size > 1)
            if (!prepareNextActiveRequest()) {
                if (!spectatorMode && !submitAutomaticActivePasses()) {
                    decisionKind = DecisionKind.WAIT
                    decisionAvailable = false
                    status = "Waiting for a battle decision…"
                }
            }
        }.onFailure {
            appendLog("Received an unreadable battle request.")
        }
    }

    private fun applySentChoice(fields: List<String>) {
        val choice = fields.drop(2).joinToString("|").trim()
        if (choice.isBlank() || requestId == null || battleFinished) return
        decisionAvailable = false
        choiceCanBeCancelled = !requestNoCancel
        selectedGimmick = null
        selectedTargetIndex = -1
        targetOptions.clear()
        activeChoices.clear()
        forceSwitchChoices.clear()
        activeSlotIndex = activeRequests.size
        status = "Choice sent. Waiting for the other player…"
    }

    private fun applyActiveRequest(active: JSONObject): Boolean {
        val requestMoves = active.optJSONArray("moves") ?: return false
        clearMoveOptions()
        for (index in 0 until requestMoves.length()) {
            val move = requestMoves.getJSONObject(index)
            val pp = move.optInt("pp", 0)
            val name = move.optString("move", "Move ${index + 1}")
            val moveInfo = moveInfoResolver?.invoke(name)
            val power = movePower(move, moveInfo)
            val accuracy = moveAccuracy(move, moveInfo?.accuracy)
            val category = move.optString("category").takeIf { it.isNotBlank() } ?: moveInfo?.category ?: "Status"
            val type = move.optString("type").uppercase().takeIf { it.isNotBlank() } ?: moveTypeResolver?.invoke(name) ?: "UNKNOWN"
            moves += MoveOption(
                name,
                type,
                pp,
                move.optInt("maxpp", pp),
                category,
                power.value,
                accuracy.value,
                protocolFlag(move.opt("disabled")) || (move.has("pp") && pp <= 0),
                move.optString("target")
            )
            moveResolutionSources += MoveResolutionSources(
                typeFromRequest = move.has("type") && move.optString("type").isNotBlank(),
                powerFromRequest = power.fromRequest,
                accuracyFromRequest = accuracy.fromRequest,
                categoryFromRequest = move.has("category") && move.optString("category").isNotBlank()
            )
        }
        val requestMoveNames = moves.map { it.name }
        var usedStruggleFallback = false
        if (moves.isNotEmpty() && moves.none { !it.disabled } && moves.none { it.name.equals("Struggle", true) }) {
            usedStruggleFallback = true
            val struggleInfo = moveInfoResolver?.invoke("Struggle")
            moves.clear()
            moveResolutionSources.clear()
            moves += MoveOption(
                name = "Struggle",
                type = moveTypeResolver?.invoke("Struggle") ?: "NORMAL",
                pp = 0,
                maxPp = 0,
                category = struggleInfo?.category ?: "Physical",
                power = struggleInfo?.power ?: "50",
                accuracy = struggleInfo?.accuracy ?: "—",
                target = "randomNormal"
            )
            moveResolutionSources += MoveResolutionSources()
        }
        val baseMoves = moves.toList()
        if (!usedStruggleFallback) {
            val zMoves = active.optJSONArray("zMoves") ?: active.optJSONArray("canZMove")
            if (zMoves != null) {
                baseMoves.forEachIndexed { index, base ->
                    val parsed = parseMoveVariant(zMoves.opt(index), base, MoveVariantKind.Z_POWER)
                    zMoveVariants += parsed?.variant
                    zMoveResolutionSources += parsed?.sources
                }
            }
            val maxMoves = active.optJSONArray("maxMoves")
                ?: active.optJSONObject("maxMoves")?.optJSONArray("maxMoves")
            if (maxMoves != null) {
                baseMoves.forEachIndexed { index, base ->
                    val parsed = parseMoveVariant(maxMoves.opt(index), base, MoveVariantKind.DYNAMAX)
                    maxMoveVariants += parsed?.variant
                    maxMoveResolutionSources += parsed?.sources
                }
            }
        }
        focusedMove = 0
        selectedTargetIndex = -1
        decisionKind = DecisionKind.MOVE
        decisionAvailable = moves.isNotEmpty()
        panel = Panel.MOVES
        status = if (activeRequests.size > 1) {
            "Choose a move for active Pokémon ${activeSlotIndex + 1}/${activeRequests.size}"
        } else {
            "Choose a move"
        }
        if (requestMoveNames.size != 1 || !requestMoveNames.single().equals("Struggle", true)) {
            updateMoveDetailsForActiveSlot(requestMoveNames)
        }
        updateAvailableGimmicks(if (usedStruggleFallback) JSONObject() else active)
        updateTargetOptions()
        return moves.isNotEmpty()
    }

    private fun updateMoveDetailsForActiveSlot(moveNames: List<String>) {
        val slot = playerSlotForRequestIndex(activeSlotIndex)
        val partyIndex = playerActivePartyIndices[slot]
            ?: playerActiveCombatants[slot]?.let { combatant ->
                teamDetails.indexOfFirst { it.matchesIdentifier(combatant.name) }.takeIf { it >= 0 }
            }
        if (partyIndex != null && partyIndex in teamDetails.indices) {
            val updated = teamDetails[partyIndex].copy(moves = moveNames)
            teamDetails[partyIndex] = updated
            if (slot.endsWith('a')) playerDetails = updated
        } else if (activeSlotIndex == 0) {
            playerDetails = playerDetails.copy(moves = moveNames)
        }
    }

    private fun playerSlotForRequestIndex(index: Int) =
        "$playerSlot${('a'.code + index).toChar()}"

    private fun movePower(move: JSONObject, info: MoveInfo?): ParsedMoveMetric {
        val value = move.opt("basePower")
        if (move.has("basePower") && value != null && value != JSONObject.NULL) {
            return ParsedMoveMetric(numericMoveValue(value.toString()) ?: "—", true)
        }
        return ParsedMoveMetric(numericMoveValue(info?.power) ?: "—", false)
    }

    private fun moveAccuracy(move: JSONObject, fallback: String?): ParsedMoveMetric {
        val value = move.opt("accuracy")
        val explicit = move.has("accuracy") && value != null && value != JSONObject.NULL
        val displayedValue = if (explicit) value.toString() else fallback
        return ParsedMoveMetric(numericMoveValue(displayedValue) ?: "—", explicit)
    }

    private fun displayedMoves(): List<MoveOption> {
        val variants = when (selectedGimmick) {
            BattleGimmick.Z_POWER -> zMoveVariants
            BattleGimmick.DYNAMAX -> maxMoveVariants
            else -> emptyList()
        }
        if (variants.isEmpty()) return moves.toList()
        return moves.mapIndexed { index, base ->
            val variant = variants.getOrNull(index)
            if (variant == null) {
                base.copy(disabled = true)
            } else {
                base.copy(
                    name = variant.name,
                    type = variant.type,
                    category = variant.category,
                    power = variant.power,
                    accuracy = variant.accuracy,
                    disabled = variant.disabled,
                    target = variant.target
                )
            }
        }
    }

    private fun parseMoveVariant(value: Any?, base: MoveOption, kind: MoveVariantKind): ParsedMoveVariant? {
        val move = value as? JSONObject ?: return null
        val name = move.optString("move").ifBlank { move.optString("name") }.ifBlank { base.name }
        val info = moveInfoResolver?.invoke(name)
        val power = movePower(move, null)
        val accuracy = moveAccuracy(move, info?.accuracy)
        val category = move.optString("category").takeIf { it.isNotBlank() } ?: base.category
        val type = move.optString("type").uppercase().takeIf { it.isNotBlank() } ?: base.type
        return ParsedMoveVariant(
            MoveVariant(
                name,
                type,
                category,
                if (power.fromRequest) power.value else variantPower(base, kind, type, info),
                accuracy.value,
                base.disabled || move.optBoolean("disabled"),
                move.optString("target").ifBlank { base.target }
            ),
            MoveResolutionSources(
                typeFromRequest = move.has("type") && move.optString("type").isNotBlank(),
                powerFromRequest = power.fromRequest,
                accuracyFromRequest = accuracy.fromRequest,
                categoryFromRequest = move.has("category") && move.optString("category").isNotBlank()
            )
        )
    }

    private fun resolveVariantInfo(
        variant: MoveVariant,
        sources: MoveResolutionSources,
        base: MoveOption?,
        kind: MoveVariantKind,
        resolver: (String) -> MoveInfo?
    ): MoveVariant {
        val info = resolver(variant.name)
        return variant.copy(
            power = if (sources.powerFromRequest) variant.power else base?.let { variantPower(it, kind, variant.type, info) } ?: variant.power,
            accuracy = if (sources.accuracyFromRequest) variant.accuracy else info?.accuracy ?: variant.accuracy,
            category = if (sources.categoryFromRequest) variant.category else base?.category ?: variant.category
        )
    }

    private fun resolveVariantType(
        variant: MoveVariant,
        sources: MoveResolutionSources,
        base: MoveOption?,
        kind: MoveVariantKind,
        resolver: (String) -> String?
    ): MoveVariant {
        val resolved = if (sources.typeFromRequest) {
            variant
        } else {
            variant.copy(type = resolver(variant.name) ?: base?.type ?: variant.type)
        }
        val info = moveInfoResolver?.invoke(resolved.name)
        return resolved.copy(
            category = if (sources.categoryFromRequest) resolved.category else base?.category ?: resolved.category,
            power = if (sources.powerFromRequest) resolved.power else base?.let { variantPower(it, kind, resolved.type, info) } ?: resolved.power
        )
    }

    private fun variantPower(base: MoveOption, kind: MoveVariantKind, type: String, info: MoveInfo?): String {
        if (info?.fixedGimmickPower == true) return info.power
        return derivedVariantPower(base, kind, type)
    }

    private fun derivedVariantPower(base: MoveOption, kind: MoveVariantKind, type: String = base.type): String {
        val power = base.power.toIntOrNull() ?: return "—"
        return when (kind) {
            MoveVariantKind.Z_POWER -> when {
                power >= 140 -> "200"
                power >= 130 -> "195"
                power >= 120 -> "190"
                power >= 110 -> "185"
                power >= 100 -> "180"
                power >= 90 -> "175"
                power >= 80 -> "160"
                power >= 70 -> "140"
                power >= 60 -> "120"
                else -> "100"
            }
            MoveVariantKind.DYNAMAX -> {
                val fightingOrPoison = type == "FIGHTING" || type == "POISON"
                if (fightingOrPoison) {
                    when {
                        power >= 150 -> "100"
                        power >= 110 -> "95"
                        power >= 75 -> "90"
                        power >= 65 -> "85"
                        power >= 55 -> "80"
                        power >= 45 -> "75"
                        else -> "70"
                    }
                } else {
                    when {
                        power >= 150 -> "150"
                        power >= 110 -> "140"
                        power >= 75 -> "130"
                        power >= 65 -> "120"
                        power >= 55 -> "110"
                        power >= 45 -> "100"
                        else -> "90"
                    }
                }
            }
        }
    }

    private fun numericMoveValue(value: String?): String? {
        val number = value?.trim()?.toDoubleOrNull() ?: return null
        if (!number.isFinite() || number <= 0) return null
        return number.toString().removeSuffix(".0")
    }

    private fun prepareNextActiveRequest(): Boolean {
        while (activeSlotIndex < activeRequests.size) {
            if (activeSlotIndex in autoPassActiveSlots) {
                ensureActiveChoiceSlots()
                activeChoices[activeSlotIndex] = "pass"
                activeSlotIndex += 1
                continue
            }
            if (applyActiveRequest(activeRequests[activeSlotIndex])) return true
            ensureActiveChoiceSlots()
            activeChoices[activeSlotIndex] = "pass"
            activeSlotIndex += 1
        }
        decisionKind = DecisionKind.WAIT
        decisionAvailable = false
        status = "Waiting for a battle decision…"
        return false
    }

    private fun submitAutomaticActivePasses(): Boolean {
        if (spectatorMode) return false
        if (activeRequests.isEmpty() || activeChoices.size < activeRequests.size || activeChoices.any { it != "pass" }) return false
        val choice = "/choose ${activeChoices.joinToString(", ")}${requestId?.let { "|$it" } ?: ""}"
        decisionAvailable = false
        choiceCanBeCancelled = false
        decisionKind = DecisionKind.WAIT
        status = "Choice sent. Waiting for the other player…"
        chatMessages += "[You] $choice"
        if (chatMessages.size > 32) chatMessages.removeAt(0)
        decisionListeners.toList().forEach { it.onDecision(choice) }
        return true
    }

    private fun protocolFlag(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is String -> value.isNotBlank() && !value.equals("false", true) && !value.equals("hidden", true)
        else -> false
    }

    private fun ensureActiveChoiceSlots() {
        while (activeChoices.size < activeRequests.size) activeChoices += ""
    }

    private fun parseBattleClock(message: String): BattleClock? {
        val match = Regex(
            "Time left:\\s*(\\d+)\\s*sec this turn\\s*\\|\\s*(\\d+)\\s*sec total\\s*\\|\\s*(\\d+)\\s*sec grace",
            RegexOption.IGNORE_CASE
        ).find(message) ?: return null
        return BattleClock(
            turnSeconds = match.groupValues[1].toInt(),
            totalSeconds = match.groupValues[2].toInt(),
            graceSeconds = match.groupValues[3].toInt()
        )
    }

    private fun applyWin(fields: List<String>) {
        fields.getOrNull(2)?.let {
            finishBattle("$it won the battle.")
        }
    }

    private fun applyTie(fields: List<String>) {
        val reason = fields.drop(2).joinToString("|").ifBlank { "The battle ended." }
        finishBattle(reason)
    }

    private fun finishBattle(result: String) {
        clearBattleClock()
        terminalBattleResult = result
        status = result
        appendLog(result)
        decisionAvailable = false
        choiceCanBeCancelled = false
        decisionKind = DecisionKind.WAIT
        requestId = null
        selectedGimmick = null
        teamPreviewOrder.clear()
        teamPreviewRequiredSize = 0
        activeRequests.clear()
        activeChoices.clear()
        autoPassActiveSlots.clear()
        revivingTeamIndices.clear()
        usedGimmickFamilies.clear()
        forceSwitchChoices.clear()
        forceSwitchSlots.clear()
        targetOptions.clear()
        activeSlotIndex = 0
        requiredSwitches = 0
        selectedTargetIndex = -1
        battleFinished = true
        battlePhase = BattlePhase.FINISHED
    }

    private fun applyBattleError(fields: List<String>) {
        val message = fields.drop(2).joinToString("|").ifBlank { "The server rejected that choice." }
        appendLog(message)
        if (battleFinished || requestId == null || decisionKind == DecisionKind.WAIT) return
        if (decisionKind == DecisionKind.MOVE && activeRequests.size > 1) {
            activeChoices.clear()
            usedGimmickFamilies.clear()
            activeSlotIndex = 0
            choiceCanBeCancelled = false
            prepareNextActiveRequest()
            return
        }
        if (decisionKind == DecisionKind.SWITCH) {
            forceSwitchChoices.clear()
            decisionAvailable = true
            panel = Panel.TEAM
            status = if (requiredSwitches > 1) {
                "Choose a Pokémon to switch in 1/$requiredSwitches"
            } else {
                "Choose a Pokémon to switch in"
            }
            return
        }
        decisionAvailable = true
        choiceCanBeCancelled = false
        selectedGimmick = null
        selectedTargetIndex = -1
        panel = when (decisionKind) {
            DecisionKind.MOVE -> Panel.MOVES
            DecisionKind.SWITCH, DecisionKind.TEAM_PREVIEW -> Panel.TEAM
            DecisionKind.WAIT -> panel
        }
        status = when (decisionKind) {
            DecisionKind.MOVE -> "Choose a move"
            DecisionKind.SWITCH -> "Choose a Pokémon to switch in"
            DecisionKind.TEAM_PREVIEW -> teamPreviewPrompt()
            DecisionKind.WAIT -> message
        }
    }

    private fun applyHitModifier(fields: List<String>, superEffective: Boolean = false, resisted: Boolean = false, critical: Boolean = false) {
        val target = fields.getOrNull(2)?.substringAfter(':')?.trim()?.takeIf { it.isNotBlank() } ?: return
        if (pendingHit != null && pendingHit?.target != target) publishPendingHit()
        val hit = pendingHit ?: PendingHit(target, target).also { pendingHit = it }
        hit.superEffective = hit.superEffective || superEffective
        hit.resisted = hit.resisted || resisted
        hit.critical = hit.critical || critical
        when {
            critical -> appendLog("A critical hit!")
            superEffective -> appendLog("It's super effective!")
            resisted -> appendLog("It's not very effective.")
        }
    }

    private fun publishPendingHit() {
        val hit = pendingHit ?: return
        pendingHit = null
        val impact = when {
            hit.superEffective && hit.critical -> HitImpact.SUPER_EFFECTIVE_CRITICAL
            hit.critical -> HitImpact.CRITICAL
            hit.superEffective -> HitImpact.SUPER_EFFECTIVE
            hit.resisted -> HitImpact.RESISTED
            else -> HitImpact.NORMAL
        }
        publishFeedback(BattleFeedback(FeedbackType.HIT, target = hit.target, impact = impact))
    }

    private fun applyChat(fields: List<String>) {
        val parsed = when (fields.getOrNull(1)) {
            "c:" -> fields.getOrNull(3)?.let { fields.getOrNull(4)?.let { message -> fields[3] to message } }
            "pm" -> fields.getOrNull(2)?.let { fields.getOrNull(4)?.let { message -> "PM $it" to message } }
            else -> fields.getOrNull(2)?.let { fields.getOrNull(3)?.let { message -> it to message } }
        } ?: return
        val message = "[${parsed.first}] ${parsed.second}"
        chatMessages += message
        if (chatMessages.size > 32) chatMessages.removeAt(0)
        appendActivity(message, ActivityOrigin.CHAT)
    }

    private fun applyCant(fields: List<String>) {
        val actorId = fields.getOrNull(2) ?: return
        clearMoveEffects(actorId)
        val actor = battleActor(actorId)
        val reason = battleEffectName(fields.getOrNull(3)).ifBlank { "that status" }
        appendLog("$actor couldn't move because of $reason.")
    }

    private fun applyBlock(fields: List<String>) {
        val actor = fields.getOrNull(2) ?: return
        val effect = battleEffectName(fields.getOrNull(3))
        val normalizedEffect = effect.lowercase().filter(Char::isLetterOrDigit)
        if (normalizedEffect in setOf("quickguard", "wideguard", "craftyshield", "protect")) {
            updateSingleBattleEffect(actor, normalizedEffect, turnScoped = true)
        }
        val item = when (effect.lowercase()) {
            "safety goggles" -> "Safety Goggles"
            "protective pads" -> "Protective Pads"
            "ability shield" -> "Ability Shield"
            else -> null
        }
        item?.let { revealedItem ->
            updateActorDetails(actor) { details -> details.copy(item = revealedItem) }
        }
        if (!isSilent(fields)) appendLog("${battleActor(actor)} was blocked by $effect.")
    }

    private fun applyAbility(fields: List<String>) {
        val actor = fields.getOrNull(2) ?: return
        val rawAbility = fields.getOrNull(3)?.takeIf(String::isNotBlank) ?: return
        val ability = abilityNameResolver?.invoke(rawAbility) ?: rawAbility
        updateActorDetails(actor) { it.copy(ability = ability) }
        if (isSilent(fields)) return
        val previousAbility = fields.drop(4)
            .firstOrNull { !it.trim().startsWith("[") && it.isNotBlank() }
            ?.let { abilityNameResolver?.invoke(it) ?: it }
        appendLog(
            when {
                previousAbility != null -> "${battleActor(actor)}'s ability changed from $previousAbility to $ability."
                protocolSource(fields) != null -> "${battleActor(actor)}'s ability became $ability."
                else -> "${battleActor(actor)}'s $ability activated."
            }
        )
    }

    private fun applyEndAbility(fields: List<String>) {
        val actor = fields.getOrNull(2) ?: return
        updateActorDetails(actor) { it.copy(ability = "Suppressed") }
        appendLog("${battleActor(actor)}'s ability was suppressed.")
    }

    private fun applyActivate(fields: List<String>) {
        val actor = fields.getOrNull(2).orEmpty()
        val hasActor = fields.size > 3 && actor.contains(":")
        val rawEffect = fields.getOrNull(if (hasActor) 3 else 2).orEmpty()
        val effect = battleEffectName(rawEffect).ifBlank { "an effect" }
        if (hasActor && rawEffect.substringBefore(":").equals("ability", true)) {
            rawEffect.substringAfter(":", "").trim().takeIf { it.isNotBlank() }?.let { ability ->
                updateActorDetails(actor) { details ->
                    details.copy(ability = abilityNameResolver?.invoke(ability) ?: ability)
                }
            }
        }
        if (hasActor && effect.equals("Baton Pass", true)) {
            val slot = targetSlot(actor)
            pendingBatonPassBySide[sideForSlot(slot)] = slot
        }
        appendLog(if (hasActor) "${battleActor(actor)} activated $effect." else "$effect activated.")
    }

    private fun applyItem(fields: List<String>, replacement: String? = null) {
        val actor = fields.getOrNull(2) ?: return
        val rawItem = replacement ?: fields.getOrNull(3)?.takeIf(String::isNotBlank) ?: return
        val item = itemNameResolver?.invoke(rawItem) ?: rawItem
        updateActorDetails(actor) { it.copy(item = item) }
        if (replacement == null && !isSilent(fields)) {
            appendLog(
                if (protocolSource(fields) != null) "${battleActor(actor)} obtained $item."
                else "${battleActor(actor)}'s $item activated."
            )
        }
    }

    private fun applyGimmickFormChange(fields: List<String>, message: String) {
        fields.getOrNull(3)
            ?.takeIf(String::isNotBlank)
            ?.takeUnless { it.trim().startsWith("[") }
            ?.let { itemNameResolver?.invoke(it) ?: it }
            ?.let { applyItem(fields, it) }
        appendLog("${battleActor(fields.getOrNull(2))} $message")
    }

    private fun applyEndItem(fields: List<String>) {
        if (fields.size < 3) return
        val actor = fields[2]
        val item = fields.getOrNull(3)
            ?.let { itemNameResolver?.invoke(it) ?: it }
            ?.takeIf(String::isNotBlank)
            ?: "its item"
        val consumed = fields.drop(4).any { it.equals("[eat]", true) }
        updateActorDetails(actor) { it.copy(item = "No item") }
        if (!isSilent(fields)) {
            appendLog(
                if (consumed) "${battleActor(actor)} consumed $item."
                else "${battleActor(actor)} lost $item."
            )
        }
    }

    private fun applyEat(fields: List<String>) {
        val actor = fields.getOrNull(2) ?: return
        val item = fields.getOrNull(3)
            ?.let { itemNameResolver?.invoke(it) ?: it }
            ?.takeIf(String::isNotBlank)
            ?: "an item"
        updateActorDetails(actor) { it.copy(item = "No item") }
        appendLog("${battleActor(actor)} consumed $item.")
    }

    private fun applyWeather(fields: List<String>) {
        val upkeep = fields.drop(3).any { it.trim().equals("[upkeep]", true) }
        weather = fields.getOrNull(2)?.takeUnless { it.equals("none", true) }.orEmpty()
        if (upkeep) return
        appendLog(if (weather.isBlank()) "The weather cleared." else "The weather changed to $weather.")
    }

    private fun applyFieldEffect(fields: List<String>, enabled: Boolean) {
        val effect = battleEffectName(fields.getOrNull(2)).takeIf { it.isNotBlank() } ?: return
        if (effect.contains("Terrain", true)) {
            if (enabled) terrain = effect else if (terrain.equals(effect, true)) terrain = ""
        } else if (enabled) {
            if (effect !in fieldEffects) fieldEffects += effect
        } else {
            fieldEffects.removeAll { it.equals(effect, true) }
        }
        appendLog(if (enabled) "$effect began." else "$effect ended.")
    }

    private fun applySideCondition(fields: List<String>, enabled: Boolean) {
        val source = fields.getOrNull(2) ?: return
        val side = source.substringBefore(':').ifBlank { source }
        val rawEffect = fields.getOrNull(3)?.takeIf { it.isNotBlank() } ?: source.substringAfter(':', "")
        val effect = battleEffectName(rawEffect).substringBefore(" [").trim().ifBlank { return }
        val conditions = if (isPlayerSide(side)) playerSideConditions else opponentSideConditions
        if (enabled) {
            if (effect !in conditions) conditions += effect
        } else {
            conditions.removeAll { it.equals(effect, true) }
        }
        appendLog(if (enabled) "$effect started on ${if (isPlayerSide(side)) "your side" else "the opponent's side"}." else "$effect ended.")
    }

    private fun applyBoost(fields: List<String>, direction: Int) {
        val side = fields.getOrNull(2) ?: return
        val stat = fields.getOrNull(3)?.lowercase()?.takeIf { it in BOOST_STATS } ?: return
        val amount = fields.getOrNull(4)?.toIntOrNull() ?: return
        val boosts = boostSlots(side).getOrPut(targetSlot(side)) { mutableMapOf() }
        val delta = amount * direction
        updateBoost(boosts, stat, (boosts[stat] ?: 0) + delta)
        removeEmptyBoostSlot(side)
        refreshVisibleBoosts()
        if (!isSilent(fields) && delta != 0) appendLog(formatStatChange(side, stat, delta))
    }

    private fun applySetBoost(fields: List<String>) {
        val side = fields.getOrNull(2) ?: return
        val stat = fields.getOrNull(3)?.lowercase()?.takeIf { it in BOOST_STATS } ?: return
        val amount = fields.getOrNull(4)?.toIntOrNull() ?: return
        val boosts = boostSlots(side).getOrPut(targetSlot(side)) { mutableMapOf() }
        updateBoost(boosts, stat, amount)
        removeEmptyBoostSlot(side)
        refreshVisibleBoosts()
        if (!isSilent(fields)) appendLog("${battleActor(side)}'s ${statLabel(stat)} was set to $amount.")
    }

    private fun clearAllBoosts() {
        playerBoostsBySlot.clear()
        opponentBoostsBySlot.clear()
        playerBoosts.clear()
        opponentBoosts.clear()
        appendLog("All stat changes were reset.")
    }

    private fun clearBoosts(fields: List<String>) {
        val side = fields.getOrNull(2) ?: return
        boostSlots(side).remove(targetSlot(side))
        refreshVisibleBoosts()
        appendLog("${if (isPlayerSide(side)) "Your" else "The opponent's"} stat changes were reset.")
    }

    private fun clearNegativeBoosts(fields: List<String>, restored: Boolean = false) {
        val side = fields.getOrNull(2) ?: return
        boostSlots(side)[targetSlot(side)]?.let { boosts ->
            boosts.filterValues { it < 0 }.keys.toList().forEach(boosts::remove)
            removeEmptyBoostSlot(side)
            refreshVisibleBoosts()
        }
        if (!isSilent(fields)) {
            appendLog(
                if (restored) "${battleActor(side)} restored its lowered stats."
                else "${battleActor(side)}'s negative stat changes were removed."
            )
        }
    }

    private fun clearPositiveBoosts(fields: List<String>) {
        val side = fields.getOrNull(2) ?: return
        boostSlots(side)[targetSlot(side)]?.let { boosts ->
            boosts.filterValues { it > 0 }.keys.toList().forEach(boosts::remove)
            removeEmptyBoostSlot(side)
            refreshVisibleBoosts()
        }
        if (!isSilent(fields)) appendLog("${battleActor(side)}'s positive stat changes were removed.")
    }

    private fun copyBoosts(fields: List<String>) {
        val source = fields.getOrNull(2) ?: return
        val target = fields.getOrNull(3) ?: return
        val copied = boostSlots(source)[targetSlot(source)].orEmpty().toMap()
        val destination = boostSlots(target)
        if (copied.isEmpty()) destination.remove(targetSlot(target)) else destination[targetSlot(target)] = copied.toMutableMap()
        refreshVisibleBoosts()
        appendLog("${battleActor(target)} copied stat changes from ${battleActor(source)}.")
    }

    private fun invertBoosts(fields: List<String>) {
        val side = fields.getOrNull(2) ?: return
        boostSlots(side)[targetSlot(side)]?.let { boosts ->
            boosts.keys.toList().forEach { stat -> boosts[stat] = -(boosts[stat] ?: 0) }
            removeEmptyBoostSlot(side)
            refreshVisibleBoosts()
        }
        appendLog("${battleActor(side)}'s stat changes were inverted.")
    }

    private fun swapBoosts(fields: List<String>) {
        val first = fields.getOrNull(2) ?: return
        val second = fields.getOrNull(3) ?: return
        val firstMap = boostSlots(first)
        val secondMap = boostSlots(second)
        val firstSlot = targetSlot(first)
        val secondSlot = targetSlot(second)
        if (firstMap === secondMap && firstSlot == secondSlot) return
        val stats = fields.getOrNull(4)
            ?.split(',')
            ?.map(String::trim)
            ?.map(String::lowercase)
            ?.filter { it in BOOST_STATS }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: BOOST_STATS
        val firstBoosts = firstMap.getOrPut(firstSlot) { mutableMapOf() }
        val secondBoosts = secondMap.getOrPut(secondSlot) { mutableMapOf() }
        stats.forEach { stat ->
            val firstValue = firstBoosts[stat]
            val secondValue = secondBoosts[stat]
            if (secondValue == null) firstBoosts.remove(stat) else firstBoosts[stat] = secondValue
            if (firstValue == null) secondBoosts.remove(stat) else secondBoosts[stat] = firstValue
        }
        removeEmptyBoostSlot(first)
        removeEmptyBoostSlot(second)
        refreshVisibleBoosts()
        appendLog("${battleActor(first)} and ${battleActor(second)} swapped stat changes.")
    }

    private fun swapSideConditions() {
        val player = playerSideConditions.toList()
        playerSideConditions.clear()
        playerSideConditions += opponentSideConditions
        opponentSideConditions.clear()
        opponentSideConditions += player
        appendLog("The sides' conditions were swapped.")
    }

    private fun updateBoost(boosts: MutableMap<String, Int>, stat: String, value: Int) {
        val bounded = value.coerceIn(-6, 6)
        if (bounded == 0) boosts.remove(stat) else boosts[stat] = bounded
    }

    private fun boostSlots(actor: String) = if (isPlayerSide(actor)) playerBoostsBySlot else opponentBoostsBySlot

    private fun targetSlot(actor: String) = actor.substringBefore(':').trim()

    private fun sideForSlot(slot: String) = slot.dropLast(1)

    private fun removeEmptyBoostSlot(actor: String) {
        val slots = boostSlots(actor)
        val slot = targetSlot(actor)
        if (slots[slot].isNullOrEmpty()) slots.remove(slot)
    }

    private fun refreshVisibleBoosts() {
        fun visible(active: Map<String, ActiveCombatant>, slots: Map<String, MutableMap<String, Int>>): Map<String, Int> {
            val primarySlot = active.keys.firstOrNull { it.endsWith('a') }
            return primarySlot?.let { slots[it] }?.toMap()
                ?: slots.values.firstOrNull()?.toMap()
                ?: emptyMap()
        }
        playerBoosts.clear()
        playerBoosts.putAll(visible(playerActiveCombatants, playerBoostsBySlot))
        opponentBoosts.clear()
        opponentBoosts.putAll(visible(opponentActiveCombatants, opponentBoostsBySlot))
    }

    private fun battleActor(value: String?) = displayPokemonName(value.orEmpty().substringAfter(':').trim().ifBlank { "Pokémon" })

    private fun battleEffectName(value: String?) = value.orEmpty().substringAfter(": ").substringBefore(" [")

    private fun formatStatChange(actor: String, stat: String, delta: Int): String {
        val strength = when (kotlin.math.abs(delta)) {
            1 -> ""
            2 -> " sharply"
            else -> " drastically"
        }
        val verb = if (delta > 0) "rose$strength" else "fell${if (strength.isBlank()) "" else if (kotlin.math.abs(delta) == 2) " harshly" else " severely"}"
        return "${battleActor(actor)}'s ${statLabel(stat)} $verb."
    }

    private fun statLabel(stat: String) = when (stat.lowercase()) {
        "atk" -> "Attack"
        "def" -> "Defense"
        "spa" -> "Special Attack"
        "spd" -> "Special Defense"
        "spe" -> "Speed"
        "accuracy" -> "accuracy"
        "evasion" -> "evasiveness"
        else -> stat
    }

    private fun protocolSource(fields: List<String>): String? = fields.drop(4)
        .firstOrNull { it.trim().startsWith("[from]", true) }
        ?.substringAfter(']')
        ?.trim()
        ?.takeIf(String::isNotBlank)

    private fun activeSlotNumber(slot: String): Int? = slot.lastOrNull()
        ?.lowercaseChar()
        ?.takeIf { it in 'a'..'z' }
        ?.let { it.code - 'a'.code + 1 }

    private fun updatePlayerDetails(transform: (PokemonDetails) -> PokemonDetails) {
        val previous = playerDetails
        playerDetails = transform(previous)
        val primaryIndex = playerActivePartyIndices["${playerSlot}a"]
            ?: teamDetails.indexOfFirst {
                it.name.equals(previous.name, true) || it.species.equals(previous.species, true)
            }
        primaryIndex
            .takeIf { it >= 0 }
            ?.let { teamDetails[it] = playerDetails }
    }

    private fun updateActorDetails(actor: String, transform: (PokemonDetails) -> PokemonDetails) {
        val name = actor.substringAfter(':').trim()
        val slot = actor.substringBefore(':').trim()
        if (isPlayerSide(actor)) {
            val targetName = playerActiveCombatants[slot]?.name ?: name
            if (slot.endsWith('a') && playerDetails.matchesIdentifier(targetName)) {
                updatePlayerDetails(transform)
            } else {
                playerActivePartyIndices[slot]?.let { teamDetails[it] = transform(teamDetails[it]) } ?: updatePlayerPartyMember(targetName, transform)
            }
        } else {
            val targetName = opponentActiveCombatants[slot]?.name ?: name
            updateOpponentPartyForSlot(slot, transform)
            if (slot.endsWith('a') && opponentDetails.matchesIdentifier(targetName)) opponentDetails = transform(opponentDetails)
        }
    }

    private fun actorDetails(actor: String): PokemonDetails? {
        val slot = actor.substringBefore(":").trim()
        if (!actor.contains(":")) return null
        return if (isPlayerSide(actor)) {
            playerActiveCombatants[slot]?.let { combatant ->
                teamDetails.firstOrNull { it.matchesIdentifier(combatant.name) }
                    ?: playerDetails.takeIf { it.matchesIdentifier(combatant.name) }
            }
        } else {
            opponentActiveCombatants[slot]?.let { combatant ->
                opponentTeamDetails.firstOrNull { it.matchesIdentifier(combatant.name) }
                    ?: opponentDetails.takeIf { it.matchesIdentifier(combatant.name) }
            }
        }
    }

    private fun baseTypesFor(species: String, fallback: List<String>) =
        pokemonTypeResolver?.invoke(species)?.takeIf { it.isNotEmpty() } ?: fallback

    private fun battleTypes(value: String?): List<String> = value.orEmpty()
        .split('/', ',')
        .map { it.trim().uppercase() }
        .filter(String::isNotBlank)
        .distinct()

    private fun isSilent(fields: List<String>) = fields.drop(2).any { it.trim().equals("[silent]", true) }

    private fun hasProtocolSource(fields: List<String>) = fields.drop(4).any { it.trim().startsWith("[from]", true) }

    private fun protocolTarget(fields: List<String>, targetIndex: Int = 2) = fields.getOrNull(targetIndex)
        ?.substringAfter(':')
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    private fun healthUpdateTargetIndices(fields: List<String>): List<Int> {
        val indices = mutableListOf<Int>()
        var targetIndex = 2
        while (targetIndex + 1 < fields.size && isProtocolActor(fields[targetIndex]) && isHealthToken(fields[targetIndex + 1])) {
            indices += targetIndex
            targetIndex += 2
        }
        return indices
    }

    private fun isProtocolActor(value: String): Boolean {
        val actor = value.trim()
        return actor.contains(':') && actor.substringBefore(':').trim().matches(Regex("p\\d+[a-z]?"))
    }

    private fun isHealthToken(value: String): Boolean {
        val token = value.trim().substringBefore(' ')
        return token.contains('/') || token.endsWith('%') || token.equals("fnt", true) || token.toFloatOrNull() != null
    }

    private fun healthForActor(actor: String): String? {
        val slot = actor.substringBefore(':').trim()
        return if (isPlayerSide(actor)) {
            playerActiveCombatants[slot]?.hp ?: playerHp.takeIf { slot.endsWith('a') }
        } else {
            opponentActiveCombatants[slot]?.hp ?: opponentHp.takeIf { slot.endsWith('a') }
        }
    }

    private fun healthFractionOrNull(hp: String): Float? {
        if (hp.contains("fnt", true)) return 0f
        val value = hp.substringBefore(' ')
        if (value.endsWith('%')) return value.dropLast(1).toFloatOrNull()?.div(100f)
        if ('/' !in value) return null
        val values = value.split('/')
        val current = values.getOrNull(0)?.toFloatOrNull() ?: return null
        val maximum = values.getOrNull(1)?.toFloatOrNull() ?: return null
        if (maximum <= 0f) return 0f
        return (current / maximum).coerceIn(0f, 1f)
    }

    private fun effectiveTypes(slot: String): List<String> {
        val base = typeChangeBySlot[slot] ?: baseTypesBySlot[slot].orEmpty()
        return (base + typeAdditionsBySlot[slot].orEmpty()).distinct()
    }

    private fun updateActiveTypes(actor: String, types: List<String>) {
        val slot = actor.substringBefore(":").trim()
        val actorName = actor.substringAfter(':').trim()
        if (isPlayerSide(actor)) {
            val combatant = playerActiveCombatants[slot]
            val name = combatant?.name ?: actorName
            combatant?.let { playerActiveCombatants[slot] = it.copy(types = types) }
            updatePlayerPartyMemberForSlot(slot, name) { details -> details.copy(types = types) }
            if (slot.endsWith('a')) updatePlayerDetails { it.copy(types = types) }
        } else {
            val combatant = opponentActiveCombatants[slot]
            val name = combatant?.name ?: actorName
            combatant?.let { opponentActiveCombatants[slot] = it.copy(types = types) }
            updateOpponentPartyForSlot(slot) { details -> details.copy(types = types) }
            if (slot.endsWith('a')) opponentDetails = opponentDetails.copy(types = types)
        }
    }

    private fun updateDynamaxState(actor: String, dynamaxed: Boolean, gMaxed: Boolean = false) {
        val slot = actor.substringBefore(":").trim()
        val update = { combatant: ActiveCombatant -> combatant.copy(dynamaxed = dynamaxed, gMaxed = dynamaxed && gMaxed) }
        if (isPlayerSide(actor)) playerActiveCombatants[slot]?.let { playerActiveCombatants[slot] = update(it) }
        else opponentActiveCombatants[slot]?.let { opponentActiveCombatants[slot] = update(it) }
    }

    private fun updateVolatileEffect(actor: String, effect: String, started: Boolean) {
        val slot = actor.substringBefore(":").trim()
        val label = volatileEffectLabel(effect) ?: return
        val update = { combatant: ActiveCombatant ->
            val effects = combatant.volatileEffects.toMutableList()
            if (started) {
                if (label !in effects) effects += label
            } else {
                effects.removeAll { it == label }
            }
            combatant.copy(volatileEffects = effects)
        }
        if (isPlayerSide(actor)) playerActiveCombatants[slot]?.let { playerActiveCombatants[slot] = update(it) }
        else opponentActiveCombatants[slot]?.let { opponentActiveCombatants[slot] = update(it) }
    }

    private fun applySingleBattleEffect(fields: List<String>, turnScoped: Boolean) {
        val actor = fields.getOrNull(2).orEmpty()
        val effect = battleEffectName(fields.getOrNull(3)).lowercase().filter(Char::isLetterOrDigit)
        if (singleBattleEffectLabel(effect) == null) return
        val slot = actor.substringBefore(":").trim()
        if (effect == "roost" && "FLYING" !in currentTypesForSlot(slot)) return
        updateSingleBattleEffect(actor, effect, turnScoped)
        if (effect == "roost") removeFlyingType(actor)
        if (!isSilent(fields)) appendLog("${battleActor(actor)}: ${battleEffectName(fields.getOrNull(3))}.")
    }

    private fun updateSingleBattleEffect(actor: String, effect: String, turnScoped: Boolean) {
        val label = singleBattleEffectLabel(effect) ?: return
        val slot = actor.substringBefore(":").trim()
        val update = { combatant: ActiveCombatant ->
            if (turnScoped) {
                combatant.copy(turnEffects = (combatant.turnEffects + label).distinct())
            } else {
                combatant.copy(moveEffects = (combatant.moveEffects + label).distinct())
            }
        }
        if (isPlayerSide(actor)) playerActiveCombatants[slot]?.let { playerActiveCombatants[slot] = update(it) }
        else opponentActiveCombatants[slot]?.let { opponentActiveCombatants[slot] = update(it) }
    }

    private fun removeFlyingType(actor: String) {
        val slot = actor.substringBefore(":").trim()
        val types = currentTypesForSlot(slot).filterNot { it == "FLYING" }.ifEmpty { listOf("NORMAL") }
        updateActiveTypes(actor, types)
    }

    private fun restoreTypesAfterRoost(slot: String, playerSide: Boolean) {
        val combatant = (if (playerSide) playerActiveCombatants else opponentActiveCombatants)[slot] ?: return
        val types = teraTypesBySlot[slot]?.let(::listOf) ?: effectiveTypes(slot)
        updateActiveTypes("$slot: ${combatant.name}", types)
    }

    private fun clearTurnEffects() {
        playerActiveCombatants.keys.toList().forEach { slot ->
            playerActiveCombatants[slot]?.let {
                val roostActive = it.turnEffects.contains("Roost")
                playerActiveCombatants[slot] = it.copy(turnEffects = emptyList())
                if (roostActive) restoreTypesAfterRoost(slot, true)
            }
        }
        opponentActiveCombatants.keys.toList().forEach { slot ->
            opponentActiveCombatants[slot]?.let {
                val roostActive = it.turnEffects.contains("Roost")
                opponentActiveCombatants[slot] = it.copy(turnEffects = emptyList())
                if (roostActive) restoreTypesAfterRoost(slot, false)
            }
        }
    }

    private fun clearMoveEffects(actor: String) {
        val slot = actor.substringBefore(":").trim()
        if (isPlayerSide(actor)) {
            playerActiveCombatants[slot]?.let { playerActiveCombatants[slot] = it.copy(moveEffects = emptyList()) }
        } else {
            opponentActiveCombatants[slot]?.let { opponentActiveCombatants[slot] = it.copy(moveEffects = emptyList()) }
        }
    }

    private fun currentTypesForSlot(slot: String): List<String> {
        val activeTypes = if (isPlayerSide(slot)) {
            playerActiveCombatants[slot]?.types
        } else {
            opponentActiveCombatants[slot]?.types
        }
        return activeTypes ?: effectiveTypes(slot)
    }

    private fun singleBattleEffectLabel(effect: String): String? = when (effect) {
        "banefulbunker" -> "Baneful Bunker"
        "beakblast" -> "Beak Blast"
        "burningbulwark" -> "Burning Bulwark"
        "craftyshield" -> "Crafty Shield"
        "destinybond" -> "Destiny Bond"
        "endure" -> "Endure"
        "followme" -> "Follow Me"
        "focuspunch" -> "Focus Punch"
        "grudge" -> "Grudge"
        "helpinghand" -> "Helping Hand"
        "kingsshield" -> "King's Shield"
        "matblock" -> "Mat Block"
        "maxguard" -> "Max Guard"
        "obstruct" -> "Obstruct"
        "powder" -> "Powder"
        "protect" -> "Protect"
        "quickguard" -> "Quick Guard"
        "ragepowder" -> "Rage Powder"
        "roost" -> "Roost"
        "shelltrap" -> "Shell Trap"
        "silktrap" -> "Silk Trap"
        "spikyshield" -> "Spiky Shield"
        "spotlight" -> "Spotlight"
        "wideguard" -> "Wide Guard"
        else -> effect.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
    }

    private fun volatileEffectLabel(effect: String): String? {
        if (isHiddenAbilityStateEffect(effect)) return null
        return when (effect) {
            "aquaring" -> "Aqua Ring"
            "attract" -> "Attract"
            "autotomize" -> "Autotomize"
            "bind", "partiallytrapped", "wrap" -> "Bound"
            "confusion" -> "Confusion"
            "curse" -> "Curse"
            "disable" -> "Disable"
            "embargo" -> "Embargo"
            "encore" -> "Encore"
            "focusenergy" -> "Focus Energy"
            "foresight", "miracleeye" -> "Identified"
            "healblock" -> "Heal Block"
            "imprison" -> "Imprison"
            "ingrain" -> "Ingrain"
            "laserfocus" -> "Laser Focus"
            "leechseed" -> "Leech Seed"
            "lightscreen" -> "Light Screen"
            "luckychant" -> "Lucky Chant"
            "magnetrise" -> "Magnet Rise"
            "nightmare" -> "Nightmare"
            "perish0", "perish1", "perish2", "perish3", "perishsong" -> "Perish Song"
            "powertrick" -> "Power Trick"
            "safeguard" -> "Safeguard"
            "smackdown" -> "Smack Down"
            "slowstart" -> "Slow Start"
            "stockpile" -> "Stockpile"
            "substitute" -> "Substitute"
            "taunt" -> "Taunt"
            "telekinesis" -> "Telekinesis"
            "torment" -> "Torment"
            "yawn" -> "Drowsy"
            else -> effect.replaceFirstChar { it.uppercase() }
        }
    }

    private fun isHiddenAbilityStateEffect(effect: String): Boolean =
        effect.startsWith("fallen") || effect.startsWith("protosynthesis") || effect.startsWith("quarkdrive")

    private fun swapSlotState(oldSlot: String, newSlot: String) {
        fun <T> swap(map: MutableMap<String, T>) {
            val oldValue = map.remove(oldSlot)
            val newValue = map.remove(newSlot)
            oldValue?.let { map[newSlot] = it }
            newValue?.let { map[oldSlot] = it }
        }
        swap(baseTypesBySlot)
        swap(typeChangeBySlot)
        swap(typeAdditionsBySlot)
        swap(teraTypesBySlot)
        if (isPlayerSide(oldSlot)) swap(playerBoostsBySlot) else swap(opponentBoostsBySlot)
        if (isPlayerSide(oldSlot)) swap(playerActivePartyIndices) else swap(opponentActivePartyIndices)
        val oldTera = terastallizedSlots.remove(oldSlot)
        val newTera = terastallizedSlots.remove(newSlot)
        if (oldTera) terastallizedSlots += newSlot
        if (newTera) terastallizedSlots += oldSlot
        refreshVisibleBoosts()
    }

    private fun statusLabel(status: String) = when (status) {
        "BRN" -> "burned"
        "FRZ" -> "frozen"
        "PAR" -> "paralyzed"
        "PSN" -> "poisoned"
        "TOX" -> "badly poisoned"
        "SLP" -> "asleep"
        else -> status.lowercase()
    }

    private fun formatStatusAnnouncement(actor: String, status: String) = when (status) {
        "BRN" -> "${battleActor(actor)} was burned."
        "FRZ" -> "${battleActor(actor)} was frozen solid."
        "PAR" -> "${battleActor(actor)} is paralyzed! It may be unable to move."
        "PSN" -> "${battleActor(actor)} was poisoned."
        "TOX" -> "${battleActor(actor)} was badly poisoned."
        "SLP" -> "${battleActor(actor)} fell asleep."
        else -> "${battleActor(actor)} became ${statusLabel(status)}."
    }

    private fun updateAvailableGimmicks(active: JSONObject) {
        activeGMaxAvailable = active.optBoolean("gigantamax") ||
            active.optJSONObject("maxMoves")?.optBoolean("gigantamax") == true
        availableTeraType = when (val value = active.opt("canTerastallize")) {
            is String -> value.trim().takeUnless { it.equals("false", true) }.orEmpty()
            else -> ""
        }
        val updated = mutableListOf<BattleGimmick>()
        val zMoves = active.optJSONArray("zMoves") ?: active.optJSONArray("canZMove")
        if (zMoves != null && (0 until zMoves.length()).any { !zMoves.isNull(it) }) updated += BattleGimmick.Z_POWER
        if (hasProtocolFlag(active, "canMegaEvo")) updated += BattleGimmick.MEGA_EVOLUTION
        if (hasProtocolFlag(active, "canMegaEvoX")) updated += BattleGimmick.MEGA_EVOLUTION_X
        if (hasProtocolFlag(active, "canMegaEvoY")) updated += BattleGimmick.MEGA_EVOLUTION_Y
        if (hasProtocolFlag(active, "canUltraBurst")) updated += BattleGimmick.ULTRA_BURST
        if (hasProtocolFlag(active, "canDynamax")) updated += BattleGimmick.DYNAMAX
        if (availableTeraType.isNotBlank()) updated += BattleGimmick.TERASTALLIZATION
        availableGimmicks.clear()
        availableGimmicks += updated.filterNot { gimmickFamily(it) in usedGimmickFamilies }
        if (selectedGimmick !in availableGimmicks) selectedGimmick = null
    }

    private fun hasProtocolFlag(active: JSONObject, key: String): Boolean {
        return when (val value = active.opt(key)) {
            is Boolean -> value
            is String -> value.isNotBlank() && !value.equals("false", true)
            JSONObject.NULL, null -> false
            else -> true
        }
    }

    private fun gimmickFamily(gimmick: BattleGimmick) = when (gimmick) {
        BattleGimmick.MEGA_EVOLUTION, BattleGimmick.MEGA_EVOLUTION_X, BattleGimmick.MEGA_EVOLUTION_Y -> "mega"
        else -> gimmick.choiceSuffix
    }

    fun gimmickLabel(gimmick: BattleGimmick) = if (gimmick == BattleGimmick.DYNAMAX && activeGMaxAvailable) {
        "Gigantamax"
    } else {
        gimmick.label
    }

    private fun teamPreviewSize(request: JSONObject, availableTeamSize: Int): Int {
        request.optInt("maxChosenTeamSize", 0).takeIf { it > 0 }?.let { return it }
        request.optInt("chosenTeamSize", 0).takeIf { it > 0 }?.let { return it }
        protocolTeamPreviewSize.takeIf { it > 0 }?.let { return it }
        val pokemon = request.optJSONObject("side")?.optJSONArray("pokemon")
        val hasIllusion = pokemon != null && (0 until pokemon.length()).any { index ->
            val entry = pokemon.optJSONObject(index) ?: return@any false
            val ability = entry.optString("baseAbility").ifBlank { entry.optString("ability") }
            ability.filter(Char::isLetterOrDigit).equals("illusion", true)
        }
        return if (hasIllusion) availableTeamSize else defaultTeamPreviewSize()
    }

    private fun defaultTeamPreviewSize() = when (gameType.lowercase()) {
        "doubles" -> 2
        "triples", "rotation" -> 3
        else -> 1
    }

    private fun activeSlotCountForBattleType(): Int? = when (gameType.lowercase()) {
        "singles", "rotation" -> 1
        "doubles", "multi" -> 2
        "triples" -> 3
        else -> null
    }

    private fun updateTargetOptions() {
        targetOptions.clear()
        selectedTargetIndex = -1
        val move = displayedMoves().getOrNull(focusedMove) ?: return
        val freeForAll = isFreeForAllBattle()
        if ((!freeForAll && activeRequests.size <= 1) || !requestTargetable) return
        val target = move.target.lowercase()
        if (freeForAll) {
            if (target !in setOf("normal", "adjacentfoe", "adjacentally", "any")) return
            targetOptions += opponentActiveCombatants.values
                .filterNot { it.condition.contains("FNT", true) }
                .mapNotNull { combatant ->
                    freeForAllTargetLocation(combatant.slot)?.let { choice ->
                        TargetOption("Foe: ${displayPokemonName(combatant.name, combatant.species)}", choice)
                    }
                }
            return
        }
        val selfPosition = activeSlotIndex + 1
        val allyPositions = activeSlots(playerActiveCombatants, activeRequests.size)
        val foePositions = activeSlots(opponentActiveCombatants, activeRequests.size)
        fun isAdjacentAlly(position: Int) = kotlin.math.abs(position - selfPosition) <= 1
        fun isAdjacentFoe(position: Int): Boolean {
            val mirroredPosition = activeRequests.size + 1 - position
            return kotlin.math.abs(mirroredPosition - selfPosition) <= 1
        }
        fun targetOptions(positions: List<Int>, ally: Boolean, adjacent: Boolean): List<TargetOption> = positions
            .filter { !adjacent || if (ally) isAdjacentAlly(it) else isAdjacentFoe(it) }
            .filterNot { ally && it == selfPosition }
            .map { position ->
                val choice = if (ally) "-$position" else "+$position"
                val label = if (ally) "Ally $position" else "Foe $position"
                TargetOption(label + if (ally && position == selfPosition) " (self)" else "", choice)
            }
        val options = when (target) {
            "adjacentally" -> targetOptions(allyPositions, ally = true, adjacent = true)
            "adjacentallyorself" -> allyPositions
                .filter { isAdjacentAlly(it) }
                .map { position ->
                    val label = "Ally $position" + if (position == selfPosition) " (self)" else ""
                    TargetOption(label, "-$position")
                }
            "normal", "adjacentfoe" -> targetOptions(foePositions, ally = false, adjacent = true)
            "any" -> targetOptions(foePositions, ally = false, adjacent = false) +
                targetOptions(allyPositions, ally = true, adjacent = false)
            else -> emptyList()
        }
        targetOptions += options
    }

    private fun activeSlots(combatants: Map<String, ActiveCombatant>, fallbackCount: Int): List<Int> {
        if (combatants.isEmpty()) return (1..fallbackCount).toList()
        return combatants.values
            .filterNot { it.condition.contains("FNT", true) }
            .mapNotNull { activeSlotNumber(it.slot) }
            .distinct()
            .sorted()
    }

    private fun freeForAllTargetLocation(targetSlot: String): String? {
        val sourceSide = playerSlot.removePrefix("p").toIntOrNull() ?: return null
        val targetSide = targetSlot.substringBefore(':').removePrefix("p").takeWhile(Char::isDigit).toIntOrNull() ?: return null
        if (sourceSide == targetSide) return null
        val sourceHalf = (sourceSide - 1) % 2
        val targetHalf = (targetSide - 1) % 2
        val targetPosition = (targetSide - 1) / 2 + 1
        val location = if (sourceHalf == targetHalf) -targetPosition else targetPosition
        return if (location > 0) "+$location" else location.toString()
    }

    private fun applyBattleRoomPresence(fields: List<String>, joining: Boolean) {
        val user = parseBattleRoomUser(fields.getOrNull(2).orEmpty())
        if (user.name.isBlank()) return
        val presence = battleRoomPresenceLog ?: BattleRoomPresenceLog().also { battleRoomPresenceLog = it }
        val target = if (joining) presence.joins else presence.leaves
        val opposite = if (joining) presence.leaves else presence.joins
        if (joining && opposite.removeAll { it.id == user.id }) {
            refreshBattleRoomPresenceFallback(presence)
            return
        }
        target.removeAll { it.id == user.id }
        target += user
        refreshBattleRoomPresenceFallback(presence)
    }

    private fun applyBattleRoomRename(fields: List<String>) {
        val nextUser = parseBattleRoomUser(fields.getOrNull(2).orEmpty())
        val previousRaw = fields.getOrNull(3).orEmpty().trim()
        if (nextUser.name.isBlank() || previousRaw.isBlank()) return
        val previousUser = parseBattleRoomUser(previousRaw)
        if (nextUser.id == previousUser.id) return
        battleRoomPresenceLog?.let { presence ->
            if (renameBattleRoomPresenceUser(presence, previousUser.id, nextUser)) refreshBattleRoomPresenceFallback(presence)
        }
        removeBattleRoomFallback(battleRoomRenameFallback)
        battleRoomRenameFallback = "${nextUser.formatted} renamed from $previousRaw."
        battleRoomRenameFallback?.let(::appendLog)
    }

    private fun parseBattleRoomUser(raw: String): BattleRoomUser {
        val value = raw.trim()
        val group = value.firstOrNull()
            ?.takeUnless(Char::isLetterOrDigit)
            ?.toString()
            .orEmpty()
        val name = value.removePrefix(group).substringBefore('@')
        return BattleRoomUser(group, name)
    }

    private fun renameBattleRoomPresenceUser(
        presence: BattleRoomPresenceLog,
        previousId: String,
        nextUser: BattleRoomUser
    ): Boolean {
        var changed = false
        listOf(presence.joins, presence.leaves).forEach { users ->
            users.indices.forEach { index ->
                if (users[index].id == previousId && users[index] != nextUser) {
                    users[index] = nextUser
                    changed = true
                }
            }
        }
        return changed
    }

    private fun refreshBattleRoomPresenceFallback(presence: BattleRoomPresenceLog) {
        val nextEntry = buildBattleRoomPresenceEntry(presence)
        if (nextEntry == presence.fallbackEntry) return
        removeBattleRoomFallback(presence.fallbackEntry)
        presence.fallbackEntry = nextEntry.takeIf(String::isNotBlank)
        presence.fallbackEntry?.let(::appendLog)
    }

    private fun buildBattleRoomPresenceEntry(presence: BattleRoomPresenceLog): String = buildList {
        if (presence.joins.isNotEmpty()) add("${battleRoomUserList(presence.joins)} joined")
        if (presence.leaves.isNotEmpty()) add("${battleRoomUserList(presence.leaves)} left")
    }.joinToString("; ")

    private fun battleRoomUserList(users: List<BattleRoomUser>): String {
        val uniqueUsers = users.map(BattleRoomUser::formatted).distinct()
        return when {
            uniqueUsers.size == 1 -> uniqueUsers.first()
            uniqueUsers.size == 2 -> uniqueUsers.joinToString(" and ")
            uniqueUsers.size <= 6 -> uniqueUsers.dropLast(1).joinToString(", ") + ", and ${uniqueUsers.last()}"
            else -> uniqueUsers.take(5).joinToString(", ") + ", and ${uniqueUsers.size - 5} others"
        }
    }

    private fun removeBattleRoomFallback(entry: String?) {
        if (entry == null) return
        battleLog.indexOfLast { it == entry }
            .takeIf { it >= 0 }
            ?.let(battleLog::removeAt)
        activityMessages.indices.reversed().firstOrNull { index ->
            activityOrigins[index] == ActivityOrigin.PROTOCOL &&
                BattleFeedMessageIdentity.matches(activityMessages[index], entry)
        }?.let(::removeActivityAt)
        val collectedEvents = protocolEventCollector
        collectedEvents?.indices?.reversed()?.firstOrNull { index ->
            BattleFeedMessageIdentity.matches(collectedEvents[index], entry)
        }?.let(collectedEvents::removeAt)
        battleFeedEntriesCache.clear()
    }

    private fun appendLog(entry: String) {
        if (battleLog.lastOrNull() == entry) return
        battleFeedVisible = true
        battleLog += entry
        if (battleLog.size > 32) battleLog.removeAt(0)
        battleLogGeneration += 1L
        battleFeedEntriesCache.clear()
        nativeBattleLogPending = true
        appendActivity(entry, ActivityOrigin.PROTOCOL)
        protocolEventCollector?.add(entry) ?: run {
            latestBattleEvent = entry
            latestBattleEventAtNanos = System.nanoTime()
        }
    }

    private fun appendActivity(entry: String, origin: ActivityOrigin = ActivityOrigin.PROTOCOL) {
        if (activityMessages.lastOrNull()?.let { BattleFeedMessageIdentity.matches(it, entry) } == true) return
        val followsTail = activityMessages.isEmpty() || focusedMessage >= activityMessages.lastIndex
        activityMessages += entry
        activityOrigins += origin
        while (activityMessages.size > BATTLE_HISTORY_LIMIT) {
            activityMessages.removeAt(0)
            activityOrigins.removeAt(0)
            if (!followsTail) focusedMessage = (focusedMessage - 1).coerceAtLeast(0)
        }
        if (followsTail) focusedMessage = activityMessages.lastIndex.coerceAtLeast(0)
    }

    private fun appendNativeActivity(entry: String) {
        val fallbackIndex = (activityMessages.lastIndex downTo 0).firstOrNull { index ->
            activityOrigins[index] == ActivityOrigin.PROTOCOL && nativeMatchesProtocolFallback(activityMessages[index], entry)
        }
        fallbackIndex?.let(::removeActivityAt)
        appendActivity(entry, ActivityOrigin.NATIVE)
    }

    private fun nativeMatchesProtocolFallback(protocolEntry: String, nativeEntry: String): Boolean {
        if (BattleFeedMessageIdentity.matches(protocolEntry, nativeEntry)) return true
        val withoutOpponentPrefix = nativeEntry.replace(Regex("(?i)^the opposing\\s+"), "")
        return BattleFeedMessageIdentity.matches(protocolEntry, withoutOpponentPrefix)
    }

    private fun removeActivityAt(index: Int) {
        if (index !in activityMessages.indices) return
        activityMessages.removeAt(index)
        activityOrigins.removeAt(index)
        if (focusedMessage > index) focusedMessage -= 1
        else if (focusedMessage == index) focusedMessage = focusedMessage.coerceAtMost(activityMessages.lastIndex).coerceAtLeast(0)
    }

    private fun appendMarkup(value: String) {
        sanitizeMarkup(value)?.let(::appendLog)
    }

    private fun applyMarkup(key: String?, value: String) {
        val message = sanitizeMarkup(value) ?: return
        val previous = key?.let { markupEntries.put(it, message) }
        if (previous == message) return
        previous?.let { oldMessage ->
            battleLog.indexOfLast { it == oldMessage }.takeIf { it >= 0 }?.let(battleLog::removeAt)
            activityMessages.indexOfLast { it == oldMessage }.takeIf { it >= 0 }?.let(::removeActivityAt)
        }
        appendLog(message)
    }

    private fun sanitizeMarkup(value: String): String? {
        val message = value
            .replace(Regex("(?is)<script.*?</script>"), " ")
            .replace(Regex("<[^>]*>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&rarr;", "→")
            .replace("&larr;", "←")
            .replace("&ndash;", "–")
            .replace("&mdash;", "—")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([,.!?])"), "$1")
            .trim()
        return message.takeIf { it.isNotBlank() }
    }

    private fun sanitizeShowdownMarkup(value: String): String? {
        val message = value
            .replace(Regex("(?is)<script.*?</script>"), "")
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&rarr;", "→")
            .replace("&larr;", "←")
            .replace("&ndash;", "–")
            .replace("&mdash;", "—")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s+([,.!?])"), "$1")
            .trim()
        return message.takeIf { it.isNotBlank() }
    }

    private fun normalizedShowdownEntries(value: String): List<String> = ShowdownBattleLogFilter
        .visibleEntries(value.replace("**", ""))
        .mapNotNull(::sanitizeShowdownMarkup)

    private fun moveMoveFocus(horizontal: Int, vertical: Int) {
        if (moves.isEmpty()) return
        val direction = if (vertical != 0) vertical else horizontal
        focusMove((focusedMove + direction).coerceIn(0, moves.lastIndex))
    }

    private fun requiresTeamDecision() = decisionKind == DecisionKind.SWITCH || decisionKind == DecisionKind.TEAM_PREVIEW

    private fun redirectToTeamDecisionPanel(): Boolean {
        if (!requiresTeamDecision()) return false
        if (panel != Panel.TEAM) {
            panel = Panel.TEAM
            notifyListeners()
        }
        return true
    }

    private fun moveTeamFocus(horizontal: Int, vertical: Int) {
        if (team.isEmpty()) return
        val columns = SwitchTeamLayout.COLUMNS
        val rowCount = (team.size + columns - 1) / columns
        val row = (focusedTeam / columns + vertical).coerceIn(0, rowCount - 1)
        val column = (focusedTeam % columns + horizontal).coerceIn(0, columns - 1)
        focusedTeam = (row * columns + column).coerceIn(0, team.lastIndex)
        status = "Ready: ${teamDisplayName(focusedTeam)}"
        notifyListeners()
    }

    private fun moveMessageFocus(size: Int, vertical: Int) {
        if (size == 0 || vertical == 0) return
        focusedMessage = (focusedMessage + vertical).coerceIn(0, size - 1)
        status = "Message ${focusedMessage + 1} selected."
        notifyListeners()
    }

    private fun moveMenuFocus(horizontal: Int, vertical: Int) {
        val row = (focusedMenuItem / MENU_COLUMNS + vertical).coerceIn(0, (MENU_ITEM_COUNT - 1) / MENU_COLUMNS)
        val column = (focusedMenuItem % MENU_COLUMNS + horizontal).coerceIn(0, MENU_COLUMNS - 1)
        selectMenuItem((row * MENU_COLUMNS + column).coerceAtMost(MENU_ITEM_COUNT - 1))
    }

    private fun menuAction(index: Int) = when (index) {
        0 -> if (battleSearchActive) "Cancel battle search" else "Find a ${matchFormatDisplayLabel()}"
        1 -> "Battle format ${matchFormatMenuLabel()}"
        2 -> "Open battle chat"
        3 -> when {
            liveBattleActive && !replayMode && !spectatorMode && isBattleParticipant() -> "Forfeit"
            liveBattleActive && !replayMode -> "Leave battle"
            else -> "Challenge player"
        }
        4 -> "Sound effects ${if (soundEffectsEnabled) "on" else "off"}"
        5 -> "Background music ${if (musicEnabled) "on" else "off"}"
        6 -> "Haptics ${if (hapticsEnabled) "on" else "off"}"
        7 -> "PBR announcer ${if (announcerEnabled) "on" else "off"}"
        8 -> "Sprite style ${spriteStyle.displayName}"
        9 -> "Team library"
        10 -> "Rooms"
        11 -> "Showdown account"
        12 -> "Configure server"
        13 -> if (replayMode) "Replay controls" else "Battle controls"
        else -> if (battleFinished) "Save replay" else "Battle timer ${if (battleTimerEnabled) "on" else "off"}"
    }

    private fun applyMenuSelection() {
        if (focusedMenuItem == 0) {
            status = "Connecting to a ${matchFormatDisplayLabel()}…"
            publishClientAction(ClientAction.FIND_BATTLE)
        } else {
            status = when (focusedMenuItem) {
                1 -> {
                    publishClientAction(ClientAction.CHOOSE_FORMAT)
                    "Choose a battle format."
                }
                2 -> {
                    publishClientAction(ClientAction.OPEN_CHAT)
                    "Open battle chat."
                }
                3 -> {
                    if (liveBattleActive && !replayMode) {
                        if (!spectatorMode && isBattleParticipant()) {
                            publishClientAction(ClientAction.FORFEIT)
                            "Forfeit requires confirmation."
                        } else {
                            publishClientAction(ClientAction.LEAVE_BATTLE)
                            "Leave the spectated battle."
                        }
                    } else {
                        publishClientAction(ClientAction.CHALLENGE_PLAYER)
                        "Challenge another player."
                    }
                }
                4 -> {
                    soundEffectsEnabled = !soundEffectsEnabled
                    "Sound effects ${if (soundEffectsEnabled) "enabled." else "muted."}"
                }
                5 -> {
                    musicEnabled = !musicEnabled
                    "Background music ${if (musicEnabled) "enabled." else "muted."}"
                }
                6 -> {
                    hapticsEnabled = !hapticsEnabled
                    "Haptics ${if (hapticsEnabled) "enabled." else "disabled."}"
                }
                7 -> {
                    announcerEnabled = !announcerEnabled
                    "PBR announcer ${if (announcerEnabled) "enabled." else "disabled."}"
                }
                8 -> {
                    spriteStyle = SpriteStyle.MODERN_3D
                    "HD-first animated sprite style enabled."
                }
                9 -> {
                    publishClientAction(ClientAction.CONFIGURE_TEAM)
                    "Manage your saved teams."
                }
                10 -> {
                    publishClientAction(ClientAction.OPEN_ROOMS)
                    "Browse Showdown rooms."
                }
                11 -> {
                    publishClientAction(ClientAction.CONFIGURE_ACCOUNT)
                    "Configure your Showdown account."
                }
                12 -> {
                    publishClientAction(ClientAction.CONFIGURE_SERVER)
                    "Choose a Pokémon Showdown server."
                }
                13 -> {
                    publishClientAction(ClientAction.OPEN_REPLAY_CONTROLS)
                    "Adjust battle playback."
                }
                else -> {
                    if (battleFinished) {
                        publishClientAction(ClientAction.SAVE_REPLAY)
                        "Saving the battle replay."
                    } else {
                        publishClientAction(ClientAction.TOGGLE_BATTLE_TIMER)
                        "Battle timer ${if (battleTimerEnabled) "stopping" else "starting"}."
                    }
                }
            }
        }
        if (focusedMenuItem in 4..8) publishClientAction(ClientAction.SETTINGS_CHANGED)
    }

    private fun confirmTeamSelection() {
        if (!decisionAvailable || focusedTeam !in team.indices) {
            status = "No Pokémon can be selected."
            return
        }
        if (decisionKind == DecisionKind.TEAM_PREVIEW) {
            if (teamCondition(focusedTeam).contains("FNT", true)) {
                status = "That Pokémon has fainted."
                return
            }
            if (focusedTeam in teamPreviewOrder) {
                status = "${teamDisplayName(focusedTeam)} is already in the order."
                return
            }
            teamPreviewOrder += focusedTeam
            if (teamPreviewOrder.size < teamPreviewRequiredSize) {
                status = "Team order ${teamPreviewOrder.size}/$teamPreviewRequiredSize: choose the next Pokémon."
                return
            }
            completeTeamSelection(
                "/choose team ${teamPreviewOrder.joinToString("") { (it + 1).toString() }}${requestId?.let { "|$it" } ?: ""}"
            )
            return
        }
        if (decisionKind == DecisionKind.MOVE) {
            confirmMoveRequestSwitch()
            return
        }
        val choice = when (decisionKind) {
            DecisionKind.SWITCH -> {
                if (teamCondition(focusedTeam).contains("FNT", true) && focusedTeam !in revivingTeamIndices) {
                    status = "That Pokémon has fainted."
                    return
                }
                if (!canSwitchTo(focusedTeam)) {
                    status = "${teamDisplayName(focusedTeam)} is already active."
                    return
                }
                val switchChoice = "switch ${focusedTeam + 1}"
                if (switchChoice in forceSwitchChoices) {
                    status = "${teamDisplayName(focusedTeam)} is already selected."
                    return
                }
                if (requiredSwitches > 1) {
                    forceSwitchChoices += switchChoice
                    val remainingChoices = requiredSwitches - forceSwitchChoices.size
                    val availableSwitches = availableSwitchChoices().size
                    repeat((remainingChoices - availableSwitches).coerceAtLeast(0)) { forceSwitchChoices += "pass" }
                    if (forceSwitchChoices.size < requiredSwitches) {
                        focusFirstAvailableTeamChoice()
                        status = "Choose a Pokémon to switch in ${forceSwitchChoices.size + 1}/$requiredSwitches"
                        return
                    }
                    "/choose ${serializeForcedSwitchChoices()}${requestId?.let { "|$it" } ?: ""}"
                } else {
                    forceSwitchChoices += switchChoice
                    "/choose ${serializeForcedSwitchChoices()}${requestId?.let { "|$it" } ?: ""}"
                }
            }
            else -> {
                status = "Selected ${teamDisplayName(focusedTeam)}."
                return
            }
        }
        completeTeamSelection(choice)
    }

    private fun availableSwitchChoices() = team.indices
        .filter(::canSwitchTo)
        .map { "switch ${it + 1}" }
        .filterNot(forceSwitchChoices::contains)

    private fun teamDisplayName(index: Int): String {
        val name = team.getOrNull(index).orEmpty()
        return displayPokemonName(name, teamDetails.getOrNull(index)?.species ?: name)
    }

    private fun submitAutomaticForcedSwitchPasses() {
        if (spectatorMode) return
        if (requiredSwitches <= 0) return
        forceSwitchChoices.clear()
        repeat(requiredSwitches) { forceSwitchChoices += "pass" }
        val choice = "/choose ${serializeForcedSwitchChoices()}${requestId?.let { "|$it" } ?: ""}"
        decisionAvailable = false
        choiceCanBeCancelled = false
        status = "Choice sent. Waiting for the other player…"
        chatMessages += "[You] $choice"
        if (chatMessages.size > 32) chatMessages.removeAt(0)
        decisionListeners.toList().forEach { it.onDecision(choice) }
    }

    private fun serializeForcedSwitchChoices(): String {
        if (forceSwitchSlots.isEmpty()) return forceSwitchChoices.joinToString(", ")
        var selectedChoiceIndex = 0
        return forceSwitchSlots.joinToString(", ") { required ->
            if (!required) {
                "pass"
            } else {
                forceSwitchChoices.getOrNull(selectedChoiceIndex++) ?: "pass"
            }
        }
    }

    private fun matchFormatDisplayLabel(): String = ShowdownTeamLibraryQuery.displayFormat(
        matchFormat.id,
        availableMatchFormats
    )

    private fun matchFormatMenuLabel(): String = ShowdownFormatCompatibility.canonical(matchFormat).menuLabel

    private fun confirmMoveRequestSwitch() {
        val activeRequest = activeRequests.getOrNull(activeSlotIndex)
        if (activeRequest?.optBoolean("trapped") == true) {
            status = "${displayPokemonName(playerPokemon)} is trapped and cannot switch."
            notifyListeners()
            return
        }
        if (teamCondition(focusedTeam).contains("FNT", true) && focusedTeam !in revivingTeamIndices) {
            status = "That Pokémon has fainted."
            notifyListeners()
            return
        }
        if (!canSwitchTo(focusedTeam)) {
            status = "${teamDisplayName(focusedTeam)} is already active."
            notifyListeners()
            return
        }
        val selectedChoice = "switch ${focusedTeam + 1}"
        val isLastActiveChoice = activeRequests.size <= 1 || activeSlotIndex >= activeRequests.lastIndex
        val choiceMayNotBeCancelled = isLastActiveChoice && activeRequest?.optBoolean("maybeTrapped") == true
        if (activeRequests.size > 1) {
            ensureActiveChoiceSlots()
            activeChoices[activeSlotIndex] = selectedChoice
            selectedGimmick = null
            if (activeSlotIndex < activeRequests.lastIndex) {
                activeSlotIndex += 1
                if (prepareNextActiveRequest()) {
                    notifyListeners()
                    return
                }
            }
        }
        val selectedChoices = if (activeRequests.size > 1) activeChoices.joinToString(", ") else selectedChoice
        val choice = "/choose $selectedChoices${requestId?.let { "|$it" } ?: ""}"
        status = "Switch sent: ${teamDisplayName(focusedTeam)}"
        appendLog("${displayPokemonName(playerPokemon)} switched to ${teamDisplayName(focusedTeam)}.")
        chatMessages += "[You] $choice"
        if (chatMessages.size > 32) chatMessages.removeAt(0)
        decisionAvailable = false
        choiceCanBeCancelled = !requestNoCancel && !choiceMayNotBeCancelled
        selectedGimmick = null
        selectedTargetIndex = -1
        targetOptions.clear()
        decisionListeners.toList().forEach { it.onDecision(choice) }
        notifyListeners()
    }

    private fun submitShiftChoice() {
        ensureActiveChoiceSlots()
        activeChoices[activeSlotIndex] = "shift"
        selectedGimmick = null
        selectedTargetIndex = -1
        targetOptions.clear()
        if (activeSlotIndex < activeRequests.lastIndex) {
            activeSlotIndex += 1
            if (prepareNextActiveRequest()) return
        }
        val selectedChoices = if (activeRequests.size > 1) activeChoices.joinToString(", ") else "shift"
        val choice = "/choose $selectedChoices${requestId?.let { "|$it" } ?: ""}"
        status = "Shift sent."
        appendLog("${displayPokemonName(playerPokemon)} shifted position.")
        chatMessages += "[You] $choice"
        if (chatMessages.size > 32) chatMessages.removeAt(0)
        decisionAvailable = false
        choiceCanBeCancelled = !requestNoCancel
        decisionListeners.toList().forEach { it.onDecision(choice) }
    }

    private fun submitTestFight() {
        val previousChoices = activeChoices.take(activeSlotIndex).filter(String::isNotBlank)
        val serializedChoices = (previousChoices + "testfight").joinToString(", ")
        val choice = "/choose $serializedChoices${requestId?.let { "|$it" } ?: ""}"
        status = "Checking whether the move is locked…"
        decisionAvailable = false
        choiceCanBeCancelled = false
        selectedGimmick = null
        selectedTargetIndex = -1
        targetOptions.clear()
        decisionListeners.toList().forEach { it.onDecision(choice) }
    }

    private fun completeTeamSelection(choice: String) {
        decisionAvailable = false
        choiceCanBeCancelled = !requestNoCancel
        status = "Queued: ${teamDisplayName(focusedTeam)}"
        appendLog(if (choice.startsWith("/choose team")) "Team order submitted." else "${teamDisplayName(focusedTeam)} was selected.")
        chatMessages += "[You] $choice"
        if (chatMessages.size > 32) chatMessages.removeAt(0)
        decisionListeners.toList().forEach { it.onDecision(choice) }
    }

    private fun teamPreviewPrompt() = if (teamPreviewRequiredSize > 0) {
        "Choose $teamPreviewRequiredSize Pokémon in battle order"
    } else {
        "Confirm your team order"
    }

    private fun syncTeamFromRequest(request: JSONObject, activePositions: List<Int>) {
        val pokemon = request.optJSONObject("side")?.optJSONArray("pokemon") ?: return
        val requestPlayerSlot = pokemon.optJSONObject(0)
            ?.optString("ident")
            ?.substringBefore(':')
            ?.trim()
            ?.takeIf { it.matches(Regex("p\\d+")) }
        if (requestPlayerSlot != null && localUsername != null && sideNames.values.none { it.equals(localUsername, true) }) {
            if (playerSlot != requestPlayerSlot) {
                playerSlot = requestPlayerSlot
                updatePerspective()
            }
        }
        val synced = mutableListOf<PokemonDetails>()
        val identifiers = mutableListOf<String>()
        val previousActiveCombatants = playerActiveCombatants.toMap()
        val activeArray = request.optJSONArray("active")
        val forceSwitchArray = request.optJSONArray("forceSwitch")
        val hasAuthoritativeActiveSlots = activeArray != null || forceSwitchArray != null
        val activeSlotLimit = when {
            activeArray != null -> activePositions.size
            forceSwitchArray != null -> forceSwitchArray.length()
            else -> activeSlotCountForBattleType()
        }
        activeTeamNames.clear()
        activeSlotNames.clear()
        playerActivePartyIndices.clear()
        val hasRevivingActive = (0 until pokemon.length()).any { index ->
            pokemon.optJSONObject(index)?.let { entry ->
                entry.optBoolean("active") && entry.optBoolean("reviving")
            } == true
        }
        var activeEntryIndex = 0
        for (index in 0 until pokemon.length()) {
            val entry = pokemon.optJSONObject(index) ?: continue
            val details = entry.optString("details", entry.optString("ident").substringAfter(": "))
            val name = details.substringBefore(',').ifBlank { entry.optString("ident").substringAfter(": ", "Pokémon") }
            val species = details.substringBefore(',').ifBlank { name }
            val identifier = entry.optString("ident").substringAfter(':').trim().ifBlank { name }
            identifiers += identifier
            if (entry.optBoolean("active") && (activeSlotLimit == null || activeEntryIndex < activeSlotLimit)) {
                val slotPosition = activePositions.getOrNull(activeEntryIndex) ?: activeEntryIndex
                if (entry.optBoolean("commanding")) autoPassActiveSlots += slotPosition
                val slot = playerSlotForRequestIndex(slotPosition)
                activeSlotNames[slot] = identifier
                playerActivePartyIndices[slot] = index
                activeEntryIndex += 1
            }
            val known = teamDetails.firstOrNull {
                it.name.equals(identifier, true) || it.name.equals(name, true) || it.species.equals(species, true)
            }
            val levelGender = parseDetails(details)
            val condition = entry.optString("condition", "100/100")
            if (hasRevivingActive && !entry.optBoolean("active") && condition(condition).contains("FNT", true)) {
                revivingTeamIndices += index
            }
            val knownMoves = entry.optJSONArray("moves")?.let { moves ->
                buildList {
                    for (moveIndex in 0 until moves.length()) add(moves.optString(moveIndex))
                }
            } ?: known?.moves.orEmpty()
            val ability = entry.optString("baseAbility", known?.ability ?: "Unknown ability")
                .let { abilityNameResolver?.invoke(it) ?: it }
            val item = entry.optString("item", known?.item ?: "Unknown item")
                .ifBlank { "Unknown item" }
                .let { itemNameResolver?.invoke(it) ?: it }
            synced += PokemonDetails(
                identifier,
                resolvedTypes(species, known?.types.orEmpty()),
                levelGender.first,
                levelGender.second,
                condition,
                condition(condition),
                ability,
                item,
                knownMoves,
                known?.stats.orEmpty(),
                entry.optString("pokeball", known?.pokeball ?: "pokeball"),
                species,
                entry.optBoolean("shiny", known?.shiny ?: false)
            )
        }
        if (synced.isEmpty()) return
        team.clear()
        team += synced.map { it.name }
        playerPartyIdentifiers.clear()
        playerPartyIdentifiers += identifiers
        teamDetails.clear()
        teamDetails += synced
        activeTeamNames += activeSlotNames.values
        if (hasAuthoritativeActiveSlots) playerActiveCombatants.clear()
        if (activeSlotNames.isNotEmpty()) {
            activeSlotNames.forEach { (slot, identifier) ->
                playerPartyIdentifiers.indexOfFirst { it.equals(identifier, true) }
                    .takeIf { it >= 0 }
                    ?.let { synced[it] }
                    ?.let { details ->
                    val previous = previousActiveCombatants[slot]?.takeIf { it.name.equals(details.name, true) }
                    playerActiveCombatants[slot] = ActiveCombatant(
                        slot = slot,
                        name = details.name,
                        species = details.species,
                        types = previous?.types ?: details.types,
                        level = details.level,
                        gender = details.gender,
                        hp = details.hp,
                        condition = details.condition,
                        entryAtNanos = playerEntryAtNanos,
                        dynamaxed = previous?.dynamaxed ?: false,
                        gMaxed = previous?.gMaxed ?: false,
                        volatileEffects = previous?.volatileEffects.orEmpty(),
                        turnEffects = previous?.turnEffects.orEmpty(),
                        moveEffects = previous?.moveEffects.orEmpty(),
                        shiny = details.shiny
                    )
                }
            }
        }
        playerBoostsBySlot.keys.filterNot(activeSlotNames::containsKey).toList().forEach(playerBoostsBySlot::remove)
        refreshVisibleBoosts()
        focusedTeam = focusedTeam.coerceIn(0, team.lastIndex)
        val primarySlot = activeSlotNames.keys
            .sortedWith(compareBy<String> { !it.endsWith('a') }.thenBy { it })
            .firstOrNull()
        val primaryIndex = primarySlot?.let { playerActivePartyIndices[it] }
            ?: playerActivePartyIndices["${playerSlot}a"]
        (primaryIndex?.let { synced.getOrNull(it) } ?: synced.firstOrNull())?.let { details ->
            playerDetails = details
            playerPokemon = details.name
            playerHp = details.hp
            playerCondition = details.condition
            playerLevel = details.level
            playerGender = details.gender
        }
    }

    private fun requestActivePositions(request: JSONObject): List<Int> {
        request.optJSONArray("active")?.let { active ->
            val positions = (0 until active.length()).filter { index ->
                active.optJSONObject(index) != null
            }
            if (positions.isNotEmpty()) return positions
        }
        request.optJSONArray("forceSwitch")?.let { forceSwitch ->
            if (forceSwitch.length() > 0) return (0 until forceSwitch.length()).toList()
        }
        return emptyList()
    }

    private fun canSwitchTo(index: Int): Boolean {
        if (index !in team.indices || index in playerActivePartyIndices.values) return false
        val fainted = teamCondition(index).contains("FNT", true)
        return !fainted || index in revivingTeamIndices
    }

    private fun focusFirstAvailableTeamChoice() {
        val candidate = team.indices.firstOrNull { index ->
            when (decisionKind) {
                DecisionKind.TEAM_PREVIEW -> !teamCondition(index).contains("FNT", true) && index !in teamPreviewOrder
                DecisionKind.SWITCH -> canSwitchTo(index) && "switch ${index + 1}" !in forceSwitchChoices
                else -> false
            }
        }
        if (candidate != null) focusedTeam = candidate
    }

    private fun rebuildOpponentPartyIdentifiers() {
        opponentPartyIdentifiers.clear()
        opponentTeamDetails.forEachIndexed { index, details ->
            details.name.takeIf(String::isNotBlank)?.let { opponentPartyIdentifiers[it] = index }
        }
        opponentTeamDetails
            .groupBy { it.species.lowercase() }
            .filterValues { it.size == 1 }
            .values
            .map { it.single() }
            .forEach { details ->
                opponentTeamDetails.indexOf(details).takeIf { it >= 0 }?.let { opponentPartyIdentifiers[details.species] = it }
            }
    }

    private fun findOpponentPartyIndex(identifier: String, species: String, slot: String): Int {
        opponentPartyIdentifiers.entries.firstOrNull { it.key.equals(identifier, true) }
            ?.value
            ?.takeIf { it in opponentTeamDetails.indices }
            ?.let { return it }
        opponentTeamDetails.indexOfFirst { it.name.equals(identifier, true) }
            .takeIf { it >= 0 }
            ?.let { return it }
        val occupiedByAnotherSlot = opponentActivePartyIndices
            .filterKeys { it != slot }
            .values
            .toSet()
        return opponentTeamDetails.indices.firstOrNull { index ->
            index !in opponentPartyIdentifiers.values &&
                index !in occupiedByAnotherSlot &&
                opponentTeamDetails[index].species.equals(species, true)
        } ?: -1
    }

    private fun unknownOpponentDetails(
        species: String,
        levelGender: Pair<String, String>,
        hp: String,
        currentCondition: String,
        shiny: Boolean = false
    ) = PokemonDetails(
        name = species,
        types = resolvedTypes(species),
        level = levelGender.first,
        gender = levelGender.second,
        hp = hp,
        condition = currentCondition,
        ability = "Unknown ability",
        item = "Unknown item",
        moves = emptyList(),
        stats = "",
        species = species,
        shiny = shiny
    )

    private fun updateOpponentParty(details: PokemonDetails) {
        val index = opponentTeamDetails.indexOfFirst { it.matchesIdentifier(details.name) || it.matchesIdentifier(details.species) }
        if (index >= 0) {
            opponentTeamDetails[index] = details
        } else if (opponentTeamDetails.size < 6) {
            opponentTeamDetails += details
        }
    }

    private fun updateOpponentPartyForSlot(slot: String, transform: (PokemonDetails) -> PokemonDetails) {
        opponentActivePartyIndices[slot]?.takeIf { it in opponentTeamDetails.indices }?.let {
            opponentTeamDetails[it] = transform(opponentTeamDetails[it])
            return
        }
        opponentActiveCombatants[slot]?.name?.let { name ->
            updateOpponentParty(name, transform)
        }
    }

    private fun updateOpponentParty(name: String, transform: (PokemonDetails) -> PokemonDetails) {
        opponentTeamDetails.firstOrNull { it.matchesIdentifier(name) }?.let { updateOpponentParty(transform(it)) }
    }

    private fun updatePlayerPartyMember(name: String, transform: (PokemonDetails) -> PokemonDetails) {
        val index = teamDetails.indexOfFirst { it.matchesIdentifier(name) }
        if (index >= 0) teamDetails[index] = transform(teamDetails[index])
    }

    private fun updatePlayerPartyMemberForSlot(slot: String, name: String, transform: (PokemonDetails) -> PokemonDetails) {
        playerActivePartyIndices[slot]?.let { teamDetails[it] = transform(teamDetails[it]) } ?: updatePlayerPartyMember(name, transform)
    }

    private fun PokemonDetails.matchesIdentifier(identifier: String) =
        name.equals(identifier, true) || species.equals(identifier, true)

    private fun PokemonDetails.withResolvedTypes() = copy(types = resolvedTypes(species, types))

    private fun resolvedTypes(species: String, current: List<String> = emptyList()) =
        current.ifEmpty { pokemonTypeResolver?.invoke(species).orEmpty() }

    private fun applyDetails(player: Boolean, details: String) {
        val parsed = parseDetails(details)
        if (player) {
            playerLevel = parsed.first
            playerGender = parsed.second
        } else {
            opponentLevel = parsed.first
            opponentGender = parsed.second
        }
    }

    private fun parseDetails(details: String, fallbackLevel: String = "50", fallbackGender: String = ""): Pair<String, String> {
        var level = fallbackLevel
        var gender = fallbackGender
        details.split(',').map(String::trim).forEach {
            when {
                it.startsWith("L") && it.drop(1).toIntOrNull() != null -> level = it.drop(1)
                it == "M" -> gender = "♂"
                it == "F" -> gender = "♀"
            }
        }
        return level to gender
    }

    private fun detailsAreShiny(details: String) = details.split(',')
        .asSequence()
        .map(String::trim)
        .any { it.equals("shiny", true) }

    private fun condition(hp: String) = hp.substringAfter(' ', "READY").uppercase()

    private fun isBattleFeedTurnMarker(value: String) = BATTLE_FEED_TURN_MARKER.matches(value.trim())

    private fun isBattleFeedEntry(value: String): Boolean {
        val normalized = value.trim()
        return normalized.isNotBlank() &&
            !isBattleFeedTurnMarker(normalized) &&
            !BATTLE_FEED_NON_ACTION_ENTRY.matches(normalized)
    }

    private fun mergeBattleFeedEntries(protocolEntries: List<String>, nativeEntries: List<String>): List<String> {
        val merged = mutableListOf<String>()
        var nativeIndex = 0
        protocolEntries.forEach { protocolEntry ->
            val relativeNativeMatch = nativeEntries.subList(nativeIndex, nativeEntries.size)
                .indexOfFirst { nativeEntry -> BattleFeedMessageIdentity.matches(protocolEntry, nativeEntry) }
            val nativeMatch = if (relativeNativeMatch >= 0) nativeIndex + relativeNativeMatch else -1
            if (nativeMatch >= 0) {
                while (nativeIndex < nativeMatch) merged += nativeEntries[nativeIndex++]
                merged += nativeEntries[nativeMatch]
                nativeIndex = nativeMatch + 1
            } else if (protocolEntry != "Battle started." || nativeEntries.isEmpty()) {
                merged += protocolEntry
            }
        }
        while (nativeIndex < nativeEntries.size) merged += nativeEntries[nativeIndex++]
        return merged
    }

    private fun isPlayerSide(side: String): Boolean {
        val normalizedSide = side.substringBefore(':').trim()
        if (isFreeForAllBattle()) {
            val ownerSide = if (normalizedSide.lastOrNull()?.let { it in 'a'..'z' || it in 'A'..'Z' } == true) {
                normalizedSide.dropLast(1)
            } else {
                normalizedSide
            }
            return ownerSide.equals(playerSlot, true)
        }
        val playerGroup = battleSideGroup(playerSlot)
        val sideGroup = battleSideGroup(side)
        return (playerGroup != null && playerGroup == sideGroup) || (playerGroup == null && side.startsWith(playerSlot))
    }

    private fun isFreeForAllBattle() = gameType.equals("freeforall", true)

    private fun battleSideGroup(side: String): Int? = side
        .removePrefix("p")
        .takeWhile(Char::isDigit)
        .toIntOrNull()
        ?.let {
            when (it) {
                1, 3 -> 1
                2, 4 -> 2
                else -> null
            }
        }

    private fun updatePerspective() {
        playerName = sideNames[playerSlot] ?: playerName
        sideNames.keys.firstOrNull { !isPlayerSide(it) }?.let { opponentSlot ->
            opponentName = sideNames[opponentSlot] ?: opponentName
        }
    }

    private fun healthFraction(hp: String): Float {
        val value = hp.substringBefore(' ')
        return when {
            hp.contains("fnt", true) -> 0f
            value.endsWith('%') -> (value.dropLast(1).toFloatOrNull()?.div(100f) ?: 1f).coerceIn(0f, 1f)
            '/' in value -> {
                val values = value.split('/')
                val current = values.getOrNull(0)?.toFloatOrNull() ?: return 1f
                val maximum = values.getOrNull(1)?.toFloatOrNull() ?: return 1f
                if (maximum <= 0f) 0f else (current / maximum).coerceIn(0f, 1f)
            }
            else -> 1f
        }
    }

    private fun notifyListeners() {
        listeners.toList().forEach { it.onBattleStateChanged() }
    }

    private fun publishFeedback(feedback: BattleFeedback) {
        feedbackListeners.toList().forEach { it.onBattleFeedback(feedback) }
    }

    private fun publishClientAction(action: ClientAction) {
        clientActionListeners.toList().forEach { it.onClientAction(action) }
    }

    companion object {
        const val MENU_ITEM_COUNT = 15
        const val MENU_COLUMNS = 3
        private const val LOBBY_STATUS = "Find a battle or challenge a player."
        private const val BATTLE_HISTORY_LIMIT = 1024
        private const val SHOWDOWN_BATTLE_FEED_WINDOW_LIMIT = 32
        private val BATTLE_FEED_TURN_MARKER = Regex("^Turn\\s+\\d+\\.?$", RegexOption.IGNORE_CASE)
        private val BATTLE_FEED_NON_ACTION_ENTRY = Regex(
            "(?i)^(?:.+ has \\d+ seconds? left\\.?|.+['’]s rating:\\s*\\d+\\s*→\\s*\\d+.*|Battle timer is (?:on|off):?.*|The battle timer is off\\.?|Battle type: .+|Generation \\d+ battle\\.|Format: .+|Rule: .+|.+ team size: \\d+|Rated battle\\.)$"
        )
        private val BOOST_STATS = setOf("atk", "def", "spa", "spd", "spe", "accuracy", "evasion")

        fun displayPokemonName(name: String, species: String = name): String {
            val label = name.trim()
            val canonicalSpecies = species.trim().ifBlank { label }
            val readableLabel = readableSpeciesName(label)
            val readableSpecies = readableSpeciesName(canonicalSpecies)
            val sameBaseSpecies = readableLabel.equals(readableSpecies, true)
            return if (label.equals(canonicalSpecies, true) || sameBaseSpecies) {
                readableSpecies
            } else {
                label
            }
        }

        private fun readableSpeciesName(value: String): String {
            return if (value.length > 18 && value.count { it == '-' } >= 2) {
                value.substringBefore('-')
            } else {
                value
            }
        }

        fun parseServerFormats(line: String): List<MatchFormat> {
            if (!line.startsWith("|formats|")) return emptyList()
            var skipSectionTitle = false
            return line.split('|').drop(2).mapNotNull { token ->
                if (token.startsWith(',')) {
                    skipSectionTitle = true
                    return@mapNotNull null
                }
                if (skipSectionTitle) {
                    skipSectionTitle = false
                    return@mapNotNull null
                }
                val parts = token.split(',', limit = 2)
                val first = parts.firstOrNull()?.trim().orEmpty()
                val second = parts.getOrNull(1)?.trim().orEmpty()
                val suffix = token.substringAfterLast(',', "").trim()
                val capabilityCode = suffix.toIntOrNull(16)?.takeIf {
                    suffix.isNotBlank() && suffix.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
                }
                val label = when {
                    first.startsWith('[') -> first
                    second.startsWith('[') -> second.substringBefore(',').trim()
                    else -> return@mapNotNull null
                }
                val id = if (first.startsWith('[')) {
                    label.lowercase().filter(Char::isLetterOrDigit)
                } else {
                    first.lowercase().filter(Char::isLetterOrDigit)
                }
                id.takeIf { it.isNotBlank() }?.let {
                    val searchEnabled = when {
                        capabilityCode != null -> capabilityCode and 2 != 0
                        token.endsWith(",,") -> true
                        token.endsWith(",") -> false
                        else -> true
                    }
                    val challengeEnabled = when {
                        capabilityCode != null -> capabilityCode and 4 != 0
                        token.endsWith(",,") -> false
                        token.endsWith(",") -> true
                        else -> true
                    }
                    MatchFormat(
                        it,
                        label,
                        usesRandomTeams = second.contains('#') || MatchFormat.usesRandomTeamsFor(it),
                        canSearch = searchEnabled,
                        canChallenge = challengeEnabled
                    )
                }
            }.distinctBy(MatchFormat::id)
        }

        val SHOWDOWN_BACKDROPS = arrayOf(
            "bg-aquacordetown.jpg",
            "bg-beach.jpg",
            "bg-city.jpg",
            "bg-dampcave.jpg",
            "bg-darkbeach.jpg",
            "bg-darkcity.jpg",
            "bg-darkmeadow.jpg",
            "bg-deepsea.jpg",
            "bg-desert.jpg",
            "bg-earthycave.jpg",
            "bg-elite4drake.jpg",
            "bg-forest.jpg",
            "bg-icecave.jpg",
            "bg-leaderwallace.jpg",
            "bg-library.jpg",
            "bg-meadow.jpg",
            "bg-orasdesert.jpg",
            "bg-orassea.jpg",
            "bg-skypillar.jpg"
        )
    }
}
