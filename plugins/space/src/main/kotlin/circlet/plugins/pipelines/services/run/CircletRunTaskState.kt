package circlet.plugins.pipelines.services.run

import circlet.plugins.pipelines.services.execution.CircletTaskRunner
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.components.service

class CircletRunTaskState(private val settings: CircletRunTaskConfigurationOptions, environment: ExecutionEnvironment) : CommandLineState(
  environment) {

  override fun startProcess(): ProcessHandler {
    val project = environment.project
    val runner = project.service<CircletTaskRunner>()
    val taskName = settings.taskName
    if (taskName == null) {
      throw ExecutionException("TaskName is null")
    }
    else {
      return runner.run(taskName)
    }
  }

}
