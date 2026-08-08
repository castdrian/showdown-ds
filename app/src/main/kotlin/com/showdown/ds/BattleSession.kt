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
        CHALLENGE_PLAYER
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
        val accuracy: String = "—"
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
    private var battleVisualSeed = Random.nextInt(1, Int.MAX_VALUE)
    private var pendingHit: PendingHit? = null
    private var requestId: Int? = null
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
    var touchConfirmationEnabled = false
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

    fun hasActivePlayerCombatant() = !playerCondition.contains("FNT", true)

    fun hasActiveOpponentCombatant() = !opponentCondition.contains("FNT", true)

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

    fun focusedTeamDetails() = teamDetails.getOrElse(focusedTeam) { playerDetails }

    fun teamMemberDetails(index: Int) = teamDetails.getOrElse(index) { playerDetails }

    fun teamCondition(index: Int) = teamDetails.getOrNull(index)?.condition.orEmpty()

    fun availableGimmicks() = availableGimmicks.toList()

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
        status = "Ready: ${moves[index].name}"
        notifyListeners()
    }

    fun selectMoveWithTouch(index: Int) {
        if (index !in moves.indices) return
        if (touchConfirmationEnabled && focusedMove != index) {
            focusMove(index)
            return
        }
        focusedMove = index
        panel = Panel.MOVES
        confirmSelection()
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
                val gimmick = selectedGimmick
                val choice = "/choose move ${focusedMove + 1}${gimmick?.let { " ${it.choiceSuffix}" } ?: ""}${requestId?.let { "|$it" } ?: ""}"
                status = "Move sent: ${gimmick?.label?.plus(" ") ?: ""}${move.name}"
                appendLog("$playerPokemon chose ${gimmick?.label?.plus(" ") ?: ""}${move.name}.")
                chatMessages += "[You] $choice"
                decisionAvailable = false
                selectedGimmick = null
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
                    "-heal" -> applyHealth(fields)
                    "-status" -> applyStatus(fields)
                    "-curestatus" -> applyStatus(fields, cured = true)
                    "-supereffective" -> applyHitModifier(fields, superEffective = true)
                    "-resisted" -> applyHitModifier(fields, resisted = true)
                    "-crit" -> applyHitModifier(fields, critical = true)
                    "-ability" -> applyAbility(fields)
                    "-item" -> applyItem(fields)
                    "-enditem" -> applyItem(fields, "No item")
                    "-weather" -> applyWeather(fields)
                    "-fieldstart" -> applyFieldEffect(fields, true)
                    "-fieldend" -> applyFieldEffect(fields, false)
                    "-sidestart" -> applySideCondition(fields, true)
                    "-sideend" -> applySideCondition(fields, false)
                    "-boost" -> applyBoost(fields, 1)
                    "-unboost" -> applyBoost(fields, -1)
                    "-setboost" -> applySetBoost(fields)
                    "-clearallboost" -> clearAllBoosts()
                    "-clearboost" -> clearBoosts(fields)
                    "-clearnegativeboost" -> clearNegativeBoosts(fields)
                    "detailschange", "-formechange", "-mega", "-primal" -> applyFormChange(fields)
                    "-terastallize" -> applyTerastallize(fields)
                    "faint" -> applyFaint(fields)
                    "request" -> applyRequest(fields)
                    "win" -> applyWin(fields)
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
        val entryDelayMillis = queueEntry(playerSide)
        when {
            playerSide -> {
                playerPokemon = pokemon
                playerHp = fields[4]
                applyDetails(true, fields[3])
                playerCondition = condition(fields[4])
                val index = team.indexOfFirst { it.equals(pokemon, true) }
                val activeDetails = if (index >= 0) teamDetails[index] else playerDetails.copy(name = pokemon, types = resolvedTypes(pokemon))
                playerDetails = activeDetails.copy(
                    name = pokemon,
                    types = resolvedTypes(pokemon, activeDetails.types),
                    level = playerLevel,
                    gender = playerGender,
                    hp = playerHp,
                    condition = playerCondition
                )
                if (index >= 0) teamDetails[index] = playerDetails
            }
            else -> {
                opponentPokemon = pokemon
                opponentHp = fields[4]
                applyDetails(false, fields[3])
                opponentCondition = condition(fields[4])
                opponentDetails = opponentDetails.copy(
                    name = pokemon,
                    types = resolvedTypes(pokemon),
                    level = opponentLevel,
                    gender = opponentGender,
                    hp = opponentHp,
                    condition = opponentCondition
                )
                updateOpponentParty(opponentDetails)
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
        when {
            isPlayerSide(fields[2]) -> {
                playerHp = fields[3]
                playerCondition = condition(fields[3])
                updatePlayerDetails { it.copy(hp = playerHp, condition = playerCondition) }
            }
            else -> {
                opponentHp = fields[3]
                opponentCondition = condition(fields[3])
                opponentDetails = opponentDetails.copy(hp = opponentHp, condition = opponentCondition)
                updateOpponentParty(opponentDetails)
            }
        }
    }

    private fun applyFaint(fields: List<String>) {
        val actor = fields.getOrNull(2) ?: return
        val pokemon = actor.substringAfter(':').trim()
        latestFaintedPokemon = pokemon
        latestFaintAtNanos = System.nanoTime()
        if (isPlayerSide(actor)) {
            playerHp = "0 fnt"
            playerCondition = "FNT"
            updatePlayerDetails { it.copy(hp = playerHp, condition = playerCondition) }
        } else {
            opponentHp = "0 fnt"
            opponentCondition = "FNT"
            opponentDetails = opponentDetails.copy(hp = opponentHp, condition = opponentCondition)
            updateOpponentParty(opponentDetails)
        }
        appendLog("$pokemon fainted.")
    }

    private fun applyStatus(fields: List<String>, cured: Boolean = false) {
        val actor = fields.getOrNull(2) ?: return
        val status = if (cured) "READY" else fields.getOrNull(3)?.uppercase() ?: return
        if (isPlayerSide(actor)) {
            playerCondition = status
            updatePlayerDetails { it.copy(condition = status) }
        } else {
            opponentCondition = status
            opponentDetails = opponentDetails.copy(condition = status)
            updateOpponentParty(opponentDetails)
        }
    }

    private fun applyFormChange(fields: List<String>) {
        val actor = fields.getOrNull(2) ?: return
        val details = fields.getOrNull(3) ?: return
        val species = details.substringBefore(',').trim().ifBlank { return }
        if (isPlayerSide(actor)) {
            playerPokemon = species
            applyDetails(true, details)
            updatePlayerDetails { it.copy(name = species, types = resolvedTypes(species), level = playerLevel, gender = playerGender) }
        } else {
            opponentPokemon = species
            applyDetails(false, details)
            opponentDetails = opponentDetails.copy(name = species, types = resolvedTypes(species), level = opponentLevel, gender = opponentGender)
        }
        appendLog("$species changed form.")
    }

    private fun applyTerastallize(fields: List<String>) {
        val actor = fields.getOrNull(2) ?: return
        val teraType = fields.getOrNull(3)?.uppercase() ?: return
        if (isPlayerSide(actor)) {
            updatePlayerDetails { it.copy(types = listOf(teraType)) }
        } else {
            opponentDetails = opponentDetails.copy(types = listOf(teraType))
        }
        appendLog("${actor.substringAfter(':').trim()} Terastallized into $teraType.")
    }

    private fun applyRequest(fields: List<String>) {
        val requestText = fields.getOrNull(2) ?: return
        teamPreviewOrder.clear()
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
            if (request.optJSONArray("forceSwitch")?.optBoolean(0) == true) {
                decisionKind = DecisionKind.SWITCH
                decisionAvailable = team.indices.any { !teamCondition(it).contains("FNT", true) }
                panel = Panel.TEAM
                status = "Choose a Pokémon to switch in"
                return@runCatching
            }
            val active = request.optJSONArray("active")?.optJSONObject(0) ?: run {
                decisionKind = DecisionKind.WAIT
                decisionAvailable = false
                status = "Waiting for a battle decision…"
                return@runCatching
            }
            val requestMoves = active.optJSONArray("moves") ?: run {
                decisionKind = DecisionKind.WAIT
                decisionAvailable = false
                status = "Waiting for a battle decision…"
                return@runCatching
            }
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
                    move.optString("accuracy", "—")
                )
            }
            focusedMove = 0
            decisionKind = DecisionKind.MOVE
            decisionAvailable = moves.isNotEmpty()
            panel = Panel.MOVES
            status = "Choose a move"
            playerDetails = playerDetails.copy(moves = moves.map { it.name })
            team.indexOf(playerPokemon).takeIf { it >= 0 }?.let { teamDetails[it] = playerDetails }
            updateAvailableGimmicks(active)
        }.onFailure {
            appendLog("Received an unreadable battle request.")
        }
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
            battleFinished = true
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
        if (fields.size > 3) {
            val message = "[${fields[2]}] ${fields[3]}"
            chatMessages += message
            if (chatMessages.size > 32) chatMessages.removeAt(0)
            appendActivity(message)
        }
    }

    private fun applyAbility(fields: List<String>) {
        if (fields.size < 4) return
        when {
            isPlayerSide(fields[2]) -> updatePlayerDetails { it.copy(ability = fields[3]) }
            else -> opponentDetails = opponentDetails.copy(ability = fields[3])
        }
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
    }

    private fun applyFieldEffect(fields: List<String>, enabled: Boolean) {
        val effect = battleEffectName(fields.getOrNull(2)).takeIf { it.contains("Terrain", true) } ?: return
        if (enabled) terrain = effect else if (terrain.equals(effect, true)) terrain = ""
    }

    private fun applySideCondition(fields: List<String>, enabled: Boolean) {
        val side = fields.getOrNull(2) ?: return
        val effect = battleEffectName(fields.getOrNull(3)).ifBlank { return }
        val conditions = if (isPlayerSide(side)) playerSideConditions else opponentSideConditions
        if (enabled) {
            if (effect !in conditions) conditions += effect
        } else {
            conditions.removeAll { it.equals(effect, true) }
        }
    }

    private fun applyBoost(fields: List<String>, direction: Int) {
        val side = fields.getOrNull(2) ?: return
        val stat = fields.getOrNull(3)?.lowercase()?.takeIf { it in BOOST_STATS } ?: return
        val amount = fields.getOrNull(4)?.toIntOrNull() ?: return
        val boosts = if (isPlayerSide(side)) playerBoosts else opponentBoosts
        updateBoost(boosts, stat, (boosts[stat] ?: 0) + amount * direction)
    }

    private fun applySetBoost(fields: List<String>) {
        val side = fields.getOrNull(2) ?: return
        val stat = fields.getOrNull(3)?.lowercase()?.takeIf { it in BOOST_STATS } ?: return
        val amount = fields.getOrNull(4)?.toIntOrNull() ?: return
        updateBoost(if (isPlayerSide(side)) playerBoosts else opponentBoosts, stat, amount)
    }

    private fun clearAllBoosts() {
        playerBoosts.clear()
        opponentBoosts.clear()
    }

    private fun clearBoosts(fields: List<String>) {
        val side = fields.getOrNull(2) ?: return
        if (isPlayerSide(side)) playerBoosts.clear() else opponentBoosts.clear()
    }

    private fun clearNegativeBoosts(fields: List<String>) {
        val side = fields.getOrNull(2) ?: return
        val boosts = if (isPlayerSide(side)) playerBoosts else opponentBoosts
        boosts.filterValues { it < 0 }.keys.toList().forEach(boosts::remove)
    }

    private fun updateBoost(boosts: MutableMap<String, Int>, stat: String, value: Int) {
        val bounded = value.coerceIn(-6, 6)
        if (bounded == 0) boosts.remove(stat) else boosts[stat] = bounded
    }

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
        val row = (focusedMenuItem / 2 + vertical).coerceIn(0, (MENU_ITEM_COUNT - 1) / 2)
        val column = (focusedMenuItem % 2 + horizontal).coerceIn(0, 1)
        selectMenuItem((row * 2 + column).coerceAtMost(MENU_ITEM_COUNT - 1))
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
        else -> "Configure server"
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
            else -> {
                publishClientAction(ClientAction.CONFIGURE_SERVER)
                "Choose a Pokémon Showdown server."
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
                "/choose switch ${focusedTeam + 1}${requestId?.let { "|$it" } ?: ""}"
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
        for (index in 0 until pokemon.length()) {
            val entry = pokemon.optJSONObject(index) ?: continue
            val details = entry.optString("details", entry.optString("ident").substringAfter(": "))
            val name = details.substringBefore(',').ifBlank { entry.optString("ident").substringAfter(": ", "Pokémon") }
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
        focusedTeam = focusedTeam.coerceIn(0, team.lastIndex)
        synced.firstOrNull { it.name.equals(playerPokemon, true) }?.let { details ->
            playerDetails = details
            playerHp = details.hp
            playerCondition = details.condition
            playerLevel = details.level
            playerGender = details.gender
        }
    }

    private fun updateOpponentParty(details: PokemonDetails) {
        val index = opponentTeamDetails.indexOfFirst { it.name.equals(details.name, true) }
        if (index >= 0) {
            opponentTeamDetails[index] = details
        } else if (opponentTeamDetails.size < 6) {
            opponentTeamDetails += details
        }
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
        const val MENU_ITEM_COUNT = 12
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
                val label = token.substringBefore(',').trim()
                if (!label.startsWith('[')) return@mapNotNull null
                val id = label.lowercase().filter(Char::isLetterOrDigit)
                id.takeIf { it.isNotBlank() }?.let {
                    MatchFormat(it, label, usesRandomTeams = token.substringAfter(',', "").contains('#') || it.contains("randombattle") || it.contains("battlefactory"))
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
