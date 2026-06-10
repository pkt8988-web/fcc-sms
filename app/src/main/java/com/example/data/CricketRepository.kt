package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class CricketRepository(private val dao: CricketDao) {

    val allTeams: Flow<List<Team>> = dao.getAllTeams()
    val allMatches: Flow<List<CricketMatch>> = dao.getAllMatches()
    val allUsers: Flow<List<PlatformUser>> = dao.getAllUsers()
    val allAdminActivityLogs: Flow<List<AdminActivityLog>> = dao.getAllAdminActivityLogs()

    fun getPlayersForTeam(teamId: Long): Flow<List<Player>> = dao.getPlayersForTeam(teamId)
    
    suspend fun getPlayersForTeamSync(teamId: Long): List<Player> = dao.getPlayersForTeamSync(teamId)

    fun getMatchById(matchId: Long): Flow<CricketMatch?> = dao.getMatchById(matchId)

    suspend fun getMatchByIdSync(matchId: Long): CricketMatch? = dao.getMatchByIdSync(matchId)

    fun getBallEventsForMatch(matchId: Long): Flow<List<BallEvent>> = dao.getBallEventsForMatch(matchId)

    suspend fun getBallEventsForMatchSync(matchId: Long): List<BallEvent> = dao.getBallEventsForMatchSync(matchId)

    suspend fun insertTeam(team: Team): Long = dao.insertTeam(team)

    suspend fun deleteTeam(team: Team) = dao.deleteTeam(team)

    suspend fun insertPlayer(player: Player): Long = dao.insertPlayer(player)

    suspend fun deletePlayer(player: Player) = dao.deletePlayer(player)

    suspend fun insertMatch(match: CricketMatch): Long = dao.insertMatch(match)

    suspend fun updateMatch(match: CricketMatch) = dao.updateMatch(match)

    suspend fun deleteMatch(match: CricketMatch) = dao.deleteMatch(match)

    suspend fun insertBallEvent(ballEvent: BallEvent): Long = dao.insertBallEvent(ballEvent)

    suspend fun undoLastBall(matchId: Long) = dao.deleteLastBallEventForMatch(matchId)

    suspend fun insertAdminActivityLog(log: AdminActivityLog): Long = dao.insertAdminActivityLog(log)

    suspend fun clearAdminActivityLogs() = dao.clearAdminActivityLogs()

    suspend fun seedMockDataIfEmpty() {
        val teams = allTeams.first()
        if (teams.isEmpty()) {
            // Seed Team A: India (Blue)
            val teamAId = dao.insertTeam(Team(name = "India", colorHex = "#1E88E5"))
            val teamAPlayers = listOf(
                Player(teamId = teamAId, name = "Rohit Sharma", role = "Batsman"),
                Player(teamId = teamAId, name = "Yashasvi Jaiswal", role = "Batsman"),
                Player(teamId = teamAId, name = "Virat Kohli", role = "Batsman"),
                Player(teamId = teamAId, name = "Suryakumar Yadav", role = "Batsman"),
                Player(teamId = teamAId, name = "Rishabh Pant", role = "Wicket-Keeper"),
                Player(teamId = teamAId, name = "Hardik Pandya", role = "All-Rounder"),
                Player(teamId = teamAId, name = "Ravindra Jadeja", role = "All-Rounder"),
                Player(teamId = teamAId, name = "Axar Patel", role = "All-Rounder"),
                Player(teamId = teamAId, name = "Jasprit Bumrah", role = "Bowler"),
                Player(teamId = teamAId, name = "Kuldeep Yadav", role = "Bowler"),
                Player(teamId = teamAId, name = "Arshdeep Singh", role = "Bowler"),
                Player(teamId = teamAId, name = "Mohammed Siraj", role = "Bowler")
            )
            dao.insertPlayers(teamAPlayers)

            // Seed Team B: Australia (Gold/Yellow)
            val teamBId = dao.insertTeam(Team(name = "Australia", colorHex = "#FDD835"))
            val teamBPlayers = listOf(
                Player(teamId = teamBId, name = "Travis Head", role = "Batsman"),
                Player(teamId = teamBId, name = "David Warner", role = "Batsman"),
                Player(teamId = teamBId, name = "Mitchell Marsh", role = "Batsman"),
                Player(teamId = teamBId, name = "Glenn Maxwell", role = "All-Rounder"),
                Player(teamId = teamBId, name = "Marcus Stoinis", role = "All-Rounder"),
                Player(teamId = teamBId, name = "Tim David", role = "Batsman"),
                Player(teamId = teamBId, name = "Matthew Wade", role = "Wicket-Keeper"),
                Player(teamId = teamBId, name = "Pat Cummins", role = "All-Rounder"),
                Player(teamId = teamBId, name = "Mitchell Starc", role = "Bowler"),
                Player(teamId = teamBId, name = "Adam Zampa", role = "Bowler"),
                Player(teamId = teamBId, name = "Josh Hazlewood", role = "Bowler"),
                Player(teamId = teamBId, name = "Nathan Ellis", role = "Bowler")
            )
            dao.insertPlayers(teamBPlayers)

            // Seed dynamic formats (Test, ODI, T10, T20) matches for platforms formats chart
            dao.insertMatch(CricketMatch(
                teamAId = teamAId, teamBId = teamBId,
                teamAName = "India", teamBName = "Australia",
                format = "T20", totalOvers = 20,
                tossWinnerId = teamAId, tossWinnerName = "India", tossDecision = "BAT",
                status = "COMPLETED", winnerId = teamAId, winnerMessage = "India won by 15 runs",
                battingFirstTeamId = teamAId, battingSecondTeamId = teamBId, currentInnings = 2
            ))
            dao.insertMatch(CricketMatch(
                teamAId = teamAId, teamBId = teamBId,
                teamAName = "India", teamBName = "Australia",
                format = "T20", totalOvers = 20,
                tossWinnerId = teamAId, tossWinnerName = "India", tossDecision = "BAT",
                status = "LIVE_1ST_INNINGS", winnerId = null, winnerMessage = null,
                battingFirstTeamId = teamAId, battingSecondTeamId = teamBId, currentInnings = 1
            ))
            dao.insertMatch(CricketMatch(
                teamAId = teamAId, teamBId = teamBId,
                teamAName = "India", teamBName = "Australia",
                format = "T10", totalOvers = 10,
                tossWinnerId = teamBId, tossWinnerName = "Australia", tossDecision = "BOWL",
                status = "COMPLETED", winnerId = teamBId, winnerMessage = "Australia won by 6 wickets",
                battingFirstTeamId = teamAId, battingSecondTeamId = teamBId, currentInnings = 2
            ))
            dao.insertMatch(CricketMatch(
                teamAId = teamAId, teamBId = teamBId,
                teamAName = "India", teamBName = "Australia",
                format = "ODI", totalOvers = 50,
                tossWinnerId = teamAId, tossWinnerName = "India", tossDecision = "BAT",
                status = "PRE_MATCH", winnerId = null, winnerMessage = null,
                battingFirstTeamId = teamAId, battingSecondTeamId = teamBId, currentInnings = 1
            ))
            dao.insertMatch(CricketMatch(
                teamAId = teamAId, teamBId = teamBId,
                teamAName = "India", teamBName = "Australia",
                format = "Test", totalOvers = 90,
                tossWinnerId = teamBId, tossWinnerName = "Australia", tossDecision = "BAT",
                status = "PRE_MATCH", winnerId = null, winnerMessage = null,
                battingFirstTeamId = teamBId, battingSecondTeamId = teamAId, currentInnings = 1
            ))
            dao.insertMatch(CricketMatch(
                teamAId = teamAId, teamBId = teamBId,
                teamAName = "India", teamBName = "Australia",
                format = "Custom", totalOvers = 5,
                tossWinnerId = teamAId, tossWinnerName = "India", tossDecision = "BAT",
                status = "PRE_MATCH", winnerId = null, winnerMessage = null,
                battingFirstTeamId = teamAId, battingSecondTeamId = teamBId, currentInnings = 1
            ))
        }

        // Seed Users if empty
        val users = dao.getAllUsers().first()
        if (users.isEmpty()) {
            val userList = mutableListOf<PlatformUser>()
            val now = System.currentTimeMillis()
            val dayMillis = 24 * 60 * 60 * 1000L
            val firstNames = listOf("Arjun", "Rahul", "Priya", "Anjali", "Siddharth", "Vikram", "Neha", "Aarav", "Kabir", "Meera", "Asha", "Rohan", "Sanjay", "Diya", "Karan")
            val lastNames = listOf("Sharma", "Verma", "Patel", "Mehta", "Singh", "Joshi", "Iyer", "Nair", "Reddy", "Gupta", "Sen", "Rao")
            
            // Generate some random registrations scattered over the last 30 days
            for (i in 0 until 180) { // average 6 per day
                val randomDayOffset = (Math.random() * 30).toInt()
                val registeredTime = now - (randomDayOffset * dayMillis) - (Math.random() * dayMillis).toLong()
                val fName = firstNames.random()
                val lName = lastNames.random()
                userList.add(PlatformUser(
                    name = "$fName $lName",
                    email = "${fName.lowercase()}.${lName.lowercase()}${ (10..99).random() }@gmail.com",
                    registeredTimestamp = registeredTime
                ))
            }
            dao.insertUsers(userList)
        }

        // Seed initial activity logs if empty
        val logs = dao.getAllAdminActivityLogs().first()
        if (logs.isEmpty()) {
            val now = System.currentTimeMillis()
            dao.insertAdminActivityLog(AdminActivityLog(
                actionType = "LAUNCH_MATCH",
                details = "Initialized Cricket Match #1 (India vs Australia - T20).",
                timestamp = now - 3 * 3600 * 1000L
            ))
            dao.insertAdminActivityLog(AdminActivityLog(
                actionType = "SETTINGS_CHANGE",
                details = "Adjusted match format overs to standard format limits.",
                timestamp = now - 2 * 3600 * 1000L
            ))
            dao.insertAdminActivityLog(AdminActivityLog(
                actionType = "SCORE_OVERRIDE",
                details = "Corrected Over #1, Ball #3: runsScored overrode from 4 to 1.",
                timestamp = now - 1 * 3600 * 1000L
            ))
        }
    }
}
