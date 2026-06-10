package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CricketDao {
    // Teams
    @Query("SELECT * FROM teams ORDER BY name ASC")
    fun getAllTeams(): Flow<List<Team>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTeam(team: Team): Long

    @Delete
    suspend fun deleteTeam(team: Team)

    // Players
    @Query("SELECT * FROM players WHERE teamId = :teamId ORDER BY name ASC")
    fun getPlayersForTeam(teamId: Long): Flow<List<Player>>

    @Query("SELECT * FROM players WHERE teamId = :teamId ORDER BY name ASC")
    suspend fun getPlayersForTeamSync(teamId: Long): List<Player>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayer(player: Player): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayers(players: List<Player>)

    @Delete
    suspend fun deletePlayer(player: Player)

    // Matches
    @Query("SELECT * FROM matches ORDER BY createdTimestamp DESC")
    fun getAllMatches(): Flow<List<CricketMatch>>

    @Query("SELECT * FROM matches WHERE id = :matchId")
    fun getMatchById(matchId: Long): Flow<CricketMatch?>

    @Query("SELECT * FROM matches WHERE id = :matchId")
    suspend fun getMatchByIdSync(matchId: Long): CricketMatch?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: CricketMatch): Long

    @Update
    suspend fun updateMatch(match: CricketMatch)

    @Delete
    suspend fun deleteMatch(match: CricketMatch)

    // Ball Events
    @Query("SELECT * FROM ball_events WHERE matchId = :matchId ORDER BY timestamp ASC")
    fun getBallEventsForMatch(matchId: Long): Flow<List<BallEvent>>

    @Query("SELECT * FROM ball_events WHERE matchId = :matchId ORDER BY timestamp ASC")
    suspend fun getBallEventsForMatchSync(matchId: Long): List<BallEvent>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBallEvent(ballEvent: BallEvent): Long

    @Delete
    suspend fun deleteBallEvent(ballEvent: BallEvent)

    @Query("DELETE FROM ball_events WHERE matchId = :matchId AND id = (SELECT MAX(id) FROM ball_events WHERE matchId = :matchId)")
    suspend fun deleteLastBallEventForMatch(matchId: Long)

    // Users
    @Query("SELECT * FROM users ORDER BY registeredTimestamp ASC")
    fun getAllUsers(): Flow<List<PlatformUser>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<PlatformUser>)

    // Admin Activity Logs
    @Query("SELECT * FROM admin_activity_logs ORDER BY timestamp DESC")
    fun getAllAdminActivityLogs(): Flow<List<AdminActivityLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdminActivityLog(log: AdminActivityLog): Long

    @Query("DELETE FROM admin_activity_logs")
    suspend fun clearAdminActivityLogs()
}
