package com.termux.devcenter.data.repository

import com.termux.devcenter.data.api.BridgeApiClient
import com.termux.devcenter.data.model.TermuxProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProjectRepository(
    private val bridgeClient: BridgeApiClient = BridgeApiClient()
) {
    suspend fun listProjects(): Result<List<TermuxProject>> {
        return withContext(Dispatchers.IO) {
            try {
                bridgeClient.listProjects().fold(
                    onSuccess = { projects ->
                        val projectList = projects.map { proj ->
                            TermuxProject(
                                name = proj["name"] as? String ?: "",
                                path = proj["path"] as? String ?: "",
                                isAndroidProject = proj["isAndroidProject"] as? Boolean ?: false,
                                hasGradle = proj["hasGradle"] as? Boolean ?: false
                            )
                        }
                        Result.success(projectList)
                    },
                    onFailure = { e ->
                        Result.failure(e)
                    }
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
