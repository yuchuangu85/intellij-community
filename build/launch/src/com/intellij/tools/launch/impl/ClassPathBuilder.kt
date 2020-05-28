package com.intellij.tools.launch.impl

import com.intellij.execution.CommandLineWrapperUtil
import com.intellij.tools.launch.ModulesProvider
import com.intellij.tools.launch.PathsProvider
import com.intellij.tools.launch.TeamCityHelper
import org.jetbrains.jps.model.JpsElementFactory
import org.jetbrains.jps.model.serialization.JpsModelSerializationDataService
import org.jetbrains.jps.model.serialization.JpsProjectLoader
import java.io.File
import com.intellij.util.SystemProperties
import org.jetbrains.jps.model.java.JpsJavaClasspathKind
import org.jetbrains.jps.model.java.JpsJavaExtensionService
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.model.module.JpsModuleDependency
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.jar.Manifest

internal class ClassPathBuilder(private val paths: PathsProvider, private val modules: ModulesProvider) {

  private val model = JpsElementFactory.getInstance().createModel() ?: throw Exception("Couldn't create JpsModel")

  fun build(): File {
    val pathVariablesConfiguration = JpsModelSerializationDataService.getOrCreatePathVariablesConfiguration(model.global)

    val m2HomePath = File(SystemProperties.getUserHome())
      .resolve(".m2")
      .resolve("repository")
    pathVariablesConfiguration.addPathVariable("MAVEN_REPOSITORY", m2HomePath.canonicalPath)

    val kotlinPath = paths.communityRootFolder
      .resolve("build")
      .resolve("dependencies")
      .resolve("build")
      .resolve("kotlin")
      .resolve("Kotlin")
      .resolve("kotlinc")
    pathVariablesConfiguration.addPathVariable("KOTLIN_BUNDLED", kotlinPath.canonicalPath)

    val pathVariables = JpsModelSerializationDataService.computeAllPathVariables(model.global)
    JpsProjectLoader.loadProject(model.project, pathVariables, paths.projectRootFolder.canonicalPath)

    val modulesList = arrayListOf<String>()
    modulesList.add("intellij.platform.boot")
    modulesList.add(modules.mainModule)
    modulesList.addAll(modules.additionalModules)
    modulesList.add("intellij.configurationScript")

    return createClassPathFileForModules(modulesList)
  }

  private fun createClassPathFileForModules(modulesList: List<String>): File {
    val classpath = mutableListOf<String>()
    for (moduleName in modulesList) {
      val module = model.project.modules.singleOrNull { it.name == moduleName }
                   ?: throw Exception("Module $moduleName not found")
      if (isModuleExcluded(module)) continue

      classpath.addAll(getClasspathForModule(module))
    }

    val tempClasspathJarFile = CommandLineWrapperUtil.createClasspathJarFile(Manifest(), classpath.distinct())
    val launcherFolder = paths.launcherFolder
    if (!launcherFolder.exists()) {
      launcherFolder.mkdirs()
    }

    // Important note: classpath file should start from CommandLineWrapperUtil.CLASSPATH_JAR_FILE_NAME_PREFIX
    val launcherClasspathPrefix = CommandLineWrapperUtil.CLASSPATH_JAR_FILE_NAME_PREFIX
    val launcherClasspathFile = launcherFolder.resolve("${launcherClasspathPrefix}Launcher${UUID.randomUUID()}.jar")
    Files.move(tempClasspathJarFile.toPath(), launcherClasspathFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    return launcherClasspathFile
  }

  fun getClasspathForModule(module: JpsModule): List<String> {
    return JpsJavaExtensionService
      .dependencies(module)
      .recursively()
      .satisfying { if (it is JpsModuleDependency) !isModuleExcluded(it.module) else true }
      .includedIn(JpsJavaClasspathKind.runtime(modules.includeTestDependencies))
      .classes().roots.map { teamCityClasspathFixer(it.canonicalPath) }.toList()
  }

  private fun teamCityClasspathFixer(element: String): String {
    return if (TeamCityHelper.isUnderTeamCity)
      element.replace("\\out\\", "\\out\\${paths.productId}\\").replace("/out/", "/out/${paths.productId}/")
    else
      element
  }

  private fun isModuleExcluded(module: JpsModule?): Boolean {
    if (module == null) return true
    return modules.excludedModules.contains(module.name)
  }
}