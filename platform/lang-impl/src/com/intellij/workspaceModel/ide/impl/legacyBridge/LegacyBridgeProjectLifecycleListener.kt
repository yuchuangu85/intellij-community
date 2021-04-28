// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.ExternalModuleListStorage
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectServiceContainerCustomizer
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.impl.ModifiableModelCommitterService
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.WorkspaceModelInitialTestContent
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer
import com.intellij.workspaceModel.ide.impl.legacyBridge.facet.FacetEntityChangeListener
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModifiableModelCommitterServiceBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.project.ProjectRootManagerBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.watcher.RootsChangeWatcher
import com.intellij.workspaceModel.ide.impl.legacyBridge.watcher.VirtualFileUrlWatcher
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class LegacyBridgeProjectLifecycleListener : ProjectServiceContainerCustomizer {
  companion object {
    private val LOG = logger<LegacyBridgeProjectLifecycleListener>()

    fun enabled(project: Project) = ModuleManager.getInstance(project) is ModuleManagerComponentBridge
  }

  override fun serviceRegistered(project: Project) {
    val enabled = WorkspaceModel.isEnabled || WorkspaceModelInitialTestContent.peek() != null
    if (!enabled) {
      LOG.info("Using legacy project model to open project")
      return
    }

    LOG.info("Using workspace model to open project")

    val pluginDescriptor = PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)
                           ?: error("Could not find plugin by id: ${PluginManagerCore.CORE_ID}")

    val container = project as ComponentManagerImpl

    container.registerComponent(JpsProjectModelSynchronizer::class.java, JpsProjectModelSynchronizer::class.java, pluginDescriptor, false)
    container.registerComponent(RootsChangeWatcher::class.java, RootsChangeWatcher::class.java, pluginDescriptor, false)
    container.registerComponent(VirtualFileUrlWatcher::class.java, VirtualFileUrlWatcher::class.java, pluginDescriptor, false)
    container.registerComponent(ModuleManager::class.java, ModuleManagerComponentBridge::class.java, pluginDescriptor, true)
    container.registerService(ProjectRootManager::class.java, ProjectRootManagerBridge::class.java, pluginDescriptor, override = true,
      preloadMode = ServiceDescriptor.PreloadMode.AWAIT)
    container.unregisterComponent(ExternalModuleListStorage::class.java)

    container.registerService(ProjectLibraryTable::class.java, ProjectLibraryTableBridgeImpl::class.java, pluginDescriptor, true)

    container.registerService(ModifiableModelCommitterService::class.java, ModifiableModelCommitterServiceBridge::class.java, pluginDescriptor, true)
    container.registerService(WorkspaceModelTopics::class.java, WorkspaceModelTopics::class.java, pluginDescriptor, false)
    container.registerService(FacetEntityChangeListener::class.java, FacetEntityChangeListener::class.java, pluginDescriptor, false)

    if (WorkspaceModel.enabledForArtifacts) {
      registerArtifactManager(container, pluginDescriptor)
    }
  }

  private fun registerArtifactManager(container: ComponentManagerImpl, pluginDescriptor: IdeaPluginDescriptor) {
    try { //todo improve
      val apiClass = Class.forName("com.intellij.packaging.artifacts.ArtifactManager", true, javaClass.classLoader)
      val implClass = Class.forName("com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactManagerBridge", true,
                                    javaClass.classLoader)
      container.registerService(apiClass, implClass, pluginDescriptor, true)
    }
    catch (ignored: Throwable) {
    }
  }
}
