package com.eventroute.app.data.repository.mock

import com.eventroute.app.domain.live.LiveFeedSimulator
import com.eventroute.app.domain.model.TeamLiveState
import com.eventroute.app.domain.repository.LiveTeamRepository
import kotlinx.coroutines.flow.Flow

class MockLiveTeamRepository : LiveTeamRepository {

    override fun observeLiveState(teamId: String): Flow<TeamLiveState> {
        LiveFeedSimulator.startSimulation()
        return LiveFeedSimulator.liveState
    }

    override fun updateUserStatus(teamId: String, status: String) {
        LiveFeedSimulator.updateUserStatus(status)
    }

    override fun triggerTrafficAlert(teamId: String, message: String) {
        LiveFeedSimulator.injectTrafficAlert(message)
    }

    override fun restartSimulation(teamId: String) {
        LiveFeedSimulator.restartSimulation()
    }

    override fun jumpToLateScenario(teamId: String) {
        LiveFeedSimulator.jumpToLateScenario()
    }
}
