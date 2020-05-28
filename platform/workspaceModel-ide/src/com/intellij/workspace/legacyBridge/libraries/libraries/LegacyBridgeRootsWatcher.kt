// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge.libraries.libraries

import com.google.common.io.Files
import com.intellij.ide.highlighter.ModuleFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.StateStorage
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.getModuleNameByFilePath
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.ProjectRootManagerImpl
import com.intellij.openapi.roots.impl.storage.ClasspathStorage
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.impl.VirtualFilePointerContainerImpl
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.workspace.api.*
import com.intellij.workspace.bracket
import com.intellij.workspace.ide.WorkspaceModel
import com.intellij.workspace.ide.WorkspaceModelChangeListener
import com.intellij.workspace.ide.WorkspaceModelTopics
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

/**
 * Provides rootsChanged events if roots validity was changed.
 * It's implemented by a listener to VirtualFilePointerContainer containing all project roots
 */
@ApiStatus.Internal
class LegacyBridgeRootsWatcher(
  val project: Project
): Disposable {

  private val LOG = Logger.getInstance(javaClass)

  private val rootsValidityChangedListener
    get() = ProjectRootManagerImpl.getInstanceImpl(project).rootsValidityChangedListener

  private val moduleManager = ModuleManager.getInstance(project)
  private val rootFilePointers = LegacyModelRootsFilePointers(project)

  private val myExecutor = if (ApplicationManager.getApplication().isUnitTestMode) ConcurrencyUtil.newSameThreadExecutorService()
  else AppExecutorUtil.createBoundedApplicationPoolExecutor("Workspace Model Project Root Manager", 1)
  private var myCollectWatchRootsFuture: Future<*> = CompletableFuture.completedFuture(null) // accessed in EDT only

  init {
    val messageBusConnection = project.messageBus.connect()
    WorkspaceModelTopics.getInstance(project).subscribeImmediately(messageBusConnection, object : WorkspaceModelChangeListener {
      override fun changed(event: EntityStoreChanged) = LOG.bracket("LibraryRootsWatcher.EntityStoreChange") {
        // TODO It's also possible to calculate it on diffs

        val roots = mutableSetOf<VirtualFileUrl>()
        val jarDirectories = mutableSetOf<VirtualFileUrl>()
        val recursiveJarDirectories = mutableSetOf<VirtualFileUrl>()

        val s = event.storageAfter

        s.entities(SourceRootEntity::class.java).forEach { roots.add(it.url) }
        s.entities(ContentRootEntity::class.java).forEach {
          roots.add(it.url)
          roots.addAll(it.excludedUrls)
        }
        s.entities(LibraryEntity::class.java).forEach {
          roots.addAll(it.excludedRoots)
          for (root in it.roots) {
            when (root.inclusionOptions) {
              LibraryRoot.InclusionOptions.ROOT_ITSELF -> roots.add(root.url)
              LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT -> jarDirectories.add(root.url)
              LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT_RECURSIVELY -> recursiveJarDirectories.add(root.url)
            }.let { } // exhaustive when
          }
        }
        s.entities(SdkEntity::class.java).forEach { roots.add(it.homeUrl) }
        s.entities(JavaModuleSettingsEntity::class.java).forEach { javaSettings -> javaSettings.compilerOutput?.let { roots.add(it) } }
        s.entities(JavaModuleSettingsEntity::class.java).forEach { javaSettings -> javaSettings.compilerOutputForTests?.let { roots.add(it) } }

        rootFilePointers.onModelChange(s)

        myCollectWatchRootsFuture.cancel(false)
        myCollectWatchRootsFuture = myExecutor.submit {
          ReadAction.run<Throwable> {
            newSync(
              newRoots = roots,
              newJarDirectories = jarDirectories,
              newRecursiveJarDirectories = recursiveJarDirectories
            )
          }
        }
      }
    })
    messageBusConnection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun before(events: List<VFileEvent>): Unit = events.forEach { event ->
        val (oldUrl, newUrl) = getUrls(event) ?: return@forEach

        rootFilePointers.onVfsChange(oldUrl, newUrl)
      }

      override fun after(events: List<VFileEvent>) = events.forEach { event ->
        if (event is VFilePropertyChangeEvent) propertyChanged(event)

        val (oldUrl, newUrl) = getUrls(event) ?: return@forEach
        updateModuleName(oldUrl, newUrl)
      }

      private fun updateModuleName(oldUrl: String, newUrl: String) {
        if (!oldUrl.isImlFile() || !newUrl.isImlFile()) return
        val oldModuleName = getModuleNameByFilePath(oldUrl)
        val newModuleName = getModuleNameByFilePath(newUrl)
        if (oldModuleName == newModuleName) return

        val workspaceModel = WorkspaceModel.getInstance(project)
        val moduleEntity = workspaceModel.entityStore.current.resolve(ModuleId(oldModuleName)) ?: return
        workspaceModel.updateProjectModel { diff ->
          diff.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) { this.name = newModuleName }
        }
      }

      private fun propertyChanged(event: VFilePropertyChangeEvent) {
        if (!event.file.isDirectory || event.requestor is StateStorage || event.propertyName != VirtualFile.PROP_NAME) return

        val parentPath = event.file.parent?.path ?: return
        val newAncestorPath = "$parentPath/${event.newValue}"
        val oldAncestorPath = "$parentPath/${event.oldValue}"
        var someModulePathIsChanged = false
        for (module in moduleManager.modules) {
          if (!module.isLoaded || module.isDisposed) continue

          val moduleFilePath = module.moduleFilePath
          if (FileUtil.isAncestor(oldAncestorPath, moduleFilePath, true)) {
            module.stateStore.setPath("$newAncestorPath/${FileUtil.getRelativePath(oldAncestorPath, moduleFilePath, '/')}")
            ClasspathStorage.modulePathChanged(module)
            someModulePathIsChanged = true
          }
        }
        if (someModulePathIsChanged) moduleManager.incModificationCount()
      }

      private fun String.isImlFile() = Files.getFileExtension(this) == ModuleFileType.DEFAULT_EXTENSION

      /** Update stored urls after folder movement */
      private fun getUrls(event: VFileEvent): Pair<String, String>? {
        val oldUrl: String
        val newUrl: String
        when (event) {
          is VFilePropertyChangeEvent -> {
            oldUrl = VfsUtilCore.pathToUrl(event.oldPath)
            newUrl = VfsUtilCore.pathToUrl(event.newPath)
          }
          is VFileMoveEvent -> {
            oldUrl = VfsUtilCore.pathToUrl(event.oldPath)
            newUrl = VfsUtilCore.pathToUrl(event.newPath)
          }
          else -> return null
        }
        return oldUrl to newUrl
      }
    })
  }

  private var disposable = Disposer.newDisposable()

  private fun newSync(newRoots: Set<VirtualFileUrl>,
                      newJarDirectories: Set<VirtualFileUrl>,
                      newRecursiveJarDirectories: Set<VirtualFileUrl>) {
    val oldDisposable = disposable
    val newDisposable = Disposer.newDisposable()
    // creating a container with these URLs with the sole purpose to get events to getRootsValidityChangedListener() when these roots change
    val container = VirtualFilePointerManager.getInstance().createContainer(newDisposable, rootsValidityChangedListener)

    container as VirtualFilePointerContainerImpl
    container.addAll(newRoots.map { it.url })
    container.addAllJarDirectories(newJarDirectories.map { it.url }, false)
    container.addAllJarDirectories(newRecursiveJarDirectories.map { it.url }, true)

    disposable = newDisposable
    Disposer.dispose(oldDisposable)
  }

  private fun clear() {
    rootFilePointers.clear()
  }

  override fun dispose() {
    clear()

    myCollectWatchRootsFuture.cancel(false)
    myExecutor.shutdownNow()

    Disposer.dispose(disposable)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): LegacyBridgeRootsWatcher = project.getComponent(LegacyBridgeRootsWatcher::class.java)
  }
}
