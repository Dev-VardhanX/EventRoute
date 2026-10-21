package com.eventroute.app.data.repository.remote

import com.eventroute.app.domain.model.TeamLiveState
import com.eventroute.app.domain.repository.LiveTeamRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Remote stub for GET /teams/{id}/live (polled every 15s).
 */
class RemoteLiveTeamRepository : LiveTeamRepository {

    override fun observeLiveState(teamId: String): Flow<TeamLiveState> = flow {
        while (true) {
            // Future implementation will call LiveTeamApi
            emit(
                TeamLiveState(
                    teamId = teamId,
                    members = emptyList(),
                    targetArrivalAt = "09:30 AM"
                )
            )
            delay(15_000L)
        }
    }

    override fun updateUserStatus(teamId: String, status: String) {
        // Stub for PUT /teams/{id}/status
    }

    override fun triggerTrafficAlert(teamId: String, message: String) {
        // Stub for POST /teams/{id}/alerts
    }

    override fun restartSimulation(teamId: String) {}

    override fun jumpToLateScenario(teamId: String) {}
}
