package com.eventroute.app.domain.repository

import com.eventroute.app.domain.model.TeamLiveState
import kotlinx.coroutines.flow.Flow

interface LiveTeamRepository {
    fun observeLiveState(teamId: String): Flow<TeamLiveState>
    fun updateUserStatus(teamId: String, status: String)
    fun triggerTrafficAlert(teamId: String, message: String)
    fun restartSimulation(teamId: String)
    fun jumpToLateScenario(teamId: String)
}
