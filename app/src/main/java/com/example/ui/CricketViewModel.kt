package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// Admin notification data representation
data class AdminAlert(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val type: String, // "Wicket Fall", "Match End", "System"
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)

// Data Transfer Objects for score calculations
data class BatsmanScorecardEntry(
    val playerId: Long,
    val name: String,
    val runs: Int,
    val balls: Int,
    val fours: Int,
    val sixes: Int,
    val strikeRate: Double,
    val outSummary: String // e.g., "c Smith b Cummins" or "b Starc" or "not out"
)

data class BowlerScorecardEntry(
    val playerId: Long,
    val name: String,
    val overs: Double, // represented as e.g. 3.2
    val maidens: Int,
    val runsConceded: Int,
    val wickets: Int,
    val economy: Double
)

data class ExtrasSummary(
    val wides: Int,
    val noBalls: Int,
    val byes: Int,
    val legByes: Int,
    val total: Int
)

data class InningsScoreSummary(
    val innings: Int,
    val battingTeamId: Long,
    val battingTeamName: String,
    val bowlingTeamId: Long,
    val bowlingTeamName: String,
    val totalRuns: Int,
    val totalWickets: Int,
    val totalOvers: Double, // represented as e.g. 14.3
    val runRate: Double,
    val batsmanScores: List<BatsmanScorecardEntry>,
    val bowlerScores: List<BowlerScorecardEntry>,
    val extras: ExtrasSummary
)

data class MatchStats(
    val match: CricketMatch,
    val innings1Summary: InningsScoreSummary,
    val innings2Summary: InningsScoreSummary?,
    val isCompleted: Boolean,
    val winnerTeamId: Long?,
    val currentInningsSummary: InningsScoreSummary
)

class CricketViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CricketRepository

    // Auth state (OTP)
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone

    private val _otpCode = MutableStateFlow("")
    val otpCode: StateFlow<String> = _otpCode

    private val _currentOtpStep = MutableStateFlow(1) // 1: phone input, 2: OTP verification
    val currentOtpStep: StateFlow<Int> = _currentOtpStep

    // Core sets
    val allTeams: StateFlow<List<Team>>
    val allMatches: StateFlow<List<CricketMatch>>
    val allUsers: StateFlow<List<PlatformUser>>
    val allAdminActivityLogs: StateFlow<List<AdminActivityLog>>

    // Notification & Alert System State
    private val _alertHistory = MutableStateFlow<List<AdminAlert>>(emptyList())
    val alertHistory: StateFlow<List<AdminAlert>> = _alertHistory

    private val _activeInAppAlert = MutableStateFlow<AdminAlert?>(null)
    val activeInAppAlert: StateFlow<AdminAlert?> = _activeInAppAlert

    val isSoundEnabled = MutableStateFlow(true)
    val isWicketAlertEnabled = MutableStateFlow(true)
    val isMatchEndAlertEnabled = MutableStateFlow(true)

    // Callback set from Activity to handle native OS notifications
    var showNativeSystemNotification: ((title: String, message: String) -> Unit)? = null

    // Match Setup Session State
    var selectedTeamAId = MutableStateFlow<Long?>(null)
    var selectedTeamBId = MutableStateFlow<Long?>(null)
    var matchFormat = MutableStateFlow("T20") // "T10", "T20", "ODI", "Test", "Custom"
    var customOvers = MutableStateFlow(5) // Default custom overs
    var tossWinnerId = MutableStateFlow<Long?>(null)
    var tossDecision = MutableStateFlow("BAT") // "BAT", "BOWL"

    // Playing XI selection lists
    var teamAPlayingXI = MutableStateFlow<List<Player>>(emptyList())
    var teamBPlayingXI = MutableStateFlow<List<Player>>(emptyList())

    // Active Live Scoring state
    val activeMatchId = MutableStateFlow<Long?>(null)
    val activeMatch = activeMatchId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.getMatchById(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activeBallEvents = activeMatchId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else repository.getBallEventsForMatch(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Striker, Non-Striker & Bowler tracking (persisted/backed in Compose or VM)
    val strikerId = MutableStateFlow<Long?>(null)
    val nonStrikerId = MutableStateFlow<Long?>(null)
    val currentBowlerId = MutableStateFlow<Long?>(null)

    // Current over helper list for visual ball-by-ball log display in live scores
    val currentOverBalls = activeBallEvents.map { events ->
        if (events.isEmpty()) return@map emptyList<BallEvent>()
        val match = activeMatch.value ?: return@map emptyList<BallEvent>()
        val currentInn = match.currentInnings
        
        // Group by overs. Find the latest over number.
        val inningsEvents = events.filter { it.innings == currentInn }
        if (inningsEvents.isEmpty()) return@map emptyList<BallEvent>()
        
        val maxOver = inningsEvents.maxOf { it.overNumber }
        inningsEvents.filter { it.overNumber == maxOver }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        val database = AppDatabase.getDatabase(application)
        repository = CricketRepository(database.cricketDao())

        allTeams = repository.allTeams.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allMatches = repository.allMatches.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allUsers = repository.allUsers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        allAdminActivityLogs = repository.allAdminActivityLogs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Auto seed
        viewModelScope.launch {
            repository.seedMockDataIfEmpty()
        }
    }

    // OTP Auth Functions
    fun setPhone(value: String) {
        _phone.value = value
    }

    fun setOtp(value: String) {
        _otpCode.value = value
    }

    fun requestOtp() {
        if (_phone.value.isNotBlank()) {
            _currentOtpStep.value = 2
        }
    }

    fun verifyOtp() {
        if (_otpCode.value == "112233" || _otpCode.value.length == 6) {
            _isLoggedIn.value = true
        }
    }

    fun logout() {
        _isLoggedIn.value = false
        _phone.value = ""
        _otpCode.value = ""
        _currentOtpStep.value = 1
    }

    // Alert Actions
    fun triggerAlert(title: String, message: String, type: String) {
        val alert = AdminAlert(title = title, message = message, type = type)
        _alertHistory.update { listOf(alert) + it }
        _activeInAppAlert.value = alert

        if (isSoundEnabled.value) {
            viewModelScope.launch {
                try {
                    val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 90)
                    toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 200)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Trigger native notification callback
        showNativeSystemNotification?.invoke(title, message)

        // Auto dismiss after 5 seconds
        viewModelScope.launch {
            kotlinx.coroutines.delay(5000)
            _activeInAppAlert.compareAndSet(alert, null)
        }
    }

    fun dismissActiveAlert() {
        _activeInAppAlert.value = null
    }

    fun clearAlertHistory() {
        _alertHistory.value = emptyList()
    }

    fun toggleAlertRead(alertId: String) {
        _alertHistory.update { list ->
            list.map { if (it.id == alertId) it.copy(isRead = true) else it }
        }
    }

    // Log Admin Action
    fun logAdminAction(actionType: String, details: String) {
        viewModelScope.launch {
            repository.insertAdminActivityLog(AdminActivityLog(actionType = actionType, details = details))
        }
    }

    fun clearAdminLogs() {
        viewModelScope.launch {
            repository.clearAdminActivityLogs()
        }
    }

    // Team and Player Management
    fun addTeam(name: String, colorHex: String) {
        viewModelScope.launch {
            repository.insertTeam(Team(name = name, colorHex = colorHex))
            repository.insertAdminActivityLog(AdminActivityLog(
                actionType = "TEAM_MANAGE",
                details = "Created a new team: '$name' (Theme: $colorHex)"
            ))
        }
    }

    fun deleteTeam(team: Team) {
        viewModelScope.launch {
            repository.deleteTeam(team)
            repository.insertAdminActivityLog(AdminActivityLog(
                actionType = "TEAM_MANAGE",
                details = "Deleted team: '${team.name}' from database"
            ))
        }
    }

    fun addPlayer(teamId: Long, name: String, role: String) {
        viewModelScope.launch {
            repository.insertPlayer(Player(teamId = teamId, name = name, role = role))
            repository.insertAdminActivityLog(AdminActivityLog(
                actionType = "TEAM_MANAGE",
                details = "Registered new player '$name' as $role"
            ))
        }
    }

    fun deletePlayer(player: Player) {
        viewModelScope.launch {
            repository.deletePlayer(player)
            repository.insertAdminActivityLog(AdminActivityLog(
                actionType = "TEAM_MANAGE",
                details = "Removed player '${player.name}' from team roster"
            ))
        }
    }

    fun getPlayersForTeamFlow(teamId: Long): Flow<List<Player>> {
        return repository.getPlayersForTeam(teamId)
    }

    // Creating Match Flow
    fun startMatchSetup(teamAId: Long, teamBId: Long) {
        selectedTeamAId.value = teamAId
        selectedTeamBId.value = teamBId
        tossWinnerId.value = teamAId // Match Default
        
        // Auto-select initial playing XI from existing team rosters
        viewModelScope.launch {
            val teamAPlayers = repository.getPlayersForTeamSync(teamAId)
            val teamBPlayers = repository.getPlayersForTeamSync(teamBId)
            
            // Limit to max 11, or add dummy if not enough
            teamAPlayingXI.value = fillToEleven(teamAPlayers, teamAId, "Team A Player")
            teamBPlayingXI.value = fillToEleven(teamBPlayers, teamBId, "Team B Player")
        }
    }

    private fun fillToEleven(existing: List<Player>, teamId: Long, defaultPrefix: String): List<Player> {
        val list = existing.toMutableList()
        val needed = 11 - list.size
        if (needed > 0) {
            val roles = listOf("Batsman", "All-Rounder", "Bowler", "Wicket-Keeper")
            for (i in 1..needed) {
                list.add(
                    Player(
                        id = -i.toLong(), // temporary or negative ID
                        teamId = teamId,
                        name = "$defaultPrefix ${existing.size + i}",
                        role = roles[i % roles.size]
                    )
                )
            }
        }
        return list.take(11)
    }

    fun createAndStartMatch() {
        val tAId = selectedTeamAId.value ?: return
        val tBId = selectedTeamBId.value ?: return
        val fmt = matchFormat.value
        val overs = if (fmt == "T10") 10 else if (fmt == "T20") 20 else if (fmt == "ODI") 50 else if (fmt == "Test") 90 else customOvers.value
        val tWinId = tossWinnerId.value ?: tAId
        val tDec = tossDecision.value

        viewModelScope.launch {
            val teamsList = allTeams.value
            val teamA = teamsList.find { it.id == tAId }
            val teamB = teamsList.find { it.id == tBId }
            val teamAName = teamA?.name ?: "Team A"
            val teamBName = teamB?.name ?: "Team B"

            // Compute Batting First and Second
            val battingFirstId = if (tWinId == tAId) {
                if (tDec == "BAT") tAId else tBId
            } else {
                if (tDec == "BAT") tBId else tAId
            }
            val battingSecondId = if (battingFirstId == tAId) tBId else tAId

            val match = CricketMatch(
                teamAId = tAId,
                teamBId = tBId,
                teamAName = teamAName,
                teamBName = teamBName,
                format = fmt,
                totalOvers = overs,
                tossWinnerId = tWinId,
                tossWinnerName = if (tWinId == tAId) teamAName else teamBName,
                tossDecision = tDec,
                status = "LIVE_1ST_INNINGS",
                battingFirstTeamId = battingFirstId,
                battingSecondTeamId = battingSecondId,
                currentInnings = 1
            )

            val newMatchId = repository.insertMatch(match)
            activeMatchId.value = newMatchId

            // To support custom mock playing XI lists, we should insert any negative ID players as real players in database
            val aXI = teamAPlayingXI.value.map {
                if (it.id < 0) {
                    val newId = repository.insertPlayer(Player(teamId = tAId, name = it.name, role = it.role))
                    it.copy(id = newId)
                } else it
            }
            val bXI = teamBPlayingXI.value.map {
                if (it.id < 0) {
                    val newId = repository.insertPlayer(Player(teamId = tBId, name = it.name, role = it.role))
                    it.copy(id = newId)
                } else it
            }
            teamAPlayingXI.value = aXI
            teamBPlayingXI.value = bXI

            // Set default strikers and bowlers
            strikerId.value = aXI.getOrNull(0)?.id ?: -1L
            nonStrikerId.value = aXI.getOrNull(1)?.id ?: -1L
            currentBowlerId.value = bXI.lastOrNull()?.id ?: -1L

            repository.insertAdminActivityLog(AdminActivityLog(
                actionType = "LAUNCH_MATCH",
                details = "Initialized Match #$newMatchId: $teamAName vs $teamBName ($fmt format, $overs overs)"
            ))
        }
    }

    fun openExistingMatch(matchId: Long) {
        viewModelScope.launch {
            activeMatchId.value = matchId
            val m = repository.getMatchByIdSync(matchId)
            if (m != null) {
                // Populate playing XI lists
                val aXI = repository.getPlayersForTeamSync(m.teamAId)
                val bXI = repository.getPlayersForTeamSync(m.teamBId)
                teamAPlayingXI.value = fillToEleven(aXI, m.teamAId, "Player")
                teamBPlayingXI.value = fillToEleven(bXI, m.teamBId, "Player")

                // Auto resolve current strikers/bowlers based on previous ball events or roster
                val events = repository.getBallEventsForMatchSync(matchId)
                val currentInningsEvents = events.filter { it.innings == m.currentInnings }
                if (currentInningsEvents.isNotEmpty()) {
                    val lastBall = currentInningsEvents.last()
                    // If wicket fell, striker/non-striker needs reassignment
                    strikerId.value = lastBall.batsmanId
                    nonStrikerId.value = lastBall.batsmanId // approximate fallback
                    currentBowlerId.value = lastBall.bowlerId
                } else {
                    val battingTeamId = if (m.currentInnings == 1) m.battingFirstTeamId else m.battingSecondTeamId
                    val bowlingTeamId = if (m.currentInnings == 1) m.battingSecondTeamId else m.battingFirstTeamId
                    val batXI = if (battingTeamId == m.teamAId) teamAPlayingXI.value else teamBPlayingXI.value
                    val bowlXI = if (bowlingTeamId == m.teamAId) teamAPlayingXI.value else teamBPlayingXI.value
                    strikerId.value = batXI.getOrNull(0)?.id ?: -1
                    nonStrikerId.value = batXI.getOrNull(1)?.id ?: -1
                    currentBowlerId.value = bowlXI.lastOrNull()?.id ?: -1
                }
            }
        }
    }

    // scoring events
    fun recordBall(
        runsScored: Int,
        isWide: Boolean = false,
        isNoBall: Boolean = false,
        isBye: Boolean = false,
        isLegBye: Boolean = false,
        wicketType: String? = null,
        wicketDismissedPlayerId: Long? = null
    ) {
        val match = activeMatch.value ?: return
        val currentInnings = match.currentInnings
        val events = activeBallEvents.value.filter { it.innings == currentInnings }

        val bId = currentBowlerId.value ?: return
        val sId = strikerId.value ?: return
        val nsId = nonStrikerId.value ?: return

        // Resolve names
        val battingTeamId = if (currentInnings == 1) match.battingFirstTeamId else match.battingSecondTeamId
        val bowlingTeamId = if (currentInnings == 1) match.battingSecondTeamId else match.battingFirstTeamId
        val batSquad = if (battingTeamId == match.teamAId) teamAPlayingXI.value else teamBPlayingXI.value
        val bowlSquad = if (bowlingTeamId == match.teamAId) teamAPlayingXI.value else teamBPlayingXI.value

        val strikerName = batSquad.find { it.id == sId }?.name ?: "Smudge"
        val bowlerName = bowlSquad.find { it.id == bId }?.name ?: "Bish"

        viewModelScope.launch {
            // Compute current over number and legal ball count
            val totalLegalBallsInInnings = events.count { !it.isWide && !it.isNoBall }
            val completedOvers = totalLegalBallsInInnings / 6
            val ballsInCurrentOver = totalLegalBallsInInnings % 6

            val newBallEvent = BallEvent(
                matchId = match.id,
                innings = currentInnings,
                overNumber = completedOvers,
                ballNumberInOver = if (isWide || isNoBall) ballsInCurrentOver else (ballsInCurrentOver + 1),
                batsmanId = sId,
                batsmanName = strikerName,
                bowlerId = bId,
                bowlerName = bowlerName,
                runsScored = runsScored,
                isWide = isWide,
                isNoBall = isNoBall,
                isBye = isBye,
                isLegBye = isLegBye,
                wicketType = wicketType,
                wicketDismissedPlayerId = wicketDismissedPlayerId,
                wicketDismissedPlayerName = if (wicketDismissedPlayerId != null) batSquad.find { it.id == wicketDismissedPlayerId }?.name else null
            )

            repository.insertBallEvent(newBallEvent)

            // Trigger Wicket Fall Admin Notification
            if (wicketType != null && isWicketAlertEnabled.value) {
                val batsmanName = newBallEvent.wicketDismissedPlayerName ?: strikerName
                triggerAlert(
                    title = "WICKET FALL!",
                    message = "$batsmanName was dismissed ($wicketType) off the bowling of $bowlerName!",
                    type = "Wicket Fall"
                )
            }

            // Dynamic logic for shifting striker/non-striker:
            // 1. Swap striker if odd runs (and not wide/noball extras, or if it has physical runs)
            val physicalRuns = if (isBye || isLegBye) 0 else runsScored
            val runSum = physicalRuns + (if (isBye || isLegBye) runsScored else 0)
            if (wicketType == null && runSum % 2 == 1) {
                swapBatsmen()
            }

            // 2. Over completed logic (if 6 legal deliveries are reached)
            // Note: After inserting, if total legal balls % 6 is 0 and last ball was legal, it's end of over
            val updatedEvents = repository.getBallEventsForMatchSync(match.id).filter { it.innings == currentInnings }
            val updatedLegalBalls = updatedEvents.count { !it.isWide && !it.isNoBall }
            
            // Check targets if 2nd innings
            if (currentInnings == 2) {
                val sum1 = calculateInningsSummary(events = repository.getBallEventsForMatchSync(match.id).filter { it.innings == 1 }, match = match, inningsNum = 1)
                val sum2 = calculateInningsSummary(events = updatedEvents, match = match, inningsNum = 2)
                
                if (sum2.totalRuns > sum1.totalRuns) {
                    // Match Completed! Team B wins
                    completeMatch(match, sum2.totalRuns, sum1.totalRuns)
                    return@launch
                } else {
                    val maxBalls = match.totalOvers * 6
                    val currentBalls = updatedLegalBalls
                    val maxWickets = 10
                    if (currentBalls >= maxBalls || sum2.totalWickets >= maxWickets) {
                        // Innings completed, match finished. Determine winner.
                        completeMatch(match, sum2.totalRuns, sum1.totalRuns)
                        return@launch
                    }
                }
            } else {
                // Check if 1st innings is finished
                val maxBalls = match.totalOvers * 6
                if (updatedLegalBalls >= maxBalls || updatedEvents.count { it.wicketDismissedPlayerId != null } >= 10) {
                    // Transition to 2nd innings
                    val updatedMatch = match.copy(
                        currentInnings = 2,
                        status = "LIVE_2ND_INNINGS"
                    )
                    repository.updateMatch(updatedMatch)
                    // Reset strikers/bowlers for 2nd innings (Bowling second team bats, batting second team bowls)
                    val batXI = if (match.battingSecondTeamId == match.teamAId) teamAPlayingXI.value else teamBPlayingXI.value
                    val bowlXI = if (match.battingFirstTeamId == match.teamAId) teamAPlayingXI.value else teamBPlayingXI.value
                    strikerId.value = batXI.getOrNull(0)?.id ?: -1L
                    nonStrikerId.value = batXI.getOrNull(1)?.id ?: -1L
                    currentBowlerId.value = bowlXI.lastOrNull()?.id ?: -1L
                    return@launch
                }
            }

            // Swap batsmen if end of over (6 legal balls)
            if (updatedLegalBalls > 0 && updatedLegalBalls % 6 == 0 && !isWide && !isNoBall) {
                swapBatsmen()
            }
        }
    }

    private fun swapBatsmen() {
        val s = strikerId.value
        val ns = nonStrikerId.value
        strikerId.value = ns
        nonStrikerId.value = s
    }

    fun manualSwapStriker() {
        swapBatsmen()
    }

    fun undoLastBall() {
        val match = activeMatch.value ?: return
        viewModelScope.launch {
            repository.undoLastBall(match.id)
            repository.insertAdminActivityLog(AdminActivityLog(
                actionType = "REDO_UNDO",
                details = "Undid/Removed the last recorded ball delivery on Match #${match.id} (${match.teamAName} vs ${match.teamBName})"
            ))
            // Read events back and restore strikers
            val remainingEvents = repository.getBallEventsForMatchSync(match.id).filter { it.innings == match.currentInnings }
            if (remainingEvents.isNotEmpty()) {
                val lastBall = remainingEvents.last()
                strikerId.value = lastBall.batsmanId
                currentBowlerId.value = lastBall.bowlerId
            }
        }
    }

    fun changeActiveBowler(bowlerId: Long) {
        currentBowlerId.value = bowlerId
    }

    fun changeDismissedBatsman(batsmanId: Long, wicketType: String, nextBatsmanId: Long) {
        val match = activeMatch.value ?: return
        viewModelScope.launch {
            // First record the ball as a wicket
            recordBall(
                runsScored = 0,
                wicketType = wicketType,
                wicketDismissedPlayerId = batsmanId
            )
            // Immediately substitute the batsman with the selected next batsman
            if (strikerId.value == batsmanId) {
                strikerId.value = nextBatsmanId
            } else if (nonStrikerId.value == batsmanId) {
                nonStrikerId.value = nextBatsmanId
            }
        }
    }

    private suspend fun completeMatch(match: CricketMatch, runs2: Int, runs1: Int) {
        val winnerId = if (runs2 > runs1) match.battingSecondTeamId else if (runs1 > runs2) match.battingFirstTeamId else null
        val description = if (runs2 > runs1) {
            val wicketsRemaining = 10 - activeBallEvents.value.filter { it.innings == 2 }.count { it.wicketDismissedPlayerId != null }
            "${if (match.battingSecondTeamId == match.teamAId) match.teamAName else match.teamBName} won by $wicketsRemaining wickets"
        } else if (runs1 > runs2) {
            val margin = runs1 - runs2
            "${if (match.battingFirstTeamId == match.teamAId) match.teamAName else match.teamBName} won by $margin runs"
        } else {
            "Match tied!"
        }

        val updatedMatch = match.copy(
            status = "COMPLETED",
            winnerId = winnerId,
            winnerMessage = description
        )
        repository.updateMatch(updatedMatch)

        // Trigger Match Completed notification
        if (isMatchEndAlertEnabled.value) {
            triggerAlert(
                title = "MATCH COMPLETED!",
                message = "${match.teamAName} vs ${match.teamBName} finished: $description",
                type = "Match End"
            )
        }
    }

    // Stat / Scorecard Calculator
    fun calculateMatchStats(match: CricketMatch, events: List<BallEvent>): MatchStats {
        val innings1Events = events.filter { it.innings == 1 }
        val innings2Events = events.filter { it.innings == 2 }

        val inn1Summary = calculateInningsSummary(innings1Events, match, 1)
        val inn2Summary = if (match.status == "LIVE_2ND_INNINGS" || match.status == "COMPLETED" || innings2Events.isNotEmpty()) {
            calculateInningsSummary(innings2Events, match, 2)
        } else null

        val currentInnSummary = if (match.currentInnings == 1) inn1Summary else inn2Summary ?: calculateInningsSummary(emptyList(), match, 2)

        return MatchStats(
            match = match,
            innings1Summary = inn1Summary,
            innings2Summary = inn2Summary,
            isCompleted = match.status == "COMPLETED",
            winnerTeamId = match.winnerId,
            currentInningsSummary = currentInnSummary
        )
    }

    private fun calculateInningsSummary(events: List<BallEvent>, match: CricketMatch, inningsNum: Int): InningsScoreSummary {
        val battingTeamId = if (inningsNum == 1) match.battingFirstTeamId else match.battingSecondTeamId
        val bowlingTeamId = if (inningsNum == 1) match.battingSecondTeamId else match.battingFirstTeamId
        val battingTeamName = if (battingTeamId == match.teamAId) match.teamAName else match.teamBName
        val bowlingTeamName = if (bowlingTeamId == match.teamAId) match.teamAName else match.teamBName

        val batSquad = if (battingTeamId == match.teamAId) teamAPlayingXI.value else teamBPlayingXI.value
        val bowlSquad = if (bowlingTeamId == match.teamAId) teamAPlayingXI.value else teamBPlayingXI.value

        // Runs and Extras
        var rawRuns = 0
        var wides = 0
        var noBalls = 0
        var byes = 0
        var legByes = 0
        var wicketsCount = 0

        for (ball in events) {
            if (ball.isWide) {
                wides += 1 + ball.runsScored
            } else if (ball.isNoBall) {
                noBalls += 1
                rawRuns += ball.runsScored // Physical runs off no-ball go to batsman
            } else if (ball.isBye) {
                byes += ball.runsScored
            } else if (ball.isLegBye) {
                legByes += ball.runsScored
            } else {
                rawRuns += ball.runsScored
            }

            if (ball.wicketDismissedPlayerId != null) {
                wicketsCount++
            }
        }

        val totalExtras = wides + noBalls + byes + legByes
        val totalRuns = rawRuns + totalExtras

        // Calculate Overs Completed (legal balls)
        val legalBalls = events.count { !it.isWide && !it.isNoBall }
        val oversDouble = (legalBalls / 6) + (legalBalls % 6) / 10.0
        val runRate = if (legalBalls == 0) 0.0 else (totalRuns.toDouble() / legalBalls) * 6.0

        // Batsman calculations
        val batsmanScores = batSquad.map { player ->
            val playerEvents = events.filter { it.batsmanId == player.id }
            val batterRuns = playerEvents.filter { !it.isWide && !it.isBye && !it.isLegBye }.sumOf { it.runsScored }
            val batterBalls = playerEvents.count { !it.isWide }
            val fours = playerEvents.count { it.runsScored == 4 && !it.isWide && !it.isBye && !it.isLegBye }
            val sixes = playerEvents.count { it.runsScored == 6 && !it.isWide && !it.isBye && !it.isLegBye }
            val sr = if (batterBalls == 0) 0.0 else (batterRuns.toDouble() / batterBalls) * 100.0

            val dismissalBall = events.find { it.wicketDismissedPlayerId == player.id }
            val outSummary = if (dismissalBall != null) {
                val fld = dismissalBall.wicketType
                val bowl = dismissalBall.bowlerName
                when (fld) {
                    "Bowled" -> "b $bowl"
                    "Caught" -> "c fielder b $bowl"
                    "LBW" -> "lbw b $bowl"
                    "Stumped" -> "st b $bowl"
                    "Run Out" -> "run out"
                    else -> "out"
                }
            } else {
                val isCurrentlyBatting = (player.id == strikerId.value || player.id == nonStrikerId.value) && (match.status.startsWith("LIVE") && match.currentInnings == inningsNum)
                if (isCurrentlyBatting) "not out*" else "not out"
            }

            BatsmanScorecardEntry(
                playerId = player.id,
                name = player.name,
                runs = batterRuns,
                balls = batterBalls,
                fours = fours,
                sixes = sixes,
                strikeRate = sr,
                outSummary = outSummary
            )
        }

        // Bowler calculations
        val bowlerScores = bowlSquad.map { bowler ->
            val bowlerEvents = events.filter { it.bowlerId == bowler.id }
            val bowlWides = bowlerEvents.count { it.isWide }
            val bowlNoBalls = bowlerEvents.count { it.isNoBall }
            val bowlLegalBalls = bowlerEvents.count { !it.isWide && !it.isNoBall }
            val bowlerOvers = (bowlLegalBalls / 6) + (bowlLegalBalls % 6) / 10.0

            // Runs conceded includes batsman physical runs on any delivery + wides + no-balls
            var bowlerRunsConceded = 0
            for (ball in bowlerEvents) {
                if (ball.isWide) {
                    bowlerRunsConceded += 1 + ball.runsScored
                } else if (ball.isNoBall) {
                    bowlerRunsConceded += 1 + ball.runsScored
                } else if (!ball.isBye && !ball.isLegBye) {
                    bowlerRunsConceded += ball.runsScored
                }
            }

            // Wickets credited to bowler exclude Run Out and Retired
            val bowlerWickets = bowlerEvents.count {
                it.wicketDismissedPlayerId != null && it.wicketType != "Run Out" && it.wicketType != "Retired"
            }

            val bowlsCount = bowlLegalBalls
            val econ = if (bowlsCount == 0) 0.0 else (bowlerRunsConceded.toDouble() / bowlsCount) * 6.0

            BowlerScorecardEntry(
                playerId = bowler.id,
                name = bowler.name,
                overs = bowlerOvers,
                maidens = 0, // Simplified
                runsConceded = bowlerRunsConceded,
                wickets = bowlerWickets,
                economy = econ
            )
        }.filter { it.overs > 0.0 } // Only display bowlers who actually bowled

        return InningsScoreSummary(
            innings = inningsNum,
            battingTeamId = battingTeamId,
            battingTeamName = battingTeamName,
            bowlingTeamId = bowlingTeamId,
            bowlingTeamName = bowlingTeamName,
            totalRuns = totalRuns,
            totalWickets = wicketsCount,
            totalOvers = oversDouble,
            runRate = runRate,
            batsmanScores = batsmanScores,
            bowlerScores = bowlerScores,
            extras = ExtrasSummary(wides = wides, noBalls = noBalls, byes = byes, legByes = legByes, total = totalExtras)
        )
    }
}
