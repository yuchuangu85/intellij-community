// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.ProductModulesLayout
import org.jetbrains.jps.model.library.JpsLibrary

@CompileStatic
class PlatformModules {
  /**
   * List of modules which are included into lib/platform-api.jar in all IntelliJ based IDEs.
   */
  static List<String> PLATFORM_API_MODULES = [
    "intellij.platform.analysis",
    "intellij.platform.builtInServer",
    "intellij.platform.core",
    "intellij.platform.diff",
    "intellij.platform.vcs.dvcs",
    "intellij.platform.editor",
    "intellij.platform.externalSystem",
    "intellij.platform.codeStyle",
    "intellij.platform.indexing",
    "intellij.platform.jps.model",
    "intellij.platform.lang",
    "intellij.platform.lvcs",
    "intellij.platform.ide",
    "intellij.platform.projectModel",
    "intellij.platform.remoteServers.agent.rt",
    "intellij.platform.remoteServers",
    "intellij.platform.tasks",
    "intellij.platform.usageView",
    "intellij.platform.vcs.core",
    "intellij.platform.vcs",
    "intellij.platform.vcs.log",
    "intellij.platform.vcs.log.graph",
    "intellij.platform.debugger",
    "intellij.xml.analysis",
    "intellij.xml",
    "intellij.xml.psi",
    "intellij.xml.structureView",
    "intellij.platform.concurrency",
  ]

  /**
   * List of modules which are included into lib/platform-impl.jar in all IntelliJ based IDEs.
   */
  static List<String> PLATFORM_IMPLEMENTATION_MODULES = [
    "intellij.platform.analysis.impl",
    "intellij.platform.builtInServer.impl",
    "intellij.platform.core.impl",
    "intellij.platform.diff.impl",
    "intellij.platform.editor.ex",
    "intellij.platform.codeStyle.impl",
    "intellij.platform.indexing.impl",
    "intellij.platform.elevation",
    "intellij.platform.elevation.client",
    "intellij.platform.elevation.common",
    "intellij.platform.elevation.daemon",
    "intellij.platform.execution.impl",
    "intellij.platform.inspect",
    "intellij.platform.lang.impl",
    "intellij.platform.workspaceModel.storage",
    "intellij.platform.workspaceModel.ide",
    "intellij.platform.lvcs.impl",
    "intellij.platform.ide.impl",
    "intellij.platform.projectModel.impl",
    "intellij.platform.externalSystem.impl",
    "intellij.platform.scriptDebugger.protocolReaderRuntime",
    "intellij.regexp",
    "intellij.platform.remoteServers.impl",
    "intellij.platform.scriptDebugger.backend",
    "intellij.platform.scriptDebugger.ui",
    "intellij.platform.smRunner",
    "intellij.platform.smRunner.vcs",
    "intellij.platform.structureView.impl",
    "intellij.platform.tasks.impl",
    "intellij.platform.testRunner",
    "intellij.platform.debugger.impl",
    "intellij.platform.configurationStore.impl",
    "intellij.platform.serviceContainer",
    "intellij.platform.objectSerializer",
    "intellij.platform.diagnostic",
    "intellij.platform.core.ui",
    "intellij.platform.credentialStore",
    "intellij.platform.rd.community",
    "intellij.platform.ml.impl"
  ]
  private static final String PLATFORM_JAR = "platform-impl.jar"

