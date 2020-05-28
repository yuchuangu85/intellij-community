// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.configurationStore

import com.intellij.facet.FacetManager
import com.intellij.facet.FacetType
import com.intellij.facet.mock.MockFacetType
import com.intellij.facet.mock.MockSubFacetType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.appSystemDir
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.impl.coroutineDispatchingContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsDataStorage
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectCacheFileName
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.packaging.artifacts.ArtifactManager
import com.intellij.packaging.elements.PackagingElementFactory
import com.intellij.packaging.impl.artifacts.PlainArtifactType
import com.intellij.pom.java.LanguageLevel
import com.intellij.project.stateStore
import com.intellij.testFramework.*
import com.intellij.util.io.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

@RunsInActiveStoreMode
class ExternalSystemStorageTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()

  }
  @JvmField
  @Rule
  val disposableRule = DisposableRule()

  @JvmField
  @Rule
  val tempDirManager = TemporaryDirectory()

  @Test
  fun `save single module`() = saveProjectAndCheckResult("singleModule") { project, projectDir ->
    val module = ModuleManager.getInstance(project).newModule(projectDir.resolve("test.iml").systemIndependentPath, ModuleTypeId.JAVA_MODULE)
    ModuleRootModificationUtil.addContentRoot(module, projectDir.systemIndependentPath)
    ExternalSystemModulePropertyManager.getInstance(module).setMavenized(true)
  }

  @Test
  fun `load single module`() = loadProjectAndCheckResults("singleModule") { project ->
    val module = ModuleManager.getInstance(project).modules.single()
    assertThat(module.name).isEqualTo("test")
    assertThat(module.moduleTypeName).isEqualTo(ModuleTypeId.JAVA_MODULE)
    assertThat(ExternalSystemModulePropertyManager.getInstance(module).isMavenized()).isTrue()
  }

  @Test
  fun `save mixed modules`() = saveProjectAndCheckResult("mixedModules") { project, projectDir ->
    val regular = ModuleManager.getInstance(project).newModule(projectDir.resolve("regular.iml").systemIndependentPath, ModuleTypeId.JAVA_MODULE)
    ModuleRootModificationUtil.addContentRoot(regular, projectDir.resolve("regular").systemIndependentPath)
    val imported = ModuleManager.getInstance(project).newModule(projectDir.resolve("imported.iml").systemIndependentPath, ModuleTypeId.JAVA_MODULE)
    ModuleRootModificationUtil.addContentRoot(imported, projectDir.resolve("imported").systemIndependentPath)
    ExternalSystemModulePropertyManager.getInstance(imported).setMavenized(true)
  }

  @Test
  fun `load mixed modules`() = loadProjectAndCheckResults("mixedModules") { project ->
    val modules = ModuleManager.getInstance(project).modules.sortedBy { it.name }
    assertThat(modules).hasSize(2)
    val (imported, regular) = modules
    assertThat(imported.name).isEqualTo("imported")
    assertThat(regular.name).isEqualTo("regular")
    assertThat(imported.moduleTypeName).isEqualTo(ModuleTypeId.JAVA_MODULE)
    assertThat(regular.moduleTypeName).isEqualTo(ModuleTypeId.JAVA_MODULE)
    assertThat(ModuleRootManager.getInstance(imported).contentRootUrls.single()).isEqualTo(VfsUtil.pathToUrl("${project.basePath}/imported"))
    assertThat(ModuleRootManager.getInstance(regular).contentRootUrls.single()).isEqualTo(VfsUtil.pathToUrl("${project.basePath}/regular"))
    assertThat(ExternalSystemModulePropertyManager.getInstance(imported).isMavenized()).isTrue()
    assertThat(ExternalSystemModulePropertyManager.getInstance(regular).isMavenized()).isFalse()
  }

  @Test
  fun `save regular facet in imported module`() = saveProjectAndCheckResult("regularFacetInImportedModule") { project, projectDir ->
    val imported = ModuleManager.getInstance(project).newModule(projectDir.resolve("imported.iml").systemIndependentPath, ModuleTypeId.JAVA_MODULE)
    FacetManager.getInstance(imported).addFacet(MockFacetType.getInstance(), "regular", null)
    ExternalSystemModulePropertyManager.getInstance(imported).setMavenized(true)
  }

  @Test
  fun `load regular facet in imported module`() = loadProjectAndCheckResults("regularFacetInImportedModule") { project ->
    val module = ModuleManager.getInstance(project).modules.single()
    assertThat(module.name).isEqualTo("imported")
    assertThat(ExternalSystemModulePropertyManager.getInstance(module).isMavenized()).isTrue()
    val facet = FacetManager.getInstance(module).allFacets.single()
    assertThat(facet.name).isEqualTo("regular")
    assertThat(facet.type).isEqualTo(MockFacetType.getInstance())
    assertThat(facet.externalSource).isNull()
  }

  @Test
  fun `imported facet in imported module`() = saveProjectAndCheckResult("importedFacetInImportedModule") { project, projectDir ->
    val imported = ModuleManager.getInstance(project).newModule(projectDir.resolve("imported.iml").systemIndependentPath, ModuleTypeId.JAVA_MODULE)
    val facetManager = FacetManager.getInstance(imported)
    val model = facetManager.createModifiableModel()
    val source = ExternalProjectSystemRegistry.getInstance().getSourceById(ExternalProjectSystemRegistry.MAVEN_EXTERNAL_SOURCE_ID)
    model.addFacet(facetManager.createFacet(MockFacetType.getInstance(), "imported", null), source)
    model.commit()
    ExternalSystemModulePropertyManager.getInstance(imported).setMavenized(true)
  }

  @Test
  fun libraries() = saveProjectAndCheckResult("libraries") { project, _ ->
    val libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
    val model = libraryTable.modifiableModel
    model.createLibrary("regular", null)
    model.createLibrary("imported", null, externalSource)
    model.commit()
  }

  @Test
  fun artifacts() = saveProjectAndCheckResult("artifacts") { project, projectDir ->
    val model = ArtifactManager.getInstance(project).createModifiableModel()
    val regular = model.addArtifact("regular", PlainArtifactType.getInstance())
    regular.outputPath = projectDir.resolve("out/artifacts/regular").systemIndependentPath
    val root = PackagingElementFactory.getInstance().createArchive("a.jar")
    val imported = model.addArtifact("imported", PlainArtifactType.getInstance(), root, externalSource)
    imported.outputPath = projectDir.resolve("out/artifacts/imported").systemIndependentPath
    model.commit()
  }

  @Before
  fun registerFacetType() {
    WriteAction.runAndWait<RuntimeException> {
      FacetType.EP_NAME.getPoint().registerExtension(MockFacetType(), disposableRule.disposable)
      FacetType.EP_NAME.getPoint().registerExtension(MockSubFacetType(), disposableRule.disposable)
    }
  }

  private val externalSource get() = ExternalProjectSystemRegistry.getInstance().getSourceById("test")

  private fun saveProjectAndCheckResult(testDataDirName: String, setupProject: (Project, Path) -> Unit) {
    runBlocking {
      createProjectAndUseInLoadComponentStateMode(tempDirManager, directoryBased = true, useDefaultProjectSettings = false) { project ->
        ExternalProjectsManagerImpl.getInstance(project).setStoreExternally(true)
        val projectDir = Paths.get(project.stateStore.directoryStorePath).parent
        val cacheDir = ExternalProjectsDataStorage.getProjectConfigurationDir(project)
        cacheDir.delete()
        Disposer.register(disposableRule.disposable, Disposable { cacheDir.delete() })

        withContext(AppUIExecutor.onUiThread().coroutineDispatchingContext()) {
          runWriteAction {
            //we need to set language level explicitly because otherwise if some tests modifies language level in the default project, we'll
            // get different content in misc.xml
            LanguageLevelProjectExtension.getInstance(project)!!.languageLevel = LanguageLevel.JDK_1_8
            setupProject(project, projectDir)
          }
        }

        project.stateStore.save()

        val expectedDir = tempDirManager.newPath("expectedStorage")
        FileUtil.copyDir(testDataRoot.resolve("common").toFile(), expectedDir.toFile())
        FileUtil.copyDir(testDataRoot.resolve(testDataDirName).toFile(), expectedDir.toFile())

        projectDir.toFile().assertMatches(directoryContentOf(expectedDir.resolve("project")))
        cacheDir.toFile().assertMatches(directoryContentOf(expectedDir.resolve("cache")), FileTextMatcher.ignoreBlankLines())
      }
    }
  }

  private val testDataRoot
    get() = Paths.get(PathManagerEx.getCommunityHomePath()).resolve("platform/external-system-impl/testData/jpsSerialization")

  private fun loadProjectAndCheckResults(testDataDirName: String, checkProject: (Project) -> Unit) {
    @Suppress("RedundantSuspendModifier")
    suspend fun copyProjectFiles(dir: VirtualFile): Path {
      val projectDir = VfsUtil.virtualToIoFile(dir)
      FileUtil.copyDir(testDataRoot.resolve("common/project").toFile(), projectDir)
      val testProjectFilesDir = testDataRoot.resolve(testDataDirName).resolve("project").toFile()
      if (testProjectFilesDir.exists()) {
        FileUtil.copyDir(testProjectFilesDir, projectDir)
      }
      val testCacheFilesDir = testDataRoot.resolve(testDataDirName).resolve("cache").toFile()
      if (testCacheFilesDir.exists()) {
        val cachePath = appSystemDir.resolve("external_build_system").resolve(getProjectCacheFileName(projectDir.absolutePath))
        FileUtil.copyDir(testCacheFilesDir, cachePath.toFile())
      }
      VfsUtil.markDirtyAndRefresh(false, true, true, dir)
      return projectDir.toPath()
    }
    runBlocking {
      createOrLoadProject(tempDirManager, ::copyProjectFiles, loadComponentState = true, useDefaultProjectSettings = false) {
        checkProject(it)
      }
    }
  }
}