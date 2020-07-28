package circlet.vcs.share

import circlet.client.api.PR_Project
import circlet.client.api.Projects
import circlet.client.pr
import circlet.common.permissions.ProjectRight
import circlet.components.circletWorkspace
import circlet.platform.api.batchAll
import circlet.platform.api.map
import circlet.platform.client.ConnectionStatus
import circlet.platform.client.resolve
import kotlinx.coroutines.CancellationException
import libraries.coroutines.extra.LifetimeSource
import runtime.reactive.MutableProperty
import runtime.reactive.awaitFirst
import runtime.reactive.filter
import runtime.reactive.mapInit

class CircletShareProjectVM(val lifetime: LifetimeSource) {

  @Suppress("RemoveExplicitTypeArguments")
  internal val projectsListState: MutableProperty<ProjectListState> = lifetime.mapInit<ProjectListState>(ProjectListState.Loading) {
    val ws = circletWorkspace.workspace.value ?: return@mapInit ProjectListState.Error()
    val client = ws.client
    client.connectionStatus.filter { it is ConnectionStatus.Connected }.awaitFirst(ws.lifetime)

    try {
      val projectService: Projects = client.pr
      // projects in which there is the right to create new repositories
      val projects = projectService.projectsWithRight(batchAll, ProjectRight.VcsAdmin.code, null, null)
        .map { it.resolve() }
        .data
      ProjectListState.Projects(projects)
    }
    catch (th: CancellationException) {
      throw th
    }
    catch (e: Exception) {
      ProjectListState.Error()
    }
  }

  sealed class ProjectListState {
    object Loading : ProjectListState()

    class Error(val error: String = "Unable to load projects") : ProjectListState()

    class Projects(val projects: List<PR_Project>) : ProjectListState()
  }
}