  @CompileDynamic
  static PlatformLayout createPlatformLayout(ProductModulesLayout productLayout,
                                             Set<String> allProductDependencies,
                                             List<JpsLibrary> additionalProjectLevelLibraries,
                                             BuildContext buildContext) {
    PlatformLayout.platform(productLayout.platformLayoutCustomizer) {
      BaseLayoutSpec.metaClass.addModule = { String moduleName ->
        if (!productLayout.excludedModuleNames.contains(moduleName)) {
          withModule(moduleName)
        }
      }
      BaseLayoutSpec.metaClass.addModule = { String moduleName, String relativeJarPath ->
        if (!productLayout.excludedModuleNames.contains(moduleName)) {
          withModule(moduleName, relativeJarPath)
        }
      }

      productLayout.additionalPlatformJars.entrySet().each {
        def jarName = it.key
        it.value.each {
          addModule(it, jarName)
        }
      }
      PLATFORM_API_MODULES.each {
        addModule(it, "platform-api.jar")
      }

      for (String module in PLATFORM_IMPLEMENTATION_MODULES) {
        addModule(module, PLATFORM_JAR)
      }
      productLayout.productApiModules.each {
        addModule(it, "openapi.jar")
      }

      for (String module in productLayout.productImplementationModules) {
        boolean isRelocated = module == "intellij.xml.dom.impl" ||
                              module == "intellij.platform.structuralSearch" ||
                              module == "intellij.platform.duplicates.analysis"
        addModule(module, isRelocated ? PLATFORM_JAR : productLayout.mainJarName)
      }

      productLayout.moduleExcludes.entrySet().each {
        layout.moduleExcludes.putValues(it.key, it.value)
      }

      addModule("intellij.platform.util", "util.jar")
      addModule("intellij.platform.util.rt", "util.jar")
      addModule("intellij.platform.util.zip", "util.jar")
      addModule("intellij.platform.util.classLoader", "util.jar")
      addModule("intellij.platform.util.text.matching", "util.jar")
      addModule("intellij.platform.util.collections", "util.jar")
      addModule("intellij.platform.util.strings", "util.jar")
      addModule("intellij.platform.util.diagnostic", "util.jar")
      addModule("intellij.platform.util.ui", "util.jar")
      addModule("intellij.platform.util.ex", "util.jar")
      addModule("intellij.platform.ide.util.io", "util.jar")
      addModule("intellij.platform.extensions", "util.jar")

      withoutModuleLibrary("intellij.platform.credentialStore", "dbus-java")
      addModule("intellij.platform.statistics", "stats.jar")
      addModule("intellij.platform.statistics.uploader", "stats.jar")
      addModule("intellij.platform.statistics.config", "stats.jar")
      addModule("intellij.platform.statistics.devkit")

      for (String module in List.of("intellij.relaxng",
                                    "intellij.json",
                                    "intellij.spellchecker",
                                    "intellij.xml.analysis.impl",
                                    "intellij.xml.psi.impl",
                                    "intellij.xml.structureView.impl",
                                    "intellij.xml.impl")) {
        addModule(module, PLATFORM_JAR)
      }

      addModule("intellij.platform.vcs.impl", PLATFORM_JAR)
      addModule("intellij.platform.vcs.dvcs.impl", PLATFORM_JAR)
      addModule("intellij.platform.vcs.log.graph.impl", PLATFORM_JAR)
      addModule("intellij.platform.vcs.log.impl", PLATFORM_JAR)
      addModule("intellij.platform.collaborationTools", PLATFORM_JAR)

      addModule("intellij.platform.objectSerializer.annotations")

      addModule("intellij.platform.bootstrap")
      addModule("intellij.java.guiForms.rt")
      addModule("intellij.platform.boot", "bootstrap.jar")

      addModule("intellij.platform.icons", "resources.jar")
      addModule("intellij.platform.resources", "resources.jar")
      addModule("intellij.platform.colorSchemes", "resources.jar")
      addModule("intellij.platform.resources.en", "resources.jar")

      addModule("intellij.platform.jps.model.serialization", "jps-model.jar")
      addModule("intellij.platform.jps.model.impl", "jps-model.jar")

      addModule("intellij.platform.externalSystem.rt", "external-system-rt.jar")

      addModule("intellij.platform.cdsAgent", "cds/classesLogAgent.jar")

      if (allProductDependencies.contains("intellij.platform.coverage")) {
        addModule("intellij.platform.coverage")
      }

      additionalProjectLevelLibraries.each {
        if (!productLayout.projectLibrariesToUnpackIntoMainJar.contains(it.name) && !layout.excludedProjectLibraries.contains(it.name)) {
          withProjectLibrary(it.name)
        }
      }
      productLayout.projectLibrariesToUnpackIntoMainJar.each {
        withProjectLibraryUnpackedIntoJar(it, productLayout.mainJarName)
      }
      withProjectLibrariesFromIncludedModules(buildContext)

      for (def toRemoveVersion : getLibsToRemoveVersion()) {
        removeVersionFromProjectLibraryJarNames(toRemoveVersion)
      }
    }
  }

  private static @NotNull Set<String> getLibsToRemoveVersion() {
    return Set.of("Trove4j", "Log4J", "jna", "jetbrains-annotations-java5", "JDOM")
  }
}
