package com.showdown.ds

import org.json.JSONObject
import kotlin.random.Random

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

    data class MatchFormat(
        val id: String,
        val label: String,
        val menuLabel: String = label,
        val usesRandomTeams: Boolean = id.contains("randombattle") || id.contains("battlefactory")
    ) {
        companion object {
            val GEN6_RANDOM = MatchFormat("gen6randombattle", "[Gen 6] Random Battle", "Gen 6 Random")
            val GEN7_RANDOM = MatchFormat("gen7randombattle", "[Gen 7] Random Battle", "Gen 7 Random")
            val GEN8_RANDOM = MatchFormat("gen8randombattle", "[Gen 8] Random Battle", "Gen 8 Random")
            val GEN9_RANDOM = MatchFormat("gen9randombattle", "[Gen 9] Random Battle", "Gen 9 Random")
            val defaults = listOf(GEN6_RANDOM, GEN7_RANDOM, GEN8_RANDOM, GEN9_RANDOM)
        }
    }

    enum class SpriteStyle {
        MODERN_3D,
        CLASSIC_2D
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
        CHOOSE_FORMAT,
        OPEN_CHAT,
        FORFEIT,
        CHALLENGE_PLAYER,
        EXPORT_REPLAY
    }

    enum class BattleGimmick(val choiceSuffix: String, val label: String) {
        Z_POWER("zmove", "Z-Power"),
        MEGA_EVOLUTION("mega", "Mega Evolution"),
        DYNAMAX("dynamax", "Dynamax"),
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
        val pokeball: String = "pokeball"
    )

    data class ActiveCombatant(
        val slot: String,
        val name: String,
        val types: List<String>,
        val level: String,
        val gender: String,
        val hp: String,
        val condition: String,
        val entryAtNanos: Long
    )

    data class BattleInfo(
        val weather: String,
        val terrain: String,
        val playerSideConditions: List<String>,
        val opponentSideConditions: List<String>,
        val playerBoosts: Map<String, Int>,
        val opponentBoosts: Map<String, Int>
    )

    private data class PendingHit(
        val actor: String,
        val target: String,
        var superEffective: Boolean = false,
        var resisted: Boolean = false,
        var critical: Boolean = false
    )

    private val listeners = mutableListOf<Listener>()
    private val feedbackListeners = mutableListOf<FeedbackListener>()
    private val decisionListeners = mutableListOf<DecisionListener>()
    private val clientActionListeners = mutableListOf<ClientActionListener>()
    private val chatListeners = mutableListOf<ChatListener>()
    private val protocolListeners = mutableListOf<ProtocolListener>()
    private val battleEventListeners = mutableListOf<BattleEventListener>()
    private var protocolEventCollector: MutableList<String>? = null
    private val protocolHistory = mutableListOf<String>()
    private var moveTypeResolver: ((String) -> String?)? = null
    private var pokemonTypeResolver: ((String) -> List<String>?)? = null
    private val availableMatchFormats = MatchFormat.defaults.toMutableList()
    private val battleLog = mutableListOf("Battle started.", "Incineroar entered the field.", "Tapu Koko's Electric Surge activated!")
    private val chatMessages = mutableListOf("[Battle] Welcome to Showdown!", "[System] Controller and touch input are ready.")
    private val activityMessages = mutableListOf<String>().apply {
        addAll(battleLog)
        addAll(chatMessages)
    }
    private val moves = mutableListOf(
        MoveOption("Fake Out", "NORMAL", 10, 10, "Physical", "40", "100"),
        MoveOption("Flare Blitz", "FIRE", 15, 15, "Physical", "120", "100"),
        MoveOption("Darkest Lariat", "DARK", 10, 10, "Physical", "85", "100"),
        MoveOption("Parting Shot", "DARK", 20, 20, "Status", "—", "100")
    )
    private val team = mutableListOf("Incineroar", "Naganadel", "Mimikyu", "Landorus", "Rotom-Wash", "Ferrothorn")
    private val teamPreviewOrder = mutableListOf<Int>()
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
    private val playerActiveCombatants = linkedMapOf<String, ActiveCombatant>()
    private val opponentActiveCombatants = linkedMapOf<String, ActiveCombatant>()
    private val activeTeamNames = mutableSetOf<String>()
    private val activeSlotNames = mutableMapOf<String, String>()
    private var battleVisualSeed = Random.nextInt(1, Int.MAX_VALUE)
    private var pendingHit: PendingHit? = null
    private var requestId: Int? = null
    private val activeRequests = mutableListOf<JSONObject>()
    private val activeChoices = mutableListOf<String>()
    private val forceSwitchChoices = mutableListOf<String>()
    private val targetOptions = mutableListOf<TargetOption>()
    private var activeSlotIndex = 0
    private var requiredSwitches = 0
    private var selectedTargetIndex = -1
    private val sideNames = mutableMapOf<String, String>()
    private var playerSlot = "p1"
    private var localUsername: String? = null
    private var liveBattleActive = false
    private var openingEntrances = 0
    private var latestOpeningEntranceAtNanos = 0L
    private var weather = ""
    private var terrain = ""
    private val playerSideConditions = mutableListOf<String>()
    private val opponentSideConditions = mutableListOf<String>()
    private val playerBoosts = mutableMapOf<String, Int>()
    private val opponentBoosts = mutableMapOf<String, Int>()

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
    var matchFormat = MatchFormat.GEN7_RANDOM
        private set
    var touchConfirmationEnabled = true
        private set
    var soundEffectsEnabled = true
        private set
    var musicEnabled = true
        private set
    var hapticsEnabled = true
        private set
    var spriteStyle = SpriteStyle.MODERN_3D
        private set
    var selectedGimmick: BattleGimmick? = null
        private set
    var battleFinished = false
        private set

    fun playerHealthFraction() = healthFraction(playerHp)

    fun opponentHealthFraction() = healthFraction(opponentHp)

    fun hasActivePlayerCombatant() = playerActiveCombatants.values.takeIf { it.isNotEmpty() }?.any { !it.condition.contains("FNT", true) }
        ?: !playerCondition.contains("FNT", true)

    fun hasActiveOpponentCombatant() = opponentActiveCombatants.values.takeIf { it.isNotEmpty() }?.any { !it.condition.contains("FNT", true) }
        ?: !opponentCondition.contains("FNT", true)

    fun isBattleFinished() = battleFinished

    fun battleInfo() = BattleInfo(
        weather,
        terrain,
        playerSideConditions.toList(),
        opponentSideConditions.toList(),
        playerBoosts.toMap(),
        opponentBoosts.toMap()
    )

    fun setMoveTypeResolver(resolver: (String) -> String?) {
        moveTypeResolver = resolver
        val resolvedMoves = moves.map { move ->
            if (move.type != "UNKNOWN") move else move.copy(type = resolver(move.name) ?: move.type)
        }
        if (resolvedMoves == moves) return
        moves.clear()
        moves += resolvedMoves
        playerDetails = playerDetails.copy(moves = moves.map { it.name })
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

    fun moves() = moves.toList()

    fun team() = team.toList()

    fun teamPreviewOrder() = teamPreviewOrder.toList()

    fun battleLog() = battleLog.toList()

    fun chatMessages() = chatMessages.toList()

    fun activityMessages() = activityMessages.toList()

    fun playerDetails() = playerDetails

    fun opponentDetails() = opponentDetails

    fun playerPartyDetails() = teamDetails.toList()

    fun opponentPartyDetails() = opponentTeamDetails.toList()

    fun playerActiveCombatants() = playerActiveCombatants.values.sortedBy { it.slot }

    fun opponentActiveCombatants() = opponentActiveCombatants.values.sortedBy { it.slot }

    fun focusedTeamDetails() = teamDetails.getOrElse(focusedTeam) { playerDetails }

    fun teamMemberDetails(index: Int) = teamDetails.getOrElse(index) { playerDetails }

    fun teamCondition(index: Int) = teamDetails.getOrNull(index)?.condition.orEmpty()

    fun availableGimmicks() = availableGimmicks.toList()

    fun targetOptions() = targetOptions.toList()

    fun availableMatchFormats() = availableMatchFormats.toList()

    fun isSinglesBattle() = !format.contains("doubles", true) && !format.contains("multi", true)

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

    fun setConnectionStatus(value: String) {
        status = value
        notifyListeners()
    }

    fun setLiveBattleActive(value: Boolean) {
        if (liveBattleActive == value) return
        liveBattleActive = value
        notifyListeners()
    }

    fun presentBattleEvent(message: String) {
        if (message.isBlank()) return
        latestBattleEvent = message
        latestBattleEventAtNanos = System.nanoTime()
        notifyListeners()
    }

    fun sendOutMessage(pokemon: String, playerSide: Boolean) =
        if (playerSide) "Go! $pokemon!" else "$opponentName sent out $pokemon!"

    fun setMatchFormat(format: MatchFormat) {
        matchFormat = format
        status = "Battle format: ${format.label}"
        notifyListeners()
    }

    fun applyServerFormats(lines: List<String>) {
        val formats = lines.flatMap(::parseServerFormats)
        if (formats.isEmpty()) return
        val selected = formats.firstOrNull { it.id == matchFormat.id } ?: matchFormat
        availableMatchFormats.clear()
        availableMatchFormats += formats
        if (availableMatchFormats.none { it.id == selected.id }) availableMatchFormats += selected
        matchFormat = selected
        notifyListeners()
    }

    fun focusMove(index: Int) {
        if (index !in moves.indices) return
        panel = Panel.MOVES
        focusedMove = index
        updateTargetOptions()
        status = "Ready: ${moves[index].name}"
        notifyListeners()
    }

    fun selectMoveWithTouch(index: Int) {
        if (index !in moves.indices) return
        if (moves[index].disabled) {
            status = "${moves[index].name} is disabled."
            notifyListeners()
            return
        }
        if (touchConfirmationEnabled && focusedMove != index) {
            focusMove(index)
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
        panel = nextPanel
        focusedMessage = 0
        if (nextPanel == Panel.MENU) focusedMenuItem = 0
        status = when (nextPanel) {
            Panel.MOVES -> "Choose a move"
            Panel.TEAM -> when (decisionKind) {
                DecisionKind.SWITCH -> "Choose a Pokémon to switch in"
                DecisionKind.TEAM_PREVIEW -> "Confirm your team order"
                else -> "Choose a Pokémon"
            }
            Panel.ACTIVITY -> "Battle activity and chat"
            Panel.MENU -> "Battle menu"
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
        when (panel) {
            Panel.MOVES -> {
                if (!decisionAvailable || decisionKind != DecisionKind.MOVE || focusedMove !in moves.indices) return
                val move = moves[focusedMove]
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
                val selectedChoice = "move ${focusedMove + 1}${gimmick?.let { " ${it.choiceSuffix}" } ?: ""}${target?.let { " $it" } ?: ""}"
                if (activeRequests.size > 1) {
                    while (activeChoices.size < activeRequests.size) activeChoices += ""
                    activeChoices[activeSlotIndex] = selectedChoice
                    selectedGimmick = null
                    if (activeSlotIndex < activeRequests.lastIndex) {
                        activeSlotIndex += 1
                        applyActiveRequest(activeRequests[activeSlotIndex])
                        status = "Choose a move for active Pokémon ${activeSlotIndex + 1}/${activeRequests.size}"
                        notifyListeners()
                        return
                    }
                }
                val selectedChoices = if (activeRequests.size > 1) activeChoices.joinToString(", ") else selectedChoice
                val choice = "/choose $selectedChoices${requestId?.let { "|$it" } ?: ""}"
                status = "Move sent: ${gimmick?.label?.plus(" ") ?: ""}${move.name}"
                appendLog("$playerPokemon chose ${gimmick?.label?.plus(" ") ?: ""}${move.name}.")
                chatMessages += "[You] $choice"
                decisionAvailable = false
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

    fun openChatComposer() {
        publishClientAction(ClientAction.OPEN_CHAT)
    }

    fun sendChat(message: String) {
        val value = message.trim()
        if (value.isBlank()) return
        val displayMessage = "[You] $value"
        chatMessages += displayMessage
        if (chatMessages.size > 32) chatMessages.removeAt(0)
        appendActivity(displayMessage)
        status = "Message sent."
        chatListeners.toList().forEach { it.onChat(value) }
        notifyListeners()
    }

    fun goBack() {
        if (panel == Panel.TEAM && decisionAvailable && decisionKind == DecisionKind.TEAM_PREVIEW && teamPreviewOrder.isNotEmpty()) {
            val removed = teamPreviewOrder.removeLast()
            status = "Removed ${team[removed]} from the order."
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
        status = selectedGimmick?.let { "${it.label} ready: choose a move" } ?: "Choose a move"
        notifyListeners()
    }

    fun cycleGimmick() {
        if (availableGimmicks.isEmpty()) return
        val currentIndex = availableGimmicks.indexOf(selectedGimmick)
        selectedGimmick = availableGimmicks[Math.floorMod(currentIndex + 1, availableGimmicks.size)]
        status = "${selectedGimmick?.label} ready: choose a move"
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
        val packet = lines.filter { it.startsWith('|') }
        if (packet.any { it.startsWith("|init|battle") }) protocolHistory.clear()
        protocolHistory += packet
        val events = mutableListOf<String>()
        protocolEventCollector = events
        try {
            protocolListeners.toList().forEach { it.onProtocol(packet) }
            packet.forEach { line ->
                val fields = line.split('|')
                if (fields.size < 2) return@forEach
                when (fields[1]) {
                    "init" -> applyInit(fields)
                    "player" -> applyPlayer(fields)
                    "tier" -> if (fields.size > 2) format = fields[2]
                    "turn" -> applyTurn(fields)
                    "switch", "drag", "replace" -> applySwitch(fields)
                    "poke" -> applyPoke(fields)
                    "move" -> {
                        publishPendingHit()
                        applyMove(fields)
                    }
                    "-damage" -> {
                        publishPendingHit()
                        applyHealth(fields)
                        pendingHit = PendingHit(fields[2].substringAfter(':').trim(), fields[2].substringAfter(':').trim())
                    }
                    "-heal", "-sethp" -> applyHealth(fields)
                    "-status" -> applyStatus(fields)
                    "-curestatus" -> applyStatus(fields, cured = true)
                    "-cureteam" -> cureTeam(fields)
                    "-supereffective" -> applyHitModifier(fields, superEffective = true)
                    "-resisted" -> applyHitModifier(fields, resisted = true)
                    "-crit" -> applyHitModifier(fields, critical = true)
                    "-ability" -> applyAbility(fields)
                    "-item" -> applyItem(fields)
                    "-enditem" -> applyItem(fields, "No item")
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
                    "-swapboost" -> appendLog("${battleActor(fields.getOrNull(2))} swapped stat changes with ${battleActor(fields.getOrNull(3))}.")
                    "-invertboost" -> appendLog("${battleActor(fields.getOrNull(2))}'s stat changes were inverted.")
                    "-clearpositiveboost" -> appendLog("${battleActor(fields.getOrNull(2))}'s positive stat changes were cleared.")
                    "-copyboost" -> appendLog("${battleActor(fields.getOrNull(2))} copied stat changes from ${battleActor(fields.getOrNull(3))}.")
                    "cant" -> applyCant(fields)
                    "-fail" -> appendLog("${battleActor(fields.getOrNull(2))} failed to use ${battleEffectName(fields.getOrNull(3))}.")
                    "-block" -> appendLog("${battleActor(fields.getOrNull(2))} was blocked by ${battleEffectName(fields.getOrNull(3))}.")
                    "-notarget" -> appendLog("${battleActor(fields.getOrNull(2))} had no target.")
                    "-miss" -> appendLog("${battleActor(fields.getOrNull(2))}'s attack missed ${battleActor(fields.getOrNull(3))}.")
                    "-immune" -> appendLog("${battleActor(fields.getOrNull(2))} is immune.")
                    "-prepare" -> appendLog("${battleActor(fields.getOrNull(2))} is preparing ${battleEffectName(fields.getOrNull(3))}.")
                    "-mustrecharge" -> appendLog("${battleActor(fields.getOrNull(2))} must recharge.")
                    "-activate" -> appendLog("${battleActor(fields.getOrNull(2))} activated ${battleEffectName(fields.getOrNull(3))}.")
                    "-start" -> appendLog("${battleActor(fields.getOrNull(2))}: ${battleEffectName(fields.getOrNull(3))} started.")
                    "-end" -> appendLog("${battleActor(fields.getOrNull(2))}: ${battleEffectName(fields.getOrNull(3))} ended.")
                    "-endability" -> applyEndAbility(fields)
                    "-hint", "-message" -> appendLog(fields.drop(2).joinToString("|").trim())
                    "-waiting" -> appendLog("${battleActor(fields.getOrNull(2))} is waiting for ${battleActor(fields.getOrNull(3))}.")
                    "-hitcount" -> appendLog("${battleActor(fields.getOrNull(2))} was hit ${fields.getOrNull(3).orEmpty()} times.")
                    "-singlemove", "-singleturn" -> appendLog("${battleActor(fields.getOrNull(2))}: ${battleEffectName(fields.getOrNull(3))}.")
                    "-nothing" -> appendLog("The move had no effect.")
                    "-zpower" -> appendLog("${battleActor(fields.getOrNull(2))} used a Z-Power move.")
                    "-zbroken" -> appendLog("${battleActor(fields.getOrNull(2))}'s protection was broken by Z-Power.")
                    "-clearallboost" -> clearAllBoosts()
                    "-clearboost" -> clearBoosts(fields)
                    "-clearnegativeboost" -> clearNegativeBoosts(fields)
                    "detailschange", "-formechange", "-transform", "-burst" -> applyFormChange(fields)
                    "-mega" -> appendLog("${battleActor(fields.getOrNull(2))} Mega Evolved.")
                    "-primal" -> appendLog("${battleActor(fields.getOrNull(2))} reverted to its primal form.")
                    "-center" -> appendLog("The remaining Pokémon moved to the center of the field.")
                    "-terastallize" -> applyTerastallize(fields)
                    "faint" -> applyFaint(fields)
                    "request" -> applyRequest(fields)
                    "win" -> applyWin(fields)
                    "tie", "draw", "prematureend" -> applyTie(fields)
                    "error" -> applyBattleError(fields)
                    "c", "c:" -> applyChat(fields)
                    "message", "inactive", "inactiveoff" -> appendLog(fields.drop(2).joinToString("|"))
                }
            }
            publishPendingHit()
        } finally {
            protocolEventCollector = null
        }
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

    private fun applyInit(fields: List<String>) {
        if (fields.getOrNull(2) != "battle") return
        teamPreviewOrder.clear()
        battleVisualSeed = Random.nextInt(1, Int.MAX_VALUE)
        selectedGimmick = null
        battleFinished = false
        openingEntrances = 0
        latestOpeningEntranceAtNanos = 0L
        playerEntryAtNanos = 0L
        opponentEntryAtNanos = 0L
        activeRequests.clear()
        activeChoices.clear()
        forceSwitchChoices.clear()
        targetOptions.clear()
        playerActiveCombatants.clear()
        opponentActiveCombatants.clear()
        activeSlotIndex = 0
        requiredSwitches = 0
        selectedTargetIndex = -1
        opponentTeamDetails.clear()
        weather = ""
        terrain = ""
        playerSideConditions.clear()
        opponentSideConditions.clear()
        playerBoosts.clear()
        opponentBoosts.clear()
        status = "Battle starting"
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
        appendLog("Turn $turn.")
    }

    private fun applySwitch(fields: List<String>) {
        if (fields.size < 5) return
        val pokemon = fields[3].substringBefore(',')
        val playerSide = isPlayerSide(fields[2])
        val slot = fields[2].substringBefore(":").trim()
        val entryDelayMillis = queueEntry(playerSide)
        val parsedDetails = parseDetails(fields[3])
        val hp = fields[4]
        val currentCondition = condition(hp)
        when {
            playerSide -> {
                val primary = slot.endsWith("a") || playerActiveCombatants.isEmpty()
                activeSlotNames[slot] = pokemon
                activeTeamNames.clear()
                activeTeamNames += activeSlotNames.values
                val index = team.indexOfFirst { it.equals(pokemon, true) }
                val activeDetails = if (index >= 0) teamDetails[index] else playerDetails.copy(name = pokemon, types = resolvedTypes(pokemon))
                val updatedDetails = activeDetails.copy(
                    name = pokemon,
                    types = resolvedTypes(pokemon, activeDetails.types),
                    level = parsedDetails.first,
                    gender = parsedDetails.second,
                    hp = hp,
                    condition = currentCondition
                )
                if (index >= 0) teamDetails[index] = updatedDetails
                playerActiveCombatants[slot] = ActiveCombatant(slot, pokemon, updatedDetails.types, parsedDetails.first, parsedDetails.second, hp, currentCondition, playerEntryAtNanos)
                if (primary) {
                    playerPokemon = pokemon
                    playerHp = hp
                    playerLevel = parsedDetails.first
                    playerGender = parsedDetails.second
                    playerCondition = currentCondition
                    playerDetails = updatedDetails
                }
            }
            else -> {
                val primary = slot.endsWith("a") || opponentActiveCombatants.isEmpty()
                val existing = opponentTeamDetails.firstOrNull { it.name.equals(pokemon, true) }
                val activeDetails = existing ?: opponentDetails.copy(name = pokemon, types = resolvedTypes(pokemon))
                val updatedDetails = activeDetails.copy(
                    name = pokemon,
                    types = resolvedTypes(pokemon, activeDetails.types),
                    level = parsedDetails.first,
                    gender = parsedDetails.second,
                    hp = hp,
                    condition = currentCondition
                )
                updateOpponentParty(updatedDetails)
                opponentActiveCombatants[slot] = ActiveCombatant(slot, pokemon, updatedDetails.types, parsedDetails.first, parsedDetails.second, hp, currentCondition, opponentEntryAtNanos)
                if (primary) {
                    opponentPokemon = pokemon
                    opponentHp = hp
                    opponentLevel = parsedDetails.first
                    opponentGender = parsedDetails.second
                    opponentCondition = currentCondition
                    opponentDetails = updatedDetails
                }
            }
        }
        val message = sendOutMessage(pokemon, playerSide)
        appendLog(message)
        publishFeedback(BattleFeedback(FeedbackType.ENTRY, actor = pokemon, delayMillis = entryDelayMillis, message = message))
        publishFeedback(BattleFeedback(FeedbackType.POKEMON_CRY, actor = pokemon, delayMillis = entryDelayMillis))
    }

    private fun applyPoke(fields: List<String>) {
        if (fields.size < 4 || isPlayerSide(fields[2])) return
        val details = fields[3]
        val pokemon = details.substringBefore(',')
        val levelGender = parseDetails(details)
        val existing = opponentTeamDetails.firstOrNull { it.name.equals(pokemon, true) }
        updateOpponentParty(
            existing?.copy(level = levelGender.first, gender = levelGender.second, types = resolvedTypes(pokemon, existing.types)) ?: PokemonDetails(
                name = pokemon,
                types = resolvedTypes(pokemon),
                level = levelGender.first,
                gender = levelGender.second,
                hp = "100/100",
                condition = "READY",
                ability = "Unknown ability",
                item = "Unknown item",
                moves = emptyList(),
                stats = ""
            )
        )
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
        val actor = fields[2].substringAfter(':').trim()
        val event = "$actor used ${fields[3]}!"
        latestMoveEvent = event
        latestMoveEventAtNanos = System.nanoTime()
        appendLog(event)
        publishFeedback(BattleFeedback(FeedbackType.MOVE, actor = actor, target = fields.getOrNull(4)?.substringAfter(':')?.trim().orEmpty(), move = fields[3]))
    }

    private fun applyHealth(fields: List<String>) {
        if (fields.size < 4) return
        val slot = fields[2].substringBefore(":").trim()
        val hp = fields[3]
        val currentCondition = condition(hp)
        when {
            isPlayerSide(fields[2]) -> {
                playerActiveCombatants[slot]?.let {
                    playerActiveCombatants[slot] = it.copy(hp = hp, condition = currentCondition)
                    updatePlayerPartyMember(it.name) { details -> details.copy(hp = hp, condition = currentCondition) }
                }
                if (slot.endsWith("a") || playerActiveCombatants.size == 1) {
                    playerHp = hp
                    playerCondition = currentCondition
                    updatePlayerDetails { it.copy(hp = playerHp, condition = playerCondition) }
                }
            }
            else -> {
                opponentActiveCombatants[slot]?.let {
                    opponentActiveCombatants[slot] = it.copy(hp = hp, condition = currentCondition)
                    updateOpponentParty(it.name) { details -> details.copy(hp = hp, condition = currentCondition) }
                }
                if (slot.endsWith("a") || opponentActiveCombatants.size == 1) {
                    opponentHp = hp
                    opponentCondition = currentCondition
                    opponentDetails = opponentDetails.copy(hp = opponentHp, condition = opponentCondition)
                    updateOpponentParty(opponentDetails)
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
                updatePlayerPartyMember(it.name) { details -> details.copy(hp = "0 fnt", condition = "FNT") }
            }
            if (slot.endsWith("a") || playerActiveCombatants.size == 1) {
                playerHp = "0 fnt"
                playerCondition = "FNT"
                updatePlayerDetails { it.copy(hp = playerHp, condition = playerCondition) }
            }
        } else {
            opponentActiveCombatants[slot]?.let {
                opponentActiveCombatants[slot] = it.copy(hp = "0 fnt", condition = "FNT")
                updateOpponentParty(it.name) { details -> details.copy(hp = "0 fnt", condition = "FNT") }
            }
            if (slot.endsWith("a") || opponentActiveCombatants.size == 1) {
                opponentHp = "0 fnt"
                opponentCondition = "FNT"
                opponentDetails = opponentDetails.copy(hp = opponentHp, condition = opponentCondition)
                updateOpponentParty(opponentDetails)
            }
        }
        appendLog("$pokemon fainted.")
    }

    private fun applyStatus(fields: List<String>, cured: Boolean = false) {
        val actor = fields.getOrNull(2) ?: return
        val status = if (cured) "READY" else fields.getOrNull(3)?.uppercase() ?: return
        val slot = actor.substringBefore(":").trim()
        if (isPlayerSide(actor)) {
            playerActiveCombatants[slot]?.let {
                playerActiveCombatants[slot] = it.copy(condition = status)
                updatePlayerPartyMember(it.name) { details -> details.copy(condition = status) }
            }
            if (slot.endsWith("a") || playerActiveCombatants.size == 1) {
                playerCondition = status
                updatePlayerDetails { it.copy(condition = status) }
            }
        } else {
            opponentActiveCombatants[slot]?.let {
                opponentActiveCombatants[slot] = it.copy(condition = status)
                updateOpponentParty(it.name) { details -> details.copy(condition = status) }
            }
            if (slot.endsWith("a") || opponentActiveCombatants.size == 1) {
                opponentCondition = status
                opponentDetails = opponentDetails.copy(condition = status)
                updateOpponentParty(opponentDetails)
            }
        }
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
        val actor = fields.getOrNull(2) ?: return
        val details = fields.getOrNull(3) ?: return
        val species = details.substringBefore(',').trim().ifBlank { return }
        val slot = actor.substringBefore(":").trim()
        if (isPlayerSide(actor)) {
            val parsed = parseDetails(details)
            playerActiveCombatants[slot]?.let {
                playerActiveCombatants[slot] = it.copy(name = species, types = resolvedTypes(species), level = parsed.first, gender = parsed.second)
                updatePlayerPartyMember(it.name) { party -> party.copy(name = species, types = resolvedTypes(species), level = parsed.first, gender = parsed.second) }
            }
            activeSlotNames[slot] = species
            activeTeamNames.clear()
            activeTeamNames += activeSlotNames.values
            if (slot.endsWith("a") || playerActiveCombatants.size == 1) {
                playerPokemon = species
                playerLevel = parsed.first
                playerGender = parsed.second
                updatePlayerDetails { it.copy(name = species, types = resolvedTypes(species), level = playerLevel, gender = playerGender) }
            }
        } else {
            val parsed = parseDetails(details)
            opponentActiveCombatants[slot]?.let {
                opponentActiveCombatants[slot] = it.copy(name = species, types = resolvedTypes(species), level = parsed.first, gender = parsed.second)
                updateOpponentParty(it.name) { party -> party.copy(name = species, types = resolvedTypes(species), level = parsed.first, gender = parsed.second) }
            }
            if (slot.endsWith("a") || opponentActiveCombatants.size == 1) {
                opponentPokemon = species
                opponentLevel = parsed.first
                opponentGender = parsed.second
                opponentDetails = opponentDetails.copy(name = species, types = resolvedTypes(species), level = opponentLevel, gender = opponentGender)
            }
        }
        appendLog("$species changed form.")
    }

    private fun applyTerastallize(fields: List<String>) {
        val actor = fields.getOrNull(2) ?: return
        val teraType = fields.getOrNull(3)?.uppercase() ?: return
        val slot = actor.substringBefore(":").trim()
        if (isPlayerSide(actor)) {
            playerActiveCombatants[slot]?.let {
                playerActiveCombatants[slot] = it.copy(types = listOf(teraType))
                updatePlayerPartyMember(it.name) { details -> details.copy(types = listOf(teraType)) }
            }
            if (slot.endsWith("a") || playerActiveCombatants.size == 1) updatePlayerDetails { it.copy(types = listOf(teraType)) }
        } else {
            opponentActiveCombatants[slot]?.let {
                opponentActiveCombatants[slot] = it.copy(types = listOf(teraType))
                updateOpponentParty(it.name) { details -> details.copy(types = listOf(teraType)) }
            }
            if (slot.endsWith("a") || opponentActiveCombatants.size == 1) opponentDetails = opponentDetails.copy(types = listOf(teraType))
        }
        appendLog("${actor.substringAfter(':').trim()} Terastallized into $teraType.")
    }

    private fun applyRequest(fields: List<String>) {
        val requestText = fields.getOrNull(2) ?: return
        teamPreviewOrder.clear()
        activeRequests.clear()
        activeChoices.clear()
        forceSwitchChoices.clear()
        targetOptions.clear()
        activeSlotIndex = 0
        requiredSwitches = 0
        selectedTargetIndex = -1
        runCatching {
            val request = JSONObject(requestText)
            requestId = request.optInt("rqid", -1).takeIf { it >= 0 }
            syncTeamFromRequest(request)
            if (request.optBoolean("wait")) {
                decisionKind = DecisionKind.WAIT
                decisionAvailable = false
                status = "Waiting for the other player…"
                return@runCatching
            }
            if (request.optBoolean("teamPreview")) {
                decisionKind = DecisionKind.TEAM_PREVIEW
                decisionAvailable = team.isNotEmpty()
                panel = Panel.TEAM
                status = "Confirm your team order"
                return@runCatching
            }
            val forceSwitch = request.optJSONArray("forceSwitch")
            requiredSwitches = forceSwitch?.let { array -> (0 until array.length()).count { array.optBoolean(it) } } ?: 0
            if (requiredSwitches > 0) {
                decisionKind = DecisionKind.SWITCH
                decisionAvailable = team.indices.any { canSwitchTo(it) }
                panel = Panel.TEAM
                status = if (requiredSwitches > 1) "Choose a Pokémon to switch in 1/$requiredSwitches" else "Choose a Pokémon to switch in"
                return@runCatching
            }
            val active = request.optJSONArray("active") ?: run {
                decisionKind = DecisionKind.WAIT
                decisionAvailable = false
                status = "Waiting for a battle decision…"
                return@runCatching
            }
            for (index in 0 until active.length()) active.optJSONObject(index)?.let(activeRequests::add)
            if (activeRequests.isEmpty()) {
                decisionKind = DecisionKind.WAIT
                decisionAvailable = false
                status = "Waiting for a battle decision…"
                return@runCatching
            }
            if (!applyActiveRequest(activeRequests[0])) {
                decisionKind = DecisionKind.WAIT
                decisionAvailable = false
                status = "Waiting for a battle decision…"
            }
        }.onFailure {
            appendLog("Received an unreadable battle request.")
        }
    }

    private fun applyActiveRequest(active: JSONObject): Boolean {
        val requestMoves = active.optJSONArray("moves") ?: return false
        moves.clear()
        for (index in 0 until requestMoves.length()) {
            val move = requestMoves.getJSONObject(index)
            val pp = move.optInt("pp", 0)
            val power = move.optInt("basePower", 0).takeIf { it > 0 }?.toString() ?: "—"
            val name = move.optString("move", "Move ${index + 1}")
            moves += MoveOption(
                name,
                move.optString("type").uppercase().takeIf { it.isNotBlank() } ?: moveTypeResolver?.invoke(name) ?: "UNKNOWN",
                pp,
                move.optInt("maxpp", pp),
                move.optString("category", "Status"),
                power,
                move.optString("accuracy", "—"),
                move.optBoolean("disabled") || pp <= 0,
                move.optString("target")
            )
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
        playerDetails = playerDetails.copy(moves = moves.map { it.name })
        team.indexOf(playerPokemon).takeIf { it >= 0 }?.let { teamDetails[it] = playerDetails }
        updateAvailableGimmicks(active)
        updateTargetOptions()
        return moves.isNotEmpty()
    }

    private fun applyWin(fields: List<String>) {
        fields.getOrNull(2)?.let {
            status = "$it won the battle."
            appendLog(status)
            decisionAvailable = false
            decisionKind = DecisionKind.WAIT
            requestId = null
            selectedGimmick = null
            teamPreviewOrder.clear()
            activeRequests.clear()
            activeChoices.clear()
            forceSwitchChoices.clear()
            targetOptions.clear()
            activeSlotIndex = 0
            requiredSwitches = 0
            selectedTargetIndex = -1
            battleFinished = true
        }
    }

    private fun applyTie(fields: List<String>) {
        val reason = fields.drop(2).joinToString("|").ifBlank { "The battle ended." }
        status = reason
        appendLog(reason)
        decisionAvailable = false
        decisionKind = DecisionKind.WAIT
        requestId = null
        selectedGimmick = null
        teamPreviewOrder.clear()
        activeRequests.clear()
        activeChoices.clear()
        forceSwitchChoices.clear()
        targetOptions.clear()
        activeSlotIndex = 0
        requiredSwitches = 0
        selectedTargetIndex = -1
        battleFinished = true
    }

    private fun applyBattleError(fields: List<String>) {
        val message = fields.drop(2).joinToString("|").ifBlank { "The server rejected that choice." }
        appendLog(message)
        if (battleFinished || requestId == null || decisionKind == DecisionKind.WAIT) return
        if (decisionKind == DecisionKind.MOVE && activeRequests.size > 1) {
            activeChoices.clear()
            activeSlotIndex = 0
            applyActiveRequest(activeRequests[0])
            status = "Choose a move for active Pokémon 1/${activeRequests.size}"
            return
        }
        if (decisionKind == DecisionKind.SWITCH && requiredSwitches > 1) {
            forceSwitchChoices.clear()
            decisionAvailable = true
            panel = Panel.TEAM
            status = "Choose a Pokémon to switch in 1/$requiredSwitches"
            return
        }
        decisionAvailable = true
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
            DecisionKind.TEAM_PREVIEW -> "Confirm your team order"
            DecisionKind.WAIT -> message
        }
    }

    private fun applyHitModifier(fields: List<String>, superEffective: Boolean = false, resisted: Boolean = false, critical: Boolean = false) {
        val hit = pendingHit ?: return
        if (fields.getOrNull(2)?.substringAfter(':')?.trim() != hit.target) return
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
        appendActivity(message)
    }

    private fun applyCant(fields: List<String>) {
        val actor = battleActor(fields.getOrNull(2))
        val reason = battleEffectName(fields.getOrNull(3)).ifBlank { "that status" }
        appendLog("$actor couldn't move because of $reason.")
    }

    private fun applyAbility(fields: List<String>) {
        if (fields.size < 4) return
        when {
            isPlayerSide(fields[2]) -> updatePlayerDetails { it.copy(ability = fields[3]) }
            else -> opponentDetails = opponentDetails.copy(ability = fields[3])
        }
    }

    private fun applyEndAbility(fields: List<String>) {
        val actor = fields.getOrNull(2) ?: return
        if (isPlayerSide(actor)) updatePlayerDetails { it.copy(ability = "Suppressed") }
        else opponentDetails = opponentDetails.copy(ability = "Suppressed")
        appendLog("${battleActor(actor)}'s ability was suppressed.")
    }

    private fun applyItem(fields: List<String>, replacement: String? = null) {
        if (fields.size < 3) return
        val item = replacement ?: fields.getOrNull(3) ?: return
        when {
            isPlayerSide(fields[2]) -> updatePlayerDetails { it.copy(item = item) }
            else -> opponentDetails = opponentDetails.copy(item = item)
        }
    }

    private fun applyWeather(fields: List<String>) {
        weather = fields.getOrNull(2)?.takeUnless { it.equals("none", true) }.orEmpty()
        appendLog(if (weather.isBlank()) "The weather cleared." else "The weather changed to $weather.")
    }

    private fun applyFieldEffect(fields: List<String>, enabled: Boolean) {
        val effect = battleEffectName(fields.getOrNull(2)).takeIf { it.contains("Terrain", true) } ?: return
        if (enabled) terrain = effect else if (terrain.equals(effect, true)) terrain = ""
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
        val boosts = if (isPlayerSide(side)) playerBoosts else opponentBoosts
        updateBoost(boosts, stat, (boosts[stat] ?: 0) + amount * direction)
        appendLog("${battleActor(side)} ${if (direction > 0) "gained" else "lost"} $amount $stat.")
    }

    private fun applySetBoost(fields: List<String>) {
        val side = fields.getOrNull(2) ?: return
        val stat = fields.getOrNull(3)?.lowercase()?.takeIf { it in BOOST_STATS } ?: return
        val amount = fields.getOrNull(4)?.toIntOrNull() ?: return
        updateBoost(if (isPlayerSide(side)) playerBoosts else opponentBoosts, stat, amount)
        appendLog("${battleActor(side)}'s $stat was set to $amount.")
    }

    private fun clearAllBoosts() {
        playerBoosts.clear()
        opponentBoosts.clear()
        appendLog("All stat changes were reset.")
    }

    private fun clearBoosts(fields: List<String>) {
        val side = fields.getOrNull(2) ?: return
        if (isPlayerSide(side)) playerBoosts.clear() else opponentBoosts.clear()
        appendLog("${if (isPlayerSide(side)) "Your" else "The opponent's"} stat changes were reset.")
    }

    private fun clearNegativeBoosts(fields: List<String>) {
        val side = fields.getOrNull(2) ?: return
        val boosts = if (isPlayerSide(side)) playerBoosts else opponentBoosts
        boosts.filterValues { it < 0 }.keys.toList().forEach(boosts::remove)
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

    private fun battleActor(value: String?) = value.orEmpty().substringAfter(':').trim().ifBlank { "Pokémon" }

    private fun battleEffectName(value: String?) = value.orEmpty().substringAfter(": ").substringBefore(" [")

    private fun updatePlayerDetails(transform: (PokemonDetails) -> PokemonDetails) {
        val previous = playerDetails
        playerDetails = transform(previous)
        teamDetails.indexOfFirst { it.name.equals(previous.name, true) }
            .takeIf { it >= 0 }
            ?.let { teamDetails[it] = playerDetails }
    }

    private fun updateAvailableGimmicks(active: JSONObject) {
        val updated = mutableListOf<BattleGimmick>()
        if (active.has("canZMove") && !active.isNull("canZMove")) updated += BattleGimmick.Z_POWER
        if (active.optBoolean("canMegaEvo")) updated += BattleGimmick.MEGA_EVOLUTION
        if (active.optBoolean("canDynamax")) updated += BattleGimmick.DYNAMAX
        if (active.has("canTerastallize") && !active.isNull("canTerastallize")) updated += BattleGimmick.TERASTALLIZATION
        availableGimmicks.clear()
        availableGimmicks += updated
        if (selectedGimmick !in availableGimmicks) selectedGimmick = null
    }

    private fun updateTargetOptions() {
        targetOptions.clear()
        selectedTargetIndex = -1
        val move = moves.getOrNull(focusedMove) ?: return
        if (activeRequests.size <= 1) return
        val target = move.target.lowercase()
        val options = when (target) {
            "adjacentally", "adjacentallyorself" -> (1..activeRequests.size).map { TargetOption("Ally $it", "-$it") }
            "normal", "adjacentfoe", "any" -> (1..activeRequests.size).map { TargetOption("Foe $it", it.toString()) }
            else -> emptyList()
        }
        targetOptions += options
    }

    private fun appendLog(entry: String) {
        if (battleLog.lastOrNull() == entry) return
        battleLog += entry
        if (battleLog.size > 32) battleLog.removeAt(0)
        appendActivity(entry)
        protocolEventCollector?.add(entry) ?: run {
            latestBattleEvent = entry
            latestBattleEventAtNanos = System.nanoTime()
        }
    }

    private fun appendActivity(entry: String) {
        if (activityMessages.lastOrNull() == entry) return
        activityMessages += entry
        if (activityMessages.size > 64) activityMessages.removeAt(0)
    }

    private fun moveMoveFocus(horizontal: Int, vertical: Int) {
        val row = (focusedMove / 2 + vertical).coerceIn(0, 1)
        val column = (focusedMove % 2 + horizontal).coerceIn(0, 1)
        focusMove(row * 2 + column)
    }

    private fun moveTeamFocus(horizontal: Int, vertical: Int) {
        if (team.isEmpty()) return
        val rowCount = (team.size + 2) / 3
        val row = (focusedTeam / 3 + vertical).coerceIn(0, rowCount - 1)
        val column = (focusedTeam % 3 + horizontal).coerceIn(0, 2)
        focusedTeam = (row * 3 + column).coerceIn(0, team.lastIndex)
        status = "Ready: ${team[focusedTeam]}"
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
        0 -> "Find a ${matchFormat.label}"
        1 -> "Battle format ${matchFormat.menuLabel}"
        2 -> "Open battle chat"
        3 -> if (liveBattleActive) "Forfeit" else "Challenge player"
        4 -> "Sound effects ${if (soundEffectsEnabled) "on" else "off"}"
        5 -> "Background music ${if (musicEnabled) "on" else "off"}"
        6 -> "Haptics ${if (hapticsEnabled) "on" else "off"}"
        7 -> "Touch confirmation ${if (touchConfirmationEnabled) "on" else "off"}"
        8 -> "Sprite style ${if (spriteStyle == SpriteStyle.MODERN_3D) "3D" else "classic"}"
        9 -> "Team library"
        10 -> "Showdown account"
        11 -> "Configure server"
        else -> "Copy battle transcript"
    }

    private fun applyMenuSelection() {
        status = when (focusedMenuItem) {
            0 -> {
                publishClientAction(ClientAction.FIND_BATTLE)
                "Connecting to a ${matchFormat.label}…"
            }
            1 -> {
                publishClientAction(ClientAction.CHOOSE_FORMAT)
                "Choose a battle format."
            }
            2 -> {
                publishClientAction(ClientAction.OPEN_CHAT)
                "Open battle chat."
            }
            3 -> {
                if (liveBattleActive) {
                    publishClientAction(ClientAction.FORFEIT)
                    "Forfeit requires confirmation."
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
                touchConfirmationEnabled = !touchConfirmationEnabled
                "Touch confirmation ${if (touchConfirmationEnabled) "enabled." else "disabled."}"
            }
            8 -> {
                spriteStyle = if (spriteStyle == SpriteStyle.MODERN_3D) SpriteStyle.CLASSIC_2D else SpriteStyle.MODERN_3D
                "${if (spriteStyle == SpriteStyle.MODERN_3D) "3D" else "Classic"} sprite style enabled."
            }
            9 -> {
                publishClientAction(ClientAction.CONFIGURE_TEAM)
                "Manage your saved teams."
            }
            10 -> {
                publishClientAction(ClientAction.CONFIGURE_ACCOUNT)
                "Configure your Showdown account."
            }
            11 -> {
                publishClientAction(ClientAction.CONFIGURE_SERVER)
                "Choose a Pokémon Showdown server."
            }
            else -> {
                publishClientAction(ClientAction.EXPORT_REPLAY)
                "Copy the battle transcript."
            }
        }
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
                status = "${team[focusedTeam]} is already in the order."
                return
            }
            teamPreviewOrder += focusedTeam
            val availableTeam = team.indices.filterNot { teamCondition(it).contains("FNT", true) }
            if (teamPreviewOrder.size < availableTeam.size) {
                status = "Team order ${teamPreviewOrder.size}/${availableTeam.size}: choose the next Pokémon."
                return
            }
            completeTeamSelection(
                "/choose team ${teamPreviewOrder.joinToString("") { (it + 1).toString() }}${requestId?.let { "|$it" } ?: ""}"
            )
            return
        }
        val choice = when (decisionKind) {
            DecisionKind.SWITCH -> {
                if (teamCondition(focusedTeam).contains("FNT", true)) {
                    status = "That Pokémon has fainted."
                    return
                }
                if (!canSwitchTo(focusedTeam)) {
                    status = "${team[focusedTeam]} is already active."
                    return
                }
                val switchChoice = "switch ${focusedTeam + 1}"
                if (switchChoice in forceSwitchChoices) {
                    status = "${team[focusedTeam]} is already selected."
                    return
                }
                if (requiredSwitches > 1) {
                    forceSwitchChoices += switchChoice
                    if (forceSwitchChoices.size < requiredSwitches) {
                        status = "Choose a Pokémon to switch in ${forceSwitchChoices.size + 1}/$requiredSwitches"
                        return
                    }
                    "/choose ${forceSwitchChoices.joinToString(", ")}${requestId?.let { "|$it" } ?: ""}"
                } else {
                    "/choose $switchChoice${requestId?.let { "|$it" } ?: ""}"
                }
            }
            else -> {
                status = "Selected ${team[focusedTeam]}."
                return
            }
        }
        completeTeamSelection(choice)
    }

    private fun completeTeamSelection(choice: String) {
        decisionAvailable = false
        status = "Queued: ${team[focusedTeam]}"
        appendLog(if (choice.startsWith("/choose team")) "Team order submitted." else "${team[focusedTeam]} was selected.")
        chatMessages += "[You] $choice"
        if (chatMessages.size > 32) chatMessages.removeAt(0)
        decisionListeners.toList().forEach { it.onDecision(choice) }
    }

    private fun syncTeamFromRequest(request: JSONObject) {
        val pokemon = request.optJSONObject("side")?.optJSONArray("pokemon") ?: return
        val synced = mutableListOf<PokemonDetails>()
        activeTeamNames.clear()
        activeSlotNames.clear()
        var activeIndex = 0
        for (index in 0 until pokemon.length()) {
            val entry = pokemon.optJSONObject(index) ?: continue
            val details = entry.optString("details", entry.optString("ident").substringAfter(": "))
            val name = details.substringBefore(',').ifBlank { entry.optString("ident").substringAfter(": ", "Pokémon") }
            if (entry.optBoolean("active")) {
                activeSlotNames["$playerSlot${('a'.code + activeIndex).toChar()}"] = name
                activeIndex += 1
            }
            val known = teamDetails.firstOrNull { it.name.equals(name, true) }
            val levelGender = parseDetails(details)
            val condition = entry.optString("condition", "100/100")
            val knownMoves = entry.optJSONArray("moves")?.let { moves ->
                buildList {
                    for (moveIndex in 0 until moves.length()) add(moves.optString(moveIndex))
                }
            } ?: known?.moves.orEmpty()
            synced += PokemonDetails(
                name,
                resolvedTypes(name, known?.types.orEmpty()),
                levelGender.first,
                levelGender.second,
                condition,
                condition(condition),
                entry.optString("baseAbility", known?.ability ?: "Unknown ability"),
                entry.optString("item", known?.item ?: "Unknown item").ifBlank { "Unknown item" },
                knownMoves,
                known?.stats.orEmpty(),
                entry.optString("pokeball", known?.pokeball ?: "pokeball")
            )
        }
        if (synced.isEmpty()) return
        team.clear()
        team += synced.map { it.name }
        teamDetails.clear()
        teamDetails += synced
        activeTeamNames += activeSlotNames.values
        if (activeSlotNames.isNotEmpty()) {
            playerActiveCombatants.clear()
            activeSlotNames.forEach { (slot, name) ->
                synced.firstOrNull { it.name.equals(name, true) }?.let { details ->
                    playerActiveCombatants[slot] = ActiveCombatant(slot, details.name, details.types, details.level, details.gender, details.hp, details.condition, playerEntryAtNanos)
                }
            }
        }
        focusedTeam = focusedTeam.coerceIn(0, team.lastIndex)
        synced.firstOrNull { it.name.equals(playerPokemon, true) }?.let { details ->
            playerDetails = details
            playerHp = details.hp
            playerCondition = details.condition
            playerLevel = details.level
            playerGender = details.gender
        }
    }

    private fun canSwitchTo(index: Int) = index in team.indices &&
        !teamCondition(index).contains("FNT", true) &&
        team[index] !in activeTeamNames

    private fun updateOpponentParty(details: PokemonDetails) {
        val index = opponentTeamDetails.indexOfFirst { it.name.equals(details.name, true) }
        if (index >= 0) {
            opponentTeamDetails[index] = details
        } else if (opponentTeamDetails.size < 6) {
            opponentTeamDetails += details
        }
    }

    private fun updateOpponentParty(name: String, transform: (PokemonDetails) -> PokemonDetails) {
        opponentTeamDetails.firstOrNull { it.name.equals(name, true) }?.let { updateOpponentParty(transform(it)) }
    }

    private fun updatePlayerPartyMember(name: String, transform: (PokemonDetails) -> PokemonDetails) {
        val index = teamDetails.indexOfFirst { it.name.equals(name, true) }
        if (index >= 0) teamDetails[index] = transform(teamDetails[index])
    }

    private fun PokemonDetails.withResolvedTypes() = copy(types = resolvedTypes(name, types))

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

    private fun parseDetails(details: String): Pair<String, String> {
        var level = "50"
        var gender = ""
        details.split(',').map(String::trim).forEach {
            when {
                it.startsWith("L") && it.length > 1 -> level = it.drop(1)
                it == "M" -> gender = "♂"
                it == "F" -> gender = "♀"
            }
        }
        return level to gender
    }

    private fun condition(hp: String) = hp.substringAfter(' ', "READY").uppercase()

    private fun isPlayerSide(side: String) = side.startsWith(playerSlot)

    private fun updatePerspective() {
        playerName = sideNames[playerSlot] ?: playerName
        val opponentSlot = if (playerSlot == "p1") "p2" else "p1"
        opponentName = sideNames[opponentSlot] ?: opponentName
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
        const val MENU_ITEM_COUNT = 13
        const val MENU_COLUMNS = 3
        private val BOOST_STATS = setOf("atk", "def", "spa", "spd", "spe", "accuracy", "evasion")

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
                    MatchFormat(it, label, usesRandomTeams = second.contains('#') || it.contains("randombattle") || it.contains("battlefactory"))
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
