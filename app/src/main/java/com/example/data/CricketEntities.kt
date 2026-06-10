package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "teams")
data class Team(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String = "#2196F3"
)

@Entity(tableName = "players")
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val teamId: Long,
    val name: String,
    val role: String // "Batsman", "Bowler", "All-Rounder", "Wicket-Keeper"
)

@Entity(tableName = "matches")
data class CricketMatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val teamAId: Long,
    val teamBId: Long,
    val teamAName: String,
    val teamBName: String,
    val format: String, // "T10", "T20", "ODI", "Test", "Custom"
    val totalOvers: Int,
    val tossWinnerId: Long,
    val tossWinnerName: String,
    val tossDecision: String, // "BAT", "BOWL"
    val status: String, // "PRE_MATCH", "LIVE_1ST_INNINGS", "LIVE_2ND_INNINGS", "COMPLETED"
    val winnerId: Long? = null,
    val winnerMessage: String? = null,
    val battingFirstTeamId: Long,
    val battingSecondTeamId: Long,
    val currentInnings: Int = 1,
    val createdTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "ball_events")
data class BallEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val innings: Int, // 1 or 2
    val overNumber: Int, // 0-based (e.g. over 0)
    val ballNumberInOver: Int, // 1 to 6 (for legal balls in this over)
    val batsmanId: Long,
    val batsmanName: String,
    val bowlerId: Long,
    val bowlerName: String,
    val runsScored: Int, // 0, 1, 2, 3, 4, 6
    val isWide: Boolean = false,
    val isNoBall: Boolean = false,
    val isBye: Boolean = false,
    val isLegBye: Boolean = false,
    val wicketType: String? = null, // "Bowled", "Caught", "LBW", "Run Out", "Stumped", "Hit Wicket", "Retired"
    val wicketDismissedPlayerId: Long? = null,
    val wicketDismissedPlayerName: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "users")
data class PlatformUser(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    val registeredTimestamp: Long
)

@Entity(tableName = "admin_activity_logs")
data class AdminActivityLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actionType: String, // "SCORE_OVERRIDE", "SETTINGS_CHANGE", "REDO_UNDO", "LAUNCH_MATCH", "TEAM_MANAGE"
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
